package com.example.messenger.message;

import static android.content.ContentValues.TAG;

import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.messenger.R;
import com.example.messenger.media.MediaItem;
import com.example.messenger.data.websocket.MessageType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;
    public static final int TYPE_DATE = 3;
    public static final int TYPE_HEADER = 4;

    public static final int STATUS_SENT = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_READ = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_SENDING = 4;

    public interface OnMediaClickListener {
        void onMediaClick(String url, MessageType type);
    }

    public interface OnMediaViewerListener {
        void onMediaViewerRequested(List<MediaItem> mediaItems, int position);
    }

    private OnMediaClickListener mediaClickListener;
    private OnMediaViewerListener mediaViewerListener;
    private final List<MessageItem> items = new ArrayList<>();
    private boolean isGroupChat = false;

    public void setGroupChat(boolean groupChat) {
        isGroupChat = groupChat;
    }

    public void setOnMediaClickListener(OnMediaClickListener listener) {
        this.mediaClickListener = listener;
    }

    public void setOnMediaViewerListener(OnMediaViewerListener listener) {
        this.mediaViewerListener = listener;
    }

    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view;
        if (viewType == TYPE_DATE) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_date_header, parent, false);
            return new DateViewHolder(view);
        } else if (viewType == TYPE_HEADER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_chat_header, parent, false);
            return new HeaderViewHolder(view);
        } else if (viewType == TYPE_OUTGOING) {
            int layoutRes = isGroupChat ? R.layout.item_message_group_outgoing : R.layout.item_message_outgoing;
            view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return new OutgoingMessageViewHolder(view);
        } else {
            int layoutRes = isGroupChat ? R.layout.item_message_group_incoming : R.layout.item_message_incoming;
            view = LayoutInflater.from(parent.getContext()).inflate(layoutRes, parent, false);
            return new IncomingMessageViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
        } else {
            MessageItem item = items.get(position);
            if (holder instanceof OutgoingMessageViewHolder && payloads.contains("status_update")) {
                Log.e("MessageAdapter", "🔄 Payload update: updating status icon for position " + position);
                ((OutgoingMessageViewHolder) holder).updateStatusIconDirectly(item.getStatus());
            } else {
                onBindViewHolder(holder, position);
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        MessageItem item = items.get(position);
        if (item.getType() == TYPE_DATE && holder instanceof DateViewHolder) {
            ((DateViewHolder) holder).bind(item);
        } else if (item.getType() == TYPE_HEADER && holder instanceof HeaderViewHolder) {
            ((HeaderViewHolder) holder).bind(item);
        } else if (item.getType() == TYPE_OUTGOING && holder instanceof OutgoingMessageViewHolder) {
            ((OutgoingMessageViewHolder) holder).bind(item, mediaClickListener, mediaViewerListener, items, isGroupChat);
        } else if (item.getType() == TYPE_INCOMING && holder instanceof IncomingMessageViewHolder) {
            ((IncomingMessageViewHolder) holder).bind(item, mediaClickListener, mediaViewerListener, items, isGroupChat);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getType();
    }

    public void setMessages(List<MessageItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void addMessage(MessageItem item) {
        items.add(item);
        notifyItemInserted(items.size() - 1);
    }

    public List<MessageItem> getItems() {
        return new ArrayList<>(items);
    }

    public boolean updateItemStatus(long messageId, int newStatus) {
        if (messageId <= 0) {
            Log.e(TAG, "❌ updateItemStatus: invalid messageId=" + messageId);
            return false;
        }

        Log.d(TAG, "🔍 updateItemStatus: searching for messageId=" + messageId + ", newStatus=" + newStatus);

        for (int i = 0; i < items.size(); i++) {
            MessageItem item = items.get(i);

            if (item.getType() == TYPE_DATE || item.getType() == TYPE_HEADER) {
                continue;
            }

            if (item.getId() == messageId && item.getId() > 0) {
                Log.d(TAG, "✅ FOUND message " + messageId +
                        ", old status=" + item.getStatus() + " → new=" + newStatus);
                item.setStatus(newStatus);
                notifyItemChanged(i, "status_update");
                Log.d(TAG, "✓✓ notifyItemChanged(" + i + ", \"status_update\") called");
                return true;
            }
        }

        Log.w(TAG, "❌ Message " + messageId + " NOT found! Adapter items:");
        for (MessageItem item : items) {
            if (item.getType() != TYPE_DATE && item.getType() != TYPE_HEADER) {
                Log.w(TAG, "   └─ Item ID: " + item.getId() + ", type: " + item.getType() +
                        ", status: " + item.getStatus() + ", messageType: " + item.getMessageType());
            }
        }
        return false;
    }

    public int findMessagePosition(long tempId) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId() == tempId) {
                return i;
            }
        }
        return -1;
    }

    public void updateImageMessage(long tempId, long realId, String fileUrl, int status) {
        List<MessageItem> items = getItems();
        for (int i = 0; i < items.size(); i++) {
            MessageItem item = items.get(i);
            if (item.getId() == tempId) {
                item.setId(realId);
                item.setImageUrl(fileUrl);
                item.setStatus(status);

                if (item.getMessageType() != MessageType.IMAGE) {
                    item.setMessageType(MessageType.IMAGE);
                }
                notifyItemChanged(i);
                Log.d(TAG, "✅ Updated image message: " + realId);
                return;
            }
        }
        Log.w(TAG, "⚠️ Image message not found for tempId: " + tempId);
    }

    public void updateMessageStatus(long tempId, int newStatus) {
        int pos = findMessagePosition(tempId);
        if (pos != -1) {
            MessageItem msg = items.get(pos);
            msg.setStatus(newStatus);
            notifyItemChanged(pos);
            Log.d(TAG, "✅ Updated message status: " + newStatus);
        }
    }

    public List<MediaItem> collectAllMediaItems() {
        List<MediaItem> result = new ArrayList<>();
        for (MessageItem msg : items) {
            if ((msg.getType() == TYPE_OUTGOING || msg.getType() == TYPE_INCOMING)
                    && isImageMessage(msg)) {
                String url = msg.getImageUrl() != null ? cleanUrl(msg.getImageUrl()) : null;
                if (url != null && !url.isEmpty()) {
                    result.add(new MediaItem(
                            url,
                            "image_" + msg.getId() + ".jpg",
                            0,
                            null,
                            msg.getId(),
                            MessageType.IMAGE
                    ));
                }
            }
        }
        return result;
    }

    public int findMediaPosition(long messageId) {
        List<MediaItem> allMedia = collectAllMediaItems();
        for (int i = 0; i < allMedia.size(); i++) {
            if (allMedia.get(i).getMessageId() == messageId) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isImageMessage(MessageItem item) {
        return item.getMessageType() == MessageType.IMAGE
                || (item.getImageUrl() != null && !item.getImageUrl().isEmpty())
                || (item.getLocalImageUri() != null);
    }

    private static String cleanUrl(String url) {
        if (url == null) return null;
        return url.replace("\"", "");
    }

    public void clearMessages() {
        items.clear();
        notifyDataSetChanged();
    }

    public void addMessagesAtTop(List<MessageItem> newItems) {
        if (newItems == null || newItems.isEmpty()) return;

        List<MessageItem> filteredItems = new ArrayList<>();
        for (MessageItem item : newItems) {
            if (item.getType() != TYPE_DATE) {
                filteredItems.add(item);
            }
        }
        if (filteredItems.isEmpty()) return;

        int insertPosition = 0;
        while (insertPosition < items.size() && items.get(insertPosition).getType() == TYPE_DATE) {
            insertPosition++;
        }

        int newCount = filteredItems.size();
        items.addAll(insertPosition, filteredItems);

        recreateDateHeaders();

        notifyItemRangeInserted(insertPosition, newCount);
    }

    private void recreateDateHeaders() {
        for (int i = items.size() - 1; i >= 0; i--) {
            if (items.get(i).getType() == TYPE_DATE) {
                items.remove(i);
            }
        }

        List<MessageItem> newList = new ArrayList<>();
        String lastDateHeader = null;
        LocalDate today = LocalDate.now();

        for (MessageItem item : items) {
            if (item.getType() == TYPE_INCOMING || item.getType() == TYPE_OUTGOING) {
                String currentDateHeader = null;
                String createdAt = item.getCreatedAt();

                if (createdAt != null) {
                    try {
                        LocalDate msgDate = LocalDateTime.parse(createdAt.replace("Z", "")).toLocalDate();
                        if (msgDate.equals(today)) {
                            currentDateHeader = "Сегодня";
                        } else if (msgDate.equals(today.minusDays(1))) {
                            currentDateHeader = "Вчера";
                        } else {
                            currentDateHeader = DateTimeFormatter.ofPattern("dd.MM.yyyy", new Locale("ru")).format(msgDate);
                        }
                    } catch (Exception e) {
                        currentDateHeader = "Сегодня";
                    }
                }

                if (currentDateHeader != null && !currentDateHeader.equals(lastDateHeader)) {
                    newList.add(new MessageItem(0, currentDateHeader, "00:00", 0, TYPE_DATE, 0));
                    lastDateHeader = currentDateHeader;
                }
                newList.add(item);
            }
        }

        items.clear();
        items.addAll(newList);
        notifyDataSetChanged();
    }

    static class DateViewHolder extends MessageViewHolder {
        private final TextView dateText;
        DateViewHolder(View itemView) {
            super(itemView);
            dateText = itemView.findViewById(R.id.dateText);
        }
        void bind(MessageItem item) { dateText.setText(item.getText()); }
    }

    static class HeaderViewHolder extends MessageViewHolder {
        private final TextView headerText;
        HeaderViewHolder(View itemView) {
            super(itemView);
            headerText = itemView.findViewById(R.id.headerText);
        }
        void bind(MessageItem item) { headerText.setText(item.getText()); }
    }

    static class OutgoingMessageViewHolder extends MessageViewHolder {
        private final TextView messageText, timeText;
        private final ImageView statusIcon;
        private final ImageView imagePreview;

        OutgoingMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            imagePreview = itemView.findViewById(R.id.imagePreview);
        }

        void bind(MessageItem item, OnMediaClickListener mediaClickListener,
                  OnMediaViewerListener mediaViewerListener, List<MessageItem> allItems, boolean isGroupChat) {
            if (isImageMessage(item)) {
                messageText.setVisibility(View.GONE);

                if (imagePreview != null) {
                    imagePreview.setVisibility(View.VISIBLE);

                    if (item.getLocalImageUri() != null) {
                        Glide.with(itemView.getContext())
                                .load(item.getLocalImageUri())
                                .centerCrop()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_error)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(imagePreview);
                    } else if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                        String url = cleanUrl(item.getImageUrl());
                        Glide.with(itemView.getContext())
                                .load(url)
                                .centerCrop()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_error)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(imagePreview);
                    } else {
                        imagePreview.setVisibility(View.GONE);
                    }

                    imagePreview.setOnClickListener(v -> {
                        if (mediaViewerListener != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();
                            for (MessageItem msg : allItems) {
                                if ((msg.getType() == TYPE_OUTGOING || msg.getType() == TYPE_INCOMING)
                                        && isImageMessage(msg) && msg.getImageUrl() != null) {
                                    mediaItems.add(new MediaItem(
                                            cleanUrl(msg.getImageUrl()),
                                            "image_" + msg.getId() + ".jpg",
                                            0,
                                            null,
                                            msg.getId(),
                                            MessageType.IMAGE
                                    ));
                                }
                            }
                            int position = -1;
                            for (int i = 0; i < mediaItems.size(); i++) {
                                if (mediaItems.get(i).getMessageId() == item.getId()) {
                                    position = i;
                                    break;
                                }
                            }
                            if (position >= 0) {
                                mediaViewerListener.onMediaViewerRequested(mediaItems, position);
                            }
                        } else if (mediaClickListener != null && item.getImageUrl() != null) {
                            mediaClickListener.onMediaClick(cleanUrl(item.getImageUrl()), MessageType.IMAGE);
                        }
                    });
                }
            } else {
                messageText.setText(item.getText());
                messageText.setVisibility(View.VISIBLE);
                if (imagePreview != null) {
                    imagePreview.setVisibility(View.GONE);
                }
            }

            timeText.setText(item.getTime());
            updateStatusIcon(statusIcon, item.getStatus());
        }

        void updateStatusIconDirectly(int status) {
            Log.e("MessageAdapter", "🎨 updateStatusIconDirectly: status=" + status);
            updateStatusIcon(statusIcon, status);
        }

        private void updateStatusIcon(ImageView icon, int status) {
            if (icon == null) {
                Log.e("MessageAdapter", "❌ statusIcon is NULL!");
                return;
            }
            int color = 0xFFFFFFFF;
            switch (status) {
                case STATUS_SENT:
                    Log.e("MessageAdapter", "🎨 Setting SINGLE checkmark");
                    icon.setImageResource(R.drawable.ic_check_single);
                    icon.setColorFilter(color);
                    icon.setVisibility(View.VISIBLE);
                    break;
                case STATUS_DELIVERED:
                case STATUS_READ:
                    Log.e("MessageAdapter", "🎨 Setting DOUBLE checkmark for status: " + status);
                    icon.setImageResource(R.drawable.ic_check_double);
                    icon.setColorFilter(color);
                    icon.setVisibility(View.VISIBLE);
                    break;
                case STATUS_FAILED:
                    Log.e("MessageAdapter", "🎨 Setting ERROR icon");
                    icon.setImageResource(R.drawable.ic_error);
                    icon.setColorFilter(0xFFFF5252);
                    icon.setVisibility(View.VISIBLE);
                    break;
                default:
                    icon.setVisibility(View.GONE);
                    break;
            }
            icon.invalidate();
            icon.requestLayout();
        }
    }

    static class IncomingMessageViewHolder extends MessageViewHolder {
        private final TextView messageText, timeText;
        private final ImageView statusIcon;
        private final ImageView imagePreview;
        private final ImageView senderAvatar;
        private final TextView senderName;

        IncomingMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            imagePreview = itemView.findViewById(R.id.imagePreview);
            senderAvatar = itemView.findViewById(R.id.senderAvatar);
            senderName = itemView.findViewById(R.id.senderName);
        }

        void bind(MessageItem item, OnMediaClickListener mediaClickListener,
                  OnMediaViewerListener mediaViewerListener, List<MessageItem> allItems, boolean isGroupChat) {


            if (isGroupChat && senderAvatar != null && senderName != null) {
                senderAvatar.setVisibility(View.VISIBLE);
                senderName.setVisibility(View.VISIBLE);

                String senderNameText = item.getSenderName() != null ? item.getSenderName() : "Пользователь";
                senderName.setText(senderNameText);

                String avatarUrl = item.getSenderAvatarUrl();
                if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                    senderAvatar.setVisibility(View.VISIBLE);
                    Glide.with(itemView.getContext())
                            .load(avatarUrl)
                            .circleCrop()
                            .placeholder(R.drawable.bg_avatar_placeholder)
                            .error(R.drawable.bg_avatar_placeholder)
                            .into(senderAvatar);
                } else {
                    senderAvatar.setImageResource(R.drawable.bg_avatar_placeholder);
                }
            } else {

                if (senderAvatar != null) senderAvatar.setVisibility(View.GONE);
                if (senderName != null) senderName.setVisibility(View.GONE);
            }

            if (isImageMessage(item)) {
                messageText.setVisibility(View.GONE);

                if (imagePreview != null) {
                    imagePreview.setVisibility(View.VISIBLE);

                    if (item.getLocalImageUri() != null) {
                        Glide.with(itemView.getContext())
                                .load(item.getLocalImageUri())
                                .centerCrop()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_error)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(imagePreview);
                    } else if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                        String url = cleanUrl(item.getImageUrl());
                        Glide.with(itemView.getContext())
                                .load(url)
                                .centerCrop()
                                .placeholder(R.drawable.ic_image_placeholder)
                                .error(R.drawable.ic_error)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .into(imagePreview);
                    } else {
                        imagePreview.setVisibility(View.GONE);
                    }

                    imagePreview.setOnClickListener(v -> {
                        if (mediaViewerListener != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();
                            for (MessageItem msg : allItems) {
                                if ((msg.getType() == TYPE_OUTGOING || msg.getType() == TYPE_INCOMING)
                                        && isImageMessage(msg) && msg.getImageUrl() != null) {
                                    mediaItems.add(new MediaItem(
                                            cleanUrl(msg.getImageUrl()),
                                            "image_" + msg.getId() + ".jpg",
                                            0,
                                            null,
                                            msg.getId(),
                                            MessageType.IMAGE
                                    ));
                                }
                            }
                            int position = -1;
                            for (int i = 0; i < mediaItems.size(); i++) {
                                if (mediaItems.get(i).getMessageId() == item.getId()) {
                                    position = i;
                                    break;
                                }
                            }
                            if (position >= 0) {
                                mediaViewerListener.onMediaViewerRequested(mediaItems, position);
                            }
                        } else if (mediaClickListener != null && item.getImageUrl() != null) {
                            mediaClickListener.onMediaClick(cleanUrl(item.getImageUrl()), MessageType.IMAGE);
                        }
                    });
                }
            } else {
                messageText.setText(item.getText());
                messageText.setVisibility(View.VISIBLE);
                if (imagePreview != null) {
                    imagePreview.setVisibility(View.GONE);
                }
            }

            timeText.setText(item.getTime());

            if (statusIcon != null && !isGroupChat) {
                statusIcon.setVisibility(View.GONE);
            }
        }
    }

    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(View itemView) { super(itemView); }
    }
}