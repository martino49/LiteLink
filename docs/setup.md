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

## Step 6: Second Screen — Extended Mode (Virtual Display)

**Duplicate mode** mirrors your main screen to the phone. **Extended mode** turns the
phone into a *real* second display — the PC desktop genuinely grows and you can drag
windows onto the phone's region. This requires a virtual display so the OS knows
there is another screen.

### On Windows (Virtual Display Driver)

Download the driver-only package (signature-verified, ~132 KB — no GUI needed):

```
https://github.com/VirtualDrivers/Virtual-Display-Driver/releases/download/25.7.23/VirtualDisplayDriver-x86.Driver.Only.zip
```

Or install via winget (admin PowerShell):

```powershell
winget install VirtualDrivers.Virtual-Display-Driver -e
```

Manual install from the ZIP:

1. Extract to a folder (contains `MttVDD.dll`, `MttVDD.inf`, `MttVDD.cat`, `vdd_settings.xml`)
2. Run the included installer `.bat` **as Administrator** to trust the driver certificate
3. **Device Manager → Display adapters → Action → Add legacy hardware →**
   **Install from a list (Advanced) → Display adapters → Have Disk → select `MttVDD.inf`**
4. The virtual monitor now appears in **Settings → Display**. Configure its resolution
   in `vdd_settings.xml` (e.g. add a 1080x1920 portrait mode for a phone).

### On Linux (X11 — no download needed)

The `xrandr` virtual monitor creates a real extended region to the right of your
primary screen, no driver required. Example (eDP-1 is the built-in screen):

```bash
xrandr --setmonitor VIRT-1 1080/1x1920/1+1920+0 none
xrandr --delmonitor VIRT-1   # to remove it
```

> **Note:** `xrandr --setmonitor` requires an **X11** session. It does **not** work
> under Wayland (virtual monitors are compositor-controlled). If your desktop runs
> Wayland, log in with an X11 session to use Extended mode, or use the
> Windows Virtual Display Driver instead.

LiteLink's Extended mode then streams that virtual monitor region to the phone
(duplicate = primary screen, extended = the virtual/second monitor).

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
| Extended mode still shows the main screen | No virtual display present — install the VDD driver (Windows) or use an X11 session with `xrandr --setmonitor` (Linux, see Step 6) |
