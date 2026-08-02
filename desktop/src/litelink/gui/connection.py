"""Connection page — server status, pairing code, phone connection, and
USB reverse / wireless setup. Added to the sidebar between Home and Detailed."""

import time

from PySide6.QtCore import QTimer, Qt
from PySide6.QtWidgets import QCheckBox, QLineEdit, QListWidget, QPushButton, QVBoxLayout

from .. import adb_utils, mdns, pairing
from ..server import DATA_DIR
from .common import ACCENT, Card, Page, icon, label, row
from .wizards import ReconnectWizard


class ConnectionPage(Page):
    """Connection & pairing hub."""

    def __init__(self, bridge, parent_window):
        super().__init__(bridge, "Connection",
                         "Server status, pairing code and phone connection.")
        self.window = parent_window

        # ---- Server card
        sc = Card("SERVER")
        self.srv_dot = label("●")
        self.srv_dot.setStyleSheet(f"color: #12B8B8; font-size: 18px;")
        self.srv_status = label("Starting…", "muted")
        lay = row(self.srv_dot, self.srv_status, None)
        lay.addStretch(1)
        sc.add_layout(lay)

        self.host_lbl = label("", "muted")
        sc.add(self.host_lbl)
        self.port_lbl = label("", "muted")
        sc.add(self.port_lbl)
        self.add_card(sc)

        # ---- Phone card
        pc = Card("PHONE")
        self.phone_dot = label("●")
        self.phone_dot.setStyleSheet("color: #667A84; font-size: 18px;")
        self.phone_status = label("Not connected", "muted")
        lay = row(self.phone_dot, self.phone_status, None)
        lay.addStretch(1)
        pc.add_layout(lay)
        self.phone_addr = label("", "muted")
        pc.add(self.phone_addr)
        self.add_card(pc)

        # ---- Pairing card
        pc2 = Card("PAIRING")
        self.code_lbl = label(pairing.get_pairing_code(DATA_DIR), "big")
        code_row = row(label("Pairing code"))
        code_row.addStretch(1)
        code_row.addWidget(self.code_lbl)
        pc2.add_layout(code_row)
        regen = QPushButton(icon("refresh"), "Regenerate pairing code")
        regen.setObjectName("ghost")
        regen.clicked.connect(self._regenerate)
        pc2.add(regen)

        from ..tls import certificate_fingerprint, format_fingerprint
        fp = format_fingerprint(certificate_fingerprint(DATA_DIR))
        pc2.add(label(f"Certificate fingerprint:\n{fp}", "muted"))
        self.add_card(pc2)

        # ---- Setup card
        sc2 = Card("SETUP")
        self.usb_status = label("", "muted")
        sc2.add(self.usb_status)

        usb_btn = QPushButton(icon("tether"), "Setup USB reverse tunnel")
        usb_btn.clicked.connect(self._setup_usb)
        sc2.add(usb_btn)

        wlan_btn = QPushButton(icon("signal"), "Enable wireless adb (tcpip 5555)")
        wlan_btn.clicked.connect(self._enable_wireless)
        sc2.add(wlan_btn)

        recon_btn = QPushButton(icon("refresh"), "Reconnect phone wizard")
        recon_btn.setObjectName("ghost")
        recon_btn.clicked.connect(self._reconnect_wizard)
        sc2.add(recon_btn)

        note = label(
            "The phone connects to wss://<this-PC-IP>:8765 with certificate "
            "fingerprint pinning. Keep TCP port 8765 open in Windows Firewall.",
            "muted")
        sc2.add(note)
        self.add_card(sc2)

        # ---- Discovery card
        dc = Card("DISCOVERED DEVICES")
        self.disc_status = label(
            "Scanning for other LiteLink devices via mDNS and UDP broadcast…", "muted")
        dc.add(self.disc_status)
        self.device_list = QListWidget()
        self.device_list.setMinimumHeight(140)
        dc.add(self.device_list)
        scan_btn = QPushButton(icon("find"), "Scan now")
        scan_btn.clicked.connect(self._scan_now)
        dc.add(scan_btn)
        self.add_card(dc)

        # ---- Live refresh
        self._timer = QTimer(self)
        self._timer.setInterval(3000)
        self._timer.timeout.connect(self.refresh)
        self._timer.start()
        self.refresh()

    # ------------------------------------------------------------------ slots
    def refresh(self):
        # server
        self.srv_status.setText("Running")
        self.host_lbl.setText(f"Bind host: 0.0.0.0")
        self.port_lbl.setText(f"Port: 8765  ·  LAN: {mdns.get_lan_ip()}  ·  "
                              f"Broadcast: {mdns.get_lan_broadcast()}")

        # phone
        ok = self.bridge.connected()
        addr = self.bridge.hub.connected_address()
        self.phone_dot.setStyleSheet(f"color: {ACCENT}; font-size: 18px;" if ok else "color: #667A84; font-size: 18px;")
        self.phone_status.setText("Connected" if ok else "Not connected")
        self.phone_addr.setText(f"Peer: {addr[0]}:{addr[1]}" if ok and addr else "")

        # adb availability hint
        state = adb_utils.get_device_state()
        if state == "connected" and not ok:
            self.usb_status.setText("USB device detected — Wi-Fi pairing not yet established.")
        elif state != "connected":
            self.usb_status.setText("No USB device detected. Plug the phone in to set up the tunnel.")

        # discovered devices
        self._render_devices()

    def _scan_now(self):
        self.device_list.clear()
        self.disc_status.setText("Scanning…")
        # force the beacon to re-announce; the next refresh will populate.
        if self.bridge.beacon:
            try:
                self.bridge.beacon._announce()
            except Exception:
                pass
        QTimer.singleShot(1500, self._render_devices)

    def _render_devices(self):
        devices = self.bridge.discovered_devices()
        self.device_list.clear()
        if not devices:
            self.disc_status.setText("No other LiteLink devices found on this network.")
            return
        now = time.time()
        entries = sorted(devices.values(), key=lambda d: d["last_seen"], reverse=True)
        self.disc_status.setText(f"Found {len(entries)} device(s).")
        for d in entries:
            age = int(now - d["last_seen"])
            self.device_list.addItem(
                f"{d['name']}  ·  {d['ip']}:{d['port']}  ·  via {d.get('via', '?')}  ·  {age}s ago")

    def _regenerate(self):
        new_code = pairing.regenerate_pairing_code(DATA_DIR)
        self.code_lbl.setText(new_code)
        self.bridge.log(f"New pairing code: {new_code}")
        if self.bridge.connected():
            from PySide6.QtWidgets import QMessageBox
            QMessageBox.information(
                self, "Pairing code changed",
                "The phone will need to reconnect with the new code.")

    def _setup_usb(self):
        self.usb_status.setText("Setting up USB reverse tunnel…")
        results = adb_utils.setup_usb_reverse()
        if not results:
            self.usb_status.setText("No phone detected over USB.")
            return
        lines = [f"USB reverse ({s}): {'ok' if ok else 'failed'}" for s, ok in results]
        self.usb_status.setText("\n".join(lines))
        for text in lines:
            self.bridge.log(text)

    def _enable_wireless(self):
        self.usb_status.setText("Enabling wireless adb on port 5555…")
        ok = adb_utils.enable_wireless(5555)
        ip = adb_utils.get_device_ip()
        if ok:
            self.usb_status.setText(f"Wireless adb enabled. Connect to {ip}:5555." if ip
                                    else "Wireless adb enabled (port 5555).")
        else:
            self.usb_status.setText("Could not enable wireless — is the phone plugged in?")

    def _reconnect_wizard(self):
        ReconnectWizard(self).exec()
