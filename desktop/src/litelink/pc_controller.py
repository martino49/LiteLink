"""PC Remote controller — turns phone commands into desktop actions.

Handles phone->PC message types (sent by the companion over the same
WebSocket that carries `input` events):
  {"type":"pc","cmd":"media.play_pause"}                    media keys
  {"type":"pc","cmd":"power.shutdown"}                       shutdown/restart/sleep/lock
  {"type":"pc","cmd":"files.list","path":"C:\\Users"}        browse PC folders
  {"type":"pc","cmd":"files.get","path":"C:\\x\\y.txt"}       push a PC file to the phone
  {"type":"pc","cmd":"mirror.start"}                         launch scrcpy
  {"type":"pc","cmd":"ring.pc"}                              make the PC beep loudly
"""

import logging
import os
import subprocess
import threading
import time

from . import input_controller, mirror

log = logging.getLogger("litelink.pc")

# The active Hub (set by the GUI/server) so we can reply to the phone.
_hub = None


def set_hub(hub):
    global _hub
    _hub = hub

# Configurable roots the phone may browse. "." = the desktop user folder.
ALLOWED_ROOTS = [os.path.expanduser("~"), "C:\\Users\\Public"]

# Media + special keys via Windows virtual-key codes
VK_MEDIA_PLAY_PAUSE = 0xB3
VK_MEDIA_NEXT_TRACK = 0xB0
VK_MEDIA_PREV_TRACK = 0xB1
VK_VOLUME_UP = 0xAF
VK_VOLUME_DOWN = 0xAE
VK_VOLUME_MUTE = 0xAD
VK_MEDIA_STOP = 0xB2


def _inside_root(path):
    """Ensure the requested path is inside one of the allowed roots."""
    p = os.path.abspath(path)
    for root in ALLOWED_ROOTS:
        r = os.path.abspath(root)
        if p == r or p.startswith(r + os.sep):
            return True
    return False


def _media(vk):
    input_controller._tap_key(vk)


def handle(message):
    """Dispatch a {'type':'pc', ...} message. Runs on the bridge thread."""
    cmd = message.get("cmd", "")
    params = message.get("params") or {}
    try:
        # ---- media
        if cmd == "media.play_pause":
            _media(VK_MEDIA_PLAY_PAUSE)
        elif cmd == "media.next":
            _media(VK_MEDIA_NEXT_TRACK)
        elif cmd == "media.prev":
            _media(VK_MEDIA_PREV_TRACK)
        elif cmd == "media.stop":
            _media(VK_MEDIA_STOP)
        elif cmd == "media.volume_up":
            _media(VK_VOLUME_UP)
        elif cmd == "media.volume_down":
            _media(VK_VOLUME_DOWN)
        elif cmd == "media.mute":
            _media(VK_VOLUME_MUTE)
        # ---- power
        elif cmd == "power.shutdown":
            subprocess.Popen(["shutdown", "/s", "/t", "0"],
                             creationflags=subprocess.CREATE_NO_WINDOW)
        elif cmd == "power.restart":
            subprocess.Popen(["shutdown", "/r", "/t", "0"],
                             creationflags=subprocess.CREATE_NO_WINDOW)
        elif cmd == "power.sleep":
            subprocess.Popen(["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"],
                             creationflags=subprocess.CREATE_NO_WINDOW)
        elif cmd == "power.lock":
            subprocess.Popen(["rundll32.exe", "user32.dll,LockWorkStation"],
                             creationflags=subprocess.CREATE_NO_WINDOW)
        # ---- files
        elif cmd == "files.list":
            _list_files(params.get("path", ""))
        elif cmd == "files.get":
            _send_file(params.get("path", ""))
        # ---- mirror
        elif cmd == "mirror.start":
            threading.Thread(target=_start_mirror, daemon=True).start()
        elif cmd == "mirror.stop":
            mirror.stop()
        # ---- second screen stream
        elif cmd == "screen.stream.start":
            from . import screen_stream
            screen_stream.start(
                mode=params.get("mode", "duplicate"),
                monitor=int(params.get("monitor", 0)),
                fps=float(params.get("fps", 8.0)),
                scale=float(params.get("scale", 0.5)),
                quality=int(params.get("quality", 55)),
            )
            _reply("screen.stream.start", True, {"status": "streaming"})
        elif cmd == "screen.stream.stop":
            from . import screen_stream
            screen_stream.stop()
            _reply("screen.stream.stop", True, {"status": "stopped"})
        # ---- find my PC
        elif cmd == "ring.pc":
            _ring_pc(params.get("seconds", 5))
        elif cmd == "ring.pc.stop":
            _stop_ring()
        # ---- voice commands
        elif cmd == "voice.command":
            _voice(params.get("text", ""))
        # ---- clipboard history
        elif cmd == "clipboard.history":
            from .gui.clipboard_history import load
            _reply("clipboard.history", True, {"items": load()})
        elif cmd == "clipboard.history.clear":
            from .gui.clipboard_history import clear
            clear()
            _reply("clipboard.history.clear", True, {})
        # ---- pc notification filter (feature 75)
        elif cmd == "pc.set_notif_filter":
            from .gui import pc_notifications
            _hub_apply_filter(params.get("keywords", ""))
            _reply("pc.set_notif_filter", True, {})
        # ---- pc notification dismiss (feature 73)
        elif cmd == "pc.dismiss":
            # minimize the foreground window to "dismiss" the notification source
            try:
                import ctypes
                user32 = ctypes.WinDLL("user32")
                user32.ShowWindow(user32.GetForegroundWindow(), 6)  # SW_MINIMIZE
                _reply("pc.dismiss", True, {})
            except Exception as e:
                _reply("pc.dismiss", False, {"error": str(e)})
        # ---- send messages to PC
        elif cmd == "messages.push":
            _save_messages(params)
        # ---- low-data mode (feature 22)
        elif cmd == "pc.low_data":
            _set_low_data(bool(params.get("enabled", False)))
            _reply("pc.low_data", True, {"enabled": _low_data()})
        else:
            log.info("unknown pc cmd: %s", cmd)
    except Exception as e:
        log.warning("pc handle failed (%s): %s", cmd, e)


