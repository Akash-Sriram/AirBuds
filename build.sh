#!/bin/bash
set -e

# Detect Android SDK Platform android.jar
if [ -z "$SDK_PLATFORM" ] || [ ! -f "$SDK_PLATFORM" ]; then
    for candidate in \
        "${ANDROID_HOME}/platforms/android-35/android.jar" \
        "${ANDROID_SDK_ROOT}/platforms/android-35/android.jar" \
        "$HOME/android-sdk/platforms/android-35/android.jar" \
        "/usr/lib/android-sdk/platforms/android-35/android.jar" \
        ${ANDROID_HOME}/platforms/android-*/android.jar \
        /usr/lib/android-sdk/platforms/android-*/android.jar; do
        if [ -f "$candidate" ]; then
            SDK_PLATFORM="$candidate"
            break
        fi
    done
fi

# Detect Android SDK Build-Tools
if [ -z "$BUILD_TOOLS" ] || [ ! -d "$BUILD_TOOLS" ]; then
    for candidate in \
        "${ANDROID_HOME}/build-tools/35.0.0" \
        "${ANDROID_SDK_ROOT}/build-tools/35.0.0" \
        "$HOME/android-sdk/build-tools/35.0.0" \
        "/usr/lib/android-sdk/build-tools/35.0.0" \
        ${ANDROID_HOME}/build-tools/* \
        /usr/lib/android-sdk/build-tools/*; do
        if [ -d "$candidate" ] && [ -f "$candidate/aapt2" ]; then
            BUILD_TOOLS="$candidate"
            break
        fi
    done
fi

if [ ! -f "$SDK_PLATFORM" ]; then
    echo "Error: android.jar not found! Please install android-35 platform."
    exit 1
fi

if [ ! -d "$BUILD_TOOLS" ]; then
    echo "Error: Android build-tools not found! Please install build-tools (e.g., 35.0.0)."
    exit 1
fi

echo "Using SDK Platform: $SDK_PLATFORM"
echo "Using Build Tools:  $BUILD_TOOLS"

AAPT2="$BUILD_TOOLS/aapt2"
D8="$BUILD_TOOLS/d8"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"

rm -rf build
mkdir -p build/gen build/obj build/bin

echo "Step 1: Compiling resources with aapt2..."
$AAPT2 compile --dir res -o build/resources.zip

echo "Step 2: Linking resources with aapt2..."
$AAPT2 link build/resources.zip \
    -I $SDK_PLATFORM \
    --manifest AndroidManifest.xml \
    --java build/gen \
    -o build/unaligned.apk

echo "Step 3: Compiling Java sources with javac..."
javac -cp "$SDK_PLATFORM" \
    -d build/obj \
    build/gen/org/airbridge/tws/R.java \
    src/org/airbridge/tws/*.java

echo "Step 4: Creating DEX with d8..."
$D8 build/obj/org/airbridge/tws/*.class \
    --lib "$SDK_PLATFORM" \
    --output build/bin

echo "Step 5: Packaging classes.dex into APK..."
cd build/bin
jar -uf ../unaligned.apk classes.dex
cd ../..

echo "Step 6: Running zipalign..."
$ZIPALIGN -f -p 4 build/unaligned.apk build/aligned.apk

echo "Step 7: Signing APK with apksigner..."
KEYSTORE="${KEYSTORE_FILE:-keystore/airbuds.jks}"
KEY_ALIAS="${KEY_ALIAS:-airbuds}"
KEY_PASS="${KEY_PASSWORD:-pass:airbuds2026}"
STORE_PASS="${STORE_PASSWORD:-pass:airbuds2026}"

if [ ! -f "$KEYSTORE" ]; then
    echo "Generating tailored keystore at $KEYSTORE..."
    mkdir -p "$(dirname "$KEYSTORE")"
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -alias "$KEY_ALIAS" -storepass airbuds2026 -keypass airbuds2026 \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=AirBuds, OU=Akash-Sriram, O=AirBuds, C=IN"
fi

$APKSIGNER sign --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "$STORE_PASS" \
    --key-pass "$KEY_PASS" \
    --out AirBuds.apk \
    build/aligned.apk

cp -f AirBuds.apk AirBridge.apk

echo "Successfully built AirBuds.apk (also synced to AirBridge.apk)!"
