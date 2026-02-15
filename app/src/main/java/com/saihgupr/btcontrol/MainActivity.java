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
import java.util.ArrayList;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;
    private RecyclerView devicesRecycler;
    private DeviceAdapter deviceAdapter;
    private BluetoothManagerHelper btHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSubtitle = findViewById(R.id.status_subtitle);
        devicesRecycler = findViewById(R.id.devices_recycler);

        btHelper = new BluetoothManagerHelper(this);
        setupRecyclerView();
    }

    private void setupRecyclerView() {
        deviceAdapter = new DeviceAdapter(new DeviceAdapter.OnDeviceActionListener() {
            @Override
            public void onConnect(BluetoothDevice device) {
                btHelper.connect(device.getAddress());
            }

            @Override
            public void onDisconnect(BluetoothDevice device) {
                btHelper.disconnect(device.getAddress());
            }
        });

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
                deviceAdapter.setDevices(new ArrayList<>(pairedDevices));
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
}
