package eu.siacs.conversations.ui.activity.chatserver;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import eu.siacs.conversations.R;

public class ServerAdapter extends RecyclerView.Adapter<ServerAdapter.ViewHolder> {
    private List<String> lists;
    public ChatUrlListener chatUrlListener;

    public ServerAdapter(List<String> lists, ChatUrlListener chatUrlListener) {
        this.lists = lists;
        this.chatUrlListener = chatUrlListener;
    }

    @Override
    public ServerAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View rowItem = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_server, parent, false);
        return new ViewHolder(rowItem);
    }

    @Override
    public void onBindViewHolder(ServerAdapter.ViewHolder holder, int position) {
        holder.textView.setText(this.lists.get(position));
        holder.imageView.setOnClickListener(view -> {
            chatUrlListener.onClickRemove(position);
        });
    }

    @Override
    public int getItemCount() {
        return this.lists.size();
    }

    public void updateList(List<String> lists) {
        this.lists = lists;
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private TextView textView;
        private ImageView imageView;

        public ViewHolder(View view) {
            super(view);
            this.textView = view.findViewById(R.id.tvServer);
            this.imageView = view.findViewById(R.id.ivRemove);
        }


    }
}

interface ChatUrlListener {
    public void onClickRemove(int pos);
}