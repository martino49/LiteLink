"""Entry point for python -m litelink.

Launches the desktop GUI when PySide6 is available; otherwise falls back
to the console WSS server.
"""

import asyncio

from litelink.server import start_server


def main():
    try:
        from litelink.gui import run as gui_run
    except ImportError:
        gui_run = None

    if gui_run is not None:
        gui_run()
        return

    print("PySide6 not installed; running console server instead.\n")
    print("LiteLink starting...")
    print("Press Ctrl+C to stop.\n")
    try:
        asyncio.run(start_server())
    except KeyboardInterrupt:
        print("\nLiteLink stopped.")


if __name__ == "__main__":
    main()
