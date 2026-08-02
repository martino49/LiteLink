"""Settings sub-pages (Screens 8-15): General, Connection, Privacy, Sync,
Remote Control, Voice Commands, PC Notifications and About.

Each sub-page is a self-contained QWidget that persists its own section of
the shared config.json. The Settings page (app.py) hosts them in a
left-list + QStackedWidget layout."""

import json

from PySide6.QtCore import Qt, QUrl
from PySide6.QtGui import QDesktopServices
from PySide6.QtWidgets import (
    QCheckBox,
    QComboBox,
    QGridLayout,
    QGroupBox,
    QLabel,
    QLineEdit,
    QListWidget,
    QMessageBox,
    QPushButton,
    QStackedWidget,
    QVBoxLayout,
    QWidget,
)

from ..server import DATA_DIR
from .common import Card, label, row
from .wizards import ReconnectWizard


class SettingsPage(QWidget):
    """Left-list settings container hosting the 8 sub-pages."""

    def __init__(self, bridge, parent_window):
        super().__init__()
        self.bridge = bridge
        self.window = parent_window
        outer = QVBoxLayout(self)
        outer.setContentsMargins(26, 22, 26, 22)
        outer.setSpacing(12)

        title = QLabel("Settings")
        title.setObjectName("h1")
        outer.addWidget(title)

        body = QVBoxLayout()
        body.setSpacing(0)

        self.list = QListWidget()
        self.list.setMaximumWidth(220)
        self.pages = QStackedWidget()

        self._subs = [
            ("General", GeneralSettings(bridge, self.window)),
            ("Connection", ConnectionSettings(bridge, self.window)),
            ("Privacy", PrivacySettings(bridge, self.window)),
            ("Sync", SyncSettings(bridge, self.window)),
            ("Remote Control", RemoteControlSettings(bridge, self.window)),
            ("Voice Commands", VoiceCommandSettings(bridge, self.window)),
            ("PC Notifications", PcNotificationSettings(bridge, self.window)),
            ("About", AboutSettings(bridge, self.window)),
        ]
        for name, page in self._subs:
            self.list.addItem(name)
            self.pages.addWidget(page)

        h = QVBoxLayout()
        h.setSpacing(0)
        h.addWidget(self.list)
        h.addWidget(self.pages, 1)

        outer.addLayout(h, 1)
        self.list.currentRowChanged.connect(self.pages.setCurrentIndex)
        self.list.setCurrentRow(0)


class _BaseSettings(QWidget):
    """Shared config persistence + common section builder."""

    CONFIG = DATA_DIR / "config.json"

    def __init__(self, bridge, parent_window):
        super().__init__()
        self.bridge = bridge
        self.window = parent_window
        self.cfg = self._load()
        self.outer = QVBoxLayout(self)
        self.outer.setContentsMargins(16, 0, 16, 16)
        self.outer.setSpacing(14)
        self.outer.addStretch(1)

    def _load(self):
        try:
            return json.loads(self.CONFIG.read_text(encoding="utf-8"))
        except Exception:
            return {}

    def save(self):
        self.CONFIG.parent.mkdir(parents=True, exist_ok=True)
        self.CONFIG.write_text(json.dumps(self.cfg, indent=2), encoding="utf-8")

    def _card(self, title):
        c = Card(title)
        self.outer.insertWidget(self.outer.count() - 1, c)
        return c

    def _bool(self, key, default=False, text=""):
        cb = QCheckBox(text)
        cb.setChecked(self.cfg.get(key, default))
        cb.toggled.connect(lambda v: (self.cfg.__setitem__(key, v), self.save()))
        return cb

    def _combo(self, key, options, default=""):
        c = QComboBox()
        c.addItems(options)
        c.setCurrentText(self.cfg.get(key, default))
        c.currentTextChanged.connect(lambda v: (self.cfg.__setitem__(key, v), self.save()))
        return c

    def _text(self, key, default="", placeholder=""):
        e = QLineEdit(self.cfg.get(key, default))
        e.setPlaceholderText(placeholder)
        e.textChanged.connect(lambda v: (self.cfg.__setitem__(key, v), self.save()))
        return e


# ------------------------------------------------------------------ General

class GeneralSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("GENERAL")
        c.add_layout(row(label("Theme"), self._combo("theme", ["Light", "Dark"], "Light")))
        c.add_layout(row(label("Font size"), self._combo("font_size", ["Small", "Medium", "Large"], "Medium")))
        c.add_layout(row(label("Language"), self._combo("language", ["English"], "English")))
        c.add(self._bool("start_with_windows", False, "Start LiteLink with Windows"))


# ------------------------------------------------------------------ Connection

class ConnectionSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("CONNECTION")
        c.add_layout(row(label("Mode"), self._combo("mode", ["Local", "Cloud"], "Local")))
        c.add_layout(row(label("Laptop IP"), self._text("host", "", "auto-detected")))
        c.add_layout(row(label("Port"), self._text("port", "8765")))
        c.add_layout(row(label("VPS Host"), self._text("vps_host", "")))
        c.add_layout(row(label("VPS Port"), self._text("vps_port", "8765")))

        c2 = self._card("RECONNECT")
        btn = QPushButton("Reconnect phone wizard")
        btn.clicked.connect(lambda: ReconnectWizard(self).exec())
        c2.add(btn)


# ------------------------------------------------------------------ Privacy

class PrivacySettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("PRIVACY")
        c.add(self._bool("encryption", True, "End-to-end encryption (AES-256)"))
        c.add_layout(row(label("Device ID"), label(_device_id(), "muted")))

        from .. import pairing
        code = pairing.get_pairing_code(DATA_DIR)
        c.add_layout(row(label("Pairing code"), label(code, "muted")))


# ------------------------------------------------------------------ Sync

class SyncSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("FOLDER SYNC")
        c.add_layout(row(label("Sync interval"),
                         self._combo("sync_interval", ["Real-time", "5 seconds", "30 seconds", "Manual"], "Real-time")))
        c.add_layout(row(label("Max file size (MB)"), self._text("max_file_mb", "100")))
        c.add(self._bool("low_data", False, "Low-data mode"))
        c.add_layout(row(label("Excluded types"), self._text("excluded_types", ".tmp, .log")))
        c.add(self._bool("sync_on_battery", True, "Sync on battery power"))


# ------------------------------------------------------------------ Remote control

class RemoteControlSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("TRACKPAD")
        c.add_layout(row(label("Sensitivity"), self._combo("trackpad_sens", ["Low", "Medium", "High"], "Medium")))
        c.add_layout(row(label("Scroll speed"), self._combo("scroll_speed", ["Slow", "Normal", "Fast"], "Normal")))
        c.add(self._bool("invert_scroll", False, "Invert scroll direction"))
        c.add(self._bool("screen_dim", True, "Dim phone screen when trackpad is active"))

        k = self._card("KEYBOARD")
        k.add_layout(row(label("Key repeat rate"), self._combo("key_repeat", ["Slow", "Normal", "Fast"], "Normal")))

        g = self._card("GAMEPAD")
        g.add(self._bool("enable_xinput", False, "Enable XInput emulation (Xbox controller)"))
        g.add_layout(row(label("Button mapping"), self._combo("gamepad_map", ["Default", "Custom"], "Default")))


# ------------------------------------------------------------------ Voice commands

class VoiceCommandSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("VOICE CONTROL")
        c.add(self._bool("voice_enabled", False, "Enable voice commands"))
        self.list = QListWidget()
        self.list.setMaximumHeight(180)
        c.add(self.list)
        self._refresh()
        c.add_layout(row(QPushButton("Add command"), QPushButton("Remove selected"), None))

    def _refresh(self):
        self.list.clear()
        for cmd in self.cfg.get("voice_commands", []):
            self.list.addItem(f"{cmd.get('phrase', '?')}  →  {cmd.get('action', '?')}")


# ------------------------------------------------------------------ PC notifications

class PcNotificationSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        c = self._card("PC NOTIFICATIONS")
        c.add(self._bool("pc_notifications", False, "Send PC notifications to phone"))
        self.list = QListWidget()
        self.list.setMaximumHeight(160)
        c.add(self.list)
        c.add_layout(row(label("Filter keywords"), self._text("pc_filter", "")))
        self._refresh()

    def _refresh(self):
        self.list.clear()
        for app in self.cfg.get("pc_notification_apps", ["WhatsApp Desktop", "Slack", "Outlook", "Teams"]):
            self.list.addItem(app)


# ------------------------------------------------------------------ About

class AboutSettings(_BaseSettings):
    def __init__(self, bridge, parent_window):
        super().__init__(bridge, parent_window)
        from .. import __version__
        from ..tls import certificate_fingerprint, format_fingerprint

        c = self._card("ABOUT")
        c.add(label(f"LiteLink v{__version__}", "muted"))
        c.add(label(f"Data directory:\n{DATA_DIR}", "muted"))
        fp = format_fingerprint(certificate_fingerprint(DATA_DIR))
        c.add(label(f"Certificate fingerprint:\n{fp}", "muted"))
        c.add_layout(row(
            QPushButton("Export logs"),
            QPushButton("Reset settings"),
            None))

        links = self._card("LINKS")
        for name, url in [("GitHub", "https://github.com/"),
                          ("Website", "https://litelink.example"),
                          ("Report Bug", "https://github.com/issues"),
                          ("Request Feature", "https://github.com/issues")]:
            b = QPushButton(name)
            b.setObjectName("ghost")
            b.clicked.connect(lambda _, u=url: QDesktopServices.openUrl(QUrl(u)))
            links.add(b)


def _device_id():
    import uuid
    return str(uuid.getnode())[-8:].upper()