def _reply(cmd, ok, data):
    """Send a result back to the phone."""
    global _hub
    if _hub is None:
        return
    payload = {"type": "pc_result", "cmd": cmd, "ok": ok, "data": data or {}}
    _hub._schedule(_hub._send(payload))


def _hub_apply_filter(keywords):
    """Apply PC notification keywords to the running monitor."""
    global _hub
    if _hub is None:
        return
    monitor = getattr(_hub, "pc_notif_monitor", None)
    if monitor is not None:
        monitor.set_keywords([k.strip() for k in keywords.split(",") if k.strip()])


# Low-data mode (feature 22): when on, large sync/offload operations are
# skipped unless the laptop is on AC power.
_low_data_enabled = False


def _low_data():
    return _low_data_enabled


def _set_low_data(enabled):
    global _low_data_enabled
    _low_data_enabled = enabled


def low_data_allowed():
    """True if transfers should proceed under low-data mode."""
    if not _low_data_enabled:
        return True
    # only allow on AC power
    import ctypes
    class SYSTEM_POWER_STATUS(ctypes.Structure):
        _fields_ = [("ACLineStatus", ctypes.c_ubyte),
                    ("BatteryFlag", ctypes.c_ubyte),
                    ("BatteryLifePercent", ctypes.c_ubyte),
                    ("Reserved1", ctypes.c_ubyte),
                    ("BatteryLifeTime", ctypes.c_ulong),
                    ("BatteryFullLifeTime", ctypes.c_ulong)]
    try:
        status = SYSTEM_POWER_STATUS()
        if ctypes.windll.kernel32.GetSystemPowerStatus(ctypes.byref(status)):
            return status.ACLineStatus == 1
    except Exception:
        pass
    return True


def _list_files(path):
    """List a directory and reply to the phone."""
    try:
        if not _inside_root(path):
            _reply("files.list", False, {"error": "not allowed"})
            return
        entries = []
        for name in sorted(os.listdir(path)):
            full = os.path.join(path, name)
            is_dir = os.path.isdir(full)
            try:
                size = os.path.getsize(full) if not is_dir else 0
            except OSError:
                size = 0
            entries.append({"name": name, "path": full, "is_dir": is_dir, "size": size})
        _reply("files.list", True, {"path": path, "entries": entries})
    except OSError as e:
        _reply("files.list", False, {"error": str(e)})


