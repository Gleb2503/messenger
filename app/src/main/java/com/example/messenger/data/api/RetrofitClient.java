package com.example.messenger.data.api;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.example.messenger.util.Constants;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit;
    private static SharedPreferences prefs;
    private static Context context;

    public static void init(Context ctx) {
        context = ctx.getApplicationContext();
        prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
        Log.d("RetrofitClient", "✅ Initialized: PREF_NAME=" + Constants.PREF_NAME);
    }

    public static ApiService getApiService() {
        if (retrofit == null) {
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(Constants.CONNECT_TIMEOUT, TimeUnit.SECONDS)
                    .readTimeout(Constants.READ_TIMEOUT, TimeUnit.SECONDS)
                    .writeTimeout(Constants.WRITE_TIMEOUT, TimeUnit.SECONDS)
                    .addInterceptor(new Interceptor() {
                        @Override
                        public Response intercept(Chain chain) throws IOException {
                            Request original = chain.request();
                            String token = getToken();

                            Request.Builder builder = original.newBuilder()
                                    .method(original.method(), original.body())
                                    .header("Content-Type", "application/json");

                            if (token != null && !token.isEmpty()) {
                                builder.header("Authorization", "Bearer " + token);
                            }
                            return chain.proceed(builder.build());
                        }
                    })
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }

    public static void clearTokens() {
        if (prefs != null) {
            prefs.edit()
                    .remove(Constants.KEY_ACCESS_TOKEN)
                    .remove(Constants.KEY_REFRESH_TOKEN)
                    .remove(Constants.KEY_USER_ID)
                    .remove(Constants.KEY_USERNAME)
                    .apply();
            Log.d("RetrofitClient", "🗑️ Tokens cleared");
        }
    }

    public static void saveTokens(String accessToken, String refreshToken, long userId, String username) {
        if (prefs != null) {
            prefs.edit()
                    .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
                    .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
                    .putLong(Constants.KEY_USER_ID, userId)
                    .putString(Constants.KEY_USERNAME, username)
                    .apply();
            Log.d("RetrofitClient", "✅ Saved: userId=" + userId + ", username=" + username);
        }
    }

    public static String getToken() {
        if (prefs == null) return null;
        return prefs.getString(Constants.KEY_ACCESS_TOKEN, null);
    }


    public static long getUserId() {
        if (prefs == null) {
            Log.e("RetrofitClient", "❌ prefs is null");
            return -1;
        }
        long userId = prefs.getLong(Constants.KEY_USER_ID, -1);  // 🔥 Default = -1
        Log.d("RetrofitClient", "👤 getUserId() = " + userId);
        return userId;
    }

    public static String getUsername() {
        if (prefs == null) return "user";
        return prefs.getString(Constants.KEY_USERNAME, "user");
    }

    public static boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }
}