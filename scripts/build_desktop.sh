#!/usr/bin/env bash
# Build LiteLink desktop app into a standalone executable
set -e

cd "$(dirname "$0")/../desktop"

echo "=== LiteLink Desktop Build ==="
echo ""

# Check Python
if ! command -v python3 &>/dev/null; then
    echo "ERROR: python3 not found"
    exit 1
fi

# Install dependencies
pip install -r requirements.txt

# Install PyInstaller
pip install pyinstaller

# Build
pyinstaller \
    --onefile \
    --windowed \
    --name "LiteLink" \
    --add-data "bin:bin" \
    src/litelink/__main__.py

echo ""
echo "=== Build complete ==="
echo "Output: dist/LiteLink"
