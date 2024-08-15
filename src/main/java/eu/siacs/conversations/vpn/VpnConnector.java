package eu.siacs.conversations.vpn;

import static androidx.core.content.ContextCompat.getSystemService;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Ikev2VpnProfile;
import android.net.VpnManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import eu.siacs.conversations.Config;

public class VpnConnector {
    public interface IntentCallback {
        void launch(Intent intent);
    }

    private static final String TAG = VpnConnector.class.getName();

    public static boolean isVpnConnected() {
        String iface = "";
        List<InterfaceAddress> ipAddrs = null;

        boolean connected = false;

        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp()) {
                    iface = networkInterface.getName();
                }

                Log.d(TAG, "IFACE NAME: " + iface);
                if (iface.contains("tun") || iface.contains("ppp") || iface.contains("pptp") || iface.contains("ipsec")) {
                    ipAddrs = networkInterface.getInterfaceAddresses();

                    if (ipAddrs != null) {
                        for (InterfaceAddress inetAddr : ipAddrs) {
                            Log.d(TAG, "IP Addresses: " + inetAddr);
                        }
                    }

                    connected = true;
                }
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }

        return connected;
    }

    private VpnManager _vpnManager;

    private Ikev2VpnProfile _profile;

    private final String _domain;
    private final String _sharedPsk;
    private final String[] _ports;

    public VpnConnector(String domain, String sPsk, String[] ports) {
        _domain = domain;
        _sharedPsk = sPsk;
        _ports = ports;
    }

    public void connect(Context context) {
        connect(context,null);
    }

    public void connect(Context context, IntentCallback callback) {
//        context.getSharedPreferences();

        if (_profile == null) {
            _profile = init(context);

            Intent intent = _vpnManager.provisionVpnProfile(_profile);

            Log.d(TAG, "VPN Profile provisioned");

            if (intent != null) {
                Log.d(TAG, "VPN Intent: " + intent.toString());

                callback.launch(intent);
            } else {
                connectProfile();
            }
        } else {
            connectProfile();
        }
    }

    public void resume() {
        if (_profile != null) {
            Log.d(Config.LOGTAG, "Reconnecting VPN ...");

            connectProfile();
        }
    }

    public void reset() {
        Log.d(Config.LOGTAG, "VPN Profile reset");

        _profile = null;
    }

    private Ikev2VpnProfile init(Context context) {
        _vpnManager = getSystemService(context, VpnManager.class);

        Log.d(TAG, "VPN Manager acquired");

        String res = "";
//        String res = initConnection();
//
//        Log.d(TAG, "VPN Result: " + res);

        Ikev2VpnProfile profile = null;

        if (res != null) {
            try {
                profile = createVpnProfile(context);
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            }
        }

        return profile;
    }

    private Ikev2VpnProfile createVpnProfile(Context context) throws IllegalArgumentException {
        @SuppressLint("HardwareIds")
        String deviceId = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ANDROID_ID);

        Ikev2VpnProfile.Builder builder = new Ikev2VpnProfile.Builder(_domain, deviceId);
        byte[] psk = _sharedPsk.getBytes(StandardCharsets.UTF_8);

        builder.setAuthPsk(psk);
        builder.setMaxMtu(1400);
        builder.setBypassable(false);

        Log.d(TAG, "VPN profile created");

        return builder.build();
    }

    private void connectProfile() {
        try {
            _vpnManager.startProvisionedVpnProfile();

            Log.d(TAG, "Profile connecting ...");

            while (!isVpnConnected()) {
                Log.d(TAG, "VPN Interface connecting...");

                SystemClock.sleep(1000);
            }
            Log.d(TAG, "VPN Interface connected");
        } catch (SecurityException e) {
            e.printStackTrace();
        }
    }

    private String initConnection() {
        Runtime runtime = Runtime.getRuntime();
        ArrayList<Process> processes = new ArrayList<Process>();
        String ncCommand = "/system/bin/nc " + _domain + " ";
        String pingCommand = "/system/bin/ping -c 2 " + _domain;

        try {
            for (String port : _ports) {
                processes.add(runtime.exec(ncCommand + port));
                SystemClock.sleep(10);
            }
            Process pingProcess = runtime.exec(pingCommand);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(pingProcess.getInputStream()));
            String inputLine = bufferedReader.readLine();
            int exitValue = pingProcess.waitFor();
            for (Process process : processes) {
                if (!process.waitFor(100, TimeUnit.MILLISECONDS)) {
                    process.destroy();
                }
            }

            return exitValue == 0 ? inputLine : null;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }
}
