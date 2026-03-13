# EVAC — OFFLINE DISASTER COORDINATION NETWORK

## Final Project Document

> **When everything fails, Evac doesn't.**

---

## 1. Elevator Pitch

Evac is an Android app that turns every phone into a node in an invisible emergency network. When cell towers and internet are destroyed, victims send distress signals, rescue teams coordinate response, and officials broadcast verified alerts — **all without internet**. Messages hop silently phone-to-phone through human movement. A Gateway at the disaster's edge bridges the mesh to a cloud dashboard accessible worldwide. People without the app connect through a Wi-Fi portal — **no download needed**.

---

## 2. Architecture — Three Zones

```
┌──────────────────────────────────────────────────────────────────┐
│  ZONE A — DISASTER ZONE  (No Internet)                           │
│                                                                  │
│  📱 Citizen ◄──BLE/WiFi──► 📱 Citizen ◄──BLE/WiFi──► 📱 Field  │
│       │                         │                   Responder    │
│       │                         │                       │        │
│       └─────── BLE/WiFi ────────┘                  BLE/WiFi      │
│                                                         │        │
│                  📱 Legacy Phone ──WiFi──► 📶 Captive Portal     │
└──────────────────────────────────────────────────────────────────┘
                                                         │
                                              Satellite / LoRa /
                                              4G / SMS
                                                         │
┌──────────────────────────────────────────────────────────────────┐
│  ZONE C — THE BRIDGE  (Edge of Disaster)                         │
│  📡 Gateway Node (phone with internet connection)                │
│  Uploads mesh data → Cloud  |  Downloads bulletins → Mesh        │
└──────────────────────────────────────────────────────────────────┘
                                                         │
                                                     Internet
                                                         │
┌──────────────────────────────────────────────────────────────────┐
│  ZONE B — COMMAND CENTER  (Remote, Has Internet)                 │
│  🖥️ Web Dashboard: Live SOS map, verified bulletins, ACKs       │
│  🗄️ Firebase Firestore: Persistent data + real-time sync        │
└──────────────────────────────────────────────────────────────────┘
```

**Key Principle:** The mesh is **fully autonomous**. The Gateway is an *enhancement*, not a dependency. If it goes down, citizens and field responders keep communicating.

---

## 3. Five Node Types

| Node | Activation | Capabilities |
|---|---|---|
| **Citizen** | Default on install | 4-button SOS, Volume SOS, receive bulletins, receive ACKs |
| **Field Responder** | PIN-gated (production: Ed25519 keypair) | All Citizen abilities **+** offline SOS map, "En Route"/"Resolved" status, activate Captive Portal |
| **Gateway** | Auto-activates when internet detected | Mesh participant **+** batches data → Firebase, injects bulletins back into mesh |
| **Legacy Portal User** | No app needed | Connects to `EVAC_EMERGENCY` Wi-Fi hotspot, uses browser portal to read bulletins + submit SOS |
| **Command Center** | Web dashboard (separate) | Live SOS map, issue signed bulletins, send signed ACKs to victims |

---

## 4. Core Features

### 🔴 Feature 1: Four-Button SOS Interface

| Button | Code | Color | Use Case |
|---|---|---|---|
| MEDICAL | `MED` | Red | Injuries, medical emergencies |
| TRAPPED | `TRP` | Orange | Stuck under debris, can't move |
| HAZARD | `HAZ` | Yellow | Downed powerline, gas leak, flood |
| SAFE | `SAF` | Green | "I'm okay" — clears prior SOS |

- **One tap** sends SOS with automatic GPS — zero typing under stress
- Optional short text note (100 chars) for details
- **People count selector** (1–10+) — critical for rescue triage: *"5 people trapped"*
- **Battery level auto-included** — responders see if victim's phone is dying and prioritize
- Rate-limited: **1 SOS per 2 minutes** per device (hardware fingerprinted)

---

### 📳 Feature 2: Volume SOS

- Press **Volume Down 3× fast** = instant distress signal
- Works with **screen off**, in the dark, in your pocket
- **Haptic pulse** confirmation only (silent — critical if trapped near danger)
- Sends with last known GPS + `TRAPPED` status + battery level
- Detected via Android `MediaSession` callback — works even when screen is locked

