package org.airbridge.tws;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelUuid;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Foreground Service managing the continuous Bluetooth RFCOMM / SPP session
 * with Realme Buds Air 8. Decoupled from the Activity lifecycle to allow persistent
 * background telemetry, quick settings control, and automatic OS reconnects.
 */
public class BudsService extends Service implements RealmeProtocol.Listener {

    private static final String TAG = "AirBridgeService";
    private static final String CHANNEL_ID = "airbridge_status_channel";
    private static final int NOTIF_ID = 1001;

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final UUID REALME_UUID = UUID.fromString("df21fe2c-2515-4fdb-8886-f12c4d67927c");
    private static final ParcelUuid FAST_PAIR_SERVICE_UUID = ParcelUuid.fromString("0000FE2C-0000-1000-8000-00805F9B34FB");

    private BluetoothLeScanner mBleScanner;
    private boolean mIsScanning = false;

    private final IBinder mBinder = new LocalBinder();
    private final CopyOnWriteArrayList<BudsState.Listener> mListeners = new CopyOnWriteArrayList<>();
    private final BudsState mState = new BudsState();
    private final BudsFramer mFramer = new BudsFramer();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private BluetoothSocket mSocket;
    private InputStream mInStream;
    private OutputStream mOutStream;
    private volatile boolean mRunning = false;
    private volatile boolean mIsConnecting = false;
    private Thread mWorkerThread;
    private BluetoothDevice mTargetDevice;

    public class LocalBinder extends Binder {
        public BudsService getService() {
            return BudsService.this;
        }
    }

    private boolean mIsForeground = false;
    private long mLastNotifUpdate = 0;
    private final Runnable mThrottledNotifRunnable = this::performUpdateNotification;
    private boolean mShowNotification = true;

    public boolean isShowNotification() {
        return mShowNotification;
    }

    public void setShowNotification(boolean show) {
        mShowNotification = show;
        getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("pref_show_notification", show)
            .apply();
        updateNotification();
    }

    public boolean hasBondedBuds() {
        BluetoothManager bm = getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
        if (adapter == null) return false;
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice dev : bonded) {
                    String name = dev.getName();
                    if (name != null) {
                        String lower = name.toLowerCase();
                        if (lower.contains("air8") || lower.contains("air 8") || lower.contains("rma2503") || lower.contains("realme")) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mShowNotification = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .getBoolean("pref_show_notification", true);
        String lastFw = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .getString("last_known_firmware", "1.1.0.104");
        mState.firmwareVersion = lastFw;
        String lastMac = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .getString("last_known_mac", "60:55:56:F9:98:FD");
        mState.deviceAddress = lastMac;
        createNotificationChannel();

        // Register system Bluetooth connect / disconnect / A2DP receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED);
        registerReceiver(mBtReceiver, filter);

        // Initiate connection to earbuds
        connectToEarbuds();

        // Start background BLE scanner for case battery detection
        startBleFastPairScanner();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (mShowNotification && mState.connState == BudsState.ConnState.CONNECTED && !mIsForeground) {
            performUpdateNotification();
        }
        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_RECONNECT".equals(action)) {
                reconnect();
            } else if ("ACTION_SET_ANC".equals(action)) {
                int mode = intent.getIntExtra("EXTRA_ANC_MODE", -1);
                if (mode != -1) {
                    setAncMode(mode);
                }
            }
        }
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public BudsState getState() {
        return mState;
    }

