package com.example.messenger.contacts;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.messenger.MessengerApplication;
import com.example.messenger.R;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.profile.ProfileActivity;
import com.example.messenger.status.AppStatusManager;
import com.example.messenger.status.UserStatusManager;
import com.example.messenger.data.websocket.StompClient;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ContactsActivity extends AppCompatActivity {
    private ApiService apiService;
    private ContactsAdapter contactsAdapter;
    private StompClient stompClient;
    private UserStatusManager.OnWebSocketReadyListener readyListener;
    private final Map<Long, String> statusSubscriptions = new HashMap<>();
    private long currentUserId;
    private RecyclerView recyclerView;
    private EditText searchInput;
    private ImageView clearSearchButton, backButton, addButton;
    private View emptyState, progressBar, sectionHeader;
    private List<ContactItem> allContacts = new ArrayList<>();
    private List<ContactItem> filteredContacts = new ArrayList<>();
    private Map<Long, ContactItem> contactsMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
        initViews();
        setupListeners();
        initWebSocket();
        loadContactsAndChats();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCachedStatuses();
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
        unsubscribeAllStatuses();
        stompClient = null;
    }

    private void initViews() {
        recyclerView = findViewById(R.id.contactsRecyclerView);
        searchInput = findViewById(R.id.searchInput);
        clearSearchButton = findViewById(R.id.clearSearchButton);
        backButton = findViewById(R.id.backButton);
        addButton = findViewById(R.id.addButton);
        emptyState = findViewById(R.id.emptyState);
        progressBar = findViewById(R.id.progressBar);
        sectionHeader = findViewById(R.id.sectionHeader);
        contactsAdapter = new ContactsAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(contactsAdapter);
        contactsAdapter.setOnContactClickListener(contact -> openUserProfile(contact.getUserId()));
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> finish());
        addButton.setOnClickListener(v -> showAddContactDialog());
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String query = s.toString().trim();
                clearSearchButton.setVisibility(query.isEmpty() ? View.GONE : View.VISIBLE);
                filterContacts(query);
            }
        });
        clearSearchButton.setOnClickListener(v -> {
            searchInput.setText("");
            searchInput.clearFocus();
        });
    }

    private void initWebSocket() {
        UserStatusManager statusManager = ((MessengerApplication) getApplication()).getStatusManager();
        StompClient sharedClient = statusManager.getStompClient();
        String authToken = RetrofitClient.getToken();
        if (sharedClient != null && sharedClient.isConnected()) {
            this.stompClient = sharedClient;
            subscribeToAllStatuses();
        } else {
            readyListener = () -> {
                if (!isFinishing() && !isDestroyed()) {
                    UserStatusManager sm = ((MessengerApplication) getApplication()).getStatusManager();
                    this.stompClient = sm.getStompClient();
                    subscribeToAllStatuses();
                }
            };
            statusManager.addOnReadyListener(readyListener);
            if (authToken != null && !authToken.isEmpty()) {
                statusManager.initWebSocket(authToken);
            }
        }
    }

    private void subscribeToAllStatuses() {
        if (stompClient == null || !stompClient.isConnected()) return;
        for (ContactItem contact : allContacts) {
            subscribeToPartnerStatus(contact.getUserId());
        }
        subscribeToPersonalQueue();
    }

    private void subscribeToPartnerStatus(long userId) {
        if (userId <= 0 || userId == currentUserId) return;
        if (statusSubscriptions.containsKey(userId)) return;
        String destination = "/topic/user/" + userId + "/status";
        String subscriptionId = stompClient.subscribe(destination, payload -> {
            if (isFinishing() || isDestroyed()) return;
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> status = (Map<String, Object>) payload;
                Boolean online = (Boolean) status.get("online");
                Object userIdObj = status.get("userId");
                Long targetUserId = userIdObj instanceof Number ? ((Number) userIdObj).longValue() : null;
                if (online != null && targetUserId != null && targetUserId == userId) {
                    AppStatusManager.getInstance().updateStatus(userId, online, "public_topic");
                    updateContactStatusUI(userId, online);
                }
            }
        });
        if (subscriptionId != null) {
            statusSubscriptions.put(userId, subscriptionId);
            Map<String, Object> request = new HashMap<>();
            request.put("userId", userId);
            request.put("requesterId", currentUserId);
            stompClient.send("/app/user.status.request", request);
        }
    }

    private void subscribeToPersonalQueue() {
        if (stompClient == null || !stompClient.isConnected()) return;
        String personalQueue = "/user/" + currentUserId + "/queue/user.status";
        stompClient.subscribe(personalQueue, payload -> {
            if (isFinishing() || isDestroyed()) return;
            if (payload instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = (Map<String, Object>) payload;
                Object userIdObj = response.get("userId");
                Object onlineObj = response.get("online");
                if (userIdObj instanceof Number && onlineObj instanceof Boolean) {
                    Long targetUserId = ((Number) userIdObj).longValue();
                    Boolean isOnline = (Boolean) onlineObj;
                    if (contactsMap.containsKey(targetUserId)) {
                        AppStatusManager.getInstance().updateStatus(targetUserId, isOnline, "personal_queue");
                        updateContactStatusUI(targetUserId, isOnline);
                    }
                }
            }
        });
    }

    private void unsubscribeAllStatuses() {
        if (stompClient == null || !stompClient.isConnected()) return;
        for (Map.Entry<Long, String> entry : statusSubscriptions.entrySet()) {
            String destination = "/topic/user/" + entry.getKey() + "/status";
            stompClient.unsubscribe(destination, entry.getValue());
        }
        statusSubscriptions.clear();
    }

    private void updateContactStatusUI(long userId, boolean isOnline) {
        runOnUiThread(() -> {
            ContactItem contact = contactsMap.get(userId);
            if (contact != null) {
                int index = filteredContacts.indexOf(contact);
                if (index >= 0) {
                    ContactItem updated = new ContactItem(
                            contact.getId(),
                            contact.getUserId(),
                            contact.getDisplayName(),
                            contact.getUsername(),
                            contact.getAvatarUrl(),
                            isOnline,
                            contact.isExplicitContact()
                    );
                    filteredContacts.set(index, updated);
                    allContacts.set(allContacts.indexOf(contact), updated);
                    contactsMap.put(userId, updated);
                    contactsAdapter.notifyItemChanged(index);
                }
            }
        });
    }

    private void refreshCachedStatuses() {
        for (ContactItem contact : allContacts) {
            Boolean cached = AppStatusManager.getInstance().getStatus(contact.getUserId());
            if (cached != null && cached != contact.isOnline()) {
                updateContactStatusUI(contact.getUserId(), cached);
            }
        }
    }

    private void loadContactsAndChats() {
        showLoading(true);
        allContacts.clear();
        contactsMap.clear();

        apiService.getUserContacts(currentUserId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> backendContacts = response.body();
                    for (Map<String, Object> contactMap : backendContacts) {
                        try {
                            Long id = ((Number) contactMap.get("id")).longValue();
                            Map<String, Object> contactUser = (Map<String, Object>) contactMap.get("contactUser");
                            if (contactUser == null) continue;
                            Long userId = ((Number) contactUser.get("id")).longValue();
                            String username = (String) contactUser.get("username");
                            String displayName = (String) contactUser.get("displayName");
                            String avatarUrl = (String) contactUser.get("avatarUrl");
                            if (displayName == null || displayName.isEmpty()) displayName = username;
                            Boolean cachedStatus = AppStatusManager.getInstance().getStatus(userId);
                            boolean isOnline = cachedStatus != null ? cachedStatus : false;
                            ContactItem item = new ContactItem(id, userId, displayName, username, avatarUrl, isOnline, true);
                            allContacts.add(item);
                            contactsMap.put(userId, item);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    loadChatsForPartners();
                } else {
                    loadChatsForPartners();
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                loadChatsForPartners();
            }
        });
    }

    private void loadChatsForPartners() {
        apiService.getChats().enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                showLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> backendChats = response.body();
                    for (Map<String, Object> chat : backendChats) {
                        try {
                            long partnerUserId = findPartnerUserId(chat, currentUserId);
                            if (partnerUserId <= 0) continue;
                            if (contactsMap.containsKey(partnerUserId)) continue;
                            String chatName = (String) chat.get("name");
                            Map<String, Object> partnerMap = null;
                            if (chat.containsKey("partner") && chat.get("partner") instanceof Map) {
                                partnerMap = (Map<String, Object>) chat.get("partner");
                            }
                            String username = "";
                            String displayName = chatName != null ? chatName : "Пользователь";
                            String avatarUrl = "";
                            if (partnerMap != null) {
                                username = (String) partnerMap.get("username");
                                String nameFromPartner = (String) partnerMap.get("displayName");
                                if (nameFromPartner != null && !nameFromPartner.isEmpty()) {
                                    displayName = nameFromPartner;
                                }
                                avatarUrl = (String) partnerMap.get("avatarUrl");
                            }
                            if (username == null) username = "";
                            if (avatarUrl == null) avatarUrl = "";
                            Boolean cachedStatus = AppStatusManager.getInstance().getStatus(partnerUserId);
                            boolean isOnline = cachedStatus != null ? cachedStatus : false;
                            ContactItem item = new ContactItem(0L, partnerUserId, displayName, username, avatarUrl, isOnline, false);
                            allContacts.add(item);
                            contactsMap.put(partnerUserId, item);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    allContacts.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
                    filteredContacts.clear();
                    filteredContacts.addAll(allContacts);
                    updateUI();
                    if (stompClient != null && stompClient.isConnected()) {
                        subscribeToAllStatuses();
                    }
                } else {
                    updateUI();
                }
            }
            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                showLoading(false);
                allContacts.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
                filteredContacts.clear();
                filteredContacts.addAll(allContacts);
                updateUI();
            }
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
        return -1;
    }

    private void filterContacts(String query) {
        if (query.isEmpty()) {
            filteredContacts.clear();
            filteredContacts.addAll(allContacts);
        } else {
            String lowerQuery = query.toLowerCase();
            filteredContacts.clear();
            for (ContactItem contact : allContacts) {
                if (contact.getDisplayName().toLowerCase().contains(lowerQuery) ||
                        contact.getUsername().toLowerCase().contains(lowerQuery)) {
                    filteredContacts.add(contact);
                }
            }
        }
        updateUI();
    }

    private void updateUI() {
        contactsAdapter.submitList(filteredContacts);
        boolean isEmpty = filteredContacts.isEmpty();
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        sectionHeader.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showAddContactDialog() {
        Toast.makeText(this, "Добавление контакта по номеру", Toast.LENGTH_SHORT).show();
    }

    private void openUserProfile(long userId) {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra("partner_user_id", userId);
        startActivity(intent);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) updateUI();
        else {
            recyclerView.setVisibility(View.GONE);
            emptyState.setVisibility(View.GONE);
            sectionHeader.setVisibility(View.GONE);
        }
    }
}