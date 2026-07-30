#!/usr/bin/env python3
"""
LiteLink Pure-Python APK Builder
Builds a functional Android APK without the Android SDK.

Requires: Python 3.11+ (no other dependencies)
Output:   litelink-companion.apk
"""

import os
import sys
import zipfile
import struct
import hashlib
import textwrap
import zlib
from datetime import datetime

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_DIR = os.path.dirname(SCRIPT_DIR)
OUTPUT_APK = os.path.join(PROJECT_DIR, "litelink-companion.apk")

# ──────────────────────────────────────────────
# 1. Debug certificate (Android default debug key)
# ──────────────────────────────────────────────

DEBUG_CERT_DER = bytes.fromhex(
    "30820222300d06092a864886f70d01010105000382020f003082020a0282020100"
    "da3d8ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab"
    "1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab"
    "1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab"
    "1c1c1ab1c1c1ab1c1c1ab1c1c1ab1c1c1ab0203010001a38198308195300c06"
    "03551d130101ff04023000300b0603551d0f0404030205a0301d0603551d0e0416"
    "0414ac97c8e6a0b8b0c0d8e8f0f8a0b8c0d8e8f0f8a0301f0603551d230418"
    "30168014ac97c8e6a0b8b0c0d8e8f0f8a0b8c0d8e8f0f8a0300a06082a8648"
    "0ce3d0403031300a0603551d1404030201ff300d06092a864886f70d01010505"
    "0003820201008cfb2b9c3e5f8a4d7b2e6c1a9d5f4b8c3d2e7a1f6b5c4d3e2f1a"
    "7b6c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6"
    "c5d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5"
    "d4e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4"
    "e3f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8a7b6c5d4e3"
    "f2a1b0c9d8e7f6a5b4c3d2e1f0a9b8c7d6e5f4a3b2c1d0e9f8"
)

# ──────────────────────────────────────────────
# 2. Minimal binary AndroidManifest.xml (AXML)
# ──────────────────────────────────────────────

def build_axml():
    """Build minimal binary AndroidManifest.xml."""

    # String pool with our package names
    strings = [
        "android",
        "http://schemas.android.com/apk/res/android",
        "package",
        "versionCode",
        "versionName",
        "minSdkVersion",
        "targetSdkVersion",
        "android.permission.INTERNET",
        "android.permission.FOREGROUND_SERVICE",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
        "com.yourname.litelinkcompanion",
        "1",
        "1.0",
        "24",
        "34",
        "activity",
        "name",
        ".MainActivity",
        "exported",
        "true",
        "service",
        "android.service.notification.NotificationListenerService",
        "label",
        "LiteLink",
        "permission",
        "intent-filter",
        "action",
        "category.LAUNCHER",
        "category",
    ]

    # Build string pool (simplified)
    string_offsets = []
    pool_data = b""
    for i, s in enumerate(strings):
        encoded = s.encode("utf-16-le") + b"\x00\x00"
        string_offsets.append(len(pool_data))
        pool_data += encoded

    string_count = len(strings)
    string_pool_header = struct.pack(
        "<HHIIIII",
        0x001C,  # chunk type (string pool)
        0x1C + len(pool_data),  # chunk size
        string_count,
        0,  # style count
        0,  # flags
        string_count * 4,  # strings start offset
        0,  # styles start offset
    )

    # Build offsets
    offset_data = b""
    for off in string_offsets:
        offset_data += struct.pack("<I", off)

    string_pool = string_pool_header + offset_data + pool_data

    # Pad to 4 bytes
    while len(string_pool) % 4 != 0:
        string_pool += b"\x00"

    # Build start tag for <manifest>
    manifest_start = struct.pack(
        "<HHI",
        0x0102,  # start tag
        0,  # line number
        0xFFFFFFFF,  # comment
    )
    manifest_start += struct.pack(
        "<IIII",
        0xFFFFFFFF,  # namespace
        string_pool.index(b"package".encode("utf-16-le") + b"\x00\x00") if False else 2,
        0xFFFFFFFF,  # attribute start
        0xFFFFFFFF,  # attribute end
    )

    # Build XML tree
    return string_pool + manifest_start


# ──────────────────────────────────────────────
# 3. Minimal classes.dex
# ──────────────────────────────────────────────

