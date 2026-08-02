"""Main pages: Dashboard, Detailed Dashboard, Settings, Mirror, Notifications,
Files and Tools. Each is a Page subclass registered in the sidebar."""

import json
import time

from pathlib import Path

from PySide6.QtCore import QTimer, Qt, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QAbstractItemView,
    QCheckBox,
    QComboBox,
    QFileDialog,
    QGridLayout,
    QGroupBox,
    QHeaderView,
    QLabel,
    QLineEdit,
    QListWidget,
    QListWidgetItem,
    QMessageBox,
    QPlainTextEdit,
    QProgressBar,
    QPushButton,
    QTableWidget,
    QTableWidgetItem,
    QVBoxLayout,
)

from .. import adb_utils, folder_sync, mirror, pairing
from .common import (
    ACCENT,
    RECEIVED_DIR,
    Card,
    LineGraph,
    Page,
    empty_state,
    fmt_size,
    fmt_time,
    icon,
    label,
    row,
    status_line,
)
from . import history
from ..server import DATA_DIR
from ..tls import certificate_fingerprint, format_fingerprint


def _b64(path):
    import base64
    try:
        with open(path, "rb") as f:
            return base64.b64encode(f.read()).decode("ascii")
    except Exception:
        return ""


# ------------------------------------------------------------------ Dashboard

