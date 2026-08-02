"""Main window: bottom navigation across all main pages + system tray + entry."""

import sys
import threading

from PySide6.QtCore import QSize, Qt
from PySide6.QtGui import QAction, QIcon
from PySide6.QtWidgets import (
    QApplication,
    QButtonGroup,
    QFrame,
    QGraphicsOpacityEffect,
    QHBoxLayout,
    QLabel,
    QMainWindow,
    QMenu,
    QMessageBox,
    QSizePolicy,
    QStackedWidget,
    QSystemTrayIcon,
    QToolButton,
    QVBoxLayout,
    QWidget,
)

from .bridge import Bridge, watch_usb
from .common import ACCENT, DARK_QSS, LIGHT_QSS, icon, make_icon
from .connection import ConnectionPage
from .pages import (
    DashboardPage,
    DetailedDashboardPage,
    FilesPage,
    MirrorPage,
    NotificationsPage,
    ToolsPage,
)
from .second_screen import SecondScreenPage
from .settings_pages import SettingsPage
from .wizards import OnboardingWizard


class MainWindow(QMainWindow):
    def __init__(self, bridge):
        super().__init__()
        self.bridge = bridge
        self.setWindowTitle("LiteLink")
        self.setMinimumSize(900, 620)
        self.resize(1080, 720)
        self.setWindowIcon(make_icon())

        central = QWidget()
        self.setCentralWidget(central)
        root = QVBoxLayout(central)
        root.setContentsMargins(0, 0, 0, 0)
        root.setSpacing(0)

        # Pages fill the area above the bottom nav
        self.pages = QStackedWidget()
        self.home = DashboardPage(bridge, self)
        self.connection = ConnectionPage(bridge, self)
        self.detailed = DetailedDashboardPage(bridge)
        self.mirror = MirrorPage(bridge, self)
        self.notifications = NotificationsPage(bridge)
        self.files = FilesPage(bridge)
        self.tools = ToolsPage(bridge, self)
        self.second_screen = SecondScreenPage(bridge, self)
        self.settings = SettingsPage(bridge, self)
        self._page_widgets = {
            "Home": self.home,
            "Connection": self.connection,
            "Detailed": self.detailed,
            "Mirror": self.mirror,
            "Notifications": self.notifications,
            "Files": self.files,
            "Tools": self.tools,
            "Second Screen": self.second_screen,
            "Settings": self.settings,
        }
        for page in self._page_widgets.values():
            self.pages.addWidget(page)
        root.addWidget(self.pages, 1)

        # Bottom navigation (Android-style): icon above label, active fill
        self._page_names = list(self._page_widgets.keys())
        nav_icons = {
            "Home": "home",
            "Connection": "tether",
            "Detailed": "battery",
            "Mirror": "mirror",
            "Notifications": "notify",
            "Files": "files",
            "Tools": "settings",
            "Second Screen": "mirror",
            "Settings": "settings",
        }
        self.nav = QFrame()
        self.nav.setObjectName("bottomnav")
        navlay = QHBoxLayout(self.nav)
        navlay.setContentsMargins(8, 4, 8, 4)
        navlay.setSpacing(4)
        self._nav_buttons = []
        from PySide6.QtWidgets import QToolButton, QButtonGroup
        self._nav_group = QButtonGroup(self)
        self._nav_group.setExclusive(True)
        for name in self._page_names:
            b = QToolButton()
            b.setObjectName("navbtn")
            b.setText(name)
            b.setIcon(icon(nav_icons.get(name, "settings"), 22))
            b.setIconSize(QSize(22, 22))
            b.setToolButtonStyle(Qt.ToolButtonTextUnderIcon)
            b.setCheckable(True)
            b.setSizePolicy(QSizePolicy.Expanding, QSizePolicy.Expanding)
            b.setMinimumWidth(72)
            b.clicked.connect(lambda checked=False, n=name: self.go(n))
            self._nav_group.addButton(b)
            navlay.addWidget(b)
            self._nav_buttons.append(b)
        navlay.addStretch(0)
        self.nav.setFixedHeight(66)
        root.addWidget(self.nav)
        self._nav_buttons[0].setChecked(True)

        # status bar
        self.dot = QLabel("●")
        self.dot.setStyleSheet("color: #667A84;")
        self.conn_label = QLabel("phone not connected")
        bar = self.statusBar()
        bar.addWidget(self.dot)
        bar.addWidget(self.conn_label)
        bar.addPermanentWidget(QLabel("LiteLink"))

        bridge.phone_changed.connect(self._on_phone)
        bridge.log_line.connect(self._on_log)

        self._setup_hotkeys()
        self._setup_tray()

    # ------------------------------------------------------------------ geometry
    def showEvent(self, event):
        super().showEvent(event)
        # Keep the window within the screen's work area (above the taskbar),
        # so the bottom nav is never hidden behind it.
        try:
            screen = self.screen()
            if screen is None:
                return
            work = screen.availableGeometry()
            g = self.geometry()
            if g.width() > work.width():
                g.setWidth(work.width())
            if g.height() > work.height():
                g.setHeight(work.height())
            if g.right() > work.right():
                g.moveRight(work.right())
            if g.bottom() > work.bottom():
                g.moveBottom(work.bottom())
            if g.left() < work.left():
                g.moveLeft(work.left())
            if g.top() < work.top():
                g.moveTop(work.top())
            self.setGeometry(g)
        except Exception:
            pass

    def resizeEvent(self, event):
        super().resizeEvent(event)
        # Keep the bottom nav visible: if the window is too short, grow it.
        try:
            if self.height() < self.minimumHeight():
                self.setMinimumHeight(self.minimumHeight())
                self.resize(self.width(), self.minimumHeight())
        except Exception:
            pass

    # ------------------------------------------------------------------ actions
    def go(self, name):
        if name in self._page_widgets:
            target = self._page_widgets[name]
            if self.pages.currentWidget() is not target:
                self._fade_in(target)
                self.pages.setCurrentWidget(target)
            idx = self._page_names.index(name)
            if 0 <= idx < len(self._nav_buttons):
                self._nav_buttons[idx].setChecked(True)

    def _fade_in(self, widget):
        """Subtle opacity fade when switching pages."""
        try:
            from PySide6.QtCore import QPropertyAnimation
            effect = QGraphicsOpacityEffect(widget)
            widget.setGraphicsEffect(effect)
            anim = QPropertyAnimation(effect, b"opacity")
            anim.setDuration(150)
            anim.setStartValue(0.6)
            anim.setEndValue(1.0)
            anim.finished.connect(lambda: widget.setGraphicsEffect(None))
            self._nav_anim = anim  # keep a reference
            anim.start()
        except Exception:
            pass

    def _on_phone(self, ok):
        self.dot.setStyleSheet(f"color: {ACCENT};" if ok else "color: #667A84;")
        self.conn_label.setText("phone connected" if ok else "phone not connected")
        if ok:
            self.home.refresh_all()
            self.detailed.refresh()

    def _on_log(self, text):
        self.home.append_log(text)
        self.statusBar().showMessage(text, 3000)

    def start_mirror(self):
        from .. import mirror

        if not mirror.is_available():
            QMessageBox.warning(self, "LiteLink", "scrcpy not found in desktop/bin")
            return
        self.bridge.mirror_screen_off = self.settings.screen_off.isChecked() \
            if hasattr(self.settings, "screen_off") else False
        ok, err = mirror.launch(turn_screen_off=self.bridge.mirror_screen_off)
        if not ok:
            QMessageBox.warning(self, "Mirroring", err or "failed to start scrcpy")
        else:
            self.bridge.mirror_wanted = True
            self.bridge.log("scrcpy mirroring started")

    def stop_mirror(self):
        from .. import mirror

        self.bridge.mirror_wanted = False
        mirror.stop()
        self.bridge.log("scrcpy mirroring stopped")

    def regenerate_code(self):
        from .. import pairing
        from ..server import DATA_DIR

        code = pairing.regenerate_pairing_code(DATA_DIR)
        self.bridge.log(f"New pairing code: {code}")
        if self.bridge.connected():
            QMessageBox.information(self, "Pairing code changed",
                                    "The phone will need to reconnect with the new code.")

    # ------------------------------------------------------------------ hotkeys
    def _setup_hotkeys(self):
        from .hotkeys import HotkeyManager

        self.hotkeys = HotkeyManager(self)
        self.hotkeys.triggered.connect(self._on_hotkey)
        self.hotkeys.start()

    def _on_hotkey(self, action):
        self.bridge.log(f"Hotkey: {action}")
        if action == "mirror":
            self.start_mirror()
        elif action == "find_phone":
            self.bridge.command("phone.ring", {"duration_ms": 15000})
        elif action == "files":
            self.go("Files")
        elif action == "sync":
            self.bridge.command("folder.sync", {"path": "/sdcard"}, lambda ok, d: None)

    # ------------------------------------------------------------------ tray
    def _setup_tray(self):
        self.tray = QSystemTrayIcon(make_icon(), self)
        self.tray.setToolTip("LiteLink")
        menu = QMenu()

        show_action = QAction("Show LiteLink", self)
        show_action.triggered.connect(self.show)
        menu.addAction(show_action)

        mirror_action = QAction("Mirror now", self)
        mirror_action.triggered.connect(self.start_mirror)
        menu.addAction(mirror_action)

        find_action = QAction("Find phone", self)
        find_action.triggered.connect(lambda: self.bridge.command("phone.ring", {"duration_ms": 15000}))
        menu.addAction(find_action)

        sync_action = QAction("Sync now", self)
        sync_action.triggered.connect(lambda: self.bridge.command("folder.sync", {"path": "/sdcard"}, lambda ok, d: None))
        menu.addAction(sync_action)

        settings_action = QAction("Settings", self)
        settings_action.triggered.connect(lambda: self.go("Settings"))
        menu.addAction(settings_action)

        menu.addSeparator()
        quit_action = QAction("Exit", self)
        quit_action.triggered.connect(QApplication.quit)
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
            "LiteLink", "Still running in the system tray",
            QSystemTrayIcon.Information, 2000)


