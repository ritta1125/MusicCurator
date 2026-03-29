package com.musicCurator.model;

public class Track {
    private final String id;
    private final String name;
    private final String artist;
    private double tempo;
    private int key;
    private double energy;
    private double valence;
    private double danceability;
    private double acousticness;
    private double instrumentalness;
    private int mode; // 0 = minor, 1 = major
    private String mood;

    // Constructor
    public Track(String id, String name, String artist) {
        this.id = id;
        this.name = name;
        this.artist = artist;
    }

    // Getters and setters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getArtist() { return artist; }
    public double getTempo() { return tempo; }
    public void setTempo(double tempo) { this.tempo = tempo; }
    public int getKey() { return key; }
    public void setKey(int key) { this.key = key; }
    public double getEnergy() { return energy; }
    public void setEnergy(double energy) { this.energy = energy; }
    public double getValence() { return valence; }
    public void setValence(double valence) { this.valence = valence; }
    public double getDanceability() { return danceability; }
    public void setDanceability(double danceability) { this.danceability = danceability; }
    public double getAcousticness() { return acousticness; }
    public void setAcousticness(double acousticness) { this.acousticness = acousticness; }
    public double getInstrumentalness() { return instrumentalness; }
    public void setInstrumentalness(double instrumentalness) { this.instrumentalness = instrumentalness; }
    public int getMode() { return mode; }
    public void setMode(int mode) { this.mode = mode; }
    public String getMood() { return mood; }
    public void setMood(String mood) { this.mood = mood; }

    @Override
    public String toString() {
        return String.format("%s - %s (Tempo: %.1f, Key: %d, Energy: %.2f, Mood: %s)",
                name, artist, tempo, key, energy, mood);
    }
}
