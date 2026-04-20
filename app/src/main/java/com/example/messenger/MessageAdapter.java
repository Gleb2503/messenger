package com.example.messenger;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
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

    private final List<MessageItem> items = new ArrayList<>();

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
            ((OutgoingMessageViewHolder) holder).bind(item);
        } else if (item.getType() == TYPE_INCOMING && holder instanceof IncomingMessageViewHolder) {
            ((IncomingMessageViewHolder) holder).bind(item);
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
        for (int i = 0; i < items.size(); i++) {
            MessageItem item = items.get(i);

            if (item.getType() != TYPE_DATE &&
                    item.getType() != TYPE_HEADER &&
                    item.getId() == messageId) {

                Log.e("MessageAdapter", "✅ FOUND message " + messageId + ", old status=" + item.getStatus() + ", new=" + newStatus);

                item.setStatus(newStatus);

                notifyItemChanged(i, "status_update");
                Log.e("MessageAdapter", "✓✓ notifyItemChanged(" + i + ", \"status_update\") called");
                return true;
            }
        }

        Log.e("MessageAdapter", "❌ Message " + messageId + " NOT found!");
        return false;
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

        OutgoingMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
        }

        void bind(MessageItem item) {
            messageText.setText(item.getText());
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

        IncomingMessageViewHolder(View itemView) {
            super(itemView);
            messageText = itemView.findViewById(R.id.messageText);
            timeText = itemView.findViewById(R.id.timeText);
            statusIcon = itemView.findViewById(R.id.statusIcon);
        }

        void bind(MessageItem item) {
            messageText.setText(item.getText());
            timeText.setText(item.getTime());
            statusIcon.setVisibility(View.GONE);
        }
    }

    abstract static class MessageViewHolder extends RecyclerView.ViewHolder {
        MessageViewHolder(View itemView) { super(itemView); }
    }
}