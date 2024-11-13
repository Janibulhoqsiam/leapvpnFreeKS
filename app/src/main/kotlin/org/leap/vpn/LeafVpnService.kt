package org.leap.vpn

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.system.Os
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*

class LeafVpnService : VpnService() {
    private var bgThread: Thread? = null
    private var nsProcess: Process? = null
    private val EXECUTABLE_NAME = "libns.so"

    init {
        System.loadLibrary("leaf")
    }

    private external fun runLeaf(configPath: String)
    private external fun stopLeaf()

    private fun stopVpn() {
        // Stop executable file
        stopExecutable()
        stopLeaf()
        stopSelf()
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && "signal_stop_vpn" == intent.action) {
                stopVpn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(broadcastReceiver, IntentFilter("signal_stop_vpn"), Context.RECEIVER_VISIBLE_TO_INSTANT_APPS)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun getExecutablePath(): String {
        val abi = if (Build.SUPPORTED_ABIS.isNotEmpty()) {
            Build.SUPPORTED_ABIS[0]
        } else {
            Build.CPU_ABI
        }

        val path = "${applicationInfo.nativeLibraryDir}/$EXECUTABLE_NAME"
        val file = File(path)

        if (file.exists() && !file.canExecute()) {
            file.setExecutable(true)
        }

        return path
    }

    private fun startExecutable(vararg args: String) {
        try {
            val pb = ProcessBuilder()
            val commands = ArrayList<String>()
            commands.add(getExecutablePath())
            commands.addAll(args)
            pb.command(commands)

            // Redirect error output to standard output
            pb.redirectErrorStream(true)
            nsProcess = pb.start()

            // Optional: Read output
            Thread {
                try {
                    BufferedReader(InputStreamReader(nsProcess?.inputStream)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            line?.let { Log.d("NsExecutable", it) }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.start()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopExecutable() {
        nsProcess?.destroy()
        nsProcess = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start executable
        startExecutable("client", "127.0.0.1:22222", "11.22.33.44:443", "www.apple.com", "12345678")

        bgThread = Thread {
            val builder = Builder()
            try {
                builder.setSession("leaf")
                    .setMtu(1500)
                    .addAddress("10.255.0.1", 24)
                    .addDnsServer("8.8.8.8")
                    .addRoute("0.0.0.0", 0)
                    .addDisallowedApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                throw RuntimeException(e)
            }

            val tunFd = builder.establish()
            if (tunFd != null) {
                try {
                    val configFile = File(filesDir, "config.conf")
                    var configContent = "[General]\n" +
                            "loglevel = error\n" +
                            "logoutput = console\n" +
                            "dns-server = 8.8.8.8, 114.114.114.114\n" +
                            "tun-fd = REPLACE-ME-WITH-THE-FD\n" +
                            "[Proxy]\n" +
                            "Direct = direct\n" +
                            "Reject = reject\n" +
                            "Socks = socks, 127.0.0.1, 22222\n" +
                            "[Rule]\n" +
                            "IP-CIDR, 11.22.33.44/32, Direct\n" +
                            "FINAL, Socks\n\n"
                    configContent = configContent.replace("REPLACE-ME-WITH-THE-FD", tunFd.detachFd().toString())

                    FileOutputStream(configFile).use { fos ->
                        fos.write(configContent.toByteArray())
                    }

                    if (configFile.exists() && configFile.length() > 0) {
                        Log.d("LeafVpnService", "Config file written successfully")
                    } else {
                        Log.d("LeafVpnService", "Config file writing failed")
                    }

                    Os.setenv("LOG_NO_COLOR", "true", true)
                    Log.d("LeafVpnService", configFile.absolutePath)
                    runLeaf(configFile.absolutePath)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                Log.e("LeafVpnService", "VPN interface creation failed")
            }
        }
        bgThread?.start()

        return START_NOT_STICKY
    }
}
