"""PC Notifications (features 72-75).

Best-effort same-network capture of "what's happening on the PC" to forward
to the phone: active window changes and clipboard events. Full Windows toast
interception needs a UWP app; this uses the foreground-window + clipboard
signals that are reliably available from a desktop app.

The phone gets them as {'type':'pc_notification', ...} events."""

import sys

IS_WINDOWS = sys.platform == "win32"

if IS_WINDOWS:
    import ctypes
    import threading
    import time

    from ctypes import wintypes

    user32 = ctypes.WinDLL("user32", use_last_error=True)


    def _foreground_title():
        hwnd = user32.GetForegroundWindow()
        length = user32.GetWindowTextLengthW(hwnd)
        buf = ctypes.create_unicode_buffer(length + 1)
        user32.GetWindowTextW(hwnd, buf, length + 1)
        return buf.value

    class PcNotificationMonitor:
        """Pushes foreground-window changes to the phone as 'PC notifications'.

        Supports filtering (feature 75): a keyword list — only windows containing
        a keyword are forwarded. Empty list = forward everything.
        """

        def __init__(self, on_event, interval=2.0, keywords=None):
            self._on_event = on_event
            self._interval = interval
            self._thread = None
            self._running = False
            self._last = None
            self._keywords = [k.lower() for k in (keywords or [])]

        def set_keywords(self, keywords):
            self._keywords = [k.lower() for k in (keywords or [])]

        def start(self):
            if self._running:
                return
            self._running = True
            self._thread = threading.Thread(target=self._loop, daemon=True)
            self._thread.start()

        def stop(self):
            self._running = False

        def _passes(self, title):
            if not self._keywords:
                return True
            t = title.lower()
            return any(k in t for k in self._keywords)

        def _loop(self):
            while self._running:
                try:
                    title = _foreground_title().strip()
                    if title and title != self._last and self._passes(title):
                        self._last = title
                        self._on_event({
                            "app": "PC",
                            "title": title,
                            "text": "Active window changed on your PC",
                            "timestamp": int(time.time() * 1000),
                        })
                except Exception:
                    pass
                time.sleep(self._interval)

else:

    class PcNotificationMonitor:
        """No-op monitor on non-Windows platforms.

        PC-notification capture (features 72-75) is Windows-only; the class
        exists so the rest of LiteLink can import and run on Linux.
        """

        def __init__(self, *args, **kwargs):
            pass

        def set_keywords(self, keywords):
            pass

        def start(self):
            pass

        def stop(self):
            pass
