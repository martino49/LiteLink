"""Locate bundled/external tools (adb, scrcpy)."""

import os
import shutil
from pathlib import Path

BIN_DIR = Path(__file__).resolve().parent.parent.parent / "bin"
SCRCPY_DIR = BIN_DIR / "scrcpy-win64-v4.1"


def find_adb():
    """Return the path to adb.exe, or None."""
    candidates = [
        SCRCPY_DIR / "adb.exe",
        Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools" / "adb.exe",
        Path(os.environ.get("ANDROID_SDK_ROOT", "")) / "platform-tools" / "adb.exe",
        Path(r"C:\Users\Derivo\AppData\Local\Android\Sdk\platform-tools\adb.exe"),
    ]
    for c in candidates:
        if c and c.exists():
            return str(c)
    return shutil.which("adb")


def find_scrcpy():
    """Return the path to scrcpy.exe, or None."""
    exe = SCRCPY_DIR / "scrcpy.exe"
    if exe.exists():
        return str(exe)
    return shutil.which("scrcpy")
