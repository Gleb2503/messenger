package com.example.messenger.chat;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.messenger.media.MediaViewerActivity;
import com.example.messenger.status.AppStatusManager;
import com.example.messenger.message.MessageAdapter;
import com.example.messenger.message.MessageItem;
import com.example.messenger.MessengerApplication;
import com.example.messenger.R;
import com.example.messenger.status.UserStatusManager;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.api.attachment.AttachmentResponse;
import com.example.messenger.data.websocket.MessageType;
import com.example.messenger.data.websocket.StompClient;
import com.example.messenger.util.FileHelper;
import com.example.messenger.util.ImageUtils;
import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    private static final String TAG = "ChatActivity";
    private static final int IMAGE_PICKER_REQUEST = 1001;
    private static final int PAGE_SIZE = 50;

    private final Map<String, String> statusSubscriptions = new HashMap<>();
    private boolean chatTopicSubscribed = false;
    private String chatTopicSubscriptionId = null;

    private RecyclerView messagesRecyclerView;
    private LinearLayoutManager layoutManager;
    private LinearLayout emptyState, noConnectionState;
    private EditText messageInput;
    private ImageView sendButton, backButton, menuIcon, statusIcon, attachButton, partnerAvatar;
    private TextView chatName, statusText;
    private Button retryButton;
    private ProgressBar topProgressBar;

    private MessageAdapter messageAdapter;
    private ApiService apiService;
    private StompClient stompClient;
    private boolean partnerStatusSubscribed = false;

    private long chatId;
    private long partnerUserId;
    private String chatNameStr;
    private String authToken;

    private UserStatusManager.OnWebSocketReadyListener readyListener;

    private boolean isLoadingMore = false;
    private boolean hasMoreMessages = true;
    private Long oldestMessageId = null;

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

        resetPagination();

        long currentUserId = getCurrentUserId();
        Log.d(TAG, "🔐 currentUserId initialized: " + currentUserId);

        authToken = RetrofitClient.getToken();

        initViews();
        setupClickListeners();

        if (partnerUserId > 0) {
            Boolean cached = AppStatusManager.getInstance().getStatus(partnerUserId);
            if (cached != null) {
                Log.d(TAG, "📦 Showing cached status: " + cached);
                updatePartnerStatus(cached);
            } else {
                Log.d(TAG, "⚠️ No cache, showing default Offline");
                updatePartnerStatus(false);
            }
            loadPartnerAvatar();
        }

        UserStatusManager statusManager = ((MessengerApplication) getApplication()).getStatusManager();
        StompClient sharedClient = statusManager.getStompClient();

        if (sharedClient != null && sharedClient.isConnected()) {
            Log.d(TAG, "🔗 Using shared StompClient from UserStatusManager");
            this.stompClient = sharedClient;
            long managerUserId = statusManager.getCurrentUserId();
            subscribeToChatTopics(managerUserId);

            if (partnerUserId > 0 && !partnerStatusSubscribed) {
                subscribeToPartnerStatus(managerUserId);
                partnerStatusSubscribed = true;
            }
        } else {
            Log.d(TAG, "⚠️ WebSocket not connected yet, will subscribe when ready");

            readyListener = () -> {
                if (!isFinishing() && !isDestroyed()) {
                    UserStatusManager sm = ((MessengerApplication) getApplication()).getStatusManager();
                    this.stompClient = sm.getStompClient();
                    long userId = sm.getCurrentUserId();
                    subscribeToChatTopics(userId);

                    if (partnerUserId > 0 && !partnerStatusSubscribed) {
                        subscribeToPartnerStatus(userId);
                        partnerStatusSubscribed = true;
                    }
                }
            };
            statusManager.addOnReadyListener(readyListener);

            if (authToken != null && !authToken.isEmpty()) {
                statusManager.initWebSocket(authToken);
            }
        }

        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        markAllMessagesAsRead();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (readyListener != null) {
            UserStatusManager sm = ((MessengerApplication) getApplication()).getStatusManager();
            if (sm != null) {
                sm.removeOnReadyListener(readyListener);
            }
            readyListener = null;
        }

        if (stompClient != null && stompClient.isConnected()) {
            if (chatTopicSubscribed && chatId > 0) {
                String chatTopic = "/topic/chat/" + chatId;
                stompClient.unsubscribe(chatTopic, chatTopicSubscriptionId);
                Log.d(TAG, "🔇 Unsubscribed from " + chatTopic);
                chatTopicSubscribed = false;
                chatTopicSubscriptionId = null;
            }

            for (Map.Entry<String, String> entry : statusSubscriptions.entrySet()) {
                String destination = entry.getKey();
                String subscriptionId = entry.getValue();
                stompClient.unsubscribe(destination, subscriptionId);
                Log.d(TAG, "🔇 Unsubscribed from " + destination);
            }
            statusSubscriptions.clear();
        }

        stompClient = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                sendImageMessage(imageUri);
            }
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
        partnerAvatar = findViewById(R.id.partnerAvatar);
        attachButton = findViewById(R.id.attachButton);
        topProgressBar = findViewById(R.id.topProgressBar);

        chatName.setText(chatNameStr);
        updatePartnerStatus(false);

        messageAdapter = new MessageAdapter();

        messageAdapter.setOnMediaViewerListener((mediaItems, position) -> {
            MediaViewerActivity.start(ChatActivity.this, mediaItems, position, chatId);
        });

        messageAdapter.setOnMediaClickListener((url, type) -> openMediaViewer(url, type));

        layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(messageAdapter);

        setupScrollListener();
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());
        menuIcon.setOnClickListener(v -> showChatMenu());
        sendButton.setOnClickListener(v -> sendMessage());
        attachButton.setOnClickListener(v -> openGallery());

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

    private void openGallery() {
        ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .galleryOnly()
                .start(IMAGE_PICKER_REQUEST);
    }

    private void loadPartnerAvatar() {
        if (partnerAvatar == null || partnerUserId <= 0) return;

        String cachedAvatar = getSharedPreferences("messenger_prefs", MODE_PRIVATE)
                .getString("avatar_user_" + partnerUserId, "");

        if (cachedAvatar != null && !cachedAvatar.isEmpty() && cachedAvatar.startsWith("http")) {
            loadAvatarIntoView(cachedAvatar);
            return;
        }

        partnerAvatar.setImageResource(R.drawable.bg_logo_gradient);

        if (authToken == null || authToken.isEmpty()) return;

        apiService.getUserProfile(partnerUserId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object avatarObj = response.body().get("avatarUrl");
                    if (avatarObj instanceof String) {
                        String avatarUrl = (String) avatarObj;
                        if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                            getSharedPreferences("messenger_prefs", MODE_PRIVATE)
                                    .edit()
                                    .putString("avatar_user_" + partnerUserId, avatarUrl)
                                    .apply();
                            if (!isFinishing() && !isDestroyed()) {
                                runOnUiThread(() -> loadAvatarIntoView(avatarUrl));
                            }
                        }
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Failed to fetch avatar for user " + partnerUserId, t);
            }
        });
    }

    private void loadAvatarIntoView(String url) {
        if (partnerAvatar == null) return;
        if (url != null && !url.isEmpty() && url.startsWith("http")) {
            Glide.with(this)
                    .load(url.trim())
                    .placeholder(R.drawable.bg_logo_gradient)
                    .error(R.drawable.bg_logo_gradient)
                    .circleCrop()
                    .into(partnerAvatar);
        } else {
            partnerAvatar.setImageResource(R.drawable.bg_logo_gradient);
        }
    }

    private void updatePartnerStatus(boolean isOnline) {
        Log.d(TAG, "🎨 Updating UI: online=" + isOnline);
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
            Log.d(TAG, "✅ UI Updated");
        });
    }

    private void subscribeToChatTopics(long currentUserId) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ Cannot subscribe: WebSocket not connected");
            return;
        }

        if (chatId > 0 && !chatTopicSubscribed) {
            String chatTopic = "/topic/chat/" + chatId;
            String subId = stompClient.subscribe(chatTopic, payload -> {
                if (isFinishing() || isDestroyed()) {
                    Log.w(TAG, "⚠️ Activity destroyed, skipping message");
                    return;
                }
                Log.d(TAG, "📨 MESSAGE RECEIVED from WS!");
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> msg = (Map<String, Object>) payload;
                    onNewMessageReceived(msg, currentUserId);
                }
            });

            if (subId != null) {
                chatTopicSubscribed = true;
                chatTopicSubscriptionId = subId;
                Log.d(TAG, "📡 Subscribed to " + chatTopic + " (id: " + subId + ")");
            }
        } else if (chatId > 0) {
            Log.d(TAG, "✅ Already subscribed to /topic/chat/" + chatId + ", skipping");
        }

        if (currentUserId > 0) {
            String personalQueue = "/user/" + currentUserId + "/queue/user.status";
            stompClient.subscribe(personalQueue, payload -> {
                if (isFinishing() || isDestroyed()) return;
                Log.d(TAG, "📥 PERSONAL QUEUE response: " + payload);
                if (payload instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> response = (Map<String, Object>) payload;
                    Object userIdObj = response.get("userId");
                    Object onlineObj = response.get("online");
                    if (userIdObj instanceof Number && onlineObj instanceof Boolean) {
                        Long targetUserId = ((Number) userIdObj).longValue();
                        Boolean isOnline = (Boolean) onlineObj;
                        Log.d(TAG, "🎯 Personal response: user=" + targetUserId + ", online=" + isOnline);
                        if (targetUserId == partnerUserId) {
                            AppStatusManager.getInstance().updateStatus(partnerUserId, isOnline, "personal_queue");
                            runOnUiThread(() -> updatePartnerStatus(isOnline));
                        }
                    }
                }
            });
            Log.d(TAG, "📡 Subscribed to personal queue: " + personalQueue);
        }

        stompClient.subscribe("/topic/message/status", payload -> {
            if (isFinishing() || isDestroyed()) return;

            Log.d(TAG, "📨 Message status update received: " + payload);

            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> update = (Map<String, Object>) payload;

                Object msgIdObj = update.get("messageId");
                Long messageId = null;

                if (msgIdObj instanceof Number) {
                    messageId = ((Number) msgIdObj).longValue();
                } else if (msgIdObj instanceof String) {
                    try {
                        messageId = Long.parseLong((String) msgIdObj);
                    } catch (NumberFormatException e) {
                        Log.e(TAG, "❌ Failed to parse messageId: " + msgIdObj);
                    }
                }

                String newStatus = (String) update.get("status");
                Long chatIdFromUpdate = null;
                if (update.get("chatId") instanceof Number) {
                    chatIdFromUpdate = ((Number) update.get("chatId")).longValue();
                }

                Log.d(TAG, "✏️ Parsed: messageId=" + messageId + ", status=" + newStatus + ", chatId=" + chatIdFromUpdate);

                if (messageId != null && newStatus != null) {
                    int status = parseStatus(newStatus);
                    boolean updated = messageAdapter.updateItemStatus(messageId, status);

                    if (updated) {
                        Log.d(TAG, "✓✓ UI updated for message " + messageId);
                    } else {
                        Log.w(TAG, "⚠️ Message " + messageId + " not found in adapter, trying to reload...");
                    }
                }
            }
        });
        Log.d(TAG, "📡 Subscribed to /topic/message/status");

        if (partnerUserId > 0 && !partnerStatusSubscribed) {
            subscribeToPartnerStatus(currentUserId);
            partnerStatusSubscribed = true;
        }

        if (messageAdapter.getItemCount() > 0) {
            markAllMessagesAsRead();
        }
    }

    private void subscribeToPartnerStatus(long currentUserId) {
        if (partnerUserId <= 0) {
            Log.w(TAG, "⚠️ Cannot subscribe: invalid partnerUserId=" + partnerUserId);
            return;
        }
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ Cannot subscribe: WebSocket not connected");
            return;
        }

        String destination = "/topic/user/" + partnerUserId + "/status";
        if (statusSubscriptions.containsKey(destination)) {
            Log.d(TAG, "✅ Already subscribed to " + destination + ", skipping");
            return;
        }

        String subscriptionId = stompClient.subscribe(destination, payload -> {
            Log.d(TAG, "📥 INCOMING status: " + payload);
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) payload;
                Boolean online = (Boolean) status.get("online");
                Object userIdObj = status.get("userId");
                Long userId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
                if (online != null && userId != null && userId == partnerUserId) {
                    Log.d(TAG, "🟢 Updating: online=" + online);
                    AppStatusManager.getInstance().updateStatus(partnerUserId, online, "public_topic");
                    runOnUiThread(() -> updatePartnerStatus(online));
                }
            }
        });

        if (subscriptionId != null) {
            statusSubscriptions.put(destination, subscriptionId);
            Log.d(TAG, "📡 Subscribed to " + destination + " with id: " + subscriptionId);
            Map<String, Object> request = new HashMap<>();
            request.put("userId", partnerUserId);
            request.put("requesterId", currentUserId);
            stompClient.send("/app/user.status.request", request);
        }
    }

    private void tryExtractPartnerUserId(List<MessageItem> messages) {
        if (partnerStatusSubscribed || partnerUserId > 0) return;
        for (MessageItem item : messages) {
            if (item.getType() == MessageItem.TYPE_INCOMING && item.getSenderId() > 0) {
                partnerUserId = item.getSenderId();
                Log.d(TAG, "🔍 Extracted partnerUserId=" + partnerUserId + " from history");
                if (stompClient != null && stompClient.isConnected()) {
                    Log.d(TAG, "📡 Calling subscribeToPartnerStatus() after extraction");
                    subscribeToPartnerStatus(getCurrentUserId());
                    partnerStatusSubscribed = true;
                } else {
                    Log.w(TAG, "⚠️ WebSocket not connected yet, will subscribe later");
                }
                return;
            }
        }
    }

    private void onNewMessageReceived(final Map<String, Object> msg, long currentUserId) {
        if (msg == null) return;
        if (isFinishing() || isDestroyed()) {
            Log.w(TAG, "⚠️ Activity is finishing/destroyed, skipping message update");
            return;
        }

        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) {
                Log.w(TAG, "⚠️ Activity destroyed during UI update, skipping");
                return;
            }

            try {
                Object idObj = msg.get("id");
                long realId = -1;
                if (idObj instanceof Number) {
                    realId = ((Number) idObj).longValue();
                }
                if (realId <= 0) {
                    Log.e(TAG, "❌ Invalid messageId: " + idObj);
                    return;
                }

                String text = msg.get("text") != null ? (String) msg.get("text") : "";
                String createdAt = (String) msg.get("createdAt");
                String time = createdAt != null ? formatTime(createdAt) : getCurrentTime();

                Object senderIdObj = msg.get("senderId");
                long senderId = senderIdObj instanceof Number ? ((Number) senderIdObj).longValue() : 0;

                int status = parseStatus(msg.get("status"));
                int type = (senderId == currentUserId) ? MessageItem.TYPE_OUTGOING : MessageItem.TYPE_INCOMING;

                MessageType messageType = MessageType.TEXT;
                String fileUrl = null;
                String fileName = null;
                long fileSize = 0;

                if (msg.containsKey("attachments") && msg.get("attachments") instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> attachments = (List<Map<String, Object>>) msg.get("attachments");
                    if (!attachments.isEmpty()) {
                        Map<String, Object> att = attachments.get(0);
                        fileUrl = (String) att.get("fileUrl");
                        fileName = (String) att.get("fileName");
                        if (att.get("fileSize") instanceof Number) {
                            fileSize = ((Number) att.get("fileSize")).longValue();
                        }
                        String attType = (String) att.get("fileType");
                        if (attType != null) {
                            if (attType.startsWith("image/")) messageType = MessageType.IMAGE;
                            else if (attType.startsWith("video/")) messageType = MessageType.VIDEO;
                            else messageType = MessageType.FILE;
                        }
                        Log.d(TAG, "📎 Parsed attachment from WS: url=" + fileUrl + ", type=" + messageType);
                    }
                }

                Log.d(TAG, "🔍 New msg: id=" + realId + ", senderId=" + senderId +
                        ", type=" + (type == MessageItem.TYPE_OUTGOING ? "OUT" : "IN") +
                        ", messageType=" + messageType);

                if (type == MessageItem.TYPE_OUTGOING) {
                    List<MessageItem> items = messageAdapter.getItems();
                    for (int i = items.size() - 1; i >= 0; i--) {
                        MessageItem item = items.get(i);

                        boolean isOptimistic = item.getType() == MessageItem.TYPE_OUTGOING &&
                                item.getStatus() == MessageItem.STATUS_SENT &&
                                item.getSenderId() == currentUserId;

                        if (isOptimistic) {
                            boolean textMatch = messageType == MessageType.TEXT && item.getText().equals(text);
                            boolean fileMatch = messageType != MessageType.TEXT &&
                                    item.getMessageType() == messageType;
                            boolean idMatch = item.getId() < 0;

                            if (textMatch || fileMatch || idMatch) {
                                Log.d(TAG, "🔄 Found optimistic message, updating with real ID: " + realId);
                                item.setId(realId);
                                item.setStatus(status);

                                if (fileUrl != null && !fileUrl.isEmpty()) {
                                    item.setImageUrl(fileUrl);
                                    item.setFileName(fileName);
                                    item.setFileSize(fileSize);
                                    item.setMessageType(messageType);
                                    Log.d(TAG, "📎 Updated attachment from WS: " + fileUrl);
                                }

                                messageAdapter.notifyItemChanged(i);
                                scrollToBottom(true);
                                return;
                            }
                        }
                    }
                    Log.w(TAG, "⚠️ Optimistic message NOT found for id=" + realId +
                            ", messageType=" + messageType);
                }

                MessageItem newItem = new MessageItem(realId, text, time, status, type, senderId, messageType, null);
                if (fileUrl != null && !fileUrl.isEmpty()) {
                    newItem.setImageUrl(fileUrl);
                    newItem.setFileName(fileName);
                    newItem.setFileSize(fileSize);
                }

                messageAdapter.addMessage(newItem);
                showState(State.CONTENT);
                scrollToBottom(true);
                Log.d(TAG, "➕ Added new message with id=" + realId + ", hasAttachment=" + (fileUrl != null));

                if (senderId != currentUserId && stompClient != null && stompClient.isConnected()) {
                    Log.d(TAG, "📤 Auto-sending message.read for messageId=" + realId);
                    sendMessageReadRequest(realId);
                }

            } catch (Exception e) {
                Log.e(TAG, "❌ Error processing message", e);
                e.printStackTrace();
            }
        });
    }

    private void markAllMessagesAsRead() {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ Cannot mark as read: WebSocket not connected");
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
            Log.d(TAG, "✅ Marked " + markedCount + " messages as read");
        }
    }

    private void sendMessageReadRequest(long messageId) {
        if (stompClient == null || !stompClient.isConnected()) return;
        Map<String, Object> readRequest = new HashMap<>();
        readRequest.put("messageId", messageId);
        readRequest.put("userId", getCurrentUserId());
        readRequest.put("chatId", chatId);
        Log.d(TAG, "📤 Sending message.read: messageId=" + messageId + ", chatId=" + chatId);
        stompClient.send("/app/message.read", readRequest);
    }

    private void loadMessages() {
        loadMessages(false);
    }

    private void loadMessages(boolean isLoadMore) {
        if (isLoadMore) {
            if (!hasMoreMessages || isLoadingMore) return;
            isLoadingMore = true;
            showTopLoadingIndicator(true);
        } else {
            oldestMessageId = null;
            hasMoreMessages = true;
            messageAdapter.clearMessages();
            showState(State.LOADING);
            showNoConnection(false);
        }

        apiService.getMessages(chatId, isLoadMore ? oldestMessageId : null, PAGE_SIZE)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (isLoadMore) {
                            isLoadingMore = false;
                            showTopLoadingIndicator(false);
                        }

                        if (response.isSuccessful() && response.body() != null) {
                            List<Map<String, Object>> backendMessages = response.body();

                            if (backendMessages.isEmpty()) {
                                hasMoreMessages = false;
                                if (!isLoadMore) showState(State.EMPTY);
                                return;
                            }

                            List<MessageItem> newMessages = mapMessagesWithDates(backendMessages);

                            if (!newMessages.isEmpty()) {
                                for (MessageItem item : newMessages) {
                                    if (item.getType() != MessageItem.TYPE_DATE && item.getId() > 0) {
                                        oldestMessageId = item.getId();
                                        break;
                                    }
                                }
                            }

                            if (isLoadMore) {
                                messageAdapter.addMessagesAtTop(newMessages);
                                if (layoutManager != null && !newMessages.isEmpty()) {
                                    layoutManager.scrollToPositionWithOffset(newMessages.size(), 0);
                                }
                            } else {
                                messageAdapter.setMessages(newMessages);
                                messagesRecyclerView.post(() -> {
                                    scrollToBottom(false);
                                    if (stompClient != null && stompClient.isConnected()) {
                                        markAllMessagesAsRead();
                                    }
                                });
                                tryExtractPartnerUserId(newMessages);
                            }

                            if (backendMessages.size() < PAGE_SIZE) {
                                hasMoreMessages = false;
                            }

                            if (!isLoadMore) showState(State.CONTENT);
                        } else {
                            Log.e(TAG, "Failed to load messages: " + response.code());
                            if (!isLoadMore) showNoConnection(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isLoadMore) {
                            isLoadingMore = false;
                            showTopLoadingIndicator(false);
                        }
                        Log.e(TAG, "Network error loading messages", t);
                        if (!isLoadMore) showNoConnection(true);
                    }
                });
    }

    private List<MessageItem> mapMessagesWithDates(List<Map<String, Object>> backendMessages) {
        List<MessageItem> items = new ArrayList<>();
        long currentUserId = getCurrentUserId();

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

            MessageType messageType = MessageType.TEXT;
            String fileUrl = null;

            if (msg.containsKey("attachments") && msg.get("attachments") instanceof List) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> attachments = (List<Map<String, Object>>) msg.get("attachments");
                if (!attachments.isEmpty()) {
                    Map<String, Object> firstAttachment = attachments.get(0);
                    fileUrl = (String) firstAttachment.get("fileUrl");
                    String attachmentType = (String) firstAttachment.get("fileType");
                    if (attachmentType != null && attachmentType.startsWith("image/")) {
                        messageType = MessageType.IMAGE;
                    } else if (attachmentType != null && attachmentType.startsWith("video/")) {
                        messageType = MessageType.VIDEO;
                    } else {
                        messageType = MessageType.FILE;
                    }
                }
            }

            MessageItem item = new MessageItem(id, text, time, status, type, senderId, messageType, null);
            item.setCreatedAt(createdAt);
            if (fileUrl != null && !fileUrl.isEmpty()) {
                item.setImageUrl(fileUrl);
            }
            items.add(item);
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

    private long getCurrentUserId() {
        return RetrofitClient.getUserId();
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Введите сообщение", Toast.LENGTH_SHORT).show();
            return;
        }
        messageInput.setText("");
        showNoConnection(false);

        long currentUserId = getCurrentUserId();
        long tempId = -System.currentTimeMillis();

        MessageItem tempMsg = new MessageItem(
                tempId, text, getCurrentTime(),
                MessageItem.STATUS_SENT, MessageItem.TYPE_OUTGOING,
                currentUserId, MessageType.TEXT, null
        );
        messageAdapter.addMessage(tempMsg);
        showState(State.CONTENT);
        scrollToBottom(true);
        Log.d(TAG, "➕ Added optimistic text message to UI");

        if (stompClient != null && stompClient.isConnected()) {
            Log.d(TAG, "📤 Sending text via WebSocket...");
            stompClient.sendMessage(String.valueOf(chatId), text, MessageType.TEXT);
            notifyMessageSent();
        } else {
            Log.d(TAG, "⚠️ WS disconnected, sending text via REST...");
            sendViaRest(text);
        }
    }

    private void sendImageMessage(Uri imageUri) {
        if (imageUri == null) {
            Toast.makeText(this, "Ошибка выбора изображения", Toast.LENGTH_SHORT).show();
            return;
        }

        showNoConnection(false);
        long currentUserId = getCurrentUserId();
        long tempId = -System.currentTimeMillis() - 1;

        MessageItem tempMsg = new MessageItem(
                tempId, "", getCurrentTime(),
                MessageItem.STATUS_SENDING, MessageItem.TYPE_OUTGOING,
                currentUserId, MessageType.IMAGE, imageUri
        );
        messageAdapter.addMessage(tempMsg);
        showState(State.CONTENT);
        scrollToBottom(true);
        Log.d(TAG, "➕ Added optimistic image message to UI");

        createEmptyMessageOnServer(chatId, "image", new Callback<Long>() {
            @Override
            public void onResponse(Call<Long> call, Response<Long> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Long realMessageId = response.body();
                    Log.d(TAG, "✅ Message created on server, real ID: " + realMessageId);

                    new Thread(() -> {
                        File compressed = null;
                        try {
                            compressed = ImageUtils.compressImage(ChatActivity.this, imageUri);
                            sendImageViaRest(compressed, realMessageId, chatId, tempId);

                        } catch (Exception e) {
                            Log.e(TAG, "Error preparing image", e);
                            runOnUiThread(() -> {
                                messageAdapter.updateMessageStatus(tempId, MessageItem.STATUS_FAILED);
                                Toast.makeText(ChatActivity.this, "Не удалось подготовить фото", Toast.LENGTH_SHORT).show();
                            });
                            if (compressed != null && compressed.exists()) {
                                compressed.delete();
                            }
                        }
                    }).start();

                } else {
                    Log.e(TAG, "❌ Failed to create message on server");
                    runOnUiThread(() -> {
                        messageAdapter.updateMessageStatus(tempId, MessageItem.STATUS_FAILED);
                        Toast.makeText(ChatActivity.this, "Ошибка создания сообщения", Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(Call<Long> call, Throwable t) {
                Log.e(TAG, "❌ Network error creating message", t);
                runOnUiThread(() -> {
                    messageAdapter.updateMessageStatus(tempId, MessageItem.STATUS_FAILED);
                    Toast.makeText(ChatActivity.this, "Нет соединения", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void createEmptyMessageOnServer(long chatId, String messageType, Callback<Long> callback) {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", chatId);
        request.put("content", "");
        request.put("messageType", messageType);

        apiService.sendMessage(chatId, request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object idObj = response.body().get("id");
                    if (idObj instanceof Number) {
                        Long realId = ((Number) idObj).longValue();
                        callback.onResponse(null, Response.success(realId));
                        return;
                    }
                }
                callback.onFailure(null, new Exception("Failed to get message ID"));
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                callback.onFailure(null, t);
            }
        });
    }

    private String encodeFileToBase64(File file) throws IOException {
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file)) {
            fis.read(bytes);
        }
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }

    private void sendImageViaRest(File imageFile, long messageId, long chatId, long tempId) {
        String fileName = imageFile.getName();
        long fileSize = imageFile.length();
        String fileType = FileHelper.getMimeType(fileName);

        RequestBody requestFile = RequestBody.create(
                imageFile,
                MediaType.parse(fileType != null ? fileType : "application/octet-stream")
        );

        MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                "file", fileName, requestFile
        );

        Log.d(TAG, "📤 Uploading attachment: messageId=" + messageId + ", fileName=" + fileName);

        apiService.uploadAttachment(
                filePart,
                messageId,
                fileName,
                fileSize,
                fileType,
                null
        ).enqueue(new Callback<AttachmentResponse>() {
            @Override
            public void onResponse(Call<AttachmentResponse> call, Response<AttachmentResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AttachmentResponse result = response.body();

                    Map<String, Object> attachment = new HashMap<>();
                    attachment.put("id", result.id);
                    attachment.put("fileUrl", result.fileUrl);
                    attachment.put("fileName", result.fileName);
                    attachment.put("fileSize", result.fileSize);
                    attachment.put("fileType", result.fileType);
                    attachment.put("thumbnailUrl", result.thumbnailUrl);
                    attachment.put("createdAt", result.createdAt);

                    List<Map<String, Object>> attachmentsList = new ArrayList<>();
                    attachmentsList.add(attachment);

                    runOnUiThread(() -> {
                        messageAdapter.updateImageMessage(
                                tempId,
                                result.id,
                                result.fileUrl,
                                MessageItem.STATUS_SENT
                        );
                        Log.d(TAG, "✅ Attachment uploaded: " + result.fileUrl);

                        String createdAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                .format(new Date());

                        broadcastMessageViaWebSocket(
                                messageId,
                                "",
                                createdAt,
                                getCurrentUserId(),
                                MessageItem.STATUS_SENT,
                                MessageType.IMAGE,
                                attachmentsList
                        );
                        notifyMessageSent();
                    });
                } else {
                    Log.e(TAG, "❌ Upload failed: " + response.code() + " - " + response.message());
                    runOnUiThread(() -> {
                        messageAdapter.updateMessageStatus(tempId, MessageItem.STATUS_FAILED);
                        Toast.makeText(ChatActivity.this,
                                "Ошибка загрузки: " + response.code(),
                                Toast.LENGTH_SHORT).show();
                    });
                }
            }

            @Override
            public void onFailure(Call<AttachmentResponse> call, Throwable t) {
                if (imageFile != null && imageFile.exists()) {
                    boolean deleted = imageFile.delete();
                    Log.d(TAG, "🗑️ Temp file deleted on failure: " + deleted);
                }
                Log.e(TAG, "❌ Upload failed: " + t.getMessage(), t);
                runOnUiThread(() -> {
                    messageAdapter.updateMessageStatus(tempId, MessageItem.STATUS_FAILED);
                    Toast.makeText(ChatActivity.this, "Нет соединения", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void broadcastImageMessage(long messageId, AttachmentResponse attachment, long chatId) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ Cannot broadcast: WebSocket not connected");
            return;
        }

        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", messageId);
        messagePayload.put("chatId", chatId);
        messagePayload.put("senderId", getCurrentUserId());
        messagePayload.put("text", "");
        messagePayload.put("messageType", "image");
        messagePayload.put("status", "sent");
        messagePayload.put("createdAt", java.time.Instant.now().toString());

        List<Map<String, Object>> attachments = new ArrayList<>();
        Map<String, Object> attachmentData = new HashMap<>();
        attachmentData.put("id", attachment.id);
        attachmentData.put("fileUrl", attachment.fileUrl);
        attachmentData.put("fileName", attachment.fileName);
        attachmentData.put("fileSize", attachment.fileSize);
        attachmentData.put("fileType", attachment.fileType);
        attachmentData.put("thumbnailUrl", attachment.thumbnailUrl);
        attachments.add(attachmentData);

        messagePayload.put("attachments", attachments);

        String destination = "/app/chat." + chatId + ".send";
        Log.d(TAG, "📤 Broadcasting image via WebSocket to " + destination);
        stompClient.send(destination, messagePayload);
    }

    private void broadcastImageMessageViaWebSocket(long messageId, AttachmentResponse attachment, long senderId) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ WebSocket not connected, skipping broadcast");
            return;
        }

        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", messageId);
        messagePayload.put("chatId", chatId);
        messagePayload.put("senderId", senderId);
        messagePayload.put("text", "");
        messagePayload.put("messageType", "image");
        messagePayload.put("status", "sent");
        messagePayload.put("createdAt", java.time.Instant.now().toString());

        List<Map<String, Object>> attachments = new ArrayList<>();
        Map<String, Object> att = new HashMap<>();
        att.put("id", attachment.id);
        att.put("fileUrl", attachment.fileUrl);
        att.put("fileName", attachment.fileName);
        att.put("fileSize", attachment.fileSize);
        att.put("fileType", attachment.fileType);
        attachments.add(att);
        messagePayload.put("attachments", attachments);

        Log.d(TAG, "📡 Broadcasting image message via WS: " + messageId);
        stompClient.send("/app/chat.message", messagePayload);
    }

    private void broadcastMessageViaWebSocket(long messageId, String text, String createdAt,
                                              long senderId, int status, MessageType messageType,
                                              List<Map<String, Object>> attachments) {
        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "⚠️ Cannot broadcast: WebSocket not connected");
            return;
        }

        Map<String, Object> messagePayload = new HashMap<>();
        messagePayload.put("id", messageId);
        messagePayload.put("text", text != null ? text : "");
        messagePayload.put("createdAt", createdAt);
        messagePayload.put("senderId", senderId);
        messagePayload.put("status", parseStatusToString(status));
        messagePayload.put("messageType", messageType != null ? messageType.name().toLowerCase() : "text");
        messagePayload.put("chatId", chatId);

        if (attachments != null && !attachments.isEmpty()) {
            messagePayload.put("attachments", attachments);
        }

        String destination = "/app/chat." + chatId + ".send";
        Log.d(TAG, "📤 Broadcasting message via WebSocket to " + destination);
        stompClient.send(destination, messagePayload);
    }

    private String parseStatusToString(int status) {
        switch (status) {
            case MessageItem.STATUS_READ: return "read";
            case MessageItem.STATUS_DELIVERED: return "delivered";
            case MessageItem.STATUS_SENT: return "sent";
            case MessageItem.STATUS_SENDING: return "sending";
            case MessageItem.STATUS_FAILED: return "failed";
            default: return "sent";
        }
    }

    private void sendViaRest(String text) {
        Map<String, Object> request = new HashMap<>();
        request.put("chatId", chatId);
        request.put("content", text);

        apiService.sendMessage(chatId, request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Log.d(TAG, "✅ REST message sent successfully");
                    notifyMessageSent();
                } else {
                    Toast.makeText(ChatActivity.this, "Ошибка отправки", Toast.LENGTH_SHORT).show();
                }
            }

            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Нет соединения", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void notifyMessageSent() {
        Intent result = new Intent();
        result.putExtra("chat_id", chatId);
        result.putExtra("updated", true);
        setResult(RESULT_OK, result);
    }

    private void showNoConnection(boolean show) {
        noConnectionState.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void showChatMenu() {
        String[] items = {"Очистить историю", "Выйти"};
        new AlertDialog.Builder(this)
                .setTitle(chatNameStr)
                .setItems(items, (dialog, which) -> {
                    if (which == 1) finish();
                    else Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void scrollToBottom(boolean smooth) {
        messagesRecyclerView.post(() -> {
            if (messageAdapter != null && layoutManager != null && messageAdapter.getItemCount() > 0) {
                int lastPosition = messageAdapter.getItemCount() - 1;
                if (smooth) {
                    messagesRecyclerView.smoothScrollToPosition(lastPosition);
                } else {
                    layoutManager.scrollToPosition(lastPosition);
                }
            }
        });
    }

    private void scrollToBottom() {
        scrollToBottom(false);
    }

    private void openMediaViewer(String url, MessageType type) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(this, "Ошибка: URL пуст", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to open media viewer", e);
            Toast.makeText(this, "Не удалось открыть изображение", Toast.LENGTH_SHORT).show();
        }
    }

    private enum State { LOADING, CONTENT, EMPTY, ERROR }

    private void showState(State state) {
        boolean isContent = (state == State.CONTENT);
        messagesRecyclerView.setVisibility(isContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
    }

    private void setupScrollListener() {
        messagesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy < 0 && !isLoadingMore && hasMoreMessages) {
                    int firstVisible = layoutManager.findFirstVisibleItemPosition();

                    if (firstVisible <= 3) {
                        Log.d(TAG, "📥 Triggered load more: firstVisible=" + firstVisible);
                        loadMessages(true);
                    }
                }
            }
        });
    }

    private void showTopLoadingIndicator(boolean show) {
        if (topProgressBar != null) {
            topProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void resetPagination() {
        isLoadingMore = false;
        hasMoreMessages = true;
        oldestMessageId = null;
    }

    private interface UploadCallback {
        void onSuccess(AttachmentResponse attachment);
        void onError(String error);
    }
}