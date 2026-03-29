package com.musicCurator.curator;

import com.musicCurator.model.Track;

public class MoodClassifier {

    public static String classifyMood(Track track) {
        double valence = track.getValence();
        double energy = track.getEnergy();
        double acousticness = track.getAcousticness();
        double instrumentalness = track.getInstrumentalness();
        double danceability = track.getDanceability();

        // Cinematic moods
        if (instrumentalness > 0.7 && energy < 0.4) {
            return "Cinematic Ambient";
        } else if (energy > 0.7 && valence < 0.3) {
            return "Epic/Intense";
        } else if (valence < 0.3 && energy < 0.3 && acousticness > 0.5) {
            return "Melancholic/Emotional";
        } else if (valence > 0.7 && energy > 0.6) {
            return "Uplifting/Triumphant";
        } else if (energy > 0.6 && valence > 0.4 && valence < 0.6) {
            return "Adventure/Journey";
        } else if (acousticness > 0.7 && valence > 0.5) {
            return "Peaceful/Serene";
        } else if (energy < 0.4 && valence > 0.3 && valence < 0.6) {
            return "Contemplative";
        } else if (danceability > 0.7) {
            return "Rhythmic/Groovy";
        } else {
            return "Neutral/Transitional";
        }
    }
}
