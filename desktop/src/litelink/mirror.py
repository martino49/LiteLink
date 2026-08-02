"""scrcpy screen-mirroring launcher."""

import os
import re
import subprocess
import threading
import time

from .tools import find_adb, find_scrcpy

_ACTIVE = {"proc": None}


def is_available():
    return find_scrcpy() is not None


def is_running():
    """True if a scrcpy process is currently active."""
    proc = _ACTIVE.get("proc")
    return proc is not None and proc.poll() is None


def _adb_cmd(adb, serial, *parts):
    cmd = [adb]
    if serial:
        cmd += ["-s", serial]
    cmd += list(parts)
    return cmd


def _screen_state(adb, serial=None):
    """Return 'ON'/'OFF' for the main display, or None if unknown."""
    try:
        out = subprocess.check_output(
            _adb_cmd(adb, serial, "shell", "dumpsys", "display"),
            stderr=subprocess.DEVNULL,
            timeout=8,
            creationflags=subprocess.CREATE_NO_WINDOW,
        ).decode("utf-8", "ignore")
    except Exception:
        return None
    m = re.search(r"mScreenState=(ON|OFF)", out)
    return m.group(1) if m else None


def _send_keyevent(adb, serial, key):
    subprocess.Popen(
        _adb_cmd(adb, serial, "shell", "input", "keyevent", key),
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NO_WINDOW,
    )


def _enforce_screen_off(proc, serial, adb, delay, attempts, interval=5.0):
    """MIUI ignores scrcpy's in-band turn-screen-off; force it via adb.

    Polls for a while: some MIUI builds re-wake the screen shortly after
    it sleeps (foreground apps, gestures), so keep enforcing for the
    window given by attempts * interval.
    """
    if not adb:
        return
    time.sleep(delay)
    for _ in range(attempts):
        if proc.poll() is not None:
            return
        if _screen_state(adb, serial) != "OFF":
            _send_keyevent(adb, serial, "KEYCODE_POWER")
        time.sleep(interval)


def launch(serial=None, turn_screen_off=False, screen_off_delay=6.0, screen_off_attempts=6):
    """Start scrcpy mirroring in its own window.

    Returns a bool (started?) and an error string.
    """
    exe = find_scrcpy()
    if not exe:
        return False, "scrcpy not found in desktop/bin"
    if _ACTIVE["proc"] is not None and _ACTIVE["proc"].poll() is None:
        return True, None

    adb = find_adb()
    cmd = [exe, "--always-on-top"]
    if turn_screen_off:
        # --stay-awake sets stay_on_while_plugged_in and fights screen-off.
        cmd.append("--turn-screen-off")
        cmd.append("--power-off-on-close")
    else:
        cmd.append("--stay-awake")
    if serial:
        cmd += ["-s", serial]

    env = dict(os.environ)
    if adb:
        env["ADB"] = adb

    try:
        proc = subprocess.Popen(
            cmd,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            creationflags=subprocess.CREATE_NO_WINDOW,
            env=env,
        )
        _ACTIVE["proc"] = proc
        threading.Thread(target=_watch, args=(proc,), daemon=True).start()
        if turn_screen_off:
            threading.Thread(
                target=_enforce_screen_off,
                args=(proc, serial, adb, screen_off_delay, screen_off_attempts),
                daemon=True,
            ).start()
        return True, None
    except OSError as e:
        return False, str(e)


def stop():
    """Stop a running scrcpy instance."""
    proc = _ACTIVE.get("proc")
    if proc is not None and proc.poll() is None:
        proc.terminate()


def _watch(proc):
    proc.wait()
    if _ACTIVE.get("proc") is proc:
        _ACTIVE["proc"] = None
