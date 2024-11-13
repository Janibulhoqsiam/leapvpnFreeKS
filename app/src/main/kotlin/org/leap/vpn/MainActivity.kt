package org.leap.vpn

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch

class MainActivity : AppCompatActivity() {
    companion object {
        private const val KEY_IS_CONNECTED = "is_connected"
    }

    private var isConnected = false
    private lateinit var connectSwitch: MaterialSwitch
    private val connectionHandler = Handler()
    private var connectionRunnable: Runnable? = null
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // Set system bar padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Initialize connection switch
        connectSwitch = findViewById(R.id.connectSwitch)
        connectSwitch.setOnCheckedChangeListener { _, isChecked ->
            isConnected = isChecked
            if (isChecked) {
                connect()
            } else {
                disconnect()
            }
        }

        // Initialize status text
        statusText = findViewById(R.id.statusText)
        updateStatusText(false)

        // Restore connection state
        savedInstanceState?.let {
            isConnected = it.getBoolean(KEY_IS_CONNECTED, false)
            connectSwitch.isChecked = isConnected
            updateStatusText(isConnected)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_IS_CONNECTED, isConnected)
    }

    private fun connect() {
        statusText.setText(R.string.connecting)

        val intent = VpnService.prepare(this)
        if (intent != null) {
            startActivityForResult(intent, 1)
        } else {
            onActivityResult(1, Activity.RESULT_OK, null)
        }
    }

    private fun disconnect() {
        connectionRunnable?.let { connectionHandler.removeCallbacks(it) }

        sendBroadcast(Intent("signal_stop_vpn"))

        isConnected = false
        updateStatusText(false)
    }

    private fun updateStatusText(connected: Boolean) {
        statusText.setText(if (connected) R.string.connected else R.string.disconnected)
        statusText.setTextColor(getColor(if (connected) R.color.connected_color else R.color.disconnected_color))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK) {
            val intent = Intent(this, LeafVpnService::class.java)
            startService(intent)
            isConnected = true
            connectionRunnable = Runnable {
                isConnected = true
                updateStatusText(true)
            }
            connectionHandler.postDelayed(connectionRunnable!!, 1200)
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }
}
