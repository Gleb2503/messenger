package com.example.messenger.data.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.messenger.LoginActivity;
import com.example.messenger.util.Constants;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthInterceptor implements Interceptor {

    private static final String TAG = "AuthInterceptor";
    private static final Semaphore semaphore = new Semaphore(1);

    private final Context context;
    private final Gson gson = new Gson();

    public AuthInterceptor(Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();


        String url = originalRequest.url().toString();
        if (url.contains("/auth/login") || url.contains("/auth/register") || url.contains("/auth/refresh")) {
            return chain.proceed(originalRequest);
        }

        String accessToken = getAccessToken();
        if (accessToken != null && !accessToken.isEmpty()) {
            Request authorizedRequest = originalRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .build();

            Response response = chain.proceed(authorizedRequest);

            if (response.code() == 401) {
                response.close();

                if (tryRefreshToken()) {
                    String newToken = getAccessToken();
                    Request retryRequest = originalRequest.newBuilder()
                            .header("Authorization", "Bearer " + newToken)
                            .build();
                    return chain.proceed(retryRequest);
                } else {
                    clearTokensAndRedirect();
                    throw new IOException("Token expired and refresh failed");
                }
            }
            return response;
        }

        return chain.proceed(originalRequest);
    }


    private boolean tryRefreshToken() {
        try {
            if (!semaphore.tryAcquire()) {
                semaphore.acquire();
                return getAccessToken() != null;
            }

            String refreshToken = getRefreshToken();
            if (refreshToken == null || refreshToken.isEmpty()) {
                return false;
            }

            Log.d(TAG, "🔄 Refreshing token...");

            Map<String, String> body = new HashMap<>();
            body.put("refreshToken", refreshToken);

            Request refreshRequest = new Request.Builder()
                    .url("http://178.212.12.112:8080/api/auth/refresh")
                    .post(RequestBody.create(
                            gson.toJson(body),
                            MediaType.parse("application/json")
                    ))
                    .build();

            okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder()
                    .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                    .build();

            Response refreshResponse = client.newCall(refreshRequest).execute();

            if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                String responseBody = refreshResponse.body().string();
                Map<String, Object> result = gson.fromJson(responseBody, Map.class);

                String newAccessToken = (String) result.get("token");
                String newRefreshToken = (String) result.get("refreshToken");

                if (newAccessToken != null) {
                    saveTokens(newAccessToken, newRefreshToken);
                    Log.d(TAG, "✅ Token refreshed successfully");
                    return true;
                }
            }

            Log.w(TAG, "❌ Token refresh failed: " + refreshResponse.code());
            return false;

        } catch (Exception e) {
            Log.e(TAG, "Error refreshing token", e);
            return false;
        } finally {
            semaphore.release();
        }
    }


    private void clearTokensAndRedirect() {
        Log.d(TAG, "🚪 Clearing tokens and redirecting to login");

        clearTokens();


        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent(context, LoginActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            context.startActivity(intent);
        });
    }


    private String getAccessToken() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
    }

    private String getRefreshToken() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(Constants.KEY_REFRESH_TOKEN, null);
    }

    private void saveTokens(String accessToken, String refreshToken) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(Constants.KEY_ACCESS_TOKEN, accessToken);
        if (refreshToken != null) {
            editor.putString(Constants.KEY_REFRESH_TOKEN, refreshToken);
        }
        editor.apply();
    }

    private void clearTokens() {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .remove(Constants.KEY_ACCESS_TOKEN)
                .remove(Constants.KEY_REFRESH_TOKEN)
                .apply();
    }
}