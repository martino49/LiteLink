"""ADB command wrapper.

Runs adb as a subprocess. All functions return structured results.
"""

import subprocess
import re


def run_adb_command(command):
    """Execute an ADB command.

    Args:
        command: List of arguments (e.g., ["devices"])

    Returns:
        Tuple of (stdout, stderr, returncode)
    """
    full = ["adb"] + command
    try:
        r = subprocess.run(
            full, capture_output=True, text=True, timeout=10
        )
        return r.stdout.strip(), r.stderr.strip(), r.returncode
    except subprocess.TimeoutExpired:
        return "", "Timeout", -1
    except FileNotFoundError:
        return "", "ADB not found", -1


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
