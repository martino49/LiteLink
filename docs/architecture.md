# LiteLink Architecture

## System Overview

LiteLink uses a **client-server architecture** over a local Wi-Fi network. The laptop runs a WebSocket server. The phone runs a companion app that connects as a client. All communication uses JSON over a single persistent WebSocket connection on port 8765.

```
┌─────────────────────────────────────┐          Wi-Fi Network          ┌──────────────────────────────┐
│         Desktop App (Python)        │      (Port 8765, WebSocket)    │     Companion App (Kotlin)    │
│                                     │ ◄────────────────────────────► │                              │
│  ┌───────────────────────────────┐  │     Persistent JSON Tunnel     │  ┌──────────────────────────┐ │
│  │  PySide6 GUI                  │  │                                   │  │  Services               │ │
│  │  ┌─────────────────────────┐  │  │                                   │  │  ┌────────────────────┐ │ │
│  │  │ Mirror Panel            │  │  │                                   │  │  │ NotificationListen.│ │ │
│  │  │ (launches scrcpy)       │  │  │                                   │  │  ├────────────────────┤ │ │
│  │  ├─────────────────────────┤  │  │                                   │  │  │ FileObserver       │ │ │
│  │  │ Notification Sidebar    │  │  │                                   │  │  ├────────────────────┤ │ │
│  │  │ (popups + history)      │  │  │                                   │  │  │ ClipboardManager   │ │ │
│  │  ├─────────────────────────┤  │  │                                   │  │  ├────────────────────┤ │ │
│  │  │ File Explorer           │  │  │                                   │  │  │ Command Executor   │ │ │
│  │  ├─────────────────────────┤  │  │                                   │  │  ├────────────────────┤ │ │
│  │  │ Folder Sync Panel       │  │  │                                   │  │  │ Camera Streamer    │ │ │
│  │  ├─────────────────────────┤  │  │                                   │  │  └────────────────────┘ │ │
│  │  │ Settings / Dashboard    │  │  │                                   │  └──────────────────────────┘ │
│  │  └─────────────────────────┘  │  │                                   │                              │
│  └───────────────────────────────┘  │                                   │  Runs on Android 7+          │
│                                     │                                   │  No root required            │
│  ┌───────────────────────────────┐  │                                   └──────────────────────────────┘
│  │  Backend Engine               │  │
│  │  ┌─────────────────────────┐  │  │
│  │  │ WebSocket Server        │  │  │
│  │  │ (asyncio, port 8765)    │  │  │
│  │  ├─────────────────────────┤  │  │
│  │  │ ADB Controller          │  │  │
│  │  │ (subprocess calls)      │  │  │
│  │  ├─────────────────────────┤  │  │
│  │  │ scrcpy Launcher         │  │  │
│  │  │ (subprocess.Popen)      │  │  │
│  │  ├─────────────────────────┤  │  │
│  │  │ File Watcher            │  │  │
│  │  │ (watchdog library)      │  │  │
│  │  ├─────────────────────────┤  │  │
│  │  │ Media Processor         │  │  │
│  │  │ (FFmpeg subprocess)     │  │  │
│  │  └─────────────────────────┘  │  │
│  └───────────────────────────────┘  │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  Bundled Binaries (bin/)     │  │
│  │  adb.exe | scrcpy.exe | ...  │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
         Runs on Windows / Linux
```

---

## WebSocket Protocol

All messages between the desktop and companion app are JSON objects.

### Phone → Desktop Messages

```json
{
  "type": "notification",
  "app": "com.whatsapp",
  "title": "Mary",
  "text": "See you at 6pm",
  "timestamp": 1712345678000
}
```

| Type | Description | Fields |
|------|-------------|--------|
| `notification` | Incoming notification | app, title, text, timestamp |
| `file_change` | File created/modified/deleted | path, event (created/modified/deleted) |
| `battery_update` | Battery stats report | level, temperature, voltage, health |
| `clipboard` | Clipboard content | text |
| `signal_strength` | Cellular signal reading | dbm, asu, network_type |

### Desktop → Phone Messages

```json
{
  "type": "sync_file",
  "path": "/sdcard/Shared/notes.txt",
  "data": "QmFzZTY0..."
}
```

| Type | Description | Fields |
|------|-------------|--------|
| `sync_file` | Write file on phone | path, data (base64) |
| `sync_delete` | Delete file on phone | path |
| `reply` | Reply to a notification | app, text |
| `dismiss` | Dismiss a notification | key |
| `ring` | Trigger find-my-phone alarm | (none) |
| `tether` | Toggle mobile hotspot | enabled (bool) |
| `clipboard` | Set clipboard content | text |
| `ussd` | Dial a USSD code | code |

---

## Connection Lifecycle

### Normal Startup

1. Desktop starts WebSocket server on `0.0.0.0:8765`
2. Companion app connects to `ws://<laptop-ip>:8765`
3. If auto-discovery is enabled, phone finds laptop via mDNS/Bonjour
4. Connection established. Both sides exchange heartbeats every 30 seconds.

### Reconnection After Disconnect

1. Companion app detects WebSocket closure
2. Waits 5 seconds, then reconnects
3. Retries with exponential backoff (5s, 10s, 20s, max 60s)
4. On reconnect, re-syncs current state (notifications, clipboard)

### Phone Reboot

1. Companion app restarts with the phone
2. Foreground service starts automatically (on Android 8+)
3. Service connects to the laptop WebSocket
4. If laptop IP was saved, uses it directly. Otherwise falls back to mDNS.
5. Full reconnection in < 10 seconds.

---

## ADB Usage (Desktop Only)

Some features use ADB directly from the desktop, not through the companion app:

- **Debloat**: `adb shell pm list packages` + `adb shell pm uninstall --user 0 <package>`
- **scrcpy WiFi init**: `adb tcpip 5555` (requires USB on first use)
- **File listing**: `adb shell ls` / `adb shell find`
- **Media offloader**: `adb pull` + `adb shell rm`

These commands run as subprocesses from the Python backend. ADB requires USB Debugging to be enabled on the phone.

---

## Bundled Binaries

The desktop app ships with these binaries in `desktop/bin/`:

| Binary | Purpose | Source |
|--------|---------|--------|
| `adb` / `adb.exe` | Android Debug Bridge | developer.android.com |
| `scrcpy` / `scrcpy.exe` | Screen mirroring | github.com/Genymobile/scrcpy |
| `ffmpeg` / `ffmpeg.exe` | Media compression | ffmpeg.org |

On Linux, users can install these via their package manager instead.
