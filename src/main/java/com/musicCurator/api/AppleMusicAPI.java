package com.musicCurator.api;

import com.google.gson.*;
import com.musicCurator.model.Track;
import okhttp3.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import java.util.logging.Logger;

/**
 * Apple Music API client.
 *
 * Requires in config.properties:
 *   apple.team.id       — 10-character Team ID from developer.apple.com
 *   apple.key.id        — Key ID of your MusicKit private key
 *   apple.private.key.path — absolute path to the .p8 file downloaded from Apple
 *
 * Authentication is a two-step process:
 *   1. Backend generates a Developer Token (ES256 JWT) from the .p8 key — no user interaction.
 *   2. Frontend loads MusicKit JS, calls music.authorize() to get a Music User Token,
 *      then POSTs it to /api/auth so the backend can make user-scoped calls.
 *
 * Audio features: Apple Music does not expose Spotify-style audio features, so tracks
 * are enriched via SpotifyBridge (same approach as YouTubeAPI).
 */
public class AppleMusicAPI {
    private static final String BASE_URL = "https://api.music.apple.com/v1/";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static final Logger logger = Logger.getLogger(AppleMusicAPI.class.getName());

    private static String developerToken;   // generated at startup from .p8 key
    private static String musicUserToken;   // set after frontend OAuth
    private static String storefront = "us";

    static {
        loadConfig();
    }

