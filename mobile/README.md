# LiteLink Companion App (Android)

Kotlin Android application that pairs with the LiteLink desktop app.

## Features

- Captures notifications via `NotificationListenerService`
- Forwards them to the desktop over WebSocket
- Monitors folders via `FileObserver` for folder sync
- Syncs clipboard via `ClipboardManager`
- Executes commands from desktop (ring, tether, USSD, etc.)

## Building

```bash
cd LiteLinkCompanion
gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## Requirements

- Android 7.0+ (API 24)
- Java JDK 11+
- Gradle 8+

## Setup on MIUI

After installing, follow the [setup guide](../docs/setup.md) to:
1. Enable Notification Access
2. Disable battery optimization
3. Lock the app in recent apps
