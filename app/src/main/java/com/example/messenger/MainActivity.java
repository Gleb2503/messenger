package com.example.messenger;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.websocket.StompClient;
import com.example.messenger.util.Constants;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import androidx.lifecycle.ProcessLifecycleOwner;
import androidx.lifecycle.Lifecycle;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private static MainActivity instance;
    private long lastStatusSentTime = 0;

    private RecyclerView chatRecyclerView;
    private View emptyState, errorState, progressBar;
    private EditText searchInput;
    private ImageView clearButton;
    private ProgressBar searchProgressBar;
    private FloatingActionButton fabAddChat;

    StompClient stompClient;

    private ChatAdapter chatAdapter;
    private ApiService apiService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<ChatItem> allChats = new ArrayList<>();
    private long currentUserId;
    private String authToken;
    private boolean isChatActivityVisible = false;

    private static final boolean TEST_EMPTY_STATE = false;
    private static final boolean TEST_ERROR_STATE = false;
    private static final long STATUS_DEBOUNCE_MS = 2000;
    private boolean statusSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        instance = this;

        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
        authToken = RetrofitClient.getToken();

        initViews();
        setupClickListeners();
        setupSearch();
        loadChats();

        if (authToken != null && !authToken.isEmpty() && currentUserId > 0) {
            initWebSocketForStatus(authToken);
        }
        statusSent = false;
    }

    @Override
    protected void onResume() {
        super.onResume();
        isChatActivityVisible = false;

        if (stompClient != null && stompClient.isConnected() && currentUserId > 0) {
            Log.d(TAG, "📤 onResume: Broadcasting online=true");
            broadcastAppStatus(true);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!isChatActivityVisible && stompClient != null && stompClient.isConnected() && currentUserId > 0) {
            Log.d(TAG, "📤 onPause: Sending OFFLINE immediately");
            broadcastAppStatus(false);
            statusSent = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (instance == this) {
            instance = null;
        }


        if (stompClient != null && stompClient.isConnected() && currentUserId > 0) {
            Log.d(TAG, "📤 onDestroy: Broadcasting online=false (final)");

            Map<String, Object> status = new HashMap<>();
            status.put("userId", currentUserId);
            status.put("online", false);
            status.put("source", "main_destroy");
            stompClient.send("/app/user.status", status);
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
    }



    public static StompClient getSharedStompClient() {
        return instance != null ? instance.stompClient : null;
    }

    public static long getSharedCurrentUserId() {
        return instance != null ? instance.currentUserId : -1;
    }

    public StompClient getStompClient() {
        return stompClient;
    }

    public long getCurrentUserId() {
        return currentUserId;
    }

    private void initWebSocketForStatus(final String token) {
        if (token == null || token.isEmpty()) return;

        Log.d(TAG, "🔌 Initializing status WebSocket, userId=" + currentUserId);

        stompClient = new StompClient(token);
        stompClient.setListener(new StompClient.StompListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "✅ Status WebSocket connected");
                broadcastAppStatus(true);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "❌ Status WebSocket disconnected");
            }

            @Override public void onMessage(String destination, Object payload) {}

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ WS Error: " + error);
            }
        });

        Log.d(TAG, "🔌 Connecting to status WebSocket...");
        stompClient.connect();
    }

    private void broadcastAppStatus(boolean online) {
        if (stompClient == null || !stompClient.isConnected() || currentUserId <= 0) {
            Log.w(TAG, "Cannot broadcast status: connected=" + (stompClient != null) + ", userId=" + currentUserId);
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastStatusSentTime < STATUS_DEBOUNCE_MS) {
            Log.d(TAG, "⏱️ Status send debounced (online=" + online + ")");
            return;
        }
        lastStatusSentTime = now;

        Map<String, Object> status = new HashMap<>();
        status.put("userId", currentUserId);
        status.put("online", online);
        status.put("source", "app");

        Log.d(TAG, "📤 Broadcasting user.status: userId=" + currentUserId + ", online=" + online);
        stompClient.send("/app/user.status", status);
    }
    private boolean isAppInForeground() {
        try {
            return ProcessLifecycleOwner.get().getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        } catch (Exception e) {

            return false;
        }
    }

    private void initViews() {
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        emptyState = findViewById(R.id.emptyState);
        errorState = findViewById(R.id.errorState);
        progressBar = findViewById(R.id.progressBar);
        searchInput = findViewById(R.id.searchInput);
        clearButton = findViewById(R.id.clearButton);
        searchProgressBar = findViewById(R.id.searchProgressBar);
        fabAddChat = findViewById(R.id.fabAddChat);

        chatAdapter = new ChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);

        chatAdapter.setOnChatClickListener(chatItem -> {
            openChatActivity(chatItem.getId(), chatItem.getName(), chatItem.getPartnerUserId());
        });

        chatAdapter.setOnChatLongClickListener((chatId, chatName, isPinned) -> {
            showChatOptionsDialog(chatId, chatName, isPinned);
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.profileIcon).setOnClickListener(v -> logoutAndGoToLogin());
        findViewById(R.id.menuIcon).setOnClickListener(v -> showMenuDialog());
        findViewById(R.id.createChatButton).setOnClickListener(v -> showCreateChatDialog());
        findViewById(R.id.addContactButton).setOnClickListener(v ->
                Toast.makeText(this, "Добавить контакт", Toast.LENGTH_SHORT).show());
        findViewById(R.id.retryButton).setOnClickListener(v -> loadChats());
        fabAddChat.setOnClickListener(v -> showCreateChatDialog());
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                clearButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                if (query.isEmpty()) {
                    chatAdapter.submitList(allChats);
                } else {
                    filterChats(query);
                }
            }
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        clearButton.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.requestFocus();
            chatAdapter.submitList(allChats);
        });

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String query = searchInput.getText().toString().trim();
                if (!query.isEmpty()) {
                    performSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    private void filterChats(String query) {
        try {
            List<ChatItem> filtered = allChats.stream()
                    .filter(chat -> !chat.isHeader())
                    .filter(chat ->
                            chat.getName().toLowerCase().contains(query.toLowerCase()) ||
                                    chat.getLastMessage().toLowerCase().contains(query.toLowerCase())
                    )
                    .collect(Collectors.toList());
            chatAdapter.submitList(filtered);
        } catch (Exception e) {
            chatAdapter.submitList(new ArrayList<>());
        }
    }

    private void performSearch(String query) {
        searchProgressBar.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> {
            filterChats(query);
            searchProgressBar.setVisibility(View.GONE);
        }, 500);
    }

    private void loadChats() {
        showState(State.LOADING);

        if (TEST_EMPTY_STATE) {
            handler.postDelayed(() -> {
                allChats = new ArrayList<>();
                chatAdapter.submitList(allChats);
                showState(State.EMPTY);
            }, 1000);
            return;
        }

        if (TEST_ERROR_STATE) {
            handler.postDelayed(() -> showState(State.ERROR), 1000);
            return;
        }

        apiService.getChats().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> backendChats = response.body();
                    if (backendChats.isEmpty()) {
                        showState(State.EMPTY);
                    } else {
                        allChats = mapBackendChatsToUI(backendChats);
                        chatAdapter.submitList(allChats);
                        showState(State.CONTENT);
                    }
                } else {
                    if (response.code() == 401) {
                        logoutAndGoToLogin();
                    } else {
                        showState(State.ERROR);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                Log.e(TAG, "Network error loading chats", t);
                showState(State.ERROR);
            }
        });
    }

    private List<ChatItem> mapBackendChatsToUI(List<Map<String, Object>> backendChats) {
        List<ChatItem> uiList = new ArrayList<>();

        List<Map<String, Object>> pinnedChats = new ArrayList<>();
        List<Map<String, Object>> regularChats = new ArrayList<>();

        for (Map<String, Object> chat : backendChats) {
            Object pinnedObj = chat.get("pinned");
            boolean isPinned = false;

            if (pinnedObj instanceof Boolean) {
                isPinned = (Boolean) pinnedObj;
            } else if (pinnedObj instanceof String) {
                isPinned = Boolean.parseBoolean((String) pinnedObj);
            } else if (pinnedObj instanceof Number) {
                isPinned = ((Number) pinnedObj).intValue() != 0;
            }

            if (isPinned) {
                pinnedChats.add(chat);
            } else {
                regularChats.add(chat);
            }
        }

        if (!pinnedChats.isEmpty()) {
            uiList.add(new ChatItem(ChatItem.TYPE_HEADER_PINNED, "Закрепленные"));
            for (Map<String, Object> chat : pinnedChats) {
                uiList.add(mapChatToUI(chat));
            }
        }

        if (!regularChats.isEmpty()) {
            uiList.add(new ChatItem(ChatItem.TYPE_HEADER_ALL, "Все чаты"));
            for (Map<String, Object> chat : regularChats) {
                uiList.add(mapChatToUI(chat));
            }
        }

        return uiList;
    }

    private ChatItem mapChatToUI(Map<String, Object> chat) {
        Object pinnedObj = chat.get("pinned");
        boolean isPinned = false;
        if (pinnedObj instanceof Boolean) {
            isPinned = (Boolean) pinnedObj;
        }

        long partnerUserId = -1;
        String type = (String) chat.get("type");
        if ("private_chat".equals(type)) {
            Map<String, Object> createdBy = (Map<String, Object>) chat.get("createdBy");
            if (createdBy != null && createdBy.get("id") != null) {
                long createdById = ((Number) createdBy.get("id")).longValue();
                if (createdById != currentUserId) {
                    partnerUserId = createdById;
                }
            }
        }

        return new ChatItem(
                chat.get("id") != null ? ((Number) chat.get("id")).longValue() : 0L,
                chat.get("name") != null ? (String) chat.get("name") : "Без названия",
                chat.get("lastMessage") != null ? (String) chat.get("lastMessage") : "Нажмите, чтобы открыть",
                formatTime((String) chat.get("updatedAt")),
                0,
                isPinned,
                partnerUserId
        );
    }

    private String formatTime(String isoTime) {
        if (isoTime == null || isoTime.isEmpty()) return "00:00";
        try {
            if (isoTime.contains("T")) {
                String[] parts = isoTime.split("T");
                if (parts.length > 1 && parts[1].length() >= 5) {
                    return parts[1].substring(0, 5);
                }
            }
            return isoTime.length() >= 5 ? isoTime.substring(0, 5) : "00:00";
        } catch (Exception e) {
            return "00:00";
        }
    }

    private void showCreateChatDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Новый личный чат");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_chat, null);

        TextInputEditText contactInput = dialogView.findViewById(R.id.contactInput);
        Spinner typeSpinner = dialogView.findViewById(R.id.chatTypeSpinner);

        String[] chatTypes = {"Личный чат"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, chatTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);
        typeSpinner.setSelection(0);

        builder.setView(dialogView);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String contact = contactInput.getText().toString().trim();
            if (contact.isEmpty()) {
                Toast.makeText(this, "Введите имя контакта", Toast.LENGTH_SHORT).show();
                return;
            }
            createPrivateChat(contact);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void createPrivateChat(String phoneNumber) {
        Map<String, String> request = new HashMap<>();
        request.put("participantPhone", phoneNumber);
        request.put("type", "private_chat");

        apiService.createChat(request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> newChat = response.body();

                    String chatName = (String) newChat.get("name");
                    long chatId = ((Number) newChat.get("id")).longValue();

                    long partnerUserId = -1;
                    Map<String, Object> createdBy = (Map<String, Object>) newChat.get("createdBy");
                    if (createdBy != null && createdBy.get("id") != null) {
                        long createdById = ((Number) createdBy.get("id")).longValue();
                        if (createdById != currentUserId) {
                            partnerUserId = createdById;
                        }
                    }

                    Toast.makeText(MainActivity.this,
                            "Чат с " + chatName + " создан", Toast.LENGTH_SHORT).show();

                    loadChats();
                    openChatActivity(chatId, chatName, partnerUserId);
                } else {
                    if (response.code() == 404) {
                        Toast.makeText(MainActivity.this,
                                "Пользователь с таким номером не найден", Toast.LENGTH_LONG).show();
                    } else if (response.code() == 400) {
                        Toast.makeText(MainActivity.this,
                                "Некорректные данные", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this,
                                "Ошибка: " + response.code(), Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Ошибка сети", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void openChatActivity(long chatId, String chatName, long partnerUserId) {
        isChatActivityVisible = true;
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("chat_name", chatName);
        intent.putExtra("partner_user_id", partnerUserId);
        startActivity(intent);
    }

    private void showChatOptionsDialog(long chatId, String chatName, boolean isPinned) {
        String[] options = isPinned ? new String[]{"Открепить чат", "Удалить чат"} : new String[]{"Закрепить чат", "Удалить чат"};

        new AlertDialog.Builder(this)
                .setTitle(chatName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        togglePinChat(chatId, !isPinned);
                    } else if (which == 1) {
                        deleteChat(chatId);
                    }
                })
                .show();
    }

    private void togglePinChat(long chatId, boolean pinned) {
        Map<String, Boolean> request = new HashMap<>();
        request.put("pinned", pinned);

        apiService.togglePin(chatId, request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this, pinned ? "Чат закреплён" : "Чат откреплён", Toast.LENGTH_SHORT).show();
                    loadChats();
                } else {
                    Toast.makeText(MainActivity.this, "Ошибка", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteChat(long chatId) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить чат")
                .setMessage("Вы уверены?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    apiService.deleteChat(chatId).enqueue(new Callback<Void>() {
                        @Override
                        public void onResponse(Call<Void> call, Response<Void> response) {
                            if (response.isSuccessful()) {
                                Toast.makeText(MainActivity.this, "Чат удалён", Toast.LENGTH_SHORT).show();
                                loadChats();
                            } else {
                                Toast.makeText(MainActivity.this, "Ошибка при удалении", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<Void> call, Throwable t) {
                            Toast.makeText(MainActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showMenuDialog() {
        String[] items = {"Настройки", "Профиль", "Выйти"};
        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 3: logoutAndGoToLogin(); break;
                        default: Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private void logoutAndGoToLogin() {
        RetrofitClient.clearTokens();
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE).edit().clear().apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private enum State { LOADING, CONTENT, EMPTY, ERROR }

    private void showState(State state) {
        boolean isContent = (state == State.CONTENT);
        chatRecyclerView.setVisibility(isContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        errorState.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        fabAddChat.setVisibility(isContent ? View.VISIBLE : View.GONE);
    }
}