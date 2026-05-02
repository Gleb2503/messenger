package com.example.messenger.data.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.messenger.login.LoginActivity;
import com.example.messenger.util.Constants;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import okhttp3.Cache;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static final String TAG = "RetrofitClient";
    private static final String BASE_URL = "http://178.212.12.112:8080/api/";
    private static Retrofit retrofit;
    private static Context instance;
    private static String cachedAccessToken;
    private static String cachedRefreshToken;
    private static long cachedUserId = -1;

    public static void init(Context context) {
        if (instance == null) {
            instance = context.getApplicationContext();
            loadTokensFromCache();
        }
    }

    public static ApiService getApiService() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(createOkHttpClient())
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    private static OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
                .cache(null)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
                .addInterceptor(chain -> {
                    Request original = chain.request();
                    Request request = original.newBuilder()
                            .header("Cache-Control", "no-cache, no-store, must-revalidate")
                            .header("Pragma", "no-cache")
                            .header("Expires", "0")
                            .build();
                    return chain.proceed(request);
                })
                .addInterceptor(new AuthInterceptor(instance))
                .build();
    }

    public static void setTokens(String accessToken, String refreshToken, long userId) {
        cachedAccessToken = accessToken;
        cachedRefreshToken = refreshToken;
        cachedUserId = userId;
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
                    .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
                    .putLong(Constants.KEY_USER_ID, userId)
                    .apply();
        }
    }

    public static void clearTokens() {
        Log.d(TAG, "Tokens cleared");
        cachedAccessToken = null;
        cachedRefreshToken = null;
        cachedUserId = -1;
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit()
                    .remove(Constants.KEY_ACCESS_TOKEN)
                    .remove(Constants.KEY_REFRESH_TOKEN)
                    .remove(Constants.KEY_USER_ID)
                    .remove(Constants.KEY_USERNAME)
                    .apply();
        }
    }

    public static String getToken() {
        if (cachedAccessToken != null) return cachedAccessToken;
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            return prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
        }
        return null;
    }

    public static String getRefreshToken() {
        if (cachedRefreshToken != null) return cachedRefreshToken;
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            return prefs.getString(Constants.KEY_REFRESH_TOKEN, null);
        }
        return null;
    }

    public static long getUserId() {
        if (cachedUserId > 0) return cachedUserId;
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            return prefs.getLong(Constants.KEY_USER_ID, -1);
        }
        return -1;
    }

    public static String getUsername() {
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            return prefs.getString(Constants.KEY_USERNAME, null);
        }
        return null;
    }

    private static void loadTokensFromCache() {
        if (instance != null) {
            SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            cachedAccessToken = prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
            cachedRefreshToken = prefs.getString(Constants.KEY_REFRESH_TOKEN, null);
            cachedUserId = prefs.getLong(Constants.KEY_USER_ID, -1);
        }
    }

    private static class AuthInterceptor implements Interceptor {
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
                if (response.code() == 401 || response.code() == 403) {
                    response.close();
                    Log.d(TAG, "Received " + response.code() + ". Attempting token refresh...");
                    if (tryRefreshToken()) {
                        String newToken = getAccessToken();
                        if (newToken != null) {
                            Log.d(TAG, "Token refreshed. Retrying request...");
                            Request retryRequest = originalRequest.newBuilder()
                                    .header("Authorization", "Bearer " + newToken)
                                    .build();
                            return chain.proceed(retryRequest);
                        }
                    }
                    Log.w(TAG, "Token refresh failed. Redirecting to login...");
                    clearTokensAndRedirect();
                    throw new IOException("Token expired/forbidden and refresh failed");
                }
                return response;
            }
            return chain.proceed(originalRequest);
        }

        private boolean tryRefreshToken() {
            try {
                if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    Thread.sleep(1000);
                    return getAccessToken() != null;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            try {
                String refreshToken = getRefreshToken();
                if (refreshToken == null || refreshToken.isEmpty()) {
                    return false;
                }
                Log.d(TAG, "Sending token refresh request...");
                Map<String, String> body = new HashMap<>();
                body.put("refreshToken", refreshToken);
                String refreshUrl = BASE_URL.endsWith("/") ? BASE_URL + "auth/refresh" : BASE_URL + "/auth/refresh";
                Request refreshRequest = new Request.Builder()
                        .url(refreshUrl)
                        .post(RequestBody.create(
                                gson.toJson(body),
                                MediaType.parse("application/json")
                        ))
                        .build();
                OkHttpClient refreshClient = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(10, TimeUnit.SECONDS)
                        .build();
                try (Response refreshResponse = refreshClient.newCall(refreshRequest).execute()) {
                    if (refreshResponse.isSuccessful() && refreshResponse.body() != null) {
                        String responseBody = refreshResponse.body().string();
                        Map<String, Object> result = gson.fromJson(responseBody, Map.class);
                        String newAccessToken = (String) result.get("token");
                        String newRefreshToken = (String) result.get("refreshToken");
                        if (newAccessToken != null && !newAccessToken.isEmpty()) {
                            setTokens(newAccessToken, newRefreshToken != null ? newRefreshToken : getRefreshToken(), getUserId());
                            Log.d(TAG, "Token successfully refreshed");
                            return true;
                        }
                    } else {
                        Log.w(TAG, "Token refresh error. Server code: " + refreshResponse.code());
                        if (refreshResponse.code() == 401 || refreshResponse.code() == 403) {
                            clearTokensAndRedirect();
                        }
                    }
                }
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Error during token refresh", e);
                return false;
            } finally {
                semaphore.release();
            }
        }

        private void clearTokensAndRedirect() {
            Log.d(TAG, "Clearing tokens and redirecting to login...");
            clearTokens();
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                if (context != null) {
                    Intent intent = new Intent(context, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    context.startActivity(intent);
                }
            });
        }

        private String getAccessToken() {
            if (cachedAccessToken != null) return cachedAccessToken;
            if (instance != null) {
                SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
                return prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
            }
            return null;
        }

        private String getRefreshToken() {
            if (cachedRefreshToken != null) return cachedRefreshToken;
            if (instance != null) {
                SharedPreferences prefs = instance.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
                return prefs.getString(Constants.KEY_REFRESH_TOKEN, null);
            }
            return null;
        }
    }
}