	/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package eu.siacs.conversations.ui;

import static eu.siacs.conversations.ui.ConversationFragment.REQUEST_DECRYPT_PGP;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Ikev2VpnProfile;
import android.net.PlatformVpnProfile;
import android.net.Uri;
import android.net.VpnManager;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ValueCallback;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.databinding.DataBindingUtil;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.openintents.openpgp.util.OpenPgpApi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import eu.siacs.conversations.Config;
import eu.siacs.conversations.R;
import eu.siacs.conversations.crypto.OmemoSetting;
import eu.siacs.conversations.databinding.ActivityConversationsBinding;
import eu.siacs.conversations.entities.Contact;
import eu.siacs.conversations.entities.Conversation;
import eu.siacs.conversations.entities.Conversational;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.interfaces.OnBackendConnected;
import eu.siacs.conversations.ui.interfaces.OnConversationArchived;
import eu.siacs.conversations.ui.interfaces.OnConversationRead;
import eu.siacs.conversations.ui.interfaces.OnConversationSelected;
import eu.siacs.conversations.ui.interfaces.OnConversationsListItemUpdated;
import eu.siacs.conversations.ui.util.ActivityResult;
import eu.siacs.conversations.ui.util.ConversationMenuConfigurator;
import eu.siacs.conversations.ui.util.MenuDoubleTabUtil;
import eu.siacs.conversations.ui.util.PendingItem;
import eu.siacs.conversations.ui.util.ToolbarUtils;
import eu.siacs.conversations.utils.ExceptionHelper;
import eu.siacs.conversations.utils.SignupUtils;
import eu.siacs.conversations.utils.XmppUri;
import eu.siacs.conversations.vpn.eccikev2vpn;
import eu.siacs.conversations.xmpp.Jid;
import eu.siacs.conversations.xmpp.OnUpdateBlocklist;

public class ConversationsActivity extends XmppActivity implements OnConversationSelected, OnConversationArchived, OnConversationsListItemUpdated, OnConversationRead, XmppConnectionService.OnAccountUpdate, XmppConnectionService.OnConversationUpdate, XmppConnectionService.OnRosterUpdate, OnUpdateBlocklist, XmppConnectionService.OnShowErrorToast, XmppConnectionService.OnAffiliationChanged {

    public static final String ACTION_VIEW_CONVERSATION = "eu.siacs.conversations.action.VIEW";
    public static final String EXTRA_CONVERSATION = "conversationUuid";
    public static final String EXTRA_DOWNLOAD_UUID = "eu.siacs.conversations.download_uuid";
    public static final String EXTRA_AS_QUOTE = "eu.siacs.conversations.as_quote";
    public static final String EXTRA_NICK = "nick";
    public static final String EXTRA_IS_PRIVATE_MESSAGE = "pm";
    public static final String EXTRA_DO_NOT_APPEND = "do_not_append";
    public static final String EXTRA_POST_INIT_ACTION = "post_init_action";
    public static final String POST_ACTION_RECORD_VOICE = "record_voice";
    public static final String EXTRA_TYPE = "type";

    private static final List<String> VIEW_AND_SHARE_ACTIONS = Arrays.asList(
            ACTION_VIEW_CONVERSATION,
            Intent.ACTION_SEND,
            Intent.ACTION_SEND_MULTIPLE
    );
    private static final int VPN_CON = 443;
    private final static int FCR = 11341;
    private static int VPN_CONNECTED = 0;
    String androidID = null;
    public static ConversationsActivity instance;
    public Context context;
    ConversationsActivity mActivity;
    private static final String TAG = ConversationsActivity.class.getSimpleName();
    Intent vpnmng;
    private String mCM;
    private ValueCallback mUM;
    private ValueCallback<Uri[]> mUMA;

    public static final int REQUEST_OPEN_MESSAGE = 0x9876;
    public static final int REQUEST_PLAY_PAUSE = 0x5432;


