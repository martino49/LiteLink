"""Shared Qt styling, widgets and helpers for the LiteLink desktop GUI.

Kept separate so every page module (dashboard, mirror, tools, dialogs, …)
can reuse the same look without importing the whole GUI entry point.
"""

import datetime
import logging
import os
import sys

from pathlib import Path

from PySide6.QtCore import QPointF, Qt
from PySide6.QtGui import QColor, QIcon, QPainter, QPen, QPixmap
from PySide6.QtWidgets import (
    QApplication,
    QCheckBox,
    QFrame,
    QHBoxLayout,
    QLabel,
    QProgressBar,
    QPushButton,
    QScrollArea,
    QStyle,
    QVBoxLayout,
    QWidget,
)

from ..server import DATA_DIR

log = logging.getLogger("litelink.gui")

ACCENT = "#009688"
RECEIVED_DIR = DATA_DIR / "received"

# Spacing scale (Material 4dp increments) — single source for layout gaps.
SP = 4
SP_SM = SP * 2      # 8
SP_MD = SP * 3      # 12
SP_LG = SP * 4      # 16
SP_XL = SP * 6      # 24

# Radius scale.
R_SM = 8
R_MD = 10
R_LG = 14
R_XL = 16

# Material 3 design tokens — single source of truth for both themes.
# Dark uses true black surfaces (OLED-style); light uses a soft blue accent.
_DARK = {
    "bg": "#000000",          # app background (pure black)
    "surface": "#0A0A0A",     # cards (near-black)
    "surface2": "#141414",    # elevated / inputs
    "surface3": "#1F1F1F",    # hover
    "border": "#262626",      # hairline borders
    "text": "#EDEDED",        # primary text
    "text2": "#8A8A8A",       # secondary / muted text
    "primary": "#26A69A",     # teal (accent, bright enough on black)
    "primary_dark": "#00796B",
    "primary_bg": "#0F2B28",  # selected / tinted background
    "danger": "#E57373",
    "success": "#81C784",
    "nav_bg": "#000000",
    "nav_border": "#1A1A1A",
}

_LIGHT = {
    "bg": "#F2F5F5",
    "surface": "#FFFFFF",
    "surface2": "#F7F9FA",
    "surface3": "#ECEFF1",
    "border": "#E0E6EA",
    "text": "#263238",
    "text2": "#607D8B",
    "primary": "#009688",     # teal
    "primary_dark": "#00796B",
    "primary_bg": "#E0F2F1",
    "danger": "#E53935",
    "success": "#43A047",
    "nav_bg": "#FFFFFF",
    "nav_border": "#ECEFF1",
}

PALETTES = {"dark": _DARK, "light": _LIGHT}


def _qss_for(p):
    return f"""
QMainWindow, QDialog {{ background: {p['bg']}; }}
QFrame#card {{
    background: {p['surface']};
    border: 1px solid {p['border']};
    border-radius: 16px;
}}
QFrame#card:hover {{ border: 1px solid {p['primary']}; }}
QFrame#pill {{
    background: {p['primary_bg']};
    border-radius: 8px;
}}
QLabel#pill {{
    font-size: 11px; font-weight: 700; letter-spacing: 1.2px;
    color: {p['primary_dark'] if p is _LIGHT else p['primary']};
    background: transparent;
}}
QLabel#h1 {{ font-size: 22px; font-weight: 700; color: {p['text']}; }}
QLabel#muted {{ color: {p['text2']}; }}
QLabel#big {{ font-size: 34px; font-weight: 800; color: {p['text']}; }}
QFrame#bottomnav {{
    background: {p['nav_bg']};
    border-top: 1px solid {p['nav_border']};
}}
QToolButton#navbtn {{
    border: none; border-radius: 12px;
    color: {p['text2']};
    background: transparent;
    padding: 6px 8px;
    font-size: 11px;
}}
QToolButton#navbtn:hover {{ background: {p['surface3']}; color: {p['text']}; }}
QToolButton#navbtn:checked {{
    background: {p['primary']}; color: #FFFFFF; font-weight: 700;
}}
QToolButton#navbtn:checked QToolButton {{ }}
QListWidget, QTableWidget {{
    background: {p['surface']};
    border: 1px solid {p['border']}; border-radius: 12px;
    color: {p['text']};
}}
QListWidget::item:selected, QTableWidget::item:selected {{
    background: {p['primary_bg']}; color: {p['primary_dark']};
}}
QLineEdit, QTextEdit, QPlainTextEdit {{
    background: {p['surface2']};
    border: 1px solid {p['border']}; border-radius: 10px;
    color: {p['text']};
}}
QLineEdit:focus, QTextEdit:focus, QPlainTextEdit:focus {{
    border: 2px solid {p['primary']};
}}
QProgressBar {{
    background: {p['surface3']}; border: none; border-radius: 6px;
    color: {p['text']}; height: 16px;
}}
QProgressBar::chunk {{ background: {p['primary']}; border-radius: 6px; }}
QStatusBar {{ background: {p['nav_bg']}; color: {p['text2']}; }}
QStatusBar::item {{ border: none; }}
QTableWidget {{ gridline-color: {p['border']}; }}
"""

