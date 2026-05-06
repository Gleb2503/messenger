package com.example.messenger.group;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.messenger.R;
import com.example.messenger.contacts.ContactItem;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.github.dhaval2404.imagepicker.ImagePicker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GroupInfoActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_IMAGE = 1004;

    private ImageView backButton, groupAvatar, editAvatar;
    private TextView groupNameText, participantCountText, editNameIcon;
    private EditText groupNameInput;
    private RecyclerView participantsRecyclerView;
    private ProgressBar progressBar;
    private View leaveButton, deleteButton;

    private ApiService apiService;
    private GroupParticipantsAdapter participantsAdapter;
    private final List<ContactItem> participants = new ArrayList<>();

    private long chatId;
    private String groupName;
    private String groupAvatarUrl;
    private long currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
        chatId = getIntent().getLongExtra("chat_id", -1);

        initViews();
        setupClickListeners();
        loadGroupInfo();
        loadParticipants();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                uploadGroupAvatar(imageUri);
            }
        }
    }

    private void initViews() {
        backButton = findViewById(R.id.backButton);
        groupAvatar = findViewById(R.id.groupAvatar);
        editAvatar = findViewById(R.id.editAvatar);
        groupNameText = findViewById(R.id.groupNameText);
        groupNameInput = findViewById(R.id.groupNameInput);
        editNameIcon = findViewById(R.id.editNameIcon);
        participantCountText = findViewById(R.id.participantCountText);
        participantsRecyclerView = findViewById(R.id.participantsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        leaveButton = findViewById(R.id.leaveButton);
        deleteButton = findViewById(R.id.deleteButton);

        // Pass 'false' as the third argument to hide checkboxes
        participantsAdapter = new GroupParticipantsAdapter(new ArrayList<>(), null, false);
        participantsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        participantsRecyclerView.setAdapter(participantsAdapter);
    }

    private void setupClickListeners() {
        if (backButton != null) {
            backButton.setOnClickListener(v -> finish());
        }
        if (editAvatar != null) {
            editAvatar.setOnClickListener(v -> openGallery());
        }
        if (editNameIcon != null) {
            editNameIcon.setOnClickListener(v -> toggleNameEdit());
        }

        if (groupNameInput != null) {
            groupNameInput.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                    saveGroupName();
                    return true;
                }
                return false;
            });
        }

        if (leaveButton != null) {
            leaveButton.setOnClickListener(v -> showLeaveDialog());
        }
        if (deleteButton != null) {
            deleteButton.setOnClickListener(v -> showDeleteDialog());
        }
    }

    private void openGallery() {
        ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .galleryOnly()
                .start(REQUEST_PICK_IMAGE);
    }

    private void toggleNameEdit() {
        if (groupNameInput == null || groupNameText == null || editNameIcon == null) return;

        if (groupNameInput.getVisibility() == View.VISIBLE) {
            saveGroupName();
        } else {
            groupNameText.setVisibility(View.GONE);
            editNameIcon.setText("Сохранить");
            groupNameInput.setVisibility(View.VISIBLE);
            groupNameInput.setText(groupName);
            groupNameInput.requestFocus();
        }
    }

    private void saveGroupName() {
        if (groupNameInput == null) return;

        String newName = groupNameInput.getText().toString().trim();
        if (newName.isEmpty() || newName.equals(groupName)) {
            cancelNameEdit();
            return;
        }

        Map<String, String> request = new HashMap<>();
        request.put("name", newName);

        apiService.updateGroupName(chatId, request)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            groupName = newName;
                            if (groupNameText != null) {
                                groupNameText.setText(groupName);
                            }
                            Toast.makeText(GroupInfoActivity.this, "Название обновлено", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(GroupInfoActivity.this, "Ошибка обновления", Toast.LENGTH_SHORT).show();
                        }
                        cancelNameEdit();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(GroupInfoActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                        cancelNameEdit();
                    }
                });
    }

    private void cancelNameEdit() {
        if (groupNameInput == null || groupNameText == null || editNameIcon == null) return;

        groupNameInput.setVisibility(View.GONE);
        editNameIcon.setText("✎");
        groupNameText.setVisibility(View.VISIBLE);
    }

    private void loadGroupInfo() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        apiService.getChatInfo(chatId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                if (isFinishing() || isDestroyed()) return;

                if (response.isSuccessful() && response.body() != null) {
                    Map<String, Object> data = response.body();

                    groupName = safeGetString(data.get("name"));
                    if (groupName != null && groupNameText != null) {
                        groupNameText.setText(groupName);
                    }
                    if (groupName != null && groupNameInput != null) {
                        groupNameInput.setText(groupName);
                    }

                    groupAvatarUrl = safeGetString(data.get("avatarUrl"));
                    if (groupAvatarUrl != null && !groupAvatarUrl.isEmpty() && groupAvatarUrl.startsWith("http")) {
                        loadGroupAvatar(groupAvatarUrl);
                    }

                    Object countObj = data.get("participantCount");
                    if (countObj instanceof Number && participantCountText != null) {
                        int count = ((Number) countObj).intValue();
                        participantCountText.setText(count + " участников");
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
                if (isFinishing() || isDestroyed()) return;
                if (participantCountText != null) {
                    participantCountText.setText("Групповой чат");
                }
            }
        });
    }

    private void loadGroupAvatar(String url) {
        if (groupAvatar == null) return;
        if (url != null && !url.isEmpty() && url.startsWith("http")) {
            Glide.with(this)
                    .load(url.trim())
                    .placeholder(R.drawable.bg_logo_gradient)
                    .error(R.drawable.bg_logo_gradient)
                    .circleCrop()
                    .into(groupAvatar);
        }
    }

    private void uploadGroupAvatar(Uri imageUri) {
        Toast.makeText(this, "Загрузка аватара...", Toast.LENGTH_SHORT).show();
    }

    private void loadParticipants() {
        apiService.getChatParticipants(chatId)
                .enqueue(new Callback<List<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<List<Map<String, Object>>> call,
                                           Response<List<Map<String, Object>>> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful() && response.body() != null) {
                            participants.clear();
                            for (Map<String, Object> participant : response.body()) {
                                try {
                                    Map<String, Object> user = (Map<String, Object>) participant.get("user");
                                    if (user == null) continue;

                                    Long id = safeGetLong(user.get("id"));
                                    String username = safeGetString(user.get("username"));
                                    String displayName = safeGetString(user.get("displayName"));
                                    String avatarUrl = safeGetString(user.get("avatarUrl"));
                                    Boolean isOnline = (Boolean) participant.get("isOnline");

                                    if (id != null && id > 0) {
                                        participants.add(new ContactItem(
                                                null,
                                                id,
                                                displayName,
                                                username,
                                                avatarUrl,
                                                isOnline != null && isOnline,
                                                true
                                        ));
                                    }
                                } catch (ClassCastException | NullPointerException e) {
                                    continue;
                                }
                            }
                            if (participantsAdapter != null) {
                                participantsAdapter.setAllContacts(participants);
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Map<String, Object>>> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(GroupInfoActivity.this, "Ошибка загрузки участников", Toast.LENGTH_SHORT).show();
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

    private void showLeaveDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из группы")
                .setMessage("Вы уверены, что хотите покинуть этот чат?")
                .setPositiveButton("Выйти", (dialog, which) -> leaveGroup())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void leaveGroup() {
        apiService.removeParticipant(chatId, currentUserId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(GroupInfoActivity.this, "Ошибка", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(GroupInfoActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showDeleteDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Удалить группу")
                .setMessage("Вы уверены? Это действие нельзя отменить.")
                .setPositiveButton("Удалить", (dialog, which) -> deleteGroup())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteGroup() {
        apiService.deleteChat(chatId)
                .enqueue(new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (isFinishing() || isDestroyed()) return;

                        if (response.isSuccessful()) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            Toast.makeText(GroupInfoActivity.this, "Ошибка", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        if (isFinishing() || isDestroyed()) return;
                        Toast.makeText(GroupInfoActivity.this, "Ошибка сети", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}