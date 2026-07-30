"""Screen mirroring panel — launches scrcpy."""

import subprocess
from pathlib import Path
from PySide6.QtWidgets import QWidget, QVBoxLayout, QPushButton, QLabel


BIN_DIR = Path(__file__).resolve().parent.parent.parent / "bin"


class MirrorPanel(QWidget):
    """Panel with a button to launch scrcpy mirroring."""

    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("Screen Mirroring"))

        self.btn = QPushButton("Mirror (scrcpy)")
        self.btn.clicked.connect(self._launch_scrcpy)
        layout.addWidget(self.btn)

    def _launch_scrcpy(self):
        scrcpy_path = BIN_DIR / "scrcpy"
        if not scrcpy_path.exists():
            scrcpy_path = BIN_DIR / "scrcpy.exe"
        try:
            subprocess.Popen([str(scrcpy_path)])
        except FileNotFoundError:
            self.btn.setText("scrcpy not found in bin/")
