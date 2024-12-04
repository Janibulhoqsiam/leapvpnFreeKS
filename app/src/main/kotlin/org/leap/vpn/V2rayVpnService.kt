package org.leap.vpn

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.LocalSocket
import android.net.LocalSocketAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.StrictMode
import android.system.Os
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import go.Seq
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import libv2ray.Libv2ray
import libv2ray.V2RayPoint
import libv2ray.V2RayVPNServiceSupportsSet
import java.lang.ref.SoftReference
import android.util.Base64
import android.provider.Settings

class V2rayVpnService : VpnService(),ServiceControl {
    private var bgThread: Thread? = null

    companion object {
        private const val VPN_MTU = 1500
        private const val TUN2SOCKS = "libtun2socks.so"
        private fun userAssetPath(context: Context?): String {
            if (context == null)
                return ""
            val extDir = context.getExternalFilesDir("assets")
                ?: return context.getDir("assets", 0).absolutePath
            return extDir.absolutePath
        }
        private fun getDeviceIdForXUDPBaseKey(): String {
            val androidId = Settings.Secure.ANDROID_ID.toByteArray(Charsets.UTF_8)
            return Base64.encodeToString(androidId.copyOf(32), Base64.NO_PADDING.or(Base64.URL_SAFE))
        }

        var serviceControl: SoftReference<ServiceControl>? = null
            set(value) {
                field = value
                Seq.setContext(value?.get()?.getService()?.applicationContext)
                Log.d(BuildConfig.APPLICATION_ID,userAssetPath(value?.get()?.getService()))
                // for xray-core
                // Libv2ray.initV2Env(userAssetPath(value?.get()?.getService()),getDeviceIdForXUDPBaseKey())
                // for v2ray-core
                Libv2ray.initV2Env(userAssetPath(value?.get()?.getService()))
            }
    }

    private lateinit var mInterface: ParcelFileDescriptor
    private lateinit var process: Process
    private var isRunning = false
    private val v2rayPoint: V2RayPoint = Libv2ray.newV2RayPoint(V2RayCallback(), Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1)
    private class V2RayCallback : V2RayVPNServiceSupportsSet {
        override fun shutdown(): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            // called by go
            return try {
                serviceControl.stopService()
                0
            } catch (e: Exception) {
                Log.d(BuildConfig.APPLICATION_ID, e.toString())
                -1
            }
        }

        override fun prepare(): Long {
            return 0
        }

        override fun protect(l: Long): Boolean {
            val serviceControl = serviceControl?.get() ?: return true
            return serviceControl.vpnProtect(l.toInt())
        }

        override fun onEmitStatus(l: Long, s: String?): Long {
            return 0
        }

        override fun setup(s: String): Long {
            val serviceControl = serviceControl?.get() ?: return -1
            return try {
                serviceControl.startService()
                0
            } catch (e: Exception) {
                Log.d(BuildConfig.APPLICATION_ID, e.toString())
                -1
            }
        }
    }

    init {

    }

    private fun copyAssetsToUserAssetPath(context: Context?) {
        if (context == null) return
        val assetManager = context.assets
        val files = assetManager.list("") ?: return

        val destDir = File(userAssetPath(context))
        if (!destDir.exists()) {
            destDir.mkdirs()
        }

        for (filename in files) {
            // Skip directories, only copy files
            if (filename.contains(".")) {
                val inStream = assetManager.open(filename)
                val outFile = File(destDir, filename)
                val outStream = FileOutputStream(outFile)

                val buffer = ByteArray(1024)
                var read: Int
                while (inStream.read(buffer).also { read = it } != -1) {
                    outStream.write(buffer, 0, read)
                }
                inStream.close()
                outStream.flush()
                outStream.close()
            }
        }
    }

    private fun setup(){
        // Close the old interface since the parameters have been changed.
        try {
            mInterface.close()
        } catch (ignored: Exception) {
            // ignored
        }

        val prepare = prepare(this)
        if (prepare != null) {
            return
        }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.requestNetwork(defaultNetworkRequest, defaultNetworkCallback)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setMetered(false)
        }

        try {
            mInterface = builder.establish()!!
            isRunning = true
            runTun2socks()
        } catch (e: Exception) {
            stopVpn()
            e.printStackTrace()
        }
    }
    override fun getService(): Service {
        return this
    }

    override fun startService() {
        setup()
    }

    override fun stopService() {
        stopV2Ray(true)
    }

    override fun vpnProtect(socket: Int): Boolean {
        return protect(socket)
    }

    /**destroy
     * Unfortunately registerDefaultNetworkCallback is going to return our VPN interface: https://android.googlesource.com/platform/frameworks/base/+/dda156ab0c5d66ad82bdcf76cda07cbc0a9c8a2e
     *
     * This makes doing a requestNetwork with REQUEST necessary so that we don't get ALL possible networks that
     * satisfies default network capabilities but only THE default network. Unfortunately we need to have
     * android.permission.CHANGE_NETWORK_STATE to be able to call requestNetwork.
     *
     * Source: https://android.googlesource.com/platform/frameworks/base/+/2df4c7d/services/core/java/com/android/server/ConnectivityService.java#887
     */
    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkRequest by lazy {
        NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
            .build()
    }

    private val connectivity by lazy { getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager }

    @delegate:RequiresApi(Build.VERSION_CODES.P)
    private val defaultNetworkCallback by lazy {
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                // it's a good idea to refresh capabilities
                setUnderlyingNetworks(arrayOf(network))
            }

            override fun onLost(network: Network) {
                setUnderlyingNetworks(null)
            }
        }
    }

    private fun stopVpn() {
        // Stop V2ray
        stopV2Ray(true)

        try {
            Log.d(packageName, "tun2socks destroy")
            process.destroy()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }

        try {
            mInterface.close()
        } catch (ignored: Exception) {
            // ignored
        }
        stopSelf()
        isRunning = false
    }

    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent != null && "signal_stop_leaf" == intent.action) {
                stopVpn()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()

        val policy = StrictMode.ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)
        V2rayVpnService.serviceControl = SoftReference(this)
