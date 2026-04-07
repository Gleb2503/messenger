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
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.util.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView chatRecyclerView;
    private View emptyState, errorState, progressBar;
    private EditText searchInput;
    private ImageView clearButton;
    private ProgressBar searchProgressBar;
    private FloatingActionButton fabAddChat;

    private ChatAdapter chatAdapter;
    private ApiService apiService;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<ChatItem> allChats = new ArrayList<>();

    private static final boolean TEST_EMPTY_STATE = false;
    private static final boolean TEST_ERROR_STATE = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();

        initViews();
        setupClickListeners();
        setupSearch();
        loadChats();
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

        chatAdapter.setOnChatClickListener(chatId -> {
            Toast.makeText(this, "Чат #" + chatId, Toast.LENGTH_SHORT).show();
        });
    }

    private void setupClickListeners() {
        findViewById(R.id.profileIcon).setOnClickListener(v -> logoutAndGoToLogin());
        findViewById(R.id.menuIcon).setOnClickListener(v -> showMenuDialog());
        findViewById(R.id.createChatButton).setOnClickListener(v ->
                Toast.makeText(this, "Создать чат", Toast.LENGTH_SHORT).show());
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

            String chatName = (String) chat.getOrDefault("name", "Unknown");
            Log.d("MainActivity", "Chat: " + chatName + ", pinned: " + isPinned);

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

        return new ChatItem(
                chat.get("id") != null ? ((Number) chat.get("id")).longValue() : 0L,
                chat.get("name") != null ? (String) chat.get("name") : "Без названия",
                "Нажмите, чтобы открыть",
                formatTime((String) chat.get("updatedAt")),
                0,
                isPinned
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
        builder.setTitle("Новый чат");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_create_chat, null);

        EditText inputName = dialogView.findViewById(R.id.chatNameInput);
        Spinner typeSpinner = dialogView.findViewById(R.id.chatTypeSpinner);

        String[] chatTypes = {"Личный чат", "Групповой чат", "Канал"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, chatTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeSpinner.setAdapter(adapter);

        builder.setView(dialogView);

        builder.setPositiveButton("Создать", (dialog, which) -> {
            String chatName = inputName.getText().toString().trim();
            if (chatName.isEmpty()) {
                Toast.makeText(this, "Введите название чата", Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedType = chatTypes[typeSpinner.getSelectedItemPosition()];
            String apiType = "";
            switch (selectedType) {
                case "Личный чат":
                    apiType = "private_chat";
                    break;
                case "Групповой чат":
                    apiType = "group";
                    break;
                case "Канал":
                    apiType = "channel";
                    break;
            }

            createNewChat(chatName, apiType);
        });
        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void createNewChat(String chatName, String chatType) {
        Map<String, String> request = new HashMap<>();
        request.put("name", chatName);
        request.put("type", chatType);

        apiService.createChat(request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(MainActivity.this, "Чат создан!", Toast.LENGTH_SHORT).show();
                    loadChats();
                } else {
                    if (response.code() == 401) {
                        logoutAndGoToLogin();
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



    private void showMenuDialog() {
        String[] items = {"Настройки", "Профиль", "О приложении", "Выйти"};

        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            Toast.makeText(this, "Настройки", Toast.LENGTH_SHORT).show();
                            break;
                        case 1:
                            Toast.makeText(this, "Профиль", Toast.LENGTH_SHORT).show();
                            break;
                        case 2:
                            Toast.makeText(this, "О приложении v1.0", Toast.LENGTH_SHORT).show();
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

    private void showState(State state) {
        boolean isContent = (state == State.CONTENT);
        chatRecyclerView.setVisibility(isContent ? View.VISIBLE : View.GONE);
        emptyState.setVisibility(state == State.EMPTY ? View.VISIBLE : View.GONE);
        errorState.setVisibility(state == State.ERROR ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(state == State.LOADING ? View.VISIBLE : View.GONE);
        fabAddChat.setVisibility(isContent ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }
}