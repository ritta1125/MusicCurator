package com.musicCurator.api;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.*;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.youtube.*;
import com.google.api.services.youtube.model.*;
import com.musicCurator.model.Track;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class YouTubeAPI {
    private static final String APPLICATION_NAME = "MusicCurator";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Arrays.asList(
            YouTubeScopes.YOUTUBE_READONLY,
            YouTubeScopes.YOUTUBE
    );

    private static YouTube youtubeService;
    private static String API_KEY;
    public static final Logger logger = Logger.getLogger(YouTubeAPI.class.getName());

    static {
        loadConfig();
    }

    private static void loadConfig() {
        Properties prop = new Properties();
        try (InputStream input = YouTubeAPI.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                prop.load(input);
                API_KEY = prop.getProperty("youtube.api.key");
            }
        } catch (IOException e) {
            System.err.println("Error loading config: " + e.getMessage());
        }
    }

    public static void initialize() throws Exception {
        if (youtubeService == null) {
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            Credential credential = getCredentials(HTTP_TRANSPORT);

            youtubeService = new YouTube.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();
        }
    }

    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT)
            throws IOException {
        // Load client secrets
        InputStream in = YouTubeAPI.class.getClassLoader()
                .getResourceAsStream("youtube_secret.json");
        if (in == null) {
            // Try alternate location
            in = new FileInputStream("youtube_secret.json");
        }

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(
                JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();

        LocalServerReceiver receiver = new LocalServerReceiver.Builder()
                .setPort(8888).build();
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }

    public static List<Track> getPlaylistTracksWithSpotifyFeatures(String playlistId) {
        List<Track> tracks = new ArrayList<>();

        try {
            YouTube.PlaylistItems.List request = youtubeService.playlistItems()
                    .list(Arrays.asList("snippet", "contentDetails"));
            request.setPlaylistId(playlistId);
            request.setMaxResults(50L);

            String nextPageToken = "";
            do {
                if (!nextPageToken.isEmpty()) {
                    request.setPageToken(nextPageToken);
                }

                PlaylistItemListResponse response = request.execute();
                List<PlaylistItem> items = response.getItems();

                for (PlaylistItem item : items) {
                    String videoId = item.getContentDetails().getVideoId();
                    String title = item.getSnippet().getTitle();
                    String channelTitle = item.getSnippet().getVideoOwnerChannelTitle();

                    // If channel title is null, try getting it from snippet
                    if (channelTitle == null || channelTitle.isEmpty()) {
                        channelTitle = item.getSnippet().getChannelTitle();
                    }

                    // Get Spotify features for this video - NOW WITH 3 PARAMETERS
                    Track track = SpotifyBridge.searchAndEnrichTrack(videoId, title, channelTitle);
                    tracks.add(track);
                }

                nextPageToken = response.getNextPageToken();
            } while (nextPageToken != null);

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error fetching YouTube playlist: " + e.getMessage());
        }

        return tracks;
    }

    public static String searchYouTubeVideo(String query) throws IOException {
        YouTube.Search.List search = youtubeService.search()
                .list(Arrays.asList("id", "snippet"));
        search.setKey(API_KEY);
        search.setQ(query);
        search.setType(List.of("video"));
        search.setMaxResults(1L);

        SearchListResponse response = search.execute();
        List<SearchResult> results = response.getItems();

        if (results != null && !results.isEmpty()) {
            return results.getFirst().getId().getVideoId();
        }

        return null;
    }

    public static String getYouTubeLink(String trackName, String artistName) {
        if (youtubeService == null) return null;
        try {
            String query = trackName + " " + artistName + " official audio";
            String videoId = searchYouTubeVideo(query);

            if (videoId != null) {
                return "https://www.youtube.com/watch?v=" + videoId;
            }
        } catch (Exception e) {
            // Silent fail for batch operations
        }
        return null;
    }

    public static String createPlaylist(String playlistName, String description,
                                        List<Track> tracks) {
        try {
            // Create playlist
            Playlist playlist = new Playlist();
            PlaylistSnippet snippet = new PlaylistSnippet();
            snippet.setTitle(playlistName);
            snippet.setDescription(description);
            playlist.setSnippet(snippet);

            PlaylistStatus status = new PlaylistStatus();
            status.setPrivacyStatus("private");
            playlist.setStatus(status);

            YouTube.Playlists.Insert playlistInsert = youtubeService.playlists()
                    .insert(Arrays.asList("snippet", "status"), playlist);

            Playlist response = playlistInsert.execute();
            String playlistId = response.getId();

            // Add videos
            int added = 0;
            for (Track track : tracks) {
                try {
                    String videoId = searchYouTubeVideo(
                            track.getName() + " " + track.getArtist());

                    if (videoId != null) {
                        addVideoToPlaylist(playlistId, videoId);
                        added++;
                        System.out.printf("\rAdding tracks: %d/%d", added, tracks.size());
                        Thread.sleep(100); // Rate limiting
                    }
                } catch (Exception e) {
                    // Continue with next track
                }
            }

            System.out.println("\n✓ Added " + added + " tracks to playlist");
            return playlistId;

        } catch (Exception e) {
            System.err.println("Error creating playlist: " + e.getMessage());
            return null;
        }
    }

    private static void addVideoToPlaylist(String playlistId, String videoId)
            throws IOException {
        PlaylistItem playlistItem = new PlaylistItem();
        PlaylistItemSnippet snippet = new PlaylistItemSnippet();
        snippet.setPlaylistId(playlistId);

        ResourceId resourceId = new ResourceId();
        resourceId.setKind("youtube#video");
        resourceId.setVideoId(videoId);
        snippet.setResourceId(resourceId);

        playlistItem.setSnippet(snippet);

        youtubeService.playlistItems()
                .insert(List.of("snippet"), playlistItem)
                .execute();
    }
}
