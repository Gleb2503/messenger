package com.example.messenger.data.websocket;

import java.util.Date;

public class MessageReadRequest {
    public Long messageId;
    public Long userId;
    public Date readAt;

    public MessageReadRequest() {}
}