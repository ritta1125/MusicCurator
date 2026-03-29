package com.musicCurator;

import com.musicCurator.api.*;
import com.musicCurator.curator.*;
import com.musicCurator.model.Track;
import com.musicCurator.web.WebServer;

import java.util.*;
import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main {
    private static String spotifyAccessToken;
    private static boolean isSpotify = true;
    private static final Scanner scanner = new Scanner(System.in);
    private static final Logger logger = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        if (args.length > 0 && args[0].equals("--web")) {
            startWeb(args.length > 1 ? Integer.parseInt(args[1]) : 8080);
            return;
        }
        startCli();
    }

    private static void startWeb(int port) {
        try {
            WebServer server = new WebServer(port);
            server.start();
            System.out.println("Open http://localhost:" + port + " in your browser");
            System.out.println("Press Ctrl+C to stop.");
            Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
            Thread.currentThread().join();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Web server failed: " + e.getMessage());
        }
    }

    private static void startCli() {
        try {
            System.out.println("🎵 Welcome to Cinematic Music Curator 🎵");
            System.out.println("=====================================");
            initializePlatform();
            runMainMenu();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Fatal error: " + e.getMessage());
        } finally {
            scanner.close();
        }
    }

    private static void initializePlatform() throws Exception {
        System.out.print("Choose platform - Spotify (s) or YouTube (y): ");
        String platform = scanner.nextLine().toLowerCase().trim();

        if (platform.startsWith("s")) {
            System.out.println("🎵 Initializing Spotify...");
            spotifyAccessToken = SpotifyAPI.getAccessToken();
            SpotifyBridge.initialize(spotifyAccessToken);
            isSpotify = true;
            System.out.println("✓ Spotify authenticated successfully!");
        } else {
            System.out.println("📺 Initializing YouTube...");
            YouTubeAPI.initialize();

            // Also get Spotify token for audio analysis
            System.out.println("🎵 Also connecting to Spotify for audio analysis...");
            spotifyAccessToken = SpotifyAPI.getAccessToken();
            SpotifyBridge.initialize(spotifyAccessToken);

            isSpotify = false;
            System.out.println("✓ YouTube setup complete!");
        }
    }

    private static void runMainMenu() throws Exception {
        while (true) {
            printMenu();
            int choice = getMenuChoice();

            switch (choice) {
                case 1:
                    createCinematicPlaylist();
                    break;
                case 2:
                    analyzePlaylist();
                    break;
                case 3:
                    exportPlaylistWithLinks();
                    break;
                case 4:
                    if (!isSpotify) {
                        analyzeYouTubePlaylist();
                    } else {
                        System.out.println("This feature is only available for YouTube.");
                    }
                    break;
                case 5:
                    System.out.println("Thanks for using Cinematic Music Curator! 🎬");
                    return;
                default:
                    System.out.println("Invalid choice. Please try again.");
            }
        }
    }

    private static void printMenu() {
        System.out.println("\n=== Main Menu ===");
        System.out.println("1. Create cinematic playlist");
        System.out.println("2. Analyze playlist moods");
        System.out.println("3. Export playlist with YouTube links");
        if (!isSpotify) {
            System.out.println("4. Analyze YouTube playlist with Spotify features");
        }
        System.out.println("5. Exit");
        System.out.print("Your choice: ");
    }

    private static int getMenuChoice() {
        try {
            return Integer.parseInt(scanner.nextLine());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void createCinematicPlaylist() throws Exception {
        System.out.print("Enter source playlist ID: ");
        String playlistId = scanner.nextLine().trim();

        System.out.println("📊 Fetching and analyzing tracks...");
        List<Track> tracks;

        if (isSpotify) {
            tracks = SpotifyAPI.getPlaylistTracks(playlistId, spotifyAccessToken);
        } else {
            tracks = YouTubeAPI.getPlaylistTracksWithSpotifyFeatures(playlistId);
        }

        if (tracks.isEmpty()) {
            System.out.println("❌ No tracks found in playlist!");
            return;
        }

        System.out.println("✓ Found " + tracks.size() + " tracks");

        // Show mood analysis
        displayMoodDistribution(tracks);

        // Get curation parameters
        System.out.print("\nEnter target mood (or press Enter for mixed): ");
        String targetMood = scanner.nextLine().trim();

        System.out.print("Enter playlist length (default 20): ");
        String lengthInput = scanner.nextLine().trim();
        int playlistLength = lengthInput.isEmpty() ? 20 : Integer.parseInt(lengthInput);

        // Create curated playlist
        System.out.println("\n🎬 Curating your cinematic playlist...");
        List<Track> curatedPlaylist = PlaylistCurator.createCinematicPlaylist(
                tracks, targetMood, playlistLength);

        if (curatedPlaylist.isEmpty()) {
            System.out.println("❌ Could not create playlist with specified criteria.");
            return;
        }

        // Apply cinematic flow
        PlaylistCurator.createMoodFlow(curatedPlaylist);

        // Display the playlist
        displayPlaylist(curatedPlaylist);

        // Save options
        offerSaveOptions(curatedPlaylist, targetMood);
    }

    private static void displayMoodDistribution(List<Track> tracks) {
        Map<String, Integer> moodCounts = new HashMap<>();
        for (Track track : tracks) {
            moodCounts.merge(track.getMood(), 1, Integer::sum);
        }

        System.out.println("\n📊 Mood Distribution:");
        moodCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    double percentage = (entry.getValue() * 100.0) / tracks.size();
                    System.out.printf("  %-25s: %3d tracks (%.1f%%)\n",
                            entry.getKey(), entry.getValue(), percentage);
                });
    }

    private static void displayPlaylist(List<Track> playlist) {
        System.out.println("\n🎬 Your Cinematic Playlist 🎬");
        System.out.println("================================");

        for (int i = 0; i < playlist.size(); i++) {
            Track track = playlist.get(i);
            System.out.printf("%2d. %-30s - %-20s [%s]\n",
                    i + 1,
                    truncate(track.getName(), 30),
                    truncate(track.getArtist(), 20),
                    track.getMood());
            System.out.printf("    Tempo: %.0f BPM | Energy: %.2f | Valence: %.2f\n",
                    track.getTempo(), track.getEnergy(), track.getValence());
        }
    }

    private static String truncate(String str, int length) {
        return str.length() <= length ? str : str.substring(0, length - 3) + "...";
    }

    private static void offerSaveOptions(List<Track> curatedPlaylist, String targetMood)
            throws Exception {
        System.out.println("\n💾 Save Options:");
        System.out.println("1. Save to " + (isSpotify ? "Spotify" : "YouTube"));
        System.out.println("2. Export to file");
        System.out.println("3. Export with YouTube links");
        System.out.println("4. Skip");
        System.out.print("Your choice: ");

        int saveChoice = getMenuChoice();

        switch (saveChoice) {
            case 1:
                saveToPlaylist(curatedPlaylist, targetMood);
                break;
            case 2:
                exportToFile(curatedPlaylist);
                break;
            case 3:
                exportWithYouTubeLinks(curatedPlaylist);
                break;
            case 4:
                System.out.println("Playlist not saved.");
                break;
        }
    }

    private static void saveToPlaylist(List<Track> playlist, String targetMood)
            throws Exception {
        System.out.print("Enter playlist name: ");
        String playlistName = scanner.nextLine().trim();

        String description = String.format("Cinematic playlist curated for %s mood. " +
                        "Created with Music Curator.",
                targetMood.isEmpty() ? "mixed" : targetMood);

        if (isSpotify) {
            System.out.print("Enter your Spotify user ID: ");
            String userId = scanner.nextLine().trim();

            String playlistId = SpotifyAPI.createPlaylist(
                    userId, playlistName, description, playlist, spotifyAccessToken);
            System.out.println("✓ Spotify playlist created! ID: " + playlistId);
        } else {
            String playlistId = YouTubeAPI.createPlaylist(playlistName, description, playlist);
            if (playlistId != null) {
                System.out.println("✓ YouTube playlist created!");
                System.out.println("🔗 https://www.youtube.com/playlist?list=" + playlistId);
            }
        }
    }

    private static void analyzePlaylist() throws Exception {
        System.out.print("Enter playlist ID to analyze: ");
        String playlistId = scanner.nextLine().trim();

        System.out.println("📊 Analyzing playlist...");
        List<Track> tracks;

        if (isSpotify) {
            tracks = SpotifyAPI.getPlaylistTracks(playlistId, spotifyAccessToken);
        } else {
            tracks = YouTubeAPI.getPlaylistTracksWithSpotifyFeatures(playlistId);
        }

        if (tracks.isEmpty()) {
            System.out.println("❌ No tracks found!");
            return;
        }

        // Display comprehensive analysis
        System.out.println("\n📊 Playlist Analysis Report 📊");
        System.out.println("=============================");
        System.out.println("Total tracks: " + tracks.size());

        displayMoodDistribution(tracks);
        displayAudioFeatureStats(tracks);
        displayKeyDistribution(tracks);
    }

    private static void displayAudioFeatureStats(List<Track> tracks) {
        System.out.println("\n🎵 Audio Feature Statistics:");

        DoubleSummaryStatistics tempoStats = tracks.stream()
                .mapToDouble(Track::getTempo).summaryStatistics();
        DoubleSummaryStatistics energyStats = tracks.stream()
                .mapToDouble(Track::getEnergy).summaryStatistics();
        DoubleSummaryStatistics valenceStats = tracks.stream()
                .mapToDouble(Track::getValence).summaryStatistics();

        System.out.printf("  Tempo:   Avg: %.1f BPM | Min: %.1f | Max: %.1f\n",
                tempoStats.getAverage(), tempoStats.getMin(), tempoStats.getMax());
        System.out.printf("  Energy:  Avg: %.2f | Min: %.2f | Max: %.2f\n",
                energyStats.getAverage(), energyStats.getMin(), energyStats.getMax());
        System.out.printf("  Valence: Avg: %.2f | Min: %.2f | Max: %.2f\n",
                valenceStats.getAverage(), valenceStats.getMin(), valenceStats.getMax());
    }

    private static void displayKeyDistribution(List<Track> tracks) {
        String[] keys = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
        Map<String, Integer> keyCount = new HashMap<>();

        for (Track track : tracks) {
            int keyIndex = track.getKey();
            if (keyIndex >= 0 && keyIndex < keys.length) {
                String key = keys[keyIndex] + (track.getMode() == 1 ? " Major" : " Minor");
                keyCount.merge(key, 1, Integer::sum);
            }
        }

        System.out.println("\n🎹 Key Distribution:");
        keyCount.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> System.out.printf("  %-10s: %d tracks\n",
                        entry.getKey(), entry.getValue()));
    }

    private static void analyzeYouTubePlaylist() {
        System.out.print("Enter YouTube playlist ID: ");
        String playlistId = scanner.nextLine().trim();

        System.out.println("🔍 Fetching YouTube playlist and analyzing with Spotify...");
        List<Track> tracks = YouTubeAPI.getPlaylistTracksWithSpotifyFeatures(playlistId);

        if (tracks.isEmpty()) {
            System.out.println("❌ No tracks found or could not match with Spotify!");
            return;
        }

        System.out.println("✓ Successfully analyzed " + tracks.size() + " tracks");
        displayMoodDistribution(tracks);
        displayAudioFeatureStats(tracks);
    }

    private static void exportPlaylistWithLinks() throws Exception {
        System.out.print("Enter playlist ID to export: ");
        String playlistId = scanner.nextLine().trim();

        List<Track> tracks;
        if (isSpotify) {
            tracks = SpotifyAPI.getPlaylistTracks(playlistId, spotifyAccessToken);
        } else {
            tracks = YouTubeAPI.getPlaylistTracksWithSpotifyFeatures(playlistId);
        }

        exportWithYouTubeLinks(tracks);
    }

    private static void exportToFile(List<Track> playlist) {
        System.out.print("Enter filename (without extension): ");
        String filename = scanner.nextLine().trim() + ".txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("🎬 Cinematic Playlist Export");
            writer.println("Generated: " + new Date());
            writer.println("=" + "=".repeat(50));
            writer.println();

            for (int i = 0; i < playlist.size(); i++) {
                Track track = playlist.get(i);
                writer.printf("%d. %s - %s\n", i + 1, track.getName(), track.getArtist());
                writer.printf("   Mood: %s | Tempo: %.0f BPM | Energy: %.2f | Valence: %.2f\n",
                        track.getMood(), track.getTempo(), track.getEnergy(), track.getValence());
                writer.println();
            }

            System.out.println("✓ Playlist exported to " + filename);
        } catch (IOException e) {
            System.err.println("❌ Error exporting playlist: " + e.getMessage());
        }
    }

    private static void exportWithYouTubeLinks(List<Track> playlist) {
        System.out.print("Enter filename (without extension): ");
        String filename = scanner.nextLine().trim() + "_youtube.txt";

        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("🎬 Cinematic Playlist with YouTube Links");
            writer.println("Generated: " + new Date());
            writer.println("=" + "=".repeat(50));
            writer.println();

            System.out.println("🔍 Finding YouTube links...");
            for (int i = 0; i < playlist.size(); i++) {
                Track track = playlist.get(i);
                writer.printf("%d. %s - %s\n", i + 1, track.getName(), track.getArtist());

                String youtubeLink = YouTubeAPI.getYouTubeLink(
                        track.getName(), track.getArtist());
                if (youtubeLink != null) {
                    writer.println("   🎥 " + youtubeLink);
                } else {
                    writer.println("   ❌ YouTube link not found");
                }
                writer.println();

                // Progress indicator
                System.out.print(".");
                if ((i + 1) % 10 == 0) {
                    System.out.println(" " + (i + 1) + "/" + playlist.size());
                }
            }
            System.out.println("\n✓ Playlist with YouTube links exported to " + filename);
        } catch (IOException e) {
            System.err.println("❌ Error exporting playlist: " + e.getMessage());
        }
    }
}