---

### 📡 Feature 3: Offline Mesh Network

```
Phone A                              Phone B
   │                                    │
   ├── BLE Advertise (custom UUID) ────►│
   │◄── BLE Discover ──────────────────┤
   │                                    │
   ├── Upgrade to Wi-Fi Direct ────────►│
   │                                    │
   ├── Exchange message ID lists ──────►│
   │◄── "I need: [a,c]. Here's [d]" ───┤
   │                                    │
   │   Transfer only MISSING messages   │
   │   Priority order: CRITICAL first   │
   │                                    │
   ├── Verify SHA-256 hash ────────────►│
   │◄── Verify SHA-256 hash ───────────┤
   │                                    │
   └── Disconnect. Target: <3 sec ─────►│
```

- **BLE** for discovery (ultra-low power, ~10mW)
- **Wi-Fi Direct** for high-speed data transfer
- Messages hop automatically — no user action
- **Max 10 hops** per message (prevents flooding)
- **TTL: 24 hours** default (stale messages auto-purged)
- **Auto-rebroadcast:** Active SOS signals are re-broadcast every 10 minutes so rescuers approaching the area pick them up even if the original sender has moved or lost power

---

### 🌉 Feature 4: Gateway Bridge

- Any phone with internet **auto-activates** as a Gateway
- **Mesh → Cloud:** Batches all new data every 5 min → uploads to Firebase Firestore
- **Cloud → Mesh:** Pulls new bulletins/ACKs from Firebase → injects into mesh
- Multiple gateways = redundancy; Firebase deduplicates via unique message IDs
- If gateway goes down, mesh continues; syncs backlog on recovery

---

### 📶 Feature 5: Captive Portal (Legacy Device Support)

- Field Responder activates Wi-Fi hotspot: **`EVAC_EMERGENCY`**
- **NanoHTTPD** (embedded HTTP server) serves a clean HTML page
- ₹500 button phones, iPhones, laptops — anything with Wi-Fi + browser works
- Portal: read bulletins + submit SOS (name, location description, status)
- **Zero download, zero sign-up**

> [!NOTE]
> Captive portal auto-redirect varies by device. Fallback: user opens browser → `192.168.49.1`

---

### 🖥️ Feature 6: Command Center Dashboard

- **Web app** (HTML/JS + Leaflet.js) hosted on Firebase Hosting (free)
- **Live map** with color-coded SOS pins:

| Color | Meaning |
|---|---|
| 🔴 Red | Medical emergency |
| 🟠 Orange | Trapped |
| 🟡 Yellow | Hazard report |
| 🟢 Green | Safe / Resolved |

- **Issue bulletins** — typed, signed with Ed25519, pushed to Firebase → Gateway → Mesh
- **Send ACKs** — click SOS pin → type *"Help dispatched, ETA 30 min"* → signed → sent back to victim
- **Priority sidebar** — ranks zones by threat density (count of active MEDICAL + TRAPPED SOSes per area)
- **Pin details** show: status, people count, battery level, time since last update

---

### 🌐 Feature 7: Multi-Language Emergency Phrases

- Pre-loaded quick phrases in **5 languages** (Hindi, English, Telugu, Tamil, Bengali)
- Victim taps a phrase instead of typing: *"I need water"*, *"Child injured"*, *"Building collapsed"*
- Overcomes language barriers between rescue teams from other states and local victims
- Takes 30 minutes to implement (just a string array + UI list)

---

## 5. ~~Removed~~ Features (Not Practical for 19hrs)

| Feature | Why Removed |
|---|---|
| **Dead Man's Switch** (45-min auto-broadcast) | Android OEMs (Xiaomi, Samsung, Huawei) aggressively kill `ForegroundService`. False positive rate is high — phone in pocket for 45 min is normal. Would flood the network with welfare-check beacons. **Mention as future scope instead.** |
| **Bloom Filter sync** | Over-engineered for 3-phone demo. Simple ID-list exchange achieves the same result. Mention Bloom filters as "production optimization" to judges. |
| **Lamport Clocks** | Phones have local clocks even offline. Simple `System.currentTimeMillis()` timestamps work. Clock drift across phones is negligible for a 24-hour disaster window. |
| **Binary protocol (26 bytes)** | Custom binary serialization is bug-prone and hard to debug at 3 AM. JSON is readable, debuggable, and Nearby Connections handles the bandwidth fine for a demo. |
| **GPS cross-validation** | Requires knowing relay chain geography — complex and unreliable. Not worth the implementation time. |

