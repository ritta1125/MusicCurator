package com.musicCurator.api;

import com.musicCurator.curator.MoodClassifier;
import com.musicCurator.model.Track;
import okhttp3.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bridge class to get Spotify audio features for YouTube videos
 */
public class SpotifyBridge {
    private static final String BASE_URL = "https://api.spotify.com/v1/";
    private static final OkHttpClient client = new OkHttpClient();
    private static String accessToken;

    /**
     * Initialize with Spotify access token
     */
    public static void initialize(String token) {
        accessToken = token;
    }

    /**
     * Search Spotify for a track based on YouTube video info
     * Returns Track object with full audio features
     */
    public static Track searchAndEnrichTrack(String videoId, String videoTitle, String channelName) {
        try {
            // Clean up the video title (remove common YouTube suffixes)
            String cleanTitle = cleanVideoTitle(videoTitle);
            String cleanArtist = cleanChannelName(channelName);

            // Search Spotify
            String query = String.format("%s %s", cleanTitle, cleanArtist);
            JsonObject searchResult = searchSpotifyTrack(query);

            if (searchResult != null) {
                // Get track details
                String spotifyId = searchResult.get("id").getAsString();
                String trackName = searchResult.get("name").getAsString();
                String artistName = searchResult.getAsJsonArray("artists")
                        .get(0).getAsJsonObject()
                        .get("name").getAsString();
                // Get audio features
                JsonObject audioFeatures = getAudioFeatures(spotifyId);

                if (audioFeatures != null) {
                    // Create enriched Track object with YouTube ID but Spotify features
                    Track track = new Track(
                            videoId,  // Keep YouTube video ID
                            trackName,
                            artistName
                    );

                    track.setEnergy(audioFeatures.get("energy").getAsDouble());
                    track.setValence(audioFeatures.get("valence").getAsDouble());
                    track.setDanceability(audioFeatures.get("danceability").getAsDouble());
                    track.setTempo(audioFeatures.get("tempo").getAsDouble());
                    track.setKey(audioFeatures.get("key").getAsInt());
                    track.setMode(audioFeatures.get("mode").getAsInt());
                    track.setAcousticness(audioFeatures.get("acousticness").getAsDouble());
                    track.setInstrumentalness(audioFeatures.get("instrumentalness").getAsDouble());
                    track.setMood(MoodClassifier.classifyMood(track));

                    System.out.printf("  ✓ Matched: %s - %s [Energy: %.2f, Valence: %.2f]%n",
                            artistName, trackName, track.getEnergy(), track.getValence());

                    return track;
                }
            }

            // If no match found, return basic track with estimated features
            System.out.printf("  ⚠ No Spotify match for: %s%n", cleanTitle);
            return createBasicTrack(videoId, videoTitle, channelName);

        } catch (Exception e) {
            System.err.println("Error enriching track: " + e.getMessage());
            return createBasicTrack(videoId, videoTitle, channelName);
        }
    }

    /**
     * Clean YouTube video title (remove [Official Video], (Audio), etc.)
     */
    private static String cleanVideoTitle(String title) {
        return title
                .replaceAll("(?i)\\(official.*?\\)", "")
                .replaceAll("(?i)\\[official.*?]", "")
                .replaceAll("(?i)\\(audio\\)", "")
                .replaceAll("(?i)\\[audio]", "")
                .replaceAll("(?i)\\(lyrics?\\)", "")
                .replaceAll("(?i)\\[lyrics?]", "")
                .replaceAll("(?i)\\(music video\\)", "")
                .replaceAll("(?i)\\[music video]", "")
                .replaceAll("(?i)official video", "")
                .replaceAll("(?i)official audio", "")
                .replaceAll("(?i)\\(.*?remix.*?\\)", "")
                .replaceAll("(?i)\\[.*?remix.*?]", "")
                .replaceAll("(?i)HD", "")
                .replaceAll("(?i)HQ", "")
                .replaceAll("(?i)4K", "")
                .replaceAll("-\\s*Topic$", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Clean channel name (remove - Topic, VEVO, etc.)
     */
    private static String cleanChannelName(String channelName) {
        if (channelName == null) return "";
        return channelName
                .replaceAll("(?i)\\s*-\\s*Topic$", "")
                .replaceAll("(?i)VEVO$", "")
                .replaceAll("(?i)Official$", "")
                .trim();
    }

    /**
     * Search Spotify for a track
     */
    private static JsonObject searchSpotifyTrack(String query) throws IOException {
        String url = BASE_URL + "search?q=" +
                java.net.URLEncoder.encode(query, StandardCharsets.UTF_8) +
                "&type=track&limit=1";

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
                JsonArray tracks = json.getAsJsonObject("tracks").getAsJsonArray("items");

                if (!tracks.isEmpty()) {
                    return tracks.get(0).getAsJsonObject();
                }
            }
        }
        return null;
    }

    /**
     * Get audio features from Spotify
     */
    private static JsonObject getAudioFeatures(String trackId) throws IOException {
        String url = BASE_URL + "audio-features/" + trackId;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return JsonParser.parseString(response.body().string()).getAsJsonObject();
            }
        }
        return null;
    }

    /**
     * Create basic track with estimated features when Spotify match not found
     */
    private static Track createBasicTrack(String videoId, String title, String channelName) {
        Track track = new Track(
                videoId,
                title,
                channelName != null ? channelName : "Unknown Artist"
        );

        // Apply estimated features based on title keywords
        track.setEnergy(estimateEnergy(title));
        track.setValence(estimateValence(title));
        track.setDanceability(0.5);
        track.setTempo(120);
        track.setMood(MoodClassifier.classifyMood(track));

        return track;
    }

    /**
     * Estimate energy from title keywords
     */
    private static double estimateEnergy(String title) {
        String lowerTitle = title.toLowerCase();

        // High energy keywords
        if (lowerTitle.matches(".*(hard|metal|rock|punk|intense|aggressive|fast|hype).*")) {
            return 0.8;
        }
        // Low energy keywords
        if (lowerTitle.matches(".*(slow|calm|peaceful|ambient|chill|lofi|acoustic|ballad).*")) {
            return 0.3;
        }
        // Medium energy
        return 0.5;
    }

    /**
     * Estimate valence (positivity) from title keywords
     */
    private static double estimateValence(String title) {
        String lowerTitle = title.toLowerCase();

        // Positive keywords
        if (lowerTitle.matches(".*(happy|joy|love|party|dance|fun|celebration|upbeat).*")) {
            return 0.7;
        }
        // Negative keywords
        if (lowerTitle.matches(".*(sad|dark|death|pain|alone|lost|cry|depressed).*")) {
            return 0.2;
        }
        // Neutral
        return 0.5;
    }

}