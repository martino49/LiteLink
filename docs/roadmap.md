# LiteLink Feature Roadmap (v2)

Expanded from the 15-page desktop app / 21-screen companion app into the full
remote-control suite. Build order below is **incremental** — each phase lands a
working, testable slice on the existing WebSocket + command bridge before the
next begins.

## Legend
- 🖥️ Desktop only · 📱 Companion only · 🔗 Collaborative (both sides)
- ✅ done · 🔨 in progress · ⬜ planned

## Phase 1 — Core Engine (done)
1. ✅ Wireless Auto-Discovery (mDNS) — 🔗
2. ✅ USB Fallback Mode (adb reverse) — 🔗
3. ✅ Smart Reconnect (boot receiver) — 🔗
4. ✅ Screen Mirroring (scrcpy) — 🖥️
5. ✅ Screen-Off Battery Saver — 🖥️
6. ✅ Drag & Drop File Transfer (push file) — 🔗

## Phase 2 — Productivity (done)
7. ✅ Real-Time Notification Mirror — 🔗
8. ✅ Quick Reply from laptop — 🔗
9. ✅ Bi-Directional Clipboard — 🔗
10. ✅ File Explorer (phone on PC) — 🖥️
11. ✅ Smart Media Offloader — 🔗
12. ✅ Batch APK Installer — 🔗
13. ✅ APK Extractor — 🔗
14. ✅ Folder Sync (real-time, watched pairs) — 🔗

## Phase 3 — System & Health (done)
15. ✅ MIUI Debloat Guardian — 🔗
16. ✅ Battery Health Dashboard — 🔗
17. ✅ Find My Phone — 🔗
18. ✅ Mobile Data Tether Toggle — 🔗
19. ✅ SMS & Call Log Archiver — 🔗
20. ✅ M-Pesa Statement Tracker — 🔗
21. ✅ Low-Data Mode — 🖥️
22. ✅ USSD Shortcut — 🔗
23. ✅ Network Signal Logger — 🔗

## Phase 4 — Advanced + Internet (done / partial)
24. ✅ Remote Camera View — 🔗
25. ✅ Tasker / Macro Integration — 🔗
26. ✅ Scheduled Maintenance Mode — 🔗
27. ✅ WhatsApp/Telegram DB Backup — 🔗
28. ⬜ Universal Clipboard History — 🖥️
29. ✅ Multi-Device (server holds a set) — 🖥️
30. ✅ Screen Recorder — 🔗
31. ⬜ Cross-Network File Transfer (VPS relay) — 🔗
32. ⬜ End-to-End Encryption hardening — 🔗
33. ✅ Device Pairing via 6-digit code — 🔗

## Phase 5 — UX & Polish (done / partial)
34. ✅ System Tray Icon — 🖥️
35. ✅ Dark / Light Mode — 🖥️
36. ⬜ Hotkey Triggers — 🖥️
37. ✅ Portable Mode (onefile exe) — 🖥️
38. ⬜ Crash Recovery (auto-restart) — 🖥️
39. ✅ Built-in ADB & scrcpy — 🖥️
40. ⬜ Auto-Update Checker — 🖥️

## Phase 6 — Phone → PC quick wins 🔨 (current)
The desktop already synthesizes input via Windows `SendInput`. These reuse it.
- **41. Media Controller** — phone controls PC playback (play/pause/next/prev/
  volume/mute) via `VK_MEDIA_*` keys. 🔗
- **42. PC Power Controls** — shutdown/restart/sleep/lock from phone. 🔗
- **43. Remote File Browser (Phone→PC)** — browse + download + delete PC files
  from the phone (configurable roots). 🔗
- **44. Remote Keyboard** — named keys (Ctrl/Shift/Alt/Win/F1-F12/arrows) +
  text. 🔗
- **45. Advanced Trackpad gestures** — two-finger scroll/right-click, three-
  finger app-switch, pinch → mouse wheel / ctrl+wheel. 🔗

## Phase 7 — PC → Phone (experimental)
- **46. Second Screen (Duplicate)** — FFmpeg capture → H.264 stream → phone
  player. 🔗
- **47. Second Screen (Extended)** — virtual display driver → real 2nd desktop
  region streamed to phone. Driver: `VirtualDrivers/Virtual-Display-Driver`
  (winget / `xrandr --setmonitor` on Linux X11). Setup: `setup.md` Step 6. 🔗
- **48. PC Notifications (on phone)** — Windows toast capture → phone. 🔗

## Phase 8 — Experimental input modes
- **49. Game Controller Mode** — virtual gamepad → XInput/keyboard mapping. 🔗
- **50. Voice Commands** — Android SpeechRecognizer → desktop command parser. 🔗

## Companion app new pages (tracked with each phase)
| # | Page | Phase |
|---|------|-------|
| 22 | Media Controller | 6 |
| 23 | PC Power | 6 |
| 24 | Remote File Browser | 6 |
| 25 | Remote Keyboard | 6 |
| 26 | Trackpad (gestures) | 6 |
| 27 | Second Screen | 7 |
| 28 | PC Notifications | 7 |
| 29 | Game Controller | 8 |
| 30 | Voice Commands | 8 |

## Where new code lives
- Desktop input synthesis: `desktop/src/litelink/input_controller.py` (extend)
- Desktop PC-command module: `desktop/src/litelink/pc_controller.py` (new)
- Desktop GUI: add a "PC Remote" page under `desktop/src/litelink/gui/`
- Message routing: `desktop/src/litelink/server.py` `_dispatch` (new `pc` type)
- Companion: new activities + `CommandCenter` handlers per feature
