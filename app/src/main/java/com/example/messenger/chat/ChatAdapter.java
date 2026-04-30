package com.example.messenger.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.messenger.R;
import java.util.Locale;

public class ChatAdapter extends ListAdapter<ChatItem, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_HEADER = 0;
    private static final int VIEW_TYPE_CHAT = 1;

    private OnChatClickListener clickListener;
    private OnChatLongClickListener longClickListener;

    public interface OnChatClickListener {
        void onChatClick(ChatItem chatItem);
    }

    public interface OnChatLongClickListener {
        void onChatLongClick(long chatId, String chatName, boolean isPinned);
    }

    public ChatAdapter() {
        super(new ChatDiffCallback());
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_HEADER) {
            View view = inflater.inflate(R.layout.item_chat_header, parent, false);
            return new HeaderViewHolder(view);
        } else {
            View view = inflater.inflate(R.layout.item_chat, parent, false);
            return new ChatViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatItem item = getItem(position);
        if (holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (holder instanceof ChatViewHolder) {
            ((ChatViewHolder) holder).bind(item, clickListener, longClickListener);
        }
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem item = getItem(position);
        return item.isHeader() ? VIEW_TYPE_HEADER : VIEW_TYPE_CHAT;
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnChatLongClickListener(OnChatLongClickListener listener) {
        this.longClickListener = listener;
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final TextView headerText;

        HeaderViewHolder(@NonNull View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerText);
        }

        void bind(ChatItem item) {
            headerText.setText(item.getName());
        }
    }

    static class ChatViewHolder extends RecyclerView.ViewHolder {
        private final TextView nameText, messageText, timeText, badgeView;
        private final ImageView pinnedIcon, avatarImage;
        private final View avatarBackground;
        private final TextView avatarLetter;

        ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            badgeView = itemView.findViewById(R.id.badgeView);
            pinnedIcon = itemView.findViewById(R.id.pinnedIcon);
            avatarBackground = itemView.findViewById(R.id.avatarBackground);
            avatarLetter = itemView.findViewById(R.id.avatarLetter);
            avatarImage = itemView.findViewById(R.id.avatarImage);
        }

        void bind(ChatItem item, OnChatClickListener listener, OnChatLongClickListener longListener) {
            nameText.setText(item.getName());
            messageText.setText(item.getLastMessage());
            timeText.setText(item.getTime());

            if (item.getUnreadCount() > 0) {
                badgeView.setVisibility(View.VISIBLE);
                badgeView.setText(String.valueOf(item.getUnreadCount()));
            } else {
                badgeView.setVisibility(View.GONE);
            }

            pinnedIcon.setVisibility(item.isPinned() ? View.VISIBLE : View.GONE);

            loadAvatar(item.getAvatarUrl(), item.getName());

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onChatClick(item);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onChatLongClick(item.getId(), item.getName(), item.isPinned());
                    return true;
                }
                return false;
            });
        }

        private void loadAvatar(String avatarUrl, String userName) {
            if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                avatarImage.setVisibility(View.VISIBLE);
                avatarBackground.setVisibility(View.GONE);
                avatarLetter.setVisibility(View.GONE);
                Glide.with(itemView.getContext())
                        .load(avatarUrl.trim())
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .error(R.drawable.bg_avatar_placeholder)
                        .circleCrop()
                        .into(avatarImage);
            } else {
                avatarImage.setVisibility(View.GONE);
                avatarBackground.setVisibility(View.VISIBLE);
                avatarLetter.setVisibility(View.VISIBLE);
                if (userName != null && !userName.isEmpty()) {
                    avatarLetter.setText(userName.substring(0, 1).toUpperCase(Locale.getDefault()));
                } else {
                    avatarLetter.setText("?");
                }
            }
        }
    }

    static class ChatDiffCallback extends DiffUtil.ItemCallback<ChatItem> {
        @Override
        public boolean areItemsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            return oldItem.getId() == newItem.getId() && oldItem.getType() == newItem.getType();
        }

        @Override
        public boolean areContentsTheSame(@NonNull ChatItem oldItem, @NonNull ChatItem newItem) {
            return oldItem.getName().equals(newItem.getName()) &&
                    oldItem.getLastMessage().equals(newItem.getLastMessage()) &&
                    oldItem.getTime().equals(newItem.getTime()) &&
                    oldItem.getUnreadCount() == newItem.getUnreadCount() &&
                    oldItem.isPinned() == newItem.isPinned() &&
                    (oldItem.getAvatarUrl() == null ? newItem.getAvatarUrl() == null :
                            oldItem.getAvatarUrl().equals(newItem.getAvatarUrl()));
        }
    }
}