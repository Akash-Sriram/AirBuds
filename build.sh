#!/bin/bash
set -e

SDK_PLATFORM="${ANDROID_HOME:-$HOME/android-sdk}/platforms/android-35/android.jar"
if [ ! -f "$SDK_PLATFORM" ]; then
    SDK_PLATFORM="/usr/lib/android-sdk/platforms/android-35/android.jar"
fi

BUILD_TOOLS="${ANDROID_HOME:-$HOME/android-sdk}/build-tools/35.0.0"
if [ ! -d "$BUILD_TOOLS" ]; then
    BUILD_TOOLS="/usr/lib/android-sdk/build-tools/35.0.0"
fi

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
KEYSTORE="/tmp/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v -keystore "$KEYSTORE" \
        -alias androiddebugkey -storepass android -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US"
fi

$APKSIGNER sign --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out AirBuds.apk \
    build/aligned.apk

cp -f AirBuds.apk AirBridge.apk

echo "Successfully built AirBuds.apk (also synced to AirBridge.apk)!"
