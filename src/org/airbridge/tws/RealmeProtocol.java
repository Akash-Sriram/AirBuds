package org.airbridge.tws;

import java.util.Arrays;

public class RealmeProtocol {
    private static int seq = 0;

    // Opcodes
    public static final int OP_BATTERY = 0x00;
    public static final int OP_ANC_STATE = 0x0C;
    public static final int OP_ANC_SET = 0x04;
    public static final int OP_EQ_STATE = 0x0F;
    public static final int OP_EQ_SET = 0x06;
    public static final int OP_CHARGING = 0x09;
    public static final int OP_FEATURES_STATE = 0x0D;
    public static final int OP_FEATURES_SET = 0x03;
    public static final int OP_DYNAMIC_BASS = 0x24;

    public static final int OP_WEAR = 0x09;
    public static final int OP_CODEC = 0x14;
    public static final int OP_RING_BUDS = 0x00; // Cmd 0x0400
    public static final int OP_PROMPT_VOL_QUERY = 0x30; // Cmd 0x0130
    public static final int OP_PROMPT_VOL_SET = 0x27; // Cmd 0x0427
    public static final int OP_REGISTER_NOTIFY = 0x05; // Cmd 0x0205
    public static final int OP_FIT_TEST = 0x05; // Cmd 0x0405
    public static final int OP_PERSONAL_ANC = 0x12; // Cmd 0x0412
    public static final int OP_PERSONAL_ANC_STORED = 0x1A; // Cmd 0x011A
    public static final int OP_MULTI_CONNECT_INFO = 0x12; // Cmd 0x0112 (action 0x01 / 0x81)
    public static final int OP_OPERATE_MULTI_CONNECT = 0x0B; // Cmd 0x040B (operate multi-connect)

    public static final int OP_FIRMWARE = 0x05; // Cmd 0x0105 — firmware version query
    public static final int OP_KEY_FUNCTION = 0x08; // Cmd 0x0108 / 0x0401 — key function bindings
    public static final int OP_CUSTOM_EQ_LIST = 0x22; // Cmd 0x0122 — list custom presets
    public static final int OP_CUSTOM_EQ_SAVE = 0x18; // Cmd 0x0418 — save/select custom preset
    public static final int OP_SPATIAL_TYPE = 0x2A; // Cmd 0x012A — read spatial mode
    public static final int OP_SPATIAL_TYPE_SET = 0x22; // Cmd 0x0422 — set spatial mode (action=0x04)

    // Feature IDs
    public static final int FEAT_AUTO_ANSWER = 5;
    public static final int FEAT_IN_EAR = 4;
    public static final int FEAT_GAME_MODE = 6;
    public static final int FEAT_VOCAL_ENHANCE = 9;
    public static final int FEAT_GOLDEN_SOUND = 11;
    public static final int FEAT_PERSONAL_ANC = 12;
    public static final int FEAT_HI_RES = 24;
    public static final int FEAT_WIND_NOISE = 26;
    public static final int FEAT_SPATIAL_AUDIO = 27;
    public static final int FEAT_MULTI_DEVICE = 17;
    public static final int FEAT_DYNAMIC_BASS = 29;

    public static class DeviceInfo {
        public final String name;
        public final byte[] mac;
        public final int connState;
        public final int activeState;
        public final boolean isConnected;
        public final boolean isAudioActive;
        public final boolean isCurrent;
        public final boolean isPriority;
        public final boolean isActive;

        public DeviceInfo(String name, byte[] mac, int connState, int activeState) {
            this.name = name;
            this.mac = mac;
            this.connState = connState;
            this.activeState = activeState;
            this.isConnected = (connState > 0);
            this.isCurrent = (activeState & 0x01) != 0;
            this.isPriority = (activeState & 0x02) != 0;
            this.isAudioActive = (activeState & 0x04) != 0;
            this.isActive = isAudioActive || isCurrent;
        }