DARK_QSS = _qss_for(_DARK)
LIGHT_QSS = _qss_for(_LIGHT)


def apply_theme(app, theme="light"):
    """Apply qt-material + brand overrides to the whole application.

    theme: 'light' or 'dark'. Defaults to light to match the Android app.
    """
    try:
        from qt_material import apply_stylesheet
    except ImportError:
        app.setStyleSheet(LIGHT_QSS if theme.lower() != "dark" else DARK_QSS)
        return

    is_dark = theme.lower() == "dark"
    qt_theme = "dark_teal.xml" if is_dark else "light_teal.xml"
    apply_stylesheet(
        app,
        theme=qt_theme,
        extra={
            "primaryColor": "#26A69A" if is_dark else "#009688",
            "primaryLightColor": "#B2DFDB",
            "primaryDarkColor": "#00796B",
            "secondaryColor": "#26A69A" if is_dark else "#009688",
        },
        invert_secondary=True,
    )
    app.setStyleSheet(app.styleSheet() + (DARK_QSS if is_dark else LIGHT_QSS))


def asset_path(name):
    base = getattr(sys, "_MEIPASS", None) or os.path.join(os.path.dirname(__file__), "..", "..", "..", "assets")
    return os.path.join(base, name)


def make_icon():
    """Brand icon: the tiled app icon (with background) when bundled, else
    two linked nodes placeholder."""
    for candidate in (asset_path("litelink.ico"), asset_path("litelink-icon.png")):
        if os.path.exists(candidate):
            return QIcon(candidate)
    pix = QPixmap(64, 64)
    pix.fill(Qt.transparent)
    p = QPainter(pix)
    p.setRenderHint(QPainter.Antialiasing)
    pen = QPen(QColor(ACCENT))
    pen.setWidth(6)
    p.setPen(pen)
    p.drawEllipse(6, 12, 20, 20)
    p.drawEllipse(38, 32, 20, 20)
    p.drawLine(26, 22, 38, 42)
    p.end()
    return QIcon(pix)


# Map icon name -> Qt standard icon enum, used as a fallback when the SVG
# bundle is missing (e.g. running from a broken install).
_STANDARD_FALLBACKS = {
    "files": QStyle.StandardPixmap.SP_DirIcon,
    "find": QStyle.StandardPixmap.SP_FileDialogContentsView,
    "settings": QStyle.StandardPixmap.SP_FileDialogDetailedView,
    "refresh": QStyle.StandardPixmap.SP_BrowserReload,
    "close": QStyle.StandardPixmap.SP_DialogCloseButton,
    "warning": QStyle.StandardPixmap.SP_MessageBoxWarning,
    "record": QStyle.StandardPixmap.SP_MediaPlay,
    "ok": QStyle.StandardPixmap.SP_DialogApplyButton,
}

_icon_cache = {}


