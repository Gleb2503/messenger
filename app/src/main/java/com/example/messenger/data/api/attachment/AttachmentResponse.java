package com.example.messenger.data.api.attachment;

import com.example.messenger.data.websocket.MessageType;
import com.google.gson.annotations.SerializedName;

public class AttachmentResponse {

    @SerializedName("id")
    public Long id;

    @SerializedName("fileUrl")
    public String fileUrl;

    @SerializedName("fileName")
    public String fileName;

    @SerializedName("fileSize")
    public Long fileSize;

    @SerializedName("fileType")
    public String fileType;

    @SerializedName("thumbnailUrl")
    public String thumbnailUrl;

    @SerializedName("createdAt")
    public String createdAt;

    @SerializedName("message")
    public MessageInfo message;

    public static class MessageInfo {
        @SerializedName("id")
        public Long id;

        @SerializedName("content")
        public String content;

        @SerializedName("messageType")
        public String messageType;

        @SerializedName("sender")
        public SenderInfo sender;
    }

    public static class SenderInfo {
        @SerializedName("id")
        public Long id;

        @SerializedName("username")
        public String username;
    }

    public MessageType getMessageType() {
        if (message != null && message.messageType != null) {
            return MessageType.fromString(message.messageType);
        }
        return MessageType.TEXT;
    }
}