package com.example.messenger.data.websocket;

import java.util.Date;

public class TypingNotification {
    public Long chatId;
    public Long userId;
    public String username;
    public boolean isTyping;
    public Date timestamp;

    public TypingNotification() {}
}