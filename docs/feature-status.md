# LiteLink Feature Status — all 86 tracked

Status: ✅ built · 🟡 partial · ❌ not built
Platform: 🖥️ desktop · 📱 companion · 🔗 collaborative

> **Scope decision (2026-08-01): same-network only.** LiteLink focuses on
> LAN / same-network features. All cross-network / internet / cloud features
> are **out of scope** and marked ⛔ below (they need a relay server).

## 1–20 (Core + Productivity + System)

| # | Feature | Status | Where |
|---|---------|--------|-------|
| 1 | Wireless Auto-Discovery | ✅ | mDNS + UDP beacon + NSD + subnet scan; connect-gate with discovered-PC cards |
| 2 | USB Fallback Mode | ✅ | `adb reverse` + `watch_usb` + setup in Connection page |
| 3 | Smart Reconnect | ✅ | BootReceiver + auto-connect + pairing gate |
| 4 | Screen Mirroring (scrcpy) | ✅ | Mirror page + toolbar (shot/record/rotate/vol) |
| 5 | Screen-Off Battery Saver | ✅ | scrcpy `--turn-screen-off` + checkbox |
| 6 | Drag & Drop File Transfer | ✅ | `push_file` + phone file browser pull |
| 7 | Real-Time Notification Mirror | ✅ | NotificationListener → desktop feed |
| 8 | Quick Reply | ✅ | `qr.send` / `qr.reply_last` |
| 9 | Bi-Directional Clipboard | ✅ | `clipboard.get/set/monitor` |
| 10 | File Explorer (phone on PC) | ✅ | Files page browser |
| 11 | Smart Media Offloader | ✅ | `media.offload` |
| 12 | Batch APK Installer | ✅ | Batch dialog + `apk.install` |
| 13 | APK Extractor | ✅ | Extractor dialog + `apk.extract` |
| 14 | Folder Sync (real-time) | ✅ | watched pairs + `folder.push/sync` |
| 15 | Cross-Network File Transfer | ⛔ out of scope | needs relay server |
| 16 | MIUI Debloat Guardian | ✅ | disable/uninstall/enable over adb |
| 17 | Battery Health Dashboard | ✅ | gauge + graph + CSV |
| 18 | Find My Phone | ✅ | `phone.ring` / `stop_ring` |
| 19 | Tether Toggle | ✅ | `tether.get/set` |
| 20 | SMS & Call Archiver | ✅ | `sms.backup` / `calls.backup` → CSV |

## 21–47 (Kenya, Advanced, Internet, Polish)

| # | Feature | Status | Where |
|---|---------|--------|-------|
| 21 | M-Pesa Tracker | ✅ | `mpesa.parse/balance` + table |
| 22 | Low-Data Mode | 🟡 | Settings toggle (behavior partial) |
| 23 | USSD Shortcut | ✅ | dialog + `ussd.send` |
| 24 | Signal Logger | ✅ | `signal.get/subscribe` + graph |
| 25 | Offline Maps Sync | ❌ | LAN phone↔PC, not built |
| 26 | Remote Camera | ✅ | `camera.capture` + Camera Remote page |
| 27 | Macro / Tasker | ✅ | `macro.exec` + Tools macro box |
| 28 | Scheduled Maintenance | ✅ | Maintenance dialog (cache/photos/WA/old/reboot) |
| 29 | WhatsApp Backup | ✅ | `whatsapp.backup` |
| 30 | Universal Clipboard History | ❌ | LAN, not built |
| 31 | Multi-Device | 🟡 | server holds a set, GUI single-peer |
| 32 | Screen Recorder | ✅ | adb `screenrecord` → pull |
| 33 | Internet Screen Sharing | ⛔ out of scope | relay needed |
| 34 | Cloud Device Discovery | ⛔ out of scope | relay needed |
| 35 | Remote Clipboard (Internet) | ⛔ out of scope | relay needed |
| 36 | End-to-End Encryption | 🟡 | TLS + fingerprint exists; AES app-layer not |
| 37 | Pairing via Code | ✅ | 6-digit + discovery beacon carries code |
| 38 | Remote Notification Sync | ⛔ out of scope | cross-network |
| 39 | Shared Folder (Cloud) | ⛔ out of scope | relay needed |
| 40 | Remote Command Execution | 🟡 | local (same-network) works; internet out of scope |
| 41 | System Tray | ✅ | full menu |
| 42 | Dark / Light Mode | ✅ | qt-material + palettes; black dark |
| 43 | Hotkey Triggers | ❌ | desktop, not built |
| 44 | Portable Mode | ✅ | onefile EXE |
| 45 | Crash Recovery | ❌ | desktop, not built |
| 46 | Bundled adb/scrcpy | ✅ | bin/ packaged |
| 47 | Auto-Update Checker | ❌ | desktop, not built |

## 48–86 (Second Screen + Remote Control)

| # | Feature | Status |
|---|---------|--------|
| 48 | Second Screen Duplicate | 🟡 page + FFmpeg frame push; no phone viewer yet |
| 49 | Second Screen Extended | ❌ LAN, not built |
| 50–52 | Second Screen res/fps/bitrate | ✅ dropdowns (UI) |
| 53 | Second Screen touch control | 🟡 input_controller exists |
| 54 | Second Screen keyboard passthrough | 🟡 |
| 55–60 | Media Controller | ✅ play/pause/next/prev/vol/mute/stop |
| 61–71 | Advanced Trackpad | 🟡 1-finger pointer + special keys; gestures not |
| 72–75 | PC Notifications (on phone) | ❌ LAN, not built |
| 76–79 | PC Power | ✅ shutdown/restart/sleep/lock |
| 80–82 | Remote File Browser (phone→PC) | ✅ browse/list/download |
| 83–84 | Remote Keyboard (phone→PC) | ✅ special keys + combos |
| 85 | Game Controller | ❌ LAN, not built |
| 86 | Voice Commands | ❌ LAN, not built |

## Remaining same-network items (2026-08-02)

| Feature | Status |
|---|---|
| 48–54 Second Screen (PC→phone) | ✅ MJPEG stream + phone viewer + Extended mode + touch + keyboard; virtual-display setup documented in `setup.md` Step 6 |
| 61–71 Trackpad | ✅ gestures + dim + haptic + sensitivity/scroll/invert |
| 72–75 PC Notifications | ✅ foreground-window → phone + filter + dismiss + reply |
| 22 Low-Data Mode | ✅ real gating (AC-power check) |
| 31 Multi-Device | 🟡 server set, single-peer GUI |
| 36 E2E Encryption app-layer | 🟡 TLS yes, AES no |

## Out of scope (cross-network / internet)
15 Cross-network transfer · 33 PC-to-PC sharing · 34 Cloud discovery · 35 Remote clipboard (internet) · 38 Remote notif sync · 39 Shared cloud folder · 40 Remote command (internet)
