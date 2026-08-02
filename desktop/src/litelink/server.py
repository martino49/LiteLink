"""WebSocket hub server — the heart of LiteLink.

Runs over WSS (self-signed TLS) on the LAN. Every connection must first
present the pairing code. After that the Hub speaks the full LiteLink
protocol in both directions:

  phone -> desktop:
    {"type":"pair","code":...}        handshake (before anything else)
    {"type":"cmd_result","id":..,"ok":..,"data":{..}}
    {"type":"event","event":..,"data":{..}}
    {"type":"notification","app":..,"title":..,"text":..,"timestamp":..}
    {"type":"file","mode":"start|chunk|done", ...}   file uploads
    {"type":"input","kind":..,"params":{..}}         phone-as-input

  desktop -> phone:
    {"type":"cmd","cmd":..,"id":..,"params":{..}}
    {"type":"file","mode":"start|chunk|done", ...}   file pushes

The Hub is thread-safe: GUI code may call send_command()/push_file()
from any thread; the actual writes are scheduled onto the asyncio loop.
"""

import asyncio
import base64
import itertools
import json
import os
import sys
from pathlib import Path

import websockets
from websockets.exceptions import ConnectionClosed

from . import pairing
from .tls import build_ssl_context, certificate_fingerprint, format_fingerprint

CONNECTED_PHONES = set()

PAIR_TIMEOUT = 15
CHUNK = 60 * 1024


def _default_data_dir() -> Path:
    """data dir: repo/desktop/data when running from source, APPDATA when frozen."""
    if getattr(sys, "frozen", False):
        base = Path(os.environ.get("APPDATA", str(Path.home())))
        return base / "LiteLink"
    return Path(__file__).resolve().parent.parent.parent / "data"


DATA_DIR = _default_data_dir()


def _wireless_adb(ip, port):
    """Ensure the phone is reachable over wireless adb on the given port."""
    import time
    from . import adb_utils
    # If the phone isn't already connected over adb, try tcpip + connect.
    for attempt in range(2):
        out, err, code = adb_utils.run_adb_command(["devices"])
        if f"{ip}:{port}" in out:
            return
        time.sleep(0.5)
    adb_utils.run_adb_command(["tcpip", str(port)])
    time.sleep(1.0)
    adb_utils.run_adb_command(["connect", f"{ip}:{port}"])


