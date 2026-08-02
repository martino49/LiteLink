"""Bridge: owns the asyncio Hub thread and fans its events into Qt signals.

The GUI pages talk to the phone exclusively through this object:
- bridge.command(cmd, params, cb)  -> cb(ok, data) on the GUI thread
- bridge.push_file(path, dir)      -> stream a local file to the phone
- bridge.connected()               -> is a phone paired right now?
- Qt signals for async events.
"""

import asyncio
import threading
import time

from PySide6.QtCore import QObject, Signal

from .. import input_controller, mdns
from ..server import Hub

class Bridge(QObject):
    """Owns the asyncio Hub thread and fans events into Qt signals."""

    phone_changed = Signal(bool)
    log_line = Signal(str)
    command_result = Signal(str, bool, object)
    event_occurred = Signal(str, object)
    notification_arrived = Signal(object)
    file_received = Signal(str, str, int, bool)

    def __init__(self):
        super().__init__()
        self.hub = Hub()
        self.thread = None
        self.advertiser = None
        self.scanner = None
        self.beacon = None
        self.mirror_wanted = False
        self.mirror_screen_off = False
        self._pending = {}

        self.hub.on_phone = lambda ok: self.phone_changed.emit(ok)
        self.hub.on_log = lambda t: self.log_line.emit(t)
        self.hub.on_command_result = lambda rid, ok, data: self.command_result.emit(rid, ok, data)
        self.hub.on_event = lambda ev, data: self.event_occurred.emit(ev, data)
        self.hub.on_notification = lambda data: self.notification_arrived.emit(data)
        self.hub.on_file_received = lambda n, d, s, ok: self.file_received.emit(n, d, s, ok)
        self.hub.on_message = self._on_message

        self.command_result.connect(self._on_command_result)

    def start(self):
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        try:
            self.loop.run_until_complete(self.hub.serve())
        except Exception as e:
            self.hub._log(f"Server error: {e}")

    def _on_message(self, data):
        # 'pc' messages are handled by the server dispatch; 'input' here.
        if data.get("type") == "input":
            input_controller.handle(data)

    def _on_command_result(self, rid, ok, data):
        cb = self._pending.pop(rid, None)
        if cb:
            cb(ok, data)

    def start_mdns(self, port=8765):
        from ..server import DATA_DIR

        self.advertiser = mdns.Advertiser(port=port)
        self.advertiser.start()
        self.scanner = mdns.Scanner()
        self.scanner.start()
        self.beacon = mdns.BroadcastBeacon(service_port=port, data_dir=DATA_DIR)
        self.beacon.start()

    def discovered_devices(self):
        """Merged snapshot of mDNS + broadcast-discovered LiteLink peers."""
        out = {}
        if self.scanner:
            out.update(self.scanner.snapshot())
        if self.beacon:
            out.update(self.beacon.snapshot())
        return out

    def stop(self):
        if self.advertiser:
            self.advertiser.stop()
        if self.scanner:
            self.scanner.stop()
        if self.beacon:
            self.beacon.stop()
        if self.hub.loop:
            self.hub.loop.call_soon_threadsafe(self.hub.loop.stop)

    # ------------------------------------------------------------------ API
    def command(self, cmd, params=None, cb=None):
        """Send a command; cb(ok, data) runs on the GUI thread."""
        rid = self.hub.send_command(cmd, params)
        if cb:
            self._pending[rid] = cb
        return rid

    def push_file(self, path, dir=""):
        self.hub.push_file(path, dir)

    def connected(self):
        return self.hub.connected

    def log(self, text):
        self.log_line.emit(text)


def watch_usb(bridge, interval=5.0):
    """Re-establish the adb reverse tunnel whenever a phone reconnects."""
    from .. import adb_utils

    known = set()
    while True:
        try:
            out, err, code = adb_utils.run_adb_command(["devices"])
            current = set()
            if code == 0:
                for line in out.splitlines():
                    parts = line.split()
                    if len(parts) == 2 and parts[1] == "device":
                        current.add(parts[0])
            if not current:
                known = set()
            else:
                rev_out, _, rev_code = adb_utils.run_adb_command(["reverse", "--list"])
                missing = (rev_code != 0) or ("tcp:8765 tcp:8765" not in rev_out)
                if missing or (current - known):
                    results = adb_utils.setup_usb_reverse()
                    for serial, ok in results:
                        bridge.log_line.emit(f"USB reverse ({serial}): {'ok' if ok else 'failed'}")
                known = current
        except Exception:
            pass
        time.sleep(interval)


def watch_mirror(bridge, interval=5.0, restarts=3):
    """Crash recovery (feature 45): restart scrcpy if it dies unexpectedly.

    Uses a flag set by the GUI when the user *intends* mirroring to run, so we
    only auto-restart after an unexpected exit — not when they stopped it.
    """
    from .. import mirror

    consecutive = 0
    while True:
        try:
            if bridge.mirror_wanted and not mirror.is_running():
                if consecutive < restarts:
                    consecutive += 1
                    bridge.log_line.emit(f"Mirror crashed — restarting ({consecutive}/{restarts})")
                    ok, err = mirror.launch(turn_screen_off=bridge.mirror_screen_off)
                    if not ok:
                        bridge.log_line.emit(f"Mirror restart failed: {err}")
                else:
                    bridge.log_line.emit("Mirror restart limit reached — stopping watchdog")
                    bridge.mirror_wanted = False
                    consecutive = 0
            elif mirror.is_running():
                consecutive = 0
        except Exception:
            pass
        time.sleep(interval)
