"""Main LiteLink GUI window."""

from PySide6.QtWidgets import QMainWindow, QWidget, QVBoxLayout, QPushButton, QLabel


class MainWindow(QMainWindow):
    """Primary application window."""

    def __init__(self):
        super().__init__()
        self.setWindowTitle("LiteLink")
        self.setMinimumSize(800, 600)

        central = QWidget()
        self.setCentralWidget(central)
        layout = QVBoxLayout(central)

        layout.addWidget(QLabel("LiteLink — Phone Bridge"))

        mirror_btn = QPushButton("Mirror Screen")
        layout.addWidget(mirror_btn)