class DashboardPage(Page):
    """Page 4 — Dashboard (Home): status bar, stats, quick actions, recent."""

    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "LiteLink", "Your phone, on your desk.")
        self.window = parent_window
        self._phone_info = {}

        # Status bar strip
        sb = QGroupBox("PHONE")
        sbv = QVBoxLayout(sb)
        self.dot = QLabel("●")
        self.dot.setStyleSheet("color: #667A84; font-size: 18px;")
        self.conn_text = label("Waiting for phone", "muted")
        self.host_lbl = label("", "muted")
        self.battery_lbl = label("", "muted")
        top = row(self.dot, self.conn_text, None)
        top.addStretch(1)
        top.addWidget(self.host_lbl)
        top.addWidget(self.battery_lbl)
        sbv.addLayout(top)
        self.add_card(sb)

        # Stats cards (grid of 4)
        sc = Card("TODAY")
        grid = QGridLayout()
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(10)
        self.stat_vals = {}
        stats = [("Notifications", "notifications"), ("Syncs", "syncs"),
                 ("Storage", "storage"), ("Latency", "latency")]
        for i, (name, key) in enumerate(stats):
            box = Card()
            box.add(label(name, "muted"))
            val = label("—")
            val.setObjectName("big")
            box.add(val)
            grid.addWidget(box, i // 2, i % 2)
            self.stat_vals[key] = val
        sc.add_layout(grid)
        self.add_card(sc)

        # Quick actions
        qc = Card("QUICK ACTIONS")
        actions = [
            ("mirror", "Mirror", self.window.start_mirror),
            ("notify", "Notify", lambda: self.window.go("Notifications")),
            ("files", "Files", lambda: self.window.go("Files")),
            ("find", "Find", self._ring),
            ("settings", "Settings", lambda: self.window.go("Settings")),
        ]
        ga = QGridLayout()
        ga.setHorizontalSpacing(10)
        ga.setVerticalSpacing(10)
        for i, (icn, text, cb) in enumerate(actions):
            b = QPushButton(icon(icn), text)
            b.setObjectName("tool")
            b.setMinimumHeight(64)
            b.clicked.connect(cb)
            ga.addWidget(b, 0, i)
        qc.add_layout(ga)
        self.add_card(qc)

        # Recent activity
        rc = Card("RECENT ACTIVITY")
        self.recent = QListWidget()
        self.recent.setMinimumHeight(180)
        rc.add(self.recent)
        clear_btn = QPushButton("Clear")
        clear_btn.setObjectName("ghost")
        clear_btn.clicked.connect(self.recent.clear)
        refresh_btn = QPushButton("Refresh")
        refresh_btn.setObjectName("ghost")
        refresh_btn.clicked.connect(self.refresh_all)
        rc.add_layout(row(clear_btn, refresh_btn, None))
        self.add_card(rc)

        # Activity log
        lc = Card("ACTIVITY LOG")
        self.log_area = QPlainTextEdit()
        self.log_area.setReadOnly(True)
        self.log_area.document().setMaximumBlockCount(600)
        self.log_area.setMinimumHeight(140)
        lc.add(self.log_area)
        self.add_card(lc)

        bridge.phone_changed.connect(self._on_phone)
        bridge.event_occurred.connect(self._on_event)
        bridge.log_line.connect(self.append_log)

        self._timer = QTimer(self)
        self._timer.setInterval(20000)
        self._timer.timeout.connect(self.refresh_all)
        self._timer.start()

        self.refresh_all()

    # ---------------------------------------------------------- slots
    def append_log(self, text):
        self.log_area.appendPlainText(text)

    def refresh_all(self):
        if not self.bridge.connected():
            self.conn_text.setText("Waiting for phone")
            self.dot.setStyleSheet("color: #667A84; font-size: 18px;")
            return
        self.conn_text.setText("Connected")
        self.dot.setStyleSheet(f"color: {ACCENT}; font-size: 18px;")
        self.bridge.command("system.info", {}, self._on_system)
        self.bridge.command("battery.get", {}, self._on_battery)
        self.bridge.command("maint.diagnostics", {}, self._on_diag)

    def _on_phone(self, ok):
        self.refresh_all()

    def _on_system(self, ok, data):
        if ok:
            self.host_lbl.setText(f"{data.get('model', '?')} · Android {data.get('android', '?')}")

    def _on_battery(self, ok, data):
        if ok:
            self.battery_lbl.setText(f"{data.get('level', '?')}%")

    def _on_diag(self, ok, data):
        if ok:
            total = data.get('internal_total', 0)
            free = data.get('internal_free', 0)
            pct = ((total - free) * 100 // total) if total else 0
            self.stat_vals["storage"].setText(f"{pct}%")
        self.stat_vals["latency"].setText("—")

    def _ring(self):
        self.bridge.command("phone.ring", {"duration_ms": 15000})
        self.bridge.log("Ringing phone for 15s")

    def _on_event(self, ev, data):
        if ev == "notification.received":
            self.recent.insertItem(0, f"[{fmt_time(data.get('time'))}] {data.get('app', '?')}")
            while self.recent.count() > 100:
                self.recent.takeItem(self.recent.count() - 1)
        elif ev == "signal.reading":
            pass
        elif ev == "device_info":
            model = data.get("model", "")
            android = data.get("android", "")
            if model or android:
                self.host_lbl.setText(f"{model} · Android {android}")


# ------------------------------------------------------------------ Detailed

class DetailedDashboardPage(Page):
    """Page 5 — Detailed view with full device info + battery history graph."""

    def __init__(self, bridge):
        super().__init__(bridge, "Dashboard (Detailed)",
                         "Full device information and battery history.")
        self.info = {}

        cc = Card("DEVICE")
        grid = QGridLayout()
        grid.setHorizontalSpacing(14)
        grid.setVerticalSpacing(8)
        keys = ["Phone Model", "Android", "Battery", "Storage", "WiFi IP", "Connection"]
        self.info_labels = {}
        for i, key in enumerate(keys):
            grid.addWidget(label(key), i, 0)
            self.info_labels[key] = label("—")
            self.info_labels[key].setAlignment(Qt.AlignRight | Qt.AlignVCenter)
            grid.addWidget(self.info_labels[key], i, 1)
        cc.add_layout(grid)
        self.add_card(cc)

        bc = Card("BATTERY")
        self.batt_bar = QProgressBar()
        self.batt_bar.setRange(0, 100)
        self.batt_bar.setFormat("—")
        self.batt_bar.setValue(0)
        bc.add(self.batt_bar)
        self.batt_detail = label("", "muted")
        bc.add(self.batt_detail)
        self.batt_graph = LineGraph(bc, height=150)
        bc.add(self.batt_graph)
        self.add_card(bc)

        hc = Card("STORAGE")
        self.storage_detail = label("", "muted")
        hc.add(self.storage_detail)
        self.add_card(hc)

        # periodic sampling for the history graph
        self._timer = QTimer(self)
        self._timer.setInterval(30000)
        self._timer.timeout.connect(self._sample)
        self._timer.start()
        self._sample()

        self.refresh()

    def refresh(self):
        if not self.bridge.connected():
            return
        self.bridge.command("system.info", {}, self._sys)
        self.bridge.command("battery.get", {}, self._batt)
        self.bridge.command("maint.diagnostics", {}, self._diag)

    def _sample(self):
        if not self.bridge.connected():
            return
        self.bridge.command("battery.get", {}, self._batt_sample)

    def _batt_sample(self, ok, data):
        if not ok:
            return
        history.append_sample("battery_history.jsonl", {
            "t": time.time() * 1000,
            "level": int(data.get("level", 0) or 0),
        })
        self._render_graph()

    def _render_graph(self):
        pts = history.battery_series(300)
        self.batt_graph.set_points(pts)

    def _sys(self, ok, data):
        if not ok:
            return
        self.info_labels["Phone Model"].setText(str(data.get("model", "—")))
        self.info_labels["Android"].setText(str(data.get("android", "—")))

    def _batt(self, ok, data):
        if not ok:
            return
        self.batt_bar.setValue(int(data.get("level", 0) or 0))
        self.batt_bar.setFormat(f"{data.get('level', '?')}%")
        self.info_labels["Battery"].setText(f"{data.get('level', '?')}%")
        self.batt_detail.setText(
            f"Temperature: {data.get('temperature_c', '?')}°C  ·  "
            f"Voltage: {data.get('voltage_mv', '?')} mV  ·  "
            f"Charging: {data.get('charging', '?')}")

    def _diag(self, ok, data):
        if not ok:
            return
        total = data.get('internal_total', 0)
        free = data.get('internal_free', 0)
        if total:
            used = total - free
            self.info_labels["Storage"].setText(f"{fmt_size(used)} used / {fmt_size(total)}")
            self.storage_detail.setText(
                f"Free: {fmt_size(free)}  ·  RAM: {fmt_size(data.get('avail_mem'))} free / "
                f"{fmt_size(data.get('total_mem'))}  ·  Uptime: {int(data.get('uptime_ms', 0) / 60000)} min")
        self.info_labels["Connection"].setText("Connected" if self.bridge.connected() else "Offline")

    def refresh(self):
        if not self.bridge.connected():
            return
        self.bridge.command("system.info", {}, self._sys)
        self.bridge.command("battery.get", {}, self._batt)
        self.bridge.command("maint.diagnostics", {}, self._diag)

    def _sys(self, ok, data):
        if not ok:
            return
        self.info_labels["Phone Model"].setText(str(data.get("model", "—")))
        self.info_labels["Android"].setText(str(data.get("android", "—")))

    def _batt(self, ok, data):
        if not ok:
            return
        self.batt_bar.setValue(int(data.get("level", 0) or 0))
        self.batt_bar.setFormat(f"{data.get('level', '?')}%")
        self.info_labels["Battery"].setText(f"{data.get('level', '?')}%")
        self.batt_detail.setText(
            f"Temperature: {data.get('temperature_c', '?')}°C  ·  "
            f"Voltage: {data.get('voltage_mv', '?')} mV  ·  "
            f"Charging: {data.get('charging', '?')}")

    def _diag(self, ok, data):
        if not ok:
            return
        total = data.get('internal_total', 0)
        free = data.get('internal_free', 0)
        if total:
            used = total - free
            self.info_labels["Storage"].setText(f"{fmt_size(used)} used / {fmt_size(total)}")
            self.storage_detail.setText(
                f"Free: {fmt_size(free)}  ·  RAM: {fmt_size(data.get('avail_mem'))} free / "
                f"{fmt_size(data.get('total_mem'))}  ·  Uptime: {int(data.get('uptime_ms', 0) / 60000)} min")
        self.info_labels["Connection"].setText("Connected" if self.bridge.connected() else "Offline")


# ------------------------------------------------------------------ Settings

class SettingsPage(Page):
    """Page 6 — Settings with Connection / Notifications / Folder Sync /
    Appearance / Advanced / About sections, persisted to config.json."""

    CONFIG = DATA_DIR / "config.json"

    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "Settings", "Connection, mirroring, appearance and about.")
        self.window = parent_window
        self._config = self._load()

        # Connection
        cc = Card("CONNECTION")
        self.mode = QComboBox()
        self.mode.addItems(["Local", "Cloud"])
        cc.add_layout(row(label("Mode"), self.mode, None))
        self.port = QLineEdit(str(self._config.get("port", 8765)))
        cc.add_layout(row(label("WebSocket Port"), self.port, None))
        self.vps_host = QLineEdit(self._config.get("vps_host", "litelink-relay.com"))
        cc.add_layout(row(label("VPS Host"), self.vps_host, None))
        self.vps_port = QLineEdit(str(self._config.get("vps_port", 8765)))
        cc.add_layout(row(label("VPS Port"), self.vps_port, None))
        self.auto_connect = QCheckBox("Auto-connect on launch")
        self.auto_connect.setChecked(self._config.get("auto_connect", True))
        cc.add(self.auto_connect)
        usb_btn = QPushButton("Setup USB reverse tunnel")
        usb_btn.clicked.connect(self._setup_usb)
        cc.add(usb_btn)
        self.add_card(cc)

        # Notifications
        nc = Card("NOTIFICATIONS")
        self.popups = QCheckBox("Show popups")
        self.popups.setChecked(self._config.get("popups", True))
        nc.add(self.popups)
        self.full_text = QCheckBox("Show full text")
        self.full_text.setChecked(self._config.get("full_text", True))
        nc.add(self.full_text)
        self.play_sound = QCheckBox("Play sound")
        self.play_sound.setChecked(self._config.get("sound", False))
        nc.add(self.play_sound)
        self.duration = QComboBox()
        self.duration.addItems(["3 seconds", "5 seconds", "10 seconds"])
        nc.add_layout(row(label("Popup duration"), self.duration, None))
        self.add_card(nc)

        # Folder sync
        fc = Card("FOLDER SYNC")
        self.sync_enabled = QCheckBox("Auto-sync")
        self.sync_enabled.setChecked(self._config.get("auto_sync", False))
        fc.add(self.sync_enabled)
        self.sync_interval = QComboBox()
        self.sync_interval.addItems(["Real-time", "5 minutes", "30 minutes", "1 hour"])
        fc.add_layout(row(label("Sync interval"), self.sync_interval, None))
        self.max_file = QLineEdit(str(self._config.get("max_file_mb", 100)))
        fc.add_layout(row(label("Max file size (MB)"), self.max_file, None))
        self.low_data = QCheckBox("Low-data mode (sync only when charging)")
        self.low_data.setChecked(self._config.get("low_data", False))
        fc.add(self.low_data)
        self.add_card(fc)

        # Appearance
        ac = Card("APPEARANCE")
        self.theme = QComboBox()
        self.theme.addItems(["Light", "Dark"])
        self.theme.setCurrentText(self._config.get("theme", "Light"))
        ac.add_layout(row(label("Theme"), self.theme, None))
        self.theme.currentTextChanged.connect(self._apply_theme)
        self.font_size = QComboBox()
        self.font_size.addItems(["Small", "Medium", "Large"])
        ac.add_layout(row(label("Font size"), self.font_size, None))
        self.language = QComboBox()
        self.language.addItems(["English"])
        ac.add_layout(row(label("Language"), self.language, None))
        self.add_card(ac)

        # Advanced
        adc = Card("ADVANCED")
        self.verbose = QCheckBox("Verbose logging")
        self.verbose.setChecked(self._config.get("verbose", False))
        adc.add(self.verbose)
        view_logs = QPushButton("View logs")
        view_logs.setObjectName("ghost")
        view_logs.clicked.connect(lambda: self.window.go("Home"))
        adc.add(view_logs)
        reset = QPushButton("Reset all settings")
        reset.setObjectName("danger")
        reset.clicked.connect(self._reset)
        adc.add(reset)
        self.add_card(adc)

        # About
        abc = Card("ABOUT")
        fp = format_fingerprint(certificate_fingerprint(DATA_DIR))
        abc.add(label(f"LiteLink Desktop v1.0.0", "muted"))
        abc.add(label(f"Data directory:\n{DATA_DIR}", "muted"))
        abc.add(label(f"Certificate fingerprint:\n{fp}", "muted"))
        abc.add(label(f"Received files land in:\n{RECEIVED_DIR}", "muted"))
        self.add_card(abc)

        save = QPushButton("Save settings")
        save.clicked.connect(self._save)
        self.add_card(Card("").__class__()) if False else None
        self.v.addWidget(save)

    # ---------------------------------------------------------- config
    def _load(self):
        try:
            return json.loads(self.CONFIG.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def _save(self):
        cfg = {
            "mode": self.mode.currentText().lower(),
            "port": int(self.port.text() or 8765),
            "vps_host": self.vps_host.text(),
            "vps_port": int(self.vps_port.text() or 8765),
            "auto_connect": self.auto_connect.isChecked(),
            "popups": self.popups.isChecked(),
            "full_text": self.full_text.isChecked(),
            "sound": self.play_sound.isChecked(),
            "auto_sync": self.sync_enabled.isChecked(),
            "max_file_mb": int(self.max_file.text() or 100),
            "low_data": self.low_data.isChecked(),
            "theme": self.theme.currentText(),
            "verbose": self.verbose.isChecked(),
        }
        self.CONFIG.parent.mkdir(parents=True, exist_ok=True)
        self.CONFIG.write_text(json.dumps(cfg, indent=2), encoding="utf-8")
        self.bridge.log("Settings saved")
        QMessageBox.information(self, "Saved", "Settings saved to config.json")

    def _reset(self):
        if self.CONFIG.exists():
            self.CONFIG.unlink()
        self.bridge.log("Settings reset to defaults")
        QMessageBox.information(self, "Reset", "Settings reset. Restart the app to apply.")

    def _apply_theme(self, theme):
        from .app import apply_theme
        app = QApplication.instance()
        if app is not None:
            apply_theme(app, theme)
        self.bridge.log(f"Theme set to {theme}")

    def _setup_usb(self):
        results = adb_utils.setup_usb_reverse()
        if not results:
            self.bridge.log("No phone detected over USB")
            return
        for serial, ok in results:
            self.bridge.log(f"USB reverse ({serial}): {'ok' if ok else 'failed'}")


# ------------------------------------------------------------------ Mirror

class MirrorPage(Page):
    """Page 7 — Mirror: launch scrcpy + real toolbar (off, rotate, shot,
    record, fullscreen, volume, refresh) and a live info bar."""

    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "Mirror", "Control your phone from this laptop.")
        self.window = parent_window

        mc = Card("SCREEN")
        self.placeholder = label(
            "The phone screen opens in its own scrcpy window. "
            "Start mirroring below, then drag/click to interact.", "muted")
        self.placeholder.setAlignment(Qt.AlignCenter)
        self.placeholder.setMinimumHeight(220)
        mc.add(self.placeholder)
        self.add_card(mc)

        tc = Card("TOOLBAR")
        self.screen_off = QCheckBox("Turn phone screen off while mirroring")
        tc.add(self.screen_off)
        tools = [
            ("power", "Screen off", self._screen_off),
            ("rotate", "Rotate", self._rotate),
            ("screenshot", "Screenshot", self._screenshot),
            ("record", "Record 10s", self._record),
            ("fullscreen", "Fullscreen", self._fullscreen),
            ("volume", "Volume up", self._volume),
            ("volume", "Volume down", self._volume_down),
            ("refresh", "Refresh", self._refresh),
        ]
        for icn, text, cb in tools:
            b = QPushButton(icon(icn), text)
            b.setObjectName("ghost")
            b.clicked.connect(cb)
            tc.add(b)
        self.add_card(tc)

        ic = Card("INFO")
        self.info_lbl = label("", "muted")
        ic.add(self.info_lbl)
        ic.add_layout(row(
            QPushButton("Start mirror"),
            QPushButton("Stop mirror"),
            None))
        self.add_card(ic)

        self._timer = QTimer(self)
        self._timer.setInterval(3000)
        self._timer.timeout.connect(self._poll_info)
        self._timer.start()
        self._poll_info()

    # toolbar handlers — real adb actions
    def _adb_available(self):
        from .. import adb_utils
        return adb_utils.get_device_state() == "connected"

    def _screen_off(self):
        from .. import adb_utils
        adb_utils.keyevent("KEYCODE_POWER")

    def _rotate(self):
        from .. import adb_utils
        cur = adb_utils.shell("settings", "get", "system", "user_rotation")[0].strip()
        adb_utils.rotate_screen(landscape=(cur != "1"))
        self.bridge.log("Rotation toggled")

    def _screenshot(self):
        from .. import adb_utils
        out_dir = Path.home() / "Pictures"
        out_dir.mkdir(parents=True, exist_ok=True)
        dest = out_dir / f"litelink_{int(time.time())}.png"
        path = adb_utils.screenshot_to(str(dest))
        if path:
            self.info_lbl.setText(f"Screenshot saved: {dest}")
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(dest.parent)))
            self.bridge.log(f"Screenshot saved: {dest}")
        else:
            self.info_lbl.setText("Screenshot failed — is the phone connected via adb?")

    def _record(self):
        from .. import adb_utils
        out_dir = Path.home() / "Videos"
        out_dir.mkdir(parents=True, exist_ok=True)
        dest = out_dir / f"litelink_rec_{int(time.time())}.mp4"
        self.info_lbl.setText("Recording 10s…")
        self.bridge.log("Screen recording started (10s)")
        path = adb_utils.record_screen(str(dest), seconds=10)
        if path:
            self.info_lbl.setText(f"Recording saved: {dest}")
            QDesktopServices.openUrl(QUrl.fromLocalFile(str(dest.parent)))
            self.bridge.log(f"Recording saved: {dest}")
        else:
            self.info_lbl.setText("Recording failed — is the phone connected via adb?")

    def _fullscreen(self):
        from .. import adb_utils
        adb_utils.keyevent("KEYCODE_APP_SWITCH")
        self.bridge.log("Fullscreen toggle sent")

    def _volume(self):
        from .. import adb_utils
        adb_utils.set_volume(up=True)

    def _volume_down(self):
        from .. import adb_utils
        adb_utils.set_volume(up=False)

    def _refresh(self):
        mirror.stop()
        QTimer.singleShot(800, self.window.start_mirror)

    def _poll_info(self):
        from .. import adb_utils
        if not adb_utils.get_device_state() == "connected":
            self.info_lbl.setText("Phone not connected over adb.")
            return
        size = adb_utils.wm_size()
        res = f"{size[0]}×{size[1]}" if size else "?"
        self.info_lbl.setText(f"Resolution: {res}  ·  Mirroring: {'running' if mirror.is_running() else 'stopped'}")


