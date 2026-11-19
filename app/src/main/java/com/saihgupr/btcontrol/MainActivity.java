package com.saihgupr.btcontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.saihgupr.btcontrol.sony.SonyController;
import com.saihgupr.btcontrol.sony.SonySession;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements BluetoothManagerHelper.OnProfileProxyListener {

    private static final String TAG = "MainActivity";
    public static final String EXTRA_AUTO_CONNECT = "autoconnect";
    public static final String ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED";

    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private MaterialCardView autoconnectCard;
    private TextView autoconnectSubtitle;
    private SwitchCompat autoconnectSwitch;
    private MaterialCardView volumeCard;
    private TextView volumeSubtitle;
    private TextView volumeBadge;
    private View noiseCard;
    private TextView noiseSubtitle;
    private TextView noiseBadge;
    private View muteCard;
    private TextView muteSubtitle;
    private SwitchCompat muteSwitch;
    private boolean noiseBusy = false;
    private RecyclerView devicesRecycler;
    private DeviceAdapter deviceAdapter;
    private BluetoothManagerHelper btHelper;
    private boolean hasAutoConnectedThisSession = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent != null ? intent.getAction() : null;
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                mainHandler.postDelayed(() -> DevicePrefs.applyStartupVolume(context), 1200);
            }
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) ||
                BluetoothAdapter.ACTION_STATE_CHANGED.equals(action) ||
                ACTION_BATTERY_LEVEL_CHANGED.equals(action)) {
                updateDeviceList();
                updateStatus();
                updateAutoConnectUI();
                updateVolumeUI();
                updateNoiseUI();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSubtitle = findViewById(R.id.status_subtitle);
        autoconnectCard = findViewById(R.id.autoconnect_card);
        autoconnectSubtitle = findViewById(R.id.autoconnect_subtitle);
        autoconnectSwitch = findViewById(R.id.autoconnect_switch);
        volumeCard = findViewById(R.id.volume_card);
        volumeSubtitle = findViewById(R.id.volume_subtitle);
        volumeBadge = findViewById(R.id.volume_badge);
        noiseCard = findViewById(R.id.noise_card);
        noiseSubtitle = findViewById(R.id.noise_subtitle);
        noiseBadge = findViewById(R.id.noise_badge);
        muteCard = findViewById(R.id.mute_card);
        muteSubtitle = findViewById(R.id.mute_subtitle);
        muteSwitch = findViewById(R.id.mute_switch);
        devicesRecycler = findViewById(R.id.devices_recycler);

        btHelper = new BluetoothManagerHelper(this);
        deviceAdapter = new DeviceAdapter(btHelper, new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onConnect(BluetoothDevice device) {
                btHelper.connect(device.getAddress());
            }

            @Override
            public void onDisconnect(BluetoothDevice device) {
                btHelper.disconnect(device.getAddress());
            }

            @Override
            public void onToggleDefault(BluetoothDevice device) {
                toggleDefaultDevice(device);
            }
        });

        btHelper.addProfileProxyListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(ACTION_BATTERY_LEVEL_CHANGED);
        registerReceiver(mReceiver, filter);

        devicesRecycler.setLayoutManager(new LinearLayoutManager(this));
        devicesRecycler.setAdapter(deviceAdapter);

        setupAutoConnectCard();
        setupVolumeCard();
        setupNoiseCard();
        setupMuteCard();
        setupFocusIndicators();

        startMuteWatcher();
    }

    /**
     * TV screens have no hover or touch, so every focusable row needs a visible focus ring.
     * Cards get a brighter stroke, the rows inside the controls card get a rounded highlight.
     */
    private void setupFocusIndicators() {
        styleCardFocus(autoconnectCard);
        styleCardFocus(volumeCard);
        styleRowFocus(noiseCard, true, false);
        styleRowFocus(muteCard, false, true);
        devicesRecycler.addOnChildAttachStateChangeListener(
                new RecyclerView.OnChildAttachStateChangeListener() {
                    @Override
                    public void onChildViewAttachedToWindow(@NonNull View view) {
                        styleCardFocus(view);
                    }

                    @Override
                    public void onChildViewDetachedFromWindow(@NonNull View view) {
                        // Nothing to release.
                    }
                });
    }

    private void styleCardFocus(View view) {
        if (!(view instanceof MaterialCardView)) return;

        final MaterialCardView card = (MaterialCardView) view;
        final float density = getResources().getDisplayMetrics().density;
        final int focusColor = ContextCompat.getColor(this, R.color.colorPrimary);
        final int idleColor = ContextCompat.getColor(this, R.color.card_overlay);
        final int focusWidth = Math.round(2 * density);
        final int idleWidth = Math.round(density);

        card.setOnFocusChangeListener((v, hasFocus) -> {
            card.setStrokeColor(hasFocus ? focusColor : idleColor);
            card.setStrokeWidth(hasFocus ? focusWidth : idleWidth);
        });
    }

    private void styleRowFocus(View row, boolean roundTop, boolean roundBottom) {
        if (row == null) return;

        final float density = getResources().getDisplayMetrics().density;
        final float radius = 14 * density;
        final float[] radii = new float[]{
                roundTop ? radius : 0f, roundTop ? radius : 0f,
                roundTop ? radius : 0f, roundTop ? radius : 0f,
                roundBottom ? radius : 0f, roundBottom ? radius : 0f,
                roundBottom ? radius : 0f, roundBottom ? radius : 0f};
        final GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setCornerRadii(radii);
        shape.setColor(Color.TRANSPARENT);

        row.setBackground(shape);

        final int focusColor = ContextCompat.getColor(this, R.color.colorPrimary);
        final int highlightColor = ContextCompat.getColor(this, R.color.card_overlay);
        final int strokeWidth = Math.round(2 * density);

        row.setOnFocusChangeListener((v, hasFocus) -> {
            shape.setColor(hasFocus ? highlightColor : Color.TRANSPARENT);
            shape.setStroke(hasFocus ? strokeWidth : 0,
                    hasFocus ? focusColor : Color.TRANSPARENT);
            v.invalidate();
        });
    }

    private void setupMuteCard() {
        if (muteCard == null) return;

        muteCard.setOnClickListener(v ->
                setMuteRemap(!DevicePrefs.isMuteRemapEnabled(this)));
        updateMuteUI();
    }

    private void setMuteRemap(boolean enabled) {
        DevicePrefs.setMuteRemapEnabled(this, enabled);
        updateMuteUI();
        if (enabled) {
            startMuteWatcher();
        } else {
            stopService(new Intent(this, MuteWatcherService.class));
        }
    }

    private void updateMuteUI() {
        if (muteSwitch == null || muteSubtitle == null) return;

        boolean enabled = DevicePrefs.isMuteRemapEnabled(this);
        muteSwitch.setChecked(enabled);
        muteSubtitle.setText(enabled
                ? R.string.mute_remap_desc_on
                : R.string.mute_remap_desc_off);
        muteSubtitle.setTextColor(ContextCompat.getColor(this,
                enabled ? R.color.colorSecondary : R.color.text_muted));
    }

    private void startMuteWatcher() {
        if (!DevicePrefs.isMuteRemapEnabled(this)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            ContextCompat.startForegroundService(this,
                    new Intent(this, MuteWatcherService.class));
        } catch (Exception e) {
            Log.w(TAG, "could not start mute watcher", e);
        }
    }

    private void setupAutoConnectCard() {
        if (autoconnectCard == null) return;

        autoconnectCard.setOnClickListener(v -> {
            boolean newState = !DevicePrefs.isAutoConnectEnabled(this);
            DevicePrefs.setAutoConnectEnabled(this, newState);
            updateAutoConnectUI();
        });

        updateAutoConnectUI();
    }

    private void setupVolumeCard() {
        if (volumeCard == null) return;

        volumeCard.setOnClickListener(v -> cycleVolumeSetting());
        updateVolumeUI();
    }

    private void cycleVolumeSetting() {
        boolean isEnabled = DevicePrefs.isStartupVolumeEnabled(this);
        int currentPercent = DevicePrefs.getStartupVolumePercent(this);

        if (!isEnabled) {
            DevicePrefs.setStartupVolumeEnabled(this, true);
            DevicePrefs.setStartupVolumePercent(this, 30);
            Toast.makeText(this, getString(R.string.startup_volume_set, 30), Toast.LENGTH_SHORT).show();
        } else {
            int nextPercent;
            if (currentPercent < 40) nextPercent = 40;
            else if (currentPercent < 50) nextPercent = 50;
            else if (currentPercent < 60) nextPercent = 60;
            else if (currentPercent < 70) nextPercent = 70;
            else if (currentPercent < 80) nextPercent = 80;
            else {
                DevicePrefs.setStartupVolumeEnabled(this, false);
                Toast.makeText(this, R.string.startup_volume_disabled_toast, Toast.LENGTH_SHORT).show();
                updateVolumeUI();
                return;
            }
            DevicePrefs.setStartupVolumePercent(this, nextPercent);
            Toast.makeText(this, getString(R.string.startup_volume_set, nextPercent), Toast.LENGTH_SHORT).show();
        }

        updateVolumeUI();
    }

    private void updateVolumeUI() {
        if (volumeSubtitle == null || volumeBadge == null) return;

        boolean isEnabled = DevicePrefs.isStartupVolumeEnabled(this);
        if (isEnabled) {
            int percent = DevicePrefs.getStartupVolumePercent(this);
            volumeSubtitle.setText(getString(R.string.startup_volume_desc_enabled, percent));
            volumeSubtitle.setTextColor(ContextCompat.getColor(this, R.color.colorSecondary));
            volumeBadge.setText(percent + "%");
            volumeBadge.setTextColor(ContextCompat.getColor(this, R.color.colorSecondary));
        } else {
            volumeSubtitle.setText(R.string.startup_volume_desc_disabled);
            volumeSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
            volumeBadge.setText("OFF");
            volumeBadge.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }

    private void setupNoiseCard() {
        if (noiseCard == null) return;

        noiseCard.setOnClickListener(v -> cycleNoiseMode());
        updateNoiseUI();
    }

    private void cycleNoiseMode() {
        if (noiseBusy) return;

        BluetoothDevice device = findConnectedSonyDevice();
        if (device == null) {
            Toast.makeText(this, R.string.noise_control_no_device, Toast.LENGTH_SHORT).show();
            return;
        }

        SonySession.Mode current = getLastNoiseMode();
        final SonySession.Mode next;
        if (current == SonySession.Mode.NC) {
            next = SonySession.Mode.AMBIENT;
        } else if (current == SonySession.Mode.AMBIENT) {
            next = SonySession.Mode.OFF;
        } else {
            next = SonySession.Mode.NC;
        }

        final int ambientLevel = DevicePrefs.getAmbientLevel(this);
        noiseBusy = true;

        new Thread(() -> {
            SonySession.Result result = null;
            try {
                result = SonyController.setMode(device, next, ambientLevel);
            } catch (Exception e) {
                Log.e(TAG, "Sony setMode failed", e);
            }
            final SonySession.Result finalResult = result;
            runOnUiThread(() -> {
                noiseBusy = false;
                if (finalResult != null && finalResult.ok) {
                    SonySession.Mode applied = finalResult.mode != null ? finalResult.mode : next;
                    DevicePrefs.setLastAncMode(this, applied.name());
                    updateNoiseUI();
                    Toast.makeText(this, getNoiseModeToast(applied), Toast.LENGTH_SHORT).show();
                } else {
                    String error = (finalResult != null && finalResult.error != null)
                            ? finalResult.error
                            : getString(R.string.noise_control_failed);
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                }
            });
        }, "noise-control-cycle").start();
    }

    private BluetoothDevice findConnectedSonyDevice() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return null;

        try {
            Set<BluetoothDevice> bondedDevices = adapter.getBondedDevices();
            if (bondedDevices == null) return null;

            for (BluetoothDevice device : bondedDevices) {
                try {
                    if (btHelper.getConnectionState(device) == BluetoothProfile.STATE_CONNECTED
                            && SonyController.looksLikeSony(device)) {
                        return device;
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Sony check failed for " + device.getAddress(), e);
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException while looking for a Sony device", e);
        }

        return null;
    }

    private SonySession.Mode getLastNoiseMode() {
        String saved = DevicePrefs.getLastAncMode(this);
        if (saved != null) {
            try {
                return SonySession.Mode.valueOf(saved);
            } catch (IllegalArgumentException ignored) {
                // Stored value from an older or newer build, fall through
            }
        }
        return SonySession.Mode.UNKNOWN;
    }

    private int getNoiseModeToast(SonySession.Mode mode) {
        if (mode == SonySession.Mode.NC) return R.string.noise_control_toast_nc;
        if (mode == SonySession.Mode.AMBIENT) return R.string.noise_control_toast_ambient;
        if (mode == SonySession.Mode.OFF) return R.string.noise_control_toast_off;
        return R.string.noise_control_toast_updated;
    }

    private void updateNoiseUI() {
        if (noiseSubtitle == null || noiseBadge == null) return;

        SonySession.Mode mode = getLastNoiseMode();
        int badgeRes;
        int badgeColor;
        if (mode == SonySession.Mode.NC) {
            badgeRes = R.string.noise_control_badge_nc;
            badgeColor = R.color.colorPrimary;
        } else if (mode == SonySession.Mode.AMBIENT) {
            badgeRes = R.string.noise_control_badge_ambient;
            badgeColor = R.color.status_ready;
        } else if (mode == SonySession.Mode.OFF) {
            badgeRes = R.string.noise_control_badge_off;
            badgeColor = R.color.text_muted;
        } else {
            badgeRes = R.string.noise_control_badge_unknown;
            badgeColor = R.color.text_muted;
        }

        noiseSubtitle.setText(R.string.noise_control_subtitle);
        noiseSubtitle.setTextColor(ContextCompat.getColor(this,
                mode == SonySession.Mode.UNKNOWN ? R.color.text_muted : R.color.colorSecondary));
        noiseBadge.setText(badgeRes);
        noiseBadge.setTextColor(ContextCompat.getColor(this, badgeColor));
    }

    private void updateAutoConnectUI() {
        if (autoconnectSwitch == null || autoconnectSubtitle == null) return;

        boolean isEnabled = DevicePrefs.isAutoConnectEnabled(this);
        autoconnectSwitch.setChecked(isEnabled);

        if (isEnabled) {
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);
            String defaultName = DevicePrefs.getDefaultDeviceName(this);
            if (defaultAddress != null) {
                String name = defaultName != null ? defaultName : defaultAddress;
                autoconnectSubtitle.setText(getString(R.string.autoconnect_desc_target, name));
            } else {
                autoconnectSubtitle.setText(R.string.autoconnect_desc_recent);
            }
            autoconnectSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_ready));
        } else {
            autoconnectSubtitle.setText(R.string.autoconnect_desc_disabled);
            autoconnectSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_muted));
        }
    }


    private void toggleDefaultDevice(BluetoothDevice device) {
        String currentDefault = DevicePrefs.getDefaultDeviceAddress(this);
        String deviceName = device.getName() != null ? device.getName() : device.getAddress();

        if (currentDefault != null && currentDefault.equalsIgnoreCase(device.getAddress())) {
            DevicePrefs.clearDefaultDevice(this);
            Toast.makeText(this, R.string.default_device_cleared, Toast.LENGTH_SHORT).show();
        } else {
            DevicePrefs.setDefaultDevice(this, device.getAddress(), deviceName);
            DevicePrefs.setAutoConnectEnabled(this, true);
            Toast.makeText(this, getString(R.string.default_device_set, deviceName), Toast.LENGTH_SHORT).show();
        }

        updateAutoConnectUI();
        deviceAdapter.notifyDataSetChanged();
    }

    private void checkAndTriggerAutoConnect() {
        if (hasAutoConnectedThisSession) return;

        boolean explicitIntent = getIntent() != null && getIntent().getBooleanExtra(EXTRA_AUTO_CONNECT, false);
        boolean autoConnectPref = DevicePrefs.isAutoConnectEnabled(this);

        if (!explicitIntent && !autoConnectPref) return;

        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null || !adapter.isEnabled()) return;

        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            if (bonded == null || bonded.isEmpty()) return;

            BluetoothDevice target = null;
            String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);

            if (defaultAddress != null) {
                for (BluetoothDevice d : bonded) {
                    if (d.getAddress().equalsIgnoreCase(defaultAddress)) {
                        target = d;
                        break;
                    }
                }
            }

            if (target == null) {
                long maxTime = -1;
                for (BluetoothDevice d : bonded) {
                    long lastUsed = DevicePrefs.getLastUsed(this, d.getAddress());
                    if (lastUsed > maxTime) {
                        maxTime = lastUsed;
                        target = d;
                    }
                }
            }

            if (target != null) {
                int state = btHelper.getConnectionState(target);
                if (state != BluetoothProfile.STATE_CONNECTED && state != BluetoothProfile.STATE_CONNECTING) {
                    hasAutoConnectedThisSession = true;
                    Log.i(TAG, "Auto-connecting on launch to: " + target.getName() + " [" + target.getAddress() + "]");
                    btHelper.connect(target.getAddress());
                }
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException during auto-connect", e);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateDeviceList();
        updateAutoConnectUI();
        updateVolumeUI();
        updateNoiseUI();
    }


    private void updateDeviceList() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            try {
                Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
                List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
                
                String defaultAddress = DevicePrefs.getDefaultDeviceAddress(this);

                // Sort: Default device first, then recent, then connected, then alphabetical
                Collections.sort(deviceList, (d1, d2) -> {
                    boolean isDef1 = defaultAddress != null && defaultAddress.equalsIgnoreCase(d1.getAddress());
                    boolean isDef2 = defaultAddress != null && defaultAddress.equalsIgnoreCase(d2.getAddress());
                    if (isDef1 && !isDef2) return -1;
                    if (!isDef1 && isDef2) return 1;

                    // 1. Sort by last used timestamp (most recent first)
                    long t1 = DevicePrefs.getLastUsed(getApplicationContext(), d1.getAddress());
                    long t2 = DevicePrefs.getLastUsed(getApplicationContext(), d2.getAddress());
                    if (t1 != t2) return Long.compare(t2, t1);

                    // 2. Sort by connection state: Connected/Connecting first
                    int s1 = btHelper.getConnectionState(d1);
                    int s2 = btHelper.getConnectionState(d2);
                    
                    boolean c1 = (s1 == BluetoothProfile.STATE_CONNECTED || s1 == BluetoothProfile.STATE_CONNECTING);
                    boolean c2 = (s2 == BluetoothProfile.STATE_CONNECTED || s2 == BluetoothProfile.STATE_CONNECTING);
                    
                    if (c1 && !c2) return -1;
                    if (!c1 && c2) return 1;
                    
                    // 3. Alphabetical fallback
                    String n1 = d1.getName() != null ? d1.getName() : "";
                    String n2 = d2.getName() != null ? d2.getName() : "";
                    return n1.compareToIgnoreCase(n2);
                });
                
                deviceAdapter.setDevices(deviceList);
            } catch (SecurityException e) {
                deviceAdapter.setDevices(new ArrayList<>());
            }
        } else {
            deviceAdapter.setDevices(new ArrayList<>());
        }
    }

    private void updateStatus() {
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                setReadyState();
            } else {
                setDisabledState();
            }
        } catch (SecurityException e) {
            setPermissionRequiredState();
        }
    }

    private void setReadyState() {
        int color = ContextCompat.getColor(this, R.color.status_ready);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_ready);
        statusSubtitle.setText(R.string.status_bt_on);
        statusSubtitle.setTextColor(color);
    }

    private void setDisabledState() {
        int color = ContextCompat.getColor(this, R.color.status_error);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_bt_off);
        statusSubtitle.setText(R.string.status_enable_hint);
        statusSubtitle.setTextColor(color);
    }

    private void setPermissionRequiredState() {
        int color = ContextCompat.getColor(this, R.color.status_error);
        statusIcon.setImageTintList(ColorStateList.valueOf(color));
        statusTitle.setText(R.string.status_permission_required);
        statusSubtitle.setText(R.string.status_permission_hint);
        statusSubtitle.setTextColor(color);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (btHelper != null) {
            btHelper.removeProfileProxyListener(this);
        }
        unregisterReceiver(mReceiver);
    }
    
    @Override
    public void onProxyConnected() {
        runOnUiThread(() -> {
            updateDeviceList();
            checkAndTriggerAutoConnect();
        });
    }

    @Override
    public void onProxyDisconnected() {
        runOnUiThread(this::updateDeviceList);
    }
}

