package com.example.messenger.data.api;

import android.content.Context;
import android.content.SharedPreferences;
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


    public static void init(Context context) {
        prefs = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
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


                            String token = prefs != null ?
                                    prefs.getString(Constants.KEY_ACCESS_TOKEN, null) : null;

                            Request.Builder builder = original.newBuilder()
                                    .method(original.method(), original.body());


                            if (token != null && !token.isEmpty()) {
                                builder.header("Authorization", "Bearer " + token);
                            }

                            builder.header("Content-Type", "application/json");

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
                    .apply();
        }
    }


    public static void saveTokens(String accessToken, String refreshToken, Long userId, String username) {
        if (prefs != null) {
            prefs.edit()
                    .putString(Constants.KEY_ACCESS_TOKEN, accessToken)
                    .putString(Constants.KEY_REFRESH_TOKEN, refreshToken)
                    .putLong(Constants.KEY_USER_ID, userId)
                    .putString(Constants.KEY_USERNAME, username)
                    .apply();
        }
    }
}