<div align="center">

# 🛡️ Child Monitoring AI

**An intelligent Android parental control app powered by AI — keeping children safe in the digital world.**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Min SDK](https://img.shields.io/badge/Min%20SDK-Android%206.0-blue)
![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?logo=firebase&logoColor=black)
![TensorFlow Lite](https://img.shields.io/badge/AI-TensorFlow%20Lite-FF6F00?logo=tensorflow&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-green)

</div>

---

## 📖 Overview

**Child Monitoring AI** is a comprehensive Android application designed to help parents keep their children safe online and offline. It combines real-time device monitoring with on-device artificial intelligence to detect harmful content automatically — all synchronized to a secure cloud dashboard accessible to parents.

The app runs silently in the background, aggregating activity data from the child's device and alerting parents to potential risks, without interrupting the child's day-to-day usage.

---

## ✨ Features

### 📱 Communication Monitoring
- **SMS & MMS** — Read and sync incoming and outgoing text messages
- **Call Logs** — Track call history including caller details and duration
- **WhatsApp** — Monitor messages and conversations
- **Telegram** — Track messages via accessibility integration
- **Instagram & Snapchat** — Monitor direct message activity

### 📍 Location & Geofencing
- **Real-time GPS Tracking** — View the child's live location at any time
- **Location History** — Review location trails and movement patterns
- **Geofence Alerts** — Set safe zones and receive instant notifications when the child leaves or enters a defined area

### 🖼️ AI-Powered Photo Monitoring
- **NSFW Detection** — On-device TensorFlow Lite model automatically analyzes photos on the device and flags inappropriate content
- **Photo Sync** — Captured or downloaded images are reviewed and uploaded securely for parental review
- **Automatic Classification** — Images are scored across multiple content categories (neutral, suggestive, explicit) with a configurable safety threshold

### 🌐 Web Activity
- **Browsing History** — Monitor websites visited by the child
- **Web Content Awareness** — Track online behaviour patterns over time

### 📊 App Usage & Management
- **App Usage Statistics** — See which apps are used, how often, and for how long
- **App Install Tracking** — Get notified when new apps are installed on the device
- **Contact Monitoring** — Review contacts stored on the child's device

### 🔔 Notification Monitoring
- **Notification Capture** — Intercept and log notifications from all apps to identify conversations happening outside monitored channels

### 🔐 Remote Device Control
- **Remote Commands** — Send commands remotely via Firebase Cloud Messaging (e.g., lock device, retrieve latest data)
- **Device Admin Integration** — Leverage Android device administrator APIs for enhanced control
- **Clipboard Monitoring** — Track content copied to the clipboard

### 👤 Parent Dashboard
- **Multi-device Support** — Monitor multiple children's devices from a single parent account
- **Secure Authentication** — Firebase Auth-backed email/password login with session management
- **Real-time Sync** — All data is streamed to Firebase Realtime Database for instant access

---

## 🧠 AI & Machine Learning

| Capability | Details |
|---|---|
| **Model** | MobileNetV2 (TensorFlow Lite) |
| **Task** | NSFW Image Classification |
| **Input** | 224 × 224 pixel images |
| **Inference** | Fully on-device (no data sent to external ML servers) |
| **Categories** | Neutral · Drawings · Suggestive · Explicit · Hentai |
| **Flagging Threshold** | Configurable (default: 70% confidence) |

The on-device inference approach ensures privacy and low latency — images are analysed instantly without requiring an internet connection for the ML step.

---

## 🛠️ Tech Stack

### Mobile Application
| Layer | Technology |
|---|---|
| **Language** | Java |
| **Platform** | Android (API 23–34) |
| **Build System** | Gradle (Kotlin DSL) |
| **UI Toolkit** | AndroidX + Material Design 3 |
| **Background Processing** | AndroidX WorkManager |
| **AI / ML** | TensorFlow Lite 2.9 + TFLite Support Library |

### Cloud & Backend
| Service | Purpose |
|---|---|
| **Firebase Realtime Database** | Store and sync all monitoring data in real time |
| **Firebase Authentication** | Secure parent and device account management |
| **Firebase Cloud Storage** | Store flagged photos and media evidence |
| **Firebase Cloud Messaging** | Push notifications and remote command delivery |

### Android System Integrations
| API | Usage |
|---|---|
| **AccessibilityService** | Monitor foreground app activity and social media content |
| **NotificationListenerService** | Intercept and log device notifications |
| **DevicePolicyManager** | Device admin controls (lock, camera policy) |
| **UsageStatsManager** | App usage analytics |
| **LocationManager + Google Play Services** | Real-time GPS and geofencing |
| **MediaStore / ContentObserver** | Photo and media monitoring |

---

## 🔒 Privacy & Security

- All data is transmitted over HTTPS to Firebase's secure infrastructure
- AI inference runs **entirely on-device** — no images are sent to third-party ML services
- Parent accounts are protected by Firebase Authentication
- The app is designed to be installed with the knowledge and consent of the device owner/parent
- Stealth operation is intended for use by parents on devices they own and manage for their children

> ⚠️ **Important:** This application is intended solely for legal parental monitoring of minor children on devices owned by the parent or guardian. Usage must comply with all applicable local laws and regulations regarding privacy and surveillance.

---

## 📋 Requirements

- Android **6.0 (Marshmallow)** or higher
- Active internet connection for cloud sync
- Firebase project credentials
- Device with Google Play Services

---

## 🤝 Contributing

Contributions, issues, and feature requests are welcome! Feel free to open an issue or submit a pull request.

---

## 📄 License

This project is licensed under the **MIT License**.

---

<div align="center">

Made with ❤️ for child safety

</div>

