"""mDNS advertisement (zeroconf) so the companion app can find the desktop.

Also provides:
- Scanner: browse the LAN for other LiteLink services (peers / phones that
  publish the same service type) and report them to the GUI.
- BroadcastBeacon: a lightweight UDP broadcast announce/probe used as a
  fallback when multicast mDNS is blocked by the network.
"""

import json
import socket
import threading
import time

from zeroconf import ServiceBrowser, ServiceInfo, ServiceListener, Zeroconf

SERVICE_TYPE = "_litelink._tcp.local."
BEACON_PORT = 8770
BEACON_INTERVAL = 5.0


def get_lan_ip():
    """Return the primary LAN IPv4 address (best effort)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


def get_lan_broadcast():
    """Return the subnet broadcast address (e.g. 192.168.1.255)."""
    ip = get_lan_ip()
    parts = ip.split(".")
    if len(parts) == 4:
        return ".".join(parts[:3]) + ".255"
    return "255.255.255.255"


class Advertiser:
    """Publishes the LiteLink WebSocket server over mDNS."""

    def __init__(self, port=8765, name=None):
        self.port = port
        self.name = name or f"LiteLink-{socket.gethostname()}"
        self.zeroconf = None

    def start(self):
        try:
            ip = get_lan_ip()
            info = ServiceInfo(
                SERVICE_TYPE,
                f"{self.name}.{SERVICE_TYPE}",
                addresses=[socket.inet_aton(ip)],
                port=self.port,
                properties={"app": "LiteLink"},
            )
            self.zeroconf = Zeroconf()
            self.zeroconf.register_service(info)
        except Exception as e:
            print(f"[mDNS] advertise failed: {e}")
            self.zeroconf = None

    def stop(self):
        if self.zeroconf is not None:
            try:
                self.zeroconf.close()
            except Exception:
                pass
            self.zeroconf = None


class _LiteLinkListener(ServiceListener):
    """Collects discovered LiteLink services into a shared dict."""

    def __init__(self, devices, lock):
        self.devices = devices
        self.lock = lock

    def add_service(self, zc, type_, name):
        self._upsert(zc, name)

    def update_service(self, zc, type_, name):
        self._upsert(zc, name)

    def remove_service(self, zc, type_, name):
        with self.lock:
            self.devices.pop(name, None)

    def _upsert(self, zc, name):
        try:
            info = zc.get_service_info(SERVICE_TYPE, name)
            if info is None:
                return
            ip = socket.inet_ntoa(info.addresses[0]) if info.addresses else "?"
            with self.lock:
                self.devices[name] = {
                    "name": name.replace(f".{SERVICE_TYPE}", ""),
                    "ip": ip,
                    "port": info.port,
                    "host": info.server,
                    "via": "mDNS",
                    "last_seen": time.time(),
                }
        except Exception:
            pass


class Scanner:
    """Browses the LAN for other LiteLink services (mDNS)."""

    def __init__(self):
        self.zeroconf = None
        self.browser = None
        self.devices = {}
        self._lock = threading.Lock()

    def start(self):
        try:
            self.zeroconf = Zeroconf()
            self.browser = ServiceBrowser(self.zeroconf, SERVICE_TYPE,
                                          _LiteLinkListener(self.devices, self._lock))
        except Exception as e:
            print(f"[mDNS] browse failed: {e}")
            self.zeroconf = None

    def stop(self):
        if self.zeroconf is not None:
            try:
                self.zeroconf.close()
            except Exception:
                pass
            self.zeroconf = None

    def snapshot(self):
        with self._lock:
            return dict(self.devices)


class BroadcastBeacon:
    """UDP broadcast announce + probe (DIY fallback when mDNS is blocked).

    Every interval it announces this host on the LAN broadcast address and
    listens for replies from other LiteLink hosts. Works where multicast is
    filtered but plain UDP broadcast is allowed.
    """

    def __init__(self, port=BEACON_PORT, service_port=8765, name=None, data_dir=None):
        self.port = port
        self.service_port = service_port
        self.name = name or f"LiteLink-{socket.gethostname()}"
        self.data_dir = data_dir
        self.devices = {}
        self._lock = threading.Lock()
        self._thread = None
        self._running = False

    def _announce(self):
        from . import pairing
        code = ""
        dev_id = ""
        if self.data_dir is not None:
            try:
                code = pairing.get_pairing_code(self.data_dir)
                dev_id = pairing.get_device_id(self.data_dir)
            except Exception:
                code = ""
                dev_id = ""
        payload = json.dumps({
            "litelink": True,
            "v": 1,
            "name": self.name,
            "id": dev_id,
            "ip": get_lan_ip(),
            "port": self.service_port,
            "code": code,
        }).encode("utf-8")
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        try:
            sock.sendto(payload, (get_lan_broadcast(), self.port))
        except Exception:
            pass
        finally:
            sock.close()

    def _listen(self):
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("", self.port))
        except OSError as e:
            print(f"[beacon] bind failed: {e}")
            return
        s.settimeout(1.0)
        while self._running:
            try:
                data, addr = s.recvfrom(4096)
            except socket.timeout:
                continue
            except OSError:
                break
            try:
                msg = json.loads(data.decode("utf-8"))
            except Exception:
                continue
            if not msg.get("litelink"):
                continue
            name = msg.get("name", addr[0])
            with self._lock:
                self.devices[name] = {
                    "name": name,
                    "ip": msg.get("ip") or addr[0],
                    "port": msg.get("port", self.service_port),
                    "host": addr[0],
                    "via": "broadcast",
                    "last_seen": time.time(),
                }
        s.close()

    def start(self):
        if self._running:
            return
        self._running = True
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def _run(self):
        self._announce()
        listener = threading.Thread(target=self._listen, daemon=True)
        listener.start()
        while self._running:
            time.sleep(BEACON_INTERVAL)
            self._announce()

    def stop(self):
        self._running = False
        if self._thread is not None:
            self._thread.join(timeout=2)

    def snapshot(self):
        with self._lock:
            return dict(self.devices)
