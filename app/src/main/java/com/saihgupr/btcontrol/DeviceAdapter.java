package com.saihgupr.btcontrol;

import android.bluetooth.BluetoothDevice;
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

    private List<BluetoothDevice> devices = new ArrayList<>();
    private OnDeviceActionListener listener;

    public interface OnDeviceActionListener {
        void onConnect(BluetoothDevice device);
        void onDisconnect(BluetoothDevice device);
    }

    public DeviceAdapter(OnDeviceActionListener listener) {
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

        String deviceName = "Unknown Device";
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            try {
                String name = device.getName();
                if (name != null) deviceName = name;
            } catch (SecurityException e) {
                deviceName = "Unknown (Perm)";
            }
        } else {
            deviceName = "Unknown (No Perm)";
        }

        holder.nameText.setText(deviceName);
        holder.addressText.setText(device.getAddress());

        holder.connectButton.setOnClickListener(v -> {
            if (listener != null) listener.onConnect(device);
        });

        holder.disconnectButton.setOnClickListener(v -> {
            if (listener != null) listener.onDisconnect(device);
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView nameText;
        TextView addressText;
        MaterialButton connectButton;
        MaterialButton disconnectButton;

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.device_name);
            addressText = itemView.findViewById(R.id.device_address);
            connectButton = itemView.findViewById(R.id.btn_connect);
            disconnectButton = itemView.findViewById(R.id.btn_disconnect);
        }
    }
}
