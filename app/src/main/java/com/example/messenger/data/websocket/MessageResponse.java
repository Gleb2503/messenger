package com.example.messenger.data.websocket;

public class MessageResponse {
    public Long id;
    public Long chatId;
    public Long senderId;
    public String text;
    public String status;
    public String createdAt;

    public MessageResponse() {}
}