class Hub:
    """Owns phone sockets, routes messages, and bridges the GUI to the phone."""

    def __init__(self, data_dir=None):
        self.data_dir = Path(data_dir) if data_dir else DATA_DIR
        self.loop = None
        self._ws = None
        self._ids = itertools.count(1)
        self._pending = {}            # id -> callable(ok, data)
        self._receive = {}            # file id -> incoming sink
        self.connected = False
        self.phone_info = {}

        # callbacks, all invoked from the asyncio thread:
        self.on_log = None            # callable(str)
        self.on_phone = None          # callable(bool)
        self.on_message = None        # callable(dict) every parsed message
        self.on_command_result = None # callable(id, ok, data)
        self.on_event = None          # callable(event, data)
        self.on_notification = None   # callable(dict)
        self.on_file_received = None  # callable(name, dir, size, ok)

    # ------------------------------------------------------------------ logging
    def _log(self, text):
        if self.on_log:
            self.on_log(text)
        else:
            print(text)

    # ------------------------------------------------------------ thread safety
    def _schedule(self, coro):
        if self.loop is not None:
            return asyncio.run_coroutine_threadsafe(coro, self.loop)
        return None

    def send_command(self, cmd, params=None, callback=None):
        """Send a command to the phone. Returns the request id.

        If callback is given, it runs on the GUI/current thread when the
        cmd_result arrives (ok: bool, data: dict).
        """
        rid = f"d{next(self._ids)}"
        if callback:
            self._pending[rid] = callback
        payload = {"type": "cmd", "cmd": cmd, "id": rid, "params": params or {}}
        self._schedule(self._send(payload))
        return rid

    def push_file(self, path, dir=""):
        """Upload a local file to the phone (dir 'install' installs the APK)."""
        path = Path(path)
        self._schedule(self._push_file(path, dir))

    def connected_address(self):
        """Return the paired phone's (host, port) or None."""
        ws = self._ws
        if ws is None:
            return None
        try:
            return ws.remote_address
        except Exception:
            return None

    def send_pc_notification(self, app, title, text):
        """Push a PC notification event to the phone (feature 72)."""
        payload = {
            "type": "pc_notification",
            "app": app,
            "title": title,
            "text": text,
            "timestamp": int(__import__("time").time() * 1000),
        }
        self._schedule(self._send(payload))

    async def _send(self, payload):
        ws = self._ws
        if ws is None:
            self._log("No phone connected")
            return
        try:
            await ws.send(json.dumps(payload))
        except ConnectionClosed:
            pass
        except Exception as e:
            self._log(f"Send failed: {e}")

    async def _push_file(self, path, dir=""):
        ws = self._ws
        if ws is None:
            self._log("No phone connected")
            return
        if not path.is_file():
            self._log(f"push failed: {path} is not a file")
            return
        size = path.stat().st_size
        fid = f"p{next(self._ids)}"
        await self._send({"type": "file", "mode": "start", "id": fid,
                          "name": path.name, "size": size, "dir": dir})
        offset = 0
        with path.open("rb") as f:
            while True:
                data = f.read(CHUNK)
                if not data:
                    break
                await self._send({"type": "file", "mode": "chunk", "id": fid,
                                  "offset": offset,
                                  "b64": base64.b64encode(data).decode("ascii")})
                offset += len(data)
        await self._send({"type": "file", "mode": "done", "id": fid, "ok": True})
        self._log(f"Sent {path.name} ({size:,} bytes) to phone [{dir}]")

    # ------------------------------------------------------------ connection
    async def handle(self, websocket):
        addr = websocket.remote_address
        try:
            await asyncio.wait_for(_require_pairing(websocket, self.data_dir), timeout=PAIR_TIMEOUT)
        except (asyncio.TimeoutError, ConnectionClosed):
            self._log(f"[Rejected] {addr}: no valid pairing")
            return
        self._log(f"[Phone connected] {addr}")
        CONNECTED_PHONES.add(websocket)
        self._ws = websocket
        self.connected = True
        if self.on_phone:
            self.on_phone(True)
        try:
            async for raw in websocket:
                try:
                    data = json.loads(raw)
                except json.JSONDecodeError:
                    self._log(f"[Invalid JSON] from {addr}: {raw[:200]}")
                    continue
                self._dispatch(data)
        except ConnectionClosed:
            self._log(f"[Phone disconnected] {addr}")
        except Exception as e:
            self._log(f"[Connection error] {e}")
        finally:
            CONNECTED_PHONES.discard(websocket)
            if self._ws is websocket:
                self._ws = None
            self.connected = False
            if self.on_phone:
                self.on_phone(False)

    def _dispatch(self, data):
        if self.on_message:
            self.on_message(data)
        msg_type = data.get("type")

        if msg_type == "cmd_result":
            rid = data.get("id")
            ok = bool(data.get("ok", False))
            result = data.get("data", {})
            cb = self._pending.pop(rid, None)
            if cb:
                cb(ok, result)
            elif self.on_command_result:
                self.on_command_result(rid, ok, result)

        elif msg_type == "event":
            ev = data.get("event")
            evd = data.get("data", {})
            if self.on_event:
                self.on_event(ev, evd)

        elif msg_type == "notification":
            if self.on_notification:
                self.on_notification(data)

        elif msg_type == "device_info":
            # phone details pushed on connect; store + emit as an event
            self.phone_info = data
            self._log(f"[device] {data.get('model', '?')} · Android {data.get('android', '?')}")
            if self.on_event:
                self.on_event("device_info", data)
            # ensure wireless adb uses the phone's port (force 5555 if needed)
            try:
                from . import adb_utils
                port = int(data.get("adb_port") or 5555)
                ip = data.get("ip") or ""
                if ip and port:
                    threading.Thread(target=_wireless_adb, args=(ip, port), daemon=True).start()
            except Exception:
                pass

        elif msg_type == "file":
            self._handle_file(data)

        elif msg_type == "input":
            # phone-as-input, handled by the GUI bridge (input_controller)
            pass

        elif msg_type == "pc":
            # phone->PC remote control (media/power/files/mirror/ring)
            from . import pc_controller
            pc_controller.handle(data)

        else:
            self._log(f"[{msg_type}]")

    def _handle_file(self, data):
        mode = data.get("mode")
        fid = data.get("id")
        if mode == "start":
            name = str(data.get("name") or "unknown")
            size = int(data.get("size") or 0)
            sub = str(data.get("dir") or "")
            base = self.data_dir / "received"
            base = base if not sub else base / sub
            parts = [p for p in name.replace("\\", "/").split("/")
                     if p not in ("", ".", "..")]
            if parts:
                target = base.joinpath(*parts[:-1]) if len(parts) > 1 else base
                target.mkdir(parents=True, exist_ok=True)
                fpath = target / parts[-1]
            else:
                base.mkdir(parents=True, exist_ok=True)
                fpath = base / "unknown"
            try:
                fh = fpath.open("wb")
            except OSError as e:
                self._log(f"[file] cannot open {fpath}: {e}")
                return
            self._receive[fid] = {"file": fh, "size": size, "got": 0,
                                  "name": name, "dir": sub}
            self._log(f"[file] receiving {name} ({size:,} bytes) -> {fpath}")
        elif mode == "chunk":
            entry = self._receive.get(fid)
            if entry:
                try:
                    raw = base64.b64decode(data.get("b64", ""))
                except Exception:
                    raw = b""
                entry["file"].write(raw)
                entry["got"] += len(raw)
        elif mode == "done":
            entry = self._receive.pop(fid, None)
            if entry:
                try:
                    entry["file"].close()
                except Exception:
                    pass
                ok = entry["got"] >= entry["size"]
                self._log(f"[file] done {entry['name']} ok={ok} "
                          f"({entry['got']:,}/{entry['size']:,} bytes)")
                if self.on_file_received:
                    self.on_file_received(entry["name"], entry["dir"], entry["got"], ok)

    # ---------------------------------------------------------------- server
    async def serve(self, host="0.0.0.0", port=8765):
        self.loop = asyncio.get_running_loop()
        from . import pc_controller, screen_stream
        pc_controller.set_hub(self)
        screen_stream.set_hub(self)
        ssl_context = build_ssl_context(self.data_dir)
        fp = certificate_fingerprint(self.data_dir)
        code = pairing.get_pairing_code(self.data_dir)
        self._log(f"LiteLink server on wss://{host}:{port}")
        self._log(f"Certificate SHA-256 fingerprint: {format_fingerprint(fp)}")
        self._log(f"Pairing code: {code}")
        async with websockets.serve(self.handle, host, port,
                                    ssl=ssl_context, ping_interval=30):
            await asyncio.Future()


async def _require_pairing(websocket, data_dir) -> None:
    """First message must be {'type': 'pair', 'code': <6-digit code>}."""
    raw = await websocket.recv()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        raise ConnectionClosed(None, "bad pairing message")
    if data.get("type") == "pair" and pairing.verify(data_dir, str(data.get("code", ""))):
        await websocket.send(json.dumps({"type": "pair_ok"}))
        return
    raise ConnectionClosed(None, "invalid pairing code")


async def start_server(
    host="0.0.0.0",
    port=8765,
    on_phone_connected=None,
    on_message=None,
    log=None,
):
    """Console-mode server for `python -m litelink` / `python run.py` fallback."""
    hub = Hub()
    hub.on_log = log
    hub.on_phone = on_phone_connected
    hub.on_message = on_message
    await hub.serve(host, port)
