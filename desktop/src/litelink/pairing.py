"""Pairing-code management for LiteLink.

The desktop generates a 6-digit pairing code, printed in the console/GUI.
The companion app must present it on connect (before any other message).
The code persists on disk so reconnects survive server restarts; it is
only regenerated explicitly (GUI button or deleting the file).
"""

import secrets
from pathlib import Path

CODE_FILE = "pairing_code.txt"


def get_pairing_code(data_dir: Path) -> str:
    """Return the current pairing code, generating one if missing."""
    path = data_dir / CODE_FILE
    if path.exists():
        return path.read_text().strip()
    return regenerate_pairing_code(data_dir)


def regenerate_pairing_code(data_dir: Path) -> str:
    """Create a new 6-digit pairing code and persist it."""
    data_dir.mkdir(parents=True, exist_ok=True)
    code = f"{secrets.randbelow(1_000_000):06d}"
    (data_dir / CODE_FILE).write_text(code)
    return code


def verify(data_dir: Path, code: str) -> bool:
    """Constant-time check of a presented pairing code."""
    current = get_pairing_code(data_dir)
    return bool(current) and secrets.compare_digest(current, code)
