# LiteLink Feature Inventory

## Legend

| Icon | Meaning |
|------|---------|
| 🖥️ | Desktop only (Python) |
| 📱 | Companion only (Kotlin) |
| 🔗 | Collaborative (both apps) |
| ✅ | Built |
| 🟡 | In progress |
| ⬜ | Planned |

---

## Tier 1: Core Engine (Must-Have)

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 1 | Wireless Auto-Discovery | 🔗 | ⬜ | Phone finds laptop on LAN via mDNS. No IP typing. |
| 2 | USB Fallback Mode | 🔗 | ⬜ | Switch to USB ADB if WiFi drops. |
| 3 | Smart Reconnect (Reboot) | 🔗 | ⬜ | Companion auto-reconnects on phone boot. |
| 4 | Screen Mirroring (scrcpy) | 🖥️ | ⬜ | Full mirroring with keyboard/mouse control. |
| 5 | Screen-Off Battery Saver | 🖥️ | ⬜ | Turn off phone screen while mirroring. |
| 6 | Drag & Drop File Transfer | 🔗 | ⬜ | Drag file onto mirror to copy to phone. |

## Tier 2: Productivity Suite

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 7 | Notification Mirror | 🔗 | ✅ | WhatsApp/M-Pesa popups on laptop. Live: phone→desktop over WebSocket. |
| 8 | Quick Reply | 🔗 | ⬜ | Reply from laptop notification popup. |
| 9 | Bi-Directional Clipboard | 🔗 | ⬜ | Copy on one, paste on the other. |
| 10 | File Explorer | 🖥️ | ⬜ | Browse phone storage as native folder. |
| 11 | Smart Media Offloader | 🔗 | ⬜ | HEIC/HEVC compress + move originals. |
| 12 | Batch APK Installer | 🔗 | ⬜ | Install multiple APKs at once. |
| 13 | APK Extractor | 🔗 | ⬜ | Save APK from phone to laptop. |
| 14 | Folder Sync | 🔗 | ⬜ | Real-time bidirectional file sync. |
| 41 | Notification History & Filtering | 🖥️ | ⬜ | Searchable log; per-app allow/block rules so the feed isn't a firehose. |

## Tier 3: System & Health

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 15 | MIUI Debloat Guardian | 🔗 | ⬜ | GUI to remove Xiaomi bloatware. |
| 16 | Battery Dashboard | 🔗 | ⬜ | Temperature, cycles, health alerts. |
| 17 | Find My Phone | 🔗 | ⬜ | Ring phone at max volume. |
| 18 | Tether Toggle | 🔗 | ⬜ | Enable hotspot from laptop. |
| 19 | SMS & Call Archiver | 🔗 | ⬜ | Backup to CSV/PDF on laptop. |
| 40 | Call Forwarding | 🔗 | ⬜ | Live incoming-call popup on laptop (from phone call state). |
| 42 | Anti-Theft Pack | 🔗 | ⬜ | Remote lock/wipe + front-camera capture of an intruder. Extends Find My Phone. |

## Tier 4: Kenya Specials

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 20 | M-Pesa Statement Tracker | 🔗 | ⬜ | Log M-Pesa SMS to SQLite/CSV. |
| 21 | Low-Data Mode | 🖥️ | ⬜ | Sync only when plugged in. |
| 22 | USSD Shortcut | 🔗 | ⬜ | Run USSD codes from laptop. |
| 23 | Signal Logger | 🔗 | ⬜ | Log cellular signal over time. |
| 24 | Offline Maps Sync | 🔗 | ⬜ | Copy offline maps between devices. |

## Tier 5: Advanced / Experimental

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 25 | Remote Camera | 🔗 | ⬜ | Use phone as wireless webcam. |
| 26 | Tasker Integration | 🔗 | ⬜ | Trigger phone tasks from laptop. |
| 27 | Scheduled Maintenance | 🔗 | ⬜ | Nightly cleanup + backup + reboot. |
| 28 | WhatsApp DB Backup | 🔗 | ⬜ | Backup chat databases to laptop. |
| 29 | Clipboard History | 🔗 | ⬜ | Searchable last 50 items. |
| 30 | Multi-Device | 🖥️ | ⬜ | Connect 2-3 phones at once. |
| 31 | Screen Recorder | 🔗 | ⬜ | Record phone screen to laptop. |
| 38 | Phone-as-Input | 🔗 | ⬜ | Use phone as touchpad/keyboard for the PC (reverse of mirroring). |
| 39 | Audio Streaming | 🔗 | ⬜ | Stream phone audio to PC speakers over the WebSocket. |
| 43 | Macro Runner | 🔗 | ⬜ | Scripted adb input taps/swipes/keys run from the laptop. |

## Tier 6: UX & Polish

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 32 | System Tray Icon | 🖥️ | ⬜ | Minimize to tray. |
| 33 | Dark / Light Mode | 🖥️ | ⬜ | Theme follows system. |
| 34 | Global Hotkeys | 🖥️ | ⬜ | Ctrl+Shift shortcuts. |
| 35 | Portable Mode | 🖥️ | ⬜ | Unzip and run. |
| 36 | Crash Recovery | 🖥️ | ⬜ | Auto-restart scrcpy/ADB. |
| 37 | Bundled Binaries | 🖥️ | ⬜ | ADB + scrcpy included. |

## Tier 7: Security & Trust

| # | Feature | Platform | Status | Description |
|---|---------|----------|--------|-------------|
| 44 | WSS + Pairing Secret | 🔗 | ⬜ | Encrypt the WebSocket (TLS) and require a one-time pairing code before Phase 1 ships. |

---

## Build Order

| Phase | Features | Est. Time |
|-------|----------|-----------|
| **1** | #1, #2, #3, #4, #5, #38, #44 — Connect + Mirror + secure transport | 1.5 weeks |
| **2** | #6, #10, #11, #12, #13, #14 — File management + transfer | 1.5 weeks |
| **3** | #7, #8, #9, #17, #40, #41 — Notifications, clipboard, calls | 1.5 weeks |
| **4** | #15, #16, #19, #42 — System management + anti-theft | 1.5 weeks |
| **5** | #20, #21, #22, #23, #24 — Kenya specials | 2 weeks |
| **6** | #25, #27, #29, #30, #31, #32, #39, #43 — Polish + advanced | 3 weeks |
