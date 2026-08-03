"""Desktop-side input synthesis via Windows SendInput.

Receives phone-as-input messages from the companion app and turns them
into real mouse/keyboard input on the Windows desktop.
"""

import ctypes
import logging
import sys

log = logging.getLogger("litelink.input")

IS_WINDOWS = sys.platform == "win32"

if IS_WINDOWS:
    from ctypes import wintypes
    user32 = ctypes.WinDLL("user32", use_last_error=True)
else:
    wintypes = None
    user32 = None

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_ABSOLUTE = 0x8000
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x01000

KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004

VK_ESCAPE = 0x1B
VK_TAB = 0x09
VK_LWIN = 0x5B
VK_LMENU = 0xA4
VK_LCONTROL = 0xA2
VK_LSHIFT = 0xA0
VK_RETURN = 0x0D
VK_BACK = 0x08
VK_SPACE = 0x20
VK_UP = 0x26
VK_DOWN = 0x28
VK_LEFT = 0x25
VK_RIGHT = 0x27
VK_HOME = 0x24
VK_END = 0x23
VK_DELETE = 0x2E

_SPECIAL_KEYS = {
    "enter": VK_RETURN, "return": VK_RETURN,
    "esc": VK_ESCAPE, "escape": VK_ESCAPE,
    "tab": VK_TAB,
    "backspace": VK_BACK, "back": VK_BACK,
    "space": VK_SPACE,
    "up": VK_UP, "down": VK_DOWN, "left": VK_LEFT, "right": VK_RIGHT,
    "home": VK_HOME, "end": VK_END,
    "delete": VK_DELETE, "del": VK_DELETE,
    "win": VK_LWIN, "windows": VK_LWIN,
    "ctrl": VK_LCONTROL, "shift": VK_LSHIFT, "alt": VK_LMENU,
    # F1-F12
    **{f"f{i}": 0x70 + (i - 1) for i in range(1, 13)},
}


if not IS_WINDOWS:

    class _NoWinAPI:
        """Placeholder for unsupported platform.

        All input synthesis functions become safe no-ops on non-Windows
        systems so the rest of LiteLink can still import and run.
        """

        def __init__(self, *a, **k):
            pass

        def __call__(self, *a, **k):
            return 0

    MOUSEINPUT = _NoWinAPI
    KEYBDINPUT = _NoWinAPI
    HARDWAREINPUT = _NoWinAPI
    _INPUTUNION = _NoWinAPI
    INPUT = _NoWinAPI

    def _mouse(*a, **k):
        return None

    def _key(*a, **k):
        return None

    def _send(*inputs):
        log.debug("input_controller disabled on this platform")

    def _screen_size():
        return 1920, 1080

    def pointer(action, x, y):
        log.debug("pointer ignored: not Windows")

    def pointer_rel(dx, dy, sensitivity=1.0):
        pass

    def _tap_key(vk):
        log.debug("key tap ignored: not Windows")

    def scroll(dx, dy):
        pass

    def key(name):
        pass

    def type_text(text):
        pass

    def handle(data):
        log.debug("handle() ignored: not Windows")

    def gesture(gtype, dx, dy):
        pass

