package org.leap.vpn;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.materialswitch.MaterialSwitch;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_IS_CONNECTED = "is_connected";
    
    private boolean isConnected = false;
    private MaterialSwitch connectSwitch;
    private Handler connectionHandler = new Handler();
    private Runnable connectionRunnable;
    private TextView statusText;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Set system bar padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize connection switch
        connectSwitch = findViewById(R.id.connectSwitch);
        connectSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isConnected = isChecked;
            if (isChecked) {
                connect();
            } else {
                disconnect();
            }
        });

        // Initialize status text
        statusText = findViewById(R.id.statusText);
        updateStatusText(false);

        // Restore connection state
        if (savedInstanceState != null) {
            isConnected = savedInstanceState.getBoolean(KEY_IS_CONNECTED, false);
            connectSwitch.setChecked(isConnected);
            updateStatusText(isConnected);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_IS_CONNECTED, isConnected);
    }

    private void connect() {
        statusText.setText(R.string.connecting);
        
        Intent intent = VpnService.prepare(this);
        if (intent != null) {
            startActivityForResult(intent, 1);
        } else {
            onActivityResult(1, Activity.RESULT_OK, null);
        }


    }

    private void disconnect() {
        if (connectionRunnable != null) {
            connectionHandler.removeCallbacks(connectionRunnable);
        }
        
        sendBroadcast(new Intent("signal_stop_vpn"));

        isConnected = false;
        updateStatusText(false);
    }

    private void updateStatusText(boolean connected) {
        statusText.setText(connected ? R.string.connected : R.string.disconnected);
        statusText.setTextColor(getColor(connected ? R.color.connected_color : R.color.disconnected_color));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            Intent intent = new Intent(this, LeafVpnService.class);
            startService(intent);
            isConnected = true;
            connectionRunnable = () -> {
                isConnected = true;
                updateStatusText(true);
            };
            connectionHandler.postDelayed(connectionRunnable, 1200);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}
