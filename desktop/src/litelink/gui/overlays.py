"""System overlays (Screens 26-27): Pairing/QR dialog and Update dialog."""

import os

from PySide6.QtCore import Qt, QUrl
from PySide6.QtGui import QDesktopServices, QPixmap
from PySide6.QtWidgets import (
    QDialog,
    QLabel,
    QPushButton,
    QVBoxLayout,
)

from .. import pairing
from ..server import DATA_DIR
from .common import make_icon


class PairingDialog(QDialog):
    """Shows the pairing code (+ QR if a QR image is available) for the phone."""

    def __init__(self, parent=None):
        super().__init__(parent)
        self.setWindowTitle("Pairing")
        self.resize(360, 420)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(24, 24, 24, 24)
        outer.setSpacing(14)
        outer.setAlignment(Qt.AlignCenter)

        logo = QLabel()
        logo.setPixmap(make_icon().pixmap(56, 56))
        logo.setAlignment(Qt.AlignCenter)
        outer.addWidget(logo)

        title = QLabel("Scan to pair")
        title.setObjectName("h1")
        title.setAlignment(Qt.AlignCenter)
        outer.addWidget(title)

        code = pairing.get_pairing_code(DATA_DIR)
        code_lbl = QLabel(code)
        code_lbl.setObjectName("big")
        code_lbl.setAlignment(Qt.AlignCenter)
        outer.addWidget(code_lbl)

        hint = QLabel("Open the LiteLink app on your phone and enter this code.")
        hint.setWordWrap(True)
        hint.setAlignment(Qt.AlignCenter)
        outer.addWidget(hint)

        # QR image if we can generate one
        qr = self._qr_pixmap(code)
        if qr is not None:
            qr_lbl = QLabel()
            qr_lbl.setPixmap(qr)
            qr_lbl.setAlignment(Qt.AlignCenter)
            outer.addWidget(qr_lbl)

        close = QPushButton("Close")
        close.clicked.connect(self.accept)
        outer.addWidget(close)

    def _qr_pixmap(self, code):
        """Render a QR code if `qrcode` is installed, else None."""
        try:
            import qrcode
            img = qrcode.make(f"litelink://pair?code={code}")
            img = img.resize((180, 180))
            from PySide6.QtGui import QImage
            data = img.tobytes("raw", "RGB")
            qimg = QImage(data, img.size[0], img.size[1], QImage.Format_RGB888)
            return QPixmap.fromImage(qimg)
        except Exception:
            return None


class UpdateDialog(QDialog):
    """Pops up when an update is available."""

    def __init__(self, version="1.1.0", notes="", parent=None):
        super().__init__(parent)
        self.setWindowTitle("Update Available")
        self.resize(420, 320)
        outer = QVBoxLayout(self)
        outer.setContentsMargins(24, 24, 24, 24)
        outer.setSpacing(12)

        title = QLabel("Update Available")
        title.setObjectName("h1")
        outer.addWidget(title)

        ver = QLabel(f"LiteLink {version} is now available.")
        ver.setWordWrap(True)
        outer.addWidget(ver)

        if notes:
            n = QLabel(notes)
            n.setWordWrap(True)
            n.setObjectName("muted")
            outer.addWidget(n)

        dl = QPushButton("Download now")
        dl.clicked.connect(lambda: QDesktopServices.openUrl(
            QUrl("https://github.com/")))
        outer.addWidget(dl)

        later = QPushButton("Remind later")
        later.setObjectName("ghost")
        later.clicked.connect(self.accept)
        outer.addWidget(later)
