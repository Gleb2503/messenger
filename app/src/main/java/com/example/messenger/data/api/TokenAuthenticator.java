//package com.example.messenger.data.api;
//
//import android.content.Context;
//import android.content.Intent;
//import android.util.Log;
//
//import androidx.annotation.NonNull;
//import androidx.annotation.Nullable;
//
//import com.example.messenger.data.api.login.LoginResponse;
//import com.google.gson.Gson;
//
//import java.io.IOException;
//
//import okhttp3.Authenticator;
//import okhttp3.MediaType;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.RequestBody;
//import okhttp3.Response;
//import okhttp3.Route;
//
//public class TokenAuthenticator implements Authenticator {
//
//    private static final String TAG = "TokenAuthenticator";
//    private final Context context;
//    private static final Object lock = new Object();
//    private static boolean isRefreshing = false;
//
//    public TokenAuthenticator(Context context) {
//        this.context = context.getApplicationContext();
//    }
//
//    @Nullable
//    @Override
//    public Request authenticate(@Nullable Route route, @NonNull Response response) throws IOException {
//
//        if (response.request().header("Authorization") == null) {
//            return null;
//        }
//
//        synchronized (lock) {
//            if (isRefreshing) {
//                Log.d(TAG, "Refresh already in progress, skipping duplicate request");
//                return null;
//            }
//            isRefreshing = true;
//        }
//
//        try {
//            String refreshToken = RetrofitClient.getRefreshToken();
//            if (refreshToken == null || refreshToken.isEmpty()) {
//                Log.w(TAG, "No refresh token available");
//                forceLogout();
//                return null;
//            }
//            OkHttpClient refreshClient = new OkHttpClient.Builder().build();
//            Request refreshRequest = new Request.Builder()
//                    .url(RetrofitClient.getBaseUrl() + "auth/refresh")
//                    .post(RequestBody.create("{\"refreshToken\":\"" + refreshToken + "\"}", MediaType.get("application/json")))
//                    .build();
//
//            try (Response refreshResponse = refreshClient.newCall(refreshRequest).execute()) {
//                if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
//                    String body = refreshResponse.body().string();
//                    LoginResponse newTokens = new Gson().fromJson(body, LoginResponse.class);
//
//
//                    RetrofitClient.setTokens(newTokens.getToken(), newTokens.getRefreshToken(), newTokens.getUserId());
//                    Log.d(TAG, "✅ Token refreshed successfully");
//
//
//                    return response.request().newBuilder()
//                            .header("Authorization", "Bearer " + newTokens.getToken())
//                            .build();
//                } else {
//                    Log.w(TAG, "❌ Refresh token expired or invalid");
//                    forceLogout();
//                    return null;
//                }
//            }
//        } catch (Exception e) {
//            Log.e(TAG, "❌ Error during token refresh", e);
//            forceLogout();
//            return null;
//        } finally {
//            synchronized (lock) {
//                isRefreshing = false;
//            }
//        }
//    }
//
//    private void forceLogout() {
//        RetrofitClient.clearTokens();
//        Intent intent = new Intent("com.example.messenger.ACTION_LOGOUT");
//        context.sendBroadcast(intent);
//    }
//}