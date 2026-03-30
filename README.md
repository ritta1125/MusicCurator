# 🎵 MusicCurator

A playlist analysis and curation tool that connects to **Spotify**, **YouTube**, and **Apple Music**. It analyzes your playlists using audio features (energy, valence, danceability, tempo, key, mood) and rebuilds them into a cinematic energy arc — perfect for DJ sets, study sessions, workouts, or any vibe you're going for.

Available as both a **CLI tool** and a **web app**.

---

## Features

- 🎧 **Multi-platform** — Spotify, YouTube, Apple Music
- 📊 **Audio analysis** — energy, valence, danceability, tempo, key, acousticness, instrumentalness
- 🎭 **Mood classification** — 9 cinematic mood categories (Euphoric, Melancholic, Aggressive, Serene, etc.)
- 🌊 **Cinematic energy arc** — greedy algorithm that shapes your playlist into a bell-curve energy profile
- 🔗 **Camelot wheel key mixing** — harmonic compatibility scoring for smooth transitions
- 🌐 **Web UI** — dark cinematic 5-step wizard (no framework, vanilla JS)
- 💾 **Save back** — export curated playlists directly to your music platform

---

## Requirements

- Java 21+
- Gradle 8+
- API credentials (see setup below)

---

## Setup

### 1. Clone the repo

```bash
git clone https://github.com/ritta1125/MusicCurator.git
cd MusicCurator
```

### 2. Create your config file

Create a file named `config.properties` in the project root:

```properties
# Spotify — https://developer.spotify.com/dashboard
spotify.client.id=YOUR_CLIENT_ID
spotify.client.secret=YOUR_CLIENT_SECRET

# YouTube — https://console.cloud.google.com → APIs & Services → Credentials
youtube.api.key=YOUR_API_KEY

# AcoustID — https://acoustid.org/login → Applications
acoustid.api.key=YOUR_API_KEY

# Apple Music — https://developer.apple.com/account/resources/authkeys/list
apple.team.id=YOUR_TEAM_ID
apple.key.id=YOUR_KEY_ID
apple.private.key.path=/absolute/path/to/AuthKey_XXXXXX.p8
```

> `config.properties` is gitignored — your keys will never be committed.

### 3. Getting API credentials

| Platform | Where to get keys |
|----------|-------------------|
| **Spotify** | [developer.spotify.com/dashboard](https://developer.spotify.com/dashboard) → Create app → grab Client ID & Secret |
| **YouTube** | [console.cloud.google.com](https://console.cloud.google.com) → Enable YouTube Data API v3 → Create API key |
| **AcoustID** | [acoustid.org](https://acoustid.org/login) → Register application |
| **Apple Music** | [developer.apple.com](https://developer.apple.com/account/resources/authkeys/list) → Create a MusicKit key → download `.p8` file |

---

## Running

### Web app (recommended)

```bash
./gradlew run --args="--web"
```

Then open [http://localhost:8080](http://localhost:8080) in your browser.

The web UI walks you through 5 steps:
1. **Connect** — choose your platform and authenticate
2. **Analyze** — paste a playlist URL/ID and scan it
3. **Results** — view mood distribution and track details
4. **Curate** — pick a mood and arc style, generate the curated order
5. **Save** — export back to your music platform

### CLI

```bash
./gradlew run
```

Follow the interactive prompts in your terminal.

### Custom port

```bash
./gradlew run --args="--web --port=9090"
```

---

## Project Structure

```
src/main/java/com/musicCurator/
├── Main.java                  # Entry point (CLI + web flag)
├── api/
│   ├── SpotifyAPI.java        # Spotify Web API client
│   ├── SpotifyBridge.java     # Enriches non-Spotify tracks with Spotify audio features
│   ├── YouTubeAPI.java        # YouTube Data API v3 client
│   └── AppleMusicAPI.java     # Apple Music / MusicKit API client
├── curator/
│   ├── PlaylistCurator.java   # Energy arc + harmonic transition algorithms
│   └── MoodClassifier.java    # Classifies tracks into 9 mood categories
├── model/
│   └── Track.java             # Track data model
├── utils/
│   └── AcoustID.java          # AcoustID fingerprint lookup
└── web/
    ├── WebServer.java          # Built-in HTTP server (no external server needed)
    ├── ApiHandler.java         # REST API routes
    └── StaticHandler.java      # Serves the frontend
src/main/resources/
└── static/
    └── index.html             # Single-page web UI
```

---

## How the curation works

1. **Fetch** — pulls all tracks from your playlist via the platform API
2. **Enrich** — non-Spotify tracks are matched on Spotify to get audio features
3. **Classify** — each track is assigned a mood based on energy + valence + tempo
4. **Arc** — a greedy algorithm assigns tracks to positions along a bell-curve energy profile, peaking at ~70% energy at the midpoint
5. **Transitions** — Camelot wheel key compatibility + BPM proximity scoring for smooth track-to-track flow

---

## Tech stack

- **Language:** Java 21
- **Build:** Gradle (Kotlin DSL)
- **HTTP client:** OkHttp 4
- **JSON:** Gson
- **YouTube:** Google API Client library
- **Web server:** `com.sun.net.httpserver.HttpServer` (JDK built-in, no extra dependencies)
- **Frontend:** Vanilla JS, no framework

---

## Notes

- Apple Music requires an [Apple Developer account](https://developer.apple.com) ($99/year) to generate a MusicKit private key.
- YouTube playlist analysis uses Spotify as a secondary lookup to get audio features (tracks are matched by title/artist search).
- The `.p8` private key file for Apple Music should be stored securely and never committed to git (already covered by `.gitignore`).
