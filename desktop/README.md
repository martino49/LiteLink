# LiteLink Desktop App

Python + PySide6 desktop application. The core of LiteLink — runs the WebSocket server, launches scrcpy, watches files, and provides the GUI.

## Quick Start

```bash
pip install -r requirements.txt
python -m litelink
```

## Project Structure

```
desktop/
├── README.md
├── requirements.txt
├── bin/                     # External binaries (adb, scrcpy, ffmpeg)
│   ├── adb                  # or adb.exe on Windows
│   ├── scrcpy               # or scrcpy.exe
│   └── ffmpeg               # or ffmpeg.exe
└── src/
    └── litelink/
        ├── __init__.py
        ├── __main__.py      # Entry point: python -m litelink
        ├── server.py        # WebSocket server
        ├── adb_utils.py     # ADB command wrapper
        ├── folder_sync.py   # File watcher + sync engine
        └── gui/
            ├── __init__.py
            ├── app.py       # Main window
            ├── mirror.py    # scrcpy launcher panel
            ├── notifications.py  # Notification sidebar
            └── settings.py  # Settings dialog
```

## Dependencies

- Python 3.11+
- PySide6 (Qt GUI)
- websockets (WebSocket server)
- watchdog (file watching)
- PyInstaller (for building .exe)
