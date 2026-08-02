"""Batch APK Installer (Screen 22) + APK Extractor (Screen 23) dialogs."""

from PySide6.QtCore import Qt
from PySide6.QtWidgets import (
    QAbstractItemView,
    QDialog,
    QFileDialog,
    QFrame,
    QHBoxLayout,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QVBoxLayout,
    QWidget,
)

from .common import fmt_size, label, row


class _ToolDialog(QDialog):
    def __init__(self, bridge, parent=None, title="", size=(560, 460)):
        super().__init__(parent)
        self.bridge = bridge
        self.setWindowTitle(title)
        self.resize(*size)
        self.outer = QVBoxLayout(self)
        self.outer.setContentsMargins(20, 18, 20, 16)
        self.outer.setSpacing(14)
        head = QLabel(title)
        head.setObjectName("h1")
        self.outer.addWidget(head)

        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        content = QWidget()
        self.body = QVBoxLayout(content)
        self.body.setContentsMargins(0, 0, 0, 0)
        self.body.setSpacing(12)
        scroll.setWidget(content)
        self.outer.addWidget(scroll, 1)

        foot = QFrame()
        foot.setObjectName("card")
        fl = QHBoxLayout(foot)
        fl.setContentsMargins(10, 6, 10, 6)
        fl.addStretch(1)
        close = QPushButton("Close")
        close.setObjectName("ghost")
        close.clicked.connect(self.accept)
        fl.addWidget(close)
        self.outer.addWidget(foot)


class BatchApkInstallerDialog(_ToolDialog):
    """Pick local APKs and send them to the phone to install."""

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "Batch APK Installer", (580, 480))
        self._files = []

        self.list = QListWidget()
        self.list.setMinimumHeight(260)
        self.body.addWidget(self.list)

        self.status = label("Add APKs, then Install All.", "muted")
        self.body.addWidget(self.status)

        self.body.addLayout(row(
            QPushButton("Add APKs"),
            QPushButton("Install All"),
            QPushButton("Clear completed"),
            None))
        r = self.body.itemAt(self.body.count() - 1).layout()
        r.itemAt(0).widget().clicked.connect(self._add)
        r.itemAt(1).widget().clicked.connect(self._install_all)
        r.itemAt(2).widget().clicked.connect(self._clear)

    def _add(self):
        paths, _ = QFileDialog.getOpenFileNames(self, "Choose APKs", "", "APK files (*.apk)")
        for p in paths:
            if p not in self._files:
                self._files.append(p)
        self._render()

    def _render(self):
        self.list.clear()
        for p in self._files:
            import os
            name = os.path.basename(p)
            size = os.path.getsize(p)
            item = QListWidgetItem(f"{name}  ({fmt_size(size)})")
            item.setData(Qt.UserRole, p)
            self.list.addItem(item)
        self.status.setText(f"{len(self._files)} APK(s) pending.")

    def _install_all(self):
        if not self._files:
            self.status.setText("No APKs selected.")
            return
        if not self.bridge.connected():
            self.status.setText("Phone not connected.")
            return
        for p in self._files:
            self.bridge.push_file(p, "install")
            self.bridge.log(f"Installing {p.split('/')[-1]}")
        self.status.setText(f"Sent {len(self._files)} APK(s) to the phone for install.")

    def _clear(self):
        self._files.clear()
        self._render()


class ApkExtractorDialog(_ToolDialog):
    """List installed apps and extract their APKs to the laptop."""

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "APK Extractor", (580, 480))

        search = QLineEdit()
        search.setPlaceholderText("Search apps…")
        search.textChanged.connect(self._render)
        self.body.addLayout(row(search, None))
        self.search = search

        self.list = QListWidget()
        self.list.setMinimumHeight(260)
        self.list.itemClicked.connect(self._toggle)
        self.body.addWidget(self.list)

        self.status = label("Loading installed apps…", "muted")
        self.body.addWidget(self.status)

        self.body.addLayout(row(QPushButton("Extract selected"), QPushButton("Refresh"), None))
        r = self.body.itemAt(self.body.count() - 1).layout()
        r.itemAt(0).widget().clicked.connect(self._extract)
        r.itemAt(1).widget().clicked.connect(self._load)

        self._apps = []
        self._selected = set()
        self._load()

    def _load(self):
        self.bridge.command("apk.list", {}, self._show)

    def _show(self, ok, data):
        if not ok:
            self.status.setText(f"Failed: {data.get('error', '?')}")
            return
        self._apps = data.get("apps", [])
        self.status.setText(f"{len(self._apps)} apps installed. Click to select.")
        self._render("")

    def _render(self, q):
        q = q.lower()
        self.list.clear()
        for a in self._apps:
            pkg = a.get("package", "")
            if q and q not in pkg.lower() and q not in a.get("label", "").lower():
                continue
            mark = "[x]" if pkg in self._selected else "[ ]"
            self.list.addItem(f"{mark} {a.get('label', pkg)}  ·  {pkg}  ·  v{a.get('version', '')}")

    def _toggle(self, item):
        pkg = item.text().split("  ·  ")[-1].split("  ·  ")[0]
        # The package is the 2nd field; re-derive robustly
        parts = item.text().split("  ·  ")
        pkg = parts[1].strip() if len(parts) >= 2 else parts[0].strip()
        if pkg in self._selected:
            self._selected.discard(pkg)
        else:
            self._selected.add(pkg)
        self._render(self.search.text())

    def _extract(self):
        if not self._selected:
            self.status.setText("Select apps to extract first.")
            return
        if not self.bridge.connected():
            self.status.setText("Phone not connected.")
            return
        self.status.setText(f"Extracting {len(self._selected)} APK(s)…")
        for pkg in list(self._selected):
            self.bridge.command("apk.extract", {"package": pkg}, lambda ok, d: None)
        self.status.setText("Extraction streaming to the laptop…")
