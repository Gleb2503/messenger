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
import retrofit2.http.PUT;
import retrofit2.http.Part;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    @POST("auth/login")
    Call<LoginResponse> login(@Body LoginRequest request);

    @POST("auth/register")
    Call<Void> register(@Body RegisterRequest request);

    @GET("chats")
    Call<List<Map<String, Object>>> getChats();

    @POST("chats")  
    Call<Map<String, Object>> createChat(@Body Map<String, String> request);

    @POST("chats") 
    Call<Map<String, Object>> createGroupChat(@Body Map<String, Object> request);

    @PATCH("chats/{id}/pin")
    Call<Map<String, Object>> togglePin(@Path("id") Long chatId, @Body Map<String, Boolean> request);

    @DELETE("chats/{id}")
    Call<Void> deleteChat(@Path("id") Long chatId);

    @GET("chats/{chatId}")
    Call<Map<String, Object>> getChatInfo(@Path("chatId") Long chatId);

    @GET("chat-members/chat/{chatId}")
    Call<List<Map<String, Object>>> getChatParticipants(@Path("chatId") Long chatId);

    @POST("chats/{chatId}/participants")
    Call<Map<String, Object>> addParticipant(@Path("chatId") Long chatId, @Body Map<String, Long> request);

    @DELETE("chats/{chatId}/participants/{userId}")
    Call<Void> removeParticipant(@Path("chatId") Long chatId, @Path("userId") Long userId);

    @PUT("chats/{chatId}/name")
    Call<Map<String, Object>> updateGroupName(@Path("chatId") Long chatId, @Body Map<String, String> request);

    @PUT("chats/{chatId}/avatar")
    Call<Map<String, Object>> updateGroupAvatar(@Path("chatId") Long chatId, @Body Map<String, String> request);

    @GET("messages/chat/{chatId}")
    Call<List<Map<String, Object>>> getMessages(
            @Path("chatId") Long chatId,
            @Query("beforeId") Long beforeId,
            @Query("size") Integer size
    );

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

    @GET("/api/users/{userId}")
    Call<Map<String, Object>> getUserProfile(@Path("userId") long userId);

    @PUT("/api/users/{userId}")
    Call<Map<String, Object>> updateUser(
            @Path("userId") Long userId,
            @Body Map<String, Object> request
    );

    @GET("/api/contacts/user/{userId}")
    Call<List<Map<String, Object>>> getUserContacts(@Path("userId") Long userId);

    @POST("/api/contacts")
    Call<Map<String, Object>> addContact(@Body Map<String, Object> request);

    @PUT("/api/contacts/{id}/block")
    Call<Map<String, Object>> blockContact(@Path("id") Long id);

    @PUT("/api/contacts/{id}/unblock")
    Call<Map<String, Object>> unblockContact(@Path("id") Long id);

    @DELETE("/api/contacts/{id}")
    Call<Void> deleteContact(@Path("id") Long id);

    @GET("/api/users/search")
    Call<List<Map<String, Object>>> searchUsersByPhone(@Query("phone") String phone);

    @Multipart
    @POST("/api/chats/group/avatar")
    Call<Map<String, Object>> uploadGroupAvatar(@Part MultipartBody.Part file);

    @POST("/api/chat-members")
    Call<Map<String, Object>> addParticipantToChat(@Body Map<String, Object> request);
}