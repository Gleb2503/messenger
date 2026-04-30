package com.example.messenger.chat;

public class ChatItem {
    public static final int TYPE_HEADER_PINNED = 0;
    public static final int TYPE_HEADER_ALL = 1;
    public static final int TYPE_CHAT = 2;

    private final int type;
    private final long id;
    private final String name;
    private final String lastMessage;
    private final String time;
    private final int unreadCount;
    private final boolean isPinned;
    private final long partnerUserId;
    private final String avatarUrl;

    public ChatItem(int type, String title) {
        this.type = type;
        this.id = -1;
        this.name = title;
        this.lastMessage = "";
        this.time = "";
        this.unreadCount = 0;
        this.isPinned = false;
        this.partnerUserId = -1;
        this.avatarUrl = "";
    }

    public ChatItem(long id, String name, String lastMessage, String time,
                    int unreadCount, boolean isPinned, long partnerUserId, String avatarUrl) {
        this.type = TYPE_CHAT;
        this.id = id;
        this.name = name;
        this.lastMessage = lastMessage;
        this.time = time;
        this.unreadCount = unreadCount;
        this.isPinned = isPinned;
        this.partnerUserId = partnerUserId;
        this.avatarUrl = avatarUrl != null ? avatarUrl : "";
    }

    public String getAvatarUrl() { return avatarUrl; }
    public long getPartnerUserId() { return partnerUserId; }
    public int getType() { return type; }
    public long getId() { return id; }
    public String getName() { return name; }
    public String getLastMessage() { return lastMessage; }
    public String getTime() { return time; }
    public int getUnreadCount() { return unreadCount; }
    public boolean isPinned() { return isPinned; }
    public boolean isHeader() { return type == TYPE_HEADER_PINNED || type == TYPE_HEADER_ALL; }
    public boolean isPinnedHeader() { return type == TYPE_HEADER_PINNED; }
    public boolean isAllChatsHeader() { return type == TYPE_HEADER_ALL; }
}