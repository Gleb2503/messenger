package com.example.messenger.register;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import com.example.messenger.R;
import com.example.messenger.data.api.ApiService;
import com.example.messenger.data.api.register.RegisterRequest;
import com.example.messenger.data.api.RetrofitClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText usernameInput, emailInput, phoneInput, passwordInput, confirmPasswordInput;

    private TextView usernameHintText, passwordHintText;

    private ImageView togglePassword, toggleConfirm;
    private View registerButton, loginButton;
    private ProgressBar progressBar;

    private ApiService apiService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        apiService = RetrofitClient.getApiService();
        initViews();
    }

    private void initViews() {
        usernameInput = findViewById(R.id.usernameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput); // Инициализация нового поля
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);


        usernameHintText = findViewById(R.id.usernameHintText);
        passwordHintText = findViewById(R.id.passwordHintText);

        togglePassword = findViewById(R.id.togglePasswordVisibility);
        toggleConfirm = findViewById(R.id.toggleConfirmVisibility);

        registerButton = findViewById(R.id.registerButton);
        loginButton = findViewById(R.id.loginButton);
        progressBar = findViewById(R.id.progressBar);

        togglePassword.setOnClickListener(v -> togglePasswordVisibility(passwordInput, togglePassword));
        toggleConfirm.setOnClickListener(v -> togglePasswordVisibility(confirmPasswordInput, toggleConfirm));

        registerButton.setOnClickListener(v -> performRegister());

        loginButton.setOnClickListener(v -> {
            finish(); // Go back to Login Activity
        });

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        setupFocusClear(usernameInput, usernameHintText, "3-50 символов");
        setupFocusClear(passwordInput, passwordHintText, "Минимум 6 символов");
        setupFocusClear(phoneInput, null, ""); // Для телефона нет подсказки снизу в макете, но можно добавить
    }

    private void togglePasswordVisibility(EditText input, ImageView icon) {
        if (input.getInputType() == (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            icon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            icon.setImageResource(R.drawable.ic_eye);
        }
        input.setSelection(input.getText().length());
    }

    private void setupFocusClear(EditText input, TextView text, String defaultHint) {
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {

                input.setBackgroundResource(R.drawable.bg_input_field);
                if (text != null) {
                    text.setTextColor(getColor(R.color.text_secondary));
                    text.setText(defaultHint);
                }
            }
        });
    }

    private void performRegister() {
        String username = usernameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String phone = phoneInput.getText().toString().trim(); // Получаем телефон
        String password = passwordInput.getText().toString();
        String confirm = confirmPasswordInput.getText().toString();

        if (!validate(username, email, phone, password, confirm)) return;

        setLoading(true);


        String normalizedPhone = normalizePhone(phone);

        RegisterRequest request = new RegisterRequest(username, email, normalizedPhone, password);

        apiService.register(request).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    Toast.makeText(RegisterActivity.this, "Успешно! Теперь войдите.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Ошибка";
                        Toast.makeText(RegisterActivity.this, "Ошибка: " + errorBody, Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(RegisterActivity.this, "Ошибка регистрации", Toast.LENGTH_LONG).show();
                    }
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                setLoading(false);
                Toast.makeText(RegisterActivity.this, "Ошибка сети: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean validate(String username, String email, String phone, String password, String confirm) {
        boolean isValid = true;

        if (username.isEmpty()) {
            setError(usernameInput, usernameHintText, "Введите имя", true);
            isValid = false;
        } else if (username.length() < 3 || username.length() > 50) {
            setError(usernameInput, usernameHintText, "Username от 3 до 50 символов", true);
            isValid = false;
        } else {
            clearError(usernameInput, usernameHintText, "3-50 символов");
        }

        if (email.isEmpty()) {
            setError(emailInput, null, "Введите email", true);
            isValid = false;
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            setError(emailInput, null, "Некорректный email", true);
            isValid = false;
        } else {
            clearError(emailInput, null, "");
        }

        if (phone.isEmpty()) {
            setError(phoneInput, null, "Введите номер телефона", true);
            isValid = false;
        } else if (phone.length() < 10) {
            setError(phoneInput, null, "Слишком короткий номер", true);
            isValid = false;
        } else {
            clearError(phoneInput, null, "");
        }

        if (password.isEmpty()) {
            setError(passwordInput, passwordHintText, "Введите пароль", true);
            isValid = false;
        } else if (password.length() < 6) {
            setError(passwordInput, passwordHintText, "Слишком короткий пароль", true);
            isValid = false;
        } else {
            clearError(passwordInput, passwordHintText, "Минимум 6 символов");
        }


        if (!confirm.equals(password)) {
            setError(confirmPasswordInput, null, "Пароли не совпадают", true);
            isValid = false;
        } else {
            clearError(confirmPasswordInput, null, "");
        }

        return isValid;
    }

    private void setError(EditText input, TextView text, String msg, boolean isRed) {
        input.setBackgroundResource(R.drawable.bg_input_field_error);
        if (text != null) {
            text.setText(msg);
            text.setTextColor(getColor(R.color.error)); // Используем цвет из resources
            text.setVisibility(View.VISIBLE);
        }
    }

    private void clearError(EditText input, TextView text, String defaultMsg) {
        input.setBackgroundResource(R.drawable.bg_input_field);
        if (text != null) {
            text.setText(defaultMsg);
            text.setTextColor(getColor(R.color.text_secondary)); // Используем цвет из resources
            text.setVisibility(View.VISIBLE);
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
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        registerButton.setEnabled(!isLoading);

        if (isLoading) {
            ((android.widget.Button) registerButton).setText("");
            registerButton.setBackgroundResource(R.drawable.bg_button_primary_disabled);
        } else {
            ((android.widget.Button) registerButton).setText("Зарегистрироваться");
            registerButton.setBackgroundResource(R.drawable.bg_button_primary);
        }

        usernameInput.setEnabled(!isLoading);
        emailInput.setEnabled(!isLoading);
        phoneInput.setEnabled(!isLoading);
        passwordInput.setEnabled(!isLoading);
        confirmPasswordInput.setEnabled(!isLoading);
    }
}