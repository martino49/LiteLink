"""Tool dialogs (Pages 11-15): Debloat Guardian, Battery Health, M-Pesa
Tracker, USSD Shortcut and Maintenance Mode. All are QDialog subclasses
launched from the Tools page."""

import json
import time

from PySide6.QtCore import Qt, QTimer, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QAbstractItemView,
    QCheckBox,
    QDialog,
    QFrame,
    QGridLayout,
    QHBoxLayout,
    QHeaderView,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
    QWidget,
)

from . import history
from .common import Card, LineGraph, fmt_size, label, row
from ..server import DATA_DIR


class _ToolDialog(QDialog):
    """Base for the tool dialogs: themed surface, header, scrollable body,
    and a footer with a Close button."""

    def __init__(self, bridge, parent=None, title="", size=(520, 460)):
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

        # scrollable body
        from PySide6.QtWidgets import QScrollArea
        scroll = QScrollArea()
        scroll.setWidgetResizable(True)
        scroll.setFrameShape(QFrame.NoFrame)
        content = QWidget()
        self.body = QVBoxLayout(content)
        self.body.setContentsMargins(0, 0, 0, 0)
        self.body.setSpacing(12)
        scroll.setWidget(content)
        self.outer.addWidget(scroll, 1)

        # footer with close
        foot = QFrame()
        foot.setObjectName("card")
        fl = QHBoxLayout(foot)
        fl.setContentsMargins(10, 6, 10, 6)
        fl.addStretch(1)
        self.close_btn = QPushButton("Close")
        self.close_btn.setObjectName("ghost")
        self.close_btn.clicked.connect(self.accept)
        fl.addWidget(self.close_btn)
        self.outer.addWidget(foot)

    def add_card(self, card):
        self.body.addWidget(card)


# ------------------------------------------------------------------ Debloat

class DebloatDialog(_ToolDialog):
    """Page 11 — Debloat Guardian: list packages with risk rating and
    disable / uninstall / enable them over adb."""

    RISK_CRITICAL = {"com.android.systemui", "com.android.phone", "com.android.settings",
                     "com.android.providers.contacts", "com.android.providers.telephony",
                     "com.android.providers.media", "com.google.android.gms",
                     "com.miui.core", "com.miui.securitycenter", "com.miui.securitycore",
                     "com.xiaomi.miui.systemui"}
    RISK_SAFE = {"com.xiaomi.glgm", "com.miui.notes", "com.miui.videoplayer",
                 "com.miui.cleanmaster", "com.miui.compass", "com.android.chrome",
                 "com.miui.gallery", "com.xiaomi.mipicks", "com.xiaomi.gamecenter",
                 "com.miui.bugreport", "com.miui.cloudbackup"}

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "Debloat Guardian", (600, 540))
        self._packages = []
        self._selected = set()

        filt = QLineEdit()
        filt.setPlaceholderText("Search packages…")
        filt.textChanged.connect(self._render)
        self.body.addLayout(row(filt, None))

        self.list = QListWidget()
        self.list.setMinimumHeight(320)
        self.body.addWidget(self.list)
        self.list.itemClicked.connect(self._toggle)

        self.status = label("Select packages, then disable or uninstall.", "muted")
        self.body.addWidget(self.status)
        self.body.addLayout(row(
            QPushButton("Disable selected"),
            QPushButton("Uninstall selected"),
            QPushButton("Enable selected"),
            None))

        row_ = self.body.itemAt(self.body.count() - 1).layout()
        row_.itemAt(0).widget().clicked.connect(lambda: self._action("disable"))
        row_.itemAt(1).widget().clicked.connect(lambda: self._action("uninstall"))
        row_.itemAt(2).widget().clicked.connect(lambda: self._action("enable"))

        self.body.addLayout(row(
            QPushButton("Backup APKs"),
            QPushButton("Refresh"),
            None))
        row_ = self.body.itemAt(self.body.count() - 1).layout()
        row_.itemAt(0).widget().clicked.connect(self._backup)
        row_.itemAt(1).widget().clicked.connect(self._load)

        self._load()

    def _load(self):
        self.bridge.command("apk.list", {}, self._show)

    def _show(self, ok, data):
        if not ok:
            self.status.setText(f"Failed: {data.get('error', '?')}")
            return
        self._packages = data.get("apps", [])
        self.status.setText(f"{len(self._packages)} packages loaded. Click to select.")
        self._render("")

    def _risk(self, pkg, system):
        if not system:
            return "Safe"
        if pkg in self.RISK_CRITICAL:
            return "Critical"
        if pkg in self.RISK_SAFE:
            return "Safe"
        return "Caution"

    def _render(self, query):
        q = query.lower()
        self.list.clear()
        for a in self._packages:
            pkg = a.get("package", "")
            if q and q not in pkg.lower() and q not in a.get("label", "").lower():
                continue
            risk = self._risk(pkg, a.get("system", False))
            mark = "[x]" if pkg in self._selected else "[ ]"
            self.list.addItem(f"{mark} {risk}  {a.get('label', pkg)}  ·  {pkg}")

    def _toggle(self, item):
        text = item.text()
        pkg = text.split("  ·  ")[-1]
        if pkg in self._selected:
            self._selected.discard(pkg)
        else:
            self._selected.add(pkg)
        self._render("")

    def _action(self, which):
        from .. import adb_utils
        if not self._selected:
            self.status.setText("Select packages first.")
            return
        if adb_utils.get_device_state() != "connected":
            self.status.setText("Phone not connected over adb — plug it in via USB.")
            return
        fn = {"disable": adb_utils.disable_package,
              "uninstall": adb_utils.uninstall_package,
              "enable": adb_utils.enable_package}[which]
        done = []
        for pkg in list(self._selected):
            out, ok = fn(pkg)
            done.append(f"{pkg}: {'OK' if ok else 'failed'}")
        self.status.setText(f"{which}: " + "; ".join(done))
        self.bridge.log(f"Debloat {which}: " + ", ".join(done))
        self._selected.clear()
        self._render("")

    def _backup(self):
        from .. import adb_utils
        if not self._selected:
            self.status.setText("Select packages to back up first.")
            return
        if adb_utils.get_device_state() != "connected":
            self.status.setText("Phone not connected over adb.")
            return
        self.status.setText("Backing up selected APKs…")
        for pkg in list(self._selected):
            self.bridge.command("apk.extract", {"package": pkg}, lambda ok, d, p=pkg: None)
        self.status.setText("APK backup streaming to the laptop…")


