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
 * and 1-tap toggling of Game Mode (45ms low latency) directly
 * from the notification shade.
 */
public class GameModeTileService extends TileService implements BudsState.Listener {

    private BudsService mService;
    private boolean mBound = false;

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BudsService.LocalBinder binder = (BudsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addListener(GameModeTileService.this);
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
            Intent intent = new Intent(this, BudsService.class);
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            return;
        }

        BudsState state = mService.getState();
        if (state.connState != BudsState.ConnState.CONNECTED) {
            mService.reconnect();
            return;
        }

        // Toggle Game Mode
        mService.setGameMode(!state.gameMode);
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
            tile.setLabel("Game Mode");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.setSubtitle("Disconnected");
            }
        } else {
            if (state.gameMode) {
                tile.setState(Tile.STATE_ACTIVE);
                tile.setLabel("Game Mode On");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.setSubtitle("45ms Low Latency");
                }
            } else {
                tile.setState(Tile.STATE_INACTIVE);
                tile.setLabel("Game Mode Off");
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.setSubtitle("Standard Latency");
                }
            }
        }

        tile.updateTile();
    }
}
