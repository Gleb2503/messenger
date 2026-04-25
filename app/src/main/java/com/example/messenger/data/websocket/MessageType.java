package com.example.messenger.data.websocket;

import com.google.gson.annotations.SerializedName;

public enum MessageType {

    @SerializedName("text") TEXT,
    @SerializedName("image") IMAGE,
    @SerializedName("video") VIDEO,
    @SerializedName("audio") AUDIO,
    @SerializedName("file") FILE;

    public static MessageType fromString(String value) {
        if (value == null) return TEXT;
        for (MessageType type : values()) {
            if (type.name().equalsIgnoreCase(value) ||
                    type.toString().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return TEXT;
    }
}