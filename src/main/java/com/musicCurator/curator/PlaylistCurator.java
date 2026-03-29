package com.musicCurator.curator;

import com.musicCurator.model.Track;

import java.util.*;

public class PlaylistCurator {

    // Camelot wheel for key compatibility
    private static final Map<Integer, List<Integer>> KEY_COMPATIBILITY = new HashMap<>();
    static {
        // Initialize key compatibility based on Camelot wheel
        KEY_COMPATIBILITY.put(0, Arrays.asList(0, 5, 7)); // C major
        KEY_COMPATIBILITY.put(1, Arrays.asList(1, 6, 8)); // C# major
        KEY_COMPATIBILITY.put(2, Arrays.asList(2, 7, 9)); // D major
        KEY_COMPATIBILITY.put(3, Arrays.asList(3, 8, 10)); // D# major
        KEY_COMPATIBILITY.put(4, Arrays.asList(4, 9, 11)); // E major
        KEY_COMPATIBILITY.put(5, Arrays.asList(5, 10, 0)); // F major
        KEY_COMPATIBILITY.put(6, Arrays.asList(6, 11, 1)); // F# major
        KEY_COMPATIBILITY.put(7, Arrays.asList(7, 0, 2)); // G major
        KEY_COMPATIBILITY.put(8, Arrays.asList(8, 1, 3)); // G# major
        KEY_COMPATIBILITY.put(9, Arrays.asList(9, 2, 4)); // A major
        KEY_COMPATIBILITY.put(10, Arrays.asList(10, 3, 5)); // A# major
        KEY_COMPATIBILITY.put(11, Arrays.asList(11, 4, 6)); // B major
    }

    public static List<Track> createCinematicPlaylist(List<Track> tracks, String targetMood, int playlistLength) {
        List<Track> playlist = new ArrayList<>();
        List<Track> availableTracks = new ArrayList<>(tracks);

        // Filter tracks by mood if specified
        if (targetMood != null && !targetMood.isEmpty()) {
            availableTracks.removeIf(track -> !track.getMood().toLowerCase().contains(targetMood.toLowerCase()));
        }

        if (availableTracks.isEmpty()) {
            System.out.println("No tracks found for mood: " + targetMood);
            return playlist;
        }

        // Start with a track that matches the mood well
        Track currentTrack = selectStartingTrack(availableTracks, targetMood);
        playlist.add(currentTrack);
        availableTracks.remove(currentTrack);

        // Build the rest of the playlist
        while (playlist.size() < playlistLength && !availableTracks.isEmpty()) {
            Track nextTrack = selectNextTrack(currentTrack, availableTracks);
            if (nextTrack != null) {
                playlist.add(nextTrack);
                availableTracks.remove(nextTrack);
                currentTrack = nextTrack;
            } else {
                break;
            }
        }

        return playlist;
    }

    private static Track selectStartingTrack(List<Track> tracks, String targetMood) {
        // For cinematic playlists, prefer tracks with moderate tempo and energy
        return tracks.stream()
                .min(Comparator.comparingDouble(t ->
                        Math.abs(t.getTempo() - 100) + Math.abs(t.getEnergy() - 0.5)))
                .orElse(tracks.getFirst());
    }

    private static Track selectNextTrack(Track currentTrack, List<Track> availableTracks) {
        return availableTracks.stream()
                .min(Comparator.comparingDouble(t -> calculateTransitionScore(currentTrack, t)))
                .orElse(null);
    }

    private static double calculateTransitionScore(Track current, Track next) {
        double score = 0;

        // Tempo difference (prefer gradual changes)
        double tempoDiff = Math.abs(current.getTempo() - next.getTempo());
        score += tempoDiff * 0.3;

        // Key compatibility
        if (!KEY_COMPATIBILITY.getOrDefault(current.getKey(), List.of()).contains(next.getKey())) {
            score += 20; // Penalty for incompatible keys
        }

        // Energy flow (prefer smooth transitions)
        double energyDiff = Math.abs(current.getEnergy() - next.getEnergy());
        score += energyDiff * 10;

        // Mood consistency (prefer similar moods)
        if (!current.getMood().equals(next.getMood())) {
            score += 5;
        }

        return score;
    }

    public static void createMoodFlow(List<Track> playlist) {
        // Sort playlist to create a cinematic arc: calm -> build up -> climax -> resolution
        // Using greedy assignment: for each arc position pick the closest available track
        int size = playlist.size();
        if (size == 0) return;

        List<Track> available = new ArrayList<>(playlist);
        List<Track> result = new ArrayList<>(size);

        for (int i = 0; i < size; i++) {
            double target = calculateTargetEnergy(i, size);
            Track best = available.stream()
                    .min(Comparator.comparingDouble(t -> Math.abs(t.getEnergy() - target)))
                    .orElse(null);
            if (best != null) {
                result.add(best);
                available.remove(best);
            }
        }

        playlist.clear();
        playlist.addAll(result);
    }

    private static double calculateTargetEnergy(int position, int totalTracks) {
        double progress = (double) position / totalTracks;

        // Create a cinematic arc: start low, peak at 70%, end low
        if (progress < 0.7) {
            return 0.3 + (progress / 0.7) * 0.5;
        } else {
            return 0.8 - ((progress - 0.7) / 0.3) * 0.5;
        }
    }
}
