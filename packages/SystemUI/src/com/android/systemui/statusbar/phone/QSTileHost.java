/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.phone;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Process;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.android.systemui.R;
import com.android.systemui.qs.QSTile;
import com.android.systemui.qs.tiles.AirplaneModeTile;
import com.android.systemui.qs.tiles.ApnTile;
import com.android.systemui.qs.tiles.BluetoothTile;
import com.android.systemui.qs.tiles.CastTile;
import com.android.systemui.qs.tiles.CellularTile;
import com.android.systemui.qs.tiles.ColorInversionTile;
import com.android.systemui.qs.tiles.DataTile;
import com.android.systemui.qs.tiles.DdsTile;
import com.android.systemui.qs.tiles.FlashlightTile;
import com.android.systemui.qs.tiles.HotspotTile;
import com.android.systemui.qs.tiles.ImmersiveTile;
import com.android.systemui.qs.tiles.IntentTile;
import com.android.systemui.qs.tiles.LiveDisplayTile;
import com.android.systemui.qs.tiles.LocationTile;
import com.android.systemui.qs.tiles.NotificationsTile;
import com.android.systemui.qs.tiles.RoamingTile;
import com.android.systemui.qs.tiles.RotationLockTile;
import com.android.systemui.qs.tiles.WifiTile;
import com.android.systemui.settings.CurrentUserTracker;
import com.android.systemui.statusbar.policy.BluetoothController;
import com.android.systemui.statusbar.policy.CastController;
import com.android.systemui.statusbar.policy.KeyguardMonitor;
import com.android.systemui.statusbar.policy.LocationController;
import com.android.systemui.statusbar.policy.NetworkController;
import com.android.systemui.statusbar.policy.RotationLockController;
import com.android.systemui.statusbar.policy.HotspotController;
import com.android.systemui.statusbar.policy.SecurityController;
import com.android.systemui.statusbar.policy.UserSwitcherController;
import com.android.systemui.statusbar.policy.ZenModeController;
import com.android.systemui.volume.VolumeComponent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Platform implementation of the quick settings tile host **/
public class QSTileHost implements QSTile.Host {
    private static final String TAG = "QSTileHost";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String TILES_SETTING = Secure.QS_TILES;

    private final Context mContext;
    private final ConnectivityManager mConnectivityManager;
    private final TelephonyManager mTelephonyManager;
    private final PhoneStatusBar mStatusBar;
    private final LinkedHashMap<String, QSTile<?>> mTiles = new LinkedHashMap<>();
    private final Observer mObserver = new Observer();
    private final BluetoothController mBluetooth;
    private final LocationController mLocation;
    private final RotationLockController mRotation;
    private final NetworkController mNetwork;
    private final ZenModeController mZen;
    private final HotspotController mHotspot;
    private final CastController mCast;
    private final Looper mLooper;
    private final CurrentUserTracker mUserTracker;
    private final VolumeComponent mVolume;
    private final UserSwitcherController mUserSwitcherController;
    private final KeyguardMonitor mKeyguard;
    private final SecurityController mSecurity;

    private Callback mCallback;

    public QSTileHost(Context context, PhoneStatusBar statusBar,
            BluetoothController bluetooth, LocationController location,
            RotationLockController rotation, NetworkController network,
            ZenModeController zen, VolumeComponent volume, HotspotController hotspot,
            CastController cast, UserSwitcherController userSwitcher, KeyguardMonitor keyguard,
            SecurityController security) {
        mContext = context;
        mStatusBar = statusBar;
        mBluetooth = bluetooth;
        mLocation = location;
        mRotation = rotation;
        mNetwork = network;
        mZen = zen;
        mVolume = volume;
        mHotspot = hotspot;
        mCast = cast;
        mUserSwitcherController = userSwitcher;
        mKeyguard = keyguard;
        mSecurity = security;
        mConnectivityManager = (ConnectivityManager)
                mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mTelephonyManager = (TelephonyManager)
                mContext.getSystemService(Context.TELEPHONY_SERVICE);

        final HandlerThread ht = new HandlerThread(QSTileHost.class.getSimpleName(),
                Process.THREAD_PRIORITY_BACKGROUND);
        ht.start();
        mLooper = ht.getLooper();

        mUserTracker = new CurrentUserTracker(mContext) {
            @Override
            public void onUserSwitched(int newUserId) {
                recreateTiles();
                for (QSTile<?> tile : mTiles.values()) {
                    tile.userSwitch(newUserId);
                }
                mSecurity.onUserSwitched(newUserId);
                mNetwork.onUserSwitched(newUserId);
                mObserver.register();
            }
        };
        recreateTiles();

        mUserTracker.startTracking();
        mObserver.register();
    }

    @Override
    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    @Override
    public QSTile<?>[] getTiles() {
        final Collection<QSTile<?>> col = mTiles.values();
        return col.toArray(new QSTile<?>[col.size()]);
    }

    @Override
    public void startSettingsActivity(final Intent intent) {
        mStatusBar.postStartSettingsActivity(intent, 0);
    }

    @Override
    public void warn(String message, Throwable t) {
        // already logged
    }

    @Override
    public void collapsePanels() {
        mStatusBar.postAnimateCollapsePanels();
    }

