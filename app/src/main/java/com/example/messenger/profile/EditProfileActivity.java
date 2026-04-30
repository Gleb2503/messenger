package com.example.messenger.profile;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.messenger.R;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.api.attachment.AttachmentResponse;
import com.example.messenger.util.Constants;
import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";
    private static final int AVATAR_PICKER_REQUEST = 1002;

    private Toolbar toolbar;
    private ImageView backButton;
    private EditText displayNameInput, phoneInput, avatarInput, emailInput, passwordInput;
    private TextView displayNameHint, phoneHint, emailHint, passwordHint;
    private View saveButton;
    private ProgressBar progressBar;

    private ImageView avatarPreview, avatarEditIcon;
    private FrameLayout avatarContainer;
    private ProgressBar avatarProgress;
    private Uri selectedAvatarUri;

    private ApiService apiService;
    private Long currentUserId;
    private String originalDisplayName, originalPhone, originalAvatar, originalEmail;
    private boolean hasChanges = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profile);

        initNetwork();
        initViews();
        loadCurrentData();
        setupListeners();
        setupChangeDetection();
    }

    private void initNetwork() {
        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        backButton = findViewById(R.id.backButton);
        displayNameInput = findViewById(R.id.displayNameInput);
        phoneInput = findViewById(R.id.phoneInput);
        avatarInput = findViewById(R.id.avatarInput);
        emailInput = findViewById(R.id.emailInput);
        passwordInput = findViewById(R.id.passwordInput);
        displayNameHint = findViewById(R.id.displayNameHint);
        phoneHint = findViewById(R.id.phoneHint);
        emailHint = findViewById(R.id.emailHint);
        passwordHint = findViewById(R.id.passwordHint);
        saveButton = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);

        avatarContainer = findViewById(R.id.avatarContainer);
        avatarPreview = findViewById(R.id.avatarPreview);
        avatarEditIcon = findViewById(R.id.avatarEditIcon);
        avatarProgress = findViewById(R.id.avatarProgress);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        setupFocusEffect(displayNameInput, displayNameHint, "До 100 символов");
        setupFocusEffect(phoneInput, phoneHint, "Формат: +7 (999) 123-45-67");
        setupFocusEffect(emailInput, emailHint, "example@email.com");
        setupFocusEffect(passwordInput, passwordHint, "Минимум 6 символов");
    }

    private void setupFocusEffect(EditText input, TextView hint, String defaultText) {
        if (input == null || hint == null) return;

        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                input.setBackgroundResource(R.drawable.bg_input_field);
                hint.setTextColor(getColor(R.color.text_secondary));
                hint.setText(defaultText);
            }
        });
    }

    private void setupChangeDetection() {
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkForChanges();
            }
            @Override public void afterTextChanged(Editable s) {}
        };

        if (displayNameInput != null) displayNameInput.addTextChangedListener(watcher);
        if (phoneInput != null) phoneInput.addTextChangedListener(watcher);
        if (emailInput != null) emailInput.addTextChangedListener(watcher);
        if (passwordInput != null) passwordInput.addTextChangedListener(watcher);
    }

    private void checkForChanges() {
        String displayName = displayNameInput != null ? displayNameInput.getText().toString().trim() : "";
        String phone = phoneInput != null ? phoneInput.getText().toString().trim() : "";
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String avatar = selectedAvatarUri != null ? "changed" : (originalAvatar != null ? originalAvatar : "");

        hasChanges = !displayName.equals(originalDisplayName) ||
                !phone.equals(originalPhone) ||
                !email.equals(originalEmail) ||
                !avatar.equals(originalAvatar);
    }

    private void loadCurrentData() {
        originalDisplayName = getIntent().getStringExtra(Constants.KEY_DISPLAY_NAME);
        originalPhone = getIntent().getStringExtra(Constants.KEY_PHONE);
        originalAvatar = getIntent().getStringExtra(Constants.KEY_AVATAR);
        originalEmail = getIntent().getStringExtra(Constants.KEY_EMAIL);

        if (displayNameInput != null) {
            displayNameInput.setText(originalDisplayName != null ? originalDisplayName : "");
        }
        if (phoneInput != null) {
            phoneInput.setText(originalPhone != null ? originalPhone : "");
        }
        if (avatarInput != null) {
            avatarInput.setText(originalAvatar != null ? originalAvatar : "");
        }
        if (emailInput != null) {
            emailInput.setText(originalEmail != null ? originalEmail : "");
        }

        loadAvatarPreview(originalAvatar);
    }

    private void loadAvatarPreview(String avatarUrl) {
        if (avatarPreview == null) return;

        if (avatarUrl != null && !avatarUrl.isEmpty() && avatarUrl.startsWith("http")) {
            Glide.with(this)
                    .load(avatarUrl.trim())
                    .placeholder(R.drawable.bg_avatar_placeholder)
                    .error(R.drawable.bg_avatar_placeholder)
                    .circleCrop()
                    .into(avatarPreview);
        } else {
            avatarPreview.setImageResource(R.drawable.bg_avatar_placeholder);
        }
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> handleBackPress());
        saveButton.setOnClickListener(v -> performUpdate());

        View.OnClickListener avatarClickListener = v -> openAvatarPicker();
        avatarContainer.setOnClickListener(avatarClickListener);
        if (avatarEditIcon != null) {
            avatarEditIcon.setOnClickListener(avatarClickListener);
        }
    }

    private void openAvatarPicker() {
        ImagePicker.with(this)
                .crop()
                .compress(512)
                .maxResultSize(512, 512)
                .galleryOnly()
                .start(AVATAR_PICKER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == AVATAR_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedAvatarUri = data.getData();
            if (selectedAvatarUri != null) {
                avatarPreview.setImageURI(selectedAvatarUri);
                uploadAvatarToServer(selectedAvatarUri);
            }
        }
    }

    private void uploadAvatarToServer(Uri imageUri) {
        if (imageUri == null) return;

        setAvatarLoading(true);

        new Thread(() -> {
            try {
                File compressedFile = getResizedImageFile(EditProfileActivity.this, imageUri);
                if (compressedFile == null) {
                    runOnUiThread(() -> {
                        setAvatarLoading(false);
                        Toast.makeText(this, R.string.select_image_error, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                String fileName = "avatar_" + System.currentTimeMillis() + ".jpg";
                long fileSize = compressedFile.length();
                String fileType = "image/jpeg";

                RequestBody requestFile = RequestBody.create(
                        compressedFile,
                        MediaType.parse(fileType)
                );

                MultipartBody.Part filePart = MultipartBody.Part.createFormData(
                        "file", fileName, requestFile
                );

                createDummyMessageForAvatar(filePart, fileName, fileSize, fileType);

            } catch (Exception e) {
                Log.e(TAG, "Error uploading avatar", e);
                runOnUiThread(() -> {
                    setAvatarLoading(false);
                    Toast.makeText(EditProfileActivity.this, R.string.upload_avatar_error, Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void createDummyMessageForAvatar(MultipartBody.Part filePart, String fileName,
                                             long fileSize, String fileType) {
        Map<String, Object> dummyRequest = new HashMap<>();
        dummyRequest.put("chatId", currentUserId);
        dummyRequest.put("content", "[AVATAR_UPDATE]");
        dummyRequest.put("messageType", "file");

        apiService.sendMessage(currentUserId != null ? currentUserId : -1L, dummyRequest)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Object idObj = response.body().get("id");
                            if (idObj instanceof Number) {
                                Long dummyMessageId = ((Number) idObj).longValue();
                                proceedWithAvatarUpload(filePart, fileName, fileSize, fileType, dummyMessageId);
                                return;
                            }
                        }
                        handleAvatarUploadError();
                    }

                    @Override
                    public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                        Log.e(TAG, "Failed to create dummy message", t);
                        handleAvatarUploadError();
                    }
                });
    }

    private void proceedWithAvatarUpload(MultipartBody.Part filePart, String fileName,
                                         long fileSize, String fileType, Long messageId) {
        apiService.uploadAttachment(filePart, messageId, fileName, fileSize, fileType, null)
                .enqueue(new Callback<AttachmentResponse>() {
                    @Override
                    public void onResponse(Call<AttachmentResponse> call, Response<AttachmentResponse> response) {
                        setAvatarLoading(false);

                        if (response.isSuccessful() && response.body() != null) {
                            String newAvatarUrl = response.body().fileUrl;

                            if (avatarInput != null) {
                                avatarInput.setText(newAvatarUrl);
                            }

                            originalAvatar = newAvatarUrl;

                            SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                            prefs.edit().putString(Constants.KEY_AVATAR, newAvatarUrl).apply();

                            runOnUiThread(() -> loadAvatarPreview(newAvatarUrl));

                            Toast.makeText(EditProfileActivity.this, R.string.avatar_updated, Toast.LENGTH_SHORT).show();

                            checkForChanges();

                        } else {
                            handleAvatarUploadError();
                        }
                    }

                    @Override
                    public void onFailure(Call<AttachmentResponse> call, Throwable t) {
                        setAvatarLoading(false);
                        Log.e(TAG, "Avatar upload failed", t);
                        Toast.makeText(EditProfileActivity.this, "Ошибка сети при загрузке", Toast.LENGTH_SHORT).show();
                        loadAvatarPreview(originalAvatar);
                    }
                });
    }

    private void handleAvatarUploadError() {
        runOnUiThread(() -> {
            setAvatarLoading(false);
            Toast.makeText(EditProfileActivity.this, R.string.upload_avatar_error, Toast.LENGTH_SHORT).show();
            loadAvatarPreview(originalAvatar);
        });
    }

    private void setAvatarLoading(boolean isLoading) {
        if (avatarProgress != null) {
            avatarProgress.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (avatarEditIcon != null) {
            avatarEditIcon.setVisibility(isLoading ? View.GONE : View.VISIBLE);
        }
        if (avatarContainer != null) {
            avatarContainer.setEnabled(!isLoading);
        }
        if (isLoading) {
            Toast.makeText(this, R.string.avatar_uploading, Toast.LENGTH_SHORT).show();
        }
    }

    private File getResizedImageFile(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

        int maxSize = 512;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        float ratio = Math.min((float) maxSize / width, (float) maxSize / height);

        Bitmap resized = Bitmap.createScaledBitmap(
                bitmap,
                (int) (width * ratio),
                (int) (height * ratio),
                true
        );

        File tempFile = new File(context.getCacheDir(), "avatar_temp.jpg");
        FileOutputStream fos = new FileOutputStream(tempFile);
        resized.compress(Bitmap.CompressFormat.JPEG, 85, fos);
        fos.close();

        return tempFile;
    }

    private void handleBackPress() {
        if (hasChanges) {
            new AlertDialog.Builder(this)
                    .setTitle("Сохранить изменения?")
                    .setMessage("У вас есть несохранённые изменения")
                    .setPositiveButton("Сохранить", (d, w) -> performUpdate())
                    .setNegativeButton("Отменить", (d, w) -> finishWithoutResult())
                    .setNeutralButton("Продолжить", null)
                    .show();
        } else {
            finishWithoutResult();
        }
    }

    private void finishWithoutResult() {
        setResult(RESULT_CANCELED);
        finish();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void performUpdate() {
        String displayName = displayNameInput != null ? displayNameInput.getText().toString().trim() : "";
        String phone = phoneInput != null ? phoneInput.getText().toString().trim() : "";
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String avatarUrl = originalAvatar != null ? originalAvatar : "";
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";

        if (!validate(displayName, phone, email, password)) return;

        setLoading(true);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("displayName", displayName);
        updateRequest.put("phoneNumber", normalizePhone(phone));
        updateRequest.put("avatarUrl", avatarUrl);
        if (!email.isEmpty()) {
            updateRequest.put("email", email);
        }
        if (!password.isEmpty()) {
            updateRequest.put("password", password);
        }

        apiService.updateUser(currentUserId, updateRequest).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                setLoading(false);

                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(EditProfileActivity.this, "Профиль обновлён", Toast.LENGTH_SHORT).show();

                    SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);
                    prefs.edit()
                            .putString(Constants.KEY_DISPLAY_NAME, displayName)
                            .putString(Constants.KEY_PHONE, phone)
                            .putString(Constants.KEY_AVATAR, avatarUrl)
                            .putString(Constants.KEY_EMAIL, email)
                            .apply();

                    Intent result = new Intent();
                    result.putExtra(Constants.KEY_DISPLAY_NAME, displayName);
                    result.putExtra(Constants.KEY_PHONE, phone);
                    result.putExtra(Constants.KEY_AVATAR, avatarUrl);
                    result.putExtra(Constants.KEY_EMAIL, email);
                    setResult(RESULT_OK, result);

                    finish();
                    overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);

                } else {
                    try {
                        String error = response.errorBody() != null ? response.errorBody().string() : "Неизвестная ошибка";
                        Toast.makeText(EditProfileActivity.this, "Ошибка: " + error, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(EditProfileActivity.this, "Ошибка обновления", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Ошибка сети: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validate(String displayName, String phone, String email, String password) {
        boolean isValid = true;

        if (displayName.length() > 100) {
            setError(displayNameInput, displayNameHint, "Максимум 100 символов");
            isValid = false;
        } else {
            clearError(displayNameInput, displayNameHint, "До 100 символов");
        }

        if (!phone.isEmpty() && phone.length() < 10) {
            setError(phoneInput, phoneHint, "Слишком короткий номер");
            isValid = false;
        } else {
            clearError(phoneInput, phoneHint, "Формат: +7 (999) 123-45-67");
        }

        if (!email.isEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(emailInput, emailHint, "Некорректный email");
            isValid = false;
        } else {
            clearError(emailInput, emailHint, "example@email.com");
        }

        if (!password.isEmpty() && password.length() < 6) {
            setError(passwordInput, passwordHint, "Минимум 6 символов");
            isValid = false;
        } else if (!password.isEmpty()) {
            clearError(passwordInput, passwordHint, "Минимум 6 символов");
        }

        return isValid;
    }

    private void setError(EditText input, TextView hint, String msg) {
        if (input != null) input.setBackgroundResource(R.drawable.bg_input_field_error);
        if (hint != null) {
            hint.setText(msg);
            hint.setTextColor(getColor(R.color.error));
            hint.setVisibility(View.VISIBLE);
        }
    }

    private void clearError(EditText input, TextView hint, String defaultMsg) {
        if (input != null) input.setBackgroundResource(R.drawable.bg_input_field);
        if (hint != null) {
            hint.setText(defaultMsg);
            hint.setTextColor(getColor(R.color.text_secondary));
            hint.setVisibility(View.VISIBLE);
        }
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^\\d+]", "");
        if (cleaned.startsWith("8") && cleaned.length() == 11) cleaned = "+7" + cleaned.substring(1);
        else if (cleaned.startsWith("7") && cleaned.length() == 11) cleaned = "+7" + cleaned.substring(1);
        else if (!cleaned.startsWith("+") && cleaned.length() == 11) cleaned = "+" + cleaned;
        return cleaned;
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (saveButton != null) saveButton.setEnabled(!isLoading);
        if (displayNameInput != null) displayNameInput.setEnabled(!isLoading);
        if (phoneInput != null) phoneInput.setEnabled(!isLoading);
        if (emailInput != null) emailInput.setEnabled(!isLoading);
        if (passwordInput != null) passwordInput.setEnabled(!isLoading);
        if (backButton != null) backButton.setEnabled(!isLoading);

        if (isLoading && saveButton instanceof android.widget.Button) {
            ((android.widget.Button) saveButton).setText("");
            saveButton.setBackgroundResource(R.drawable.bg_button_primary_disabled);
        } else if (saveButton instanceof android.widget.Button) {
            ((android.widget.Button) saveButton).setText(getString(R.string.save_changes_button));
            saveButton.setBackgroundResource(R.drawable.bg_button_primary);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }
}