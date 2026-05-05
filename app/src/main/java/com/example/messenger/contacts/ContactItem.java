package com.example.messenger.contacts;

import java.io.Serializable;

public class ContactItem implements Serializable {
    private Long id;
    private Long userId;
    private String displayName;
    private String username;
    private String avatarUrl;
    private boolean isOnline;
    private String sectionLetter;
    private boolean isExplicitContact;

    public ContactItem(Long id, Long userId, String displayName, String username, String avatarUrl, boolean isOnline, boolean isExplicitContact) {
        this.id = id;
        this.userId = userId;
        this.displayName = displayName != null ? displayName : "Пользователь";
        this.username = username != null ? username : "";
        this.avatarUrl = avatarUrl;
        this.isOnline = isOnline;
        this.isExplicitContact = isExplicitContact;
        if (!this.displayName.isEmpty()) {
            this.sectionLetter = this.displayName.substring(0, 1).toUpperCase();
        } else if (!this.username.isEmpty()) {
            this.sectionLetter = this.username.substring(0, 1).toUpperCase();
        } else {
            this.sectionLetter = "#";
        }
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }
    public String getUsername() { return username; }
    public String getAvatarUrl() { return avatarUrl; }
    public boolean isOnline() { return isOnline; }
    public String getSectionLetter() { return sectionLetter; }
    public boolean isExplicitContact() { return isExplicitContact; }
    public String getStatusText() { return isOnline ? "онлайн" : "оффлайн"; }
    public int getStatusColor() { return isOnline ? 0xFF4CAF50 : 0xFF9E9E9E; }
}