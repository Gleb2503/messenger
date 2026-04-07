package com.example.messenger.data.api;

import com.example.messenger.data.api.login.LoginRequest;
import com.example.messenger.data.api.login.LoginResponse;
import com.example.messenger.data.api.register.RegisterRequest;
import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {


    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<Void> register(@Body RegisterRequest request);

    @POST("auth/refresh")
    Call<LoginResponse> refreshToken(@Query("refreshToken") String refreshToken);


    @GET("chats")
    Call<List<Map<String, Object>>> getChats();

    @POST("chats")
    Call<Map<String, Object>> createChat(@Body Map<String, String> request);

    @GET("chats/{id}")
    Call<Map<String, Object>> getChatById(@Path("id") Long id);

    @PUT("chats/{id}")
    Call<Map<String, Object>> updateChat(@Path("id") Long id, @Body Map<String, String> request);

    @DELETE("chats/{id}")
    Call<Void> deleteChat(@Path("id") Long id);
}