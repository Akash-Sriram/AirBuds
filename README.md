# AirBuds 🎧

[![Android](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84.svg?style=flat&logo=android)](https://www.android.com)
[![Release](https://img.shields.io/github/v/release/Akash-Sriram/AirBuds?style=flat&color=blue)](https://github.com/Akash-Sriram/AirBuds/releases)
[![APK Size](https://img.shields.io/badge/APK%20Size-~106%20KB-success.svg)](#)
[![License](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

An ultra-lightweight, high-performance, open-source companion app for **Realme / OPPO / OnePlus TWS Earbuds** (BES2600 / BES2700 Bluetooth chipsets).

Built natively in pure Java without third-party frameworks, background bloat, tracking, or cloud account requirements.

---

## ✨ Features

- **🛡️ Active Noise Cancellation (ANC)**
  - Toggle between **ANC On**, **Transparency**, and **Off**.
  - Dynamic status indicator with instant hardware synchronization.
- **⚡ Quick Settings Tile**
  - Instant ANC mode cycle directly from the Android Quick Settings shade without opening the app.
- **🔀 Multi-Point Dual Connection Management**
  - Real-time paired device list retrieved directly from earbud firmware memory.
  - Contextual hardware icons for each connected device: **Smartphone**, **Laptop**, **Desktop PC**, and **Tablet**.
  - Audio route handover: force switch playback between paired devices with a single tap.
  - Disconnect or remove/unpair devices directly from the app.
- **🎛️ Equalizer & Audio Presets**
  - Switch between studio acoustic tunings: **Serenade** (Vocals), **Original Sound**, **Clear Bass**, and **Deep Bass**.
  - 6-band Custom Equalizer with live interactive frequency response visualizer curve.
- **👆 Touch Gesture Remapping**
  - Customize single-tap, double-tap, triple-tap, and touch-and-hold gestures independently for Left and Right earbuds.
  - Map actions to Play/Pause, Next/Previous Track, Voice Assistant, Game Mode, or ANC cycling.
- **⚡ Game Mode (Low Latency)**
  - Toggle ultra-low latency audio streaming mode.
- **🔔 Find My Earbuds**
  - High-frequency acoustic sweep alert to locate misplaced earbuds.
- **🔋 Battery & Charging Telemetry**
  - Independent battery levels for Left earbud, Right earbud, and Charging Case.
  - Active charging indicator badge.
- **🪶 Ultra Lightweight & Zero Telemetry**
  - Standalone APK size of **~106 KB**.
  - Zero external dependencies, analytics, internet permissions, or proprietary vendor accounts.

---

## 📱 Supported Devices

Designed for earbuds utilizing the **Bestechnic (BES)** Bluetooth SPP protocol (`00001101-0000-1000-8000-00805f9b34fb`), including:

- **Realme**: Buds Air 5, Buds Air 5 Pro, Buds Air 6, Buds Air 6 Pro, Buds Air 3, Buds Wireless 3
- **OPPO**: Enco Air 3 Pro, Enco X2, Enco Free series
- **OnePlus**: Buds Pro 2, Buds 3, Nord Buds series

---

## 🛠️ Building from Source

AirBuds uses a streamlined shell build pipeline that relies solely on standard Android SDK tools (`aapt2`, `javac`, `d8`, `zipalign`, `apksigner`) without heavy Gradle overhead.

### Prerequisites
- Android SDK Platform `android-35`
- Android Build-Tools `35.0.0`
- JDK 17+

### Build Steps
```bash
# Clone the repository
git clone git@github.com:Akash-Sriram/AirBuds.git
cd AirBuds

# Compile and package the APK
chmod +x build.sh
./build.sh
```

The signed production APK will be generated at `./AirBuds.apk`.

---

## 🔬 Protocol & Architecture

Communication with the earbuds operates over the **Bluetooth Serial Port Profile (SPP)** using proprietary framed packets:
- **Magic Frame**: `0xAA` start byte
- **Length & Sequence**: 2-byte length, 2-byte command ID, 1-byte sequence counter
- **Commands**:
  - `0x00`: Handshake & Hardware Info
  - `0x04`: Battery & Firmware Telemetry
  - `0x05`: Firmware Version String Query
  - `0x09`: ANC Mode & Sound Effect Settings
  - `0x0C`: Dual Device Query / Audio Handover
  - `0x0D`: Touch Gesture Configuration
  - `0x12`: Device Memory Table
  - `0x1A`: Equalizer Preset & Custom Gain Curve

---

## 📄 License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

## 🙏 Acknowledgements & References

- [Pods-Protocol-Reverse-Engineering](https://github.com)
- [QuickBuds](https://github.com) & [BudsLink](https://github.com)
- Realme / OPPO HeyMelody protocol reverse-engineering contributors
