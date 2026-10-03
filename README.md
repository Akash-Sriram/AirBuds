# AirBuds

A lightweight, bloat-free Android companion app for **realme Buds Air 8** (Model: RMA2503).

No account login, no telemetry, no tracking, and no internet permission. Standalone APK size is ~130 KB.

> **Compatibility**: Developed specifically for **realme Buds Air 8 (Model: RMA2503)** on firmware `1.1.0.104`. Other Realme, OPPO, or OnePlus earbuds using Bestechnic (BES) chipsets may work partially depending on firmware variations.

## Features

- **Noise Control**: Active Noise Cancellation (ANC), Transparency, and Off modes, with granular ANC level tuning (Smart, Max, Moderate, Mild).
- **Notification & Quick Settings**: Persistent status widget and Quick Settings tile to switch ANC modes directly from Android's notification shade.
- **Equalizer & Sound Effects**: Factory sound presets (Serenade, Original Sound, Clear Bass, Deep Bass), real-time 6-band custom EQ curve syncing, Dynamic Bass sliders, Spatial Audio, and Hi-Res audio toggle.
- **Touch Controls**: Remap double-tap, triple-tap, and touch-and-hold gestures independently for each earbud with direct EEPROM persistence.
- **Triple-Device Multipoint**: View paired device history (with device-type icons and MAC addresses) and toggle simultaneous triple-device connection on/off.
- **Smart Earbud Controls**: In-ear detection, wind noise reduction, vocal enhancement, auto-answer calls, and low-latency Game Mode.
- **Find Earbuds**: Acoustic sweep chime to locate misplaced earbuds.
- **Battery Status**: Live battery percentages and charging indicators for Left bud, Right bud, and the Charging Case.

## Known Limitations

Due to firmware locks in realme's BES MCU implementation and Android OS security policies:

- **Remote Device Disconnect & Audio Hijacking**: The earbud MCU does not implement remote ACL disconnect opcodes (`0x0429` / `0x040B`). Disconnecting a secondary device must be done from that device. Audio routing is automatically arbitrated by the earbud hardware upon playback.
- **Active Audio Detection**: The earbud MCU masks the streaming status (`0x00`) in device list packets (`0x8112`), making it impossible to detect which connected device is actively streaming audio via protocol telemetry.
- **Local Disconnect & Unpair**: Android restricts programmatic disconnection of Bluetooth audio profiles (A2DP/HFP) and unpairing (`removeBond`) to privileged system apps (`BLUETOOTH_PRIVILEGED`). These actions must be performed via Android's native Bluetooth settings.
- **Firmware Updates (OTA)**: Bootloader hardware secure boot requires Realme's cryptographic signature keys; flashing unsigned firmware is blocked to prevent unrecoverable bricking. Use the official HeyMelody app for OTA updates.

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
