# AirBuds

A lightweight, bloat-free Android companion app for **realme Buds Air 8** (Model: RMA2503).

No account login, no telemetry, no tracking, and no internet permission. Standalone APK size is ~105 KB.

> **Compatibility**: Tested and developed specifically for the **realme Buds Air 8 (Model: RMA2503)** on firmware 1.1.0.104. Other Realme, OPPO, or OnePlus earbuds using Bestechnic (BES) chipsets may or may not work depending on firmware variations.

## Features

- **Noise Control**: Toggle between Active Noise Cancellation (ANC), Transparency, and Off.
- **Quick Settings Tile**: Switch ANC modes directly from Android's notification shade.
- **Dual-Device Connection**: View paired devices and manually route audio playback between them.
- **Equalizer**: Presets (Serenade, Original Sound, Clear Bass, Deep Bass) and a 6-band custom EQ.
- **Touch Controls**: Remap single, double, triple tap, and hold gestures independently for each earbud.
- **Game Mode**: Toggle low-latency audio streaming.
- **Find Earbuds**: High-frequency acoustic chime sweep to locate misplaced buds.
- **Battery Status**: Live battery levels and charging indicators for Left, Right, and Case.

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
