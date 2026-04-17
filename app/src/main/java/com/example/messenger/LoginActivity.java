package com.example.messenger;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.login.LoginResponse;
import com.example.messenger.data.api.login.LoginRequest;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.util.Constants;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText phoneInput, passwordInput;
    private TextView phoneErrorText, passwordErrorText, forgotPasswordText, registerButton;
    private ImageView togglePasswordVisibility;
    private ProgressBar progressBar;
    private View loginButton;

    private ApiService apiService;
    private SharedPreferences sharedPreferences;
    private boolean isPasswordVisible = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        apiService = RetrofitClient.getApiService();
        sharedPreferences = getSharedPreferences(Constants.PREF_NAME, MODE_PRIVATE);

        initViews();

        if (isLoggedIn()) {
            navigateToMain();
        }
    }

    private void initViews() {
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        phoneErrorText = findViewById(R.id.phoneErrorText);
        passwordErrorText = findViewById(R.id.passwordErrorText);
        togglePasswordVisibility = findViewById(R.id.togglePasswordVisibility);
        progressBar = findViewById(R.id.progressBar);
        loginButton = findViewById(R.id.loginButton);
        forgotPasswordText = findViewById(R.id.forgotPasswordText);
        registerButton = findViewById(R.id.registerButton);


        togglePasswordVisibility.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                togglePasswordVisibility.setImageResource(android.R.drawable.ic_menu_close_clear_cancel); // Или иконка перечеркнутого глаза
            } else {
                passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                togglePasswordVisibility.setImageResource(R.drawable.ic_eye);
            }
            passwordInput.setSelection(passwordInput.getText().length());
        });


        phoneInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) clearError(phoneInput, phoneErrorText);
        });
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) clearError(passwordInput, passwordErrorText);
        });


        loginButton.setOnClickListener(v -> performLogin());

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });

        forgotPasswordText.setOnClickListener(v ->
                Toast.makeText(this, "Функция в разработке", Toast.LENGTH_SHORT).show());
    }

    private void performLogin() {
        String rawPhone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (!validateInputs(rawPhone, password)) return;

        setLoading(true);
        String normalizedPhone = normalizePhone(rawPhone);
        LoginRequest request = new LoginRequest(normalizedPhone, password);

        apiService.login(request).enqueue(new Callback<LoginResponse>() {

            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    saveUserData(response.body());


                    UserStatusManager statusManager =
                            ((MessengerApplication) getApplication()).getStatusManager();
                    statusManager.updateUserId(response.body().getUserId());

                    navigateToMain();
                } else {
                    if (response.code() == 401 || response.code() == 404) {
                        showError(passwordInput, passwordErrorText, "Неверный номер или пароль");
                    } else {
                        Toast.makeText(LoginActivity.this, "Ошибка сервера", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this, "Нет соединения с интернетом", Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validateInputs(String phone, String password) {
        boolean isValid = true;

        if (phone.isEmpty()) {
            showError(phoneInput, phoneErrorText, "Введите номер телефона");
            isValid = false;
        } else if (phone.length() < 10) {
            showError(phoneInput, phoneErrorText, "Слишком короткий номер");
            isValid = false;
        }

        if (password.isEmpty()) {
            showError(passwordInput, passwordErrorText, "Введите пароль");
            isValid = false;
        } else if (password.length() < 6) {
            showError(passwordInput, passwordErrorText, "Минимум 6 символов");
            isValid = false;
        }

        return isValid;
    }


    private void showError(EditText editText, TextView errorText, String message) {
        editText.setBackgroundResource(R.drawable.bg_input_field_error);
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }

    private void clearError(EditText editText, TextView errorText) {
        editText.setBackgroundResource(R.drawable.bg_input_field);
        errorText.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        loginButton.setEnabled(!loading);
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);


        if (loading) {
            ((android.widget.Button) loginButton).setText("");
        } else {
            ((android.widget.Button) loginButton).setText("Войти");
        }

        phoneInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
    }

    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^\\d+]", "");
        if (cleaned.startsWith("8") && cleaned.length() == 11) cleaned = "+7" + cleaned.substring(1);
        else if (cleaned.startsWith("7") && cleaned.length() == 11) cleaned = "+7" + cleaned.substring(1);
        else if (!cleaned.startsWith("+") && cleaned.length() == 11) cleaned = "+" + cleaned;
        return cleaned;
    }

    private void saveUserData(LoginResponse response) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(Constants.KEY_ACCESS_TOKEN, response.getToken());
        editor.putString(Constants.KEY_REFRESH_TOKEN, response.getRefreshToken());
        editor.putLong(Constants.KEY_USER_ID, response.getUserId());
        editor.putString(Constants.KEY_USERNAME, response.getUsername());
        editor.apply();
    }

    private boolean isLoggedIn() {
        String token = sharedPreferences.getString(Constants.KEY_ACCESS_TOKEN, null);
        return token != null && !token.isEmpty();
    }

    private void navigateToMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}