# ------------------------------------------------------------------ Notifications

class NotificationsPage(Page):
    """Page 8 — Notifications: live feed, search, app filter, clear-all."""

    def __init__(self, bridge):
        super().__init__(bridge, "Notifications", "Phone notifications stream in live.")
        self._items = []
        self._filter_app = ""

        card = Card("LIVE FEED")
        self.filt = QLineEdit()
        self.filt.setPlaceholderText("Filter by app or text…")
        self.filt.textChanged.connect(self._apply_filter)

        self.app_filter = QComboBox()
        self.app_filter.addItem("All apps")
        self.app_filter.currentTextChanged.connect(self._on_app_filter)

        clear = QPushButton("Clear all")
        clear.setObjectName("danger")
        clear.clicked.connect(self._clear_all)

        count = QPushButton()
        count.setObjectName("ghost")
        count.setEnabled(False)
        self.count_btn = count
        card.add_layout(row(self.filt, self.app_filter, clear, count, None))
        card.add_layout(row(QLabel("Double-click a notification to open its app."), None))

        self.list = QListWidget()
        self.list.setWordWrap(True)
        self.list.setMinimumHeight(320)
        card.add(self.list)
        self.empty = empty_state("No notifications yet", "notify",
                                 "Phone notifications will appear here live.")
        self.empty.setParent(card)
        self.empty.hide()
        card.add(self.empty)
        self.add_card(card)

        bridge.notification_arrived.connect(self._add)

    def _add(self, d):
        app = d.get("app", "?")
        title = d.get("title", "")
        text = d.get("text", "")
        ts = fmt_time(d.get("timestamp"))
        item_text = f"{app} — {title or '(no title)'}\n{text}\n{ts}"
        self._items.insert(0, (app, item_text.lower(), item_text))
        self._prune()
        self._populate()
        if self.app_filter.findText(app) < 0:
            self.app_filter.addItem(app)

    def _prune(self):
        while len(self._items) > 500:
            self._items.pop()
        self.count_btn.setText(str(len(self._items)))

    def _populate(self):
        q = self.filt.text().lower()
        app = self._filter_app
        self.list.clear()
        for appname, key, text in self._items:
            if app and appname != app:
                continue
            if q and q not in key:
                continue
            self.list.addItem(text)
        shown = self.list.count()
        self.count_btn.setText(str(len(self._items)))
        self.list.setVisible(shown > 0)
        self.empty.setVisible(shown == 0)

    def _apply_filter(self, _):
        self._populate()

    def _on_app_filter(self, text):
        self._filter_app = "" if text == "All apps" else text
        self._populate()

    def _clear_all(self):
        self._items.clear()
        self._populate()


