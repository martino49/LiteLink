"""Persistent battery + signal history for the desktop dashboard graphs.

Samples are appended by a QTimer in the pages and stored as JSON lines in
the LiteLink data directory so graphs survive restarts.
"""

import json
import time

from pathlib import Path

from ..server import DATA_DIR


def _file(name):
    return DATA_DIR / name


def append_sample(name, sample: dict):
    """Append one sample dict to a JSON-lines history file."""
    f = _file(name)
    f.parent.mkdir(parents=True, exist_ok=True)
    with open(f, "a", encoding="utf-8") as fh:
        fh.write(json.dumps(sample) + "\n")


def load_samples(name, limit=500):
    """Return the last `limit` samples as a list of dicts."""
    f = _file(name)
    if not f.exists():
        return []
    out = []
    try:
        with open(f, encoding="utf-8") as fh:
            for line in fh:
                line = line.strip()
                if not line:
                    continue
                try:
                    out.append(json.loads(line))
                except json.JSONDecodeError:
                    continue
    except OSError:
        return []
    return out[-limit:]


def clear_samples(name):
    f = _file(name)
    if f.exists():
        f.unlink()


def battery_series(limit=500):
    """Return [(time, level), ...] from the battery history."""
    return [(s.get("t", 0), s.get("level", 0)) for s in load_samples("battery_history.jsonl", limit)]


def signal_series(limit=500):
    """Return [(time, dbm), ...] from the signal history."""
    return [(s.get("t", 0), s.get("dbm", 0)) for s in load_samples("signal_history.jsonl", limit)]
