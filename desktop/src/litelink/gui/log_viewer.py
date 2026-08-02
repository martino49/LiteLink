"""Log Viewer dialog (Screen 21): color-coded, filterable debug log."""

import time

from PySide6.QtCore import Qt, QTimer
from PySide6.QtWidgets import (
    QComboBox,
    QDialog,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QPushButton,
    QVBoxLayout,
)

from .common import label, row


class LogViewerDialog(QDialog):
    """Shows the recent desktop log lines with level filtering + search."""

    def __init__(self, bridge, parent=None):
        super().__init__(parent)
        self.bridge = bridge
        self.setWindowTitle("Log Viewer")
        self.resize(560, 420)
        self._lines = []  # list of (level, text)

        outer = QVBoxLayout(self)
        outer.setContentsMargins(18, 18, 18, 18)
        outer.setSpacing(12)

        head = QLabel("Log Viewer")
        head.setObjectName("h1")
        outer.addWidget(head)

        self.level = QComboBox()
        self.level.addItems(["All", "INFO", "WARN", "ERROR"])
        self.level.currentTextChanged.connect(self._render)
        search = QLineEdit()
        search.setPlaceholderText("Search…")
        search.textChanged.connect(self._render)
        outer.addLayout(row(self.level, search, None))
        self.search = search

        self.list = QListWidget()
        self.list.setMinimumHeight(260)
        outer.addWidget(self.list)

        clear = QPushButton("Clear")
        outer.addWidget(clear)
        clear.clicked.connect(self._clear)

        # receive new log lines live
        self._hist = []
        bridge.log_line.connect(self._add)
        self._timer = QTimer(self)
        self._timer.setInterval(2000)
        self._timer.timeout.connect(self._render)
        self._timer.start()

    def _add(self, text):
        level = "INFO"
        if "ERROR" in text or "failed" in text.lower():
            level = "ERROR"
        elif "warn" in text.lower():
            level = "WARN"
        self._lines.append((level, text))
        if len(self._lines) > 500:
            self._lines = self._lines[-500:]
        self._render()

    def _render(self):
        q = self.search.text().lower()
        lvl = self.level.currentText()
        self.list.clear()
        for level, text in self._lines[-200:]:
            if lvl != "All" and level != lvl:
                continue
            if q and q not in text.lower():
                continue
            color = {"ERROR": "#C62828", "WARN": "#F57C00", "INFO": "#009688"}.get(level, "#607D8B")
            self.list.addItem(f"[{level}] {text}")
            from PySide6.QtGui import QColor
            self.list.item(self.list.count() - 1).setForeground(QColor(color))

    def _clear(self):
        self._lines.clear()
        self._render()
