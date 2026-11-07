# AI_BOT 2.0 🤖⚡
> **Futuristic Android Companion App & ESP32 Smart Robot Ecosystem**

![Android](https://img.shields.io/badge/Android-14%2B-10B981?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7C3AED?style=for-the-badge&logo=kotlin&logoColor=white)
![ESP32](https://img.shields.io/badge/ESP32-Arduino%20C%2B%2B-00E5FF?style=for-the-badge&logo=espressif&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-EF4444?style=for-the-badge)

---

## ✨ Features Overview

### 🤖 Expressive Robot Face & Visual Animations
* **5 Expressions**: `NORMAL` (Neon Cyan), `SMILING` (Emerald Green), `CRYING` (Royal Blue), `SHIVERING` (Ice Blue), and `ANGRY` (Crimson Red).
* **Lip-Sync Audio Morphing**: Anime-style mouth morphing synchronized with Friday's TTS voice output.
* **Music Headphone Visuals**: Robot wears glowing neon studio headphones and closes eyes (`^ ^`) in blissful music mode.
* **Synchronized Color Theme**: All HUD boxes (`Digital Clock`, `Weather`, `Music Player`, `Environment Pills`, `Background Glow`) dynamically update their glowing liquid-flow borders and accent text in **100% unison** with the active expression mode.

### 🎵 Cyberpunk Music Player HUD
* **Automatic System Media Sync**: Automatically hooks into active background playback (YouTube Music, Spotify, Apple Music, Wynk, Local Audio).
* **Live Album Art Cover Photo**: Displays real-time album cover artwork directly inside Friday's Music HUD.
* **Interactive Touch Seek Bar**: Touch/drag anywhere on the slider to seek directly to any timestamp.
* **Synchronized Real-Time Lyrics**: Rhythmical lyric line box scrolling in sync with track position.
* **Hardware Media Controls**: Crimson Red **`⏮ Previous`**, Mode Accent **`⏯ Play/Pause`**, and Emerald Green **`⏭ Next`** buttons.


### ⏰ Digital Clock & System Alarm Sync
* **Ultra-Huge Digits**: Massive 50sp extra-bold time numbers filling the left portrait box.
* **Header AM/PM Badge**: Automatic AM/PM pill badge in the clock header.
* **Live Mobile Alarm Sync**: Queries Android `AlarmManager.nextAlarmClock` in real-time (`⏰ Alarm: 07:30 AM`).
* **1-Tap Alarm Creator**: Tap the alarm bar to open an in-app TimePicker that sets an alarm in Android's native Alarm Clock app.


### 🎙 Friday Voice Assistant & Female TTS
* **Offline Speech Recognition**: Built-in voice command processor for temperature, weather, time, and expression switches.
* **Female TTS Output**: Customized Android TextToSpeech engine with forced female voice override (`en-us-x-sfg-local`).

### 🔕 Distraction-Free DND Mode
* **Notification Suppression**: Automatically enables Priority Do Not Disturb (DND) mode while the app is active, keeping notification popups and sounds hidden. Restores original settings when exiting.


### ⛅ Global Weather HUD
* **OpenWeatherMap Integration**: Displays live temperatures, weather conditions, high/low range, and humidity for dual cities (e.g. Guwahati & Mumbai).


### 🌐 Triple Connection Engine (ESP32)
* **📶 BLE GATT Server**: Direct Bluetooth Low Energy communication via Service UUID `0000FFE0-0000-1000-8000-00805F9B34FB`.
* **🔌 USB OTG Serial**: Direct wired serial communication at 115200 Baud using Android USB Host API.
* **📡 Wi-Fi AP & mDNS**: Connects to ESP32 Access Point (`192.168.4.1`) or mDNS hostname (`friday-bot.local`).
## 💻 Software & ESP32 Code

### ESP32 Firmware (`AI_Bot_ESP32_DHT22.ino`)
The firmware handles dual-mode communication over **USB Serial (115200 Baud)** and **Wi-Fi WebServer (Port 80)**.

#### Flash the ESP32:
1. Open [`D:\AI bot Ino\AI_Bot_ESP32_DHT22\AI_Bot_ESP32_DHT22.ino`](D:/AI%20bot%20Ino/AI_Bot_ESP32_DHT22/AI_Bot_ESP32_DHT22.ino) in **Arduino IDE**.
2. Install the **DHT sensor library** (`DHT.h`).
3. Select Board: **ESP32 Dev Module** and Upload.

> 💡 **Tip**: If using the blue **DHT11** sensor instead of white DHT22, change `#define DHT_TYPE DHT22` to `#define DHT_TYPE DHT11` at line 28 of the `.ino` sketch.

---

## 🛠️ Project Architecture

### Required Components
1. **ESP32 Development Board** (ESP-WROOM-32 / NodeMCU ESP32)
2. **DHT22** (or DHT11) Temperature & Humidity Sensor
3. **USB Type-C OTG Cable** (Type-C to Type-C or OTG Adapter)
4. **Android Smartphone** (Android 11 / API 30 or higher)

### Wiring Diagram
| DHT22 Sensor Pin | ESP32 Pin |
| :--- | :--- |
| **VCC** | `3.3V` or `5V` |
| **DATA** | `GPIO 4` |
| **GND** | `GND` |
```
AI_BOT20/
├── app/src/main/
│   ├── java/com/example/ai_bot20/
│   │   └── MainActivity.kt        # Main Compose UI, ViewModels, System Media Sync & Voice Manager
│   └── AndroidManifest.xml        # DND, Audio, Bluetooth, USB & Notification Listener Permissions
├── firmware/
│   └── AI_Bot_ESP32_DHT22.ino     # ESP32 C++ Sketch (BLE GATT, USB OTG, Wi-Fi AP, DHT11/22)
├── build.gradle.kts
└── settings.gradle.kts
```

---



## 🚀 Getting Started (Android App)

### Prerequisites
* **Android Studio**: Jellyfish / Koala or newer
* **Android Device**: Android 8.0 (API 26) or higher
* **ESP32 Board**: ESP32 Dev Module (WROOM-32)
* **Arduino IDE**: 2.0+ with ESP32 board package installed

### 1. ESP32 Firmware Setup
1. Open `firmware/AI_Bot_ESP32_DHT22.ino` in Arduino IDE.
2. Install libraries: `DHT sensor library`, `Adafruit Unified Sensor`, `ESPAsyncWebServer` (optional).
3. Select board: **ESP32 Dev Module**.
4. Upload firmware to ESP32 via USB.

### 2. Android App Build
1. Clone this repository:
   ```bash
   git clone https://github.com/your-username/AI_BOT2.0.git
   ```
2. Open the project in **Android Studio**.
3. Build and assemble Debug APK:
   ```bash
   ./gradlew :app:assembleDebug
   ```
4. Deploy to your Android device.

---

## 📲 Permissions Required

```
+--------------------------------------------------------------+
| 🟢 ESP32 Connected (USB OTG)                      📡 Settings|
|                                                              |
| +--------------+              +------------------+           |
| | DIGITAL CLOCK|              |   ROBOT FACE     |           |
| | 08:28:37 PM  |              |   (👀 Animated)  |           |
| +--------------+              +------------------+           |
|                                                              |
|  🗣 "What is the room temperature?"                          |
|                                                              |
| [⚡ 88%]  [🌡 28.4°C]  [💧 63%]                  🎙 [ASK FRIDAY]|
| [Normal]  [Smile]  [Cry]  [Shiver]  [Angry]                  |
+--------------------------------------------------------------+
```
![Project Screenshot](Dashboard_SS.png)

* `ACCESS_NOTIFICATION_POLICY`: Auto-enables Distraction-Free DND mode.
* `BIND_NOTIFICATION_LISTENER_SERVICE`: Real-time YouTube Music / Spotify track & album art sync.
* `BLUETOOTH_SCAN` & `BLUETOOTH_CONNECT`: BLE scanning and GATT connection.
* `RECORD_AUDIO`: Friday voice assistant speech recognition.
* `READ_MEDIA_AUDIO` / `READ_EXTERNAL_STORAGE`: Local music file playback.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

