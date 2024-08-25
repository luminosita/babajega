package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import eu.siacs.conversations.entities.AppSharedPreferences;
import eu.siacs.conversations.ui.activity.pin.PinConfirmActivity;

public class ConversationActivity extends AppCompatActivity {
    AppSharedPreferences appSharedPreferences;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        appSharedPreferences = new AppSharedPreferences(this);

        String unlockPin = appSharedPreferences.getString(AppSharedPreferences.APP_PIN);
        if (unlockPin != null && !unlockPin.isEmpty()) {
            startActivity(new Intent(this, PinConfirmActivity.class));
        } else {
            startActivity(new Intent(this, ConversationsActivity.class));
        }
        finish();
    }
}