    @Override
    public Looper getLooper() {
        return mLooper;
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public BluetoothController getBluetoothController() {
        return mBluetooth;
    }

    @Override
    public LocationController getLocationController() {
        return mLocation;
    }

    @Override
    public RotationLockController getRotationLockController() {
        return mRotation;
    }

    @Override
    public NetworkController getNetworkController() {
        return mNetwork;
    }

    @Override
    public ZenModeController getZenModeController() {
        return mZen;
    }

    @Override
    public VolumeComponent getVolumeComponent() {
        return mVolume;
    }

    @Override
    public HotspotController getHotspotController() {
        return mHotspot;
    }

    @Override
    public CastController getCastController() {
        return mCast;
    }

    @Override
    public KeyguardMonitor getKeyguardMonitor() {
        return mKeyguard;
    }

    public UserSwitcherController getUserSwitcherController() {
        return mUserSwitcherController;
    }

    public SecurityController getSecurityController() {
        return mSecurity;
    }

    private void recreateTiles() {
        if (DEBUG) Log.d(TAG, "Recreating tiles");
        final List<String> tileSpecs = loadTileSpecs();
        for (Map.Entry<String, QSTile<?>> tile : mTiles.entrySet()) {
            if (!tileSpecs.contains(tile.getKey())) {
                if (DEBUG) Log.d(TAG, "Destroying tile: " + tile.getKey());
                tile.getValue().destroy();
            }
        }
        final LinkedHashMap<String, QSTile<?>> newTiles = new LinkedHashMap<>();
        for (String tileSpec : tileSpecs) {
            if (mTiles.containsKey(tileSpec)) {
                newTiles.put(tileSpec, mTiles.get(tileSpec));
            } else {
                if (DEBUG) Log.d(TAG, "Creating tile: " + tileSpec);
                try {
                    newTiles.put(tileSpec, createTile(tileSpec));
                } catch (Throwable t) {
                    Log.w(TAG, "Error creating tile for spec: " + tileSpec, t);
                }
            }
        }
        if (Arrays.equals(mTiles.keySet().toArray(), newTiles.keySet().toArray())) return;
        mTiles.clear();
        mTiles.putAll(newTiles);
        if (mCallback != null) {
            mCallback.onTilesChanged();
        }
    }

    private QSTile<?> createTile(String tileSpec) {
        if (tileSpec.equals(WifiTile.SPEC)) return new WifiTile(this);
        else if (tileSpec.equals(BluetoothTile.SPEC)) return new BluetoothTile(this);
        else if (tileSpec.equals(ColorInversionTile.SPEC)) return new ColorInversionTile(this);
        else if (tileSpec.equals(CellularTile.SPEC)) return new CellularTile(this);
        else if (tileSpec.equals(AirplaneModeTile.SPEC)) return new AirplaneModeTile(this);
        else if (tileSpec.equals(RotationLockTile.SPEC)) return new RotationLockTile(this);
        else if (tileSpec.equals(FlashlightTile.SPEC)) return new FlashlightTile(this);
        else if (tileSpec.equals(LocationTile.SPEC)) return new LocationTile(this);
        else if (tileSpec.equals(CastTile.SPEC)) return new CastTile(this);
        else if (tileSpec.equals(HotspotTile.SPEC)) return new HotspotTile(this);
        else if (tileSpec.equals(ImmersiveTile.SPEC)) return new ImmersiveTile(this);
        else if (tileSpec.equals(LiveDisplayTile.SPEC)) return new LiveDisplayTile(this);
        else if (tileSpec.equals(NotificationsTile.SPEC)) return new NotificationsTile(this);
        else if (tileSpec.equals(DataTile.SPEC)
                && mConnectivityManager.isNetworkSupported(ConnectivityManager.TYPE_MOBILE))
            return new DataTile(this);
        else if (tileSpec.equals(RoamingTile.SPEC)) return new RoamingTile(this);
        else if (tileSpec.equals(DdsTile.SPEC) && mTelephonyManager.isMultiSimEnabled()
                && (mTelephonyManager.getMultiSimConfiguration()
                == TelephonyManager.MultiSimVariants.DSDA))
            return new DdsTile(this);
        else if (tileSpec.equals(ApnTile.SPEC)) return new ApnTile(this);
        else if (tileSpec.startsWith(IntentTile.PREFIX)) return IntentTile.create(this,tileSpec);
        else throw new IllegalArgumentException("Bad tile spec: " + tileSpec);
    }

    public List<String> loadTileSpecs() {
        final Resources res = mContext.getResources();
        final String defaultTileList = res.getString(R.string.quick_settings_tiles_default);
        String tileList = Secure.getStringForUser(mContext.getContentResolver(), TILES_SETTING,
                mUserTracker.getCurrentUserId());
        if (tileList == null) {
            tileList = res.getString(R.string.quick_settings_tiles);
            if (DEBUG) Log.d(TAG, "Loaded tile specs from config: " + tileList);
        } else {
            if (DEBUG) Log.d(TAG, "Loaded tile specs from setting: " + tileList);
        }

        final ArrayList<String> tiles = new ArrayList<String>();
        boolean addedDefault = false;
        for (String tile : tileList.split(",")) {
            tile = tile.trim();
            if (tile.isEmpty()) continue;
            if (tile.equals("default")) {
                if (!addedDefault) {
                    tiles.addAll(Arrays.asList(defaultTileList.split(",")));
                    addedDefault = true;
                }
            } else {
                tiles.add(tile);
            }
        }

        for (String defaultTile : defaultTileList.split(",")) {
            defaultTile = defaultTile.trim();
            if (defaultTile.isEmpty()) continue;
            if (!tiles.contains(defaultTile)) {
                tiles.add(defaultTile);
            }
        }

        return tiles;
    }

    private class Observer extends ContentObserver {
        private boolean mRegistered;

        public Observer() {
            super(new Handler(Looper.getMainLooper()));
        }

        public void register() {
            if (mRegistered) {
                mContext.getContentResolver().unregisterContentObserver(this);
            }
            mContext.getContentResolver().registerContentObserver(Secure.getUriFor(TILES_SETTING),
                    false, this, mUserTracker.getCurrentUserId());
            mRegistered = true;
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            recreateTiles();
        }
    }
}
