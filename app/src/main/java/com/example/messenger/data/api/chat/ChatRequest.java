package com.example.messenger.data.api.chat;

import com.google.gson.annotations.SerializedName;

public class ChatRequest {

    @SerializedName("name")
    private final String name;

    @SerializedName("type")
    private final String type;

    @SerializedName("avatarUrl")
    private final String avatarUrl;

    public ChatRequest(String name, String type, String avatarUrl) {
        this.name = name;
        this.type = type;
        this.avatarUrl = avatarUrl;
    }

    public String getName() { return name; }
    public String getType() { return type; }
    public String getAvatarUrl() { return avatarUrl; }
}