def run():
    from .. import adb_utils
    from ..server import DATA_DIR
    from .splash import SplashWindow

    app = QApplication(sys.argv)
    app.setWindowIcon(make_icon())

    # Theme from settings (default: light, matching the app's light-blue look).
    theme = "Light"
    cfg = DATA_DIR / "config.json"
    if cfg.exists():
        try:
            import json
            theme = json.loads(cfg.read_text(encoding="utf-8")).get("theme", "Light")
        except Exception:
            theme = "Light"
    apply_theme(app, theme)

    bridge = Bridge()
    bridge.start()
    bridge.start_mdns()

    results = adb_utils.setup_usb_reverse()
    for serial, ok in results:
        bridge.log_line.emit(f"USB reverse ({serial}): {'ok' if ok else 'failed'}")
    threading.Thread(target=watch_usb, args=(bridge,), daemon=True).start()
    from .bridge import watch_mirror
    threading.Thread(target=watch_mirror, args=(bridge,), daemon=True).start()
    from .clipboard_history import ClipboardMonitor
    bridge.clipboard_monitor = ClipboardMonitor()
    bridge.clipboard_monitor.start()
    from .pc_notifications import PcNotificationMonitor
    bridge.pc_notif_monitor = PcNotificationMonitor(
        lambda ev: bridge.hub.send_pc_notification(ev.get("app", "PC"), ev.get("title", ""), ev.get("text", "")))
    bridge.pc_notif_monitor.start()
    bridge.hub.pc_notif_monitor = bridge.pc_notif_monitor  # for pc_controller to reach
    # apply notification filter keywords from config (feature 75)
    try:
        import json as _json
        _cfg = _json.loads((DATA_DIR / "config.json").read_text(encoding="utf-8"))
        _kw = [k.strip() for k in str(_cfg.get("pc_notif_keywords", "")).split(",") if k.strip()]
        bridge.pc_notif_monitor.set_keywords(_kw)
    except Exception:
        pass

    state = {"main": None}

    def open_main():
        if state["main"] is None:
            state["main"] = MainWindow(bridge)
        state["main"].show()

    def open_first_run():
        wizard = OnboardingWizard()
        wizard.exec()
        # Mark setup as done so the wizard only ever runs once.
        try:
            (DATA_DIR / "setup_done").write_text("1")
        except OSError:
            pass
        open_main()

    # First run: show onboarding. Otherwise splash -> main.
    if not (DATA_DIR / "setup_done").exists():
        open_first_run()
    else:
        splash = SplashWindow(open_main)
        splash.show()

    bridge.log_line.emit("LiteLink GUI started")
    _check_updates_later(state, bridge)
    exit_code = app.exec()

    bridge.stop()
    sys.exit(exit_code)


def apply_theme(app, theme):
    """Apply the light or dark theme (qt-material + brand overrides)."""
    from .common import apply_theme as _apply_qt_material
    _apply_qt_material(app, theme)


def _check_updates_later(state, bridge):
    """Auto-update check (feature 47): fetch a version manifest in the
    background and show the UpdateDialog if a newer version exists."""
    import threading as _t
    from .. import __version__

    def _check():
        import json
        import urllib.request
        from .overlays import UpdateDialog

        url = "https://raw.githubusercontent.com/LiteLink/litelink/main/desktop/version.json"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "LiteLink"})
            with urllib.request.urlopen(req, timeout=8) as r:
                data = json.loads(r.read().decode("utf-8"))
            latest = str(data.get("version", ""))
            notes = str(data.get("notes", ""))
            if latest and latest != __version__:
                bridge.log_line.emit(f"Update available: v{latest}")
                if state.get("main"):
                    from PySide6.QtCore import QTimer
                    QTimer.singleShot(2500, lambda: UpdateDialog(latest, notes, state["main"]).exec())
        except Exception:
            pass  # offline or unreachable — silently skip

    _t.Thread(target=_check, daemon=True).start()


if __name__ == "__main__":
    run()
