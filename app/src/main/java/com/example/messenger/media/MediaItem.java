package com.example.messenger.media;

import android.os.Parcel;
import android.os.Parcelable;
import com.example.messenger.data.websocket.MessageType;

public class MediaItem implements Parcelable {
    private final String url;
    private final String fileName;
    private final long fileSize;
    private final String createdAt;
    private final long messageId;
    private final MessageType type;

    public MediaItem(String url, String fileName, long fileSize,
                     String createdAt, long messageId, MessageType type) {
        this.url = url;
        this.fileName = fileName;
        this.fileSize = fileSize;
        this.createdAt = createdAt;
        this.messageId = messageId;
        this.type = type;
    }

    protected MediaItem(Parcel in) {
        url = in.readString();
        fileName = in.readString();
        fileSize = in.readLong();
        createdAt = in.readString();
        messageId = in.readLong();
        String typeName = in.readString();
        type = typeName != null && !typeName.isEmpty() ? MessageType.valueOf(typeName) : MessageType.TEXT;
    }

    public static final Creator<MediaItem> CREATOR = new Creator<MediaItem>() {
        @Override
        public MediaItem createFromParcel(Parcel in) {
            return new MediaItem(in);
        }

        @Override
        public MediaItem[] newArray(int size) {
            return new MediaItem[size];
        }
    };

    public String getUrl() { return url; }
    public String getFileName() { return fileName; }
    public long getFileSize() { return fileSize; }
    public String getCreatedAt() { return createdAt; }
    public long getMessageId() { return messageId; }
    public MessageType getType() { return type; }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
        dest.writeString(fileName);
        dest.writeLong(fileSize);
        dest.writeString(createdAt);
        dest.writeLong(messageId);
        dest.writeString(type != null ? type.name() : null);
    }
}