# LiteLink Companion App

## Overview

The companion app is a Kotlin Android application that runs on your phone. It connects to the LiteLink desktop app over WebSocket and provides access to Android system APIs that aren't available over ADB.

## Features Powered by the Companion App

| Feature | Android API Used |
|---------|-----------------|
| Notification mirror | `NotificationListenerService` |
| Quick reply | `NotificationManager` reply actions |
| Clipboard sync | `ClipboardManager` |
| Folder sync (phone side) | `FileObserver` |
| Find my phone | `AudioManager` + `MediaPlayer` |
| Tether toggle | `ConnectivityManager` |
| SMS/Call reading | `ContentResolver` (Telephony SMS, CallLog) |
| M-Pesa parsing | Same as SMS reading + regex |
| USSD execution | `Intent.ACTION_CALL` + screen capture |
| Battery stats | `BatteryManager` |
| Signal strength | `TelephonyManager` |
| Camera streaming | `Camera2` API |
| Screen recording | `MediaProjection` |

## Requirements for Building

### Option A: Build on PC (Requires ~2.5 GB)

```bash
# Requirements:
# - Java JDK 11+
# - Android Command-Line Tools

cd mobile/LiteLinkCompanion
gradlew assembleDebug
```

The APK will be at: `app/build/outputs/apk/debug/app-debug.apk`

### Option B: Pre-Built APK (Zero Build Tools)

Get the Base64-encoded APK string from the project maintainer, then decode it:

```python
import base64
with open("LiteLinkCompanion.apk", "wb") as f:
    f.write(base64.b64decode("<BASE64_STRING>"))
```

## Installation on Redmi 14 C (MIUI)

1. Transfer `LiteLinkCompanion.apk` to your phone
2. Tap the file to install
3. If blocked, enable **Install from Unknown Sources**

### Grant Notification Access

1. **Settings → Notification & Control Center → Notification Access**
2. Toggle **LiteLinkCompanion** ON

### Prevent MIUI from Killing the App

MIUI aggressively kills background apps. These steps are required:

1. **Settings → Apps → Manage Apps → LiteLinkCompanion**
2. **Battery Saver → No restrictions**
3. **Other Permissions → Display pop-up windows → Allow**
4. **Recent Apps → Lock the app** (long-press, tap lock icon)

## First Run

1. Open the app on your phone
2. Enter your **laptop's local IP address** (find it with `ipconfig` / `ip addr`)
3. Toggle the switch to **ON**
4. The app will start as a foreground service

## Project Structure

```
mobile/
└── LiteLinkCompanion/
    ├── app/
    │   ├── src/main/
    │   │   ├── java/com/yourname/litelinkcompanion/
    │   │   │   ├── MainActivity.kt
    │   │   │   ├── NotificationListener.kt
    │   │   │   └── WebSocketManager.kt
    │   │   ├── res/layout/
    │   │   │   └── activity_main.xml
    │   │   └── AndroidManifest.xml
    │   └── build.gradle.kts
    ├── build.gradle.kts
    └── settings.gradle.kts
```

## Key Files

| File | Purpose |
|------|---------|
| `NotificationListener.kt` | Captures notifications, forwards via WebSocket |
| `WebSocketManager.kt` | OkHttp WebSocket client with auto-reconnect |
| `MainActivity.kt` | IP input + toggle switch UI |
| `AndroidManifest.xml` | Permissions and service declarations |