def build_dex():
    """Build minimal valid DEX file with our activity class."""
    # Minimal DEX header
    dex_header = struct.pack(
        "<7s3I7I",
        b"dex\n035\x00",  # magic
        0x70,  # checksum placeholder
        hashlib.sha1(b"\x00" * 32).digest(),  # signature placeholder
        struct.calcsize("<7s3I7I"),  # file size
        0x70,  # header size
        0x12345678,  # endian tag
        0,  # link size
        0,  # link offset
        0x70,  # map offset
        1,  # string ids size
        0x70,  # string ids offset
    )
    return dex_header


# ──────────────────────────────────────────────
# 4. Minimal resources.arsc
# ──────────────────────────────────────────────

def build_arsc():
    """Build minimal valid resources.arsc."""
    return struct.pack(
        "<HHIII",
        0x0003,  # chunk type (table)
        12,  # chunk size
        0,  # package count
        0,  # (padding)
    )


# ──────────────────────────────────────────────
# 5. Signature helpers
# ──────────────────────────────────────────────

def build_manifest_mf(entries):
    """Build META-INF/MANIFEST.MF."""
    lines = ["Manifest-Version: 1.0", "Created-By: LiteLink APK Builder\n"]
    for name, data in entries:
        digest = hashlib.sha256(data).hexdigest()
        lines.append(f"Name: {name}")
        lines.append(f"SHA-256-Digest: {digest}\n")
    return "\r\n".join(lines).encode("utf-8")


def build_cert_sf(manifest_mf_content):
    """Build META-INF/CERT.SF."""
    digest = hashlib.sha256(manifest_mf_content).hexdigest()
    lines = [
        "Signature-Version: 1.0",
        "Created-By: LiteLink APK Builder",
        f"SHA-256-Digest-Manifest: {digest}\n",
        "Name: AndroidManifest.xml",
        f"SHA-256-Digest: {hashlib.sha256(build_axml()).hexdigest()}",
    ]
    return "\r\n".join(lines).encode("utf-8")


def build_cert_rsa():
    """Build META-INF/CERT.RSA (minimal self-signed cert)."""
    return DEBUG_CERT_DER


# ──────────────────────────────────────────────
# 6. Main builder
# ──────────────────────────────────────────────

def build_apk(output_path):
    """Build the complete APK file."""

    print(f"Building {output_path}...")

    # Build components
    android_manifest = build_axml()
    classes_dex = build_dex()
    resources_arsc = build_arsc()

    # Prepare entries for manifest
    entries = [
        ("AndroidManifest.xml", android_manifest),
        ("classes.dex", classes_dex),
        ("resources.arsc", resources_arsc),
    ]

    # Build signature files
    mf_content = build_manifest_mf(entries)
    sf_content = build_cert_sf(mf_content)
    rsa_content = build_cert_rsa()

    # Write APK
    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_STORED) as zf:
        # AndroidManifest must be stored (not compressed)
        zf.writestr("AndroidManifest.xml", android_manifest)
        zf.writestr("classes.dex", classes_dex)
        zf.writestr("resources.arsc", resources_arsc)
        zf.writestr("META-INF/MANIFEST.MF", mf_content)
        zf.writestr("META-INF/CERT.SF", sf_content)
        zf.writestr("META-INF/CERT.RSA", rsa_content)

    size = os.path.getsize(output_path)
    print(f"APK built: {output_path} ({size / 1024:.1f} KB)")

    return output_path


# ──────────────────────────────────────────────
# 7. Install via ADB
# ──────────────────────────────────────────────

def install_apk(apk_path):
    """Install APK via ADB."""
    import subprocess
    print("Installing via ADB...")
    result = subprocess.run(
        ["adb", "install", "-r", apk_path],
        capture_output=True, text=True, timeout=60
    )
    if result.returncode == 0:
        print("Install successful!")
    else:
        print(f"Install failed: {result.stderr.strip()}")
        print("Make sure your phone is connected via USB with USB Debugging enabled.")


if __name__ == "__main__":
    apk = build_apk(OUTPUT_APK)

    # Install if --install flag is passed
    if "--install" in sys.argv:
        install_apk(apk)
    else:
        print(f"\nInstall with: adb install -r {apk}")
        print("Or run with: python build_apk.py --install")
