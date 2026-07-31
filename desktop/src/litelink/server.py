"""WebSocket server — the central hub of LiteLink.

Runs over WSS (self-signed TLS) on the LAN. Every connection must first
present the pairing code before any other message is accepted.
All JSON messages (notifications, file changes, commands) pass through here.
"""

import asyncio
import json
from pathlib import Path

import websockets

from . import pairing
from .tls import build_ssl_context, certificate_fingerprint, format_fingerprint

CONNECTED_PHONES = set()

# Desktop data dir: <repo>/desktop/data
DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data"

PAIR_TIMEOUT = 15


async def start_server(
    host="0.0.0.0",
    port=8765,
    on_phone_connected=None,
    on_message=None,
    log=None,
):
    """Start the WSS server and run forever.

    Args:
        on_phone_connected: callable(bool) — True when a phone connects.
        on_message: callable(dict) — every parsed message from a phone.
        log: callable(str) — server log lines (falls back to print).
    """
    def out(text):
        if log:
            log(text)
        else:
            print(text)

    ssl_context = build_ssl_context(DATA_DIR)
    fp = certificate_fingerprint(DATA_DIR)
    code = pairing.get_pairing_code(DATA_DIR)

    out(f"LiteLink server on wss://{host}:{port}")
    out(f"Certificate SHA-256 fingerprint: {format_fingerprint(fp)}")
    out(f"Pairing code: {code}")

    async def handle_connection(websocket):
        addr = websocket.remote_address

        try:
            await asyncio.wait_for(_require_pairing(websocket), timeout=PAIR_TIMEOUT)
        except (asyncio.TimeoutError, websockets.exceptions.ConnectionClosed):
            out(f"[Rejected] {addr}: no valid pairing")
            return

        out(f"[Phone connected] {addr}")
        CONNECTED_PHONES.add(websocket)
        if on_phone_connected:
            on_phone_connected(True)

        try:
            async for raw in websocket:
                try:
                    data = json.loads(raw)
                    msg_type = data.get("type", "unknown")

                    if msg_type == "notification":
                        app = data.get("app", "?")
                        title = data.get("title", "")
                        text = data.get("text", "")
                        out(f"[{app}] {title}")
                        if text:
                            out(f"    {text}")
                    else:
                        out(f"[{msg_type}] from {addr}")

                    if on_message:
                        on_message(data)

                    # TODO: route to GUI or process accordingly
                    await websocket.send(json.dumps({"status": "received"}))

                except json.JSONDecodeError:
                    out(f"[Invalid JSON] from {addr}: {raw[:200]}")

        except websockets.exceptions.ConnectionClosed:
            out(f"[Phone disconnected] {addr}")
        finally:
            CONNECTED_PHONES.discard(websocket)
            if on_phone_connected:
                on_phone_connected(False)

    async with websockets.serve(
        handle_connection, host, port, ssl=ssl_context, ping_interval=30
    ):
        await asyncio.Future()


async def _require_pairing(websocket) -> None:
    """First message must be {'type': 'pair', 'code': <6-digit code>}."""
    raw = await websocket.recv()
    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        raise websockets.exceptions.ConnectionClosedError(None, "bad pairing message")
    if data.get("type") == "pair" and pairing.verify(DATA_DIR, str(data.get("code", ""))):
        await websocket.send(json.dumps({"type": "pair_ok"}))
        return
    raise websockets.exceptions.ConnectionClosedError(None, "invalid pairing code")
