"""Real-time folder sync engine.

Watches a desktop folder for changes and syncs them to the phone
via WebSocket. Uses watchdog for file system monitoring.
"""

import os
import json
import base64
import hashlib
import threading
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler


class SyncHandler(FileSystemEventHandler):
    """Watches a folder and sends file changes to the phone."""

    def __init__(self, local_root, phone_root, websocket, direction="bidirectional"):
        self.local_root = os.path.abspath(local_root)
        self.phone_root = phone_root
        self.ws = websocket
        self.direction = direction
        self.hashes = {}
        self._timers = {}

    def on_modified(self, event):
        if not event.is_dir:
            self._dispatch(event.src_path, "modified")

    def on_created(self, event):
        if not event.is_dir:
            self._dispatch(event.src_path, "created")

    def on_deleted(self, event):
        if not event.is_dir:
            self._dispatch(event.src_path, "deleted")

    def on_moved(self, event):
        if not event.is_dir:
            self._dispatch(event.src_path, "deleted")
            self._dispatch(event.dest_path, "created")

    def _dispatch(self, path, event_type):
        if self.direction == "to_pc":
            return

        rel = os.path.relpath(path, self.local_root)
        remote = os.path.join(self.phone_root, rel).replace("\\", "/")

        # Debounce
        if path in self._timers:
            self._timers[path].cancel()
        t = threading.Timer(0.5, self._sync, args=[path, remote, event_type, rel])
        t.daemon = True
        self._timers[path] = t
        t.start()

    def _sync(self, local_path, remote_path, event_type, rel_path):
        self._timers.pop(local_path, None)

        if event_type == "deleted":
            msg = json.dumps({"type": "sync_delete", "path": remote_path})
        else:
            if not os.path.exists(local_path):
                return
            with open(local_path, "rb") as f:
                data = f.read()
            h = hashlib.md5(data).hexdigest()
            if h == self.hashes.get(local_path):
                return
            self.hashes[local_path] = h
            msg = json.dumps({
                "type": "sync_file",
                "path": remote_path,
                "data": base64.b64encode(data).decode(),
            })

        # TODO: send via websocket (needs async bridge)
        print(f"[Sync] {event_type} {rel_path} ({len(msg)} bytes)")


class FolderSync:
    """Manages watchdog observer for a sync pair."""

    def __init__(self, local_path, phone_path, websocket, direction="bidirectional"):
        self.local = local_path
        self.phone = phone_path
        self.ws = websocket
        self.direction = direction
        self._observer = None

    def start(self):
        handler = SyncHandler(self.local, self.phone, self.ws, self.direction)
        self._observer = Observer()
        self._observer.schedule(handler, self.local, recursive=True)
        self._observer.start()
        print(f"[FolderSync] Watching {self.local}")

    def stop(self):
        if self._observer:
            self._observer.stop()
            self._observer.join()
