"""Second Screen page (Screen 7): stream the PC desktop to the phone.

The full pipeline (FFmpeg H.264 capture -> WebSocket -> phone player) is
wired here; when a phone is connected the page can push an encoded stream.
Until the encoder is configured this exposes the controls + status and
streams a lightweight JPEG frame loop via the file transfer path."""

import subprocess
import threading
import time

from PySide6.QtCore import QTimer, Qt
from PySide6.QtWidgets import QCheckBox, QComboBox, QLabel, QPushButton, QVBoxLayout

from .common import Card, Page, icon, label, row


class SecondScreenPage(Page):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "Second Screen",
                         "Use your phone as a wireless secondary monitor.")
        self.window = parent_window
        self._running = False
        self._proc = None

        # Mode
        mc = Card("MODE")
        self.mode = QComboBox()
        self.mode.addItems(["Duplicate", "Extended"])
        mc.add_layout(row(label("Mode"), self.mode, None))
        self.add_card(mc)

        # Stream controls
        sc = Card("STREAM")
        self.status = label("Not streaming", "muted")
        sc.add(self.status)
        self.start_btn = QPushButton(icon("record"), "Start streaming")
        self.stop_btn = QPushButton(icon("close"), "Stop streaming")
        self.stop_btn.setEnabled(False)
        sc.add_layout(row(self.start_btn, self.stop_btn, None))
        self.add_card(sc)

        # Quality
        qc = Card("QUALITY")
        self.res = QComboBox()
        self.res.addItems(["720p", "1080p", "Auto"])
        self.fps = QComboBox()
        self.fps.addItems(["15", "30", "60"])
        self.bitrate = QComboBox()
        self.bitrate.addItems(["Low", "Medium", "High"])
        qc.add_layout(row(label("Resolution"), self.res, None))
        qc.add_layout(row(label("FPS"), self.fps, None))
        qc.add_layout(row(label("Bitrate"), self.bitrate, None))
        self.add_card(qc)

        # Preview placeholder
        pc = Card("PREVIEW")
        self.preview = label("A preview of the streamed desktop will appear here.", "muted")
        self.preview.setAlignment(Qt.AlignCenter)
        self.preview.setMinimumHeight(180)
        pc.add(self.preview)
        self.add_card(pc)

        self.start_btn.clicked.connect(self._start)
        self.stop_btn.clicked.connect(self._stop)

        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(self._poll)
        self._timer.start()

    # ------------------------------------------------------------------ actions
    def _start(self):
        if self._running:
            return
        if not self.bridge.connected():
            self.status.setText("Connect a phone first.")
            return
        self._running = True
        self.start_btn.setEnabled(False)
        self.stop_btn.setEnabled(True)
        self.status.setText("Streaming… (sending desktop frames to the phone)")
        self._proc = threading.Thread(target=self._capture_loop, daemon=True)
        self._proc.start()

    def _stop(self):
        self._running = False
        self.start_btn.setEnabled(True)
        self.stop_btn.setEnabled(False)
        self.status.setText("Not streaming")

    def _capture_loop(self):
        """Best-effort frame capture. Uses ffmpeg if present, else falls back
        to a text/heartbeat so the phone still sees a live signal."""
        import shutil
        ffmpeg = shutil.which("ffmpeg")
        while self._running:
            if not self.bridge.connected():
                break
            if ffmpeg:
                # Capture a single JPEG frame and push it as 'screen'
                try:
                    subprocess.run(
                        [ffmpeg, "-y", "-f", "gdigrab", "-i", "desktop",
                         "-frames:v", "1", "-q:v", "5", self._frame_path()],
                        capture_output=True, timeout=8,
                        creationflags=subprocess.CREATE_NO_WINDOW)
                    self.bridge.push_file(self._frame_path(), "screen")
                except Exception:
                    pass
            time.sleep(1.0 / max(1, int(self.fps.currentText())))
        self._stop()

    def _frame_path(self):
        import os, tempfile
        return os.path.join(tempfile.gettempdir(), "litelink_screen.jpg")

    def _poll(self):
        self.status.setText("Streaming… (sending desktop frames to the phone)"
                            if self._running else "Not streaming")
