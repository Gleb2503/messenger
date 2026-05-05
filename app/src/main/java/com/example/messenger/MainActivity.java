package com.example.messenger;

import android.content.Intent;
import android.content.SharedPreferences;
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

import com.bumptech.glide.Glide;
import com.example.messenger.chat.ChatActivity;
import com.example.messenger.chat.ChatAdapter;
import com.example.messenger.chat.ChatItem;
import com.example.messenger.contacts.ContactsActivity;
import com.example.messenger.login.LoginActivity;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.profile.ProfileActivity;
import com.example.messenger.util.Constants;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_CHAT = 100;
    private static final long REFRESH_DELAY_MS = 300;
    private static MainActivity instance;

    private RecyclerView chatRecyclerView;
    private View emptyState, errorState, progressBar;
    private EditText searchInput;
    private ImageView clearButton, profileIcon;
    private ProgressBar searchProgressBar;
    private FloatingActionButton fabAddChat;

    private ChatAdapter chatAdapter;
    private ApiService apiService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<ChatItem> allChats = new ArrayList<>();
    private long currentUserId;
    private String authToken;

    private boolean shouldRefreshChats = false;
    private long lastLoadTime = 0;
    private static final long MIN_REFRESH_INTERVAL_MS = 2000;

    private static final boolean TEST_EMPTY_STATE = false;
    private static final boolean TEST_ERROR_STATE = false;

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
        loadProfileAvatar();
        setupClickListeners();
        setupSearch();
        loadChats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileAvatar();


        if (shouldRefreshChats) {
            shouldRefreshChats = false;
            long now = System.currentTimeMillis();
            if (now - lastLoadTime > MIN_REFRESH_INTERVAL_MS) {
                handler.postDelayed(this::loadChats, REFRESH_DELAY_MS);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        shouldRefreshChats = true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
        handler.removeCallbacksAndMessages(null);
    }

    public static long getSharedCurrentUserId() {
        return instance != null ? instance.currentUserId : -1;
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
        profileIcon = findViewById(R.id.profileIcon);

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

    private void loadProfileAvatar() {
        if (profileIcon == null) return;

        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
        String avatarUrl = prefs.getString(Constants.KEY_AVATAR, "");

        if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
            loadAvatarIntoView(avatarUrl);
        } else {
            profileIcon.setImageResource(R.drawable.bg_avatar_placeholder);
            fetchAvatarFromServer();
        }
    }

    private void fetchAvatarFromServer() {
        if (authToken == null || authToken.isEmpty() || currentUserId <= 0) return;

        apiService.getUserProfile(currentUserId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Object avatarObj = response.body().get("avatarUrl");
                    if (avatarObj instanceof String) {
                        String newAvatarUrl = (String) avatarObj;
                        if (newAvatarUrl != null && !newAvatarUrl.isEmpty() && newAvatarUrl.startsWith("http")) {
                            SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                            prefs.edit().putString(Constants.KEY_AVATAR, newAvatarUrl).apply();
                            if (!isFinishing() && !isDestroyed()) {
                                runOnUiThread(() -> loadAvatarIntoView(newAvatarUrl));
                            }
                        }
                    }
                }
            }
            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Failed to fetch avatar from server", t);
            }
        });
    }

    private void loadAvatarIntoView(String url) {
        if (profileIcon == null) return;
        if (url != null && !url.isEmpty() && url.startsWith("http")) {
            Glide.with(this)
                    .load(url.trim())
                    .placeholder(R.drawable.bg_avatar_placeholder)
                    .error(R.drawable.bg_avatar_placeholder)
                    .circleCrop()
                    .into(profileIcon);
        } else {
            profileIcon.setImageResource(R.drawable.bg_avatar_placeholder);
        }
    }

    private void setupClickListeners() {
        profileIcon.setOnClickListener(v -> {
            v.setAlpha(0.7f);
            v.postDelayed(() -> v.setAlpha(1f), 150);
            Intent intent = new Intent(MainActivity.this, ProfileActivity.class);
            startActivity(intent);
            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
        });

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
                            chat.getName().toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault())) ||
                                    chat.getLastMessage().toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()))
                    )
                    .collect(Collectors.toList());
            chatAdapter.submitList(filtered);
        } catch (Exception e) {
            Log.e(TAG, "Error filtering chats", e);
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

        allChats.clear();
        chatAdapter.submitList(new ArrayList<>());

        if (TEST_EMPTY_STATE) {
            handler.postDelayed(() -> {
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
                        fetchPartnerAvatars(allChats);
                        lastLoadTime = System.currentTimeMillis();
                    }
                } else {
                    if (response.code() == 401) {
                        logoutAndGoToLogin();
                    } else {
                        Log.e(TAG, "Failed to load chats: " + response.code());
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

    private void fetchPartnerAvatars(List<ChatItem> chats) {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        for (ChatItem chat : chats) {
            if (chat.isHeader()) continue;

            if (chat.getAvatarUrl() != null && !chat.getAvatarUrl().isEmpty() && chat.getAvatarUrl().startsWith("http")) {
                continue;
            }

            long partnerUserId = chat.getPartnerUserId();
            if (partnerUserId <= 0) continue;

            String cachedAvatar = prefs.getString("avatar_user_" + partnerUserId, "");
            if (!cachedAvatar.isEmpty() && cachedAvatar.startsWith("http")) {
                updateChatAvatar(chat.getId(), cachedAvatar);
                continue;
            }

            apiService.getUserProfile(partnerUserId).enqueue(new Callback<Map<String, Object>>() {
                @Override
                public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        Object avatarObj = response.body().get("avatarUrl");
                        if (avatarObj instanceof String) {
                            String avatarUrl = (String) avatarObj;
                            if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
                                prefs.edit().putString("avatar_user_" + partnerUserId, avatarUrl).apply();
                                updateChatAvatar(chat.getId(), avatarUrl);
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
    }

    private void updateChatAvatar(long chatId, String newAvatarUrl) {
        runOnUiThread(() -> {
            for (int i = 0; i < allChats.size(); i++) {
                ChatItem chat = allChats.get(i);
                if (chat.getId() == chatId && !chat.isHeader()) {
                    ChatItem updated = new ChatItem(
                            chat.getId(),
                            chat.getName(),
                            chat.getLastMessage(),
                            chat.getTime(),
                            chat.getUnreadCount(),
                            chat.isPinned(),
                            chat.getPartnerUserId(),
                            newAvatarUrl
                    );
                    allChats.set(i, updated);
                    chatAdapter.notifyItemChanged(i);
                    break;
                }
            }
        });
    }

    private LocalDateTime parseDateTimeWithFallback(Map<String, Object> chat) {
        String[] fields = {"lastMessageTime", "updatedAt", "createdAt"};

        for (String field : fields) {
            Object value = chat.get(field);
            if (value instanceof String && !((String) value).isEmpty()) {
                try {
                    return LocalDateTime.parse((String) value, DateTimeFormatter.ISO_DATE_TIME);
                } catch (DateTimeParseException e1) {
                    try {
                        return LocalDateTime.parse((String) value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                    } catch (DateTimeParseException e2) {
                        try {
                            String cleaned = ((String) value).replace("Z", "").split("\\.")[0];
                            return LocalDateTime.parse(cleaned, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                        } catch (Exception e3) {
                            continue;
                        }
                    }
                }
            }
        }
        return null;
    }

    private int compareByLastMessageTime(Map<String, Object> a, Map<String, Object> b) {
        LocalDateTime timeA = parseDateTimeWithFallback(a);
        LocalDateTime timeB = parseDateTimeWithFallback(b);

        if (timeA == null && timeB == null) return 0;
        if (timeA == null) return 1;
        if (timeB == null) return -1;

        return timeB.compareTo(timeA);
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

        pinnedChats.sort(this::compareByLastMessageTime);
        regularChats.sort(this::compareByLastMessageTime);

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

    private String extractAvatarUrl(Map<String, Object> chat, long partnerUserId) {
        Object avatarObj = chat.get("avatarUrl");
        if (avatarObj instanceof String) {
            String url = (String) avatarObj;
            if (url != null && !url.isEmpty() && url.startsWith("http")) {
                return url;
            }
        }

        Object createdByObj = chat.get("createdBy");
        if (createdByObj instanceof Map) {
            Map<String, Object> createdBy = (Map<String, Object>) createdByObj;
            Object createdByAvatar = createdBy.get("avatarUrl");
            if (createdByAvatar instanceof String) {
                String url = (String) createdByAvatar;
                if (url != null && !url.isEmpty() && url.startsWith("http")) {
                    return url;
                }
            }
        }

        if (partnerUserId > 0 && chat.containsKey("participants") && chat.get("participants") instanceof List) {
            List<Map<String, Object>> participants = (List<Map<String, Object>>) chat.get("participants");
            for (Map<String, Object> participant : participants) {
                Object userIdObj = participant.get("userId");
                if (userIdObj instanceof Number && ((Number) userIdObj).longValue() == partnerUserId) {
                    Object participantAvatar = participant.get("avatarUrl");
                    if (participantAvatar instanceof String) {
                        String url = (String) participantAvatar;
                        if (url != null && !url.isEmpty() && url.startsWith("http")) {
                            return url;
                        }
                    }
                    break;
                }
            }
        }

        return "";
    }

    private ChatItem mapChatToUI(Map<String, Object> chat) {
        Object pinnedObj = chat.get("pinned");
        boolean isPinned = false;
        if (pinnedObj instanceof Boolean) {
            isPinned = (Boolean) pinnedObj;
        } else if (pinnedObj instanceof String) {
            isPinned = Boolean.parseBoolean((String) pinnedObj);
        } else if (pinnedObj instanceof Number) {
            isPinned = ((Number) pinnedObj).intValue() != 0;
        }

        long partnerUserId = findPartnerUserId(chat, currentUserId);

        String avatarUrl = "";
        if (chat.containsKey("partner") && chat.get("partner") instanceof Map) {
            Map<String, Object> partnerMap = (Map<String, Object>) chat.get("partner");
            Object avatarObj = partnerMap.get("avatarUrl");
            if (avatarObj instanceof String) {
                avatarUrl = (String) avatarObj;
            }
        }

        if (avatarUrl == null || avatarUrl.isEmpty()) {
            avatarUrl = extractAvatarUrl(chat, partnerUserId);
        }

        long chatId = chat.get("id") != null ? ((Number) chat.get("id")).longValue() : 0L;
        String name = chat.get("name") != null ? (String) chat.get("name") : "Без названия";

        String lastMessage = "Нажмите, чтобы открыть";
        if (chat.containsKey("lastMessage")) {
            Object lastMsgObj = chat.get("lastMessage");
            if (lastMsgObj instanceof String) {
                String msg = (String) lastMsgObj;
                if (msg != null && !msg.isEmpty()) {
                    lastMessage = msg;
                }
            }
        }

        String rawTime = (String) chat.get("lastMessageTime");
        if (rawTime == null || rawTime.isEmpty()) {
            rawTime = (String) chat.get("updatedAt");
        }
        if (rawTime == null || rawTime.isEmpty()) {
            rawTime = (String) chat.get("createdAt");
        }
        String time = formatTime(rawTime);

        Log.d("ChatMapper", "Chat id=" + chatId + ", name=" + name + ", lastMessage='" + lastMessage + "'");

        return new ChatItem(chatId, name, lastMessage, time, 0, isPinned, partnerUserId, avatarUrl);
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
                    Toast.makeText(MainActivity.this, "Чат с " + chatName + " создан", Toast.LENGTH_SHORT).show();
                    loadChats();
                    autoAddContact(currentUserId, partnerUserId, chatName);
                    openChatActivity(chatId, chatName, partnerUserId);
                } else {
                    if (response.code() == 404) {
                        Toast.makeText(MainActivity.this, "Пользователь с таким номером не найден", Toast.LENGTH_LONG).show();
                    } else if (response.code() == 400) {
                        Toast.makeText(MainActivity.this, "Некорректные данные", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Ошибка: " + response.code(), Toast.LENGTH_LONG).show();
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
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra("chat_id", chatId);
        intent.putExtra("chat_name", chatName);
        intent.putExtra("partner_user_id", partnerUserId);
        startActivityForResult(intent, REQUEST_CHAT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CHAT && resultCode == RESULT_OK) {
            loadChats();
        }

        if (requestCode == Constants.REQUEST_EDIT_PROFILE && resultCode == RESULT_OK) {
            loadProfileAvatar();
        }
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
        String[] items = {"Контакты", "Настройки", "Профиль", "Выйти"};
        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            Intent contactsIntent = new Intent(MainActivity.this, ContactsActivity.class);
                            startActivity(contactsIntent);
                            overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                            break;
                        case 1:
                            Toast.makeText(this, "Настройки", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            Intent profileIntent = new Intent(MainActivity.this, ProfileActivity.class);
                            startActivity(profileIntent);
                            break;
                        case 3:
                            logoutAndGoToLogin();
                            break;
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
    private void autoAddContact(long userId, long contactUserId, String nickname) {
        if (userId <= 0 || contactUserId <= 0) return;
        apiService.getUserContacts(userId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    boolean exists = false;
                    for (Map<String, Object> c : response.body()) {
                        Map<String, Object> cu = (Map<String, Object>) c.get("contactUser");
                        if (cu != null && cu.get("id") instanceof Number && ((Number) cu.get("id")).longValue() == contactUserId) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        Map<String, Object> addReq = new HashMap<>();
                        addReq.put("userId", userId);
                        addReq.put("contactUserId", contactUserId);
                        addReq.put("nickname", nickname);
                        apiService.addContact(addReq).enqueue(new Callback<Map<String, Object>>() {
                            @Override public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {}
                            @Override public void onFailure(Call<Map<String, Object>> call, Throwable t) {}
                        });
                    }
                }
            }
            @Override public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {}
        });
    }
    private long findPartnerUserId(Map<String, Object> chat, long currentUserId) {
        if (chat.containsKey("partner") && chat.get("partner") instanceof Map) {
            Map<String, Object> partnerMap = (Map<String, Object>) chat.get("partner");
            Object partnerIdObj = partnerMap.get("id");
            if (partnerIdObj instanceof Number) {
                return ((Number) partnerIdObj).longValue();
            }
        }

        if (chat.containsKey("participants") && chat.get("participants") instanceof List) {
            List<Map<String, Object>> participants = (List<Map<String, Object>>) chat.get("participants");
            for (Map<String, Object> participant : participants) {
                Object userIdObj = participant.get("userId");
                if (userIdObj instanceof Number) {
                    long userId = ((Number) userIdObj).longValue();
                    if (userId != currentUserId) {
                        return userId;
                    }
                }
            }
        }

        Object createdByObj = chat.get("createdBy");
        if (createdByObj instanceof Map) {
            Map<String, Object> createdBy = (Map<String, Object>) createdByObj;
            Object idObj = createdBy.get("id");
            if (idObj instanceof Number) {
                long createdById = ((Number) idObj).longValue();
                if (createdById != currentUserId) {
                    return createdById;
                }
            }
        }

        Object chatName = chat.get("name");
        if (chatName instanceof String && !((String) chatName).isEmpty()) {
            Log.w(TAG, "Could not find partner userId for chat: " + chatName + ". API may not include participants array.");
        }

        return -1;
    }

    private void showState(State state) {
        boolean isContent = (state == State.CONTENT);
        chatRecyclerView.setVisibility(isContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        errorState.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        fabAddChat.setVisibility(isContent ? View.VISIBLE : View.GONE);
    }
}