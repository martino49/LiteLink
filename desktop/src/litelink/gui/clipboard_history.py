"""Universal Clipboard History (feature 30).

Polls the Windows clipboard on the desktop, keeps the last 50 unique text
entries in a JSON file, and serves them to the phone via pc_controller
(feature 30, same-network)."""

import json
import threading
import time

from pathlib import Path

from ..server import DATA_DIR

HISTORY_FILE = DATA_DIR / "clipboard_history.json"
MAX_ITEMS = 50


def load():
    try:
        return json.loads(HISTORY_FILE.read_text(encoding="utf-8"))
    except Exception:
        return []


def add(text):
    text = str(text).strip()
    if not text:
        return
    items = load()
    if items and items[0] == text:
        return
    items = [text] + [i for i in items if i != text]
    items = items[:MAX_ITEMS]
    HISTORY_FILE.parent.mkdir(parents=True, exist_ok=True)
    HISTORY_FILE.write_text(json.dumps(items, indent=2), encoding="utf-8")


def clear():
    try:
        HISTORY_FILE.unlink()
    except OSError:
        pass


class ClipboardMonitor:
    """Poll the clipboard for changes and append to history."""

    def __init__(self, on_new=None):
        self._thread = None
        self._running = False
        self._last = None
        self._on_new = on_new

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._loop, daemon=True)
        self._thread.start()

    def stop(self):
        self._running = False

    def _loop(self):
        while self._running:
            try:
                import ctypes
                u32 = ctypes.WinDLL("user32")
                CF_UNICODETEXT = 13
                if u32.IsClipboardFormatAvailable(CF_UNICODETEXT):
                    u32.OpenClipboard(0)
                    try:
                        h = u32.GetClipboardData(CF_UNICODETEXT)
                        if h:
                            ptr = ctypes.windll.kernel32.GlobalLock(ctypes.c_void_p(h))
                            try:
                                text = ctypes.wstring_at(ptr)
                            finally:
                                ctypes.windll.kernel32.GlobalUnlock(ctypes.c_void_p(h))
                            if text and text != self._last:
                                self._last = text
                                add(text)
                                if self._on_new:
                                    self._on_new(text)
                    finally:
                        u32.CloseClipboard()
            except Exception:
                pass
            time.sleep(1.0)