    //secondary fragment (when holding the conversation, must be initialized before refreshing the overview fragment
    private static final @IdRes
    int[] FRAGMENT_ID_NOTIFICATION_ORDER = {R.id.secondary_fragment, R.id.main_fragment};
    private final PendingItem<Intent> pendingViewIntent = new PendingItem<>();
    private final PendingItem<ActivityResult> postponedActivityResult = new PendingItem<>();
    private ActivityConversationsBinding binding;
    private boolean mActivityPaused = true;
    private final AtomicBoolean mRedirectInProcess = new AtomicBoolean(false);

    private static boolean isViewOrShareIntent(Intent i) {
        Log.d(Config.LOGTAG, "action: " + (i == null ? null : i.getAction()));
        return i != null && VIEW_AND_SHARE_ACTIONS.contains(i.getAction()) && i.hasExtra(EXTRA_CONVERSATION);
    }

    private static Intent createLauncherIntent(Context context) {
        final Intent intent = new Intent(context, ConversationsActivity.class);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        return intent;
    }

    @Override
    protected void refreshUiReal() {
        invalidateOptionsMenu();
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            refreshFragment(id);
        }
    }

    @Override
    protected void onBackendConnected() {
        if (performRedirectIfNecessary(true)) {
            return;
        }
        xmppConnectionService.getNotificationService().setIsInForeground(true);
        final Intent intent = pendingViewIntent.pop();
        if (intent != null) {
            if (processViewIntent(intent)) {
                if (binding.secondaryFragment != null) {
                    notifyFragmentOfBackendConnected(R.id.main_fragment);
                }
                invalidateActionBarTitle();
                return;
            }
        }
        for (@IdRes int id : FRAGMENT_ID_NOTIFICATION_ORDER) {
            notifyFragmentOfBackendConnected(id);
        }

        final ActivityResult activityResult = postponedActivityResult.pop();
        if (activityResult != null) {
            handleActivityResult(activityResult);
        }

        invalidateActionBarTitle();
        if (binding.secondaryFragment != null && ConversationFragment.getConversation(this) == null) {
            Conversation conversation = ConversationsOverviewFragment.getSuggestion(this);
            if (conversation != null) {
                openConversation(conversation, null);
            }
        }
        showDialogsIfMainIsOverview();
    }

    private boolean performRedirectIfNecessary(boolean noAnimation) {
        return performRedirectIfNecessary(null, noAnimation);
    }

    private boolean performRedirectIfNecessary(final Conversation ignore, final boolean noAnimation) {
        if (xmppConnectionService == null) {
            return false;
        }
        boolean isConversationsListEmpty = xmppConnectionService.isConversationsListEmpty(ignore);
        if (isConversationsListEmpty && mRedirectInProcess.compareAndSet(false, true)) {
            final Intent intent = SignupUtils.getRedirectionIntent(this);
            if (noAnimation) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            }
            runOnUiThread(() -> {
                startActivity(intent);
                if (noAnimation) {
                    overridePendingTransition(0, 0);
                }
            });
        }
        return mRedirectInProcess.get();
    }

    private void showDialogsIfMainIsOverview() {
        if (xmppConnectionService == null) {
            return;
        }
        final Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            if (ExceptionHelper.checkForCrash(this)) {
                return;
            }
            if (openBatteryOptimizationDialogIfNeeded()) {
                return;
            }
            requestNotificationPermissionIfNeeded();
        }
    }

    private String getBatteryOptimizationPreferenceKey() {
        @SuppressLint("HardwareIds") String device = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        return "show_battery_optimization" + (device == null ? "" : device);
    }

    private void setNeverAskForBatteryOptimizationsAgain() {
        getPreferences().edit().putBoolean(getBatteryOptimizationPreferenceKey(), false).apply();
    }

    private boolean openBatteryOptimizationDialogIfNeeded() {
        if (isOptimizingBattery() && getPreferences().getBoolean(getBatteryOptimizationPreferenceKey(), true)) {
            final MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
            builder.setTitle(R.string.battery_optimizations_enabled);
            builder.setMessage(getString(R.string.battery_optimizations_enabled_dialog, getString(R.string.app_name)));
            builder.setPositiveButton(R.string.next, (dialog, which) -> {
                final Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                final Uri uri = Uri.parse("package:" + getPackageName());
                intent.setData(uri);
                try {
                    startActivityForResult(intent, REQUEST_BATTERY_OP);
                } catch (final ActivityNotFoundException e) {
                    Toast.makeText(this, R.string.device_does_not_support_battery_op, Toast.LENGTH_SHORT).show();
                }
            });
            builder.setOnDismissListener(dialog -> setNeverAskForBatteryOptimizationsAgain());
            final AlertDialog dialog = builder.create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
            return true;
        }
        return false;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_POST_NOTIFICATION);
        }
    }

    private void notifyFragmentOfBackendConnected(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof OnBackendConnected callback) {
            callback.onBackendConnected();
        }
    }

    private void refreshFragment(@IdRes int id) {
        final Fragment fragment = getFragmentManager().findFragmentById(id);
        if (fragment instanceof XmppFragment xmppFragment) {
            xmppFragment.refresh();
        }
    }

    private boolean processViewIntent(Intent intent) {
        final String uuid = intent.getStringExtra(EXTRA_CONVERSATION);
        final Conversation conversation = uuid != null ? xmppConnectionService.findConversationByUuid(uuid) : null;
        if (conversation == null) {
            Log.d(Config.LOGTAG, "unable to view conversation with uuid:" + uuid);
            return false;
        }
        openConversation(conversation, intent.getExtras());
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        UriHandlerActivity.onRequestPermissionResult(this, requestCode, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switch (requestCode) {
                    case REQUEST_OPEN_MESSAGE:
                        refreshUiReal();
                        ConversationFragment.openPendingMessage(this);
                        break;
                    case REQUEST_PLAY_PAUSE:
                        ConversationFragment.startStopPending(this);
                        break;
                }
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, final Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ActivityResult activityResult = ActivityResult.of(requestCode, resultCode, data);
        if (xmppConnectionService != null) {
            handleActivityResult(activityResult);
        } else {
            this.postponedActivityResult.push(activityResult);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            Uri[] results = null;
            //Check if response is positive

            if (resultCode == Activity.RESULT_OK) {
                if (requestCode == VPN_CON) {
                    VPN_CONNECTED = 1;
                    return;
                }
                if (requestCode == FCR) {
                    if (null == mUMA) {
                        return;
                    }
                    if (data == null || data.getData() == null) {
                        //Capture Photo if no image available
                        if (mCM != null) {
                            results = new Uri[]{Uri.parse(mCM)};
                        }
                    } else {
                        String dataString = data.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                }
            }
            mUMA.onReceiveValue(results);
            mUMA = null;

        } else {
            if (requestCode == FCR) {
                if (null == mUM) return;
                Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
                mUM.onReceiveValue(result);
                mUM = null;
            }
        }
    }

    private void handleActivityResult(final ActivityResult activityResult) {
        if (activityResult.resultCode == Activity.RESULT_OK) {
            handlePositiveActivityResult(activityResult.requestCode, activityResult.data);
        } else {
            handleNegativeActivityResult(activityResult.requestCode);
        }
        if (activityResult.requestCode == REQUEST_BATTERY_OP) {
            // the result code is always 0 even when battery permission were granted
            requestNotificationPermissionIfNeeded();
            XmppConnectionService.toggleForegroundService(xmppConnectionService);
        }
    }

    private void handleNegativeActivityResult(int requestCode) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                if (conversation == null) {
                    break;
                }
                conversation.getAccount().getPgpDecryptionService().giveUpCurrentDecryption();
                break;
            case REQUEST_BATTERY_OP:
                setNeverAskForBatteryOptimizationsAgain();
                break;
        }
    }

    private void handlePositiveActivityResult(int requestCode, final Intent data) {
        Conversation conversation = ConversationFragment.getConversationReliable(this);
        if (conversation == null) {
            Log.d(Config.LOGTAG, "conversation not found");
            return;
        }
        switch (requestCode) {
            case REQUEST_DECRYPT_PGP:
                conversation.getAccount().getPgpDecryptionService().continueDecryption(data);
                break;
            case REQUEST_CHOOSE_PGP_ID:
                long id = data.getLongExtra(OpenPgpApi.EXTRA_SIGN_KEY_ID, 0);
                if (id != 0) {
                    conversation.getAccount().setPgpSignId(id);
                    announcePgp(conversation.getAccount(), null, null, onOpenPGPKeyPublished);
                } else {
                    choosePgpSignId(conversation.getAccount());
                }
                break;
            case REQUEST_ANNOUNCE_PGP:
                announcePgp(conversation.getAccount(), conversation, data, onOpenPGPKeyPublished);
                break;
        }
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConversationMenuConfigurator.reloadFeatures(this);
        OmemoSetting.load(this);
        this.binding = DataBindingUtil.setContentView(this, R.layout.activity_conversations);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        setSupportActionBar(binding.toolbar);
        configureActionBar(getSupportActionBar());
        this.getFragmentManager().addOnBackStackChangedListener(this::invalidateActionBarTitle);
        this.getFragmentManager().addOnBackStackChangedListener(this::showDialogsIfMainIsOverview);
        this.initializeFragments();
        this.invalidateActionBarTitle();
        final Intent intent;
        if (savedInstanceState == null) {
            intent = getIntent();
        } else {
            intent = savedInstanceState.getParcelable("intent");
        }
        if (isViewOrShareIntent(intent)) {
            pendingViewIntent.push(intent);
            setIntent(createLauncherIntent(this));
        }
        androidID = Settings.System.getString(this.getContentResolver(), Settings.Secure.ANDROID_ID);
        //******************VPN*****************//
        instance = this;
        context = this;
        mActivity = ConversationsActivity.this;
        // Show the PIN dialog on startup
       showPinDialog();
       Boolean vpn_connected = vpn(); //provera da li je vpn već zakačen
      if (vpn_connected == false) {
           konektuj(context, vpn_connected);
      }
      Log.i(TAG, "konektovanje./././././././././");
//        vpnManager = new OpenVPNManager(this);
//        vpnManager.startVPN();
    }

    //******************************TRAZENJE VALIDNE IP ADRESE*******************//
    @RequiresApi(api = Build.VERSION_CODES.O)
    public String isURLReachableA(String domain) {
        Runtime runtime = Runtime.getRuntime();
        String inputLine = "";
        String konekcija = "";
        String port = "17441";
        String port2 = "45781";
        String port3 = "26884";
        String komanda = "/system/bin/nc " + domain + " " + port;
        String komanda2 = "/system/bin/nc " + domain + " " + port2;
        String komanda3 = "/system/bin/nc " + domain + " " + port3;
        String komanda4 = "/system/bin/ping -c 2 " + domain;
        try {
            Process ipProcess = runtime.exec(komanda);
            SystemClock.sleep(10);
            Process ipProcess2 = runtime.exec(komanda2);
            SystemClock.sleep(10);
            Process ipProcess3 = runtime.exec(komanda3);
            SystemClock.sleep(10);
            Process ipProcess4 = runtime.exec(komanda4);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ipProcess4.getInputStream()));
            inputLine = bufferedReader.readLine();
            int exitValue = ipProcess4.waitFor();
            //return (exitValue == 0);
            if (exitValue == 0) {
                konekcija = inputLine;
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
            } else {
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
                konekcija = "false";
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return konekcija;
    }

    public String isURLReachableB(String domain) {
        Runtime runtime = Runtime.getRuntime();
        String inputLine = "";
        String konekcija = "";
        String port = "34881";
        String port2 = "64771";
        String port3 = "21336";
        String komanda = "/system/bin/nc " + domain + " " + port;
        String komanda2 = "/system/bin/nc " + domain + " " + port2;
        String komanda3 = "/system/bin/nc " + domain + " " + port3;
        String komanda4 = "/system/bin/ping -c 2 " + domain;
        try {
            Process ipProcess = runtime.exec(komanda);
            SystemClock.sleep(10);
            Process ipProcess2 = runtime.exec(komanda2);
            SystemClock.sleep(10);
            Process ipProcess3 = runtime.exec(komanda3);
            SystemClock.sleep(10);
            Process ipProcess4 = runtime.exec(komanda4);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ipProcess4.getInputStream()));
            inputLine = bufferedReader.readLine();
            int exitValue = ipProcess4.waitFor();
            //return (exitValue == 0);
            if (exitValue == 0) {
                konekcija = inputLine;
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
            } else {
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
                konekcija = "false";
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return konekcija;
    }

    public String isURLReachableD(String domain) {
        Runtime runtime = Runtime.getRuntime();
        String inputLine = "";
        String konekcija = "";
        String port = "64771";
        String port2 = "49331";
        String port3 = "50608";
        String komanda = "/system/bin/nc " + domain + " " + port;
        String komanda2 = "/system/bin/nc " + domain + " " + port2;
        String komanda3 = "/system/bin/nc " + domain + " " + port3;
        String komanda4 = "/system/bin/ping -c 2 " + domain;
        try {
            Process ipProcess = runtime.exec(komanda);
            SystemClock.sleep(10);
            Process ipProcess2 = runtime.exec(komanda2);
            SystemClock.sleep(10);
            Process ipProcess3 = runtime.exec(komanda3);
            SystemClock.sleep(10);
            Process ipProcess4 = runtime.exec(komanda4);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(ipProcess4.getInputStream()));
            inputLine = bufferedReader.readLine();
            int exitValue = ipProcess4.waitFor();
            //return (exitValue == 0);
            if (exitValue == 0) {
                konekcija = inputLine;
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
            } else {
                if (!ipProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess.destroy();
                }
                if (!ipProcess2.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess2.destroy();
                }
                if (!ipProcess3.waitFor(100, TimeUnit.MILLISECONDS)) {
                    ipProcess3.destroy();
                }
                konekcija = "false";
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return konekcija;
    }

    public String parsiraj_adresu(String response) {
        String ipAdresa = "";
        ipAdresa = response.substring(response.indexOf("(") + 1, response.indexOf(")"));
        return ipAdresa;
    }

    public String parsiraj_ip_adresu() {
        String lokacija_a = "f0260e90cf00.sn.mynetname.net";
        String lokacija_b = "f0260eb05672.sn.mynetname.net";
        // String lokacija_c = "e14e0d3905c9.sn.mynetname.net";
        String lokacija_d = "f0260e756836.sn.mynetname.net";
        String ip_adresa = "";

        String mreza = isURLReachableA(lokacija_a);
        if (mreza == "false") {
            mreza = isURLReachableB(lokacija_b);
            if (mreza == "false") {
                mreza = isURLReachableD(lokacija_d);
                if (mreza == "false") {
                    ip_adresa = "109.198.0.4";
                } else {
                    ip_adresa = parsiraj_adresu(mreza);
                }
            } else {
                ip_adresa = parsiraj_adresu(mreza);
            }
        } else {
            ip_adresa = parsiraj_adresu(mreza);
        }
        return ip_adresa;
    }

    public String pronadji_ip_adresu() {
        String lokacija_a = "109.198.0.4";
        String lokacija_b = "79.175.112.244";
        // String lokacija_c = "109.111.239.54";
        String lokacija_d = "79.175.112.161";

        String ip_adresa = "";

        String mreza = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            mreza = isURLReachableA(lokacija_a);
        }
        if (mreza == "false") {
            mreza = isURLReachableB(lokacija_b);
            if (mreza == "false") {
                mreza = isURLReachableD(lokacija_d);
                if (mreza == "false") {
                    ip_adresa = parsiraj_ip_adresu();
                } else {
                    ip_adresa = parsiraj_adresu(mreza);
                }
            } else {
                ip_adresa = parsiraj_adresu(mreza);
            }
        } else {
            ip_adresa = parsiraj_adresu(mreza);
        }
        return ip_adresa;
    }

    //******************************TRAZENJE VALIDNE IP ADRESE END***************//
    //***********************************IkeV2 IPSEC*****************************//
    public boolean vpn() {
        String iface = "";
        try {
            for (NetworkInterface networkInterface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (networkInterface.isUp())
                    iface = networkInterface.getName();
                Log.d("DEBUG", "IFACE NAME: " + iface);
                if (iface.contains("tun") || iface.contains("ppp") || iface.contains("pptp") || iface.contains("ipsec")) {
                    return true;
                }
            }
        } catch (SocketException e1) {
            e1.printStackTrace();
        }
        return false;
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    public eccikev2vpn connect_vpn(Boolean vpn_connected, Context context, VpnManager vpnManager, eccikev2vpn vpn) throws IOException {
        // eccikev2vpn vpn = new eccikev2vpn();
        if (vpn_connected == false) {
            String radna_ip_adresa = pronadji_ip_adresu();

            vpn.adresa_servera = radna_ip_adresa;
            vpn.createVpnBuilder();
            vpnmng = vpn.onStartvpn(vpnManager, context);

            if (vpnmng != null) {
                startActivityForResult(vpnmng, VPN_CON);
            }
        }
        return vpn;
        //SystemClock.sleep(1000);
    }

    public void connecting_profile(VpnManager vpn_mng) {
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                vpn_mng.startProvisionedVpnProfile(); //ovde se konektuje profil
            }
        });
        thread.start();
    }

    public void konektuj(Context context, Boolean vpn_connected) {
        VpnManager vpnManager = (VpnManager) getSystemService(Context.VPN_MANAGEMENT_SERVICE);
        if (vpn_connected == false) {
            eccikev2vpn ikev2 = new eccikev2vpn();
            try {
                ikev2 = connect_vpn(vpn_connected, context, vpnManager, ikev2);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (vpnmng != null) {
                do {
                    vpnmng = ikev2.onStartvpn(vpnManager, context);
                } while (vpnmng != null);
            }

            try {
                connecting_profile(vpnManager);
            } catch (Throwable e) {
                e.printStackTrace();
            }

            do {
                vpn_connected = vpn();
                SystemClock.sleep(1000);
            } while (vpn_connected == false);
            Log.i(TAG, "konektovanje..*.*.*.*.*.*.");
        }
    }

    //***********************************IkeV2 IPSEC END******************************//
    public static ConversationsActivity getInstance() {
        return instance;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_conversations, menu);
        final MenuItem qrCodeScanMenuItem = menu.findItem(R.id.action_scan_qr_code);
        if (qrCodeScanMenuItem != null) {
            if (isCameraFeatureAvailable()) {
                Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
                boolean visible = getResources().getBoolean(R.bool.show_qr_code_scan)
                        && fragment instanceof ConversationsOverviewFragment;
                qrCodeScanMenuItem.setVisible(visible);
            } else {
                qrCodeScanMenuItem.setVisible(false);
            }
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onConversationSelected(Conversation conversation) {
        clearPendingViewIntent();
        if (ConversationFragment.getConversation(this) == conversation) {
            Log.d(Config.LOGTAG, "ignore onConversationSelected() because conversation is already open");
            return;
        }
        openConversation(conversation, null);
    }

    public void clearPendingViewIntent() {
        if (pendingViewIntent.clear()) {
            Log.e(Config.LOGTAG, "cleared pending view intent");
        }
    }

    private void displayToast(final String msg) {
        runOnUiThread(() -> Toast.makeText(ConversationsActivity.this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    public void onAffiliationChangedSuccessful(Jid jid) {

    }

    @Override
    public void onAffiliationChangeFailed(Jid jid, int resId) {
        displayToast(getString(resId, jid.asBareJid().toString()));
    }

    private void openConversation(Conversation conversation, Bundle extras) {
        final FragmentManager fragmentManager = getFragmentManager();
        executePendingTransactions(fragmentManager);
        ConversationFragment conversationFragment = (ConversationFragment) fragmentManager.findFragmentById(R.id.secondary_fragment);
        final boolean mainNeedsRefresh;
        if (conversationFragment == null) {
            mainNeedsRefresh = false;
            final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
            if (mainFragment instanceof ConversationFragment) {
                conversationFragment = (ConversationFragment) mainFragment;
            } else {
                conversationFragment = new ConversationFragment();
                FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
                fragmentTransaction.replace(R.id.main_fragment, conversationFragment);
                fragmentTransaction.addToBackStack(null);
                try {
                    fragmentTransaction.commit();
                } catch (IllegalStateException e) {
                    Log.w(Config.LOGTAG, "sate loss while opening conversation", e);
                    //allowing state loss is probably fine since view intents et all are already stored and a click can probably be 'ignored'
                    return;
                }
            }
        } else {
            mainNeedsRefresh = true;
        }
        conversationFragment.reInit(conversation, extras == null ? new Bundle() : extras);
        if (mainNeedsRefresh) {
            refreshFragment(R.id.main_fragment);
        }
        invalidateActionBarTitle();
    }

    private static void executePendingTransactions(final FragmentManager fragmentManager) {
        try {
            fragmentManager.executePendingTransactions();
        } catch (final Exception e) {
            Log.e(Config.LOGTAG,"unable to execute pending fragment transactions");
        }
    }

    public boolean onXmppUriClicked(Uri uri) {
        XmppUri xmppUri = new XmppUri(uri);
        if (xmppUri.isValidJid() && !xmppUri.hasFingerprints()) {
            final Conversation conversation = xmppConnectionService.findUniqueConversationByJid(xmppUri);
            if (conversation != null) {
                openConversation(conversation, null);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuDoubleTabUtil.shouldIgnoreTap()) {
            return false;
        }
        switch (item.getItemId()) {
            case android.R.id.home:
                FragmentManager fm = getFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    try {
                        fm.popBackStack();
                    } catch (IllegalStateException e) {
                        Log.w(Config.LOGTAG, "Unable to pop back stack after pressing home button");
                    }
                    return true;
                }
                break;
            case R.id.action_scan_qr_code:
                UriHandlerActivity.scan(this);
                return true;
            case R.id.action_search_all_conversations:
                startActivity(new Intent(this, SearchActivity.class));
                return true;
            case R.id.action_search_this_conversation:
                final Conversation conversation = ConversationFragment.getConversation(this);
                if (conversation == null) {
                    return true;
                }
                final Intent intent = new Intent(this, SearchActivity.class);
                intent.putExtra(SearchActivity.EXTRA_CONVERSATION_UUID, conversation.getUuid());
                startActivity(intent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent keyEvent) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP && keyEvent.isCtrlPressed()) {
            final ConversationFragment conversationFragment = ConversationFragment.get(this);
            if (conversationFragment != null && conversationFragment.onArrowUpCtrlPressed()) {
                return true;
            }
        }
        return super.onKeyDown(keyCode, keyEvent);
    }

    @Override
    public void onSaveInstanceState(final Bundle savedInstanceState) {
        final Intent pendingIntent = pendingViewIntent.peek();
        savedInstanceState.putParcelable("intent", pendingIntent != null ? pendingIntent : getIntent());
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onStart() {
        super.onStart();
        mRedirectInProcess.set(false);
    }

    @Override
    protected void onNewIntent(final Intent intent) {
        super.onNewIntent(intent);
        if (isViewOrShareIntent(intent)) {
            if (xmppConnectionService != null) {
                clearPendingViewIntent();
                processViewIntent(intent);
            } else {
                pendingViewIntent.push(intent);
            }
        }
        setIntent(createLauncherIntent(this));
    }

    @Override
    public void onPause() {
        this.mActivityPaused = true;
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        this.mActivityPaused = false;
    }

    private void initializeFragments() {
        final FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (mainFragment != null) {
            if (binding.secondaryFragment != null) {
                if (mainFragment instanceof ConversationFragment) {
                    getFragmentManager().popBackStack();
                    transaction.remove(mainFragment);
                    transaction.commit();
                    fragmentManager.executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.secondary_fragment, mainFragment);
                    transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
                    transaction.commit();
                    return;
                }
            } else {
                if (secondaryFragment instanceof ConversationFragment) {
                    transaction.remove(secondaryFragment);
                    transaction.commit();
                    getFragmentManager().executePendingTransactions();
                    transaction = fragmentManager.beginTransaction();
                    transaction.replace(R.id.main_fragment, secondaryFragment);
                    transaction.addToBackStack(null);
                    transaction.commit();
                    return;
                }
            }
        } else {
            transaction.replace(R.id.main_fragment, new ConversationsOverviewFragment());
        }
        if (binding.secondaryFragment != null && secondaryFragment == null) {
            transaction.replace(R.id.secondary_fragment, new ConversationFragment());
        }
        transaction.commit();
    }

    private void invalidateActionBarTitle() {
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar == null) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment conversationFragment) {
            final Conversation conversation = conversationFragment.getConversation();
            if (conversation != null) {
                actionBar.setTitle(conversation.getName());
                actionBar.setDisplayHomeAsUpEnabled(true);
                ToolbarUtils.setActionBarOnClickListener(
                        binding.toolbar,
                        (v) -> openConversationDetails(conversation)
                );
                return;
            }
        }
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment conversationFragment) {
            final Conversation conversation = conversationFragment.getConversation();
            if (conversation != null) {
                actionBar.setTitle(conversation.getName());
            } else {
                // actionBar.setTitle(R.string.app_name);
                actionBar.setTitle("Chats");
            }
        } else {
//           actionBar.setTitle(R.string.app_name);
            actionBar.setTitle("Chats");
        }
        actionBar.setDisplayHomeAsUpEnabled(false);
        ToolbarUtils.resetActionBarOnClickListeners(binding.toolbar);
    }

    private void openConversationDetails(final Conversation conversation) {
        if (conversation.getMode() == Conversational.MODE_MULTI) {
            ConferenceDetailsActivity.open(this, conversation);
        } else {
            final Contact contact = conversation.getContact();
            if (contact.isSelf()) {
                switchToAccount(conversation.getAccount());
            } else {
                switchToContactDetails(contact);
            }
        }
    }

    @Override
    public void onConversationArchived(Conversation conversation) {
        if (performRedirectIfNecessary(conversation, false)) {
            return;
        }
        final FragmentManager fragmentManager = getFragmentManager();
        final Fragment mainFragment = fragmentManager.findFragmentById(R.id.main_fragment);
        if (mainFragment instanceof ConversationFragment) {
            try {
                fragmentManager.popBackStack();
            } catch (final IllegalStateException e) {
                Log.w(Config.LOGTAG, "state loss while popping back state after archiving conversation", e);
                //this usually means activity is no longer active; meaning on the next open we will run through this again
            }
            return;
        }
        final Fragment secondaryFragment = fragmentManager.findFragmentById(R.id.secondary_fragment);
        if (secondaryFragment instanceof ConversationFragment) {
            if (((ConversationFragment) secondaryFragment).getConversation() == conversation) {
                Conversation suggestion = ConversationsOverviewFragment.getSuggestion(this, conversation);
                if (suggestion != null) {
                    openConversation(suggestion, null);
                }
            }
        }
    }

    @Override
    public void onConversationsListItemUpdated() {
        Fragment fragment = getFragmentManager().findFragmentById(R.id.main_fragment);
        if (fragment instanceof ConversationsOverviewFragment) {
            ((ConversationsOverviewFragment) fragment).refresh();
        }
    }

    @Override
    public void switchToConversation(Conversation conversation) {
        Log.d(Config.LOGTAG, "override");
        openConversation(conversation, null);
    }

    @Override
    public void onConversationRead(Conversation conversation, String upToUuid) {
        if (!mActivityPaused && pendingViewIntent.peek() == null) {
            xmppConnectionService.sendReadMarker(conversation, upToUuid);
        } else {
            Log.d(Config.LOGTAG, "ignoring read callback. mActivityPaused=" + mActivityPaused);
        }
    }

    @Override
    public void onAccountUpdate() {
        this.refreshUi();
    }

    @Override
    public void onConversationUpdate() {
        if (performRedirectIfNecessary(false)) {
            return;
        }
        this.refreshUi();
    }

    @Override
    public void onRosterUpdate() {
        this.refreshUi();
    }

    @Override
    public void OnUpdateBlocklist(OnUpdateBlocklist.Status status) {
        this.refreshUi();
    }

    @Override
    public void onShowErrorToast(int resId) {
        runOnUiThread(() -> Toast.makeText(this, resId, Toast.LENGTH_SHORT).show());
    }

    private void showPinDialog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String pinCode = prefs.getString("pin_code", "1111");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter PIN Code");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("OK", (dialog, which) -> {
            String enteredPin = input.getText().toString();
            if (enteredPin.equals(pinCode)) {
                // PIN is correct
                Toast.makeText(this, "Access granted", Toast.LENGTH_SHORT).show();
            } else {
                // PIN is incorrect
                Toast.makeText(this, "Invalid PIN", Toast.LENGTH_SHORT).show();
                finish(); // Close the app if the PIN is incorrect
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            finish(); // Close the app if cancelled
        });

        builder.show();
    }
}