# ------------------------------------------------------------------ Battery

class BatteryDialog(_ToolDialog):
    """Page 12 — Battery Health: gauge, stats, live history graph, alerts, export."""

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "Battery Health", (560, 560))

        self.percent = QLabel("—")
        self.percent.setObjectName("big")
        self.percent.setAlignment(Qt.AlignCenter)
        self.body.addWidget(self.percent)

        self.bar = QProgressBar()
        self.bar.setRange(0, 100)
        self.bar.setFormat("—")
        self.body.addWidget(self.bar)

        self.stats = label("", "muted")
        self.body.addWidget(self.stats)

        self.alert = label("", "muted")
        self.body.addWidget(self.alert)

        graph_card = Card("HISTORY")
        self.graph = LineGraph(graph_card, height=150)
        graph_card.add(self.graph)
        self.body.addWidget(graph_card)

        self.body.addLayout(row(
            QPushButton("Refresh"),
            QPushButton("Export CSV"),
            None))
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(0).widget()
        btn.clicked.connect(self.refresh)
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(1).widget()
        btn.clicked.connect(self._export)

        self._timer = QTimer(self)
        self._timer.setInterval(30000)
        self._timer.timeout.connect(self.refresh)
        self._timer.start()

        self._render_graph()
        self.refresh()

    def refresh(self):
        if not self.bridge.connected():
            self.stats.setText("Phone not connected.")
            return
        self.bridge.command("battery.get", {}, self._show)

    def _show(self, ok, data):
        if not ok:
            self.stats.setText(f"Failed: {data.get('error', '?')}")
            return
        level = int(data.get("level", 0) or 0)
        self.percent.setText(f"{level}%")
        self.bar.setValue(level)
        self.bar.setFormat(f"{level}%")
        temp = data.get("temperature_c", 0)
        health = "Good"
        color = "(green)"
        if temp > 45:
            health, color = "Poor", "(red)"
        elif temp > 40:
            health, color = "Fair", "(yellow)"
        self.stats.setText(
            f"Temperature: {temp}°C  ·  Voltage: {data.get('voltage_mv')} mV  ·  "
            f"Health: {health} {color}\nCharging: {data.get('charging')}  ·  "
            f"Plugged: {data.get('plugged')}")
        if temp > 45:
            self.alert.setText("Warning: battery temperature is high.")
        else:
            self.alert.setText("OK: battery temperature is normal.")
        # persist + render the history graph
        history.append_sample("battery_history.jsonl", {
            "t": time.time() * 1000,
            "level": level,
        })
        self._render_graph()

    def _render_graph(self):
        self.graph.set_points(history.battery_series(300))

    def _export(self):
        import csv
        path = DATA_DIR / "received" / "battery_history.csv"
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8", newline="") as f:
            w = csv.writer(f)
            w.writerow(["time_ms", "level_pct"])
            for s in history.load_samples("battery_history.jsonl"):
                w.writerow([s.get("t", 0), s.get("level", 0)])
        QMessageBox.information(self, "Exported", f"Saved to\n{path}")