    // ── Config / token generation ──────────────────────────────────────────────

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream in = AppleMusicAPI.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (in == null) return;
            prop.load(in);
        } catch (IOException e) {
            System.err.println("AppleMusicAPI: could not load config.properties: " + e.getMessage());
            return;
        }

        String teamId   = prop.getProperty("apple.team.id");
        String keyId    = prop.getProperty("apple.key.id");
        String keyPath  = prop.getProperty("apple.private.key.path");

        if (teamId == null || keyId == null || keyPath == null) {
            System.err.println("AppleMusicAPI: missing apple.team.id / apple.key.id / apple.private.key.path in config");
            return;
        }

        try {
            String pem = Files.readString(Path.of(keyPath.trim()));
            developerToken = buildDeveloperToken(teamId.trim(), keyId.trim(), pem);
        } catch (Exception e) {
            System.err.println("AppleMusicAPI: failed to generate developer token: " + e.getMessage());
        }
    }

    /**
     * Generates an ES256 JWT developer token valid for 6 months.
     * The .p8 file from Apple is a PKCS#8-encoded EC private key in PEM format.
     */
    private static String buildDeveloperToken(String teamId, String keyId, String pem) throws Exception {
        // Strip PEM headers and decode
        String keyContent = pem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(keyContent);
        PrivateKey privateKey = KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(keyBytes));

        // Build header + payload
        long now = System.currentTimeMillis() / 1000;
        String header  = b64u((("{\"alg\":\"ES256\",\"kid\":\"" + keyId + "\"}").getBytes(StandardCharsets.UTF_8)));
        String payload = b64u((("{\"iss\":\"" + teamId + "\",\"iat\":" + now + ",\"exp\":" + (now + 15_552_000L) + "}").getBytes(StandardCharsets.UTF_8)));
        String sigInput = header + "." + payload;

        // Sign with SHA256withECDSA (produces DER), convert to raw 64-byte JWT format
        Signature sig = Signature.getInstance("SHA256withECDSA");
        sig.initSign(privateKey);
        sig.update(sigInput.getBytes(StandardCharsets.US_ASCII));
        byte[] jwtSig = derToRaw64(sig.sign());

        return sigInput + "." + b64u(jwtSig);
    }

    /**
     * Convert a DER-encoded ECDSA signature (Java's default) to the raw 64-byte
     * R||S format expected by JWT ES256.
     */
    private static byte[] derToRaw64(byte[] der) {
        int pos = 0;
        if (der[pos++] != 0x30) throw new IllegalArgumentException("Invalid DER signature");
        // Skip total-length field (may be 1 or 2 bytes)
        if ((der[pos] & 0x80) != 0) pos += (der[pos] & 0x7F) + 1; else pos++;

        if (der[pos++] != 0x02) throw new IllegalArgumentException("Invalid DER r-marker");
        int rLen = der[pos++] & 0xFF;
        byte[] r  = Arrays.copyOfRange(der, pos, pos + rLen); pos += rLen;

        if (der[pos++] != 0x02) throw new IllegalArgumentException("Invalid DER s-marker");
        int sLen = der[pos++] & 0xFF;
        byte[] s  = Arrays.copyOfRange(der, pos, pos + sLen);

        byte[] out = new byte[64];
        copyRightAligned(r, out, 0,  32);
        copyRightAligned(s, out, 32, 32);
        return out;
    }

    /** Copies src (stripping leading 0x00 padding) right-aligned into dst[dstOff..dstOff+width). */
    private static void copyRightAligned(byte[] src, byte[] dst, int dstOff, int width) {
        int start = 0;
        while (start < src.length && src[start] == 0) start++;
        int len = src.length - start;
        System.arraycopy(src, start, dst, dstOff + width - len, len);
    }

    private static String b64u(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns the developer token to be served to MusicKit JS in the browser. */
    public static String getDeveloperToken() {
        return developerToken;
    }

    public static boolean isConfigured() {
        return developerToken != null;
    }

    /**
     * Called after the user completes MusicKit JS authorization in the browser.
     * @param userToken  the Music-User-Token returned by music.authorize()
     */
    public static void initialize(String userToken) throws Exception {
        musicUserToken = userToken;
        storefront = fetchStorefront();
        System.out.println("Apple Music initialized — storefront: " + storefront);
    }

    /**
     * Fetches all tracks from an Apple Music playlist and enriches them with
     * Spotify audio features via SpotifyBridge.
     * Supports both user-library playlists (p.*) and catalog playlists (pl.*).
     */
    public static List<Track> getPlaylistTracks(String playlistId) throws Exception {
        List<Track> tracks = new ArrayList<>();
        String url = buildPlaylistTracksUrl(playlistId);

        while (url != null) {
            JsonObject resp = get(url);
            JsonArray data = resp.getAsJsonArray("data");

            for (JsonElement el : data) {
                JsonObject item  = el.getAsJsonObject();
                JsonObject attrs = item.getAsJsonObject("attributes");
                if (attrs == null) continue;

                String videoId = item.get("id").getAsString();
                String name    = attrs.has("name")       ? attrs.get("name").getAsString()       : "Unknown";
                String artist  = attrs.has("artistName") ? attrs.get("artistName").getAsString() : "Unknown";

                Track track = SpotifyBridge.searchAndEnrichTrack(videoId, name, artist);
                tracks.add(track);
            }

            // Pagination
            url = null;
            if (resp.has("next") && !resp.get("next").isJsonNull()) {
                String next = resp.get("next").getAsString();
                // Apple returns a relative path like /v1/me/library/playlists/.../tracks?offset=...
                url = "https://api.music.apple.com" + next;
            }
        }

        return tracks;
    }

    /**
     * Searches Apple Music catalog for each track, creates a playlist, and returns its ID.
     */
    public static String createPlaylist(String name, String description, List<Track> tracks) throws Exception {
        // Step 1: resolve catalog IDs for each track
        List<JsonObject> trackRefs = new ArrayList<>();
        int added = 0;
        for (Track track : tracks) {
            String catalogId = searchCatalogId(track.getName(), track.getArtist());
            if (catalogId != null) {
                JsonObject ref = new JsonObject();
                ref.addProperty("id", catalogId);
                ref.addProperty("type", "songs");
                trackRefs.add(ref);
                added++;
                System.out.printf("\rSearching Apple Music: %d/%d", added, tracks.size());
            }
            Thread.sleep(80); // rate limiting
        }
        System.out.println();

        // Step 2: create the playlist
        JsonObject attrs = new JsonObject();
        attrs.addProperty("name", name);
        attrs.addProperty("description", description);

        JsonArray dataArr = new JsonArray();
        trackRefs.forEach(dataArr::add);
        JsonObject tracksRel = new JsonObject();
        JsonObject tracksData = new JsonObject();
        tracksData.add("data", dataArr);
        tracksRel.add("tracks", tracksData);

        JsonObject body = new JsonObject();
        body.add("attributes", attrs);
        body.add("relationships", tracksRel);

        JsonObject response = post("me/library/playlists", body);
        JsonArray created = response.getAsJsonArray("data");
        if (created == null || created.isEmpty()) {
            throw new Exception("Apple Music did not return a playlist ID");
        }
        return created.get(0).getAsJsonObject().get("id").getAsString();
    }

    // ── Private helpers ────────────────────────────────────────────────────────

    private static String buildPlaylistTracksUrl(String playlistId) {
        // Catalog playlists start with "pl."
        if (playlistId.startsWith("pl.")) {
            return BASE_URL + "catalog/" + storefront + "/playlists/" + playlistId + "/tracks";
        }
        // Everything else is treated as a user-library playlist
        return BASE_URL + "me/library/playlists/" + playlistId + "/tracks";
    }

    private static String fetchStorefront() throws Exception {
        JsonObject resp = get("me/storefront");
        JsonArray data = resp.getAsJsonArray("data");
        if (data != null && !data.isEmpty()) {
            return data.get(0).getAsJsonObject().get("id").getAsString();
        }
        return "us";
    }

    /** Returns the Apple Music catalog song ID for the best search match, or null. */
    private static String searchCatalogId(String trackName, String artist) throws IOException {
        String query = java.net.URLEncoder.encode(trackName + " " + artist, StandardCharsets.UTF_8);
        String url = BASE_URL + "catalog/" + storefront + "/search?term=" + query + "&types=songs&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + developerToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            JsonObject results = json.getAsJsonObject("results");
            if (results == null) return null;
            JsonObject songs = results.getAsJsonObject("songs");
            if (songs == null) return null;
            JsonArray data = songs.getAsJsonArray("data");
            if (data == null || data.isEmpty()) return null;
            return data.get(0).getAsJsonObject().get("id").getAsString();
        }
    }

    private static JsonObject get(String relativeUrl) throws IOException {
        String url = relativeUrl.startsWith("http") ? relativeUrl : BASE_URL + relativeUrl;
        Request.Builder req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + developerToken);
        if (musicUserToken != null) req.addHeader("Music-User-Token", musicUserToken);

        try (Response response = client.newCall(req.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Apple Music API error " + response.code() + ": " +
                        (response.body() != null ? response.body().string() : ""));
            }
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }

    private static JsonObject post(String relativeUrl, JsonObject body) throws IOException {
        String url = BASE_URL + relativeUrl;
        RequestBody rb = RequestBody.create(gson.toJson(body),
                MediaType.parse("application/json; charset=utf-8"));
        Request.Builder req = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + developerToken)
                .post(rb);
        if (musicUserToken != null) req.addHeader("Music-User-Token", musicUserToken);

        try (Response response = client.newCall(req.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Apple Music API error " + response.code() + ": " +
                        (response.body() != null ? response.body().string() : ""));
            }
            return JsonParser.parseString(response.body().string()).getAsJsonObject();
        }
    }
}