else:

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
    
    
    def pointer_rel(dx, dy, sensitivity=1.0):
        """Relative pointer movement (gentle, like a real trackpad).
    
        dx/dy are normalized deltas (-1..1); they're scaled to a smooth
        relative mouse move instead of an absolute jump.
        """
        # send a relative move: smooth scaling avoids the "jumps to a point" feel
        scale = 12.0 * sensitivity
        mdx = int(max(-50, min(50, dx * scale)))
        mdy = int(max(-50, min(50, dy * scale)))
        if mdx == 0 and mdy == 0:
            return
        _send(_mouse(MOUSEEVENTF_MOVE, 0))
        # move relative
        inp = INPUT()
        inp.type = INPUT_MOUSE
        inp.union.mi.dx = mdx
        inp.union.mi.dy = mdy
        inp.union.mi.dwFlags = MOUSEEVENTF_MOVE
        _send(inp)
    
    
    def scroll(dx, dy):
        """Mouse wheel. dy is a normalized delta; sign flips the direction."""
        lines = max(-120, min(120, int(-dy * 120)))
        if lines != 0:
            _send(_mouse(MOUSEEVENTF_WHEEL, ctypes.c_uint32(lines).value))
    
    
    def _tap_key(vk):
        _send(_key(vk=vk))
        _send(_key(vk=vk, flags=KEYEVENTF_KEYUP))
    
    
    def key(name):
        """Named phone buttons / special keys mapped to desktop keys.
    
        Supports: back, home, recents, special keys (enter/tab/f1..f12/arrows),
        and combos like "ctrl+c" / "ctrl+shift+esc".
        """
        name = (name or "").strip().lower()
        if not name:
            return
    
        # combo: "ctrl+c"
        if "+" in name:
            parts = name.split("+")
            modifiers = {"ctrl": VK_LCONTROL, "shift": VK_LSHIFT, "alt": VK_LMENU, "win": VK_LWIN}
            downs = []
            for p in parts:
                p = p.strip()
                if p in modifiers:
                    downs.append(modifiers[p])
            target = parts[-1].strip()
            if target not in modifiers:
                vk = _SPECIAL_KEYS.get(target)
                if vk is not None:
                    for m in downs:
                        _send(_key(vk=m))
                    _send(_key(vk=vk))
                    _send(_key(vk=vk, flags=KEYEVENTF_KEYUP))
                    for m in reversed(downs):
                        _send(_key(vk=m, flags=KEYEVENTF_KEYUP))
                    return
            # ctrl/shift/alt/win alone
            for m in downs:
                _send(_key(vk=m))
                _send(_key(vk=m, flags=KEYEVENTF_KEYUP))
            return
    
        if name == "back":
            _tap_key(VK_ESCAPE)
        elif name == "home":
            _tap_key(VK_LWIN)
        elif name == "recents":
            _send(_key(vk=VK_LMENU))
            _send(_key(vk=VK_TAB))
            _send(_key(vk=VK_TAB, flags=KEYEVENTF_KEYUP))
            _send(_key(vk=VK_LMENU, flags=KEYEVENTF_KEYUP))
        elif name in _SPECIAL_KEYS:
            _tap_key(_SPECIAL_KEYS[name])
    
    
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
            elif kind == "pointer_rel":
                pointer_rel(float(params.get("dx", 0.0)),
                            float(params.get("dy", 0.0)),
                            float(params.get("sensitivity", 1.0)))
            elif kind == "scroll":
                scroll(float(params.get("x", 0.0)),
                       float(params.get("y", 0.0)))
            elif kind == "key":
                key(params.get("key", ""))
            elif kind == "text":
                type_text(params.get("text", ""))
            elif kind == "gesture":
                gesture(params.get("type", ""),
                        float(params.get("dx", 0.0)),
                        float(params.get("dy", 0.0)))
        except Exception as e:
            log.warning("input handle failed: %s", e)
    
    
    def gesture(gtype, dx, dy):
        """Trackpad gestures (features 61-65)."""
        if gtype == "right_click":
            _send(_mouse(MOUSEEVENTF_RIGHTDOWN))
            _send(_mouse(MOUSEEVENTF_RIGHTUP))
        elif gtype == "two_finger_scroll":
            # vertical scroll scaled by the drag delta
            scroll(dx, dy * 2.0)
        elif gtype == "three_finger_swipe":
            _send(_key(vk=VK_LMENU))
            _send(_key(vk=VK_TAB))
            _send(_key(vk=VK_TAB, flags=KEYEVENTF_KEYUP))
            _send(_key(vk=VK_LMENU, flags=KEYEVENTF_KEYUP))
        elif gtype == "pinch":
            # pinch -> Ctrl+wheel to zoom
            lines = max(-120, min(120, int(-dy * 120)))
            if lines != 0:
                _send(_key(vk=VK_LCONTROL))
                _send(_mouse(MOUSEEVENTF_WHEEL, ctypes.c_uint32(lines).value))
                _send(_key(vk=VK_LCONTROL, flags=KEYEVENTF_KEYUP))
        elif gtype == "middle_click":
            _send(_mouse(MOUSEEVENTF_MIDDLEDOWN))
            _send(_mouse(MOUSEEVENTF_MIDDLEUP))
