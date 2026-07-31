# AI.md — Instructions for AI Agents Working on LiteLink

> Read this file first. It captures the project's architecture, the **exact working toolchain**, and the hard-won gotchas discovered during real setup on the developer's machine. Do not "re-discover" environment facts by brute force — they are documented below.

## 1. What LiteLink Is

A local, offline-first **PC ↔ Android companion suite** (like a self-hosted KDE Connect). No cloud, no root, no emulator.

- **Desktop side** (`desktop/`): Python + PySide6 (GUI planned) + a `websockets` WebSocket hub server on `0.0.0.0:8765`. The phone connects to it over the LAN.
- **Companion side** (`mobile/LiteLinkCompanion/`): Kotlin app that connects as a WebSocket **client** to the desktop hub and forwards phone events (currently: notifications).
- The phone's Internet protocol against the desktop is **`ws://<laptop-lan-ip>:8765`** — always a client→server model. Desktop→phone control is done via **adb** (scrcpy, input, install, etc.).

The developer tests on a **physical Xiaomi Redmi 14C (Android 16 / API 36)**, never an emulator.

## 2. Repository Layout

```
├── README.md              # Overview + build phases
├── docs/
│   ├── features.md        # Feature inventory #1–44 + build order
│   ├── architecture.md    # Design + protocol
│   ├── companion-app.md   # Building/installing the APK on MIUI
│   ├── development.md     # Dev environment guide (generic)
│   ├── setup.md           # First-run install guide
│   └── index.md           # Docs index
├── desktop/
│   ├── run.py             # Entry point (adds src/ to path, calls main)
│   └── src/litelink/
│       ├── __main__.py    # CLI entry: MUST asyncio.run(start_server())
│       ├── server.py      # WebSocket hub, connection handler
│       └── adb_utils.py   # adb command wrappers
├── mobile/LiteLinkCompanion/
│   ├── build.gradle.kts   # AGP 8.2.2, Kotlin 1.9.22
│   ├── settings.gradle.kts
│   ├── app/build.gradle.kts  # compileSdk 34, minSdk 24, buildTools 36.0.0
│   └── app/src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/yourname/litelinkcompanion/
│       │   ├── MainActivity.kt
│       │   ├── ConnectionService.kt
│       │   ├── NotificationListener.kt
│       │   └── WebSocketManager.kt
│       └── res/layout/activity_main.xml
└── scripts/               # build_apk.sh (Linux), build_apk_pure.py (NOT usable — ignore)
```

## 3. The Working Toolchain (Windows, THIS machine)

These exact facts were verified. Do not guess.

- **Gradle 8.5** (pinned by `gradle/wrapper/gradle-wrapper.properties`, but the wrapper jar/scripts are NOT in the repo).
  - Installed at `C:\Users\Derivo\DevTools\gradle-8.5`. Binary: `C:\Users\Derivo\DevTools\gradle-8.5\bin\gradle.bat`.
  - **Gradle 8.5 requires JDK 17–21.** It does NOT work with Android Studio's bundled JBR (OpenJDK 25 at `C:\Program Files\Android\Android Studio\jbr`).
- **JDK 17 (Temurin)**: `C:\Users\Derivo\DevTools\jdk-17.0.20+8`.
- **Android SDK**: `C:\Users\Derivo\AppData\Local\Android\Sdk` — platforms `android-34` (auto-installed by AGP) and `android-37`, build-tools `36.0.0`, platform-tools (adb) `37.0.1`. Licenses accepted.
- **Python**: `python` → 3.13.2 (use this). Desktop venv at `desktop\.venv` with `websockets` 17.0.1 installed (PySide6 install was aborted due to slow network — it is the only missing desktop dep; the GUI is a skeleton anyway).

### Build the APK (verified working)

```powershell
$env:JAVA_HOME = "C:\Users\Derivo\DevTools\jdk-17.0.20+8"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
& "C:\Users\Derivo\DevTools\gradle-8.5\bin\gradle.bat" assembleDebug   # run in mobile/LiteLinkCompanion
```

- Output: `mobile/LiteLinkCompanion/app/build/outputs/apk/debug/app-debug.apk` (~6.4 MB).
- `local.properties` already points at the SDK. `gradle.properties` sets AndroidX + jvmargs.
- Always use **debug** builds; release signing is not configured.

### Run the desktop server (verified working)

```powershell
cd desktop
.\.venv\Scripts\python.exe -u run.py     # -u = unbuffered, so prints appear immediately in the console
```

- The console IS the UI right now — it prints `[Phone connected] ...`, `[<app>] <title>` + text for each notification. No GUI exists yet.
- Default bind: `0.0.0.0:8765`. Set `HOST`/`PORT` by editing `server.py`.

### Windows Firewall (verified)

- The dev's Wi-Fi network ("Murungaasiba") is **Public profile**, which blocks inbound by default. An inbound allow rule for TCP 8765 must exist:
  `New-NetFirewallRule -DisplayName "LiteLink 8765" -Direction Inbound -Protocol TCP -LocalPort 8765 -Action Allow -Profile Any` (needs admin).
- It is already created on this machine.

## 4. Architecture & WebSocket Protocol

- Phone connects as a WebSocket **client** to the desktop hub; the hub tracks connections in `CONNECTED_PHONES` (a `set` of websockets).
- Messages are **JSON** with a `type` discriminator:
  - `{"type": "notification", "app": "<app label>", "title": "...", "text": "...", "timestamp": <ms>}` — phone → desktop. The desktop currently echoes `{"status": "received"}`.
  - `{"type": "phone_status", ...}` — reserved (not sent yet).
  - Plan: server routes messages to the GUI when it exists.
