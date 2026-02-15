package com.saihgupr.btcontrol;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private ImageView statusIcon;
    private TextView statusTitle;
    private TextView statusSubtitle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusIcon = findViewById(R.id.status_icon);
        statusTitle = findViewById(R.id.status_title);
        statusSubtitle = findViewById(R.id.status_subtitle);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }

    private void updateStatus() {
        // Check for runtime permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                setPermissionRequiredState();
                return;
            }
        }

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
