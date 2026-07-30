# LiteLink — Complete File Map

Every file in the project, what it does, and why it exists.

---

## Root

| File | Purpose |
|------|---------|
| `README.md` | Project overview — features, quick start, build phases, documentation links. |
| `LICENSE` | MIT license. Open-source permission grant. |
| `.gitignore` | Ignores: Python cache, virtual envs, build artifacts, APK files, IDE configs, OS files. |
| `assets/.gitkeep` | Placeholder for icons, images, UI assets. |

---

## `docs/` — Project Documentation

| File | Purpose |
|------|---------|
| `index.md` | Documentation landing page. Links to all other docs. |
| `setup.md` | First-run guide: installing Python, downloading ADB/scrcpy, enabling USB debugging, granting MIUI permissions, pairing devices. |
| `architecture.md` | System design: component diagram, WebSocket protocol (message types for every feature), connection lifecycle (startup, reconnect, reboot), ADB usage scope, bundled binaries. |
| `features.md` | Complete inventory of all 37 features across 6 tiers. Each entry shows platform ownership (Desktop / Companion / Collaborative) and build status. |
| `companion-app.md` | Android app docs: what each Android API powers, build options (command-line vs Base64), MIUI-specific installation steps, project structure. |
| `development.md` | Contributor guide: setting up dev environment, running, building releases, code style, branch strategy, testing philosophy. |
| `FILE_MAP.md` | *(This file)* — Maps every file in the project to its purpose. |

---

## `desktop/` — Python Desktop Application

### Root

| File | Purpose |
|------|---------|
| `README.md` | Desktop-specific quick start and module overview. |
| `requirements.txt` | Python dependencies: PySide6 (GUI), websockets (server), watchdog (file watching). |
| `pyproject.toml` | PEP 621 project metadata. Defines package name, version, dependencies, and entry point. |
| `run.py` | Convenience launcher. Adds `src/` to Python path and calls `litelink.__main__:main`. Usage: `python run.py`. |

### `src/litelink/` — Main Python Package

| File | Purpose |
|------|---------|
| `__init__.py` | Package marker. Exports `__version__`, `__author__`, `__license__`. |
| `__main__.py` | Entry point for `python -m litelink`. Starts the WebSocket server. |
| `server.py` | **WebSocket server** — runs on `0.0.0.0:8765`. Accepts connections from the companion app. Routes JSON messages (notifications, file changes, commands) between the phone and GUI. Stores connected phones in a set for multi-device support. |
| `adb_utils.py` | **ADB command wrapper** — runs `adb` as a subprocess. Functions: `run_adb_command()`, `get_device_state()`, `get_device_ip()`, `enable_wireless()`, `connect_wireless()`. |
| `folder_sync.py` | **File sync engine** — uses `watchdog.Observer` to watch desktop folders. `SyncHandler` catches create/modify/delete/move events, debounces them (500ms), computes MD5 hashes to detect real changes, and encodes files as Base64 for WebSocket transport. `FolderSync` class manages start/stop of the observer. |

### `src/litelink/gui/` — PySide6 User Interface

| File | Purpose |
|------|---------|
| `__init__.py` | Package marker (empty). |
| `app.py` | **Main window** — `QMainWindow` subclass. Sets window title, minimum size, holds the central widget layout. Starter stub with a "Mirror Screen" button. |
| `mirror.py` | **Mirror panel** — `MirrorPanel` widget with a button that launches `scrcpy` via `subprocess.Popen`. Looks for scrcpy binary in `desktop/bin/` (cross-platform path resolution). |
| `notifications.py` | **Notification sidebar** — `NotificationSidebar` widget with a `QListWidget`. `add_notification()` appends entries and caps the list at 50 items with auto-eviction. |
| `settings.py` | **Settings dialog** — `QDialog` with an IP address input field and a Save button. Laptop IP is used by the companion app to connect. |

### `bin/` — External Binaries (User Downloads)

| File | Purpose |
|------|---------|
| `.gitkeep` | Placeholder. Users place adb, scrcpy, ffmpeg executables here. |

---

## `mobile/` — Android Companion (Kotlin)

### Root

| File | Purpose |
|------|---------|
| `README.md` | Mobile quick start: building the APK, requirements, setup on MIUI. |

### `LiteLinkCompanion/` — Android Studio Project Root

| File | Purpose |
|------|---------|
| `build.gradle.kts` | **Project-level Gradle** — declares Android Gradle Plugin (8.2.2) and Kotlin (1.9.22) plugin versions. |
| `settings.gradle.kts` | **Project settings** — plugin repositories, dependency resolution repositories, includes `:app` module. |
| `gradle/wrapper/gradle-wrapper.properties` | **Gradle wrapper config** — pins Gradle version to 8.5 for reproducible builds. |

### `app/` — Android Module

| File | Purpose |
|------|---------|
| `build.gradle.kts` | **Module Gradle** — defines namespace, SDK versions (min 24, target 34), dependencies: OkHttp 4.12 (WebSocket client), Gson 2.10 (JSON), Coroutines 1.7 (async), Material Design. |

### `app/src/main/` — Android Source

| File | Purpose |
|------|---------|
| `AndroidManifest.xml` | App manifest — declares INTERNET, FOREGROUND_SERVICE, BIND_NOTIFICATION_LISTENER_SERVICE permissions. Registers `MainActivity` and `NotificationListener` (with the required `BIND_NOTIFICATION_LISTENER_SERVICE` permission attribute). |

### `app/src/main/java/com/yourname/litelinkcompanion/` — Kotlin Sources

| File | Purpose |
|------|---------|
| `MainActivity.kt` | **Main UI** — `AppCompatActivity` with a simple layout: IP address input field + toggle switch for enabling notification mirroring. |
| `NotificationListener.kt` | **Notification capturer** — extends `NotificationListenerService`. `onNotificationPosted()` extracts package name, title, and text from incoming notifications. Logs them. *(Will send via WebSocketManager.)* |
| `WebSocketManager.kt` | **WebSocket client** — uses OkHttp to connect to `ws://<host>:8765`. Ping interval of 30 seconds. Callbacks for connect/disconnect. `sendNotification()` builds JSON and sends it. `disconnect()` cleanly closes the socket. |

### `app/src/main/res/` — Android Resources

| File | Purpose |
|------|---------|
| `layout/activity_main.xml` | XML layout for the main activity — centered LinearLayout with title, IP input field, and toggle switch. |
| `values/strings.xml` | String resources — app name "LiteLink". |

---

## `scripts/` — Build & Utility Scripts

| File | Purpose |
|------|---------|
| `build_desktop.sh` | **Linux build** — installs dependencies + PyInstaller, builds standalone executable. Output: `desktop/dist/LiteLink`. |
| `build_desktop.bat` | **Windows build** — same as above. Output: `desktop\dist\LiteLink.exe`. Pauses after completion. |

---

## Summary: File Count

| Directory | Files |
|-----------|-------|
| Root | 3 |
| `docs/` | 7 |
| `desktop/` | 14 |
| `mobile/` | 10 |
| `scripts/` | 2 |
| `assets/` | 1 |
| **Total** | **37** |
