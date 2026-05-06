package com.example.messenger.message;

import android.net.Uri;
import com.example.messenger.data.websocket.MessageType;

public class MessageItem {

    public static final int TYPE_DATE = 3;
    public static final int TYPE_HEADER = 4;
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;

    public static final int STATUS_SENT = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_READ = 2;
    public static final int STATUS_FAILED = 3;
    public static final int STATUS_SENDING = 4;

    private long id;
    private String text;
    private String time;
    private int status;
    private int type;
    private long senderId;

    private MessageType messageType;
    private String imageUrl;
    private String fileName;
    private long fileSize;
    private int uploadProgress;

    private Uri localImageUri;
    private String createdAt;


    private String senderName;
    private String senderAvatarUrl;

    public MessageItem(long id, String text, String time, int status, int type, long senderId) {
        this(id, text, time, status, type, senderId, MessageType.TEXT, null);
    }

    public MessageItem(long id, String text, String time, int status, int type,
                       long senderId, MessageType messageType, Uri localImageUri) {
        this.id = id;
        this.text = text;
        this.time = time;
        this.status = status;
        this.type = type;
        this.senderId = senderId;
        this.messageType = messageType != null ? messageType : MessageType.TEXT;
        this.localImageUri = localImageUri;
        this.uploadProgress = 0;
        this.senderName = null;
        this.senderAvatarUrl = null;
    }
    public long getId() { return id; }
    public String getText() { return text; }
    public String getTime() { return time; }
    public int getStatus() { return status; }
    public int getType() { return type; }
    public long getSenderId() { return senderId; }
    public MessageType getMessageType() { return messageType; }
    public Uri getLocalImageUri() { return localImageUri; }
    public String getImageUrl() { return imageUrl; }
    public int getUploadProgress() { return uploadProgress; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getCreatedAt() { return createdAt; }


    public String getSenderName() { return senderName; }
    public String getSenderAvatarUrl() { return senderAvatarUrl; }


    public void setId(long id) { this.id = id; }
    public void setText(String text) { this.text = text; }
    public void setTime(String time) { this.time = time; }
    public void setStatus(int status) { this.status = status; }
    public void setType(int type) { this.type = type; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
    public void setMessageType(MessageType messageType) { this.messageType = messageType; }
    public void setLocalImageUri(Uri uri) { this.localImageUri = uri; }
    public void setImageUrl(String url) { this.imageUrl = url; }
    public void setUploadProgress(int progress) { this.uploadProgress = progress; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }


    public void setSenderName(String senderName) { this.senderName = senderName; }
    public void setSenderAvatarUrl(String senderAvatarUrl) { this.senderAvatarUrl = senderAvatarUrl; }
}