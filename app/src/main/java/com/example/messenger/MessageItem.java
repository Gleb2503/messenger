package com.example.messenger;

public class MessageItem {


    public static final int TYPE_DATE = 3;
    public static final int TYPE_HEADER = 4;
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;


    public static final int STATUS_SENT = 0;
    public static final int STATUS_DELIVERED = 1;
    public static final int STATUS_READ = 2;
    public static final int STATUS_FAILED = 3;

    private long id;
    private String text;
    private String time;
    private int status;
    private int type;
    private long senderId;

    public MessageItem(long id, String text, String time, int status, int type, long senderId) {
        this.id = id;
        this.text = text;
        this.time = time;
        this.status = status;
        this.type = type;
        this.senderId = senderId;
    }


    public long getId() { return id; }
    public String getText() { return text; }
    public String getTime() { return time; }
    public int getStatus() { return status; }
    public int getType() { return type; }
    public long getSenderId() { return senderId; }


    public void setId(long id) { this.id = id; }
    public void setText(String text) { this.text = text; }
    public void setTime(String time) { this.time = time; }
    public void setStatus(int status) { this.status = status; }
    public void setType(int type) { this.type = type; }
    public void setSenderId(long senderId) { this.senderId = senderId; }
}