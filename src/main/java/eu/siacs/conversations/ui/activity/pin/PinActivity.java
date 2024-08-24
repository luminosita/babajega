package eu.siacs.conversations.ui.activity.pin;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPinBinding;

public class PinActivity extends AppCompatActivity {

    ActivityPinBinding binding;
    private String firstPin;
    private boolean isConfirmScreen = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_pin);
        binding.materialToolbar.setNavigationOnClickListener(
                view -> finish());
        initPinUi(binding.pin1, binding.pin2, binding.pin3, binding.pin4);
        initPinUi(binding.resetpin1, binding.resetpin2, binding.resetpin3, binding.resetpin4);

        binding.pin1.requestFocus();
        binding.pin1.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY);
        }, 200);

        binding.btnCancel.setOnClickListener(view -> finish());
        binding.btnApply.setOnClickListener(view -> {
            if (isEmptyPin()) {
                Toast.makeText(this, R.string.require_4_digits_app_lock_pin, Toast.LENGTH_SHORT).show();
            } else if (isEmptyResetPin()) {
                Toast.makeText(this, R.string.require_reset_pin, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, getString(R.string.pin_setup_successfully), Toast.LENGTH_SHORT).show();
                finish();
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
        et.addTextChangedListener(new PinTextWatcher(et, binding, () -> {
        }));
    }


    private String getText(EditText et) {
        return et.getText().toString();
    }


    private String fetchPin() {
        return binding.pin1.getText().toString() + binding.pin2.getText().toString() + binding.pin3.getText().toString() + binding.pin4.getText().toString();
    }

    private String fetchResetPin() {
        return binding.resetpin1.getText().toString() + binding.resetpin2.getText().toString() + binding.resetpin3.getText().toString() + binding.resetpin4.getText().toString();
    }

    private Boolean isEmptyResetPin() {
        return getText(binding.resetpin1).isEmpty() || getText(binding.resetpin2).isEmpty() || getText(binding.resetpin3).isEmpty() || getText(binding.resetpin4).isEmpty();
    }

    private Boolean isEmptyPin() {
        return getText(binding.pin1).isEmpty() || getText(binding.pin2).isEmpty() || getText(binding.pin3).isEmpty() || getText(binding.pin4).isEmpty();
    }


}