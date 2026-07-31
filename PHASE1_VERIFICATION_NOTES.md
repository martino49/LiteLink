# LiteLink Phase 1 — Verification Notes

Session date: 2026-07-31
Test device: Xiaomi Redmi 14C (Android 16 / API 36, HyperOS), serial `XSKZSGFA6DUGTWBI`
Laptop: `192.168.100.110` (Wi-Fi), phone Wi-Fi: `192.168.100.108` — both on SSID `Murungaasiba`
GUI interpreter: project venv `desktop\.venv\Scripts\python.exe` (3.13.2; PySide6 6.11.1, zeroconf 0.150.0, cryptography)

---

## 1. Verified end-to-end

### WSS + pairing (#44)
- Desktop WSS server listens on `0.0.0.0:8765` (self-signed ECDSA cert, TOFU fingerprint pinning on phone).
- Pairing flow verified: `{"type":"pair","code":685572}` -> `{"type":"pair_ok"}`, app shows **Connected**.
- App connects over Wi-Fi **or** USB `adb reverse` localhost fallback.

### Phone-as-input (#38)
- Touchpad swipe on the phone moved the desktop cursor in the matching direction
  (`adb shell input swipe` 300,900->500,550 produced a desktop cursor move 614,447->901,199).
- Pointer/scroll/key/text messages go over the established WSS and are executed via SendInput.

