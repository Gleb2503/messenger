package com.example.messenger.data.websocket;

import com.google.gson.annotations.SerializedName;

public class CreateMessageRequest {

    public Long chatId;
    public String content;


    @SerializedName("messageType")
    public MessageType messageType = MessageType.TEXT;

    public CreateMessageRequest() {}


    public CreateMessageRequest(Long chatId, String content, MessageType type) {
        this.chatId = chatId;
        this.content = content;
        this.messageType = type != null ? type : MessageType.TEXT;
    }
}