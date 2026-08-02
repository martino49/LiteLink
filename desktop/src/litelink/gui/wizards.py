"""Pages 2 & 3 — Onboarding wizard and reconnection wizard.

Onboarding is a first-run QWizard (Welcome → ADB/scrcpy → Connect USB →
Wireless → Complete). The reconnect wizard appears when a paired phone is
missing and offers a one-click USB → wireless reconnection flow.
"""

import os

from PySide6.QtCore import Qt
from PySide6.QtGui import QDesktopServices, QPixmap
from PySide6.QtWidgets import (
    QCheckBox,
    QLabel,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
    QWizard,
    QWizardPage,
)

from .. import adb_utils, mirror
from .common import asset_path, make_icon, row


def _info_label(text, obj_name="muted"):
    lbl = QLabel(text)
    lbl.setObjectName(obj_name)
    lbl.setWordWrap(True)
    return lbl


class OnboardingWizard(QWizard):
    """First-run 5-step wizard."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("LiteLink — Setup")
        self.setWindowIcon(make_icon())
        self.setWizardStyle(QWizard.ModernStyle)
        self.setMinimumSize(640, 460)

        self.setPage(0, _WelcomePage())
        self.setPage(1, _AdbPage())
        self.setPage(2, _UsbPage())
        self.setPage(3, _WirelessPage())
        self.setPage(4, _CompletePage())


class _WelcomePage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("Welcome to LiteLink")
        self.setSubTitle("Bridge your Android phone and this laptop over Wi-Fi.")
        lay = QVBoxLayout(self)

        logo = QLabel()
        logo.setPixmap(make_icon().pixmap(80, 80))
        logo.setAlignment(Qt.AlignCenter)
        lay.addWidget(logo)

        lay.addWidget(_info_label(
            "LiteLink lets you mirror your phone, stream notifications, sync folders, "
            "back up SMS and call logs, run USSD codes and much more — all from this laptop.\n\n"
            "This short wizard checks your tools and connects your phone. Click Next to begin.",
            "muted"))


class _AdbPage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("Install tools")
        self.setSubTitle("LiteLink needs adb and scrcpy to mirror and control your phone.")
        lay = QVBoxLayout(self)

        self.adb_status = _info_label("Checking for adb…", "muted")
        self.scrcpy_status = _info_label("Checking for scrcpy…", "muted")
        lay.addWidget(self.adb_status)
        lay.addWidget(self.scrcpy_status)

        self.recheck = QPushButton("Re-check")
        self.recheck.clicked.connect(self.initializePage)
        self.dl = QPushButton("Download scrcpy (includes adb)")
        self.dl.clicked.connect(lambda: QDesktopServices.openUrl(
            __import__("PySide6.QtCore", fromlist=["QUrl"]).QUrl(
                "https://github.com/Genymobile/scrcpy/releases")))
        lay.addLayout(row(self.recheck, self.dl, stretch=1))

    def initializePage(self):
        adb = adb_utils.find_adb() if hasattr(adb_utils, "find_adb") else None
        self.adb_status.setText(f"adb: {'found' if adb else 'not found — bundled scrcpy provides it'}")
        sc = mirror.is_available()
        self.scrcpy_status.setText(f"scrcpy: {'found' if sc else 'not found'}")


class _UsbPage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("Connect your phone")
        self.setSubTitle("Enable USB debugging and plug the phone in.")
        lay = QVBoxLayout(self)
        lay.addWidget(_info_label(
            "1. On your phone open Settings → About phone → tap 'MIUI version' 7 times.\n"
            "2. Go to Settings → Additional settings → Developer options.\n"
            "3. Turn on 'USB debugging' (and 'Install via USB').\n"
            "4. Plug the phone into this laptop with a USB cable.", "muted"))

        self.status = _info_label("", "muted")
        lay.addWidget(self.status)
        btn = QPushButton("Check connection")
        btn.clicked.connect(self._check)
        lay.addWidget(btn)

    def initializePage(self):
        self._check()

    def _check(self):
        self.status.setText("Detecting phone…")
        devs = adb_utils.get_device_state() if hasattr(adb_utils, "get_device_state") else []
        if isinstance(devs, str):
            devs = [devs]
        if devs:
            self.status.setText(f"Found {len(devs)} device(s): {', '.join(str(d) for d in devs)}")
        else:
            self.status.setText("No device detected. Check the cable and USB debugging.")


class _WirelessPage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("Wireless setup")
        self.setSubTitle("Enable wireless adb so you can unplug the phone.")
        lay = QVBoxLayout(self)
        lay.addWidget(_info_label(
            "With the phone plugged in, tap below to open wireless adb on port 5555. "
            "You can then unplug and stay connected over Wi-Fi.", "muted"))
        self.status = _info_label("", "muted")
        lay.addWidget(self.status)
        btn = QPushButton("Enable wireless adb")
        btn.clicked.connect(self._enable)
        lay.addWidget(btn)

    def _enable(self):
        self.status.setText("Enabling wireless adb on port 5555…")
        ok = adb_utils.enable_wireless() if hasattr(adb_utils, "enable_wireless") else False
        self.status.setText("Wireless adb enabled — you can unplug now." if ok
                            else "Could not enable wireless automatically. Run: adb tcpip 5555")


class _CompletePage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("All set!")
        self.setSubTitle("You're ready to use LiteLink.")
        lay = QVBoxLayout(self)
        logo = QLabel()
        logo.setPixmap(make_icon().pixmap(64, 64))
        logo.setAlignment(Qt.AlignCenter)
        lay.addWidget(logo)
        lay.addWidget(_info_label(
            "The server runs on this laptop at port 8765. Open the LiteLink companion app on "
            "your phone, enter the pairing code from the dashboard, and you're connected.",
            "muted"))


class ReconnectWizard(QWizard):
    """Shown when a previously paired phone is not connected."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("LiteLink — Phone reconnection")
        self.setWindowIcon(make_icon())
        self.setWizardStyle(QWizard.ModernStyle)
        self.setMinimumSize(600, 400)
        self.setPage(0, _ReconnectPage())


