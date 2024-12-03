package org.leap.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.system.Os
import android.util.Log
import androidx.core.app.NotificationCompat
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LeafVpnService : VpnService() {
    private var serviceJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var nsProcess: Process? = null
    private val EXECUTABLE_NAME = "libns.so"
    private var isRunning = false
    private var configFile: File? = null

    init {
        System.loadLibrary("leaf")
    }

    private external fun runLeaf(configPath: String)
    private external fun stopLeaf()

    private fun stopVpn() {
        try {
            serviceScope.launch(NonCancellable) {
                isRunning = false
                
                // 清理协程资源
                serviceJob?.cancel()
                serviceScope.cancel()
                
                // 清理VPN相关资源
                withContext(Dispatchers.IO) {
                    try {
                        stopLeaf()
                        stopExecutable()
                        
                        // 清理配置文件
                        configFile?.let {
                            if (it.exists()) {
                                it.delete()
                            }
                        }
                        
                        // 清理其他临时文件
                        cleanupTempFiles()
                    } catch (e: Exception) {
                        Log.e("LeafVpnService", "Error during cleanup", e)
                    }
                }
                
                stopSelf()
            }
        } catch (e: Exception) {
            Log.e("LeafVpnService", "Fatal error during VPN shutdown", e)
            stopSelf()
        }
    }

    private fun cleanupTempFiles() {
        try {
            filesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".conf") || file.name.endsWith(".tmp")) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e("LeafVpnService", "Error cleaning temp files", e)
        }
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && "signal_stop_leaf" == intent.action) {
                stopVpn()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(broadcastReceiver, IntentFilter("signal_stop_leaf"), Context.RECEIVER_NOT_EXPORTED)

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "leap_vpn_channel",
                "Leap VPN Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }

        // Create notification
        val notification = NotificationCompat.Builder(this, "leap_vpn_channel")
            .setContentTitle("Leap VPN")
            .setContentText("is running...")
            .setSmallIcon(R.drawable.ic_leapblack)
            .build()

        // Start foreground service with notification
        startForeground(1, notification)
    }

    override fun onDestroy() {
        serviceJob?.cancel()
        serviceScope.cancel()
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
            pb.redirectErrorStream(true)
            nsProcess = pb.start()

            // 使用协程替代Thread读取输出
            serviceScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        BufferedReader(InputStreamReader(nsProcess?.inputStream)).use { reader ->
                            while (isActive) {
                                val line = reader.readLine() ?: break
                                Log.d("NsExecutable", line)
                            }
                        }
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun stopExecutable() {
        nsProcess?.destroy()
        nsProcess = null
    }

    private fun sendStateUpdate(state: String) {
        val intent = Intent("leap_vpn.state_changed")
        intent.putExtra("state", state)
        sendBroadcast(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendStateUpdate("connecting")
        startExecutable("client", "127.0.0.1:22222", "11.22.33.44:443", "www.apple.com", "12345678")

        // 使用协程替代Thread
        serviceJob = serviceScope.launch {
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
                sendStateUpdate("connected")
                withContext(Dispatchers.IO) {
                    try {
                        val configFile = File(filesDir, "config.conf")
                        var configContent = "[General]\n" +
                                "loglevel = error\n" +
                                "logoutput = console\n" +
                                "dns-server = 8.8.8.8, 1.1.1.1\n" +
                                "tun-fd = REPLACE-ME-WITH-THE-FD\n" +
                                "[Proxy]\n" +
                                "Direct = direct\n" +
                                "Reject = reject\n" +
                                "Socks = socks, 127.0.0.1, 22222\n" +
                                "[Rule]\n" +
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
                }
            } else {
                Log.e("LeafVpnService", "VPN interface creation failed")
            }
        }

        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        // 发送断开连接状态
        sendStateUpdate("disconnected")
        // 当系统VPN连接被中断时，清理所有资源
        stopVpn()
    }
}
