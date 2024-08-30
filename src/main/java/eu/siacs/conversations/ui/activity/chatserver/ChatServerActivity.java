package eu.siacs.conversations.ui.activity.chatserver;

import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import eu.siacs.conversations.R;
import eu.siacs.conversations.databinding.ActivityChatServerBinding;
import eu.siacs.conversations.entities.AppSharedPreferences;
import eu.siacs.conversations.ui.Activities;

public class ChatServerActivity extends AppCompatActivity {

    ActivityChatServerBinding binding;
    ServerAdapter serverAdapter;
    AppSharedPreferences appSharedPreferences;
    Gson gson = new Gson();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_server);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_chat_server);

        setSupportActionBar(binding.materialToolbar);
        Activities.setStatusAndNavigationBarColors(this, binding.getRoot());
        appSharedPreferences = new AppSharedPreferences(this);

        serverAdapter = new ServerAdapter(new ArrayList<>(), (pos) -> {
            List<String> list = new ArrayList<>();
            List<String> serverList = getServerList();
            if (serverList != null) {
                list.addAll(serverList);
                list.remove(pos);
                setList(list);
                serverAdapter.updateList(list);
            }
        });
        binding.rvServers.setAdapter(serverAdapter);
        binding.btnAdd.setOnClickListener(view -> {
            serverEntryDialog();
        });

        List<String> list = new ArrayList<>();
        if (getServerList()==null || getServerList().isEmpty()) {
            list.add("139.59.154.43");
            setList(list);
            serverAdapter.updateList(list);
        } else {
            list.addAll(getServerList());
            serverAdapter.updateList(list);
        }

        binding.materialToolbar.setNavigationOnClickListener(
                view -> {
                    finish();
                });

    }

    public void serverEntryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.add_server);
        final EditText editText = new EditText(this);
        editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        builder.setView(editText);
        builder.setPositiveButton(getString(R.string.add), (dialog, which) -> {
            String text = editText.getText().toString();
            List<String> list = new ArrayList<>();
            List<String> serverList = getServerList();
            if (serverList != null) {
                list.addAll(serverList);
            }
            list.add(text);
            setList(list);
            serverAdapter.updateList(list);
        });
        builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> dialog.cancel());
        builder.show();
    }

    public void setList(List<String> list) {
        String json = gson.toJson(list);
        appSharedPreferences.setString(AppSharedPreferences.SERVERS_LIST, json);
    }

    public List<String> getServerList() {
        String json = appSharedPreferences.getString(AppSharedPreferences.SERVERS_LIST);
        Type type = new TypeToken<List<String>>() {
        }.getType();
        return gson.fromJson(json, type);
    }

}