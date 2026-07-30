# Development Guide

## Setting Up the Development Environment

### Desktop (Python)

```bash
# Clone the repo
git clone https://github.com/yourname/litelink
cd litelink/desktop

# Create virtual environment
python -m venv .venv
source .venv/bin/activate    # Linux
.venv\Scripts\activate        # Windows

# Install dependencies
pip install -r requirements.txt
```

### Dependencies

```
PySide6>=6.5.0       # Qt GUI
websockets>=12.0     # WebSocket server
watchdog>=3.0.0      # File watching
```

### External Binaries

Download these and place in `desktop/bin/` or install via package manager:

```bash
# Linux
sudo apt install adb scrcpy ffmpeg

# Windows — download manually:
#   adb: developer.android.com/tools/releases/platform-tools
#   scrcpy: github.com/Genymobile/scrcpy/releases
#   ffmpeg: ffmpeg.org/download.html
```

### Companion (Kotlin)

- Open `mobile/LiteLinkCompanion/` in Android Studio
- Or build via command line: `gradlew assembleDebug`

## Running During Development

```bash
cd desktop
python run.py
```

The server starts on `ws://0.0.0.0:8765`.

## Building a Release

### Desktop EXE (PyInstaller)

```bash
cd desktop
pip install pyinstaller
pyinstaller --onefile --windowed --name LiteLink run.py
```

Output: `desktop/dist/LiteLink.exe` (Windows) or `LiteLink` (Linux)

### Companion APK

```bash
cd mobile/LiteLinkCompanion
gradlew assembleRelease
```

## Code Style

- **Python:** Follow PEP 8. Use type hints.
- **Kotlin:** Follow Kotlin coding conventions. Use coroutines for async.
- **Documentation:** Write docs in Markdown. Keep them in `docs/`.

## Branch Strategy

- `main` — Stable releases
- `develop` — Active development
- `feature/<name>` — Individual features

## Testing

```bash
# Desktop unit tests (planned)
cd desktop
python -m pytest tests/

# Manual integration test
# 1. Start desktop: python run.py
# 2. Connect companion app
# 3. Verify features
```

## Project Ideology

- **No root required.** Everything works with stock Android + USB Debugging.
- **Offline-first.** All communication is over local network. No cloud dependency.
- **Battery-conscious.** The companion app uses < 2% battery per day.
- **Budget-hardware friendly.** Python + PySide6 uses ~80MB RAM. Kotlin app is ~3MB APK.
- **Kenya-localized.** M-Pesa, USSD, low-data mode are first-class features, not afterthoughts.
