"""Page 1 — Launcher / Splash window.

Shows the logo, a staged progress bar and status text while the server
initialises, then transitions to the main window (or onboarding on first run).
"""

import os

from PySide6.QtCore import QTimer, Qt
from PySide6.QtWidgets import QApplication, QFrame, QLabel, QProgressBar, QVBoxLayout, QWidget

from .. import __version__
from .common import asset_path, make_icon


class SplashWindow(QWidget):
    """Frameless splash with staged init messages, then auto-transition."""

    # Stage, progress, message
    STAGES = [
        (15, "Initializing…"),
        (35, "Checking ADB…"),
        (60, "Starting WebSocket server…"),
        (85, "Loading settings…"),
        (100, "Ready"),
    ]

    def __init__(self, on_done):
        super().__init__()
        self.on_done = on_done
        self._stage = 0
        self.setWindowFlags(Qt.FramelessWindowHint | Qt.WindowStaysOnTopHint)
        self.setFixedSize(440, 320)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(40, 40, 40, 36)
        outer.setAlignment(Qt.AlignCenter)
        outer.setSpacing(14)

        logo_path = asset_path("litelink-logo-transparent.png")
        if os.path.exists(logo_path):
            logo = QLabel()
            pix = make_icon().pixmap(110, 110)
            logo.setPixmap(pix)
            logo.setAlignment(Qt.AlignCenter)
            outer.addWidget(logo)

        self.name = QLabel("LiteLink")
        self.name.setObjectName("h1")
        self.name.setAlignment(Qt.AlignCenter)
        outer.addWidget(self.name)

        self.tagline = QLabel("Your phone, on your desk.")
        self.tagline.setObjectName("muted")
        self.tagline.setAlignment(Qt.AlignCenter)
        outer.addWidget(self.tagline)

        self.bar = QProgressBar()
        self.bar.setRange(0, 100)
        self.bar.setValue(0)
        self.bar.setTextVisible(False)
        self.bar.setMaximumHeight(6)
        outer.addWidget(self.bar)

        self.status = QLabel("Initializing…")
        self.status.setObjectName("muted")
        self.status.setAlignment(Qt.AlignCenter)
        outer.addWidget(self.status)

        self.version = QLabel(f"v{__version__}")
        self.version.setObjectName("muted")
        self.version.setAlignment(Qt.AlignCenter)
        outer.addWidget(self.version)

        self._timer = QTimer(self)
        self._timer.setInterval(420)
        self._timer.timeout.connect(self._advance)
        self._timer.start()

    def _advance(self):
        self._stage += 1
        if self._stage >= len(self.STAGES):
            self._timer.stop()
            QTimer.singleShot(300, self._finish)
            return
        pct, msg = self.STAGES[self._stage]
        self.bar.setValue(pct)
        self.status.setText(msg)

    def _finish(self):
        self.on_done()
        self.close()


if __name__ == "__main__":
    # quick visual smoke test: python -m litelink.gui.splash
    app = QApplication([])
    from .common import apply_theme
    apply_theme(app, "light")
    win = SplashWindow(lambda: print("done"))
    win.show()
    app.exec()