def icon(name, size=18):
    """Return a QIcon for a named UI glyph.

    Prefers the bundled SVG set; falls back to a Qt standard icon so buttons
    never end up blank. Results are cached per (name, size).
    """
    key = (name, size)
    if key in _icon_cache:
        return _icon_cache[key]

    svg = asset_path(os.path.join("icons", f"{name}.svg"))
    if os.path.exists(svg):
        ic = QIcon(svg)
    else:
        std = _STANDARD_FALLBACKS.get(name)
        if std is not None:
            app = QApplication.instance()
            if app is not None:
                ic = app.style().standardIcon(std)
            else:
                ic = QIcon()
        else:
            ic = QIcon()
    if size:
        ic = QIcon(ic.pixmap(size, size))
    _icon_cache[key] = ic
    return ic


def fmt_size(n):
    try:
        n = int(n)
    except (TypeError, ValueError):
        return "?"
    if n < 0:
        return "?"
    for unit in ("B", "KB", "MB", "GB", "TB"):
        if n < 1024 or unit == "TB":
            return f"{n:.0f} {unit}" if unit == "B" else f"{n:.1f} {unit}"
        n /= 1024


def fmt_time(ms):
    try:
        return datetime.datetime.fromtimestamp(int(ms) / 1000).strftime("%H:%M:%S")
    except (TypeError, ValueError, OSError):
        return ""


# ------------------------------------------------------------------ base widgets

class Card(QFrame):
    """White rounded card (Material style) with a pill section header."""

    def __init__(self, title=None, parent=None):
        super().__init__(parent)
        self.setObjectName("card")
        outer = QVBoxLayout(self)
        outer.setContentsMargins(SP_LG, SP_MD, SP_LG, SP_LG)
        outer.setSpacing(SP_MD)
        if title:
            outer.addWidget(pill(title))
        self.body = QVBoxLayout()
        self.body.setSpacing(SP_SM)
        outer.addLayout(self.body)

    def add(self, widget):
        self.body.addWidget(widget)

    def add_layout(self, layout):
        self.body.addLayout(layout)


def pill(text=""):
    """A small teal section header on a tinted pill — matches the Android app.

    Built from a QFrame (objectName 'pill') wrapping a QLabel so the
    background + radius render reliably, unlike QSS on a bare QLabel.
    """
    frame = QFrame()
    frame.setObjectName("pill")
    lay = QHBoxLayout(frame)
    lay.setContentsMargins(12, 5, 12, 5)
    lay.setSpacing(0)
    lbl = QLabel(text)
    lbl.setObjectName("pill")
    lay.addWidget(lbl)
    return frame


class Page(QWidget):
    """Scrollable page with a title header — base for all main pages."""

    def __init__(self, bridge, title, subtitle=""):
        super().__init__()
        self.bridge = bridge
        self.outer = QVBoxLayout(self)
        self.outer.setContentsMargins(26, 22, 26, 22)
        self.outer.setSpacing(6)

        self.title = QLabel(title)
        self.title.setObjectName("h1")
        self.outer.addWidget(self.title)
        if subtitle:
            self.sub = QLabel(subtitle)
            self.sub.setObjectName("muted")
            self.sub.setWordWrap(True)
            self.outer.addWidget(self.sub)

        self.scroll = QScrollArea()
        self.scroll.setWidgetResizable(True)
        self.scroll.setFrameShape(QFrame.NoFrame)
        self.content = QWidget()
        self.v = QVBoxLayout(self.content)
        self.v.setContentsMargins(0, 10, 0, 10)
        self.v.setSpacing(16)
        self.scroll.setWidget(self.content)
        self.outer.addWidget(self.scroll, 1)

    def add_card(self, card):
        self.v.addWidget(card)


def row(*widgets, stretch=None):
    lay = QHBoxLayout()
    lay.setSpacing(8)
    for w in widgets:
        if w is not None:
            lay.addWidget(w)
    if stretch is not None:
        lay.addStretch(stretch)
    return lay


