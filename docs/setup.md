# LiteLink Setup Guide

## Prerequisites

- A laptop running **Windows 10/11** or **Linux** (x86_64)
- An **Android phone** running Android 7+ (Redmi 14 C recommended)
- Both devices on the **same Wi-Fi network**
- A USB cable (for initial ADB setup only)

---

## Step 1: Install the Desktop App

### Option A: Portable Build (Coming Soon)

Download the latest release ZIP from the releases page. Unzip and run `LiteLink.exe`.

### Option B: From Source (Current)

```bash
# Install Python 3.11+ from python.org if you don't have it

# Clone the repository
git clone https://github.com/yourname/litelink
cd litelink/desktop

# Create a virtual environment (recommended)
python -m venv .venv
source .venv/bin/activate   # Linux
.venv\Scripts\activate       # Windows

# Install dependencies
pip install -r requirements.txt
```

### Download External Dependencies

LiteLink bundles `adb`, `scrcpy`, and `ffmpeg`. Download them manually:

1. **ADB (Android Debug Bridge):** Download platform-tools from developer.android.com/tools/releases/platform-tools
2. **scrcpy:** Download from github.com/Genymobile/scrcpy/releases
3. **FFmpeg:** Download from ffmpeg.org/download.html

Place the executables in `desktop/bin/`:

```
desktop/bin/
├── adb.exe         (Windows) or adb (Linux)
├── scrcpy.exe      (Windows) or scrcpy (Linux)
└── ffmpeg.exe      (Windows) or ffmpeg (Linux)
```

On Linux, you can install them via your package manager instead:

```bash
sudo apt install adb scrcpy ffmpeg
```

---

## Step 2: Prepare Your Phone

### Enable Developer Options

1. Open **Settings → About Phone**
2. Tap **MIUI version** 7 times until "You are now a developer" appears
3. Go back to **Settings → Additional Settings → Developer Options**

### Enable USB Debugging

1. In Developer Options, enable **USB Debugging**
2. Enable **USB Debugging (Security Settings)** — needed for scrcpy keyboard/mouse control

### Install the Companion APK

1. Transfer the `LiteLinkCompanion.apk` to your phone
2. Open the file and tap **Install**
3. If MIUI blocks it, enable **Install from Unknown Sources** when prompted

---

## Step 3: Grant Permissions

### Notification Access

1. Go to **Settings → Notification & Control Center → Notification Access**
2. Find **LiteLinkCompanion** and toggle it ON
3. Tap **Allow** on the warning dialog

### Prevent MIUI from Killing the Companion App

This is critical. Without these steps, MIUI will kill the app within 30 minutes.

1. **Settings → Apps → Manage Apps → LiteLinkCompanion**
2. Tap **Battery Saver** → Select **No restrictions**
3. Tap **Permissions → Other Permissions** → Enable **Display pop-up windows**
4. Open the **Recent Apps** screen (square button or gesture)
5. Find LiteLinkCompanion, long-press it, tap the **lock icon**

---

## Step 4: First Connection

### On Your Laptop

```bash
cd litelink/desktop
python run.py
```

You should see:

```
🔌 LiteLink - Setup Assistant
Phone Status: disconnected
Starting WebSocket Server... Press Ctrl+C to stop.
```

### Pair via USB (First Time Only)

1. Plug your phone into the laptop via USB
2. On your phone, tap **Allow** on the USB Debugging prompt
3. In the terminal, the status should change to "connected"
4. Run the wireless setup:

```bash
adb tcpip 5555
adb connect <YOUR_PHONE_IP>:5555
```

5. Unplug the USB cable

### Connect the Companion App

1. Open LiteLinkCompanion on your phone
2. Enter your laptop's local IP address (find it with `ipconfig` on Windows or `ip addr` on Linux)
3. Toggle the switch to **ON**
4. You should see `Phone connected from: ...` in your laptop terminal

---

## Step 5: Verify It Works

| Test | Expected Result |
|------|----------------|
| Click "Mirror" in LiteLink | scrcpy window opens with your phone screen |
| Send yourself a WhatsApp | Notification popup appears on laptop |
| Edit a file in the synced folder on laptop | File updates on phone within 2 seconds |
| Copy text on phone | Pasteable on laptop with Ctrl+V |

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `adb: command not found` | Download platform-tools or add to PATH |
| `scrcpy: command not found` | Download scrcpy or install via package manager |
| Phone shows "unauthorized" | Re-plug USB cable and tap "Allow" |
| Companion app disconnects after 30 min | Lock the app in recent apps (see Step 3) |
| WebSocket connection refused | Check laptop firewall (port 8765) |
| Notifications show no text | Enable Notification Access in Settings |
