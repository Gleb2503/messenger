package com.example.messenger.data.api.login;

import com.google.gson.annotations.SerializedName;

public class LoginResponse {
    @SerializedName("userId")
    private final long userId;
    @SerializedName("username")
    private final String username;
    @SerializedName("token")
    private final String token;
    @SerializedName("refreshToken")
    private final String refreshToken;

    public LoginResponse(long userId, String username, String token, String refreshToken) {
        this.userId = userId;
        this.username = username;
        this.token = token;
        this.refreshToken = refreshToken;
    }

    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getToken() { return token; }
    public String getRefreshToken() { return refreshToken; }
}