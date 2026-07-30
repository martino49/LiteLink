"""Notification sidebar — displays incoming notifications from the phone."""

from PySide6.QtWidgets import QWidget, QVBoxLayout, QLabel, QListWidget


class NotificationSidebar(QWidget):
    """Vertical list of recent notifications from the phone."""

    def __init__(self):
        super().__init__()
        layout = QVBoxLayout(self)
        layout.addWidget(QLabel("Notifications"))

        self.list_widget = QListWidget()
        layout.addWidget(self.list_widget)

    def add_notification(self, app, title, text):
        """Add a notification to the list."""
        self.list_widget.addItem(f"[{app}] {title}: {text}")
        if self.list_widget.count() > 50:
            self.list_widget.takeItem(0)
