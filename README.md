# 🚨 EVAC — Offline Disaster Coordination Network

<div align="center">

![EVAC Banner](https://img.shields.io/badge/EVAC-When%20Everything%20Fails%2C%20Evac%20Doesn't-red?style=for-the-badge)

**🏆 Winner - Mega Hackathon 2024, BMS College of Engineering**

[![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Firebase](https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black)](https://firebase.google.com)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge)](https://opensource.org/licenses/MIT)

[Demo Video](#-demo) • [Features](#-features) • [Architecture](#-architecture) • [Installation](#-installation) • [Team](#-team)

</div>

---

## 📖 What is EVAC?

**EVAC** is an Android application that transforms every smartphone into a node in an autonomous emergency mesh network. When natural disasters destroy cell towers and internet infrastructure, EVAC enables:

- 🆘 **Victims** to send distress signals without internet
- 🚁 **Rescue teams** to coordinate response in real-time
- 🗺️ **Command centers** to monitor disaster zones remotely
- 📱 **Anyone** to participate — even without the app

### The Problem

During the 2015 Nepal earthquake, 8,790 people died. Many survivors were trapped under rubble for days because:
- Cell towers collapsed — no way to call for help
- Rescue teams couldn't locate victims efficiently
- Coordination between agencies was impossible
- Information about safe zones wasn't accessible

### Our Solution

EVAC creates a **fully offline mesh network** where phones automatically relay SOS messages to each other. A single phone with internet at the disaster's edge bridges the mesh to a cloud dashboard accessible worldwide.

**No internet. No cell towers. Just phones helping phones.**

---

## ✨ Features

### Core Capabilities

#### 🔴 One-Tap SOS Interface
- **4 distress signals**: Medical, Trapped, Hazard, Safe
- Automatic GPS, battery level, and people count
- Optional multi-language emergency phrases (Hindi, English, Telugu, Tamil, Bengali)
- Rate-limited to prevent spam (1 SOS per 2 minutes)

#### 📳 Volume SOS (Stealth Mode)
- Press **Volume Down 3× fast** to send silent SOS
- Works with screen off, in pocket, in the dark
- Haptic-only confirmation (critical if hiding from danger)

#### 📡 Self-Healing Mesh Network
- **BLE discovery** (ultra-low power, 10mW)
- **WiFi Direct transfer** (high-speed data exchange)
- Messages hop phone-to-phone automatically
- Up to 10 hops, 24-hour message lifespan
- Intelligent sync: only transfers missing messages

#### 🗺️ Field Responder Map
- Real-time offline map showing all SOS signals
- Color-coded pins: 🔴 Medical | 🟠 Trapped | 🟡 Hazard | 🟢 Safe
- Tap pin → see people count, battery, location
- "En Route" / "Resolved" status updates

#### 🌉 Gateway Bridge
- Any phone with internet **auto-becomes a gateway**
- Uploads mesh data → Firebase Firestore
- Injects bulletins/ACKs → mesh
- Multiple gateways = redundancy

#### 🖥️ Command Center Dashboard
- **Live map** with SOS signals worldwide
- Send verified bulletins to disaster zone
- Send acknowledgments to victims: *"Help ETA 20 min"*
- Priority sidebar ranking critical zones
- Export data to CSV

#### 📢 Acoustic Beacon (NEW)
- Volume Up 3× → emit loud alternating tones
- Helps rescuers physically locate trapped victims
- Smart frequency: increases when responder nearby
- Battery-aware throttling

#### 🚁 Extended Range Mode (NEW)
- **BLE Coded PHY**: 4× range (up to 400m)
- WiFi power boost + 5GHz preference
- Helicopter detection (altitude > 200m → Sky Relay mode)
- Software-only range extension

#### 🏥 Safe Spots (NEW)
- Shows nearest disaster-appropriate shelters
- Filters by type: Flood → High ground | Earthquake → Open spaces
- Walking distance + estimated time
- Route visualization on map

#### 📶 Captive Portal (Zero-Install Access)
- Field responders activate `EVAC_EMERGENCY` WiFi hotspot
- ₹500 button phones, iPhones, laptops — anything with WiFi works
- Submit SOS via browser, no download needed

---

## 🏗️ Architecture

### Three-Zone Model
```
┌─────────────────────────────────────────────────────────────────┐
│  ZONE A — DISASTER ZONE (No Internet)                           │
│                                                                  │
│  📱 Victim ◄──BLE──► 📱 Citizen ◄──WiFi Direct──► 📱 Responder │
│                                                                  │
│  All phones relay messages automatically                        │
│  No central server, fully peer-to-peer                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ One phone at disaster edge
                              │ has internet connection
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  ZONE B — THE BRIDGE                                            │
│  📡 Gateway: Uploads mesh → Cloud | Downloads bulletins → Mesh  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              │ Internet
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  ZONE C — COMMAND CENTER (Remote, Global Access)                │
│  🖥️ Dashboard: Live SOS map, verified bulletins, ACKs          │
│  🗄️ Firebase: Real-time sync, global accessibility             │
└─────────────────────────────────────────────────────────────────┘
```

**Key Principle**: The mesh is **fully autonomous**. If the gateway fails, victims and rescuers keep communicating offline.

## 🛠️ Tech Stack

| Component | Technology | Why |
|-----------|-----------|-----|
| **Mobile App** | Android Native (Kotlin) | Maximum performance, full hardware access |
| **Mesh Discovery** | BLE (Bluetooth Low Energy) | Ultra-low power (~10mW), works with screen off |
| **Mesh Transfer** | WiFi Direct (Nearby Connections API) | High-speed data exchange, no router needed |
| **Database** | Room (SQLite) | Offline-first, automatic sync |
| **Maps** | OSMDroid | Fully offline maps, no Google API dependency |
| **Backend** | Firebase Firestore | Real-time sync, generous free tier |
| **Dashboard** | HTML/JS + Leaflet.js | Lightweight, works on any device |
| **Hosting** | Firebase Hosting | Free, global CDN |
| **Cryptography** | SHA-256 + Ed25519 | Message integrity, bulletin authentication |

**Total Cost**: ₹0 (100% free & open-source)

---

## 🚀 Installation

### Prerequisites
- Android Studio Arctic Fox or newer
- Android device with API 26+ (Android 8.0+)
- Firebase account (free tier)

### Quick Start
```bash
# Clone the repository
git clone https://github.com/yourusername/evac.git
cd evac

# Firebase Setup
1. Create Firebase project at console.firebase.google.com
2. Download google-services.json → place in app/
3. Enable Firestore + Hosting

# Build the app
./gradlew assembleDebug

# Install on device
adb install app/build/outputs/apk/debug/app-debug.apk

# Deploy dashboard
cd dashboard
firebase deploy --only hosting
```

### Running from Terminal (No Android Studio)
```bash
# Build APK
./gradlew clean assembleDebug

# Connect phone via USB (enable USB Debugging)
adb devices

# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Launch
adb shell am start -n com.evac.app/.MainActivity

# View logs
adb logcat | grep "EVAC"
```

---

## 🧪 Testing

### Unit Tests
```bash
./gradlew test
```

### Integration Tests
```bash
./gradlew connectedAndroidTest
```

### Manual Testing Checklist
- [ ] SOS signals send and receive offline
- [ ] Messages hop across 3+ phones
- [ ] Volume SOS works with screen off
- [ ] Dashboard shows live updates
- [ ] Bulletins appear on phones
- [ ] ACKs reach correct recipient
- [ ] Safe spots display correctly
- [ ] Acoustic beacon activates

---

## 📊 Performance Metrics

| Metric | Result |
|--------|--------|
| **Mesh Range** | 100m (standard), 400m (extended mode) |
| **Message Relay Speed** | <3 seconds between phones |
| **Battery Impact** | ~8 hours continuous operation |
| **Gateway Sync** | Every 5 minutes (configurable) |
| **Max Network Size** | 10,000+ devices (untested at scale) |
| **Message Lifespan** | 24 hours (auto-purged after TTL) |

---

## 🔒 Security

| Threat | Mitigation |
|--------|-----------|
| **Fake SOS Spam** | Hardware fingerprint rate limiting (1 per 2 min) |
| **Fake Bulletins** | Ed25519 digital signatures (private key on command center only) |
| **Message Tampering** | SHA-256 hash verification |
| **Replay Attacks** | UUID deduplication |
| **Network Flooding** | Max 10 hops per message |

---

## 🗺️ Roadmap

### Completed ✅
- [x] Offline mesh networking
- [x] 4-button SOS interface
- [x] Volume SOS (stealth mode)
- [x] Field responder map
- [x] Command center dashboard
- [x] Gateway bridge
- [x] Multi-language phrases
- [x] Acoustic beacon
- [x] Extended range mode
- [x] Safe spots integration

### Planned 🚧
- [ ] iOS app (via MultipeerConnectivity)
- [ ] Dead Man's Switch (auto-SOS after inactivity)
- [ ] AI-powered triage (prioritize critical cases)
- [ ] Bloom filter sync optimization
- [ ] Binary protocol (26-byte messages)
- [ ] NDRF/SDRF integration (CAP format)
- [ ] Drone relay nodes
- [ ] Satellite uplink support

---

## 👥 Team

**Built in 19 hours at Mega Hackathon 2024, BMS College of Engineering (March 13-14, 2024)**

| Name | Role | 
|------|------|
| **Jason** | Backend + UI| 
| **Mithun** | Android UI + Integration | 
| **Darshan** | Mesh Network| 
| **Nahyan** |  Mesh Network | 
---

## 🙏 Acknowledgments

- **BMS College of Engineering** for hosting the hackathon
- **Android Nearby Connections API** for enabling offline mesh
- **OSMDroid** for offline mapping capabilities
- **Firebase** for generous free tier
- **Leaflet.js** for beautiful web maps
- **Judges** for recognizing the potential impact

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
```
MIT License

Copyright (c) 2024 EVAC Team

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

[Full MIT License text...]
```

---

## 🌟 Why This Matters

Every year, natural disasters affect **160 million people** worldwide. During the critical first 72 hours:
- **Communication infrastructure fails** — no way to call for help
- **Rescue coordination breaks down** — wasted time, duplicated efforts
- **Victims remain invisible** — unable to signal their location

EVAC addresses all three problems with zero infrastructure dependency.

**This isn't just a hackathon project. It's a blueprint for disaster-resilient communication.**

<div align="center">

**⭐ If this project helped you, please consider giving it a star!**

**Built with ❤️ in BMSCE,Bangalore during a 19-hour sprint**

</div>
