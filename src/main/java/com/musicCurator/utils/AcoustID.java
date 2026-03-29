package com.musicCurator.utils;

import okhttp3.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * AcoustID audio fingerprinting integration
 * Requires: yt-dlp for audio extraction, fpcalc for fingerprinting
 */
public class AcoustID {
    private static final String API_KEY;

    static {
        Properties prop = new Properties();
        String key = null;
        try (InputStream input = AcoustID.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                prop.load(input);
                key = prop.getProperty("acoustid.api.key");
            }
        } catch (IOException e) {
            System.err.println("Error loading AcoustID config: " + e.getMessage());
        }
        API_KEY = (key != null) ? key : "";
    }
    private static final String BASE_URL = "https://api.acoustid.org/v2/lookup";
    private static final OkHttpClient client = new OkHttpClient();

    /**
     * Identify track from YouTube video
     * Requires yt-dlp and fpcalc to be installed
     */
    public static JsonObject identifyYouTubeVideo(String videoId) throws IOException, InterruptedException {
        // Step 1: Download audio snippet (first 30 seconds)
        String audioFile = downloadAudioSnippet(videoId);

        // Step 2: Generate fingerprint
        String fingerprint = generateFingerprint(audioFile);

        // Step 3: Query AcoustID
        JsonObject result = queryAcoustID(fingerprint);

        // Cleanup
        new File(audioFile).delete();

        return result;
    }

    private static String downloadAudioSnippet(String videoId) throws IOException, InterruptedException {
        String outputFile = "/tmp/audio_" + videoId + ".mp3";

        ProcessBuilder pb = new ProcessBuilder(
                "yt-dlp",
                "-x",
                "--audio-format", "mp3",
                "--postprocessor-args", "-ss 0 -t 30",
                "-o", outputFile,
                "https://www.youtube.com/watch?v=" + videoId
        );

        Process process = pb.start();
        process.waitFor();

        return outputFile;
    }

    private static String generateFingerprint(String audioFile) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder("fpcalc", "-json", audioFile);
        Process process = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line);
        }

        process.waitFor();

        JsonObject json = JsonParser.parseString(output.toString()).getAsJsonObject();
        return json.get("fingerprint").getAsString();
    }

    private static JsonObject queryAcoustID(String fingerprint) throws IOException {
        String url = String.format("%s?client=%s&duration=%d&fingerprint=%s&meta=recordings",
                BASE_URL, API_KEY, 30, java.net.URLEncoder.encode(fingerprint, StandardCharsets.UTF_8));

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful() && response.body() != null) {
                return JsonParser.parseString(response.body().string()).getAsJsonObject();
            }
        }
        return null;
    }
}