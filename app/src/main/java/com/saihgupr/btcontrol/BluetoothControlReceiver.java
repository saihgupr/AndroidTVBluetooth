package com.saihgupr.btcontrol;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BluetoothControlReceiver extends BroadcastReceiver {

    private static final String TAG = "BtReceiver";
    private static final String ACTION_CONNECT = "com.saihgupr.btcontrol.ACTION_CONNECT";
    private static final String ACTION_DISCONNECT = "com.saihgupr.btcontrol.ACTION_DISCONNECT";
    private static final String EXTRA_ADDRESS = "address";
    private static final String EXTRA_NAME = "name";
    
    // We keep a static instance to keep the helper/proxies alive if possible,
    // though Manifest receivers have short lifecycles.
    // Ideally, this should be a Service, but for a simple "fire and forget" tool,
    // re-initializing the helper (and thus the proxies) mostly works if we give it time to bind.
    private static BluetoothManagerHelper sHelper;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        if (sHelper == null) {
            sHelper = new BluetoothManagerHelper(context.getApplicationContext());
        }

        String address = intent.getStringExtra(EXTRA_ADDRESS);
        String name = intent.getStringExtra(EXTRA_NAME);

        if (address == null && name == null) {
            Log.e(TAG, "No address or name provided");
            return;
        }

        // Normalize address just in case (upper case and fix separators)
        if (address != null) {
            address = address.toUpperCase().replace("-", ":");
        }

        if (ACTION_CONNECT.equals(action)) {
            if (address != null) sHelper.connect(address);
            else sHelper.connectByName(name);
        } else if (ACTION_DISCONNECT.equals(action)) {
            if (address != null) sHelper.disconnect(address);
            else sHelper.disconnectByName(name);
        }
    }
}
