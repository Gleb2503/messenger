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

import java.util.ArrayList;
import java.util.List;

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
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_outgoing, parent, false);
            return new OutgoingMessageViewHolder(view);
        } else {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message_incoming, parent, false);
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
            ((OutgoingMessageViewHolder) holder).bind(item, mediaClickListener, mediaViewerListener, items);
        } else if (item.getType() == TYPE_INCOMING && holder instanceof IncomingMessageViewHolder) {
            ((IncomingMessageViewHolder) holder).bind(item, mediaClickListener, mediaViewerListener, items);
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

    public void updateImageMessage(long tempId, long serverId, String imageUrl, int newStatus) {
        int pos = findMessagePosition(tempId);
        if (pos != -1) {
            MessageItem msg = items.get(pos);
            msg.setId(serverId);
            msg.setImageUrl(imageUrl);
            msg.setStatus(newStatus);
            msg.setUploadProgress(100);
            notifyItemChanged(pos);
            Log.d(TAG, "✅ Updated image message: " + serverId);
        } else {
            Log.w(TAG, "⚠️ Message with tempId " + tempId + " not found");
        }
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
                  OnMediaViewerListener mediaViewerListener, List<MessageItem> allItems) {
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

        IncomingMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
            imagePreview = itemView.findViewById(R.id.imagePreview);
        }

        void bind(MessageItem item, OnMediaClickListener mediaClickListener,
                  OnMediaViewerListener mediaViewerListener, List<MessageItem> allItems) {
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
            statusIcon.setVisibility(View.GONE);
        }
    }

    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(View itemView) { super(itemView); }
    }
}