package com.example.messenger.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.messenger.R;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.util.Constants;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EditProfileActivity extends AppCompatActivity {

    private static final String TAG = "EditProfileActivity";

    private Toolbar toolbar;
    private ImageView backButton;
    private EditText displayNameInput, phoneInput, avatarInput, emailInput, passwordInput;
    private TextView displayNameHint, phoneHint, avatarHint, emailHint, passwordHint;
    private View saveButton;
    private ProgressBar progressBar;

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
        avatarHint = findViewById(R.id.avatarHint);
        emailHint = findViewById(R.id.emailHint);
        passwordHint = findViewById(R.id.passwordHint);
        saveButton = findViewById(R.id.saveButton);
        progressBar = findViewById(R.id.progressBar);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        setupFocusEffect(displayNameInput, displayNameHint, "До 100 символов");
        setupFocusEffect(phoneInput, phoneHint, "Формат: +7 (999) 123-45-67");
        setupFocusEffect(avatarInput, avatarHint, "https://example.com/avatar.jpg");
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
        if (avatarInput != null) avatarInput.addTextChangedListener(watcher);
        if (emailInput != null) emailInput.addTextChangedListener(watcher);
        if (passwordInput != null) passwordInput.addTextChangedListener(watcher);
    }

    private void checkForChanges() {
        String displayName = displayNameInput != null ? displayNameInput.getText().toString().trim() : "";
        String phone = phoneInput != null ? phoneInput.getText().toString().trim() : "";
        String avatar = avatarInput != null ? avatarInput.getText().toString().trim() : "";
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";

        hasChanges = !displayName.equals(originalDisplayName) ||
                !phone.equals(originalPhone) ||
                !avatar.equals(originalAvatar) ||
                !email.equals(originalEmail) ||
                !password.isEmpty();
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
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> handleBackPress());
        saveButton.setOnClickListener(v -> performUpdate());
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
        String avatarUrl = avatarInput != null ? avatarInput.getText().toString().trim() : "";
        String email = emailInput != null ? emailInput.getText().toString().trim() : "";
        String password = passwordInput != null ? passwordInput.getText().toString().trim() : "";

        if (!validate(displayName, phone, avatarUrl, email, password)) return;

        setLoading(true);

        Map<String, Object> updateRequest = new HashMap<>();
        updateRequest.put("displayName", displayName);
        updateRequest.put("phoneNumber", normalizePhone(phone));
        updateRequest.put("avatarUrl", avatarUrl);
        updateRequest.put("email", email);
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

    private boolean validate(String displayName, String phone, String avatarUrl, String email, String password) {
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

        if (!avatarUrl.isEmpty() && !avatarUrl.startsWith("http")) {
            setError(avatarInput, avatarHint, "Должен начинаться с https://");
            isValid = false;
        } else {
            clearError(avatarInput, avatarHint, "https://example.com/avatar.jpg");
        }


        if (!email.isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(emailInput, emailHint, "Неверный формат email");
            isValid = false;
        } else {
            clearError(emailInput, emailHint, "example@email.com");
        }


        if (!password.isEmpty() && password.length() < 6) {
            setError(passwordInput, passwordHint, "Минимум 6 символов");
            isValid = false;
        } else {
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
        if (phone == null || phone.isEmpty()) return "";
        String cleaned = phone.replaceAll("[^\\d+]", "");
        if (cleaned.startsWith("8") && cleaned.length() == 11) {
            cleaned = "+7" + cleaned.substring(1);
        } else if (cleaned.startsWith("7") && cleaned.length() == 11) {
            cleaned = "+" + cleaned;
        } else if (!cleaned.startsWith("+") && cleaned.length() == 10) {
            cleaned = "+7" + cleaned;
        }
        return cleaned;
    }

    private void setLoading(boolean isLoading) {
        if (progressBar != null) progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        if (saveButton != null) saveButton.setEnabled(!isLoading);
        if (displayNameInput != null) displayNameInput.setEnabled(!isLoading);
        if (phoneInput != null) phoneInput.setEnabled(!isLoading);
        if (avatarInput != null) avatarInput.setEnabled(!isLoading);
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
        handleBackPress();
    }
}