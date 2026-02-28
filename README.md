# 💊 Morpheus — Smart Pill Box

> **An intelligent medication management system** that combines an Android app with an ESP32-powered pill dispenser to help users never miss a dose.

![Kotlin](https://img.shields.io/badge/Kotlin-97%25-7F52FF?logo=kotlin&logoColor=white)
![Python](https://img.shields.io/badge/Python-3%25-3776AB?logo=python&logoColor=white)
![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-26-brightgreen)

---

## 📖 Overview

**Morpheus** (Smart Pill Box) is a full-stack IoT medication management solution. It features:

- 📱 **Android App** — Add medicines via OCR prescription scanning or manual entry, manage medication schedules, and receive timed reminders.
- 🖥️ **Flask Server** — A Python-based bridge that receives schedules from the Android app and dispatches commands to the hardware over WebSocket.
- 🔌 **ESP32 Hardware** — Controls physical compartment doors on a smart pill box, opening the correct compartments at the scheduled time.

---

## ✨ Features

| Feature | Description |
|---|---|
| **Prescription Scanning (OCR)** | Scan prescriptions using CameraX + Google ML Kit to automatically extract medicine names, dosages, and frequencies |
| **AI-Powered Parsing** | Uses Google Gemini API to intelligently parse scanned text into structured medication data |
| **Manual Medicine Entry** | Add medicines manually with name, dosage, and frequency |
| **Smart Scheduling** | Auto-generates alarms based on frequency (e.g., "twice daily" → Morning & Evening slots) |
| **Medication Reminders** | Android notification channels for medication reminders and missed dose alerts |
| **ESP32 Integration** | Sends commands via WebSocket to open specific pill compartments at scheduled times |
| **Dose Logging** | Tracks taken, missed, and pending doses with full history |
| **Local Database** | Room database for offline-first medicine and alarm storage |
| **Firebase Backend** | Firebase Auth, Realtime Database, and Cloud Messaging for sync and notifications |

---

## 🏗️ Architecture

```
┌──────────────────┐       HTTP/JSON        ┌──────────────────┐      WebSocket
│                  │  ───────────────────▶   │                  │  ───────────────▶  ┌──────────┐
│   Android App    │    POST /upload         │   Flask Server   │    cmd: session    │  ESP32   │
│   (Kotlin)       │  ◀───────────────────   │   (Python)       │  ◀───────────────  │  Pill    │
│                  │       schedule          │                  │                    │  Box     │
└──────────────────┘                         └──────────────────┘                    └──────────┘
```

---

## 📂 Project Structure

```
sits-pm-final/
├── app/                            # Android application module
│   ├── build.gradle.kts            # App-level Gradle config
│   ├── proguard-rules.pro
│   └── src/
│       └── main/
│           └── java/com/smartpillbox/app/
│               ├── MainActivity.kt             # Home screen with navigation cards
│               ├── SmartPillBoxApp.kt          # Application class, DB & notification setup
│               ├── data/
│               │   ├── local/
│               │   │   ├── AlarmDao.kt         # Room DAO for alarms
│               │   │   ├── AlarmEntity.kt      # Alarm table entity
│               │   │   ├── DoseLogDao.kt       # Room DAO for dose logs
│               │   │   ├── MedicineDao.kt      # Room DAO for medicines
│               │   │   └── MedicineEntity.kt   # Medicine table entity
│               │   └── model/
│               │       └── Medicine.kt         # Medicine data class
│               ├── ui/
│               │   ├── scan/
│               │   │   ├── ScanActivity.kt     # CameraX + ML Kit OCR scanning
│               │   │   └── MedicineAdapter.kt  # RecyclerView adapter for medicines
│               │   ├── schedule/
│               │   │   └── MedicationsActivity.kt  # View & manage saved medicines
│               │   └── setup/
│               │       └── AddMedicineActivity.kt  # Manual medicine entry
│               └── util/
│                   └── ScheduleGenerator.kt    # Frequency → time slot mapping
├── sits-pm-working-main/          # Server & working copy
│   ├── server.py                  # Flask server (ESP32 bridge)
│   └── requirements.txt          # Python dependencies
├── build.gradle.kts               # Root Gradle config
├── settings.gradle.kts            # Gradle settings (project name: SmartPillBox)
├── gradle.properties
└── gradlew / gradlew.bat         # Gradle wrapper scripts
```

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1) or newer
- **JDK 17**
- **Android SDK 35** (compile & target)
- **Python 3.8+** (for the Flask server)
- **ESP32** microcontroller with WebSocket firmware flashed

### 1. Clone the Repository

```bash
git clone https://github.com/epic-mindspark/sits-pm-final.git
cd sits-pm-final
```

### 2. Android App Setup

1. Open the project in **Android Studio**.
2. Create a `local.properties` file in the project root (if not present) and add your Gemini API key:
   ```properties
   GEMINI_API_KEY=your_gemini_api_key_here
   ```
3. Add your `google-services.json` file from Firebase Console to the `app/` directory.
4. Sync Gradle and run the app on a device/emulator (min API 26).

### 3. Flask Server Setup

```bash
cd sits-pm-working-main
pip install -r requirements.txt
```

Edit `server.py` to set your network IPs:

```python
ESP32_IP   = "YOUR_ESP32_IP"      # ESP32 IP from Serial Monitor
LAPTOP_IP  = "YOUR_LAPTOP_IP"     # Your machine's local IP
```

Start the server:

```bash
python server.py
```

The server exposes:

| Endpoint | Method | Description |
|---|---|---|
| `/upload` | `POST` | Receive medication schedule from the Android app |
| `/schedule` | `GET` | View current schedule and active jobs |
| `/fire` | `POST` | Manually trigger compartment doors |
| `/status` | `GET` | Server health check |

---

## 🛠️ Tech Stack

### Android App
| Technology | Purpose |
|---|---|
| **Kotlin** | Primary language |
| **Jetpack Room** | Local SQLite database |
| **CameraX** | Camera integration for scanning |
| **Google ML Kit** | On-device OCR text recognition |
| **Google Gemini API** | AI-powered prescription parsing |
| **Firebase Auth** | User authentication |
| **Firebase Realtime DB** | Cloud data sync |
| **Firebase Cloud Messaging** | Push notifications |
| **Kotlin Coroutines** | Asynchronous operations |
| **Material Design 3** | UI components |

### Server
| Technology | Purpose |
|---|---|
| **Flask** | REST API framework |
| **APScheduler** | Cron-based job scheduling |
| **WebSocket-Client** | ESP32 communication |
| **Flask-CORS** | Cross-origin request handling |

---

## 📋 How It Works

1. **Add Medicines** — Scan a prescription with your camera (OCR + Gemini AI) or add medicines manually.
2. **Auto-Schedule** — The `ScheduleGenerator` maps frequencies like *"twice daily"* or *"TDS"* to optimal time slots (Morning 8:00, Afternoon 14:00, Evening 20:00, Bedtime 22:30).
3. **Upload Schedule** — The app sends the schedule as JSON to the Flask server.
4. **Timed Dispatch** — APScheduler fires cron jobs at each scheduled time, sending WebSocket commands to the ESP32.
5. **Dispense** — The ESP32 opens the correct pill compartment doors.
6. **Notify** — The Android app sends medication reminders and missed-dose alerts via notification channels.

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is a submisson for Project Morpheus Hackathon

---

<p align="center">
  Built with ❤️ by <a href="https://github.com/epic-mindspark">epic-mindspark</a>
</p>
