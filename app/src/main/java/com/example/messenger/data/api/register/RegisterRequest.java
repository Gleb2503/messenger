package com.example.messenger.data.api.register;

import com.google.gson.annotations.SerializedName;

public class RegisterRequest {

    @SerializedName("username")
    private final String username;

    @SerializedName("email")
    private final String email;

    @SerializedName("phone")
    private final String phone;

    @SerializedName("password")
    private final String password;


    public RegisterRequest(String username, String email, String phone, String password) {
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }


    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }  // ← Новый геттер
    public String getPassword() { return password; }
}