//        copyAssetsToUserAssetPath(this)

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
        super.onDestroy()
        unregisterReceiver(broadcastReceiver)
    }

    private fun stopV2Ray(isForced: Boolean = true) {
        isRunning = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                connectivity.unregisterNetworkCallback(defaultNetworkCallback)
            } catch (ignored: Exception) {
                // ignored
            }
        }

        try {
//            Log.d(packageName, "tun2socks destroy")
            process.destroy()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }

        stopV2rayPoint()

        if (isForced) {
            //stopSelf has to be called ahead of mInterface.close(). otherwise v2ray core cannot be stooped
            //It's strage but true.
            //This can be verified by putting stopself() behind and call stopLoop and startLoop
            //in a row for several times. You will find that later created v2ray core report port in use
            //which means the first v2ray core somehow failed to stop and release the port.
            stopSelf()

            try {
                mInterface.close()
            } catch (ignored: Exception) {
                // ignored
            }
        }
    }

    fun startV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return
        if (v2rayPoint.isRunning) {
            return
        }

        v2rayPoint.configureFileContent = "{\n" +
                "  \"stats\":{},\n" +
                "  \"log\": {\n" +
                "    \"loglevel\": \"warning\"\n" +
                "  },\n" +
                "  \"policy\":{\n" +
                "      \"levels\": {\n" +
                "        \"8\": {\n" +
                "          \"handshake\": 4,\n" +
                "          \"connIdle\": 300,\n" +
                "          \"uplinkOnly\": 1,\n" +
                "          \"downlinkOnly\": 1\n" +
                "        }\n" +
                "      },\n" +
                "      \"system\": {\n" +
                "        \"statsOutboundUplink\": true,\n" +
                "        \"statsOutboundDownlink\": true\n" +
                "      }\n" +
                "  },\n" +
                "  \"inbounds\": [{\n" +
                "    \"tag\": \"socks\",\n" +
                "    \"port\": 22222,\n" +
                "    \"protocol\": \"socks\",\n" +
                "    \"settings\": {\n" +
                "      \"auth\": \"noauth\",\n" +
                "      \"udp\": true,\n" +
                "      \"userLevel\": 8\n" +
                "    }\n" +
                "  }\n" +
                "],\n" +
                "  \"outbounds\": [{\n" +
                "    \"tag\": \"proxy\",\n" +
                "    \"protocol\": \"vmess\",\n" +
                "    \"settings\": {\n" +
                "      \"vnext\": [\n" +
                "        {\n" +
                "          \"address\": \"v2ray.cool\",\n" +
                "          \"port\": 10086,\n" +
                "          \"users\": [\n" +
                "            {\n" +
                "              \"id\": \"a3482e88-686a-4a58-8126-99c9df64b7bf\",\n" +
                "              \"alterId\": 0,\n" +
                "              \"security\": \"auto\",\n" +
                "              \"level\": 8\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ],\n" +
                "      \"servers\": [\n" +
                "        {\n" +
                "        \"address\": \"v2ray.cool\",\n" +
                "        \"method\": \"chacha20\",\n" +
                "        \"ota\": false,\n" +
                "        \"password\": \"123456\",\n" +
                "        \"port\": 10086,\n" +
                "        \"level\": 8\n" +
                "      }\n" +
                "      ]\n" +
                "    },\n" +
                "    \"streamSettings\": {\n" +
                "      \"network\": \"tcp\"\n" +
                "    },\n" +
                "    \"mux\": {\n" +
                "      \"enabled\": false\n" +
                "    }\n" +
                "  },\n" +
                "  {\n" +
                "    \"protocol\": \"freedom\",\n" +
                "    \"settings\": {\n" +
                "      \"domainStrategy\": \"UseIP\"\n" +
                "    },\n" +
                "    \"tag\": \"direct\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"protocol\": \"blackhole\",\n" +
                "    \"tag\": \"block\",\n" +
                "    \"settings\": {\n" +
                "      \"response\": {\n" +
                "        \"type\": \"http\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "  ],\n" +
                "  \"routing\": {\n" +
                "      \"domainStrategy\": \"IPIfNonMatch\",\n" +
                "      \"rules\": []\n" +
                "  },\n" +
                "  \"dns\": {\n" +
                "      \"hosts\": {},\n" +
                "      \"servers\": []\n" +
                "  }\n" +
                "}\n"
        v2rayPoint.domainName = "v2ray.cool:443"

        try {
            v2rayPoint.runLoop(false)
        } catch (e: Exception) {
            Log.e(BuildConfig.APPLICATION_ID, e.toString())
        }
    }

    fun stopV2rayPoint() {
        val service = serviceControl?.get()?.getService() ?: return

        if (v2rayPoint.isRunning) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    v2rayPoint.stopLoop()
                } catch (e: Exception) {
                    Log.e(BuildConfig.APPLICATION_ID, e.toString())
                }
            }
        }
    }

    private fun sendStateUpdate(state: String) {
        val intent = Intent("leap_vpn.state_changed")
        intent.putExtra("state", state)
        sendBroadcast(intent)
    }

    private fun runTun2socks() {
        val socksPort = 22222
        val cmd = arrayListOf(
            File(applicationContext.applicationInfo.nativeLibraryDir, TUN2SOCKS).absolutePath,
            "--netif-ipaddr", "10.255.0.1",
            "--netif-netmask", "255.255.255.0",
            "--socks-server-addr", "127.0.0.1:${socksPort}",
            "--tunmtu", VPN_MTU.toString(),
            "--sock-path", File(applicationContext.filesDir, "sock_path").absolutePath,//"sock_path",//
            "--enable-udprelay",
            "--loglevel", "notice"
        )

        Log.d(packageName, cmd.toString())

        try {
            val proBuilder = ProcessBuilder(cmd)
            proBuilder.redirectErrorStream(true)
            process = proBuilder
                .directory(applicationContext.filesDir)
                .start()
            Thread {
//                Log.d(packageName, "$TUN2SOCKS check")
                process.waitFor()
//                Log.d(packageName, "$TUN2SOCKS exited")
                if (isRunning) {
//                    Log.d(packageName, "$TUN2SOCKS restart")
                    runTun2socks()
                }
            }.start()
            Log.d(packageName, process.toString())

            sendFd()
        } catch (e: Exception) {
            Log.d(packageName, e.toString())
        }
    }

    private fun sendFd() {
        val fd = mInterface.fileDescriptor
        val path = File(applicationContext.filesDir, "sock_path").absolutePath

        CoroutineScope(Dispatchers.IO).launch {
            var tries = 0
            while (true) try {
                Thread.sleep(50L shl tries)
                Log.d(packageName, "sendFd tries: $tries")
                LocalSocket().use { localSocket ->
                    localSocket.connect(LocalSocketAddress(path, LocalSocketAddress.Namespace.FILESYSTEM))
                    localSocket.setFileDescriptorsForSend(arrayOf(fd))
                    localSocket.outputStream.write(42)
                }
                break
            } catch (e: Exception) {
                Log.d(packageName, e.toString())
                if (tries > 5) break
                tries += 1
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        sendStateUpdate("connecting")
        // Start executable
        startV2rayPoint()
        sendStateUpdate("connected")
        return START_STICKY
    }

    override fun onRevoke() {
        super.onRevoke()
        // Send disconnected state
        sendStateUpdate("disconnected")
        // When the system VPN connection is interrupted, clean up all resources
        stopVpn()
    }
}
