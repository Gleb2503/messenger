package com.example.messenger.group;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.messenger.R;
import com.example.messenger.contacts.ContactItem;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateGroupChatActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IMAGE = 1002;
    private static final int MAX_PARTICIPANTS = 100;

    private EditText groupNameInput, phoneSearchInput;
    private ImageView groupAvatarPreview;
    private RecyclerView contactsRecyclerView;
    private Button createButton, addByPhoneButton;
    private TextView emptyContactsHint;

    private ApiService apiService;
    private GroupParticipantsAdapter participantsAdapter;
    private final List<ContactItem> selectedContacts = new ArrayList<>();
    private final List<ContactItem> allContacts = new ArrayList<>();
    private final Map<Long, ContactItem> contactsMap = new HashMap<>();
    private Uri selectedAvatarUri;
    private long currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group_chat);

        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();

        initViews();
        setupClickListeners();
        loadContactsAndChats();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            selectedAvatarUri = data.getData();
            if (selectedAvatarUri != null) {
                Glide.with(this)
                        .load(selectedAvatarUri)
                        .circleCrop()
                        .placeholder(R.drawable.bg_avatar_placeholder)
                        .error(R.drawable.bg_avatar_placeholder)
                        .into(groupAvatarPreview);
            }
        }
    }

    private void initViews() {
        groupNameInput = findViewById(R.id.groupNameInput);
        phoneSearchInput = findViewById(R.id.phoneSearchInput);
        groupAvatarPreview = findViewById(R.id.groupAvatarPreview);
        contactsRecyclerView = findViewById(R.id.contactsRecyclerView);
        createButton = findViewById(R.id.createButton);
        addByPhoneButton = findViewById(R.id.addByPhoneButton);
        emptyContactsHint = findViewById(R.id.emptyContactsHint);

        participantsAdapter = new GroupParticipantsAdapter(selectedContacts, this::toggleContactSelection);
        contactsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        contactsRecyclerView.setAdapter(participantsAdapter);
    }

    private void setupClickListeners() {
        View backButton = findViewById(R.id.backButton);
        if (backButton != null) backButton.setOnClickListener(v -> finish());

        if (groupAvatarPreview != null) groupAvatarPreview.setOnClickListener(v -> openGallery());
        if (createButton != null) createButton.setOnClickListener(v -> createGroupChat());
        if (addByPhoneButton != null) addByPhoneButton.setOnClickListener(v -> searchAndAddByPhone());
    }

    private void openGallery() {
        ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .galleryOnly()
                .start(REQUEST_PICK_IMAGE);
    }

    private void loadContactsAndChats() {
        allContacts.clear();
        contactsMap.clear();

        apiService.getUserContacts(currentUserId).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> backendContacts = response.body();
                    for (Map<String, Object> contactMap : backendContacts) {
                        try {
                            Long id = safeGetLong(contactMap.get("id"));
                            Map<String, Object> contactUser = (Map<String, Object>) contactMap.get("contactUser");
                            if (contactUser == null) continue;

                            Long userId = safeGetLong(contactUser.get("id"));
                            String username = safeGetString(contactUser.get("username"));
                            String displayName = safeGetString(contactUser.get("displayName"));
                            String avatarUrl = safeGetString(contactUser.get("avatarUrl"));
                            Boolean isOnline = (Boolean) contactMap.get("isOnline");
                            Boolean isExplicit = (Boolean) contactMap.get("isExplicitContact");

                            if (userId != null && userId > 0 && userId != currentUserId) {
                                if (displayName == null || displayName.isEmpty()) displayName = username;
                                ContactItem item = new ContactItem(id, userId, displayName, username, avatarUrl,
                                        isOnline != null && isOnline, isExplicit != null && isExplicit);
                                allContacts.add(item);
                                contactsMap.put(userId, item);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                loadChatsForPartners();
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
                if (response.isSuccessful() && response.body() != null) {
                    List<Map<String, Object>> backendChats = response.body();
                    for (Map<String, Object> chat : backendChats) {
                        try {
                            long partnerUserId = findPartnerUserId(chat, currentUserId);
                            if (partnerUserId <= 0 || partnerUserId == currentUserId) continue;
                            if (contactsMap.containsKey(partnerUserId)) continue;

                            String chatName = safeGetString(chat.get("name"));
                            Map<String, Object> partnerMap = null;
                            if (chat.containsKey("partner") && chat.get("partner") instanceof Map) {
                                partnerMap = (Map<String, Object>) chat.get("partner");
                            }

                            String username = "";
                            String displayName = chatName != null && !chatName.isEmpty() ? chatName : "Пользователь";
                            String avatarUrl = "";

                            if (partnerMap != null) {
                                username = safeGetString(partnerMap.get("username"));
                                String nameFromPartner = safeGetString(partnerMap.get("displayName"));
                                if (nameFromPartner != null && !nameFromPartner.isEmpty()) {
                                    displayName = nameFromPartner;
                                }
                                avatarUrl = safeGetString(partnerMap.get("avatarUrl"));
                            }

                            ContactItem item = new ContactItem(0L, partnerUserId, displayName, username, avatarUrl, false, false);
                            allContacts.add(item);
                            contactsMap.put(partnerUserId, item);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
                finalizeContactsList();
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                finalizeContactsList();
            }
        });
    }

    private void finalizeContactsList() {
        allContacts.sort((c1, c2) -> c1.getDisplayName().compareToIgnoreCase(c2.getDisplayName()));
        updateContactsUI();
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

    private void updateContactsUI() {
        if (allContacts.isEmpty()) {
            showEmptyContactsHint(true);
        } else {
            showEmptyContactsHint(false);
            participantsAdapter.setAllContacts(allContacts);
            participantsAdapter.notifyDataSetChanged();
        }
    }

    private void showEmptyContactsHint(boolean show) {
        if (emptyContactsHint != null) {
            emptyContactsHint.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void searchAndAddByPhone() {
        String phone = phoneSearchInput != null ? phoneSearchInput.getText().toString().trim() : "";
        if (phone.isEmpty()) {
            Toast.makeText(this, "Введите номер телефона", Toast.LENGTH_SHORT).show();
            return;
        }

        addByPhoneButton.setEnabled(false);
        addByPhoneButton.setText("Поиск...");

        apiService.searchUsersByPhone(phone).enqueue(new Callback<List<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<List<Map<String, Object>>> call, Response<List<Map<String, Object>>> response) {
                addByPhoneButton.setEnabled(true);
                addByPhoneButton.setText("Найти");
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null && !response.body().isEmpty()) {
                    Map<String, Object> user = response.body().get(0);
                    Long userId = safeGetLong(user.get("id"));
                    if (userId != null && userId > 0) {
                        if (isUserAlreadySelected(userId)) {
                            Toast.makeText(CreateGroupChatActivity.this, "Пользователь уже добавлен", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (userId == currentUserId) {
                            Toast.makeText(CreateGroupChatActivity.this, "Нельзя добавить себя", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        String displayName = safeGetString(user.get("displayName"));
                        String username = safeGetString(user.get("username"));
                        String avatarUrl = safeGetString(user.get("avatarUrl"));

                        ContactItem newItem = new ContactItem(null, userId, displayName, username, avatarUrl, false, false);
                        selectedContacts.add(newItem);
                        if (!allContacts.contains(newItem)) {
                            allContacts.add(newItem);
                            contactsMap.put(userId, newItem);
                        }

                        participantsAdapter.setAllContacts(allContacts);
                        participantsAdapter.notifyDataSetChanged();
                        updateCreateButton();
                        if (phoneSearchInput != null) phoneSearchInput.setText("");
                        Toast.makeText(CreateGroupChatActivity.this, "Пользователь найден и добавлен", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CreateGroupChatActivity.this, "Пользователь не найден", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(CreateGroupChatActivity.this, "Пользователь с таким номером не найден", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                addByPhoneButton.setEnabled(true);
                addByPhoneButton.setText("Найти");
                if (isFinishing() || isDestroyed()) return;
                Toast.makeText(CreateGroupChatActivity.this, "Ошибка сети при поиске", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private boolean isUserAlreadySelected(Long userId) {
        for (ContactItem item : selectedContacts) {
            if (item.getUserId() != null && item.getUserId().equals(userId)) return true;
        }
        return false;
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
        participantsAdapter.notifyDataSetChanged();
        updateCreateButton();
    }

    private void updateCreateButton() {
        if (createButton == null) return;
        createButton.setEnabled(selectedContacts.size() >= 2);
        createButton.setText(selectedContacts.size() >= 2 ?
                "Создать чат (" + selectedContacts.size() + ")" : "Выберите минимум 2 контакта");
    }

    private void createGroupChat() {
        String groupName = groupNameInput != null ? groupNameInput.getText().toString().trim() : "";
        if (groupName.isEmpty()) {
            if (groupNameInput != null) {
                groupNameInput.setError("Введите название группы");
                groupNameInput.requestFocus();
            }
            return;
        }
        if (selectedContacts.size() < 2) {
            Toast.makeText(this, "Выберите минимум 2 контакта", Toast.LENGTH_SHORT).show();
            return;
        }


        Map<String, Object> request = new HashMap<>();
        request.put("name", groupName);
        request.put("type", "group");

        createButton.setEnabled(false);

        apiService.createGroupChat(request).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                createButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    Object idObj = response.body().get("id");
                    Object nameObj = response.body().get("name");
                    if (idObj instanceof Number && nameObj instanceof String) {
                        long chatId = ((Number) idObj).longValue();

                        addParticipantsToGroup(chatId, new Runnable() {
                            @Override
                            public void run() {
                                Intent result = new Intent();
                                result.putExtra("chat_id", chatId);
                                result.putExtra("chat_name", nameObj.toString());
                                result.putExtra("is_group", true);
                                setResult(RESULT_OK, result);
                                finish();
                            }
                        });
                    } else {
                        showError("Неверный формат ответа сервера");
                    }
                } else {
                    String errorDetails = readErrorBody(response);
                    showError("Ошибка " + response.code() + ": " + errorDetails);
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                createButton.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                showError("Ошибка сети: " + t.getMessage());
            }
        });
    }


    private void addParticipantsToGroup(long chatId, Runnable onSuccess) {
        if (selectedContacts.isEmpty()) {
            onSuccess.run();
            return;
        }
        addParticipantRecursive(chatId, 0, selectedContacts, onSuccess);
    }

    private void addParticipantRecursive(long chatId, int index, List<ContactItem> contacts, Runnable onSuccess) {
        if (index >= contacts.size()) {
            onSuccess.run();
            return;
        }

        ContactItem contact = contacts.get(index);
        if (contact.getUserId() == null || contact.getUserId() <= 0 || contact.getUserId() == currentUserId) {
            addParticipantRecursive(chatId, index + 1, contacts, onSuccess);
            return;
        }


        Map<String, Object> request = new HashMap<>();
        request.put("chatId", chatId);
        request.put("userId", contact.getUserId());
        request.put("role", "member");

        apiService.addParticipantToChat(request)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        // Игнорируем ошибки — продолжаем со следующими участниками
                        addParticipantRecursive(chatId, index + 1, contacts, onSuccess);
                    }
                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        addParticipantRecursive(chatId, index + 1, contacts, onSuccess);
                    }
                });
    }

    private String readErrorBody(Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (IOException e) {
            return e.getMessage();
        }
        return "";
    }

    private void showError(String message) {
        Toast.makeText(CreateGroupChatActivity.this, message, Toast.LENGTH_LONG).show();
    }

    private Long safeGetLong(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : null;
    }

    private String safeGetString(Object value) {
        return value instanceof String ? (String) value : "";
    }
}