"""ADB command wrapper.

Runs adb as a subprocess. All functions return structured results.
"""

import os
import re
import subprocess

from .tools import find_adb


def adb_binary():
    """Resolve the adb executable to use."""
    path = find_adb()
    return path if path else "adb"


def run_adb_command(command):
    """Execute an ADB command.

    Args:
        command: List of arguments (e.g., ["devices"])

    Returns:
        Tuple of (stdout, stderr, returncode)
    """
    full = [adb_binary()] + command
    kwargs = {}
    if os.name == "nt":
        kwargs["creationflags"] = subprocess.CREATE_NO_WINDOW
    try:
        r = subprocess.run(
            full, capture_output=True, text=True, timeout=10, **kwargs
        )
        return r.stdout.strip(), r.stderr.strip(), r.returncode
    except subprocess.TimeoutExpired:
        return "", "Timeout", -1
    except FileNotFoundError:
        return "", "ADB not found", -1


def setup_usb_reverse(port=8765):
    """Forward the phone's localhost:<port> to the desktop via USB.

    This powers the USB fallback feature: the companion app connects to
    wss://localhost:<port> over the USB cable when Wi-Fi is unavailable.

    Returns: list of (serial, ok).
    """
    out, err, code = run_adb_command(["devices"])
    results = []
    if code != 0:
        return results
    for line in out.splitlines():
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            serial = parts[0]
            _, _, rc = run_adb_command(
                ["-s", serial, "reverse", f"tcp:{port}", f"tcp:{port}"]
            )
            results.append((serial, rc == 0))
    return results


def get_device_state():
    """Check if a phone is connected via ADB.

    Returns: "connected", "disconnected", "offline", or "unauthorized"
    """
    out, err, code = run_adb_command(["devices"])
    if code != 0:
        return "disconnected"
    for line in out.splitlines():
        if "device" in line and "List" not in line:
            if "offline" in line:
                return "offline"
            if "unauthorized" in line:
                return "unauthorized"
            return "connected"
    return "disconnected"


def get_device_ip():
    """Get the phone's Wi-Fi IP address.

    Returns: IP string or None.
    """
    out, err, code = run_adb_command(["shell", "ip", "route"])
    if code == 0:
        m = re.search(r"wlan0\s+\S+\s+(\S+)", out)
        if m:
            return m.group(1)

    out, err, code = run_adb_command(["shell", "ifconfig", "wlan0"])
    if code == 0:
        m = re.search(r"inet addr:(\S+)", out)
        if m:
            return m.group(1)

    return None


def enable_wireless(port=5555):
    """Enable ADB over Wi-Fi. Requires USB connection first.

    Returns: True if successful.
    """
    out, err, code = run_adb_command(["tcpip", str(port)])
    return code == 0


def connect_wireless(ip, port=5555):
    """Connect to phone over Wi-Fi.

    Returns: True if connected.
    """
    out, err, code = run_adb_command(["connect", f"{ip}:{port}"])
    return code == 0 and "connected" in out.lower()


def shell(*parts):
    """Run `adb shell <parts>`; returns (stdout, ok)."""
    out, err, code = run_adb_command(["shell", *parts])
    return out, code == 0


def keyevent(key):
    """Send a keyevent to the (single) connected phone."""
    return shell("input", "keyevent", str(key))


def wm_size():
    """Return the phone display resolution as (width, height) or None."""
    out, ok = shell("wm", "size")
    if not ok:
        return None
    m = re.search(r"Physical size: (\d+)x(\d+)", out)
    return (int(m.group(1)), int(m.group(2))) if m else None


def screenshot_to(dest):
    """Capture a screenshot and save it to a local file path.

    Returns the destination path on success, or None.
    """
    import subprocess as _sp
    try:
        r = _sp.run(
            [adb_binary(), "exec-out", "screencap", "-p"],
            capture_output=True, timeout=15,
            creationflags=_sp.CREATE_NO_WINDOW,
        )
        if r.returncode == 0 and r.stdout:
            with open(dest, "wb") as f:
                f.write(r.stdout)
            return dest
    except Exception:
        pass
    return None


def record_screen(dest, seconds=10):
    """Record the screen to a local file via adb screenrecord.

    Returns the destination path on success, or None.
    """
    remote = "/sdcard/litelink_rec.mp4"
    shell("rm", "-f", remote)
    ok = shell("screenrecord", "--time-limit", str(seconds), remote)
    if not ok[1]:
        return None
    out, err, code = run_adb_command(["pull", remote, str(dest)])
    shell("rm", "-f", remote)
    return dest if code == 0 else None


def rotate_screen(landscape=False):
    """Set screen rotation. 1 = landscape, 0 = portrait."""
    return shell("settings", "put", "system", "user_rotation", "1" if landscape else "0")


def set_volume(percent=None, up=True):
    """Adjust volume with a keyevent (or set absolute level if percent given)."""
    if percent is not None:
        return shell("media", "volume", "--stream", "3", "--set", str(percent))
    return keyevent("KEYCODE_VOLUME_UP" if up else "KEYCODE_VOLUME_DOWN")


def package_state(pkg):
    """Return 'enabled'/'disabled'/'missing' for a package."""
    out, ok = shell("pm", "list", "packages", "-d")
    return "disabled" if ok and pkg in out else "enabled"


def disable_package(pkg):
    """Disable a package for the current user (works on many system apps)."""
    return shell("pm", "disable-user", "--user", "0", pkg)


def enable_package(pkg):
    """Re-enable a previously disabled package."""
    return shell("pm", "enable", pkg)


def uninstall_package(pkg):
    """Uninstall a package for the current user."""
    return shell("pm", "uninstall", "--user", "0", pkg)
