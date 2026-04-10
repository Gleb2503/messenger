package com.example.messenger.data.api;

import com.example.messenger.data.api.login.LoginRequest;
import com.example.messenger.data.api.login.LoginResponse;
import com.example.messenger.data.api.register.RegisterRequest;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<Void> register(@Body RegisterRequest request);

    @GET("chats")
    Call<List<Map<String, Object>>> getChats();

    @POST("chats")
    Call<Map<String, Object>> createChat(@Body Map<String, String> request);

    @PATCH("chats/{id}/pin")
    Call<Map<String, Object>> togglePin(@Path("id") Long chatId, @Body Map<String, Boolean> request);

    @DELETE("chats/{id}")
    Call<Void> deleteChat(@Path("id") Long chatId);


    @GET("chats/{chatId}/messages")
    Call<List<Map<String, Object>>> getMessages(@Path("chatId") Long chatId);

    @POST("chats/{chatId}/messages")
    Call<Map<String, Object>> sendMessage(@Path("chatId") Long chatId, @Body Map<String, Object> request);


    @POST("message-reads")
    Call<Map<String, Object>> markAsRead(@Body Map<String, Long> request);
}