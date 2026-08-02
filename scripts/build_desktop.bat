@echo off
REM Build LiteLink desktop app into a standalone executable
cd /d "%~dp0\..\desktop"

echo === LiteLink Desktop Build ===
echo.

REM Install dependencies
pip install -r requirements.txt

REM Install PyInstaller
pip install pyinstaller

REM Build (windowed GUI; __main__ prefers the GUI when PySide6 is present)
pyinstaller ^
    --onefile ^
    --windowed ^
    --name "LiteLink" ^
    --clean ^
    --add-data "bin;bin" ^
    --hidden-import websockets ^
    --hidden-import websockets.asyncio ^
    --hidden-import zeroconf ^
    --hidden-import cryptography ^
    src/litelink/__main__.py

echo.
echo === Build complete ===
echo Output: dist\LiteLink.exe
pause
