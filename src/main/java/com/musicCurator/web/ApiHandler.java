package com.musicCurator.web;

import com.google.gson.*;
import com.musicCurator.api.*;
import com.musicCurator.api.AppleMusicAPI;
import com.musicCurator.curator.PlaylistCurator;
import com.musicCurator.model.Track;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class ApiHandler implements HttpHandler {
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Server-side state (single-user local app)
    private static String currentPlatform = null;
    private static boolean authenticated = false;
    private static String spotifyAccessToken = null;
    private static List<Track> lastAnalyzedTracks = null;
    private static List<Track> lastCuratedTracks = null;
    private static String lastCuratedMood = null;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            switch (path) {
                case "/api/status"         -> { if (method.equals("GET"))  handleStatus(exchange); }
                case "/api/auth"           -> { if (method.equals("POST")) handleAuth(exchange); }
                case "/api/analyze"        -> { if (method.equals("POST")) handleAnalyze(exchange); }
                case "/api/curate"         -> { if (method.equals("POST")) handleCurate(exchange); }
                case "/api/save"           -> { if (method.equals("POST")) handleSave(exchange); }
                case "/api/apple-dev-token"-> { if (method.equals("GET"))  handleAppleDevToken(exchange); }
                default                    -> sendJson(exchange, 404, map("error", "Not found"));
            }
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Internal server error";
            sendJson(exchange, 500, map("error", msg));
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private void handleStatus(HttpExchange exchange) throws IOException {
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("platform", currentPlatform);
        resp.put("authenticated", authenticated);
        resp.put("appleMusicConfigured", AppleMusicAPI.isConfigured());
        sendJson(exchange, 200, resp);
    }

    private void handleAppleDevToken(HttpExchange exchange) throws IOException {
        String token = AppleMusicAPI.getDeveloperToken();
        if (token == null) {
            sendJson(exchange, 503, map("error",
                    "Apple Music is not configured. Add apple.team.id, apple.key.id, " +
                    "and apple.private.key.path to config.properties."));
            return;
        }
        sendJson(exchange, 200, map("token", token));
    }

    private void handleAuth(HttpExchange exchange) throws Exception {
        JsonObject body = parseBody(exchange);
        String platform = body.get("platform").getAsString();

        switch (platform) {
            case "spotify" -> {
                spotifyAccessToken = SpotifyAPI.getAccessToken();
                SpotifyBridge.initialize(spotifyAccessToken);
            }
            case "youtube" -> {
                YouTubeAPI.initialize();
                spotifyAccessToken = SpotifyAPI.getAccessToken();
                SpotifyBridge.initialize(spotifyAccessToken);
            }
            case "applemusic" -> {
                if (!body.has("musicUserToken")) {
                    sendJson(exchange, 400, map("error", "musicUserToken is required for Apple Music"));
                    return;
                }
                // Also get a Spotify token for audio feature enrichment
                spotifyAccessToken = SpotifyAPI.getAccessToken();
                SpotifyBridge.initialize(spotifyAccessToken);
                AppleMusicAPI.initialize(body.get("musicUserToken").getAsString());
            }
            default -> {
                sendJson(exchange, 400, map("error", "Unknown platform: " + platform));
                return;
            }
        }

        currentPlatform = platform;
        authenticated = true;
        sendJson(exchange, 200, map("success", true, "platform", platform));
    }

    private void handleAnalyze(HttpExchange exchange) throws Exception {
        if (!authenticated) {
            sendJson(exchange, 401, map("error", "Not authenticated"));
            return;
        }

        String playlistId = parseBody(exchange).get("playlistId").getAsString();

        List<Track> tracks = switch (currentPlatform) {
            case "spotify"     -> SpotifyAPI.getPlaylistTracks(playlistId, spotifyAccessToken);
            case "youtube"     -> YouTubeAPI.getPlaylistTracksWithSpotifyFeatures(playlistId);
            case "applemusic"  -> AppleMusicAPI.getPlaylistTracks(playlistId);
            default -> throw new IllegalStateException("Unknown platform: " + currentPlatform);
        };

        lastAnalyzedTracks = tracks;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tracks", tracks);
        resp.put("moodDistribution", moodDistribution(tracks));
        resp.put("stats", audioStats(tracks));
        resp.put("availableMoods", new ArrayList<>(moodDistribution(tracks).keySet()));
        sendJson(exchange, 200, resp);
    }

    private void handleCurate(HttpExchange exchange) throws Exception {
        if (!authenticated || lastAnalyzedTracks == null) {
            sendJson(exchange, 400, map("error", "Analyze a playlist first"));
            return;
        }

        JsonObject body = parseBody(exchange);
        String mood = body.has("mood") ? body.get("mood").getAsString() : "";
        int length = body.has("length") ? body.get("length").getAsInt() : 20;

        List<Track> curated = PlaylistCurator.createCinematicPlaylist(lastAnalyzedTracks, mood, length);
        PlaylistCurator.createMoodFlow(curated);

        lastCuratedTracks = curated;
        lastCuratedMood = mood.isEmpty() ? "Mixed" : mood;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("tracks", curated);
        resp.put("mood", lastCuratedMood);
        resp.put("length", curated.size());
        sendJson(exchange, 200, resp);
    }

    private void handleSave(HttpExchange exchange) throws Exception {
        if (!authenticated || lastCuratedTracks == null) {
            sendJson(exchange, 400, map("error", "No curated playlist available"));
            return;
        }

        JsonObject body = parseBody(exchange);
        String name = body.get("name").getAsString();
        String mood = lastCuratedMood != null ? lastCuratedMood : "mixed";
        String description = "Cinematic playlist curated for " + mood + " mood. Created with Music Curator.";

        switch (currentPlatform) {
            case "spotify" -> {
                String userId = body.get("userId").getAsString();
                String pid = SpotifyAPI.createPlaylist(
                        userId, name, description, lastCuratedTracks, spotifyAccessToken);
                sendJson(exchange, 200, map("success", true, "playlistId", pid,
                        "url", "https://open.spotify.com/playlist/" + pid));
            }
            case "youtube" -> {
                String pid = YouTubeAPI.createPlaylist(name, description, lastCuratedTracks);
                if (pid == null) { sendJson(exchange, 500, map("error", "Failed to create YouTube playlist")); return; }
                sendJson(exchange, 200, map("success", true, "playlistId", pid,
                        "url", "https://www.youtube.com/playlist?list=" + pid));
            }
            case "applemusic" -> {
                String pid = AppleMusicAPI.createPlaylist(name, description, lastCuratedTracks);
                sendJson(exchange, 200, map("success", true, "playlistId", pid,
                        "url", "https://music.apple.com/library/playlist/" + pid));
            }
            default -> sendJson(exchange, 500, map("error", "Unknown platform: " + currentPlatform));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Map<String, Integer> moodDistribution(List<Track> tracks) {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Track t : tracks) {
            raw.merge(t.getMood() != null ? t.getMood() : "Unknown", 1, Integer::sum);
        }
        return raw.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
    }

    private Map<String, Object> audioStats(List<Track> tracks) {
        DoubleSummaryStatistics tempo   = tracks.stream().mapToDouble(Track::getTempo).summaryStatistics();
        DoubleSummaryStatistics energy  = tracks.stream().mapToDouble(Track::getEnergy).summaryStatistics();
        DoubleSummaryStatistics valence = tracks.stream().mapToDouble(Track::getValence).summaryStatistics();

        String[] keyNames = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        Map<String, Integer> keyCount = new LinkedHashMap<>();
        for (Track t : tracks) {
            int ki = t.getKey();
            if (ki >= 0 && ki < keyNames.length) {
                keyCount.merge(keyNames[ki] + (t.getMode() == 1 ? " Major" : " Minor"), 1, Integer::sum);
            }
        }
        List<String> topKeys = keyCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(3).map(Map.Entry::getKey).toList();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", tracks.size());
        stats.put("avgTempo",   round(tempo.getAverage(), 1));
        stats.put("avgEnergy",  round(energy.getAverage(), 2));
        stats.put("avgValence", round(valence.getAverage(), 2));
        stats.put("topKeys", topKeys);
        return stats;
    }

    private static double round(double v, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(v * factor) / factor;
    }

    private JsonObject parseBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    private void sendJson(HttpExchange exchange, int code, Object obj) throws IOException {
        byte[] bytes = gson.toJson(obj).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Convenience: build a LinkedHashMap from alternating key-value pairs. */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> map(Object... kv) {
        Map<K, V> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((K) kv[i], (V) kv[i + 1]);
        return m;
    }
}
