package com.example.messenger.group;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.messenger.R;
import com.example.messenger.contacts.ContactItem;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddParticipantsActivity extends AppCompatActivity {

    private static final int MAX_PARTICIPANTS = 100;

    private ImageView backButton;
    private EditText searchInput;
    private RecyclerView contactsRecyclerView;
    private Button addButton;

    private ApiService apiService;
    private GroupParticipantsAdapter participantsAdapter;
    private final List<ContactItem> selectedContacts = new ArrayList<>();
    private final List<ContactItem> allContacts = new ArrayList<>();

    private long chatId;
    private long currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_participants);

        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
        chatId = getIntent().getLongExtra("chat_id", -1);

        initViews();
        setupClickListeners();
        loadContacts();
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        searchInput = findViewById(R.id.searchInput);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        addButton = findViewById(R.id.addButton);

        participantsAdapter = new GroupParticipantsAdapter(selectedContacts, this::toggleContactSelection);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(participantsAdapter);
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (addButton != null) {
            addButton.setOnClickListener(v -> addSelectedParticipants());
        }

        if (searchInput != null) {
            searchInput.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void afterTextChanged(android.text.Editable s) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    filterContacts(s.toString().trim());
                }
            });
        }
    }

    private void loadContacts() {
        apiService.getUserContacts(currentUserId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            allContacts.clear();
                            for (Map<String, Object> contact : response.body()) {
                                try {
                                    Map<String, Object> user = (Map<String, Object>) contact.get("contactUser");
                                    if (user == null) continue;

                                    Long id = safeGetLong(contact.get("id"));
                                    Long userId = safeGetLong(user.get("id"));
                                    String displayName = safeGetString(user.get("displayName"));
                                    String username = safeGetString(user.get("username"));
                                    String avatarUrl = safeGetString(user.get("avatarUrl"));
                                    Boolean isOnline = (Boolean) contact.get("isOnline");
                                    Boolean isExplicit = (Boolean) contact.get("isExplicitContact");

                                    if (userId != null && userId > 0) {
                                        allContacts.add(new ContactItem(
                                                id,
                                                userId,
                                                displayName,
                                                username,
                                                avatarUrl,
                                                isOnline != null && isOnline,
                                                isExplicit != null && isExplicit
                                        ));
                                    }
                                } catch (ClassCastException | NullPointerException e) {
                                    continue;
                                }
                            }
                            if (participantsAdapter != null) {
                                participantsAdapter.setAllContacts(allContacts);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(AddParticipantsActivity.this, "Ошибка загрузки контактов", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void filterContacts(String query) {
        if (participantsAdapter == null) return;

        if (query.isEmpty()) {
            participantsAdapter.setAllContacts(allContacts);
        } else {
            List<ContactItem> filtered = new ArrayList<>();
            for (ContactItem contact : allContacts) {
                if (contact.getDisplayName().toLowerCase().contains(query.toLowerCase()) ||
                        contact.getUsername().toLowerCase().contains(query.toLowerCase())) {
                    filtered.add(contact);
                }
            }
            participantsAdapter.setAllContacts(filtered);
        }
    }

    private void toggleContactSelection(ContactItem contact) {
        if (contact == null || contact.getUserId() == null) return;

        if (selectedContacts.contains(contact)) {
            selectedContacts.remove(contact);
        } else {
            if (selectedContacts.size() >= MAX_PARTICIPANTS) {
                Toast.makeText(this, "Максимум " + MAX_PARTICIPANTS + " участников", Toast.LENGTH_SHORT).show();
                return;
            }
            selectedContacts.add(contact);
        }
        if (participantsAdapter != null) {
            participantsAdapter.notifyDataSetChanged();
        }
        updateAddButton();
    }

    private void updateAddButton() {
        if (addButton == null) return;
        addButton.setEnabled(!selectedContacts.isEmpty());
        addButton.setText(selectedContacts.isEmpty() ?
                "Выберите контакты" : "Добавить (" + selectedContacts.size() + ")");
    }

    private void addSelectedParticipants() {
        if (selectedContacts.isEmpty()) {
            Toast.makeText(this, "Выберите хотя бы одного контакта", Toast.LENGTH_SHORT).show();
            return;
        }

        for (ContactItem contact : selectedContacts) {
            if (contact.getUserId() != null && contact.getUserId() > 0) {
                addParticipant(contact.getUserId());
            }
        }
    }

    private void addParticipant(long userId) {
        Map<String, Long> request = new HashMap<>();
        request.put("userId", userId);

        apiService.addParticipant(chatId, request)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call,
                                           Response<Map<String, Object>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            Toast.makeText(AddParticipantsActivity.this, "Участник добавлен", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(AddParticipantsActivity.this, "Ошибка добавления", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(AddParticipantsActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private Long safeGetLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private String safeGetString(Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        return "";
    }
}