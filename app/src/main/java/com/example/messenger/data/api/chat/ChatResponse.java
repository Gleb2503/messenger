package com.example.messenger.data.api.chat;

import com.google.gson.annotations.SerializedName;
import java.time.LocalDateTime;

public class ChatResponse {

    @SerializedName("id")
    private Long id;

    @SerializedName("name")
    private String name;

    @SerializedName("type")
    private String type;

    @SerializedName("avatarUrl")
    private String avatarUrl;

    @SerializedName("lastMessageTime")
    private String lastMessageTime;

    @SerializedName("createdBy")
    private CreatedByInfo createdBy;


    public Long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getAvatarUrl() { return avatarUrl; }
    public String getLastMessageTime() { return lastMessageTime; }
    public CreatedByInfo getCreatedBy() { return createdBy; }


    public static class CreatedByInfo {
        @SerializedName("id")
        private Long id;
        @SerializedName("username")
        private String username;
        @SerializedName("email")
        private String email;

        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getEmail() { return email; }
    }
}