# ------------------------------------------------------------------ M-Pesa

class MpesaDialog(_ToolDialog):
    """Page 13 — M-Pesa Tracker: totals + transaction table."""

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "M-Pesa Tracker", (560, 500))

        self.sent = label("Sent: —")
        self.received = label("Received: —")
        self.net = label("Net: —")
        self.count = label("Count: —")
        self.body.addLayout(row(self.sent, self.received, None))
        self.body.addLayout(row(self.net, self.count, None))

        self.tx = QTableWidget(0, 5)
        self.tx.setHorizontalHeaderLabels(["Date", "Type", "Amount", "Peer", "Balance"])
        self.tx.setEditTriggers(QAbstractItemView.NoEditTriggers)
        self.tx.horizontalHeader().setSectionResizeMode(QHeaderView.Stretch)
        self.tx.verticalHeader().setVisible(False)
        self.tx.setMinimumHeight(260)
        self.body.addWidget(self.tx)

        self.body.addLayout(row(
            QPushButton("Scan transactions"),
            QPushButton("Export CSV"),
            None))
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(0).widget()
        btn.clicked.connect(self._scan)
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(1).widget()
        btn.clicked.connect(self._export)

    def _scan(self):
        self.bridge.command("mpesa.parse", {}, self._show)

    def _show(self, ok, data):
        txns = data.get("transactions", []) if ok else []
        self.tx.setRowCount(0)
        sent = received = 0.0
        for t in txns:
            r = self.tx.rowCount()
            self.tx.insertRow(r)
            amount = t.get("amount", 0)
            typ = t.get("type", "")
            if typ in ("sent", "withdrawn"):
                sent += amount
            elif typ in ("received", "deposited"):
                received += amount
            vals = [t.get("date", ""), typ, str(amount), t.get("peer", ""), str(t.get("balance", ""))]
            for c, v in enumerate(vals):
                self.tx.setItem(r, c, QTableWidgetItem(v))
        self.sent.setText(f"Sent: KES {sent:,.0f}")
        self.received.setText(f"Received: KES {received:,.0f}")
        self.net.setText(f"Net: KES {received - sent:,.0f}")
        self.count.setText(f"Count: {len(txns)}")

    def _export(self):
        path = DATA_DIR / "received" / "mpesa_transactions.csv"
        path.parent.mkdir(parents=True, exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write("date,type,amount,peer,balance\n")
            for r in range(self.tx.rowCount()):
                vals = [self.tx.item(r, c).text() for c in range(5)]
                f.write(",".join(vals) + "\n")
        QMessageBox.information(self, "Exported", f"Saved to\n{path}")


# ------------------------------------------------------------------ USSD

class UssdDialog(_ToolDialog):
    """Page 14 — USSD Shortcut: quick codes, custom input, response."""

    QUICK = [("*544#", "M-Pesa balance"), ("*455#", "M-Pesa menu"),
             ("*100#", "Airtime"), ("*150#", "Offers"), ("*200#", "Customer care")]

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "USSD Shortcut", (500, 460))

        qc = Card("QUICK CODES")
        self.quick_btns = []
        for code, label_text in self.QUICK:
            b = QPushButton(f"{code}  —  {label_text}")
            b.setObjectName("ghost")
            b.clicked.connect(lambda _, c=code: self._send(c))
            qc.add(b)
        self.body.addWidget(qc)

        self.input = QLineEdit()
        self.input.setPlaceholderText("Custom USSD code, e.g. *544#")
        self.body.addLayout(row(self.input, QPushButton("Send"), None))
        self.body.itemAt(self.body.count() - 1).layout().itemAt(1).widget().clicked.connect(
            lambda: self._send(self.input.text().strip()))

        rc = Card("RESPONSE")
        self.response = QLabel("No response yet.")
        self.response.setWordWrap(True)
        rc.add(self.response)
        self.body.addWidget(rc)

    def _send(self, code):
        if not code:
            return
        self.response.setText(f"Sending {code}…")
        self.bridge.command("ussd.send", {"code": code}, self._done)

    def _done(self, ok, data):
        self.response.setText(str(data.get("response", data)) if ok
                              else f"Failed: {data.get('error', '?')}")


