"""WebSocket server — the central hub of LiteLink.

The desktop runs this server. The companion app connects to it.
All JSON messages (notifications, file changes, commands) pass through here.
"""

import asyncio
import json
import websockets

CONNECTED_PHONES = set()


async def handle_connection(websocket):
    """Handle an incoming connection from the companion app."""
    addr = websocket.remote_address
    print(f"[Phone connected] {addr}")
    CONNECTED_PHONES.add(websocket)

    try:
        async for raw in websocket:
            try:
                data = json.loads(raw)
                msg_type = data.get("type", "unknown")

                if msg_type == "notification":
                    app = data.get("app", "?")
                    title = data.get("title", "")
                    text = data.get("text", "")
                    print(f"\n[{app}] {title}")
                    if text:
                        print(f"    {text}")
                else:
                    print(f"[{msg_type}] from {addr}")

                # TODO: route to GUI or process accordingly
                # For now, echo received status
                await websocket.send(json.dumps({"status": "received"}))

            except json.JSONDecodeError:
                print(f"[Invalid JSON] from {addr}: {raw[:200]}")

    except websockets.exceptions.ConnectionClosed:
        print(f"[Phone disconnected] {addr}")
    finally:
        CONNECTED_PHONES.discard(websocket)


async def start_server(host="0.0.0.0", port=8765):
    """Start the WebSocket server and run forever."""
    async with websockets.serve(handle_connection, host, port):
        await asyncio.Future()
