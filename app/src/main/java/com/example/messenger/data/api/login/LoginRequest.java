package com.example.messenger.data.api.login;

import com.google.gson.annotations.SerializedName;

public class LoginRequest {

    @SerializedName("phone")
    private final String phone;

    @SerializedName("password")
    private final String password;


    public LoginRequest(String phone, String password) {
        this.phone = phone;
        this.password = password;
    }


    public String getPhone() {
        return phone;
    }

    public String getPassword() {
        return password;
    }
}