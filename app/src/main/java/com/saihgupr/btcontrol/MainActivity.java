package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.Collections;
import java.util.List;
import android.bluetooth.BluetoothProfile;
import java.util.ArrayList;
import java.util.Set;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class MainActivity extends AppCompatActivity implements BluetoothManagerHelper.OnProfileProxyListener {

    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private RecyclerView devicesRecycler;
    private DeviceAdapter deviceAdapter;
    private BluetoothManagerHelper btHelper;
    
    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action) ||
                BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action) ||
                BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                updateDeviceList();
                updateStatus();
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
        });

        btHelper.addProfileProxyListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        registerReceiver(mReceiver, filter);

        devicesRecycler.setLayoutManager(new LinearLayoutManager(this));
        devicesRecycler.setAdapter(deviceAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateDeviceList();
    }

    private void updateDeviceList() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null && adapter.isEnabled()) {
            try {
                Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
                List<BluetoothDevice> deviceList = new ArrayList<>(pairedDevices);
                
                // Sort by recency, then connection state, then alphabetically
                Collections.sort(deviceList, (d1, d2) -> {
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
        // Check for runtime permission only if it throws during execution
        // Some Android TV versions/ADB states allow access without explicit check for isEnabled()

        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter != null && adapter.isEnabled()) {
                setReadyState();
            } else {
                setDisabledState();
            }
        } catch (SecurityException e) {
            // Fallback if somehow permission check passed but access denied
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
        int color = ContextCompat.getColor(this, R.color.status_error); // Or status_disabled if preferred
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
        runOnUiThread(this::updateDeviceList);
    }

    @Override
    public void onProxyDisconnected() {
        runOnUiThread(this::updateDeviceList);
    }
}
