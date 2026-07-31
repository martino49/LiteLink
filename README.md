# LiteLink

**Bridge your Android phone and laptop. Real-time notifications, screen mirroring, folder sync, and system control — all over Wi-Fi.**

LiteLink connects your Redmi 14 C (or any Android phone) to your Windows/Linux laptop without cables, cloud services, or rooting. It runs as a lightweight desktop app (Python + PySide6) paired with a small companion app (Kotlin) on the phone.

## Features

- **Screen Mirroring** — Control your phone from your laptop via scrcpy. Turn off the phone screen to save battery while mirroring.
- **Notifications** — See WhatsApp, SMS, and M-Pesa popups on your laptop. Reply from your keyboard.
- **Folder Sync** — Real-time bidirectional sync. Edit a file on your laptop, it updates on your phone instantly.
- **Clipboard Sync** — Copy on one device, paste on the other.
- **File Explorer** — Browse your phone's storage as a native folder on your laptop.
- **MIUI Debloat** — Remove Xiaomi bloatware from a graphical interface.
- **Media Offloader** — Auto-compress old photos/videos to HEIC/HEVC and move originals to your laptop.
- **M-Pesa Tracker** — Log M-Pesa transactions from SMS to a local database.
- **USSD Shortcuts** — Run Safaricom/Airtel USSD codes from your laptop.
- **Find My Phone** — Ring your phone at max volume from your laptop.
- **Battery Dashboard** — Monitor charge cycles, temperature, and health.

## Quick Start

```bash
git clone https://github.com/yourname/litelink
cd litelink/desktop
pip install -r requirements.txt
python run.py
```

Then install the companion APK on your phone, enable Notification Access, and both devices find each other automatically on the same Wi-Fi.

## Documentation

| Document | Description |
|----------|-------------|
| [docs/setup.md](docs/setup.md) | Full setup guide for desktop and phone |
| [docs/architecture.md](docs/architecture.md) | System architecture and data flow |
| [docs/features.md](docs/features.md) | Complete feature inventory |
| [docs/companion-app.md](docs/companion-app.md) | Building and installing the Android companion |
| [docs/development.md](docs/development.md) | Contributor guide |

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Desktop GUI | Python 3.11+ / PySide6 (Qt) |
| Desktop server | Python websockets (asyncio) |
| Screen mirroring | scrcpy (C, bundled as binary) |
| Phone companion | Kotlin / OkHttp WebSocket client |
| Communication | WebSocket over Wi-Fi (port 8765) |
| Packaging (desktop) | PyInstaller (portable .exe) |

## Project Structure

```
LiteLink/
├── README.md
├── LICENSE
├── .gitignore
├── docs/                     # Project documentation
│   ├── index.md
│   ├── setup.md
│   ├── architecture.md
│   ├── features.md
│   ├── companion-app.md
│   └── development.md
├── desktop/                  # Python desktop application
│   ├── README.md
│   ├── requirements.txt
│   └── src/litelink/
│       ├── __init__.py
│       ├── __main__.py
│       ├── server.py         # WebSocket server
│       ├── adb_utils.py      # ADB commands
│       ├── folder_sync.py    # File watcher + sync engine
│       └── gui/              # PySide6 interface
├── mobile/                   # Android companion (Kotlin)
│   ├── README.md
│   └── LiteLinkCompanion/
├── scripts/                  # Build and utility scripts
│   ├── build_desktop.sh
│   └── build_desktop.bat
└── assets/                   # Icons, images
```

## Build Phases

| Phase | Features | Status |
|-------|----------|--------|
| 1 | Auto-connect, secure transport (WSS + pairing), screen mirroring, phone-as-input | 🟡 WebSocket hub + reconnect live |
| 2 | Drag & drop files, file explorer, folder sync, APK installer/extractor | ⬜ |
| 3 | Notifications (built), quick reply, clipboard, call popups, notification history | 🟡 Notification mirror done |
| 4 | Debloat, battery dashboard, SMS backup, anti-theft pack | ⬜ |
| 5 | M-Pesa tracker, USSD, tether toggle, signal logger, offline maps | ⬜ |
| 6 | Remote camera/webcam, screen recorder, macros, audio streaming, tray, hotkeys, themes, portable | ⬜ |

## License

MIT