### scrcpy mirroring (#4)
- `mirror.launch()` starts scrcpy v4.1 (`desktop\bin\scrcpy-win64-v4.1\`), window appears showing the phone.
- **Important:** scrcpy v4.1 dropped the `--adb` flag. The desktop passes the adb path via the
  `ADB` environment variable (`env["ADB"] = adb` in `mirror.py`).

### Screen-off battery saver (#5)
- Implemented and verified in `mirror.py`.
- **MIUI/HyperOS ignores scrcpy's in-band `--turn-screen-off`** (the phone screen stayed ON even
  with the flag set and `stay_on_while_plugged_in` cleared).
- **`--stay-awake` directly conflicts with screen-off**: it runs `svc power stayon true`
  (sets `stay_on_while_plugged_in=7`), which re-wakes the screen shortly after it sleeps.
- Fix: when `turn_screen_off=True`, the scrcpy command omits `--stay-awake`, and a watchdog thread
  (`_enforce_screen_off`) polls `adb shell dumpsys display` (`mScreenState=`) every 5s for ~30s and
  sends `adb shell input keyevent KEYCODE_POWER` whenever the screen is not OFF. Verified: screen
  OFF from ~t+10s and stayed OFF for the full 45s window while scrcpy kept streaming.

### USB fallback via `adb reverse` (#2)
- `adb reverse tcp:8765 tcp:8765` verified. The app tries the Wi-Fi host first, then `localhost`
  (`WebSocketManager.connect(listOf(host, "localhost"))`).
- Observed a live connection `127.0.0.1:54684 -> 127.0.0.1:8765` with the app log showing
  `Connecting wss://localhost:8765` -> `Pairing OK` -> `Connected`. USB reverse fallback works.

### Wi-Fi WSS path
- After a clean GUI restart the app auto-reconnected over Wi-Fi:
  `192.168.100.110:8765 <- 192.168.100.108:36042` (established, forwarding notifications).
- Phone->desktop unicast TCP to :8765 confirmed working from the phone via `toybox nc`.

---

## 2. Blocked / not verifiable on this network + root causes

### mDNS auto-discovery (#1) — BLOCKED by router, not a LiteLink bug
- Desktop side fully verified: mDNS advertises `LiteLink-DESKTOP-H2BKEHV._litelink._tcp.local.`
  @ 192.168.100.110:8765 (confirmed cross-process with a standalone zeroconf browser).
- Phone side: tapping **DISCOVER (MDNS)** sets status to "Searching for desktop...", but the
  desktop NEVER receives any mDNS packet from the phone.
- Proof of network filtering: a 5353 sniffer on the desktop captured the desktop's own mDNS
  announcements (loopback) but **zero** packets from the phone during 30-40s of active searching.
  Meanwhile phone->desktop unicast TCP works fine, and phone->gateway works.
- Conclusion: the `Murungaasiba` router filters wireless **multicast** between clients
  (multicast/client isolation). mDNS discovery cannot work on this network without router changes.
  The IP-based connect path (works) and USB reverse (works) are the usable fallbacks.

### Reconnect on boot (#3) — code correct, live broadcast blocked by Android 16
- Manifest: `RECEIVE_BOOT_COMPLETED` + `BootReceiver` with the `BOOT_COMPLETED` intent filter
  (exported=false is fine — system broadcasts are still delivered).
- `BootReceiver.onReceive` reads `enabled`/`host` prefs and starts `ConnectionService`
  (currently enabled=true, host=192.168.100.110, pairing_code=685572).
- Could NOT simulate the broadcast on-device: `am broadcast -a android.intent.action.BOOT_COMPLETED`
  from `adb shell` is denied (`SecurityException`, protected broadcast), and `adb root` is refused
  on this production build. Verified by manifest + code inspection only.

### On-device UI automation — flaky
- `uiautomator dump` intermittently returns `null root node` (MIUI). Re-dump or verify
  `topResumedActivity` after every tap.
- Other apps randomly take the foreground (observed: Facebook Lite, launcher), which made several
  test taps land on the wrong app.

---

## 3. Code changes made this session

### `desktop\src\litelink\mirror.py`
- Launch command drops `--stay-awake` when `turn_screen_off=True` (stay-on re-wakes the screen).
- Added `_enforce_screen_off` watchdog thread (KEYCODE_POWER fallback for MIUI) and helpers
  `_screen_state`, `_send_keyevent`, `_adb_cmd`.

### `desktop\src\litelink\gui.py`
- Added `_watch_usb` daemon thread: every 5s checks `adb devices` and `adb reverse --list`;
  re-runs `setup_usb_reverse()` when the `tcp:8765 tcp:8765` rule goes missing or a device
  (re)appears. **Covers the unplug/replug case where the reverse rule is lost.**
  NOTE: `adb devices` alone is insufficient — it auto-starts the server, so brief replugs can be
  missed; the `reverse --list` check is the reliable trigger.
- `_watch_usb` is started in `run()` alongside the existing one-shot `setup_usb_reverse()`.

---

## 4. Environment quirks & gotchas

- **GUI must run from the project venv**: `desktop\.venv\Scripts\python.exe`. The system Pythons
  lack pieces of the stack (3.13 has PySide6 but NOT zeroconf/cryptography; 3.12/3.14 have neither).
- Launch GUI detached via WMI + a `.bat` redirect (background GUI processes die when the
  foreground shell command times out otherwise). Pattern:
  `Invoke-CimMethod Win32_Process Create -Arguments @{ CommandLine = 'cmd.exe /c <bat>' }`
  with the bat running the venv python on `run.py`.
- scrcpy's bundled adb is at `desktop\bin\scrcpy-win64-v4.1\adb.exe` and is what
  `tools.find_adb()` returns.
- The phone may be unplugged at any time; re-check `adb devices` before driving the device.
- Pairing code is currently `685572` (`desktop\data\pairing_code.txt`).

---

## 5. Next steps

1. **USB watcher (`_watch_usb`)** — implemented but not yet re-verified after the `reverse --list`
   fix. Simulate unplug by `adb kill-server` then `adb start-server`, and confirm the rule is
   restored automatically and the app reconnects via localhost. (Testing paused per request.)
2. **mDNS** — cannot be fixed in code on this network; requires disabling multicast/client
   isolation on the `Murungaasiba` router, or re-testing on another network/AP.
3. **Boot reconnect** — code/registration verified; do a real reboot test when convenient
   (enable stays on; host pref saved; service should auto-start and reconnect).
4. **Screen-off** — re-run the full `mirror.launch(turn_screen_off=True)` sanity check after the
   next GUI restart (new code is loaded only after the GUI restarts).
5. Rebuild/install the companion app is NOT needed — no mobile code changed this session.
