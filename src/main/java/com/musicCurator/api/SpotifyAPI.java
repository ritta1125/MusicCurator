package com.musicCurator.api;

import com.musicCurator.curator.MoodClassifier;
import com.musicCurator.model.Track;
import okhttp3.*;
import com.google.gson.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class SpotifyAPI {
    private static final String BASE_URL = "https://api.spotify.com/v1/";
    private static final OkHttpClient client = new OkHttpClient();
    private static final Gson gson = new Gson();
    private static String CLIENT_ID;
    private static String CLIENT_SECRET;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = SpotifyAPI.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) { CLIENT_ID = ""; CLIENT_SECRET = ""; return; }
            prop.load(input);
            CLIENT_ID = prop.getProperty("spotify.client.id", "");
            CLIENT_SECRET = prop.getProperty("spotify.client.secret", "");
        } catch (IOException e) {
            System.err.println("Error loading config.properties: " + e.getMessage());
            CLIENT_ID = "";
            CLIENT_SECRET = "";
        }
    }

    public static String getAccessToken() throws Exception {
        String credentials = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", "Basic " + encodedCredentials)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Failed to authenticate: " +
                        (response.body() != null ? response.body().string() : "unknown error"));
            }
            String jsonResponse = response.body().string();
            JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
            return jsonObject.get("access_token").getAsString();
        }
    }

    public static List<Track> getPlaylistTracks(String playlistId, String accessToken) throws Exception {
        List<Track> tracks = new ArrayList<>();
        String url = BASE_URL + "playlists/" + playlistId + "/tracks";

        while (url != null) {
            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            JsonObject jsonResponse;
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new Exception("Failed to fetch playlist: " +
                            (response.body() != null ? response.body().string() : "unknown error"));
                }
                jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
            }
            JsonArray items = jsonResponse.getAsJsonArray("items");

            for (JsonElement item : items) {
                JsonObject trackObj = item.getAsJsonObject().getAsJsonObject("track");
                if (trackObj != null && !trackObj.get("id").isJsonNull()) {
                    String id = trackObj.get("id").getAsString();
                    String name = trackObj.get("name").getAsString();
                    String artist = trackObj.getAsJsonArray("artists").get(0).getAsJsonObject().get("name").getAsString();

                    Track track = new Track(id, name, artist);
                    tracks.add(track);
                }
            }

            // Check for next page
            url = jsonResponse.has("next") && !jsonResponse.get("next").isJsonNull()
                    ? jsonResponse.get("next").getAsString() : null;
        }

        // Get audio features for all tracks
        enrichTracksWithFeatures(tracks, accessToken);

        return tracks;
    }

    private static void enrichTracksWithFeatures(List<Track> tracks, String accessToken) throws Exception {
        // Process in batches of 100 (Spotify's limit)
        for (int i = 0; i < tracks.size(); i += 100) {
            List<Track> batch = tracks.subList(i, Math.min(i + 100, tracks.size()));
            String ids = String.join(",", batch.stream().map(Track::getId).toArray(String[]::new));

            Request request = new Request.Builder()
                    .url(BASE_URL + "audio-features?ids=" + ids)
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject jsonResponse = JsonParser.parseString(response.body().string()).getAsJsonObject();
                    JsonArray audioFeatures = jsonResponse.getAsJsonArray("audio_features");

                    for (int j = 0; j < audioFeatures.size(); j++) {
                        JsonObject features = audioFeatures.get(j).getAsJsonObject();
                        if (features != null && !features.isJsonNull()) {
                            Track track = batch.get(j);
                            track.setTempo(features.get("tempo").getAsDouble());
                            track.setKey(features.get("key").getAsInt());
                            track.setEnergy(features.get("energy").getAsDouble());
                            track.setValence(features.get("valence").getAsDouble());
                            track.setDanceability(features.get("danceability").getAsDouble());
                            track.setAcousticness(features.get("acousticness").getAsDouble());
                            track.setInstrumentalness(features.get("instrumentalness").getAsDouble());
                            track.setMode(features.get("mode").getAsInt());

                            // Classify mood
                            track.setMood(MoodClassifier.classifyMood(track));
                        }
                    }
                }
            }
        }
    }

    public static String createPlaylist(String userId, String playlistName, String description,
                                        List<Track> tracks, String accessToken) throws Exception {
        // Create playlist
        JsonObject playlistData = new JsonObject();
        playlistData.addProperty("name", playlistName);
        playlistData.addProperty("description", description);
        playlistData.addProperty("public", false);

        RequestBody body = RequestBody.create(
                MediaType.parse("application/json"),
                gson.toJson(playlistData)
        );

        Request request = new Request.Builder()
                .url(BASE_URL + "users/" + userId + "/playlists")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        String playlistId;
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Failed to create playlist: " +
                        (response.body() != null ? response.body().string() : "unknown error"));
            }
            JsonObject playlist = JsonParser.parseString(response.body().string()).getAsJsonObject();
            playlistId = playlist.get("id").getAsString();
        }

        // Add tracks to playlist
        JsonObject tracksData = new JsonObject();
        JsonArray uris = new JsonArray();
        for (Track track : tracks) {
            uris.add("spotify:track:" + track.getId());
        }
        tracksData.add("uris", uris);

        body = RequestBody.create(
                MediaType.parse("application/json"),
                gson.toJson(tracksData)
        );

        request = new Request.Builder()
                .url(BASE_URL + "playlists/" + playlistId + "/tracks")
                .addHeader("Authorization", "Bearer " + accessToken)
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new Exception("Failed to add tracks: " +
                        (response.body() != null ? response.body().string() : "unknown error"));
            }
        }

        return playlistId;
    }
}
