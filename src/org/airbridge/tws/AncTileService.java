package org.airbridge.tws;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

/**
 * Android Quick Settings Tile providing instant status display
 * and 1-tap toggling of ANC / Transparency / Off modes directly
 * from the notification shade.
 */
public class AncTileService extends TileService implements BudsState.Listener {

    private BudsService mService;
    private boolean mBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BudsService.LocalBinder binder = (BudsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addListener(AncTileService.this);
            updateTileState(mService.getState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
            updateTileState(null);
        }
    };

    @Override
    public void onStartListening() {
        super.onStartListening();
        Intent intent = new Intent(this, BudsService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onStopListening() {
        super.onStopListening();
        if (mBound && mService != null) {
            mService.removeListener(this);
            unbindService(mConnection);
            mBound = false;
            mService = null;
        }
    }

    @Override
    public void onClick() {
        super.onClick();
        if (!mBound || mService == null) {
            // Start service if not already running
            Intent intent = new Intent(this, BudsService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            return;
        }

        BudsState state = mService.getState();
        if (state.connState != BudsState.ConnState.CONNECTED) {
            mService.reconnect();
            return;
        }

        // Cycle through modes according to configured cycle mask
        // Mask bits: bit 0: Off (1), bit 1: ANC (2), bit 2: Transparency (4)
        int current = state.ancMode;
        int next = 8; // Default to ANC

        if (current == 8) { // Currently ANC
            if ((state.ancHoldCycleMask & 4) != 0) {
                next = 2; // Switch to Transparency
            } else if ((state.ancHoldCycleMask & 1) != 0) {
                next = 1; // Switch to Off
            }
        } else if (current == 2) { // Currently Transparency
            if ((state.ancHoldCycleMask & 1) != 0) {
                next = 1; // Switch to Off
            } else if ((state.ancHoldCycleMask & 2) != 0) {
                next = 8; // Switch to ANC
            }
        } else { // Currently Off (1)
            if ((state.ancHoldCycleMask & 2) != 0) {
                next = 8; // Switch to ANC
            } else if ((state.ancHoldCycleMask & 4) != 0) {
                next = 2; // Switch to Transparency
            }
        }

        mService.setAncMode(next);
    }

    @Override
    public void onStateChanged(BudsState state) {
        updateTileState(state);
    }

    @Override
    public void onRawPacket(String direction, String hex) {}

    private void updateTileState(BudsState state) {
        Tile tile = getQsTile();
        if (tile == null) return;

        if (state == null || state.connState != BudsState.ConnState.CONNECTED) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("AirBuds");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("Disconnected");
            }
        } else {
            String label;
            int tileState = Tile.STATE_ACTIVE;

            switch (state.ancMode) {
                case 8:
                    label = "ANC On";
                    break;
                case 2:
                    label = "Transparency";
                    break;
                case 1:
                default:
                    label = "Normal / Off";
                    tileState = Tile.STATE_INACTIVE;
                    break;
            }

            tile.setState(tileState);
            tile.setLabel(label);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                String sub = state.deviceName;
                if (state.batteryLeft >= 0 && state.batteryRight >= 0) {
                    sub = "L " + state.batteryLeft + "%  R " + state.batteryRight + "%";
                }
                tile.setSubtitle(sub);
            }
        }

        tile.updateTile();
    }
}
