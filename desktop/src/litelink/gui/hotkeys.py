"""Global hotkey triggers (feature 43).

Polls GetAsyncKeyState in a background thread and fires callbacks on the
GUI thread. Simpler and more reliable than RegisterHotKey + native filters,
and works whether or not the window has focus.

Default bindings (configurable in settings):
  Ctrl+Shift+M  -> mirror
  Ctrl+Shift+F  -> find phone
  Ctrl+Shift+E  -> open Files page
  Ctrl+Shift+S  -> sync now
"""

import ctypes
import logging
import sys
import threading
import time

from PySide6.QtCore import QObject, Signal

log = logging.getLogger("litelink.hotkeys")

IS_WINDOWS = sys.platform == "win32"

VK_CONTROL = 0x11
VK_SHIFT = 0x10
VK_M = 0x4D
VK_F = 0x46
VK_E = 0x45
VK_S = 0x53


def _down(vk):
    """True if the key is currently held."""
    if not IS_WINDOWS:
        return False
    return bool(user32.GetAsyncKeyState(vk) & 0x8000)


class HotkeyManager(QObject):
    """Polls for global hotkey combos and emits a signal on match."""

    triggered = Signal(str)  # action name

    # action -> (mods, key)
    BINDINGS = {
        "mirror": ([VK_CONTROL, VK_SHIFT], VK_M),
        "find_phone": ([VK_CONTROL, VK_SHIFT], VK_F),
        "files": ([VK_CONTROL, VK_SHIFT], VK_E),
        "sync": ([VK_CONTROL, VK_SHIFT], VK_S),
    }

    def __init__(self, parent=None):
        super().__init__(parent)
        self._thread = None
        self._running = False
        self._pressed = set()

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
                for action, (mods, key) in self.BINDINGS.items():
                    if all(_down(m) for m in mods) and _down(key):
                        if action not in self._pressed:
                            self._pressed.add(action)
                            self.triggered.emit(action)
                    else:
                        self._pressed.discard(action)
            except Exception:
                pass
            time.sleep(0.15)


if IS_WINDOWS:
    user32 = ctypes.WinDLL("user32", use_last_error=True)
else:
    user32 = None