class _ReconnectPage(QWizardPage):
    def __init__(self):
        super().__init__()
        self.setTitle("Reconnect your phone")
        self.setSubTitle("Your phone rebooted and wireless adb needs re-enabling.")
        lay = QVBoxLayout(self)
        lay.addWidget(_info_label(
            "1. \U0001F50C Plug the phone in via USB.\n"
            "2. \U0001F50D The phone is detected automatically.\n"
            "3. \U0001F4E1 Wireless adb is re-enabled on port 5555.\n"
            "4. \u2705 You can unplug now.", "muted"))

        self.status = _info_label("", "muted")
        lay.addWidget(self.status)

        self.reconnect = QPushButton("Reconnect")
        self.reconnect.clicked.connect(self._run)
        self.skip = QPushButton("Skip for now")
        lay.addLayout(row(self.reconnect, self.skip, stretch=1))
        self.skip.clicked.connect(lambda: self.accept())

    def _run(self):
        self.status.setText("Detecting phone…")
        devs = adb_utils.get_device_state() if hasattr(adb_utils, "get_device_state") else []
        if isinstance(devs, str):
            devs = [devs]
        if not devs:
            self.status.setText("No phone found. Plug in the USB cable and retry.")
            return
        self.status.setText(f"Enabling wireless adb for {len(devs)} device(s)…")
        results = adb_utils.setup_usb_reverse()
        lines = [f"USB reverse ({s}): {'ok' if ok else 'failed'}" for s, ok in results]
        self.status.setText("\n".join(lines) if lines else "No devices to configure.")
        if results:
            QMessageBox.information(self, "Reconnected", "Wireless adb is enabled. You can unplug the phone.")
