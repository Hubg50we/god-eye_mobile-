# 🛰️ God's Eye View — Android Mobile

[![Build Android Mobile APK](https://github.com/Hubg50we/god-eye_mobile-/actions/workflows/build-apk.yml/badge.svg)](https://github.com/Hubg50we/god-eye_mobile-/actions/workflows/build-apk.yml)
[![Latest APK Release](https://img.shields.io/github/v/release/Hubg50we/god-eye_mobile-?color=brightgreen&label=Download%20APK)](https://github.com/Hubg50we/god-eye_mobile-/releases/latest)
[![License: MIT](https://img.shields.io/badge/License-MIT-teal.svg)](LICENSE)
[![Platform: Android](https://img.shields.io/badge/Platform-Android_7.0+-3DDC84.svg?logo=android&logoColor=white)](https://developer.android.com)
[![Cesium](https://img.shields.io/badge/CesiumJS-WebGL_2.0-blue.svg)](https://cesium.com)

**God's Eye View Mobile** is the native Android application port of [Bilawal Sidhu's God's Eye View](https://github.com/bilawalsidhu/gods-eye-view) — an open-source real-time spatial intelligence simulator and interactive 3D globe console running in high-performance WebGL on mobile devices.

### 📥 [Direct Download APK (v1.5.0)](https://github.com/Hubg50we/god-eye_mobile-/releases/download/v1.5.0/godseye-android.apk)

---

## 🌍 Real 3D Earth Map Engine (Everything Included)

God's Eye View Mobile features a complete **Photorealistic 3D Earth WebGL Engine**:
* **🛰️ Real High-Res Earth Textures**: Photorealistic NASA Blue Marble & Esri satellite imagery with ocean specular reflections, day/night city lights, and tactical contours.
* **✨ Atmospheric Rayleigh Scattering & 3D Clouds**: Custom WebGL atmosphere rim shader enveloping the planet and an independently rotating semi-transparent 3D cloud layer.
* **✈️ Real 3D Aircraft & Kinematic Meshes**: Directional aircraft models flying at accurate scaled altitudes with live velocity heading vectors.
* **💥 Volumetric 3D Seismic Shockwaves**: Pulsing 3D rings erupting from USGS earthquake hypocenters across global fault lines.
* **🛰️ 3D Space Station (ISS) & Orbital Rings**: Full 3D modeled satellite orbiting above the Earth with an inclination trajectory ring.
* **🌌 Cosmic Deep Space**: Dynamic starfield and celestial illumination from directional solar lighting.

---

## 🤖 Full App Access Conversational AI Assistant (AEGIS)

God's Eye View Mobile features a complete **on-device Conversational AI Assistant** with full autonomous control of the application:
* **🎙️ Voice Interaction & Audio Synthesizer**: Talk directly to the AI Assistant or use quick chips. Includes real-time audio waveform visualizer and synthesized tactical sci-fi audio cues.
* **🌍 Autonomous Flight to Any Location (Geocoding)**: Tell AEGIS *"Fly to Tokyo"*, *"Go to Paris"*, or *"Take me to Honolulu"* — the Assistant geocodes the place via OpenStreetMap/Photon and animates the 3D globe camera there.
* **🪐 Full Camera & System Control**: Maneuver zoom levels (*"Zoom in"*, *"Zoom out"*), toggle planetary auto-orbit (*"Spin the globe"*), reset orientations (*"Reset view"*), and engage device hardware WakeLock (*"Keep screen awake"*).
* **📋 Native OS Sitrep Export & Sharing**: Instruct the Assistant to *"Copy sitrep"* or *"Share sitrep"* to copy multi-domain intelligence to your Android clipboard or launch the native Android share sheet.
* **🧠 100% On-Device Neural Net**: Instant classification (<1ms) with zero cloud tokens, zero latency, and zero external API dependencies.

---

## 📱 Mobile Key Features

* **⚡ Native Android Acceleration:** Full hardware-accelerated WebGL 2.0 runtime powered by AndroidX WebKit.
* **🕶️ Immersive Fullscreen Tactical HUD:** Edge-to-edge display layout supporting punch-hole camera cutouts, notches, and transient navigation gestures.
* **📍 GPS "My Location" Integration:** Direct native hardware GPS bridge to center the 3D globe camera on your current real-world coordinates.
* **🎙️ Voice Control Bridge:** Authoritative runtime permissions bridge for microphone capture and OpenAI Realtime / WebRTC voice tools.
* **🛰️ Live Telemetry Data Feeds:**
  * **Aircraft:** Live ADS-B flight tracking via OpenSky Network and adsb.lol.
  * **Satellites:** Thousands of active satellites computed in real time via orbital SGP4 propagation (`satellite.js`).
  * **Maritime Vessels:** Global ship traffic via live AIS data feeds.
  * **Environmental:** Real-time earthquakes (USGS), active wildfires (NASA FIRMS), and global weather radar.
  * **Live Surveillance & CCTV:** Public webcams and streaming video layers.
* **⚙️ Tactical Config & Server Switcher:**
  * Quickly switch between **Offline Tactical Mode**, **Local Dev Server** (`http://10.0.2.2:5173`), or **Custom Cloud Deployment** (e.g. Vercel, Render, or VPS).
  * In-app configuration for **Cesium Ion Token** and **Google Maps Photorealistic 3D Tiles API Key**.
* **🔋 Battery & Tactical Controls:** WakeLock toggle to keep the situational awareness display active without screen timeouts.

---

## 🕹️ Mobile Touch Gestures

| Gesture | Action |
|---|---|
| **Single-Finger Drag** | Rotate & pan the 3D Earth globe |
| **Two-Finger Pinch** | Zoom in / Zoom out |
| **Two-Finger Vertical Drag** | Pitch / tilt the camera angle |
| **Two-Finger Twist / Rotate** | Change compass heading / azimuth |
| **Tap Entity (Plane/Sat/Ship)** | Inspect live telemetry, speed, altitude & callsign |

---

## 🏗️ Architecture

```mermaid
flowchart TD
    subgraph Android Native Shell [Android Native Kotlin Layer]
        A[MainActivity.kt] --> B[WindowInsetsController - Immersive HUD]
        A --> C[Native Permissions - GPS / Mic / Network]
        A --> D[Tactical Settings Dialog - SharedPreferences]
        A --> E[GodsEyeNativeBridge]
    end

    subgraph WebGL Engine [AndroidX WebKit Engine]
        F[Hardware Accelerated WebView]
        G[WebGL 2.0 / OpenGL ES 3.0]
        H[WebChromeClient - Auto Permissions]
    end

    subgraph Spatial Intelligence App [God's Eye View Core]
        I[CesiumJS 3D Photorealistic Globe]
        J[Vue 3 & Pinia Tactical HUD]
        K[Live Feeds: OpenSky / AIS / Satellites / USGS]
    end

    Android Native Shell <--> WebGL Engine
    WebGL Engine <--> Spatial Intelligence App
```

---

## 🚀 How to Get and Run the App

### Option 1: Direct APK Download (Ready to Install)
* **Direct Repo Download**: You can download [`godseye-android.apk`](release_bin/godseye-android.apk?raw=true) directly from the `release_bin/` folder in this repository!
* **GitHub Releases**: Go to [**Releases**](https://github.com/Hubg50we/god-eye_mobile-/releases) and download the latest release APK.
* **GitHub Actions**: Go to the [**Actions**](https://github.com/Hubg50we/god-eye_mobile-/actions) tab, click the latest workflow run, and download the `godseye-android-apk` artifact.
2. Click the latest **Build Android Mobile APK** workflow run.
3. Under **Artifacts**, download `GodsEyeView-Android-Debug-APK.zip`.
4. Unzip and install `app-debug.apk` directly onto your Android device!

---

### Option 2: Build in Android Studio
1. Clone this repository:
   ```bash
   git clone https://github.com/Hubg50we/god-eye_mobile-.git
   ```
2. Open **Android Studio**.
3. Select **Open an Existing Project** and navigate to the `android/` directory inside this repository.
4. Let Gradle sync dependencies.
5. Connect your Android phone (or launch an Android Emulator with API 26+) and click **Run (Shift + F10)**.

---

### Option 3: Command-Line Build (Gradle)

On Linux / macOS:
```bash
cd android
chmod +x gradlew
./gradlew assembleDebug
```

On Windows (PowerShell):
```powershell
cd android
.\gradlew.bat assembleDebug
```

The resulting APK will be generated at:
`android/app/build/outputs/apk/debug/app-debug.apk`

---

## ⚙️ Configuration & API Keys

To unlock photorealistic 3D city buildings and custom imagery:
1. Tap the **Tactical Settings** button (gear icon) on the top right of the mobile screen.
2. Enter your:
   * **Cesium Ion Token:** Get a free token at [cesium.com](https://cesium.com/ion/)
   * **Google Maps 3D Tiles API Key:** Enable *Map Tiles API* in Google Cloud Console
3. Tap **SAVE CONFIG** to immediately re-engage the tactical uplink.

---

## 📜 Credits & License

* Original **God's Eye View** browser concept and 3D globe architecture created by [Bilawal Sidhu](https://github.com/bilawalsidhu/gods-eye-view).
* Android Mobile port and native orchestration maintained by [Hubg50we](https://github.com/Hubg50we).
* Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.
