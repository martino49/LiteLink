"""Desktop-side input synthesis via Windows SendInput.

Receives phone-as-input messages from the companion app and turns them
into real mouse/keyboard input on the Windows desktop.
"""

import ctypes
import logging
from ctypes import wintypes

log = logging.getLogger("litelink.input")

user32 = ctypes.WinDLL("user32", use_last_error=True)

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_WHEEL = 0x0800

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

VK_ESCAPE = 0x1B
VK_TAB = 0x09
VK_LWIN = 0x5B
VK_LMENU = 0xA4


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [
        ("dx", wintypes.LONG),
        ("dy", wintypes.LONG),
        ("mouseData", wintypes.DWORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [
        ("wVk", wintypes.WORD),
        ("wScan", wintypes.WORD),
        ("dwFlags", wintypes.DWORD),
        ("time", wintypes.DWORD),
        ("dwExtraInfo", ctypes.POINTER(ctypes.c_ulong)),
    ]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [
        ("uMsg", wintypes.DWORD),
        ("wParamL", wintypes.WORD),
        ("wParamH", wintypes.WORD),
    ]


class _INPUTUNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", wintypes.DWORD), ("union", _INPUTUNION)]


def _mouse(flags, data=0):
    inp = INPUT()
    inp.type = INPUT_MOUSE
    inp.union.mi.dwFlags = flags
    inp.union.mi.mouseData = data
    return inp


def _key(vk=0, scan=0, flags=0):
    inp = INPUT()
    inp.type = INPUT_KEYBOARD
    inp.union.ki.wVk = vk
    inp.union.ki.wScan = scan
    inp.union.ki.dwFlags = flags
    return inp


def _send(*inputs):
    n = len(inputs)
    if n == 0:
        return
    array = (INPUT * n)(*inputs)
    sent = user32.SendInput(n, array, ctypes.sizeof(INPUT))
    if sent != n:
        log.warning("SendInput sent %s/%s (error %s)", sent, n, ctypes.get_last_error())


def _screen_size():
    return user32.GetSystemMetrics(0), user32.GetSystemMetrics(1)


def pointer(action, x, y):
    """Normalized (0..1) pointer position; action in down/move/up."""
    w, h = _screen_size()
    px = int(max(0.0, min(1.0, x)) * w)
    py = int(max(0.0, min(1.0, y)) * h)
    user32.SetCursorPos(px, py)
    if action == "down":
        _send(_mouse(MOUSEEVENTF_LEFTDOWN))
    elif action == "up":
        _send(_mouse(MOUSEEVENTF_LEFTUP))


def scroll(dx, dy):
    """Mouse wheel. dy is a normalized delta; sign flips the direction."""
    lines = max(-120, min(120, int(-dy * 120)))
    if lines != 0:
        _send(_mouse(MOUSEEVENTF_WHEEL, ctypes.c_uint32(lines).value))


def _tap_key(vk):
    _send(_key(vk=vk))
    _send(_key(vk=vk, flags=KEYEVENTF_KEYUP))


def key(name):
    """Named phone buttons mapped to desktop keys."""
    if name == "back":
        _tap_key(VK_ESCAPE)
    elif name == "home":
        _tap_key(VK_LWIN)
    elif name == "recents":
        _send(_key(vk=VK_LMENU))
        _send(_key(vk=VK_TAB))
        _send(_key(vk=VK_TAB, flags=KEYEVENTF_KEYUP))
        _send(_key(vk=VK_LMENU, flags=KEYEVENTF_KEYUP))


def type_text(text):
    """Insert text via UNICODE virtual-key events."""
    for ch in str(text):
        code = ord(ch)
        _send(_key(scan=code, flags=KEYEVENTF_UNICODE))
        _send(_key(scan=code, flags=KEYEVENTF_UNICODE | KEYEVENTF_KEYUP))


def handle(message):
    """Dispatch an {'type':'input', 'kind':..., 'params':...} message."""
    kind = message.get("kind")
    params = message.get("params") or {}
    try:
        if kind == "pointer":
            pointer(params.get("action", "move"),
                    float(params.get("x", 0.5)),
                    float(params.get("y", 0.5)))
        elif kind == "scroll":
            scroll(float(params.get("x", 0.0)),
                   float(params.get("y", 0.0)))
        elif kind == "key":
            key(params.get("key", ""))
        elif kind == "text":
            type_text(params.get("text", ""))
    except Exception as e:
        log.warning("input handle failed: %s", e)
