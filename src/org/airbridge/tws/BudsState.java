package org.airbridge.tws;

import java.util.ArrayList;
import java.util.List;

/**
 * Observable state model representing the real-time configuration,
 * telemetry, and connection status of the Realme Buds Air 8.
 */
public class BudsState {

    public enum ConnState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    public interface Listener {
        void onStateChanged(BudsState state);
        void onRawPacket(String direction, String hex);
    }

    // Connection Info
    public ConnState connState = ConnState.DISCONNECTED;
    public String deviceName = "Realme Buds Air 8";
    public String deviceAddress = "";
    public String firmwareVersion = "--";
    public String codecName = "--";

    // Battery & Charging (Percentages: 0-100, -1 = unknown)
    public int batteryLeft = -1;
    public int batteryRight = -1;
    public int batteryCase = -1;
    public boolean chargingLeft = false;
    public boolean chargingRight = false;
    public boolean chargingCase = false;

    // Wear Status (1 = in ear, 0 = out, -1 = unknown)
    public int wearLeft = -1;
    public int wearRight = -1;
    public int wearCase = -1;

    // ANC State
    // Mode: 1 = Normal/Off, 8 = ANC On, 2 = Transparency
    public int ancMode = 8;
    // Level: 0x10 = Smart, 4 = Max, 3 = Moderate, 2 = Mild
    public int ancLevel = 0x10;
    // Hold Cycle Mask: bit 0 = Off (1), bit 1 = ANC (2), bit 2 = Transparency (4)
    public int ancHoldCycleMask = 0x06; // Default ANC + Transparency (2 + 4)
    public boolean personalAncStored = false;

    // EQ State
    // Preset: 0 = Original, 1 = Deep Bass, 2 = Serenade, 3 = Clear Bass, 4 = Custom1
    public int eqPreset = 0;
    // 6-Band Hardware EQ Gains (-6 to +6 dB): [62Hz, 250Hz, 1kHz, 4kHz, 8kHz, 16kHz]
    public final int[] eqBands = new int[]{0, 0, 0, 0, 0, 0};

    // Hardware Feature Switches
    public boolean autoAnswer = false;
    public boolean inEarDetection = true;
    public boolean gameMode = false;
    public boolean dynamicBass = false;
    public int dynamicBassLevel = 3; // 1 to 5
    public boolean spatialAudio = true;
    public boolean windNoiseReduction = true;
    public boolean vocalEnhancement = true;
    public boolean hiResAudio = false;
    public boolean goldenSound = true;
    public boolean dualDeviceEnabled = true;

    // Connected Multipoint Devices
    public List<RealmeProtocol.DeviceInfo> devices = new ArrayList<>();

    // Gestures configuration [dev (1=L/2=R)][act (2=Double, 3=Triple, 4=Hold)] -> function ID
    public int gestureLeftDouble = 0x01; // Play/Pause
    public int gestureLeftTriple = 0x06; // Next
    public int gestureLeftHold = 0x08;   // ANC Cycle
    public int gestureRightDouble = 0x01;
    public int gestureRightTriple = 0x06;
    public int gestureRightHold = 0x08;

    // Status message for display
    public String statusText = "○ Disconnected";

    // Helper method to make a shallow copy if needed
    public synchronized void copyFrom(BudsState other) {
        this.connState = other.connState;
        this.deviceName = other.deviceName;
        this.deviceAddress = other.deviceAddress;
        this.firmwareVersion = other.firmwareVersion;
        this.codecName = other.codecName;
        this.batteryLeft = other.batteryLeft;
        this.batteryRight = other.batteryRight;
        this.batteryCase = other.batteryCase;
        this.chargingLeft = other.chargingLeft;
        this.chargingRight = other.chargingRight;
        this.chargingCase = other.chargingCase;
        this.wearLeft = other.wearLeft;
        this.wearRight = other.wearRight;
        this.wearCase = other.wearCase;
        this.ancMode = other.ancMode;
        this.ancLevel = other.ancLevel;
        this.ancHoldCycleMask = other.ancHoldCycleMask;
        this.personalAncStored = other.personalAncStored;
        this.eqPreset = other.eqPreset;
        System.arraycopy(other.eqBands, 0, this.eqBands, 0, 6);
        this.autoAnswer = other.autoAnswer;
        this.inEarDetection = other.inEarDetection;
        this.gameMode = other.gameMode;
        this.dynamicBass = other.dynamicBass;
        this.dynamicBassLevel = other.dynamicBassLevel;
        this.spatialAudio = other.spatialAudio;
        this.windNoiseReduction = other.windNoiseReduction;
        this.vocalEnhancement = other.vocalEnhancement;
        this.hiResAudio = other.hiResAudio;
        this.goldenSound = other.goldenSound;
        this.dualDeviceEnabled = other.dualDeviceEnabled;
        this.devices = new ArrayList<>(other.devices);
        this.gestureLeftDouble = other.gestureLeftDouble;
        this.gestureLeftTriple = other.gestureLeftTriple;
        this.gestureLeftHold = other.gestureLeftHold;
        this.gestureRightDouble = other.gestureRightDouble;
        this.gestureRightTriple = other.gestureRightTriple;
        this.gestureRightHold = other.gestureRightHold;
        this.statusText = other.statusText;
    }
}
