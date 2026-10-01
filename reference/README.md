# Reference Documentation: External Projects & Protocol Findings

This directory contains external reference repositories and reverse-engineering findings for the **Realme TWS (AirBridge)** project.

---

## 1. Reference Projects Catalog

| Repository | Path | Tech Stack | Primary Value & Use Case |
|---|---|---|---|
| [**QuickBuds**](https://github.com/spizganed/QuickBuds) | `reference/QuickBuds` | Kotlin (Android) / Rust & Slint (Desktop) | Authoritative wire protocol specification (`docs/PROTOCOL.md`), LEB128 packet framer (`OppoPacketFramer.kt`), and full model catalog (`ModelCatalog.kt`). |
| [**BudsLink**](https://github.com/maniacx/BudsLink) | `reference/BudsLink` | JavaScript (Linux Flatpak / BlueZ) | Dedicated Realme device profiles (`RealmeBudsAir7Pro.js`, `RealmeBudsAir7.js`, `RealmeBudsWireless5ANC.js`), 6-band EQ frequencies, and gesture mapping tables. |
| [**OppoPodsManager**](https://github.com/Zhaoyi-ya/OppoPodsManager) | `reference/OppoPodsManager` | C# / .NET Avalonia (Desktop) | Incremental sliding-buffer packet decoder (`FrameCodec.cs`), modular feature controllers (`Battery.cs`, `NoiseCancellation.cs`, `CustomEqualizer.cs`, `MultiDevice.cs`), and 135+ device profiles. |
| [**OppoPods**](https://github.com/Leaf-lsgtky/OppoPods) | `reference/OppoPods` | Kotlin (Android) | 945 lines of production-tested packet encoders and parsers (`Packets.kt`), RFCOMM controller (`RfcommController.kt`), and native connection popup dialogs (`ConnectionPopupActivity.kt`). |
| [**Pods-Protocol-Reverse-Engineering**](https://github.com/Star-ZER0/Pods-Protocol-Reverse-Engineering) | `reference/Pods-Protocol-Reverse-Engineering` | Markdown Docs | Byte-by-byte reverse-engineering notes (`handmade/OPPO-Protocol.md`), detailing handshakes, broadcast code subscriptions (`0x0205`), and ANC SET vs NOTIFY differences. |
| [**OPPO-Pods-Win**](https://github.com/Zhaoyi-ya/OPPO-Pods-Win) | `reference/OPPO-Pods-Win` | Python / CustomTkinter | Minimal, single-file desktop controller (`oppopods_ctk.py`) demonstrating raw RFCOMM socket operations. |
| [**DevPods**](https://github.com/ORION2809/DevPods) | `reference/DevPods` | TypeScript / Kotlin | Android earbud relay mesh and multi-vendor gesture event routing. |

---

## 2. Protocol Wire Format Summary

The communication channel uses **Classic Bluetooth RFCOMM / SPP**:
- **Primary UUID**: `0000079A-D102-11E1-9B23-00025B00A5A5`
- **Fallback UUID**: `00001107-D102-11E1-9B23-00025B00A5A5`
- **Socket Channel**: Typically RFCOMM channel `15` or dynamic via SDP service search.

### Frame Layout
```text
AA <TotalLen> 00 00 <Cmd LE> <Seq> <PayLen LE> <Payload...>
1B LEB128     2B    2B       1B    2B          PayLen bytes
```

- **Header (`0xAA`)**: Start delimiter.
- **TotalLen (LEB128)**: Length of remainder (`7 + PayLen`). If `< 128`, standard 1 byte; if `>= 128`, LEB128 encoded.
- **Reserved (`0x00 0x00`)**: Always two null bytes.
- **Cmd (2 Bytes Little-Endian)**: Command opcode. Earbud replies set bit 15 (`Cmd | 0x8000`).
- **Seq (1 Byte)**: Sequence counter (`0x01`–`0xFE`). Echoed in response. `0xFF` signifies an unsolicited device push notification.
- **PayLen (2 Bytes Little-Endian)**: Length of payload.
- **Payload**: Command arguments or response data.

---

## 3. Essential Command & Query Map

### Connection & Lifecycle
- `0x0100` -> `0x8100`: **Remote Capability Handshake**
- `0x0103` -> `0x8103`: **Product ID / Model Query** (returns 3-byte model code like `06 4C 12`)
- `0x0105` -> `0x8105`: **Firmware Version Query**
- `0x0200` -> `0x8200`: **Broadcast Capability Query**
- `0x0205` -> `0x8205`: **Subscribe to Broadcast Events** (`0x01` Battery, `0x02` Wear status, `0x03` ANC changes)

### Battery & Charging (`0x0106` / `0x0402`)
- **Query**: `0x0106` with empty payload.
- **Reply/Push**: TLV triples for Left, Right, Case:
  - Left: Tag `0x01`, Level byte `LL`
  - Right: Tag `0x02`, Level byte `RR`
  - Case: Tag `0x03`, Level byte `MM`
  - Battery Percentage: `byte & 0x7F` (0–100%)
  - Charging Status: `(byte & 0x80) != 0` (true when charging)

### Noise Control (ANC)
> **Crucial Rule**: The SET codes (`0x0404`) and NOTIFY bitmasks (`0x010C` / `0x810C`) differ!

| Mode | SET Command (`0x0404`) Payload | NOTIFY / Query Bitmask (`0x010C`) |
|---|---|---|
| **Off** | `01 01 01` (`0x01`) | `08 00` (`0x0008`) |
| **Noise Cancelling (ANC)** | `01 01 02` (`0x02`) | `10 00` (`0x0010`) |
| **Transparency** | `01 01 04` (`0x04`) | `00 01` (`0x0100`) |
| **Adaptive** | `01 01 00 08` (`0x0800`) | `00 08` (`0x0800`) |

### Equalizer & Audio Presets
- `0x010F` -> `0x810F`: **Query Active EQ Preset**
  - Presets: `0x00` (Original/Classic), `0x01` (Deep Bass), `0x02` (Serenade), `0x03` (Clear Bass)
- `0x0406`: **Set EQ Preset** (`payload = [presetId]`)
- `0x0418`: **Save/Update Custom 6-Band EQ**
  - Bands: `50 Hz, 250 Hz, 1000 Hz, 4000 Hz, 8000 Hz, 16000 Hz` (range: -6 dB to +6 dB)
- `0x0424`: **Bass Boost / BassWave Level**

### Touch Gestures & Button Configuration
- `0x0108`: **Query Current Gesture Configuration Table**
- `0x0401`: **Set Gesture Function**
  - Configures Left/Right bud, tap type (single, double, triple, hold), and assigned action (play/pause, next, prev, voice assistant, noise control, device switch, game mode).

### Dual Device Connection & Multipoint
- `0x0112` -> `0x8112`: **Query Connected Device List** (returns paired device MACs, connection state, active status, and device names)
- `0x0413`: **Handover / Switch Active Audio Device**

---

## 4. Key Takeaways for Realme TWS (AirBridge)

1. **Stream Reassembly**: RFCOMM delivers byte streams that may fragment or bundle packets. An incremental buffer with sliding window decoding (as in `OppoPodsManager/FrameCodec.cs` and `QuickBuds/OppoPacketFramer.kt`) ensures zero lost frames.
2. **Explicit Broadcast Subscriptions**: Subscribing via `0x0205` enables spontaneous notifications (`Seq = 0xFF`) for battery, wear detection, and ANC changes without polling.
3. **Safe Parameter Validation**: Hardware ignores or silently rejects malformed command payloads. Always read back states after writes to verify successful application.