        public String getMacString() {
            if (mac == null || mac.length < 6) return "";
            return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                    mac[5] & 0xFF, mac[4] & 0xFF, mac[3] & 0xFF,
                    mac[2] & 0xFF, mac[1] & 0xFF, mac[0] & 0xFF);
        }
    }

    public interface Listener {
        void onBatteryUpdate(int left, int right, int box);
        void onChargingUpdate(int leftState, int rightState, int boxState);
        void onWearStatusUpdate(int leftStatus, int rightStatus, int boxStatus);
        void onCodecUpdate(int codecId, String codecName);
        void onPromptVolumeUpdate(int volumeLevel);
        void onFitTestResult(int leftStatus, int rightStatus);
        void onPersonalAncResult(int result);
        void onPersonalAncAck(int status);
        void onPersonalAncStoredResult(boolean exists);
        void onAncModeUpdate(int mode); // 1 = Normal, 8 = ANC On, 2 = Transparency
        void onAncLevelUpdate(int level); // 0x10 = Smart, 4 = Max, 3 = Moderate, 2 = Mild
        void onEqPresetUpdate(int preset); // 0 = Original, 1 = Deep Bass, 2 = Serenade, 3 = Clear Bass
        void onFeatureUpdate(int featureId, int value);
        void onConnectedDevicesUpdate(java.util.List<DeviceInfo> devices);
        void onDeviceRoutingChanged();
        void onFirmwareUpdate(String version); // parsed from 0x8105
        void onSpatialTypeUpdate(int type); // 0=off, 1=fixed, 2=cinema from 0x812A
        void onAncHoldCycleUpdate(int mask); // bit 0: Off, bit 1: ANC, bit 2: Transparency
        void onKeyFunctionUpdate(int dev, int act, int fn); // dev 1=L/2=R, act 2=Double/3=Triple/4=Hold, fn=actionId
        void onRawPacket(String direction, String hex);
    }

    public static byte[] makePacket(int opcode, int action, byte[] payload) {
        int len = payload == null ? 0 : payload.length;
        byte[] p = new byte[len + 9];
        p[0] = (byte) 0xAA;
        p[1] = (byte) (len + 7);
        p[2] = 0x00;
        p[3] = 0x00;
        p[4] = (byte) opcode;
        p[5] = (byte) action;
        p[6] = (byte) (seq++ & 0xFF);
        p[7] = (byte) (len & 0xFF);
        p[8] = (byte) ((len >> 8) & 0xFF);
        if (len > 0) {
            System.arraycopy(payload, 0, p, 9, len);
        }
        return p;
    }

    public static String toHex(byte[] data, int len) {
        if (data == null || len <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", data[i]));
        }
        return sb.toString().trim();
    }

    public static void parseStream(byte[] packet, int len, Listener listener) {
        if (len < 9 || (packet[0] & 0xFF) != 0xAA) return;

        int headerLen = 9;
        int opcode;
        int action;
        int payloadLen;

        if (len > 9 && (packet[2] & 0xFF) != 0 && packet[3] == 0 && packet[4] == 0) {
            headerLen = 10;
            opcode = packet[5] & 0xFF;
            action = packet[6] & 0xFF;
            payloadLen = (packet[8] & 0xFF) | ((packet[9] & 0xFF) << 8);
        } else {
            opcode = packet[4] & 0xFF;
            action = packet[5] & 0xFF;
            payloadLen = (packet[7] & 0xFF) | ((packet[8] & 0xFF) << 8);
        }

        byte[] payload = new byte[Math.min(payloadLen, len - headerLen)];
        if (payload.length > 0) {
            System.arraycopy(packet, headerLen, payload, 0, payload.length);
        }

        if (listener != null) {
            listener.onRawPacket("RX", toHex(packet, len));
        }

        if (opcode == OP_BATTERY && (action & 0xF0) == 0x80 && payload.length >= 5) {
            int left = payload[2] & 0x7F;
            int right = payload[3] & 0x7F;
            int box = payload[4] & 0x7F;
            if (listener != null) {
                listener.onBatteryUpdate(left, right, box);
            }
        } else if (opcode == 0x04 && action == 0x02 && payload.length >= 8 && payload[0] == 0x01) {
            // Air 8 battery format: 01 03 01 [L] 02 [R] 03 [Case]
            int left = payload[3] & 0x7F;
            int right = payload[5] & 0x7F;
            int box = payload[7] & 0x7F;
            if (listener != null) {
                listener.onBatteryUpdate(left, right, box);
            }
        } else if (opcode == 0x04 && action == 0x02 && payload.length >= 8 && payload[0] == 0x02) {
            // Air 8 wear & charging format: 02 03 01 [L_state] 02 [R_state] 03 [Box_state]
            int leftState = payload[3] & 0xFF;
            int rightState = payload[5] & 0xFF;
            int boxState = payload[7] & 0xFF;
            if (listener != null) {
                listener.onChargingUpdate(leftState, rightState, boxState);
                listener.onWearStatusUpdate(leftState, rightState, boxState);
            }
        } else if ((opcode == OP_MULTI_CONNECT_INFO || (opcode == 0x04 && payload.length >= 2 && (payload[0] & 0xFF) == 0x06)) && payload.length >= 2) {
            int devCount;
            int idx;
            if (opcode == OP_MULTI_CONNECT_INFO) {
                if ((action & 0xF0) == 0x80) { // Response: 00 <count> ...
                    if (payload[0] != 0 && payload.length < 2) return;
                    devCount = payload[1] & 0xFF;
                    idx = 2;
                } else { // Notification: <count> ...
                    devCount = payload[0] & 0xFF;
                    idx = 1;
                }
            } else {
                // Opcode 0x04 SubType 0x06
                devCount = payload[1] & 0xFF;
                idx = 2;
            }

            java.util.List<DeviceInfo> devList = new java.util.ArrayList<>();
            while (devList.size() < devCount && idx + 8 < payload.length) {
                byte[] mac = new byte[6];
                System.arraycopy(payload, idx, mac, 0, 6);
                idx += 6;

                if (idx + 3 >= payload.length) break;
                int elemLen = payload[idx++] & 0xFF;
                int entryEnd = idx + elemLen;
                int connState = payload[idx++] & 0xFF;
                int activeState = payload[idx++] & 0xFF;
                int nameLen = payload[idx++] & 0xFF;

                String name = "";
                if (nameLen > 0 && idx + nameLen <= payload.length) {
                    try {
                        name = new String(payload, idx, nameLen, "UTF-8").trim().replace("\0", "");
                    } catch (Exception ignored) {
                        name = new String(payload, idx, nameLen).trim().replace("\0", "");
                    }
                }
                devList.add(new DeviceInfo(name, mac, connState, activeState));
                idx = (elemLen > 0 && entryEnd <= payload.length) ? entryEnd : (idx + Math.max(0, nameLen));
            }
            if (listener != null) {
                listener.onConnectedDevicesUpdate(devList);
            }
        } else if (opcode == 0x04 && (action == 0x02 || action == 0x05 || (action & 0xF0) == 0x80) && payload.length >= 2) {
            int subType = payload[0] & 0xFF;
            if (subType == 0x04 && payload.length >= 5) {
                // Fit test result: 04 01 [L_fit] 02 [R_fit]
                int leftFit = payload[2] & 0xFF;
                int rightFit = payload[4] & 0xFF;
                if (listener != null) {
                    listener.onFitTestResult(leftFit, rightFit);
                }
            } else if (subType == 0x0B && payload.length >= 2) {
                // Personalised ANC result: 0B <result>
                int res = payload[1] & 0xFF;
                if (listener != null) {
                    listener.onPersonalAncResult(res);
                }
            } else if (subType == 0xF2) {
                // Audio routing handover broadcast from earbuds
                if (listener != null) {
                    listener.onDeviceRoutingChanged();
                }
            }
        } else if ((action & 0xF0) == 0x80) {
            if (opcode == OP_ANC_STATE && payload.length >= 4) {
                int group = payload[1] & 0xFF;
                int subType = payload[2] & 0xFF;
                int val = payload[3] & 0xFF;
                if (listener != null) {
                    if (group == 0x02 && subType == 0x01) {
                        listener.onAncHoldCycleUpdate(val);
                    } else if (subType == 0x02) {
                        listener.onAncLevelUpdate(val);
                    } else {
                        listener.onAncModeUpdate(val);
                    }
                }
            } else if (opcode == OP_KEY_FUNCTION && payload.length >= 2) {
                int count = payload[1] & 0xFF;
                int idx = 2;
                while (idx + 4 <= payload.length) {
                    int dev = payload[idx] & 0xFF;
                    int act = payload[idx + 2] & 0xFF;
                    int fn = payload[idx + 3] & 0xFF;
                    if (listener != null) {
                        listener.onKeyFunctionUpdate(dev, act, fn);
                    }
                    idx += 4;
                }
            } else if (opcode == OP_EQ_STATE && payload.length >= 2) {
                int preset = payload[1] & 0xFF;
                if (listener != null) {
                    listener.onEqPresetUpdate(preset);
                }
            } else if (opcode == OP_CHARGING && payload.length >= 8) {
                // Charging status format: 00 03 01 [L] 02 [R] 03 [Box]
                int leftState = payload[3] & 0xFF;
                int rightState = payload[5] & 0xFF;
                int boxState = payload[7] & 0xFF;
                if (listener != null) {
                    listener.onChargingUpdate(leftState, rightState, boxState);
                }
            } else if (opcode == OP_WEAR && payload.length >= 6) {
                // Wearing status query reply format
                int off = (payload[0] == 0) ? 1 : 0;
                if (payload.length >= off + 6) {
                    int l = payload[off + 2] & 0xFF;
                    int r = payload[off + 4] & 0xFF;
                    int b = (payload.length >= off + 6) ? (payload[off + 6] & 0xFF) : 0;
                    if (listener != null) {
                        listener.onWearStatusUpdate(l, r, b);
                    }
                }
            } else if (opcode == OP_CODEC && payload.length >= 1) {
                int codecId = (payload.length >= 2 && payload[0] == 0) ? (payload[1] & 0xFF) : (payload[0] & 0xFF);
                String name = "SBC";
                switch (codecId) {
                    case 8: name = "LHDC 5.0"; break;
                    case 7: name = "LHDC"; break;
                    case 6: name = "aptX Adaptive"; break;
                    case 5: name = "aptX HD"; break;
                    case 4: name = "aptX"; break;
                    case 3: name = "LDAC"; break;
                    case 2: name = "AAC"; break;
                    default: name = "SBC"; break;
                }
                if (listener != null) {
                    listener.onCodecUpdate(codecId, name);
                }
            } else if (opcode == OP_PROMPT_VOL_QUERY && payload.length >= 1) {
                int vol = (payload.length >= 2 && payload[0] == 0) ? (payload[1] & 0xFF) : (payload[0] & 0xFF);
                if (listener != null) {
                    listener.onPromptVolumeUpdate(vol);
                }
            } else if (opcode == OP_PROMPT_VOL_SET && payload.length >= 2) {
                int vol = payload[1] & 0xFF;
                if (listener != null) {
                    listener.onPromptVolumeUpdate(vol);
                }
            } else if (opcode == OP_PERSONAL_ANC && payload.length >= 1) {
                int status = payload[0] & 0xFF;
                if (listener != null) {
                    listener.onPersonalAncAck(status);
                }
            } else if (opcode == OP_PERSONAL_ANC_STORED && payload.length >= 1) {
                boolean exists = ((payload.length >= 2 ? payload[1] : payload[0]) & 0xFF) != 0;
                if (listener != null) {
                    listener.onPersonalAncStoredResult(exists);
                }
            } else if (opcode == OP_FEATURES_STATE && payload.length >= 2) {
                int count = payload[1] & 0xFF;
                for (int i = 0; i < count && (2 + i * 2 + 1) < payload.length; i++) {
                    int featId = payload[2 + i * 2] & 0xFF;
                    int featVal = payload[3 + i * 2] & 0xFF;
                    if (listener != null) {
                        listener.onFeatureUpdate(featId, featVal);
                    }
                }
            } else if (opcode == OP_FIRMWARE && action == 0x81 && payload.length >= 3 && payload[0] == 0) {
                // 0x8105: 00 <count> <text> — version triples "devType,verType,ver,..."
                try {
                    String text = new String(payload, 2, payload.length - 2, "UTF-8");
                    String[] parts = text.split(",");
                    String ver = "";
                    int k = 0;
                    while (k + 2 < parts.length) {
                        if (parts[k + 1].trim().equals("2")) {
                            String candidate = parts[k + 2].trim();
                            if (candidate.contains(".")) {
                                ver = candidate;
                                break;
                            }
                        }
                        k += 3;
                    }
                    if (ver.isEmpty() && parts.length >= 3) {
                        ver = parts[2].trim();
                    }
                    if (!ver.isEmpty() && listener != null) {
                        listener.onFirmwareUpdate(ver);
                    }
                } catch (Exception ignored) {}
            } else if (opcode == OP_SPATIAL_TYPE && payload.length >= 2 && payload[0] == 0) {
                // 0x812A: 00 <type>  0=off, 1=fixed, 2=cinema
                int type = payload[1] & 0xFF;
                if (listener != null) {
                    listener.onSpatialTypeUpdate(type);
                }
            }
        }
    }

    public static byte[] buildWearQuery() {
        return makePacket(OP_WEAR, 0x01, null);
    }

    public static byte[] buildCodecQuery() {
        return makePacket(OP_CODEC, 0x01, null);
    }

    public static byte[] buildRingBudsCommand(boolean start) {
        return makePacket(OP_RING_BUDS, 0x04, new byte[]{ (byte) (start ? 0x01 : 0x00) });
    }

    public static byte[] buildPromptVolumeQuery() {
        return makePacket(OP_PROMPT_VOL_QUERY, 0x01, null);
    }

    public static byte[] buildPromptVolumeCommand(int level) {
        int l = Math.max(1, Math.min(10, level));
        return makePacket(OP_PROMPT_VOL_SET, 0x04, new byte[]{ (byte) l });
    }

    public static byte[] buildFitTestCommand(boolean start) {
        return makePacket(OP_FIT_TEST, 0x04, new byte[]{ (byte) (start ? 0x01 : 0x00) });
    }

    public static byte[] buildPersonalAncCommand(int action) {
        return makePacket(OP_PERSONAL_ANC, 0x04, new byte[]{ (byte) action });
    }

    public static byte[] buildPersonalAncStoredQuery() {
        return makePacket(OP_PERSONAL_ANC_STORED, 0x01, null);
    }
}
