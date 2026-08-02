"""Second Screen (features 48-54): stream the PC desktop to the phone.

Uses PIL.ImageGrab to capture the screen and pushes JPEG frames over the
WebSocket as lightweight MJPEG. The phone renders them in an ImageView loop.

Modes:
  duplicate  -> grab the primary monitor
  extended   -> grab the nth monitor (all_screens) — the phone acts as a
                secondary display

Control (phone -> PC):
  {"type":"pc","cmd":"screen.stream.start","params":{mode,monitor,fps,scale,quality}}
  {"type":"pc","cmd":"screen.stream.stop","params":{}}
  {"type":"input","kind":"pointer"|"key"|"text"|"gesture"}  -> touch/keyboard passthrough
"""

import io
import ctypes
import logging
import threading
import time

from ctypes import wintypes
from PIL import Image, ImageGrab

from . import input_controller

log = logging.getLogger("litelink.stream")

_hub = None
_running = False
_thread = None
_lock = threading.Lock()
_config = {"mode": "duplicate", "monitor": 0, "fps": 8.0, "scale": 0.5, "quality": 55}


def set_hub(hub):
    global _hub
    _hub = hub


def _monitor_rects():
    """Enumerate real monitors -> list of (left, top, right, bottom)."""
    rects = []

    class RECT(ctypes.Structure):
        _fields_ = [("left", wintypes.LONG), ("top", wintypes.LONG),
                    ("right", wintypes.LONG), ("bottom", wintypes.LONG)]

    MonitorEnumProc = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HMONITOR,
                                         wintypes.HDC, ctypes.POINTER(RECT), wintypes.LPARAM)

    def cb(hmon, hdc, lprc, lparam):
        rects.append((lprc.contents.left, lprc.contents.top,
                      lprc.contents.right, lprc.contents.bottom))
        return True

    user32 = ctypes.WinDLL("user32")
    try:
        user32.EnumDisplayMonitors(None, None, MonitorEnumProc(cb), 0)
    except Exception:
        pass
    return rects


def start(mode="duplicate", monitor=0, fps=8.0, scale=0.5, quality=55):
    """Begin streaming desktop frames to the phone."""
    global _running, _thread, _config
    with _lock:
        if _running:
            # update settings without restarting the thread
            _config.update(mode=mode, monitor=monitor, fps=fps, scale=scale, quality=quality)
            return
        _running = True
        _config.update(mode=mode, monitor=monitor, fps=fps, scale=scale, quality=quality)
    _thread = threading.Thread(target=_loop, daemon=True)
    _thread.start()


def stop():
    global _running
    with _lock:
        _running = False


def is_running():
    with _lock:
        return _running


def _grab():
    """Grab a frame for the configured mode/monitor."""
    cfg = dict(_config)
    if cfg["mode"] == "extended":
        rects = _monitor_rects()
        if len(rects) >= 2:
            # real extended monitor available: crop its region from the merged grab
            idx = min(cfg["monitor"], len(rects) - 1)
            l, t, r, b = rects[idx]
            merged = ImageGrab.grab(all_screens=True)
            return merged.crop((l, t, r, b))
        # fall back to primary (no second monitor present)
        return ImageGrab.grab()
    return ImageGrab.grab()


def _cursor_pos():
    """Return the current cursor (x, y) in physical pixels."""
    try:
        user32 = ctypes.WinDLL("user32")
        class POINT(ctypes.Structure):
            _fields_ = [("x", wintypes.LONG), ("y", wintypes.LONG)]
        pt = POINT()
        user32.GetCursorPos(ctypes.byref(pt))
        return pt.x, pt.y
    except Exception:
        return None


def _draw_cursor(img):
    """Overlay the Windows cursor icon onto a captured frame."""
    pos = _cursor_pos()
    if pos is None:
        return img
    cx, cy = pos
    try:
        user32 = ctypes.WinDLL("user32")
        class CURSORINFO(ctypes.Structure):
            _fields_ = [("cbSize", wintypes.DWORD), ("flags", wintypes.DWORD),
                        ("hCursor", wintypes.HANDLE), ("ptScreenPos", wintypes.POINT)]
        ci = CURSORINFO()
        ci.cbSize = ctypes.sizeof(CURSORINFO)
        if not user32.GetCursorInfo(ctypes.byref(ci)):
            return img
        if ci.flags == 0:  # cursor hidden
            return img
        # draw a simple arrow marker at the cursor location
        from PIL import ImageDraw
        d = ImageDraw.Draw(img)
        x, y = cx, cy
        if x < 0 or y < 0:
            return img
        size = 18
        # arrow: head + shaft in white with black outline for visibility
        pts = [(x, y), (x + size, y + size * 0.5), (x + size * 0.6, y + size * 0.6),
               (x + size * 0.7, y + size), (x + size * 0.3, y + size * 0.8),
               (x, y + size * 0.9)]
        d.polygon(pts, fill="white", outline="black")
    except Exception:
        pass
    return img


def _loop():
    interval = 1.0 / max(1.0, _config["fps"])
    while True:
        with _lock:
            if not _running:
                return
            scale = _config["scale"]
            quality = _config["quality"]
        try:
            img = _grab()
            w, h = img.size
            img = _draw_cursor(img)
            if scale != 1.0:
                img = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS)
            buf = io.BytesIO()
            img.save(buf, format="JPEG", quality=quality)
            payload = buf.getvalue()
            if _hub is not None:
                import base64
                _hub._schedule(_hub._send({
                    "type": "screen_frame",
                    "seq": int(time.time() * 1000),
                    "b64": base64.b64encode(payload).decode("ascii"),
                    "w": img.size[0],
                    "h": img.size[1],
                }))
        except Exception as e:
            log.warning("screen frame failed: %s", e)
            time.sleep(1.0)
            continue
        time.sleep(interval)


def handle_control(msg):
    """Route phone touch/keyboard messages to the desktop (features 53-54)."""
    input_controller.handle(msg)
