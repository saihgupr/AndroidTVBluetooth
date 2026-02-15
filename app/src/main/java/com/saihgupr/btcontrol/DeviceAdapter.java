package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;
import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private static final String TAG = "DeviceAdapter";
    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceActionListener listener;
    private BluetoothManagerHelper btHelper;

    public interface OnDeviceActionListener {
        void onConnect(BluetoothDevice device);
        void onDisconnect(BluetoothDevice device);
    }

    public DeviceAdapter(BluetoothManagerHelper btHelper, OnDeviceActionListener listener) {
        this.btHelper = btHelper;
        this.listener = listener;
    }

    public void setDevices(List<BluetoothDevice> newDevices) {
        this.devices = newDevices;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        Context context = holder.itemView.getContext();
        String deviceName = context.getString(R.string.unknown_device);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                String name = device.getName();
                if (name != null) deviceName = name;
            } catch (SecurityException e) {
                // Keep default localized "Unknown Device"
            }
        }

        holder.nameText.setText(deviceName);
        holder.addressText.setText(device.getAddress());

        int state = btHelper.getConnectionState(device);
        boolean isConnected = (state == BluetoothProfile.STATE_CONNECTED);
        boolean isConnecting = (state == BluetoothProfile.STATE_CONNECTING);

        if (isConnected) {
            holder.statusTag.setVisibility(View.VISIBLE);
            holder.statusTag.setText(R.string.status_connected);
            holder.connectButton.setVisibility(View.GONE);
            holder.disconnectButton.setVisibility(View.VISIBLE);
        } else if (isConnecting) {
            holder.statusTag.setVisibility(View.VISIBLE);
            holder.statusTag.setText(R.string.status_connecting);
            holder.connectButton.setVisibility(View.GONE);
            holder.disconnectButton.setVisibility(View.VISIBLE);
            holder.disconnectButton.setEnabled(false);
        } else {
            holder.statusTag.setVisibility(View.GONE);
            holder.connectButton.setVisibility(View.VISIBLE);
            holder.disconnectButton.setVisibility(View.GONE);
        }

        holder.connectButton.setContentDescription(context.getString(R.string.connect_device_description, deviceName));
        holder.disconnectButton.setContentDescription(context.getString(R.string.disconnect_device_description, deviceName));

        // Make the entire card clickable for better TV navigation
        holder.itemView.setFocusable(true);
        holder.itemView.setClickable(true);
        holder.itemView.setOnClickListener(v -> {
            if (isConnected) {
                android.util.Log.d(TAG, "Card clicked (Disconnect): " + device.getName());
                if (listener != null) listener.onDisconnect(device);
            } else if (!isConnecting) {
                android.util.Log.d(TAG, "Card clicked (Connect): " + device.getName());
                if (listener != null) listener.onConnect(device);
            }
        });
        
        // Remove individual button click listeners to avoid conflicts
        holder.connectButton.setOnClickListener(null);
        holder.connectButton.setClickable(false);
        holder.connectButton.setFocusable(false);
        
        holder.disconnectButton.setOnClickListener(null);
        holder.disconnectButton.setClickable(false);
        holder.disconnectButton.setFocusable(false);
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView addressText;
        TextView statusTag;
        MaterialButton connectButton;
        MaterialButton disconnectButton;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.device_name);
            addressText = itemView.findViewById(R.id.device_address);
            statusTag = itemView.findViewById(R.id.status_tag);
            connectButton = itemView.findViewById(R.id.btn_connect);
            disconnectButton = itemView.findViewById(R.id.btn_disconnect);
        }
    }
}
