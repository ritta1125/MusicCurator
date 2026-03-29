/*
Idea:
- smooth transition b/ songs(e.g. ending note == starting note)
- song choice/sort by mood

Functions:
- sort/make playlist
    - by transition
    - by mood
    - by photo
- analyze song
    - Tempo (BPM): Faster songs for hype moods, slower for chill vibes.
    - Key & Mode: Major keys for happy moods, minor keys for sadder vibes.
    - Energy Levels: High energy for workouts, low energy for relaxation.
    - Danceability: How easy it is to groove to the track.

-
 */

import java.io.*;
import java.util.*;
import okhttp3.*;
import com.google.gson.JsonObject;
import okhttp3.*;
import com.google.gson.JsonObject;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class Main{
    public static void main(String[] args) throws IOException {
        BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter pw = new PrintWriter(System.out);

        pw.println("Welcome!!");

    }
}

public class SpotifyAuth {
    private static final String CLIENT_ID = "your_client_id";
    private static final String CLIENT_SECRET = "your_client_secret";

    public static String getAccessToken() throws Exception {
        OkHttpClient client = new OkHttpClient();

        RequestBody body = new FormBody.Builder()
                .add("grant_type", "client_credentials")
                .build();

        Request request = new Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .addHeader("Authorization", "Basic " +
                        java.util.Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes()))
                .post(body)
                .build();

        Response response = client.newCall(request).execute();
        String jsonResponse = response.body().string();

        JsonObject jsonObject = new com.google.gson.JsonParser().parse(jsonResponse).getAsJsonObject();
        return jsonObject.get("access_token").getAsString();
    }
}

public class SpotifyAPI {
    private static final String BASE_URL = "https://api.spotify.com/v1/";

    public static JsonObject getTrackFeatures(String trackId, String accessToken) throws Exception {
        OkHttpClient client = new OkHttpClient();

        Request request = new Request.Builder()
                .url(BASE_URL + "audio-features/" + trackId)
                .addHeader("Authorization", "Bearer " + accessToken)
                .build();

        Response response = client.newCall(request).execute();
        String jsonResponse = response.body().string();

        return new com.google.gson.JsonParser().parse(jsonResponse).getAsJsonObject();
    }

    public static void main(String[] args) throws Exception {
        String accessToken = SpotifyAuth.getAccessToken();
        JsonObject trackFeatures = getTrackFeatures("track_id_here", accessToken);

        System.out.println("Tempo: " + trackFeatures.get("tempo").getAsDouble());
        System.out.println("Key: " + trackFeatures.get("key").getAsInt());
        System.out.println("Valence: " + trackFeatures.get("valence").getAsDouble());
        System.out.println("Energy: " + trackFeatures.get("energy").getAsDouble());
    }
}

public class PlaylistSorter {
    static class Song {
        String name;
        double tempo;
        int key;
        double energy;

        public Song(String name, double tempo, int key, double energy) {
            this.name = name;
            this.tempo = tempo;
            this.key = key;
            this.energy = energy;
        }

        @Override
        public String toString() {
            return name + " (Tempo: " + tempo + ", Key: " + key + ", Energy: " + energy + ")";
        }
    }

    public static void main(String[] args) {
        List<Song> songs = Arrays.asList(
                new Song("Song A", 120, 5, 0.8),
                new Song("Song B", 115, 7, 0.7),
                new Song("Song C", 130, 5, 0.9)
        );

        songs.sort(Comparator.comparingDouble(song -> song.tempo)); // Sort by tempo
        songs.forEach(System.out::println);
    }
}

public class MusicCuratorApp extends Application {
    @Override
    public void start(Stage stage) {
        TextField moodInput = new TextField();
        moodInput.setPromptText("Enter your mood (e.g., happy, chill)");

        Button generateButton = new Button("Generate Playlist");
        generateButton.setOnAction(e -> {
            String mood = moodInput.getText();
            System.out.println("Generating playlist for mood: " + mood);
            // Call your playlist generation logic here
        });

        VBox layout = new VBox(10, moodInput, generateButton);
        Scene scene = new Scene(layout, 300, 200);

        stage.setScene(scene);
        stage.setTitle("Music Curator");
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}