def _send_file(path):
    """Push a PC file to the phone via the file-transfer channel."""
    global _hub
    if _hub is None:
        return
    if not low_data_allowed():
        _reply("files.get", False, {"error": "low-data mode: not on AC power"})
        return
    if os.path.isfile(path) and _inside_root(path):
        _hub.push_file(path, "pc")
        _reply("files.get", True, {"path": path, "status": "sending"})
        log.info("[files.get] sending %s", path)
    else:
        _reply("files.get", False, {"error": "file not allowed or missing"})


def _start_mirror():
    ok, err = mirror.launch(turn_screen_off=False)
    log.info("[mirror.start] ok=%s err=%s", ok, err)


_ring_stop = threading.Event()


def _ring_pc(seconds):
    """Make the PC beep loudly until stopped (find-my-PC)."""
    global _ring_stop
    _ring_stop.clear()
    def loop():
        end = time.time() + max(seconds, 5)
        while time.time() < end and not _ring_stop.is_set():
            try:
                import ctypes
                ctypes.windll.user32.MessageBeep(0xFFFFFFFF)
            except Exception:
                pass
            time.sleep(0.4)
    threading.Thread(target=loop, daemon=True).start()


def _stop_ring():
    _ring_stop.set()


def _save_messages(params):
    """Save pushed SMS/call data from the phone to the desktop (feature 20/80)."""
    import json
    from pathlib import Path
    base = Path.home() / "LiteLink"
    base.mkdir(parents=True, exist_ok=True)
    kind = params.get("kind", "messages")
    items = params.get("items", [])
    ts = int(time.time())
    path = base / f"{kind}_{ts}.json"
    try:
        path.write_text(json.dumps({"kind": kind, "time": ts, "items": items}, indent=2),
                        encoding="utf-8")
        log.info("[messages.push] saved %d %s -> %s", len(items), kind, path)
        _reply("messages.push", True, {"saved": path.name, "count": len(items)})
    except Exception as e:
        _reply("messages.push", False, {"error": str(e)})


def _voice(text):
    t = (text or "").lower().strip()
    log.info("[voice] %s", t)
    if not t:
        return
    # open <app>
    if t.startswith("open "):
        app = t[len("open "):].strip()
        _open_app(app)
    elif t.startswith("search for ") or t.startswith("search "):
        q = t.split(" ", 1)[1] if " " in t else ""
        subprocess.Popen(["cmd", "/c", "start", "", f"https://www.google.com/search?q={q.replace(' ', '+')}"],
                         creationflags=subprocess.CREATE_NO_WINDOW)
    elif "play" in t or "music" in t:
        _media(VK_MEDIA_PLAY_PAUSE)
    elif "pause" in t or "stop" in t:
        _media(VK_MEDIA_STOP)
    elif "volume up" in t:
        _media(VK_VOLUME_UP)
    elif "volume down" in t:
        _media(VK_VOLUME_DOWN)
    elif "mute" in t:
        _media(VK_VOLUME_MUTE)
    elif "shutdown" in t:
        subprocess.Popen(["shutdown", "/s", "/t", "0"], creationflags=subprocess.CREATE_NO_WINDOW)
    elif "lock" in t:
        subprocess.Popen(["rundll32.exe", "user32.dll,LockWorkStation"], creationflags=subprocess.CREATE_NO_WINDOW)
    else:
        _open_app(t)


def _open_app(name):
    """Best-effort launch: chrome/notepad/explorer/calculator by name."""
    known = {
        "chrome": "chrome", "browser": "chrome",
        "notepad": "notepad",
        "explorer": "explorer", "files": "explorer",
        "calculator": "calc",
        "cmd": "cmd", "terminal": "cmd",
        "settings": "ms-settings:",
    }
    key = name.strip().lower()
    for alias, target in known.items():
        if alias in key:
            if target.endswith(":"):
                subprocess.Popen(["start", target], shell=True)
            else:
                subprocess.Popen(["cmd", "/c", "start", "", target], creationflags=subprocess.CREATE_NO_WINDOW)
            return
    # fallback: try the literal name as an app
    subprocess.Popen(["cmd", "/c", "start", "", name], creationflags=subprocess.CREATE_NO_WINDOW)
