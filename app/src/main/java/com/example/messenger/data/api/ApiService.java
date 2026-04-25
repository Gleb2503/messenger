package com.example.messenger.data.api;

import com.example.messenger.data.api.attachment.AttachmentResponse;
import com.example.messenger.data.api.login.LoginRequest;
import com.example.messenger.data.api.login.LoginResponse;
import com.example.messenger.data.api.register.RegisterRequest;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Part;
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
    @POST("auth/refresh")
    Call<Map<String, Object>> refreshToken(@Body Map<String, String> request);
    @Multipart
    @POST("attachments")
    Call<AttachmentResponse> uploadAttachment(
            @Part MultipartBody.Part file,
            @Part("messageId") Long messageId,
            @Part("fileName") String fileName,
            @Part("fileSize") Long fileSize,
            @Part("fileType") String fileType,
            @Part("thumbnailUrl") String thumbnailUrl
    );


    @GET("attachments/{id}/download")
    Call<okhttp3.ResponseBody> downloadAttachment(@Path("id") Long id);

}