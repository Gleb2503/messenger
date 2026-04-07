package com.example.messenger.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.messenger.data.api.login.LoginResponse;
import com.example.messenger.util.Constants;

public class AuthRepository {

    private final SharedPreferences prefs;

    public AuthRepository(Context context) {
        prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    public void saveTokens(LoginResponse response) {
        prefs.edit()
                .putString(Constants.KEY_ACCESS_TOKEN, response.getToken())
                .putString(Constants.KEY_REFRESH_TOKEN, response.getRefreshToken())
                .putLong(Constants.KEY_USER_ID, response.getUserId())
                .putString(Constants.KEY_USERNAME, response.getUsername())
                .apply();
    }

    public String getAccessToken() {
        return prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(Constants.KEY_REFRESH_TOKEN, null);
    }

    public long getUserId() {
        return prefs.getLong(Constants.KEY_USER_ID, -1);
    }

    public String getUsername() {
        return prefs.getString(Constants.KEY_USERNAME, null);
    }

    public boolean isLoggedIn() {
        return getAccessToken() != null && !getAccessToken().isEmpty();
    }

    public void logout() {
        prefs.edit().clear().apply();
    }
}