#!/usr/bin/env bash
# LiteLink Companion APK Builder
# Downloads Android SDK and builds the APK
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$SCRIPT_DIR/.."
MOBILE_DIR="$PROJECT_DIR/mobile/LiteLinkCompanion"
SDK_DIR="$PROJECT_DIR/.android-sdk"
BUILD_DIR="$PROJECT_DIR/.build-tools"

echo "=== LiteLink APK Builder ==="
echo ""

# Check Java
if ! command -v java &>/dev/null; then
    echo "ERROR: Java not found. Install JDK 17+ first."
    echo "       Debian/Ubuntu: sudo apt install openjdk-17-jdk"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | grep -oP '\d+' | head -1)
echo "Java version: $JAVA_VER"

if [ "$JAVA_VER" -lt 17 ] || [ "$JAVA_VER" -gt 21 ]; then
    echo "WARNING: Android Gradle Plugin works best with Java 17-21."
    echo "         You have Java $JAVA_VER. The build may fail."
    echo "         Press Ctrl+C to cancel, or wait to try anyway."
    sleep 3
fi

# Download Android command-line tools if not present
if [ ! -f "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "Downloading Android command-line tools..."
    mkdir -p "$SDK_DIR"
    cd "$SDK_DIR"
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
    unzip -q commandlinetools-linux-*.zip
    mkdir -p cmdline-tools/latest
    mv cmdline-tools/* cmdline-tools/latest/ 2>/dev/null || true
    rm -f commandlinetools-linux-*.zip
    echo "Android command-line tools downloaded."
fi

export ANDROID_HOME="$SDK_DIR"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"

# Accept licenses
echo "y" | $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager --licenses >/dev/null 2>&1 || true

# Install required SDK components
echo "Installing Android SDK 34..."
$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
    "platforms;android-34" \
    "build-tools;34.0.0" \
    >/dev/null 2>&1 || {
    echo "WARNING: SDK install failed. Trying without output redirection..."
    $ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager \
        "platforms;android-34" \
        "build-tools;34.0.0" || true
}

# Create local.properties
echo "sdk.dir=$ANDROID_HOME" > "$MOBILE_DIR/local.properties"

# Build APK
echo ""
echo "Building APK..."
cd "$MOBILE_DIR"

# Download Gradle wrapper if not present
if [ ! -f "gradlew" ]; then
    echo "Downloading Gradle wrapper..."
    gradle_version=$(grep distributionUrl gradle/wrapper/gradle-wrapper.properties | grep -oP 'gradle-\K[^\\-]+')
    wget -q "https://services.gradle.org/distributions/gradle-${gradle_version}-bin.zip" -O /tmp/gradle-bin.zip
    mkdir -p /tmp/gradle-wrapper
    unzip -q /tmp/gradle-bin.zip -d /tmp/gradle-wrapper
    /tmp/gradle-wrapper/gradle-${gradle_version}/bin/gradle wrapper --gradle-version=${gradle_version}
    rm -rf /tmp/gradle-bin.zip /tmp/gradle-wrapper
    echo "Gradle wrapper created."
fi

chmod +x gradlew
./gradlew assembleDebug

APK_OUTPUT="$MOBILE_DIR/app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK_OUTPUT" ]; then
    echo ""
    echo "=== BUILD SUCCESSFUL ==="
    echo "APK: $APK_OUTPUT"
    echo "Size: $(du -h "$APK_OUTPUT" | cut -f1)"
    echo ""
    echo "Install with: adb install $APK_OUTPUT"
else
    echo ""
    echo "=== BUILD FAILED ==="
    echo "Check the error messages above."
    exit 1
fi
