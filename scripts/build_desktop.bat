@echo off
REM Build LiteLink desktop app into a standalone executable
cd /d "%~dp0\..\desktop"

echo === LiteLink Desktop Build ===
echo.

REM Install dependencies
pip install -r requirements.txt

REM Install PyInstaller
pip install pyinstaller

REM Build
pyinstaller ^
    --onefile ^
    --windowed ^
    --name "LiteLink" ^
    --add-data "bin;bin" ^
    src/litelink/__main__.py

echo.
echo === Build complete ===
echo Output: dist\LiteLink.exe
pause
