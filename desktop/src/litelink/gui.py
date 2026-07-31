"""PySide6 desktop GUI for LiteLink.

Runs the WSS server in a background asyncio thread, advertises via mDNS,
sets up the USB adb reverse tunnel, and provides a tray-able main window
with pairing-code, mirroring and log controls.
"""

import asyncio
import logging
import threading
import time

from PySide6.QtCore import Qt, QObject, Signal
from PySide6.QtGui import QAction, QIcon, QPainter, QPen, QPixmap, QColor
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QMenu,
    QMessageBox,
    QPushButton,
    QSystemTrayIcon,
    QTextEdit,
    QVBoxLayout,
    QWidget,
)

from . import adb_utils, input_controller, mdns, mirror, pairing, server
from .server import DATA_DIR
from .tls import certificate_fingerprint, format_fingerprint

log = logging.getLogger("litelink.gui")


def make_icon():
    """Placeholder brand icon (two linked nodes) until the real logo lands."""
    pix = QPixmap(64, 64)
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    pen = QPen(QColor("#0FA3A3"))
    pen.setWidth(6)
    p.setPen(pen)
    p.drawEllipse(6, 12, 20, 20)
    p.drawEllipse(38, 32, 20, 20)
    p.drawLine(26, 22, 38, 42)
    p.end()
    return QIcon(pix)


class Bridge(QObject):
    """Owns the asyncio server thread and fans events into Qt signals."""

    connected_changed = Signal(bool)
    log_line = Signal(str)

    def __init__(self):
        super().__init__()
        self.thread = None
        self.loop = None
        self.advertiser = None

    def start(self):
        self.thread = threading.Thread(target=self._run, daemon=True)
        self.thread.start()

    def _run(self):
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)
        try:
            self.loop.run_until_complete(
                server.start_server(
                    on_phone_connected=self._on_connected,
                    on_message=self._on_message,
                    log=self._on_log,
                )
            )
        except Exception as e:
            self._on_log(f"Server error: {e}")

    def start_mdns(self, port=8765):
        self.advertiser = mdns.Advertiser(port=port)
        self.advertiser.start()

    def _on_connected(self, ok):
        self.connected_changed.emit(ok)

    def _on_message(self, data):
        if data.get("type") == "input":
            input_controller.handle(data)

    def _on_log(self, text):
        self.log_line.emit(text)

    def stop(self):
        if self.advertiser:
            self.advertiser.stop()
        if self.loop:
            self.loop.call_soon_threadsafe(self.loop.stop)


class MainWindow(QMainWindow):

    def __init__(self, bridge):
        super().__init__()
        self.bridge = bridge
        self.setWindowTitle("LiteLink")
        self.resize(520, 560)

        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)

        self.status_label = QLabel("Server: starting...")
        self.phone_label = QLabel("Phone: not connected")
        layout.addWidget(self.status_label)
        layout.addWidget(self.phone_label)

        self.code_label = QLabel(f"Pairing code: {pairing.get_pairing_code(DATA_DIR)}")
        layout.addWidget(self.code_label)

        regen_button = QPushButton("Regenerate pairing code")
        regen_button.clicked.connect(self._regenerate_code)
        layout.addWidget(regen_button)

        fp = format_fingerprint(certificate_fingerprint(DATA_DIR))
        fp_label = QLabel(f"Certificate fingerprint: {fp}")
        fp_label.setWordWrap(True)
        layout.addWidget(fp_label)

        mirror_row = QHBoxLayout()
        self.mirror_button = QPushButton("Start Mirroring")
        self.mirror_button.clicked.connect(self._start_mirror)
        stop_button = QPushButton("Stop Mirroring")
        stop_button.clicked.connect(mirror.stop)
        mirror_row.addWidget(self.mirror_button)
        mirror_row.addWidget(stop_button)
        layout.addLayout(mirror_row)

        self.screen_off = QCheckBox("Turn phone screen off while mirroring")
        layout.addWidget(self.screen_off)

        self.log_area = QTextEdit()
        self.log_area.setReadOnly(True)
        self.log_area.setMinimumHeight(180)
        layout.addWidget(self.log_area)

        bridge.connected_changed.connect(self._on_connected)
        bridge.log_line.connect(self._on_log)

        self._setup_tray()

    def _regenerate_code(self):
        code = pairing.regenerate_pairing_code(DATA_DIR)
        self.code_label.setText(f"Pairing code: {code}")
        self._log_line(f"New pairing code: {code}")

    def _start_mirror(self):
        if not mirror.is_available():
            QMessageBox.warning(self, "LiteLink", "scrcpy not found in desktop/bin")
            return
        ok, err = mirror.launch(turn_screen_off=self.screen_off.isChecked())
        if not ok:
            QMessageBox.warning(self, "Mirroring", err or "failed to start scrcpy")

    def _on_connected(self, ok):
        self.phone_label.setText(f"Phone: {'connected' if ok else 'not connected'}")

    def _on_log(self, text):
        self.log_area.append(text)

    def _log_line(self, text):
        self.log_area.append(text)

    def _setup_tray(self):
        self.tray = QSystemTrayIcon(make_icon(), self)
        self.tray.setToolTip("LiteLink")
        menu = QMenu()
        show_action = QAction("Show", self)
        show_action.triggered.connect(self.show)
        quit_action = QAction("Quit", self)
        quit_action.triggered.connect(QApplication.quit)
        menu.addAction(show_action)
        menu.addSeparator()
        menu.addAction(quit_action)
        self.tray.setContextMenu(menu)
        self.tray.activated.connect(self._tray_activated)
        self.tray.show()

    def _tray_activated(self, reason):
        if reason == QSystemTrayIcon.Trigger:
            self.show()

    def closeEvent(self, event):
        event.ignore()
        self.hide()
        self.tray.showMessage(
            "LiteLink", "Still running in the system tray", QSystemTrayIcon.Information, 2000
        )


def _watch_usb(bridge, interval=5.0):
    """Re-establish the adb reverse tunnel whenever a phone reconnects.

    The adb reverse rule is lost when the USB cable is unplugged. Poll
    `adb reverse --list` and re-run setup_usb_reverse() when the expected
    rule goes missing. `adb devices` alone is not enough: it auto-starts
    the adb server, so a brief unplug/replug can be missed entirely.
    """
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
                        bridge._on_log(f"USB reverse ({serial}): {'ok' if ok else 'failed'}")
                known = current
        except Exception:
            pass
        time.sleep(interval)


def run():
    """Launch the GUI (server + tray). Blocks until the window closes."""
    import sys

    app = QApplication(sys.argv)
    app.setWindowIcon(make_icon())

    bridge = Bridge()
    bridge.start()
    bridge.start_mdns()

    results = adb_utils.setup_usb_reverse()
    for serial, ok in results:
        bridge._on_log(f"USB reverse ({serial}): {'ok' if ok else 'failed'}")
    threading.Thread(target=_watch_usb, args=(bridge,), daemon=True).start()

    window = MainWindow(bridge)
    window.show()
    window._on_log("LiteLink GUI started")

    exit_code = app.exec()

    bridge.stop()
    sys.exit(exit_code)


if __name__ == "__main__":
    run()
