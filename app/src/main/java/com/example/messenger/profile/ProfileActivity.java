
package com.example.messenger.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.messenger.R;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.login.LoginActivity;
import com.example.messenger.util.Constants;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";
    private static ProfileActivity instance;

    private Toolbar toolbar;
    private ImageView backButton, moreOptionsButton, profileAvatar;
    private FrameLayout avatarContainer;
    private TextView userName, userUsername, userEmail, userPhone, registrationDate;
    private MaterialButton editProfileButton;
    private MaterialCardView emailCard, phoneCard, registrationCard;

    private String username = "";
    private String handle = "";
    private String email = "";
    private String phone = "";
    private String avatarUrl = "";
    private String registrationDateText = "";

    private ApiService apiService;
    private long currentUserId;
    private String authToken;

    private boolean viewsInitialized = false;
    private boolean isProfileJustUpdated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        instance = this;

        setContentView(R.layout.activity_profile);
        initViews();
        initNetwork();
        loadProfileData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (instance == this) instance = null;
    }

    public static ProfileActivity getInstance() {
        return instance;
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        backButton = findViewById(R.id.backButton);
        moreOptionsButton = findViewById(R.id.moreOptionsButton);

        avatarContainer = findViewById(R.id.avatarContainer);
        profileAvatar = findViewById(R.id.profileAvatar);

        userName = findViewById(R.id.userName);
        userUsername = findViewById(R.id.userUsername);
        userEmail = findViewById(R.id.userEmail);
        userPhone = findViewById(R.id.userPhone);
        registrationDate = findViewById(R.id.registrationDate);

        emailCard = findViewById(R.id.emailCard);
        phoneCard = findViewById(R.id.phoneCard);
        registrationCard = findViewById(R.id.registrationCard);
        editProfileButton = findViewById(R.id.editProfileButton);

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
            getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        }

        setupListeners();
        viewsInitialized = true;
    }

    private void initNetwork() {
        RetrofitClient.init(this);
        apiService = RetrofitClient.getApiService();
        currentUserId = RetrofitClient.getUserId();
        authToken = RetrofitClient.getToken();
    }

    private void loadProfileData() {
        loadFromSharedPreferences();
        updateProfileUI();
        fetchUserProfileFromServer();
    }

    private void loadFromSharedPreferences() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        String rawUsername = prefs.getString(Constants.KEY_USERNAME, "");
        if (!rawUsername.isEmpty()) {
            username = rawUsername.startsWith("@") ? rawUsername.substring(1) : rawUsername;
            handle = rawUsername.startsWith("@") ? rawUsername : "@" + rawUsername;
        } else {
            username = "Пользователь";
            handle = "@user";
        }

        email = prefs.getString(Constants.KEY_EMAIL, "");
        phone = prefs.getString(Constants.KEY_PHONE, "");
        avatarUrl = prefs.getString(Constants.KEY_AVATAR, "");
        registrationDateText = prefs.getString(Constants.KEY_REGISTRATION_DATE, "Неизвестно");
    }

    private void fetchUserProfileFromServer() {
        if (isProfileJustUpdated) {
            isProfileJustUpdated = false;
            return;
        }

        if (authToken == null || authToken.isEmpty() || currentUserId <= 0) {
            return;
        }

        apiService.getUserProfile(currentUserId).enqueue(new Callback<Map<String, Object>>() {
            @Override
            public void onResponse(Call<Map<String, Object>> call, Response<Map<String, Object>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    parseUserProfile(response.body());
                    saveToSharedPreferences();
                    updateProfileUI();
                }
            }

            @Override
            public void onFailure(Call<Map<String, Object>> call, Throwable t) {
                Log.e(TAG, "Network error", t);
            }
        });
    }

    private void parseUserProfile(Map<String, Object> userData) {
        String displayName = getStringField(userData, "displayName");
        if (!displayName.isEmpty()) {
            username = displayName;
        } else {
            String rawUsername = getStringField(userData, "username");
            username = rawUsername.startsWith("@") ? rawUsername.substring(1) : rawUsername;
        }

        String rawUsername = getStringField(userData, "username");
        if (!rawUsername.isEmpty()) {
            handle = rawUsername.startsWith("@") ? rawUsername : "@" + rawUsername;
        } else {
            handle = "@user";
        }

        email = getStringField(userData, "email");

        phone = getStringField(userData, "phone");
        if (phone.isEmpty()) phone = getStringField(userData, "phoneNumber");
        if (phone.isEmpty()) phone = getStringField(userData, "mobile");

        avatarUrl = getStringField(userData, "avatarUrl");
        if (avatarUrl == null) avatarUrl = "";

        String createdAt = getStringField(userData, "createdAt");
        registrationDateText = formatDate(createdAt);
    }

    private String getStringField(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value instanceof String ? (String) value : "";
    }

    private String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "Неизвестно";
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(isoDate.split("T")[0]);

            SimpleDateFormat outputFormat = new SimpleDateFormat("dd MMMM yyyy", new Locale("ru"));
            outputFormat.setTimeZone(TimeZone.getDefault());
            return outputFormat.format(date);
        } catch (Exception e) {
            return isoDate.split("T")[0];
        }
    }

    private void saveToSharedPreferences() {
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE).edit()
                .putString(Constants.KEY_USERNAME, username)
                .putString(Constants.KEY_DISPLAY_NAME, username)
                .putString(Constants.KEY_EMAIL, email)
                .putString(Constants.KEY_PHONE, phone)
                .putString(Constants.KEY_AVATAR, avatarUrl)
                .putString(Constants.KEY_REGISTRATION_DATE, registrationDateText)
                .apply();
    }

    private void updateProfileUI() {
        if (!viewsInitialized) {
            return;
        }

        runOnUiThread(() -> {
            if (userName != null) {
                userName.setText(username);
                userName.setTextSize(24);
                userName.setTextColor(getColor(R.color.text_primary));
            }
            if (userUsername != null) {
                userUsername.setText(handle);
                userUsername.setTextSize(16);
                userUsername.setTextColor(getColor(R.color.text_secondary));
            }
            if (userEmail != null) {
                userEmail.setText(email.isEmpty() ? "Не указан" : email);
            }
            if (userPhone != null) {
                userPhone.setText(phone.isEmpty() ? "Не указан" : phone);
            }
            if (registrationDate != null) {
                registrationDate.setText(registrationDateText);
            }
            if (profileAvatar != null) {
                loadAvatar(avatarUrl);
            }
        });
    }

    private void loadAvatar(String url) {
        if (profileAvatar == null) return;

        if (url != null && !url.isEmpty() && url.startsWith("http")) {
            Glide.with(this)
                    .load(url.trim())
                    .placeholder(R.drawable.bg_avatar_placeholder)
                    .error(R.drawable.bg_avatar_placeholder)
                    .circleCrop()
                    .into(profileAvatar);
        } else {
            profileAvatar.setImageResource(R.drawable.bg_avatar_placeholder);
        }
    }

    private void setupListeners() {
        backButton.setOnClickListener(v -> onBackPressed());
        moreOptionsButton.setOnClickListener(v -> showOptionsMenu());

        emailCard.setOnClickListener(v -> openEditProfile());
        phoneCard.setOnClickListener(v -> openEditProfile());

        registrationCard.setOnClickListener(v ->
                Toast.makeText(this, "Дата регистрации: " + registrationDateText, Toast.LENGTH_SHORT).show()
        );

        editProfileButton.setOnClickListener(v -> openEditProfile());
    }

    private void openEditProfile() {
        Intent intent = new Intent(this, EditProfileActivity.class);
        intent.putExtra(Constants.KEY_DISPLAY_NAME, username);
        intent.putExtra(Constants.KEY_PHONE, phone);
        intent.putExtra(Constants.KEY_EMAIL, email);
        intent.putExtra(Constants.KEY_AVATAR, avatarUrl);

        startActivityForResult(intent, Constants.REQUEST_EDIT_PROFILE);
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    private void showOptionsMenu() {
        String[] items = {"Настройки", "Помощь", "Выйти"};
        new AlertDialog.Builder(this)
                .setTitle("Меню")
                .setItems(items, (dialog, which) -> {
                    if (which == 2) showLogoutConfirmation();
                    else Toast.makeText(this, items[which], Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showLogoutConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Выйти из аккаунта?")
                .setMessage("Вы уверены?")
                .setPositiveButton("Выйти", (d, w) -> logoutAndGoToLogin())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void logoutAndGoToLogin() {
        RetrofitClient.clearTokens();
        getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE).edit().clear().apply();

        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == Constants.REQUEST_EDIT_PROFILE && resultCode == RESULT_OK && data != null) {
            String newDisplayName = data.getStringExtra(Constants.KEY_DISPLAY_NAME);
            String newPhone = data.getStringExtra(Constants.KEY_PHONE);
            String newAvatar = data.getStringExtra(Constants.KEY_AVATAR);
            String newEmail = data.getStringExtra(Constants.KEY_EMAIL);

            if (newDisplayName != null) {
                username = newDisplayName.startsWith("@") ? newDisplayName.substring(1) : newDisplayName;
                handle = newDisplayName.startsWith("@") ? newDisplayName : "@" + newDisplayName;
            }
            if (newPhone != null) phone = newPhone;
            if (newEmail != null) email = newEmail;

            String avatarToSave = newAvatar != null && !newAvatar.isEmpty() ? newAvatar : avatarUrl;

            getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE).edit()
                    .putString(Constants.KEY_USERNAME, username)
                    .putString(Constants.KEY_DISPLAY_NAME, username)
                    .putString(Constants.KEY_PHONE, phone)
                    .putString(Constants.KEY_EMAIL, email)
                    .putString(Constants.KEY_AVATAR, avatarToSave)
                    .apply();

            updateProfileUI();

            if (newAvatar != null && !newAvatar.isEmpty()) {
                loadAvatar(newAvatar);
            }

            Toast.makeText(this, "✅ Данные обновлены", Toast.LENGTH_SHORT).show();

            isProfileJustUpdated = true;
        }
    }

    public void forceRefreshProfile() {
        isProfileJustUpdated = false;
        fetchUserProfileFromServer();
    }
}