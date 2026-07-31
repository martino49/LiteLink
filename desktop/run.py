"""Convenience entry point: python run.py

Launches the LiteLink desktop GUI. If PySide6 is not installed yet it
falls back to the console server.
"""

import sys
import os

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "src"))

try:
    from litelink.gui import run as gui_run
except ImportError:
    gui_run = None

if __name__ == "__main__":
    if gui_run is not None:
        gui_run()
    else:
        print("PySide6 not installed; running console server instead.\n")
        from litelink.__main__ import main

        main()
