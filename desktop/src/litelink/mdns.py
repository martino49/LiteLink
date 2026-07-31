"""mDNS advertisement (zeroconf) so the companion app can find the desktop."""

import socket

from zeroconf import ServiceInfo, Zeroconf

SERVICE_TYPE = "_litelink._tcp.local."


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
