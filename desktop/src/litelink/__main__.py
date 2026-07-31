"""Entry point for python -m litelink"""

import asyncio

from litelink.server import start_server


def main():
    print("LiteLink starting...")
    print("WebSocket server listening on ws://0.0.0.0:8765")
    print("Press Ctrl+C to stop.\n")

    try:
        asyncio.run(start_server())
    except KeyboardInterrupt:
        print("\nLiteLink stopped.")


if __name__ == "__main__":
    main()