    public void addListener(BudsState.Listener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            listener.onStateChanged(mState);
        }
    }

    public void removeListener(BudsState.Listener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    private void notifyStateChanged() {
        mMainHandler.post(() -> {
            updateNotification();
            for (BudsState.Listener l : mListeners) {
                l.onStateChanged(mState);
            }
        });
    }

    private void logPacket(String dir, String hex) {
        Log.d(TAG, "[" + dir + "] " + hex);
        mMainHandler.post(() -> {
            for (BudsState.Listener l : mListeners) {
                l.onRawPacket(dir, hex);
            }
        });
    }

    // ------------------------------------------------------------------------
    // Connection Lifecycle
    // ------------------------------------------------------------------------

    public synchronized void reconnect() {
        disconnectInternal();
        connectToEarbuds();
    }

    public synchronized void connectToEarbuds() {
        if (mRunning || mIsConnecting) {
            return;
        }
        mIsConnecting = true;
        mState.connState = BudsState.ConnState.CONNECTING;
        mState.statusText = "Connecting...";
        notifyStateChanged();

        mWorkerThread = new Thread(() -> {
            try {
                BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
                if (adapter == null || !adapter.isEnabled()) {
                    onConnectFailed("Bluetooth Disabled");
                    return;
                }

                Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                BluetoothDevice target = null;
                if (bonded != null) {
                    for (BluetoothDevice dev : bonded) {
                        String name = dev.getName();
                        if (name != null && name.toLowerCase().contains("air8")) {
                            target = dev;
                            break;
                        }
                    }
                    if (target == null) {
                        for (BluetoothDevice dev : bonded) {
                            String name = dev.getName();
                            if (name != null && name.toLowerCase().contains("realme")) {
                                target = dev;
                                break;
                            }
                        }
                    }
                }

                if (target == null) {
                    onConnectFailed("No Buds Found");
                    return;
                }

                mTargetDevice = target;
                mState.deviceName = target.getName() != null ? target.getName() : "Realme Buds Air 8";
                mState.deviceAddress = target.getAddress();

                // Connect to system profile if not already connected
                try {
                    Method isConnMethod = target.getClass().getMethod("isConnected");
                    boolean alreadyConn = (boolean) isConnMethod.invoke(target);
                    if (!alreadyConn) {
                        Method m = target.getClass().getMethod("connect");
                        m.invoke(target);
                        logPacket("SYS", "Triggered Android OS connect to " + target.getName());
                        Thread.sleep(600);
                    }
                } catch (Exception ignored) {}

                BluetoothSocket socket = null;
                for (int attempt = 1; attempt <= 2 && socket == null; attempt++) {
                    if (attempt > 1) {
                        logPacket("SYS", "Retrying RFCOMM socket in 800ms (attempt 2)...");
                        try { Thread.sleep(800); } catch (Exception ignored) {}
                    }

                    // Attempt 1: Standard SPP UUID
                    try {
                        logPacket("SYS", "Connecting via SPP UUID: " + SPP_UUID);
                        socket = target.createRfcommSocketToServiceRecord(SPP_UUID);
                        socket.connect();
                        logPacket("SYS", "Connected via SPP UUID!");
                        break;
                    } catch (Exception e1) {
                        logPacket("SYS", "SPP UUID failed: " + e1.getMessage());
                    }

                    // Attempt 2: Realme specific UUID
                    try {
                        logPacket("SYS", "Connecting via Realme UUID: " + REALME_UUID);
                        socket = target.createRfcommSocketToServiceRecord(REALME_UUID);
                        socket.connect();
                        logPacket("SYS", "Connected via Realme UUID!");
                        break;
                    } catch (Exception e2) {
                        logPacket("SYS", "Realme UUID failed: " + e2.getMessage());
                    }

                    // Attempt 3: Reflection on channel 17
                    try {
                        logPacket("SYS", "Connecting via reflection on channel 17...");
                        Method m = target.getClass().getMethod("createRfcommSocket", new Class[]{int.class});
                        socket = (BluetoothSocket) m.invoke(target, 17);
                        socket.connect();
                        logPacket("SYS", "Connected on channel 17!");
                        break;
                    } catch (Exception e3) {
                        logPacket("SYS", "Channel 17 failed: " + e3.getMessage());
                    }
                }

                if (socket == null) {
                    onConnectFailed("Conn Failed");
                    return;
                }

                mSocket = socket;
                mInStream = socket.getInputStream();
                mOutStream = socket.getOutputStream();
                mRunning = true;
                mFramer.reset();

                mState.connState = BudsState.ConnState.CONNECTED;
                mState.statusText = "● Connected";
                stopBleFastPairScanner();
                notifyStateChanged();

                // Start dedicated packet reading thread
                new Thread(this::readerLoop).start();

                // Send hardware-verified initialization query chain
                sendInitialQueries();

            } catch (Exception e) {
                Log.e(TAG, "Connection error", e);
                onConnectFailed("Error: " + e.getMessage());
            } finally {
                mIsConnecting = false;
            }
        });
        mWorkerThread.start();
    }

    private void onConnectFailed(String reason) {
        mRunning = false;
        mState.connState = BudsState.ConnState.DISCONNECTED;
        mState.statusText = reason;
        notifyStateChanged();
        disconnectInternal();
    }

    private synchronized void disconnectInternal() {
        mMainHandler.removeCallbacks(mDeviceQueryRunnable);
        mRunning = false;
        mIsConnecting = false;
        try {
            if (mInStream != null) mInStream.close();
            if (mOutStream != null) mOutStream.close();
            if (mSocket != null) mSocket.close();
        } catch (Exception ignored) {}
        mSocket = null;
        mInStream = null;
        mOutStream = null;
    }

    private void onDisconnected() {
        disconnectInternal();
        mState.connState = BudsState.ConnState.DISCONNECTED;
        mState.statusText = "○ Disconnected";
        if (mIsForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
            } catch (Exception ignored) {}
            mIsForeground = false;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIF_ID);
        }
        startBleFastPairScanner();
        notifyStateChanged();
    }

    private void readerLoop() {
        byte[] buffer = new byte[1024];

        while (mRunning && mInStream != null) {
            try {
                int read = mInStream.read(buffer);
                if (read <= 0) break;

                List<byte[]> frames = mFramer.append(buffer, read);
                for (byte[] frame : frames) {
                    RealmeProtocol.parseStream(frame, frame.length, this);
                }
            } catch (Exception e) {
                if (mRunning) {
                    logPacket("ERR", "Read error: " + e.getMessage());
                    onDisconnected();
                }
                break;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Verified Initialization Queries
    // ------------------------------------------------------------------------

    private void sendInitialQueries() {
        try {
            Thread.sleep(100);
            sendCmd(RealmeProtocol.OP_BATTERY, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_ANC_STATE, 0x01, new byte[]{0x01, 0x01});
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_ANC_STATE, 0x01, new byte[]{0x01, 0x02});
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_EQ_STATE, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_FEATURES_STATE, 0x01, new byte[]{
                16, 7, 6, 4, 5, 11, 8, 17, 19, 24, 9, 26, 12, 29, 27, 54, 38
            });
            Thread.sleep(80);
            queryConnectedDevices();
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_CHARGING, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_WEAR, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_CODEC, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_PERSONAL_ANC_STORED, 0x01, new byte[0]);
            Thread.sleep(80);
            // Register notifications: Battery, Wear, ANC, Fit, Game Mode (0x05), Multi-Connect (0x06), Spatial (0x08), Personal ANC (0x0B), Dual Conn (0x0D), Spatial State (0x0F), Codec (0x10)
            sendCmd(RealmeProtocol.OP_REGISTER_NOTIFY, 0x02, new byte[]{ 11, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x08, 0x0B, 0x0D, 0x0F, 0x10 });
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_FIRMWARE, 0x01, new byte[0]);
            Thread.sleep(80);
            sendCmd(RealmeProtocol.OP_ANC_STATE, 0x01, new byte[]{0x02, 0x01});
            mMainHandler.postDelayed(this::queryConnectedDevices, 1500);
        } catch (Exception e) {
            Log.e(TAG, "Error sending queries", e);
        }
    }

    // ------------------------------------------------------------------------
    // Command Dispatcher
    // ------------------------------------------------------------------------

    public synchronized void sendCmd(int opcode, int action, byte[] payload) {
        if (!mRunning || mOutStream == null) return;
        byte[] packet = RealmeProtocol.makePacket(opcode, action, payload);
        logPacket("TX", RealmeProtocol.toHex(packet, packet.length));
        try {
            mOutStream.write(packet);
            mOutStream.flush();
        } catch (Exception e) {
            logPacket("ERR", "Write failed: " + e.getMessage());
        }
    }

    public void setAncMode(int mode) {
        mState.ancMode = mode;
        sendCmd(RealmeProtocol.OP_ANC_SET, 0x04, new byte[]{0x01, 0x01, (byte) mode});
        notifyStateChanged();
    }

    public void setAncLevel(int level) {
        mState.ancLevel = level;
        sendCmd(RealmeProtocol.OP_ANC_SET, 0x04, new byte[]{0x01, 0x02, (byte) level});
        notifyStateChanged();
    }

    public void setAncHoldCycle(int mask) {
        mState.ancHoldCycleMask = mask;
        sendCmd(RealmeProtocol.OP_ANC_SET, 0x04, new byte[]{0x02, 0x01, (byte) mask});
        notifyStateChanged();
    }

    public void setEqPreset(int preset) {
        mState.eqPreset = preset;
        sendCmd(RealmeProtocol.OP_EQ_SET, 0x04, new byte[]{(byte) preset});
        notifyStateChanged();
    }

    public void applyCustomEq(int[] bands) {
        if (bands != null && bands.length == 6) {
            System.arraycopy(bands, 0, mState.eqBands, 0, 6);
        }
        // Custom EQ: 0x0418 payload: 01 06 [b1..b6] 04
        byte[] payload = new byte[9];
        payload[0] = 0x01; // Preset id: Custom 1
        payload[1] = 0x06; // Band count
        for (int i = 0; i < 6; i++) {
            payload[2 + i] = (byte) mState.eqBands[i];
        }
        payload[8] = 0x04; // Select preset 4 (Custom1)
        sendCmd(RealmeProtocol.OP_CUSTOM_EQ_SAVE, 0x04, payload);
        mState.eqPreset = 4;
        notifyStateChanged();
    }

    public void setFeature(int featureId, int value) {
        sendCmd(RealmeProtocol.OP_FEATURES_SET, 0x04, new byte[]{(byte) featureId, (byte) value});
        // Optimistically update state
        updateFeatureState(featureId, value);
        notifyStateChanged();
    }

    public void setDynamicBass(boolean enabled, int level) {
        mState.dynamicBass = enabled;
        mState.dynamicBassLevel = level;
        sendCmd(RealmeProtocol.OP_FEATURES_SET, 0x04, new byte[]{(byte) RealmeProtocol.FEAT_DYNAMIC_BASS, (byte) (enabled ? 1 : 0)});
        if (enabled) {
            sendCmd(RealmeProtocol.OP_DYNAMIC_BASS, 0x04, new byte[]{(byte) level});
        }
        notifyStateChanged();
    }

    public void setGesture(int dev, int act, int fn) {
        byte[] payload = new byte[]{
            0x01, // Count
            (byte) dev,
            0x01, // Button 1
            (byte) act,
            (byte) fn
        };
        sendCmd(RealmeProtocol.OP_KEY_FUNCTION, 0x04, payload);
    }

    private final Runnable mDeviceQueryRunnable = new Runnable() {
        @Override
        public void run() {
            if (mRunning && mState.connState == BudsState.ConnState.CONNECTED) {
                queryConnectedDevices();
            }
        }
    };

    public void queryConnectedDevices() {
        sendCmd(RealmeProtocol.OP_MULTI_CONNECT_INFO, 0x01, new byte[0]); // Cmd 0x0112: Query Multi-Connect Devices
        sendCmd(RealmeProtocol.OP_PREFERRED_DEVICE, 0x01, new byte[]{0x02}); // Cmd 0x0132 02: Query Preferred Device
        checkLocalAudioState();
    }

    private void checkLocalAudioState() {
        try {
            android.media.AudioManager am = (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
            boolean isPlaying = (am != null && (am.isMusicActive() || am.getMode() == android.media.AudioManager.MODE_IN_CALL || am.getMode() == android.media.AudioManager.MODE_IN_COMMUNICATION));
            if (mState.isLocalA2dpPlaying != isPlaying) {
                mState.isLocalA2dpPlaying = isPlaying;
                notifyStateChanged();
            }
        } catch (Exception ignored) {}
    }
    public boolean isThisPhone(String devName) {
        if (devName == null) return false;
        String curDevName = Settings.Global.getString(getContentResolver(), "device_name");
        String btName = null;
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null) btName = adapter.getName();
        } catch (SecurityException ignored) {}

        String lower = devName.toLowerCase();
        if (lower.contains("poco") || lower.contains("pocophone")) return true;
        if (curDevName != null && devName.equalsIgnoreCase(curDevName)) return true;
        if (btName != null && devName.equalsIgnoreCase(btName)) return true;
        return devName.equalsIgnoreCase(Build.MODEL);
    }

    public void disconnectThisPhone() {
        Log.d(TAG, "disconnectThisPhone: Disconnecting local phone from earbuds");
        BluetoothDevice target = mTargetDevice;
        if (target == null) {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
            if (adapter != null) {
                try {
                    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    if (bonded != null) {
                        for (BluetoothDevice dev : bonded) {
                            String name = dev.getName();
                            if (name != null && (name.toLowerCase().contains("air8") || name.toLowerCase().contains("realme"))) {
                                target = dev;
                                break;
                            }
                        }
                    }
                } catch (SecurityException ignored) {}
            }
        }
        disconnectInternal();
        onDisconnected();
        if (target != null) {
            try {
                Method m = target.getClass().getMethod("disconnect");
                m.invoke(target);
            } catch (Exception e) {
                Log.w(TAG, "BluetoothDevice.disconnect reflection failed", e);
            }
        }
    }

    public void unpairThisPhone() {
        Log.d(TAG, "unpairThisPhone: Removing bond from this phone");
        BluetoothDevice target = mTargetDevice;
        if (target == null) {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
            if (adapter != null) {
                try {
                    Set<BluetoothDevice> bonded = adapter.getBondedDevices();
                    if (bonded != null) {
                        for (BluetoothDevice dev : bonded) {
                            String name = dev.getName();
                            if (name != null && (name.toLowerCase().contains("air8") || name.toLowerCase().contains("realme"))) {
                                target = dev;
                                break;
                            }
                        }
                    }
                } catch (SecurityException ignored) {}
            }
        }
        disconnectInternal();
        onDisconnected();
        if (target != null) {
            try {
                Method m = target.getClass().getMethod("removeBond");
                m.invoke(target);
            } catch (Exception e) {
                Log.w(TAG, "BluetoothDevice.removeBond reflection failed", e);
            }
        }
    }

    public void ringBuds(boolean ring) {
        byte[] payload = new byte[]{(byte) (ring ? 3 : 0)}; // 3 = Ring both L & R
        sendCmd(RealmeProtocol.OP_RING_BUDS, 0x04, payload);
    }

    public void startFitTest() {
        sendCmd(RealmeProtocol.OP_FIT_TEST, 0x04, new byte[]{0x01});
    }

    public void stopFitTest() {
        sendCmd(RealmeProtocol.OP_FIT_TEST, 0x04, new byte[]{0x02});
    }

    private void updateFeatureState(int featureId, int value) {
        boolean on = (value == 1);
        switch (featureId) {
            case RealmeProtocol.FEAT_AUTO_ANSWER: mState.autoAnswer = on; break;
            case RealmeProtocol.FEAT_IN_EAR: mState.inEarDetection = on; break;
            case RealmeProtocol.FEAT_GAME_MODE: mState.gameMode = on; break;
            case RealmeProtocol.FEAT_DYNAMIC_BASS: mState.dynamicBass = on; break;
            case RealmeProtocol.FEAT_SPATIAL_AUDIO: mState.spatialAudio = on; break;
            case RealmeProtocol.FEAT_WIND_NOISE: mState.windNoiseReduction = on; break;
            case RealmeProtocol.FEAT_VOCAL_ENHANCE: mState.vocalEnhancement = on; break;
            case RealmeProtocol.FEAT_HI_RES: mState.hiResAudio = on; break;
            case RealmeProtocol.FEAT_GOLDEN_SOUND: mState.goldenSound = on; break;
            case RealmeProtocol.FEAT_MULTI_DEVICE: mState.dualDeviceEnabled = on; break;
        }
    }

    // ------------------------------------------------------------------------
    // RealmeProtocol.Listener Implementation
    // ------------------------------------------------------------------------

    @Override
    public void onBatteryUpdate(int left, int right, int box) {
        mState.batteryLeft = left;
        mState.batteryRight = right;
        mState.batteryCase = box;
        notifyStateChanged();
    }

    @Override
    public void onChargingUpdate(int leftState, int rightState, int boxState) {
        mState.chargingLeft = (leftState == 1);
        mState.chargingRight = (rightState == 1);
        mState.chargingCase = (boxState == 1);
        notifyStateChanged();
    }

    @Override
    public void onWearStatusUpdate(int leftStatus, int rightStatus, int boxStatus) {
        mState.wearLeft = leftStatus;
        mState.wearRight = rightStatus;
        mState.wearCase = boxStatus;
        notifyStateChanged();
    }

    @Override
    public void onCodecUpdate(int codecId, String codecName) {
        mState.codecName = codecName;
        notifyStateChanged();
    }

    @Override
    public void onPromptVolumeUpdate(int volumeLevel) {}

    @Override
    public void onFitTestResult(int leftStatus, int rightStatus) {}

    @Override
    public void onPersonalAncResult(int result) {}

    @Override
    public void onPersonalAncAck(int status) {}

    @Override
    public void onPersonalAncStoredResult(boolean exists) {
        mState.personalAncStored = exists;
        notifyStateChanged();
    }

    @Override
    public void onAncModeUpdate(int mode) {
        mState.ancMode = mode;
        notifyStateChanged();
    }

    @Override
    public void onAncLevelUpdate(int level) {
        mState.ancLevel = level;
        notifyStateChanged();
    }

    @Override
    public void onEqPresetUpdate(int preset) {
        mState.eqPreset = preset;
        notifyStateChanged();
    }

    @Override
    public void onFeatureUpdate(int featureId, int value) {
        updateFeatureState(featureId, value);
        notifyStateChanged();
    }

    @Override
    public void onConnectedDevicesUpdate(List<RealmeProtocol.DeviceInfo> devices) {
        StringBuilder sb = new StringBuilder();
        for (RealmeProtocol.DeviceInfo d : devices) {
            sb.append(d.name).append("[conn=").append(d.isConnected)
              .append(",cur=").append(d.isCurrent)
              .append(",audio=").append(d.isAudioActive).append("] ");
        }
        Log.d(TAG, "onConnectedDevicesUpdate: " + sb.toString());
        mState.devices = new ArrayList<>(devices);
        notifyStateChanged();
    }

    @Override
    public void onPreferredDeviceUpdate(String mac, boolean isAuto) {
        Log.d(TAG, "onPreferredDeviceUpdate: mac=" + mac + ", isAuto=" + isAuto);
        mState.preferredDeviceMac = mac;
        mState.isPreferredAuto = isAuto;
        notifyStateChanged();
    }

    @Override
    public void onDeviceRoutingChanged() {
        queryConnectedDevices();
    }

    @Override
    public void onFirmwareUpdate(String version) {
        Log.d(TAG, "onFirmwareUpdate: " + version);
        mState.firmwareVersion = version;
        getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("last_known_firmware", version)
            .apply();
        notifyStateChanged();
    }

    @Override
    public void onSpatialTypeUpdate(int type) {
        mState.spatialAudio = (type != 0);
        notifyStateChanged();
    }

    @Override
    public void onAncHoldCycleUpdate(int mask) {
        mState.ancHoldCycleMask = mask;
        notifyStateChanged();
    }

    @Override
    public void onKeyFunctionUpdate(int dev, int act, int fn) {
        if (dev == 1) { // Left
            if (act == 2) mState.gestureLeftDouble = fn;
            else if (act == 3) mState.gestureLeftTriple = fn;
            else if (act == 4) mState.gestureLeftHold = fn;
        } else if (dev == 2) { // Right
            if (act == 2) mState.gestureRightDouble = fn;
            else if (act == 3) mState.gestureRightTriple = fn;
            else if (act == 4) mState.gestureRightHold = fn;
        }
        notifyStateChanged();
    }

    @Override
    public void onRawPacket(String direction, String hex) {
        logPacket(direction, hex);
    }

    // ------------------------------------------------------------------------
    // Notifications & System Receivers
    // ------------------------------------------------------------------------

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "AirBuds Status",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Real-time battery and ANC status");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));

        String l = mState.batteryLeft >= 0 ? mState.batteryLeft + "%" : "--";
        String r = mState.batteryRight >= 0 ? mState.batteryRight + "%" : "--";
        String c = mState.batteryCase >= 0 ? mState.batteryCase + "%" : "--";
        String contentText = "L: " + l + "  Case: " + c + "  R: " + r;

        boolean isConnected = (mState.connState == BudsState.ConnState.CONNECTED);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID);
        builder.setContentTitle("realme Buds Air 8")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notif_earbuds)
            .setContentIntent(pi)
            .setOngoing(isConnected);

        try {
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification_buds_compact);

            views.setTextViewText(R.id.notif_tv_left, l);
            views.setViewVisibility(R.id.notif_iv_charge_left, mState.chargingLeft ? View.VISIBLE : View.GONE);
            views.setTextViewText(R.id.notif_tv_case, c);
            views.setViewVisibility(R.id.notif_iv_charge_case, mState.chargingCase ? View.VISIBLE : View.GONE);
            views.setTextViewText(R.id.notif_tv_right, r);
            views.setViewVisibility(R.id.notif_iv_charge_right, mState.chargingRight ? View.VISIBLE : View.GONE);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                builder.setCustomContentView(views);
                builder.setCustomBigContentView(views);
                builder.setStyle(new Notification.DecoratedCustomViewStyle());
            }
        } catch (Exception e) {
            Log.w(TAG, "Error applying custom RemoteViews to notification", e);
        }

        return builder.build();
    }

    private void performUpdateNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        try {
            if (mShowNotification && mState.connState == BudsState.ConnState.CONNECTED) {
                if (!mIsForeground) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(NOTIF_ID, buildNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
                    } else {
                        startForeground(NOTIF_ID, buildNotification());
                    }
                    mIsForeground = true;
                } else {
                    nm.notify(NOTIF_ID, buildNotification());
                }
            } else {
                if (mIsForeground) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE);
                    } else {
                        stopForeground(true);
                    }
                    mIsForeground = false;
                } else {
                    nm.cancel(NOTIF_ID);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Error updating notification", e);
        }
    }

    private void updateNotification() {
        long now = System.currentTimeMillis();
        long diff = now - mLastNotifUpdate;
        if (diff >= 500) {
            mLastNotifUpdate = now;
            mMainHandler.removeCallbacks(mThrottledNotifRunnable);
            performUpdateNotification();
        } else {
            mMainHandler.removeCallbacks(mThrottledNotifRunnable);
            mMainHandler.postDelayed(mThrottledNotifRunnable, 500 - diff);
        }
    }

    private final BroadcastReceiver mBtReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            BluetoothDevice dev;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                dev = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
            } else {
                dev = getDeviceExtraCompat(intent);
            }
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                if (dev != null && dev.getName() != null && dev.getName().toLowerCase().contains("air8")) {
                    logPacket("SYS", "Earbuds connected to Android system! Auto-connecting SPP...");
                    connectToEarbuds();
                }
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                if (dev != null && dev.getName() != null && dev.getName().toLowerCase().contains("air8")) {
                    logPacket("SYS", "Earbuds disconnected from Android system.");
                    onDisconnected();
                }
            } else if (BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothA2dp.STATE_NOT_PLAYING);
                boolean isPlaying = (state == BluetoothA2dp.STATE_PLAYING);
                Log.d(TAG, "A2DP playing state changed: isPlaying=" + isPlaying + " (" + state + ")");
                if (mState.isLocalA2dpPlaying != isPlaying) {
                    mState.isLocalA2dpPlaying = isPlaying;
                    notifyStateChanged();
                }
            }
        }

        @SuppressWarnings("deprecation")
        private BluetoothDevice getDeviceExtraCompat(Intent intent) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        }
    };

    // ------------------------------------------------------------------------
    // BLE Fast Pair Case Battery Scanner
    // ------------------------------------------------------------------------

    private final ScanCallback mBleScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (result == null) return;
            ScanRecord record = result.getScanRecord();
            if (record == null) return;
            byte[] serviceData = record.getServiceData(FAST_PAIR_SERVICE_UUID);
            if (serviceData != null) {
                parseFastPairBattery(serviceData);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            if (results != null) {
                for (ScanResult r : results) {
                    onScanResult(0, r);
                }
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "BLE Fast Pair scan failed with code: " + errorCode);
            mIsScanning = false;
        }
    };

    public void startBleFastPairScanner() {
        if (mIsScanning) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "BLUETOOTH_SCAN permission not granted; skipping BLE case scan");
                return;
            }
        }
        try {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            BluetoothAdapter adapter = bm != null ? bm.getAdapter() : null;
            if (adapter == null || !adapter.isEnabled()) return;

            mBleScanner = adapter.getBluetoothLeScanner();
            if (mBleScanner == null) return;

            List<ScanFilter> filters = new ArrayList<>();
            filters.add(new ScanFilter.Builder()
                .setServiceData(FAST_PAIR_SERVICE_UUID, null)
                .build());

            ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                .setReportDelay(0)
                .build();

            mBleScanner.startScan(filters, settings, mBleScanCallback);
            mIsScanning = true;
            Log.d(TAG, "BLE Fast Pair scanner started (service UUID 0xFE2C)");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BLE Fast Pair scanner", e);
        }
    }

    public void stopBleFastPairScanner() {
        if (!mIsScanning || mBleScanner == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                mIsScanning = false;
                return;
            }
        }
        try {
            mBleScanner.stopScan(mBleScanCallback);
        } catch (Exception ignored) {}
        mIsScanning = false;
        Log.d(TAG, "BLE Fast Pair scanner stopped");
    }

    private void parseFastPairBattery(byte[] serviceData) {
        if (serviceData == null || serviceData.length < 4) return;
        for (int i = 0; i < serviceData.length - 3; i++) {
            int hdr = serviceData[i] & 0xFF;
            int length = (hdr >> 4) & 0x0F;
            int uiType = hdr & 0x0F;
            if (length == 3 && (uiType == 0x03 || uiType == 0x04)) {
                int leftRaw = serviceData[i + 1] & 0xFF;
                int rightRaw = serviceData[i + 2] & 0xFF;
                int caseRaw = serviceData[i + 3] & 0xFF;

                boolean changed = false;
                int lVal = leftRaw & 0x7F;
                if (lVal <= 100) {
                    if (mState.batteryLeft != lVal) {
                        mState.batteryLeft = lVal;
                        changed = true;
                    }
                    boolean chg = (leftRaw & 0x80) != 0;
                    if (mState.chargingLeft != chg) {
                        mState.chargingLeft = chg;
                        changed = true;
                    }
                }

                int rVal = rightRaw & 0x7F;
                if (rVal <= 100) {
                    if (mState.batteryRight != rVal) {
                        mState.batteryRight = rVal;
                        changed = true;
                    }
                    boolean chg = (rightRaw & 0x80) != 0;
                    if (mState.chargingRight != chg) {
                        mState.chargingRight = chg;
                        changed = true;
                    }
                }

                int cVal = caseRaw & 0x7F;
                if (cVal <= 100) {
                    if (mState.batteryCase != cVal) {
                        mState.batteryCase = cVal;
                        changed = true;
                    }
                    boolean chg = (caseRaw & 0x80) != 0;
                    if (mState.chargingCase != chg) {
                        mState.chargingCase = chg;
                        changed = true;
                    }
                }

                if (changed) {
                    logPacket("BLE_ADV", "FastPair battery: L=" + mState.batteryLeft + "% (" + (mState.chargingLeft ? "⚡" : "") + ") "
                        + "R=" + mState.batteryRight + "% (" + (mState.chargingRight ? "⚡" : "") + ") "
                        + "Case=" + mState.batteryCase + "% (" + (mState.chargingCase ? "⚡" : "") + ")");
                    notifyStateChanged();
                }
                break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mMainHandler.removeCallbacks(mThrottledNotifRunnable);
        if (mIsForeground) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE);
                } else {
                    stopForeground(true);
                }
                mIsForeground = false;
            } catch (Exception ignored) {}
        }
        stopBleFastPairScanner();
        try {
            unregisterReceiver(mBtReceiver);
        } catch (Exception ignored) {}
        disconnectInternal();
    }
}
