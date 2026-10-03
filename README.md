# AirBuds

A lightweight, bloat-free Android companion app for **realme Buds Air 8** (Model: RMA2503).

No account login, no telemetry, no tracking, and no internet permission. Standalone APK size is ~130 KB.

> **Compatibility**: Tested and developed specifically for the **realme Buds Air 8 (Model: RMA2503)** on firmware `1.1.0.104`. Other Realme, OPPO, or OnePlus earbuds using Bestechnic (BES) chipsets may partially work depending on firmware variations.

## Features

- **Noise Control**: Toggle between Active Noise Cancellation (ANC), Transparency, and Off, with granular ANC level tuning (Smart, Max, Moderate, Mild).
- **Notification & Quick Settings**: Persistent status widget and Quick Settings tile to switch ANC modes directly from Android's notification shade.
- **Equalizer & Sound Effects**: Factory sound presets (Serenade, Original Sound, Clear Bass, Deep Bass), real-time 6-band custom EQ curve syncing, Dynamic Bass sliders, Spatial Audio, and Hi-Res audio toggle.
- **Touch Controls**: Remap double tap, triple tap, and touch-and-hold gestures independently for each earbud with direct EEPROM persistence.
- **Triple-Device Multipoint**: View paired devices history (with device-type icons and MAC addresses) and toggle simultaneous triple-device connection on/off.
- **Real-Time Local Audio Detection**: Instant, event-driven active audio playback detection on the connected phone with zero polling lag.
- **Smart Earbud Controls**: In-ear detection, wind noise reduction, vocal enhancement, auto-answer calls, and low-latency Game Mode.
- **Find Earbuds**: Acoustic sweep chime to locate misplaced earbuds.
- **Battery Status**: Live battery percentages and charging indicators for Left bud, Right bud, and the Charging Case.
- **Local Device Management**: Cleanly disconnect or unpair earbuds directly from the app using native Android Bluetooth APIs.

## Known Limitations

Due to hardware-level encryption and firmware restrictions in realme's BES MCU implementation (`066812`), certain operations cannot be performed by any third-party application:

### 1. Remote Device Disconnection & Forced Audio Switching
* **Firmware Lock**: The realme Buds Air 8 firmware does not implement remote ACL disconnection or remote unpairing opcodes (`0x0429` / `0x040B`). In BBK's firmware architecture, this capability (`connectDisconnectDevice`) is reserved strictly for a small number of flagship OnePlus and Oppo models.
* **Behavior**: To disconnect a remote paired device (e.g. secondary phone, laptop, or PC), you must turn off Bluetooth on that device or disconnect it from its own Bluetooth menu.
* **Audio Handover**: Audio routing cannot be forcefully hijacked remotely; the earbud hardware arbitrates audio playback automatically at the chip level as soon as a connected device begins streaming media or receives a phone call.

### 2. Remote Active Audio Telemetry
* **Encryption & Firmware Masking**: The earbud MCU always sets the status flags to `0x00` for remote connected devices in the device list packet (`0x8112`), regardless of whether audio is streaming.
* **AES-128 CCM Link Encryption**: Bluetooth ACL audio streams between secondary devices and the earbuds are encrypted point-to-point with AES-128 CCM keys negotiated during pairing, making over-the-air packet sniffing mathematically impossible.
* **Behavior**: AirBuds reports `● Active Audio` strictly for the local phone (verified in real time via Android's `AudioManager` and `BluetoothA2dp` playing states), while accurately showing remote devices as `Connected` without speculative guesswork.

### 3. Firmware Updates (OTA)
* **Hardware Secure Boot**: The BES chipset bootloader strictly validates cryptographic digital signatures (RSA/ECDSA) on all firmware binary blocks before committing them to flash memory. Third-party binaries cannot be signed without Realme's private key.
* **Permanent Bricking Risk**: Flashing dual-earbud firmware requires low-level DFU (Device Firmware Upgrade) partition synchronization across both independent earbuds simultaneously over RFCOMM. Any dropped packet, timeout, or partition handshake failure would permanently brick the earbuds into an unrecoverable state with no physical recovery interface.
* **Recommendation**: If an official firmware update is ever needed, temporarily open the official HeyMelody app to install it.

## Acknowledgements

This project relies on the protocol research, packet framing specifications, and reverse-engineering work from these open-source projects:

- [QuickBuds](https://github.com/spizganed/QuickBuds) by [spizganed](https://github.com/spizganed) – Wire protocol specifications and packet framing logic.
- [BudsLink](https://github.com/maniacx/BudsLink) by [maniacx](https://github.com/maniacx) – Realme profile definitions and 6-band EQ mappings.
- [OppoPodsManager](https://github.com/Zhaoyi-ya/OppoPodsManager) by [Zhaoyi-ya](https://github.com/Zhaoyi-ya) – Buffer packet decoder and multi-device connection handling.
- [OppoPods](https://github.com/Leaf-lsgtky/OppoPods) by [Leaf-lsgtky](https://github.com/Leaf-lsgtky) – Android RFCOMM Bluetooth controller implementation.
- [Pods-Protocol-Reverse-Engineering](https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering) by [Star-ZER0](https://github.com/Star-ZER0) – Packet structure and opcode documentation.
- [OPPO-Pods-Win](https://github.com/Zhaoyi-ya/OPPO-Pods-Win) by [Zhaoyi-ya](https://github.com/Zhaoyi-ya) – RFCOMM socket communication reference.
- [DevPods](https://github.com/ORION2809/DevPods) by [ORION2809](https://github.com/ORION2809) – Gesture event routing reference.

## License

[GNU General Public License v3.0](LICENSE)
