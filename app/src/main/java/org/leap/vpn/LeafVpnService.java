package org.leap.vpn;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LeafVpnService extends VpnService {
    private Thread bgThread;
    private Thread protectThread;
    private Process nsProcess;
    private static final String EXECUTABLE_NAME = "libns.so";

    static {
        System.loadLibrary("leaf");
    }

    private native void runLeaf(String configPath);
    private native void stopLeaf();

    private void stopVpn() {
        // Stop executable file
        stopExecutable();
        stopLeaf();
        stopSelf();
    }

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && "signal_stop_vpn".equals(intent.getAction())) {
                stopVpn();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerReceiver(broadcastReceiver, new IntentFilter("signal_stop_vpn"),
                Context.RECEIVER_VISIBLE_TO_INSTANT_APPS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
    }

    private String getExecutablePath() {
        String abi;
        if (android.os.Build.SUPPORTED_ABIS.length > 0) {
            abi = android.os.Build.SUPPORTED_ABIS[0];
        } else {
            abi = android.os.Build.CPU_ABI;
        }
        
        String path = getApplicationInfo().nativeLibraryDir + "/" + EXECUTABLE_NAME;
        File file = new File(path);
        
        if (file.exists() && !file.canExecute()) {
            boolean success = file.setExecutable(true);
        }
        
        return path;
    }

    private void startExecutable(String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder();
            List<String> commands = new ArrayList<>();
            commands.add(getExecutablePath());
            commands.addAll(Arrays.asList(args));
            pb.command(commands);
            
            // Redirect error output to standard output
            pb.redirectErrorStream(true);
            nsProcess = pb.start();
            
            // Optional: Read output
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(nsProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.d("NsExecutable", line);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void stopExecutable() {
        if (nsProcess != null) {
            nsProcess.destroy();
            nsProcess = null;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start executable
        startExecutable("client","127.0.0.1:22222", "11.22.33.44:443", "www.example.com", "12345678");

        bgThread = new Thread(new Runnable() {
            @Override
            public void run() {
                Builder builder = new Builder();
                try {
                    builder.setSession("leaf")
                            .setMtu(1500)
                            .addAddress("10.255.0.1", 24)
                            .addDnsServer("8.8.8.8")
                            .addRoute("0.0.0.0", 0)
                            .addDisallowedApplication(getPackageName());
                } catch (PackageManager.NameNotFoundException e) {
                    throw new RuntimeException(e);
                }

                android.os.ParcelFileDescriptor tunFd = builder.establish();
                if (tunFd != null) {
                    try {
                        File configFile = new File(getFilesDir(), "config.conf");
                        String configContent = "[General]\n" +
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
                                "FINAL, Socks\n\n";
                        configContent = configContent.replace("REPLACE-ME-WITH-THE-FD", String.valueOf(tunFd.detachFd()));
                        System.out.println(configContent);

                        try (FileOutputStream fos = new FileOutputStream(configFile)) {
                            fos.write(configContent.getBytes());
                        }

                        if (configFile.exists() && configFile.length() > 0) {
                            System.out.println("config file written successfully");
                        } else {
                            System.out.println("config file writing failed");
                        }

                        Os.setenv("LOG_NO_COLOR", "true", true);
                        System.out.println(configFile.getAbsolutePath());
                        runLeaf(configFile.getAbsolutePath());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    System.out.println("VPN interface creation failed");
                }
            }
        });
        bgThread.start();

        return START_NOT_STICKY;
    }
}