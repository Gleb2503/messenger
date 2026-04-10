package com.example.messenger;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.websocket.StompClient;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private RecyclerView messagesRecyclerView;
    private LinearLayout emptyState, noConnectionState;
    private EditText messageInput;
    private ImageView sendButton, backButton, menuIcon, statusIcon;
    private TextView chatName, statusText;
    private Button retryButton;

    private MessageAdapter messageAdapter;
    private ApiService apiService;
    private StompClient stompClient;

    private long chatId;
    private long partnerUserId;
    private String chatNameStr;
    private long currentUserId;
    private String authToken;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();

        chatId = getIntent().getLongExtra("chat_id", -1);
        partnerUserId = getIntent().getLongExtra("partner_user_id", -1);
        chatNameStr = getIntent().getStringExtra("chat_name");
        if (chatNameStr == null) chatNameStr = "Чат";

        currentUserId = RetrofitClient.getUserId();
        Log.d("ChatActivity", "🔐 currentUserId initialized: " + currentUserId);

        authToken = RetrofitClient.getToken();

        initViews();
        setupClickListeners();

        if (authToken != null && !authToken.isEmpty()) {
            initWebSocket(authToken);
        }

        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        broadcastUserStatus(true);
        if (stompClient != null && stompClient.isConnected()) {
            markAllMessagesAsRead();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        broadcastUserStatus(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
    }

    private void initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        noConnectionState = findViewById(R.id.noConnectionState);
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        backButton = findViewById(R.id.backButton);
        menuIcon = findViewById(R.id.menuIcon);
        statusIcon = findViewById(R.id.statusIcon);
        statusText = findViewById(R.id.statusText);
        chatName = findViewById(R.id.chatName);
        retryButton = findViewById(R.id.retryButton);

        chatName.setText(chatNameStr);
        updatePartnerStatus(false);

        messageAdapter = new MessageAdapter();
        messagesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        messagesRecyclerView.setAdapter(messageAdapter);
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        menuIcon.setOnClickListener(v -> showChatMenu());
        sendButton.setOnClickListener(v -> sendMessage());

        messageInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) {
                sendMessage();
                return true;
            }
            return false;
        });

        messageInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (stompClient != null && stompClient.isConnected()) {
                    stompClient.sendTyping(String.valueOf(chatId), s.length() > 0);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        retryButton.setOnClickListener(v -> loadMessages());
    }

    private void updatePartnerStatus(boolean isOnline) {
        runOnUiThread(() -> {
            if (isOnline) {
                statusIcon.setImageResource(R.drawable.ic_status_online);
                statusText.setText("Онлайн");
                statusText.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                statusIcon.setImageResource(R.drawable.ic_status_offline);
                statusText.setText("Оффлайн");
                statusText.setTextColor(Color.parseColor("#757575"));
            }
            statusIcon.setVisibility(View.VISIBLE);
            statusText.setVisibility(View.VISIBLE);
            statusText.animate().alpha(1f).setDuration(200).start();
        });
    }

    private void broadcastUserStatus(boolean isOnline) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w("ChatActivity", "⚠️ Cannot send user status: WebSocket not connected");
            return;
        }

        Map<String, Object> status = new HashMap<>();
        status.put("userId", currentUserId);
        status.put("online", isOnline);

        Log.d("ChatActivity", "📤 Sending user.status: userId=" + currentUserId + ", online=" + isOnline);
        stompClient.send("/app/user.status", status);
    }

    private void initWebSocket(final String token) {
        if (token == null || token.isEmpty()) return;

        Log.d("ChatActivity", "🔌 Initializing WebSocket, partnerUserId=" + partnerUserId);

        stompClient = new StompClient(token);
        stompClient.setListener(new StompClient.StompListener() {
            @Override
            public void onConnected() {
                Log.d("ChatActivity", "✅ WebSocket connected");

                runOnUiThread(() -> {
                    if (stompClient == null) return;


                    if (chatId > 0) {
                        stompClient.subscribeToChat(String.valueOf(chatId), payload -> {
                            Log.d("ChatActivity", "📨 MESSAGE RECEIVED from WS!");
                            if (payload instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> msg = (Map<String, Object>) payload;
                                onNewMessageReceived(msg);
                            } else {
                                Log.e("ChatActivity", "❌ Payload is not a Map: " + payload);
                            }
                        });
                        Log.d("ChatActivity", "📡 Subscribed to /topic/chat/" + chatId);
                    }


                    if (partnerUserId > 0) {
                        subscribeToPartnerStatus();
                    }


                    stompClient.subscribe("/topic/message/status", payload -> {
                        Log.d("ChatActivity", "📨 Message status update received: " + payload);

                        if (payload instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> update = (Map<String, Object>) payload;

                            Object msgIdObj = update.get("messageId");
                            Long messageId = null;
                            if (msgIdObj instanceof Number) {
                                messageId = ((Number) msgIdObj).longValue();
                            }

                            String newStatus = (String) update.get("status");

                            if (messageId != null && newStatus != null) {
                                int status = parseStatus(newStatus);
                                Log.d("ChatActivity", "✏️ Updating message " + messageId + " status to " + status);

                                boolean updated = messageAdapter.updateItemStatus(messageId, status);
                                if (updated) {
                                    Log.d("ChatActivity", "✓✓ UI updated for message " + messageId);
                                }
                            }
                        }
                    });
                    Log.d("ChatActivity", "📡 Subscribed to /topic/message/status");

                    broadcastUserStatus(true);

                    if (messageAdapter.getItemCount() > 0) {
                        markAllMessagesAsRead();
                    }
                });
            }

            @Override
            public void onDisconnected() {
                Log.d("ChatActivity", "❌ WebSocket disconnected");
                runOnUiThread(() -> {
                    Toast.makeText(ChatActivity.this, "Соединение потеряно", Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onMessage(String destination, Object payload) {}

            @Override
            public void onError(String error) {
                Log.e("ChatActivity", "❌ WS Error: " + error);
            }
        });

        Log.d("ChatActivity", "🔌 Connecting to WebSocket...");
        stompClient.connect();
    }

    private void subscribeToPartnerStatus() {
        if (partnerUserId <= 0 || stompClient == null || !stompClient.isConnected()) return;

        stompClient.subscribe("/topic/user/" + partnerUserId + "/status", payload -> {
            Log.d("ChatActivity", "👤 Status update received: " + payload);
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) payload;
                Boolean online = (Boolean) status.get("online");
                Object userIdObj = status.get("userId");
                Long userId;

                if (userIdObj instanceof Number) {
                    userId = ((Number) userIdObj).longValue();
                } else {

                    userId = null;
                    Log.w("ChatActivity", "Invalid type for userId: " + userIdObj.getClass());
                }


                if (online != null && userId != null && userId == partnerUserId) {
                    Log.d("ChatActivity", "🟢 Updating partner status: online=" + online);
                    updatePartnerStatus(online);
                }
            }
        });
        Log.d("ChatActivity", "📡 Subscribed to /topic/user/" + partnerUserId + "/status");
    }

    private void tryExtractPartnerUserId(List<MessageItem> messages) {
        if (partnerUserId > 0) return;

        for (MessageItem item : messages) {
            if (item.getType() == MessageItem.TYPE_INCOMING) {
                partnerUserId = item.getSenderId();
                Log.d("ChatActivity", "🔍 Extracted partnerUserId=" + partnerUserId + " from history");

                if (stompClient != null && stompClient.isConnected()) {
                    subscribeToPartnerStatus();
                }
                return;
            }
        }
    }

    private void onNewMessageReceived(final Map<String, Object> msg) {
        if (msg == null) return;

        runOnUiThread(() -> {
            try {
                Object idObj = msg.get("id");
                long realId = idObj != null && idObj instanceof Number ? ((Number) idObj).longValue() : System.currentTimeMillis();

                String text = msg.get("text") != null ? (String) msg.get("text") : "";
                String createdAt = (String) msg.get("createdAt");
                String time = createdAt != null ? formatTime(createdAt) : getCurrentTime();

                Object senderIdObj = msg.get("senderId");
                long senderId = 0;
                if (senderIdObj instanceof Number) {
                    senderId = ((Number) senderIdObj).longValue();
                }

                int status = parseStatus(msg.get("status"));
                int type = (senderId == currentUserId) ? MessageItem.TYPE_OUTGOING : MessageItem.TYPE_INCOMING;

                Log.d("ChatActivity", "🔍 New msg: id=" + realId + ", senderId=" + senderId + ", type=" + (type == MessageItem.TYPE_OUTGOING ? "OUT" : "IN"));


                if (type == MessageItem.TYPE_OUTGOING) {
                    List<MessageItem> items = messageAdapter.getItems();

                    for (int i = items.size() - 1; i >= 0; i--) {
                        MessageItem item = items.get(i);


                        if (item.getType() == MessageItem.TYPE_OUTGOING &&
                                item.getText().equals(text) &&
                                item.getStatus() == MessageItem.STATUS_SENT &&
                                item.getSenderId() == currentUserId) {

                            Log.d("ChatActivity", "🔄 Found optimistic message, updating with real ID: " + realId);

                            item.setId(realId);
                            item.setStatus(status);


                            messageAdapter.notifyItemChanged(i);
                            return;
                        }
                    }
                }

                MessageItem newItem = new MessageItem(realId, text, time, status, type, senderId);
                messageAdapter.addMessage(newItem);
                showState(State.CONTENT);
                scrollToBottom();

                if (partnerUserId <= 0 && senderId != currentUserId) {
                    partnerUserId = senderId;
                    Log.d("ChatActivity", "🔍 Extracted partnerUserId=" + partnerUserId + " from real-time message");
                    if (stompClient != null && stompClient.isConnected()) {
                        subscribeToPartnerStatus();
                    }
                }

                if (senderId != currentUserId && stompClient != null && stompClient.isConnected()) {
                    Log.d("ChatActivity", "📤 Auto-sending message.read for messageId=" + realId);
                    sendMessageReadRequest(realId);
                }

            } catch (Exception e) {
                Log.e("ChatActivity", "❌ Error processing message", e);
                e.printStackTrace();
            }
        });
    }

    private void markAllMessagesAsRead() {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w("ChatActivity", "⚠️ Cannot mark as read: WebSocket not connected");
            return;
        }

        List<MessageItem> items = messageAdapter.getItems();
        int markedCount = 0;

        for (int i = 0; i < items.size(); i++) {
            MessageItem item = items.get(i);

            if (item.getType() == MessageItem.TYPE_INCOMING && item.getStatus() != MessageItem.STATUS_READ) {
                item.setStatus(MessageItem.STATUS_READ);
                messageAdapter.notifyItemChanged(i);
                markedCount++;

                sendMessageReadRequest(item.getId());
            }
        }

        if (markedCount > 0) {
            Log.d("ChatActivity", "✅ Marked " + markedCount + " messages as read");
        }
    }

    private void sendMessageReadRequest(long messageId) {
        if (stompClient == null || !stompClient.isConnected()) return;

        Map<String, Object> readRequest = new HashMap<>();
        readRequest.put("messageId", messageId);
        readRequest.put("userId", currentUserId);
        readRequest.put("chatId", chatId);

        Log.d("ChatActivity", "📤 Sending message.read: messageId=" + messageId + ", chatId=" + chatId);
        stompClient.send("/app/message.read", readRequest);
    }

    private void loadMessages() {
        showState(State.LOADING);
        showNoConnection(false);

        apiService.getMessages(chatId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<MessageItem> messages = mapMessagesWithDates(response.body());
                    if (messages.isEmpty()) {
                        showState(State.EMPTY);
                    } else {
                        messageAdapter.setMessages(messages);
                        showState(State.CONTENT);
                        scrollToBottom();

                        tryExtractPartnerUserId(messages);

                        if (stompClient != null && stompClient.isConnected()) {
                            markAllMessagesAsRead();
                        }
                    }
                } else {
                    Log.e("ChatActivity", "Failed to load messages: " + response.code());
                    showNoConnection(true);
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                Log.e("ChatActivity", "Network error loading messages", t);
                showNoConnection(true);
            }
        });
    }

    private List<MessageItem> mapMessagesWithDates(List<Map<String, Object>> backendMessages) {
        List<MessageItem> items = new ArrayList<>();
        String lastDateHeader = null;
        LocalDate today = LocalDate.now();

        for (Map<String, Object> msg : backendMessages) {
            Object idObj = msg.get("id");
            long id = idObj != null && idObj instanceof Number ? ((Number) idObj).longValue() : System.currentTimeMillis();

            String text = msg.get("text") != null ? (String) msg.get("text") : "";
            String createdAt = (String) msg.get("createdAt");
            String time = createdAt != null ? formatTime(createdAt) : getCurrentTime();

            Object senderIdObj = msg.get("senderId");
            long senderId = 0;
            if (senderIdObj instanceof Number) {
                senderId = ((Number) senderIdObj).longValue();
            }

            int status = parseStatus(msg.get("status"));
            int type = (senderId == currentUserId) ? MessageItem.TYPE_OUTGOING : MessageItem.TYPE_INCOMING;

            String currentDateHeader = null;
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
                items.add(new MessageItem(0, currentDateHeader, "00:00", 0, MessageItem.TYPE_DATE, 0));
                lastDateHeader = currentDateHeader;
            }

            items.add(new MessageItem(id, text, time, status, type, senderId));
        }
        return items;
    }

    private int parseStatus(Object statusObj) {
        if (statusObj == null) return MessageItem.STATUS_SENT;
        String s = statusObj.toString().toLowerCase().trim();
        if (s.contains("failed") || s.contains("error")) return MessageItem.STATUS_FAILED;
        if (s.contains("read")) return MessageItem.STATUS_READ;
        if (s.contains("delivered") || s.contains("received")) return MessageItem.STATUS_DELIVERED;
        if (s.contains("sent")) return MessageItem.STATUS_SENT;
        return MessageItem.STATUS_SENT;
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return getCurrentTime();
        try {
            String normalized = isoTime.endsWith("Z") ? isoTime : isoTime + "Z";
            Instant instant = Instant.parse(normalized);
            return instant.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"));
        } catch (Exception e) {
            return getCurrentTime();
        }
    }

    private String getCurrentTime() {
        return new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }


        messageInput.setText("");
        showNoConnection(false);

        long tempId = System.currentTimeMillis();
        MessageItem tempMsg = new MessageItem(
                tempId,
                text,
                getCurrentTime(),
                MessageItem.STATUS_SENT,
                MessageItem.TYPE_OUTGOING,
                currentUserId
        );

        messageAdapter.addMessage(tempMsg);
        showState(State.CONTENT);
        scrollToBottom();
        Log.d("ChatActivity", "➕ Added optimistic message to UI");


        if (stompClient != null && stompClient.isConnected()) {
            Log.d("ChatActivity", "📤 Sending via WebSocket...");
            stompClient.sendMessage(String.valueOf(chatId), text);
        } else {
            Log.d("ChatActivity", "⚠️ WS disconnected, sending via REST...");
            sendViaRest(text);
        }
    }

    private void sendViaRest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", chatId);
        request.put("senderId", currentUserId);
        request.put("content", text);

        apiService.sendMessage(chatId, request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d("ChatActivity", "✅ REST message sent successfully");
                } else {
                    Toast.makeText(ChatActivity.this, "Ошибка отправки", Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Нет соединения", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showNoConnection(boolean show) {
        noConnectionState.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showChatMenu() {
        String[] items = {"Информация о чате", "Очистить историю", "Выйти"};
        new AlertDialog.Builder(this)
                .setTitle(chatNameStr)
                .setItems(items, (dialog, which) -> {
                    if (which == 2) finish();
                    else Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void scrollToBottom() {
        messagesRecyclerView.post(() -> {
            if (messageAdapter.getItemCount() > 0) {
                messagesRecyclerView.smoothScrollToPosition(messageAdapter.getItemCount() - 1);
            }
        });
    }

    private enum State { LOADING, CONTENT, EMPTY, ERROR }

    private void showState(State state) {
        boolean isContent = (state == State.CONTENT);
        messagesRecyclerView.setVisibility(isContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
    }
}