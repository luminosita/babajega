package eu.siacs.conversations.entities;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

public class AppSharedPreferences {
    private SharedPreferences appSharedPrefs;
    private SharedPreferences.Editor prefsEditor;

    final public static String APP_PIN = "app_pin";
    final public static String RESET_PIN = "reset_pin";
    final public static String SERVERS_LIST = "server_list";

    public AppSharedPreferences(Context context) {
        this.appSharedPrefs = context.getSharedPreferences("app_shared_preference", Activity.MODE_PRIVATE);
        this.prefsEditor = appSharedPrefs.edit();
    }


    public String getString(String key) {
        return appSharedPrefs.getString(key, "");
    }

    public void setString(String key, String value) {
        prefsEditor.putString(key, value).commit();
    }

    public void clearData() {
        prefsEditor.clear().commit();
    }

}