def label(text="", obj_name="muted"):
    lbl = QLabel(text)
    lbl.setObjectName(obj_name)
    lbl.setWordWrap(True)
    return lbl


def status_line(text="", value=""):
    lay = QHBoxLayout()
    lay.addWidget(label(text))
    val = label(value)
    val.setAlignment(Qt.AlignRight | Qt.AlignVCenter)
    lay.addWidget(val)
    return lay, val


def empty_state(text, icon_name="files", hint="", parent=None):
    """A polished empty-state block: icon, title, and optional hint."""
    w = QWidget(parent)
    lay = QVBoxLayout(w)
    lay.setAlignment(Qt.AlignCenter)
    lay.setSpacing(8)
    lay.setContentsMargins(16, 24, 16, 24)

    ic = QLabel()
    ic.setPixmap(icon(icon_name, 44).pixmap(44, 44))
    ic.setAlignment(Qt.AlignCenter)
    lay.addWidget(ic)

    title = QLabel(text)
    title.setObjectName("muted")
    title.setAlignment(Qt.AlignCenter)
    title.setWordWrap(True)
    lay.addWidget(title)

    if hint:
        h = QLabel(hint)
        h.setObjectName("muted")
        h.setAlignment(Qt.AlignCenter)
        h.setWordWrap(True)
        h.setStyleSheet("font-size: 11px; color: #9AA5AE;")
        lay.addWidget(h)

    w.setMinimumHeight(160)
    return w


class LineGraph(QWidget):
    """A lightweight line chart for history data (battery %, signal, etc.).

    Accepts a list of (x, y) floats; y values are auto-scaled with padding.
    """

    def __init__(self, parent=None, height=140, theme="dark"):
        super().__init__(parent)
        self.setMinimumHeight(height)
        self._points = []
        self._color = QColor("#26A69A")
        pal = PALETTES.get(theme, _DARK)
        self._grid = QColor(pal["border"])
        self._text = QColor(pal["text2"])

    def set_points(self, points, color=None):
        """points: iterable of (x, y)."""
        self._points = [(float(x), float(y)) for x, y in points]
        if color is not None:
            self._color = QColor(color)
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.Antialiasing)
        w, h = self.width(), self.height()
        if w <= 0 or h <= 0:
            return

        # padding
        left, right, top, bottom = 8, 8, 12, 22
        pw, ph = w - left - right, h - top - bottom
        if pw <= 0 or ph <= 0 or len(self._points) < 2:
            painter.setPen(self._text)
            painter.drawText(self.rect(), Qt.AlignCenter, "no data")
            return

        xs = [p[0] for p in self._points]
        ys = [p[1] for p in self._points]
        xmin, xmax = min(xs), max(xs)
        ymin, ymax = min(ys), max(ys)
        if xmax == xmin:
            xmax = xmin + 1
        if ymax == ymin:
            ymax = ymin + 1
        pad_y = (ymax - ymin) * 0.15

        # grid lines
        painter.setPen(self._grid)
        for i in range(5):
            gy = top + ph * i / 4
            painter.drawLine(left, int(gy), w - right, int(gy))
        # y labels
        painter.setPen(self._text)
        for i in range(5):
            gy = top + ph * i / 4
            val = ymax - pad_y - (ymax - ymin + 2 * pad_y) * i / 4
            painter.drawText(2, int(gy) - 3, f"{val:.0f}")

        def mapx(x):
            return left + (x - xmin) / (xmax - xmin) * pw

        def mapy(y):
            return top + ph - (y - (ymin - pad_y)) / (ymax - ymin + 2 * pad_y) * ph

        pen = QPen(self._color)
        pen.setWidth(2)
        pen.setCapStyle(Qt.RoundCap)
        painter.setPen(pen)
        prev = None
        for x, y in self._points:
            pt = QPointF(mapx(x), mapy(y))
            if prev is not None:
                painter.drawLine(prev, pt)
            painter.setBrush(self._color)
            painter.drawEllipse(pt, 2.5, 2.5)
            prev = pt

        painter.end()
