package eu.siacs.conversations.ui.activity.pin;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPinConfirmBinding;
import eu.siacs.conversations.entities.AppSharedPreferences;
import eu.siacs.conversations.services.XmppConnectionService;
import eu.siacs.conversations.ui.Activities;
import eu.siacs.conversations.ui.ConversationsActivity;
import eu.siacs.conversations.ui.WelcomeActivity;
import eu.siacs.conversations.utils.Compatibility;

public class PinConfirmActivity extends AppCompatActivity {
    ActivityPinConfirmBinding binding;
    AppSharedPreferences appSharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_confirm);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin_confirm);
        setSupportActionBar(binding.materialToolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        initPinUi(binding.pin1, binding.pin2, binding.pin3, binding.pin4);
        appSharedPreferences = new AppSharedPreferences(this);
        PinConfirmActivity activity = this;

        binding.pin1.requestFocus();
        binding.pin1.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }, 200);

        binding.btnCancel.setOnClickListener(view -> finish());
        binding.btnApply.setOnClickListener(view -> {
            String unlockPin = appSharedPreferences.getString(AppSharedPreferences.APP_PIN);
            String resetPin = appSharedPreferences.getString(AppSharedPreferences.RESET_PIN);

            if (unlockPin.equals(fetchPin())) {
                startActivity(new Intent(this, ConversationsActivity.class));
            } else if (resetPin.equals(fetchPin())) {
                final Intent serviceIntent = new Intent(activity, XmppConnectionService.class);
                serviceIntent.setAction(XmppConnectionService.ACTION_ACCOUNT_RESET);
                Compatibility.startService(activity, serviceIntent);

                Intent intent = new Intent(activity, WelcomeActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
                startActivity(intent);
                finish();
            } else {
                Toast.makeText(getApplicationContext(), getString(R.string.pin_doesn_t_match), Toast.LENGTH_SHORT).show();
            }


        });


    }

    private void initPinUi(EditText pin1, EditText pin2, EditText pin3, EditText pin4) {
        pinEditTextSetup(pin1);
        pinEditTextSetup(pin2);
        pinEditTextSetup(pin3);
        pinEditTextSetup(pin4);

        pin2.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (binding.pin2.getText().toString().isEmpty()) {
                    pin1.requestFocus();
                }
            }
            return false;
        });

        pin3.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (pin3.getText().toString().isEmpty()) {
                    pin2.requestFocus();
                }
            }
            return false;
        });

        pin4.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DEL) {
                if (pin4.getText().toString().isEmpty()) {
                    pin3.requestFocus();
                }
            }

            return false;
        });

    }

    private void pinEditTextSetup(EditText et) {
        et.addTextChangedListener(new ConfirmPinTextWatcher(et, binding, () -> {
        }));
    }


    private String getText(EditText et) {
        return et.getText().toString();
    }


    private String fetchPin() {
        return binding.pin1.getText().toString() + binding.pin2.getText().toString() + binding.pin3.getText().toString() + binding.pin4.getText().toString();
    }


    private Boolean isEmptyPin() {
        return getText(binding.pin1).isEmpty() || getText(binding.pin2).isEmpty() || getText(binding.pin3).isEmpty() || getText(binding.pin4).isEmpty();
    }

    public class ConfirmPinTextWatcher implements TextWatcher {

        private View view;
        private ActivityPinConfirmBinding pinBinding;
        private PinEntryListener listener;

        ConfirmPinTextWatcher(View view, ActivityPinConfirmBinding binding, PinEntryListener listener) {
            this.view = view;
            this.pinBinding = binding;
            this.listener = listener;
        }

        @Override
        public void afterTextChanged(Editable s) {
            String text = s.toString();
            int i = view.getId();
            if (i == R.id.pin1) {
                if (text.length() == 1) {
                    pinBinding.pin2.requestFocus();
                }
            } else if (i == R.id.pin2) {
                if (text.length() == 1) {
                    pinBinding.pin3.requestFocus();
                }
            } else if (i == R.id.pin3) {
                if (text.length() == 1) {
                    pinBinding.pin4.requestFocus();
                }
            } else if (i == R.id.pin4) {
                if (text.length() == 1) {
                    listener.onEntryPin4();
                }
            }

        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

    }

}