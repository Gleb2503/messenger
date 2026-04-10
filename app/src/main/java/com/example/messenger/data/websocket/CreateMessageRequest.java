package com.example.messenger.data.websocket;

public class CreateMessageRequest {
    public Long chatId;
    public String content;
    public String messageType = "text";

    public CreateMessageRequest() {}
}