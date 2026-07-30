"""Settings dialog for LiteLink."""

from PySide6.QtWidgets import QDialog, QVBoxLayout, QLabel, QLineEdit, QPushButton


class SettingsDialog(QDialog):
    """Application settings."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("LiteLink Settings")
        layout = QVBoxLayout(self)

        layout.addWidget(QLabel("Laptop IP (for companion app):"))
        self.ip_input = QLineEdit("192.168.1.100")
        layout.addWidget(self.ip_input)

        save_btn = QPushButton("Save")
        save_btn.clicked.connect(self.accept)
        layout.addWidget(save_btn)