# ------------------------------------------------------------------ Maintenance

class MaintenanceDialog(_ToolDialog):
    """Page 15 — Maintenance Mode: schedule, tasks, run now, status."""

    TASKS = [("Clear app cache", "cache"),
             ("Compress photos", "photos"),
             ("Backup WhatsApp DB", "whatsapp"),
             ("Delete old files (30d)", "old"),
             ("Reboot phone", "reboot")]

    def __init__(self, bridge, parent=None):
        super().__init__(bridge, parent, "Maintenance Mode", (540, 520))

        sc = Card("SCHEDULE")
        self.sched = QCheckBox("Enable scheduled maintenance")
        sc.add(self.sched)
        self.time_ed = QLineEdit("02:00 AM")
        sc.add_layout(row(QLabel("Time"), self.time_ed, None))
        self.body.addWidget(sc)

        tc = Card("TASKS")
        self.task_boxes = {}
        for name, _key in self.TASKS:
            cb = QCheckBox(name)
            cb.setChecked(True)
            self.task_boxes[name] = cb
            tc.add(cb)
        self.body.addWidget(tc)

        self.status = label("Status: Not run yet", "muted")
        self.body.addWidget(self.status)
        self.body.addLayout(row(QPushButton("Run now"), QPushButton("Stop"), None))
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(0).widget()
        btn.clicked.connect(self._run)
        btn = self.body.itemAt(self.body.count() - 1).layout().itemAt(1).widget()
        btn.clicked.connect(self._stop)

        self._running = False

    # ------------------------------------------------------------------ run
    def _run(self):
        if self._running:
            self.status.setText("Already running…")
            return
        self._running = True
        self._report = []
        self._run_step(0)

    def _stop(self):
        self._running = False
        self.status.setText("Status: Stopped by user")

    def _run_step(self, idx):
        if not self._running:
            return
        if idx >= len(self.TASKS):
            self._finish()
            return
        name, key = self.TASKS[idx]
        if not self.task_boxes[name].isChecked():
            self._run_step(idx + 1)
            return
        self.status.setText(f"Status: {name}…")
        handler = {
            "cache": self._task_cache,
            "photos": self._task_photos,
            "whatsapp": self._task_whatsapp,
            "old": self._task_old,
            "reboot": self._task_reboot,
        }[key]
        handler(lambda: self._run_step(idx + 1))

    def _finish(self):
        self._running = False
        self.status.setText("Status: Completed\n" + "\n".join(self._report))

    def _task_cache(self, done):
        self.bridge.command("maint.clear_cache", {}, lambda ok, d: (
            self._report.append(f"Cache: cleared {fmt_size(d.get('freed', 0))}" if ok
                                else f"Cache: failed ({d.get('error', '?')})"),
            done()))

    def _task_photos(self, done):
        # Offload new media, then best-effort compress via Pillow.
        self.bridge.command("media.offload", {}, lambda ok, d: (
            self._report.append(f"Photos: offloaded {d.get('uploaded', 0)} item(s)" if ok
                                else f"Photos: failed ({d.get('error', '?')})"),
            done()))

    def _task_whatsapp(self, done):
        self.bridge.command("whatsapp.backup", {"include_media": False}, lambda ok, d: (
            self._report.append(f"WhatsApp: backed up {d.get('uploaded', 0)} item(s)" if ok
                                else f"WhatsApp: failed ({d.get('error', '?')})"),
            done()))

    def _task_old(self, done):
        from .. import adb_utils
        # Delete files older than 30 days in the phone's Download/DCIM.
        out, ok = adb_utils.shell(
            "find", "/sdcard/Download", "/sdcard/DCIM",
            "-type", "f", "-mtime", "+30")
        if not ok or not out.strip():
            self._report.append("Old files: nothing to clean")
            done()
            return
        deleted = sum(1 for line in out.splitlines() if line.strip())
        adb_utils.shell("find", "/sdcard/Download", "/sdcard/DCIM",
                        "-type", "f", "-mtime", "+30", "-delete")
        self._report.append(f"Old files: deleted {deleted} file(s)")
        done()

    def _task_reboot(self, done):
        from .. import adb_utils
        ok = adb_utils.shell("reboot")[1]
        self._report.append("Reboot: sent" if ok else "Reboot: failed")
        done()