- **Mobile side** (`WebSocketManager`, a Kotlin `object` singleton):
  - `connect(host, port=8765)` / `disconnect()` / `sendNotification(app, title, text)`.
  - Auto-reconnect with exponential backoff 5s → 60s. `isConnected` flag + `addConnectionListener`/`removeConnectionListener` (multi-listener, NOT a single callback).
  - `ConnectionService` (foreground service, `dataSync` type) owns the connection; shows an ongoing notification with connect state.
  - `MainActivity`: EditText `ipInput` + Switch `toggleSwitch` + `statusText`. On enable → `ConnectionService.start(ctx, host)`; saves host/enabled to SharedPreferences. **Note:** restoring the toggle from prefs does NOT trigger the listener — the service only starts on a user toggle action.
  - `NotificationListener`: posts → `WebSocketManager.sendNotification(...)`.
- **Desktop `__main__.py`:** `asyncio.run(start_server())`. (This was a real bug: it used to call the coroutine without awaiting, so the server silently never started.)

## 5. Gotchas That Cost Real Time (verify before "fixing" these again)

1. **Android cleartext policy**: targetSdk ≥ 28 blocks plain `ws://`. The manifest has `android:usesCleartextTraffic="true"` — required for LAN `ws://`. Do not remove it (unless you add WSS, feature #44).
2. **MIUI/HyperOS notification-listener grant**: `settings put secure enabled_notification_listeners ...` was **ignored** by the system. The working method is the supported command:
   `adb shell cmd notification allow_listener com.yourname.litelinkcompanion/com.yourname.litelinkcompanion.NotificationListener`
   Verify with `adb shell dumpsys notification` → look for the component under "Live notification listeners".
3. **Gradle 8.5 + Java 25 = fail.** Always set JAVA_HOME to the JDK 17 at `DevTools\jdk-17.0.20+8`.
4. **`buildToolsVersion = "36.0.0"`** is set in `app/build.gradle.kts` so AGP 8.2.2 reuses the installed build-tools instead of downloading 34.0.0.
5. **`dependencyResolutionManagement`** (not `dependencyResolution`) in `settings.gradle.kts` — was a typo that broke sync.
6. **No gradle wrapper in repo** — use the extracted Gradle 8.5 binary directly. (Running `gradle wrapper` in the project would regenerate it if ever needed.)
7. **Xiaomi USB install** shows a confirmation prompt on the phone; if `INSTALL_FAILED_USER_RESTRICTED` appears, the user must confirm on the phone (or enable "Install via USB" in Developer options).
8. **`scripts/build_apk_pure.py` is a naive hack and does not produce a working APK** — never use it.

## 6. Test Environment (known-good numbers)

- Laptop LAN IP: `192.168.100.110` (may change; re-query with `Get-NetIPConfiguration`).
- Phone: Xiaomi Redmi 14C (`XSKZSGFA6DUGTWBI`, `pond_global`), Wi-Fi IP `192.168.100.108`, ADB authorized over USB.
- adb binary: `C:\Users\Derivo\AppData\Local\Android\Sdk\platform-tools\adb.exe`.
- Server log (when launched by this session): `C:\Users\Derivo\AppData\Local\Temp\opencode\litelink-server.log`.

### End-to-end verification recipe

```powershell
# 1. server up (unbuffered)
cd desktop; .\.venv\Scripts\python.exe -u run.py

# 2. phone app: enter laptop IP, toggle ON -> statusText shows "Connected"
# 3. confirm server sees it: log prints "[Phone connected] ..."
# 4. fire a real notification:
adb shell cmd notification post -t "Test" mytag "Hello from the phone"
# 5. expect server log: "[<app>] Test" / "    Hello from the phone"
```

## 7. Current Status

**Working today:**
- Full build pipeline (JDK 17 + Gradle 8.5 → debug APK), adb install, live phone↔desktop WebSocket connection with auto-reconnect.
- **Notification mirror** (feature #7) end-to-end on the real device. Desktop shows them in the console window.

**Not built:** desktop GUI, screen mirroring/scrcpy, all file features, clipboard, quick reply, and everything else in the roadmap. `server.py` handler has a `TODO` to route to a GUI.

## 8. Roadmap (see `docs/features.md` #1–44)

- **Phase 1** (in progress): auto-discovery/mDNS + QR pairing, USB fallback, smart reconnect, screen mirroring (scrcpy), screen-off battery saver, phone-as-input, **WSS + pairing secret (#44)**.
- **Phase 2**: drag-drop files, file explorer, folder sync, APK installer/extractor, media offloader.
- **Phase 3**: notifications (done), quick reply, clipboard, call popups (#40), notification history/filtering (#41).
- **Phase 4**: MIUI debloat, battery dashboard, SMS/call archiver, anti-theft (#42).
- **Phase 5** (Kenya): M-Pesa statement tracker, USSD, tether toggle, signal logger, offline maps.
- **Phase 6**: remote camera/webcam (#25), screen recorder, macros (#43), audio streaming (#39), tray, hotkeys, themes, portable.

Priority for the next session (recommended): **QR pairing / mDNS auto-discovery**, then **drag-and-drop file transfer**, then **phone-as-webcam**.

## 9. Rules for the AI

- Ask the user before committing, before touching the phone's settings, and before network/firewall changes that need admin.
- Run builds with the exact commands in §3. Report the real error if it fails — do not retry blindly.
- The dev's network is slow/flaky: prefer small targeted downloads; use `curl.exe -L --retry 10 --retry-all-errors` for large files.
- Keep desktop changes PEP 8 with type hints; Kotlin per official conventions; coroutines for async.
- **No root / no cloud / battery-conscious** are hard constraints (see `docs/development.md` "Project Ideology").
- When a task is ambiguous (device, network, install), ask — one short question beats a wrong assumption.