---

## 6. Message Schema (JSON)

### SOS Message

```json
{
  "id": "uuid-v4",
  "type": "SOS",
  "status": "MEDICAL | TRAPPED | HAZARD | SAFE",
  "device_id": "sha256(android_id + ble_mac)",
  "timestamp": "2026-03-13T20:00:00Z",
  "ttl_hours": 24,
  "hop_count": 0,
  "max_hops": 10,
  "lat": 17.385,
  "lng": 78.4867,
  "accuracy_m": 10,
  "people_count": 3,
  "battery_pct": 42,
  "note": "3rd floor, near stairwell. 2 adults, 1 child.",
  "phrase_key": "CHILD_INJURED",
  "is_volume_sos": false,
  "hash": "sha256-of-all-fields-except-hash"
}
```

### Bulletin Message

```json
{
  "id": "uuid-v4",
  "type": "BULLETIN",
  "alert_type": "FLOOD | EARTHQUAKE | CYCLONE | GENERAL",
  "affected_zone": { "lat": 17.4, "lng": 78.5, "radius_km": 5 },
  "body": "Flash flood warning. Move to higher ground immediately.",
  "timestamp": "2026-03-13T20:00:00Z",
  "ttl_hours": 12,
  "hop_count": 0,
  "max_hops": 10,
  "ed25519_signature": "base64-encoded-64-bytes",
  "hash": "sha256-of-all-fields-except-hash-and-sig"
}
```

### ACK Message

```json
{
  "id": "uuid-v4",
  "type": "ACK",
  "target_device_id": "sha256-of-victim-device",
  "body": "Rescue team dispatched. ETA 30 minutes. Stay where you are.",
  "timestamp": "2026-03-13T20:05:00Z",
  "ttl_hours": 6,
  "hop_count": 0,
  "max_hops": 10,
  "ed25519_signature": "base64-encoded-64-bytes",
  "hash": "sha256-of-all-fields-except-hash-and-sig"
}
```

---

## 7. Security Model

| Threat | Mitigation |
|---|---|
| Fake SOS spam | Hardware fingerprint rate limit: **1 SOS per 2 min**. Field Responders can **mute** a device. |
| Fake bulletins / ACKs | **Ed25519 signature**. Public key in app. Private key only on Command Center. |
| Message tampering | **SHA-256 hash** per message. Tampered = dropped. |
| Stale information | **TTL** field — expired messages purged every sync. |
| Network flooding | **Max 10 hops**. Messages beyond limit dropped. |
| Replay attacks | Reject messages with same `id` (UUID deduplication). |
| Device ID spoofing | `sha256(android_id + BLE_MAC)` — survives app reinstall. |

---

## 8. Power Management

| Mode | Trigger | BLE Scan | GPS | Est. Battery |
|---|---|---|---|---|
| **Normal** | Default | Every 30s | Every 60s | ~8 hrs |
| **Power Saver** | Battery ≤ 30% | Every 5 min | Every 5 min | ~18 hrs |
| **Emergency Beacon** | Battery ≤ 15% | Minimal broadcast | Off (last known) | ~6–8 hrs |

Auto-switch is handled in the `ForegroundService`. No user action needed.

---

## 9. Tech Stack (₹0 Total)

| Component | Technology |
|---|---|
| Mobile App | Android Native (Kotlin) |
| Mesh Discovery | BLE (Android SDK) |
| Mesh Transfer | Nearby Connections API |
| Local Database | Room (SQLite) |
| Captive Portal | NanoHTTPD |
| Offline Maps | OSMDroid |
| Crypto | TweetNaCl-java (Ed25519) |
| Cloud Backend | Firebase Firestore (free tier) |
| Dashboard | HTML/JS + Leaflet.js |
| Hosting | Firebase Hosting (free tier) |

---

## 10. 19-Hour Build Plan (4 People)

