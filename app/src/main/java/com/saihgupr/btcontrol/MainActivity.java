package com.saihgupr.btcontrol;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView tv = new TextView(this);
        tv.setText("Bluetooth Control Service is ready.\nYou can now use ADB commands.");
        tv.setTextSize(24);
        tv.setPadding(50, 50, 50, 50);
        setContentView(tv);
    }
}
