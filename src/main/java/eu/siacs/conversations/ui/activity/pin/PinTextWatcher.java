package eu.siacs.conversations.ui.activity.pin;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityPinBinding;

public class PinTextWatcher implements TextWatcher {

    private View view;
    private ActivityPinBinding pinBinding;
    private PinEntryListener listener;

    PinTextWatcher(View view, ActivityPinBinding binding, PinEntryListener listener) {
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
                pinBinding.resetpin1.requestFocus();
            }
        } else if (i == R.id.resetpin1) {
            if (text.length() == 1) {
                pinBinding.resetpin2.requestFocus();
            }
        } else if (i == R.id.resetpin2) {
            if (text.length() == 1) {
                pinBinding.resetpin3.requestFocus();
            }
        } else if (i == R.id.resetpin3) {
            if (text.length() == 1) {
                pinBinding.resetpin4.requestFocus();
            }
        } else if (i == R.id.resetpin4) {
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

interface PinEntryListener {
    public void onEntryPin4();
}