**Start:** 7:49 PM  |  **Deadline:** Tomorrow 3:00 PM

### Person 1 — Mesh Networking 🔴 Critical Path

| Hours | Task |
|---|---|
| 0–3 | Nearby Connections API "hello world" — send a string between 2 phones |
| 3–8 | Store-and-forward relay: Phone A → Phone B → Phone C |
| 8–12 | BLE discovery + continuous background scanning via ForegroundService |
| 12–16 | Sync protocol: exchange ID lists → transfer missing messages, CRITICAL first |
| 16–19 | Gateway: batch upload to Firebase + pull bulletins into mesh |

> [!CAUTION]
> **If mesh isn't working by Hour 5**, pivot to phones on same local Wi-Fi exchanging messages. Show BLE architecture in slides. A working demo on Wi-Fi beats a broken demo on BLE.

### Person 2 — Mobile App UI

| Hours | Task |
|---|---|
| 0–4 | App scaffold: ForegroundService, Room DB, device fingerprint, bottom navigation |
| 4–8 | 4-button SOS UI, people count selector, GPS grab, battery level auto-capture |
| 8–12 | Volume SOS: 3× Volume Down detection via MediaSession, haptic confirm |
| 12–16 | Field Responder mode: PIN gate, OSMDroid map with color-coded SOS pins, status buttons |
| 16–19 | Bulletin/ACK display feed, multi-language phrase selector, auto-rebroadcast toggle |

### Person 3 — Backend + Dashboard

| Hours | Task |
|---|---|
| 0–4 | Firebase project (Firestore + Hosting), dashboard scaffold (HTML + Leaflet) |
| 4–10 | Dashboard: Firestore listener, color-coded SOS markers, pin details (people, battery, time) |
| 10–14 | Bulletin form + Ed25519 signing (TweetNaCl-js in browser) |
| 14–18 | ACK flow: click pin → type response → sign → Firestore. Priority sidebar. |
| 18–19 | Captive Portal: NanoHTTPD + HTML page (bulletins + SOS form) |

### Person 4 — Integration + Demo

| Hours | Task |
|---|---|
| 0–12 | Wire networking ↔ UI: SOS → Room → mesh → received → saved → displayed |
| 12–16 | Wire Gateway ↔ Firebase ↔ Dashboard (both directions) |
| 16–17 | Wire Captive Portal → Room → mesh |
| 17–19 | **End-to-end testing + demo rehearsal (minimum 2 full dry runs)** |

---

## 11. Priority Tiers — What to Cut

| Tier | Features | Rule |
|---|---|---|
| **P0 — Must Ship** | Mesh send/receive, 4-button SOS + GPS, Firebase upload, Dashboard map | No demo without these |
| **P1 — High Impact** | Volume SOS, Bulletin → phone, ACK → victim, people count + battery in SOS | Easy wins, big demo value |
| **P2 — Differentiator** | Captive Portal, multi-language phrases, auto-rebroadcast | Cut if behind. Show in slides. |
| **P3 — Polish** | Power Saver mode, priority sidebar, offline map tiles | Mention only |

---

## 12. 4-Minute Demo Script

### Setup
- **4 devices:** Phone 1 (Field Responder), Phone 2 (Gateway), Phone 3 (Citizen), Phone 4 (Victim)
- Phones 1, 3, 4 → **Airplane Mode** (BLE + Wi-Fi re-enabled)
- Phone 2 has internet (Gateway)
- Laptop showing Command Center dashboard

### Script

| Min | Action | Judges See |
|---|---|---|
| 0:00 | Show Airplane Mode on 3 phones. "No internet. No cell towers." | Credibility. |
| 0:30 | **Phone 4:** Press Volume Down 3× in pocket. Feel haptic. | *"Victim is trapped in the dark. One hand free."* |
| 1:00 | Hold Phone 4 near Phone 3, then Phone 3 near Phone 1. | SOS **hops** across mesh. Red dot appears on Responder's map: "TRAPPED — 3 people — Battery 34%". |
| 1:30 | Phone 1 taps "En Route" on the SOS pin. | Status changes to "Responder dispatched." |
| 2:00 | Phone 1 near Phone 2 (Gateway). Point to laptop. | SOS appears on **Command Center dashboard** with all details. |
| 2:30 | On dashboard: click pin → type *"Helicopter ETA 20 min"* → send ACK. | ACK flows: Dashboard → Firebase → Gateway → Mesh → Phone 4. |
| 3:00 | Show Phone 4: ACK received — **"✅ Helicopter ETA 20 min"** | *"The victim knows help is coming."* |
| 3:30 | Legacy phone connects to `EVAC_EMERGENCY` hotspot. Browser opens. Submits SOS. | Appears on dashboard. *"No app needed."* |
| 4:00 | Closing. | |