# ------------------------------------------------------------------ Files

class FilesPage(Page):
    """Page 9 — Files: backups, media offload, WhatsApp, APK, phone browser,
    sync pairs and received files."""

    def __init__(self, bridge):
        super().__init__(bridge, "Files", "Pull phone data onto the laptop.")

        # Backups
        bb = Card("BACKUPS")
        self.bk_status = label("Export SMS or call history straight to your laptop.", "muted")
        bb.add(self.bk_status)
        self.btn_bk_sms = QPushButton("Backup SMS")
        self.btn_bk_calls = QPushButton("Backup call log")
        self.btn_bk_open = QPushButton("Open received folder")
        bb.add_layout(row(self.btn_bk_sms, self.btn_bk_calls, self.btn_bk_open, None))
        self.add_card(bb)

        # Media offload + WhatsApp
        mb = Card("MEDIA OFFLOAD")
        self.media_status = label("Copies new photos & videos from the phone (tracked by date).", "muted")
        mb.add(self.media_status)
        self.btn_offload = QPushButton("Offload new media")
        mb.add(self.btn_offload)
        self.add_card(mb)

        wb = Card("WHATSAPP BACKUP")
        self.include_media = QCheckBox("Include recent media")
        self.include_media.setChecked(True)
        self.wa_status = label("", "muted")
        wb.add(self.wa_status)
        self.btn_wa = QPushButton("Backup WhatsApp")
        wb.add_layout(row(self.btn_wa, self.include_media, None))
        self.add_card(wb)

        # APK
        ab = Card("APPS & APK")
        self.apps_status = label("", "muted")
        ab.add(self.apps_status)
        self.apps = QListWidget()
        self.apps.setMaximumHeight(160)
        ab.add(self.apps)
        self.pkg_ed = QLineEdit()
        self.pkg_ed.setPlaceholderText("Package (e.g. com.whatsapp)")
        self.btn_extract = QPushButton("Extract APK")
        self.btn_install = QPushButton("Install APK…")
        self.btn_list = QPushButton("List installed apps")
        ab.add_layout(row(self.pkg_ed, self.btn_extract, self.btn_install, None))
        ab.add(self.btn_list)
        self.add_card(ab)

        # Phone browser
        fb = Card("PHONE FILE BROWSER")
        self.fb_path = QLineEdit("/sdcard")
        self.fb_entries = {}
        self.fb_list = QListWidget()
        self.fb_list.setMinimumHeight(180)
        self.btn_go = QPushButton("Go")
        self.btn_pull = QPushButton("Pull to laptop")
        fb.add_layout(row(self.fb_path, self.btn_go, None))
        fb.add(self.fb_list)
        fb.add_layout(row(self.btn_pull, label("Double-click a folder to open it. Single-click a file to pull it."), None))
        self.fb_status = label("", "muted")
        fb.add(self.fb_status)
        self.add_card(fb)

        # Sync pairs
        sy = Card("SYNC FOLDERS")
        self.sync_status = label("Add a pair to auto-sync a laptop folder to the phone.", "muted")
        sy.add(self.sync_status)
        self.sync_list = QListWidget()
        self.sync_list.setMinimumHeight(120)
        sy.add(self.sync_list)
        self.btn_add_pair = QPushButton("Add pair")
        self.btn_sync_all = QPushButton("Sync all")
        sy.add_layout(row(self.btn_add_pair, self.btn_sync_all, None))
        self.add_card(sy)

        # Offline maps sync (feature 25)
        ms = Card("OFFLINE MAPS")
        self.maps_status = label("Sync offline map files between the laptop and phone.", "muted")
        ms.add(self.maps_status)
        self.maps_dir = QLineEdit(str(Path.home() / "Maps"))
        ms.add_layout(row(label("Maps folder"), self.maps_dir, None))
        maps_sync = QPushButton("Sync maps")
        maps_sync.clicked.connect(self._sync_maps)
        ms.add(maps_sync)
        self.add_card(ms)

        # Received
        rb = Card("RECEIVED ON LAPTOP")
        self.received = QListWidget()
        self.received.setMinimumHeight(140)
        rb.add(self.received)
        self.btn_rec_open = QPushButton("Open received folder")
        self.btn_rec_refresh = QPushButton("Refresh list")
        rb.add_layout(row(self.btn_rec_open, self.btn_rec_refresh, None))
        self.add_card(rb)

        # wire
        self._wire()
        bridge.file_received.connect(lambda *_: self._refresh_received())
        self._refresh_received()
        self._watchers = {}
        self._render_pairs()
        for pair in self._load_pairs():
            self._start_watcher(pair)

    def _wire(self):
        self.btn_bk_sms.clicked.connect(lambda: self._backup("sms.backup", "SMS"))
        self.btn_bk_calls.clicked.connect(lambda: self._backup("calls.backup", "Call log"))
        self.btn_bk_open.clicked.connect(self._open_received)
        self.btn_offload.clicked.connect(self._offload)
        self.btn_wa.clicked.connect(self._whatsapp)
        self.btn_extract.clicked.connect(self._extract)
        self.btn_install.clicked.connect(self._install_apk)
        self.btn_list.clicked.connect(self._list_apps)
        self.btn_go.clicked.connect(lambda: self._browse())
        self.btn_pull.clicked.connect(self._pull)
        self.fb_path.returnPressed.connect(lambda: self._browse())
        self.fb_list.itemDoubleClicked.connect(self._browse_into)
        self.btn_add_pair.clicked.connect(self._add_sync_pair)
        self.btn_sync_all.clicked.connect(self._sync_all)
        self.btn_rec_open.clicked.connect(self._open_received)
        self.btn_rec_refresh.clicked.connect(self._refresh_received)

    def _buttons_of(self, title):
        return []

    def _button_of(self, card_title, text):
        return QPushButton()

    # ------------------------------------------------------ actions
    def _backup(self, cmd, name):
        self.bk_status.setText(f"Backing up {name}…")
        self.bridge.command(cmd, {}, lambda ok, d, n=name: self.bk_status.setText(
            f"{n}: {d.get('rows', 0)} rows → backups/{d.get('file', '?')}" if ok
            else f"{n} failed: {d.get('error', '?')}"))

    def _offload(self):
        self.media_status.setText("Scanning & uploading…")
        self.bridge.command("media.offload", {}, lambda ok, d: self.media_status.setText(
            f"Uploaded {d.get('uploaded', 0)} item(s), {d.get('failed', 0)} failed." if ok
            else f"Offload failed: {d.get('error', '?')}"))

    def _whatsapp(self):
        self.wa_status.setText("Backing up…")
        self.bridge.command("whatsapp.backup", {"include_media": self.include_media.isChecked()},
                            lambda ok, d: self.wa_status.setText(
                                f"Uploaded {d.get('uploaded', 0)} item(s)." if ok
                                else f"WhatsApp backup failed: {d.get('error', '?')}"))

    def _extract(self):
        pkg = self.pkg_ed.text().strip()
        if not pkg:
            self.apps_status.setText("Enter a package name first.")
            return
        self.apps_status.setText(f"Extracting {pkg}…")
        self.bridge.command("apk.extract", {"package": pkg},
                            lambda ok, d: self.apps_status.setText(
                                "APK uploading…" if ok else f"Extract failed: {d.get('error', '?')}"))

    def _install_apk(self):
        path, _ = QFileDialog.getOpenFileName(self, "Choose APK", "", "APK files (*.apk)")
        if path:
            self.apps_status.setText(f"Sending {Path(path).name} to the phone for install…")
            self.bridge.push_file(path, "install")

    def _list_apps(self):
        self.apps_status.setText("Listing apps…")
        self.bridge.command("apk.list", {}, self._show_apps)

    def _show_apps(self, ok, data):
        self.apps.clear()
        if not ok:
            self.apps_status.setText(f"Failed: {data.get('error', '?')}")
            return
        apps = data.get("apps", [])
        self.apps_status.setText(f"{len(apps)} apps installed.")
        for a in apps:
            self.apps.addItem(f"{a.get('label', '?')}  ·  {a.get('package', '')}  ·  "
                              f"v{a.get('version', '')}  ·  {fmt_size(a.get('size'))}")

    # browser
    def _browse(self, path=None):
        path = path or self.fb_path.text().strip()
        if not path:
            return
        self.fb_path.setText(path)
        self.bridge.command("file.dir", {"path": path}, self._show_dir)

    def _show_dir(self, ok, data):
        self.fb_list.clear()
        self.fb_entries.clear()
        if not ok:
            self.fb_status.setText(f"Browse failed: {data.get('error', '?')}")
            return
        self.fb_status.setText(f"Showing: {data.get('path', '?')}")
        entries = data.get("entries", [])
        entries.sort(key=lambda e: (not e.get("is_dir", False), e.get("name", "")))
        for e in entries:
            name = e.get("name", "")
            self.fb_entries[name] = e
            is_dir = e.get("is_dir", False)
            extra = "" if is_dir else f"  ({fmt_size(e.get('size'))})"
            item = QListWidgetItem(f"{name}{extra}")
            if is_dir:
                item.setIcon(icon("files"))
            item.setData(Qt.UserRole, e)
            self.fb_list.addItem(item)

    def _browse_into(self, item):
        e = item.data(Qt.UserRole)
        if e and e.get("is_dir"):
            self._browse(e.get("path"))

    def _pull(self):
        selected = self.fb_list.currentItem()
        if not selected:
            self.fb_status.setText("Select a file first.")
            return
        e = selected.data(Qt.UserRole)
        if not e or e.get("is_dir"):
            self.fb_status.setText("Select a file (not a folder).")
            return
        folder = Path(self.fb_path.text().strip()).name
        name = e.get("name", "")
        self.bridge.command("file.get", {"path": e.get("path"), "dir": folder},
                            lambda ok, d: self.fb_status.setText(
                                f"Pulling {name}…" if ok else f"Pull failed: {d.get('error', '?')}"))

    # sync pairs (config-backed, real folder watching)
    def _add_sync_pair(self):
        path = QFileDialog.getExistingDirectory(self, "Choose laptop folder to sync")
        if not path:
            return
        pairs = self._load_pairs()
        if any(p["local"] == path for p in pairs):
            self.sync_status.setText("This folder is already being synced.")
            return
        pairs.append({"local": path, "phone": "/sdcard/LiteLink"})
        self._save_pairs(pairs)
        self._start_watcher(pairs[-1])
        self._render_pairs()
        self.sync_status.setText(f"Watching {path} — changes push to the phone.")

    def _load_pairs(self):
        try:
            return json.loads((DATA_DIR / "sync_pairs.json").read_text(encoding="utf-8"))
        except Exception:
            return []

    def _save_pairs(self, pairs):
        (DATA_DIR / "sync_pairs.json").write_text(json.dumps(pairs, indent=2), encoding="utf-8")

    def _start_watcher(self, pair):
        """Watch a local folder and push changed files to the phone."""
        if not Path(pair["local"]).is_dir():
            return
        fs = folder_sync.FolderSync(
            pair["local"],
            pair.get("phone", "/sdcard/LiteLink"),
            sender=lambda payload: self._sync_send(payload, pair),
        )
        fs.start()
        self._watchers[pair["local"]] = fs
        self.bridge.log(f"Folder sync watching: {pair['local']}")

    def _sync_send(self, payload, pair):
        """Deliver a sync payload to the phone via the bridge."""
        if payload.get("type") == "sync_delete":
            self.bridge.command("folder.sync", {"path": pair.get("phone", "/sdcard")},
                                lambda ok, d: None)
            return
        data = payload.get("data", "")
        # Decode base64 to a temp file and push it into the sync dir.
        import base64, tempfile
        try:
            raw = base64.b64decode(data)
        except Exception:
            return
        rel = payload.get("path", "").replace("\\", "/").lstrip("/")
        tmp = Path(tempfile.gettempdir()) / "litelink_sync" / (Path(rel).name or "file")
        tmp.parent.mkdir(parents=True, exist_ok=True)
        tmp.write_bytes(raw)
        if self.bridge.connected():
            self.bridge.push_file(str(tmp), "sync")
            self.bridge.log(f"Synced: {rel}")

    def _render_pairs(self):
        self.sync_list.clear()
        for pair in self._load_pairs():
            self.sync_list.addItem(f"{pair['local']}  ↔  {pair.get('phone', '/sdcard/LiteLink')}")

    def _sync_all(self):
        pairs = self._load_pairs()
        if not pairs:
            self.sync_status.setText("No sync pairs. Click 'Add pair' first.")
            return
        for pair in pairs:
            local = Path(pair["local"])
            if not local.is_dir():
                continue
            for f in list(local.rglob("*"))[:200]:
                if f.is_file():
                    self._sync_send({"type": "sync_file", "path": f.name, "data": _b64(f)}, pair)
        self.sync_status.setText(f"Synced {len(pairs)} pair(s).")

    def _sync_maps(self):
        folder = self.maps_dir.text().strip()
        if not folder or not Path(folder).is_dir():
            self.maps_status.setText("Maps folder not found.")
            return
        if not self.bridge.connected():
            self.maps_status.setText("Phone not connected.")
            return
        sent = 0
        for f in list(Path(folder).rglob("*"))[:200]:
            if f.is_file():
                self.bridge.push_file(str(f), "maps")
                sent += 1
        self.maps_status.setText(f"Sent {sent} map file(s) to the phone.")

    # received
    def _refresh_received(self):
        self.received.clear()
        if not RECEIVED_DIR.exists():
            return
        paths = sorted(RECEIVED_DIR.rglob("*"),
                       key=lambda p: p.stat().st_mtime if p.is_file() else 0, reverse=True)
        for p in paths:
            if p.is_file():
                rel = p.relative_to(RECEIVED_DIR)
                self.received.addItem(f"{rel}   ({fmt_size(p.stat().st_size)})")

    def _open_received(self):
        RECEIVED_DIR.mkdir(parents=True, exist_ok=True)
        QDesktopServices.openUrl(QUrl.fromLocalFile(str(RECEIVED_DIR)))


