package eu.siacs.conversations.vpn;

import android.content.Context;
import android.content.Intent;
import android.net.Ikev2VpnProfile;
import android.net.PlatformVpnProfile;
import android.net.VpnManager;
import android.os.Build;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import java.nio.charset.StandardCharsets;

import eu.siacs.conversations.ui.ConversationsActivity;

public class eccikev2vpn extends AppCompatActivity {

    private static final String TAG = "ConversationsActivity";
    private final static int FCR = 11;
    PlatformVpnProfile pvp;
    //   int typeIkev2IpsecPsk = pvp.TYPE_IKEV2_IPSEC_PSK;
    Ikev2VpnProfile.Builder builder;
    String AID = String.valueOf(ConversationsActivity.getInstance());
    public String adresa_servera = "109.198.0.4";
    //String adresa_servera = "79.175.112.161";
    String identitet = AID; //ovde pokušati da ga mikrotik identifikuje
    String spsk = "Msbbolnicavs323";
    Ikev2VpnProfile profil;
    int requestCode = 1;
    //public static final String EXTRA_VPN_PROFILE_STATE;

    private static final int RESULT_CANCELED = 1;

    public void createVpnBuilder() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder = new Ikev2VpnProfile.Builder(adresa_servera, identitet);
            //  byte[] psk = hexStringToByteArray("test12345");
            byte[] psk = spsk.getBytes(StandardCharsets.UTF_8);
            builder.setAuthPsk(psk);
            builder.setMaxMtu(1400);
            builder.setBypassable(false);
            // builder.setAuthUsernamePassword(user, passwd, null);
            profil = builder.build();
            pvp = profil;
            Log.i(TAG, "kreiran vpn profil");
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (resultCode != FCR) {
            Log.i(TAG, "problem nije fcr");
        }
        Log.i(TAG, "problem jeste fcr");
    }

    public Intent onStartvpn(VpnManager vpnManager, Context cnt) {
        Intent vpnmng = vpnManager.provisionVpnProfile(profil);
        //cnt.startActivity(vpnmng);
        // startActivityForResult(vpnmng, FCR);
        //.startProvisionedVpnProfile();
        return vpnmng;
    }


    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

}