### Closing Line

> ***"Four phones. Zero internet. A victim trapped in the dark pressed a button three times. Sixty seconds later, a command center 500 km away dispatched a helicopter. When everything fails, Evac doesn't."***

---

## 13. Judge Q&A

| Question | Answer |
|---|---|
| *"Fake SOS?"* | Hardware fingerprint rate limit (1 per 2 min) + responder mute. |
| *"Battery drain?"* | BLE ~10mW. Auto-saver at 30%. Emergency beacon at 15% lasts 6–8 hrs. |
| *"Fake alerts?"* | Ed25519 signatures. Private key only on Command Center. |
| *"Remote govt facility?"* | Gateway auto-bridges mesh → Firebase → Dashboard. Works globally. |
| *"No smartphone?"* | Captive Portal: any Wi-Fi device + browser. Zero download. |
| *"Scale?"* | 10-hop max + 24hr TTL + delta-sync. Network stays lean. |
| *"GPS indoors?"* | Last known GPS + manual text description. |
| *"Nobody moves?"* | Wi-Fi Direct ~200m covers a building. Auto-rebroadcast every 10 min. |
| *"Cost?"* | ₹0. 100% free/open-source. |
| *"iOS?"* | Captive Portal works for iOS. Native port is future scope. |

---

## 14. Suggested Project Structure

```
evac/
├── app/src/main/java/com/evac/
│   ├── MainActivity.kt
│   ├── ui/
│   │   ├── CitizenFragment.kt         # 4-button SOS + phrase selector
│   │   ├── BulletinFragment.kt        # Bulletin + ACK feed
│   │   ├── MapFragment.kt             # OSMDroid map (Responder)
│   │   └── ResponderFragment.kt       # Status controls
│   ├── mesh/
│   │   ├── MeshService.kt             # ForegroundService (BLE + WiFi Direct)
│   │   ├── NearbyManager.kt           # Nearby Connections wrapper
│   │   └── SyncEngine.kt              # ID exchange + delta transfer
│   ├── gateway/
│   │   └── GatewayManager.kt          # Firebase upload/download
│   ├── portal/
│   │   └── CaptivePortalServer.kt     # NanoHTTPD
│   ├── db/
│   │   ├── AppDatabase.kt             # Room
│   │   ├── MessageDao.kt
│   │   └── MessageEntity.kt
│   ├── crypto/
│   │   └── SignatureManager.kt        # Ed25519 verify
│   └── util/
│       ├── DeviceFingerprint.kt
│       ├── VolumeSosDetector.kt        # 3× Volume Down
│       └── Phrases.kt                 # Multi-language strings
├── dashboard/
│   ├── index.html
│   ├── app.js                          # Leaflet + Firestore
│   └── style.css
└── firebase/
    └── firestore.rules
```

---

## 15. Future Scope (Mention if Judges Ask)

- **Dead Man's Switch** — auto-broadcast if no user interaction for 45 min (needs OEM-specific tuning)
- **AI triage** — on-device ML to prioritize SOS signals
- **Bloom filter sync** — reduce bandwidth at scale
- **Binary protocol** — 26-byte compact format for BLE efficiency
- **Drone relay nodes** — extend mesh coverage
- **NDRF/SDRF integration** — CAP (Common Alerting Protocol) format
- **iOS native app** — via MultipeerConnectivity framework

---

**START TIME:** 7:49 PM  
**DEADLINE:** Tomorrow 3:00 PM (19 hours)  
**TEAM:** 4 people  
**OUTCOME:** Working prototype + polished 4-minute demo

**Go build it. 🔥**
