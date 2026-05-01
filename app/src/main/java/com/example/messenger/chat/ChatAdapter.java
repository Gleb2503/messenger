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

public class ChatAdapter extends ListAdapter<ChatItem, ChatAdapter.ChatViewHolder> {

    private OnChatClickListener clickListener;
    private OnChatLongClickListener longClickListener;

    public interface OnChatClickListener {
        void onChatClick(ChatItem chatItem);
    }

    public interface OnChatLongClickListener {
        void onChatLongClick(long chatId, String chatName, boolean isPinned);
    }

    public ChatAdapter() {
        super(new DiffUtil.ItemCallback<ChatItem>() {
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
                        (oldItem.getAvatarUrl() != null ? oldItem.getAvatarUrl().equals(newItem.getAvatarUrl()) : newItem.getAvatarUrl() == null);
            }
        });
    }

    public void setOnChatClickListener(OnChatClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnChatLongClickListener(OnChatLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public ChatViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat, parent, false);
        return new ChatViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ChatViewHolder extends RecyclerView.ViewHolder {
        TextView nameText, messageText, timeText, badgeView, avatarLetter;
        ImageView pinnedIcon, avatarImage;
        View avatarBackground, avatarContainer;

        public ChatViewHolder(@NonNull View itemView) {
            super(itemView);
            nameText = itemView.findViewById(R.id.nameText);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            badgeView = itemView.findViewById(R.id.badgeView);
            pinnedIcon = itemView.findViewById(R.id.pinnedIcon);

            avatarLetter = itemView.findViewById(R.id.avatarLetter);
            avatarImage = itemView.findViewById(R.id.avatarImage);
            avatarBackground = itemView.findViewById(R.id.avatarBackground);
            avatarContainer = itemView.findViewById(R.id.avatarContainer);

            itemView.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && clickListener != null) {
                    ChatItem item = getItem(pos);
                    if (!item.isHeader()) {
                        clickListener.onChatClick(item);
                    }
                }
            });

            itemView.setOnLongClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && longClickListener != null) {
                    ChatItem item = getItem(pos);
                    if (!item.isHeader()) {
                        longClickListener.onChatLongClick(item.getId(), item.getName(), item.isPinned());
                        return true;
                    }
                }
                return false;
            });
        }

        public void bind(ChatItem chat) {
            if (chat.isHeader()) {
                nameText.setText(chat.getName());
                nameText.setTextSize(14);
                nameText.setTextColor(itemView.getContext().getColor(R.color.text_tertiary));
                nameText.setPadding(0, 48, 0, 24);

                messageText.setVisibility(View.GONE);
                timeText.setVisibility(View.GONE);
                badgeView.setVisibility(View.GONE);
                pinnedIcon.setVisibility(View.GONE);
                if (avatarContainer != null) avatarContainer.setVisibility(View.GONE);

                itemView.setClickable(false);
                itemView.setFocusable(false);
                itemView.setBackgroundResource(R.color.surface);
                return;
            }

            nameText.setTextSize(16);
            nameText.setTextColor(itemView.getContext().getColor(R.color.text_primary));
            nameText.setPadding(0, 0, 0, 0);

            messageText.setVisibility(View.VISIBLE);
            timeText.setVisibility(View.VISIBLE);
            if (avatarContainer != null) avatarContainer.setVisibility(View.VISIBLE);
            itemView.setClickable(true);
            itemView.setFocusable(true);

            nameText.setText(chat.getName());
            messageText.setText(chat.getLastMessage());
            timeText.setText(chat.getTime());

            if (chat.getUnreadCount() > 0) {
                badgeView.setVisibility(View.VISIBLE);
                badgeView.setText(String.valueOf(chat.getUnreadCount()));
            } else {
                badgeView.setVisibility(View.GONE);
            }

            pinnedIcon.setVisibility(chat.isPinned() ? View.VISIBLE : View.GONE);

            String avatarUrl = chat.getAvatarUrl();

            if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                avatarImage.setVisibility(View.VISIBLE);
                avatarLetter.setVisibility(View.GONE);

                Glide.with(itemView.getContext())
                        .load(avatarUrl.trim())
                        .circleCrop()
                        .placeholder(R.drawable.bg_logo_gradient)
                        .error(R.drawable.bg_logo_gradient)
                        .into(avatarImage);
            } else {
                avatarImage.setVisibility(View.GONE);
                avatarLetter.setVisibility(View.VISIBLE);

                String firstLetter = chat.getName().isEmpty() ? "?" : chat.getName().substring(0, 1).toUpperCase();
                avatarLetter.setText(firstLetter);
            }
        }
    }
}