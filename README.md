# 🤖 AI_BOT2.0 - Friday AI Companion & Robot Face

> A futuristic, interactive Android AI Companion & Robot Face interface powered by **Jetpack Compose** and **ESP32 Microcontroller** with real-time environment sensing, animated expressions, female voice assistant (Friday TTS), and dual USB OTG / Wi-Fi connectivity.

---

## ✨ Features

- **🎭 Futuristic Animated Robot Face**:
  - Dynamic expression state transitions: **Normal**, **Smiling**, **Crying** (animated tears), **Shivering** (haptic shake effect), and **Angry** (glowing brow overlay).
  - Lifelike floating idle animation, breathing aura glow, eye blinks, and glass reflection overlays.

- **🎙 Friday AI Voice Companion (TTS)**:
  - Female voice manager providing spoken responses for room temperature, humidity, clock time, battery status, and expression modes.
  - **Quick Ask Dialog**: Instant tap-to-ask options to query environmental data or switch robot expressions without relying on external internet servers.
  - Auto-disappearing (2-second) transcript feedback pill on user interactions.

- **🔌 Dual Connectivity Modes**:
  - **USB OTG Direct Cable**: Plug the ESP32 directly into your phone's USB Type-C port for instant plug-and-play communication (powers ESP32 directly from the phone battery while keeping mobile data / home Wi-Fi 100% active).
  - **Wi-Fi WebServer & AP Mode**: ESP32 Access Point mode (`Techno` / `techno123` at `192.168.4.1`), mDNS local hostname (`friday-bot.local`), and 1-tap UDP Auto-Discovery.

- **⏰ Digital Table Clock & Environment HUD**:
  - Real-time digital clock HUD display.
  - Live temperature (`°C`), humidity (`%`), and ESP32 battery indicators.

- **🔄 Rotation-Safe Architecture**:
  - Built using Android **ViewModel** and configuration-safe handlers to ensure USB OTG and Wi-Fi connections never disconnect when rotating the phone screen.

---

## 🛠 Hardware Setup

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

---

## 💻 Software & ESP32 Code

### ESP32 Firmware (`AI_Bot_ESP32_DHT22.ino`)
The firmware handles dual-mode communication over **USB Serial (115200 Baud)** and **Wi-Fi WebServer (Port 80)**.

#### Flash the ESP32:
1. Open [`D:\AI bot Ino\AI_Bot_ESP32_DHT22\AI_Bot_ESP32_DHT22.ino`](D:/AI%20bot%20Ino/AI_Bot_ESP32_DHT22/AI_Bot_ESP32_DHT22.ino) in **Arduino IDE**.
2. Install the **DHT sensor library** (`DHT.h`).
3. Select Board: **ESP32 Dev Module** and Upload.

> 💡 **Tip**: If using the blue **DHT11** sensor instead of white DHT22, change `#define DHT_TYPE DHT22` to `#define DHT_TYPE DHT11` at line 28 of the `.ino` sketch.

---

## 🚀 Getting Started (Android App)

### Building the App
1. Open the project in **Android Studio (Ladybug / Jellyfish or newer)**.
2. Ensure Kotlin Gradle Plugin and Compose dependencies are synced.
3. Build and Run `:app:assembleDebug` on your Android device.

### How to Connect
1. **USB OTG Cable Connection (Recommended)**:
   - Turn **ON** `OTG Connection` in your phone's system settings.
   - Plug the ESP32 into your phone using the USB OTG cable.
   - Open the app, tap the **📡 Settings** button at top-right $\rightarrow$ **`🔌 Connect USB OTG Cable`**.
   - Grant USB permission on the prompt.

2. **Wi-Fi Connection**:
   - Connect your phone to ESP32 Wi-Fi SSID: **`Techno`** (Password: `techno123`).
   - In the app Settings dialog, tap **`192.168.4.1 (AP)`** or **`friday-bot.local`** and tap **Connect Wi-Fi**.

---

## 📱 App Screenshots & UI Structure

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

---

## 🛡 License

Distributed under the **MIT License**. Free to use and modify for personal and educational robotics projects.