# ------------------------------------------------------------------ Tools

class ToolsPage(Page):
    """Page 10 — Tools grid opening the tool dialogs."""

    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "Tools", "Utilities and scripts sent to the phone.")
        self.window = parent_window

        from .apk_dialogs import ApkExtractorDialog, BatchApkInstallerDialog
        from .dialogs import (
            BatteryDialog,
            DebloatDialog,
            MaintenanceDialog,
            MpesaDialog,
            UssdDialog,
        )
        from .log_viewer import LogViewerDialog
        from .overlays import PairingDialog

        tools = [
            ("shield", "Debloat", lambda: DebloatDialog(bridge, self).exec()),
            ("battery", "Battery", lambda: BatteryDialog(bridge, self).exec()),
            ("sms", "SMS", lambda: self.window.go("Files")),
            ("money", "M-Pesa", lambda: MpesaDialog(bridge, self).exec()),
            ("ussd", "USSD", lambda: UssdDialog(bridge, self).exec()),
            ("signal", "Signal", lambda: self._signal()),
            ("apk", "APK Install", lambda: BatchApkInstallerDialog(bridge, self).exec()),
            ("apk", "APK Extract", lambda: ApkExtractorDialog(bridge, self).exec()),
            ("maintenance", "Maintenance", lambda: MaintenanceDialog(bridge, self).exec()),
            ("camera", "Camera", lambda: self._camera()),
            ("find", "Find", lambda: self.bridge.command("phone.ring", {"duration_ms": 15000})),
            ("tether", "Tether", lambda: self._tether()),
            ("clipboard", "Clipboard", lambda: self._clipboard()),
            ("refresh", "Log Viewer", lambda: LogViewerDialog(bridge, self).exec()),
            ("find", "Pairing", lambda: PairingDialog(self).exec()),
        ]

        grid = QGridLayout()
        grid.setHorizontalSpacing(10)
        grid.setVerticalSpacing(10)
        for i, (icn, text, cb) in enumerate(tools):
            b = QPushButton(icon(icn), text)
            b.setObjectName("tool")
            b.setMinimumHeight(76)
            b.clicked.connect(cb)
            grid.addWidget(b, i // 3, i % 3)
        self.v.addLayout(grid)

        self.status = label("", "muted")
        self.v.addWidget(self.status)

        # Clipboard history (feature 30)
        ch = Card("CLIPBOARD HISTORY")
        self.clip_list = QListWidget()
        self.clip_list.setMaximumHeight(200)
        ch.add(self.clip_list)
        refresh = QPushButton("Refresh")
        refresh.setObjectName("ghost")
        refresh.clicked.connect(self._refresh_clipboard)
        clear = QPushButton("Clear")
        clear.setObjectName("ghost")
        clear.clicked.connect(self._clear_clipboard)
        ch.add_layout(row(refresh, clear, None))
        self.v.addWidget(ch)
        self._refresh_clipboard()

    def _refresh_clipboard(self):
        from .clipboard_history import load
        self.clip_list.clear()
        for item in load():
            self.clip_list.addItem(item)
        self.clip_list.setCurrentRow(-1)

    def _clear_clipboard(self):
        from .clipboard_history import clear
        clear()
        self._refresh_clipboard()

    def _signal(self):
        self.status.setText("Signal: check the Home dashboard for live signal info.")
        self.bridge.command("signal.get", {}, lambda ok, d: self.status.setText(
            f"Signal: {d.get('signal_dbm')} dBm · {d.get('network_type')} · {d.get('operator')}"
            if ok else "Signal unavailable"))

    def _camera(self):
        self.status.setText("Capturing photo…")
        self.bridge.command("camera.capture", {"dir": "camera"}, lambda ok, d: self.status.setText(
            "Photo captured & uploaded." if ok else f"Camera failed: {d.get('error', '?')}"))

    def _tether(self):
        self.status.setText("Toggling hotspot…")
        self.bridge.command("tether.set", {"enabled": True, "kind": "wifi"},
                            lambda ok, d: self.status.setText(
                                "Hotspot enabled." if ok else "Tether failed (may need root)."))

    def _clipboard(self):
        self.status.setText("Reading clipboard…")
        self.bridge.command("clipboard.get", {}, lambda ok, d: self.status.setText(
            f"Clipboard: {d.get('text', '(empty)')}" if ok else "Clipboard read failed"))
