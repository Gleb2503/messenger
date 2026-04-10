package com.example.messenger.data.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class WebSocketClient {

    private static final String TAG = "StompClient";
    private static final String BASE_URL = "ws://178.212.12.112:8080/ws";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private final String token;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private StompListener listener;
    private boolean isConnected = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT = 5;

    private final Map<String, StompSubscription> subscriptions = new HashMap<>();

    public interface StompListener {
        void onConnected();
        void onDisconnected();
        void onMessageReceived(String chatId, MessageResponse message);
        void onTypingReceived(TypingNotification typing);
        void onError(String error);
    }

    public interface StompSubscription {
        void onMessage(Object payload);
    }

    public WebSocketClient(String token) {
        this.token = token;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build();
    }

    public void setListener(StompListener listener) {
        this.listener = listener;
    }

    public void connect() {
        if (isConnected && webSocket != null) return;

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Sec-WebSocket-Protocol", "v10.stomp, v11.stomp, v12.stomp")
                .build();

        Log.d(TAG, "Connecting to " + BASE_URL);

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket opened");
                sendConnectFrame();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                handleStompFrame(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "Closing: " + reason);
                isConnected = false;
                if (listener != null) mainHandler.post(new Runnable() {
                    @Override public void run() { listener.onDisconnected(); }
                });
                tryReconnect();
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
                isConnected = false;
                if (listener != null) {
                    final String errMsg = t.getMessage();
                    mainHandler.post(new Runnable() {
                        @Override public void run() { listener.onError(errMsg); }
                    });
                }
                tryReconnect();
            }
        });
    }

    private void sendConnectFrame() {
        StringBuilder frame = new StringBuilder();
        frame.append("CONNECT\n");
        frame.append("accept-version:1.2,1.1,1.0\n");
        frame.append("heart-beat:10000,10000\n");
        frame.append("Authorization:Bearer ").append(token).append("\n");
        frame.append("\n").append((char) 0);

        if (webSocket != null) webSocket.send(frame.toString());
    }

    private void handleStompFrame(String text) {
        if (text == null || text.isEmpty()) return;

        String[] parts = text.split("\n\n", 2);
        if (parts.length < 2) return;

        String[] headerLines = parts[0].split("\n");
        String command = headerLines[0].trim();
        Map<String, String> headers = new HashMap<>();

        for (int i = 1; i < headerLines.length; i++) {
            String[] kv = headerLines[i].split(":", 2);
            if (kv.length == 2) headers.put(kv[0].trim(), kv[1].trim());
        }

        String body = parts.length > 1 ? parts[1] : "";
        if (body.endsWith("\0")) body = body.substring(0, body.length() - 1);

        switch (command) {
            case "CONNECTED":
                isConnected = true;
                reconnectAttempts = 0;
                Log.d(TAG, "STOMP connected");
                if (listener != null) mainHandler.post(new Runnable() {
                    @Override public void run() { listener.onConnected(); }
                });
                break;

            case "MESSAGE":
                String destination = headers.get("destination");
                if (destination != null && subscriptions.containsKey(destination)) {
                    final StompSubscription sub = subscriptions.get(destination);
                    Object payload = gson.fromJson(body, Object.class);
                    if (sub != null && payload != null) {
                        mainHandler.post(new Runnable() {
                            @Override public void run() { sub.onMessage(payload); }
                        });
                    }
                }
                break;

            case "ERROR":
                Log.e(TAG, "STOMP error: " + body);
                if (listener != null) {
                    final String err = body;
                    mainHandler.post(new Runnable() {
                        @Override public void run() { listener.onError(err); }
                    });
                }
                break;
        }
    }

    private void tryReconnect() {
        if (reconnectAttempts < MAX_RECONNECT) {
            reconnectAttempts++;
            final long delay = Math.min(1000 * (1L << reconnectAttempts), 30000);
            Log.d(TAG, "Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override public void run() { connect(); }
            }, delay);
        } else {
            Log.e(TAG, "Max reconnect attempts reached");
            if (listener != null) mainHandler.post(new Runnable() {
                @Override public void run() { listener.onDisconnected(); }
            });
        }
    }

    public void disconnect() {
        if (webSocket != null && isConnected) {
            String frame = "DISCONNECT\nreceipt:disconnect-1\n\n\u0000";
            webSocket.send(frame);
            webSocket.close(1000, "Client disconnect");
        }
        isConnected = false;
        subscriptions.clear();
        reconnectAttempts = 0;
    }

    public boolean isConnected() {
        return isConnected && webSocket != null;
    }

    public void subscribe(final String destination, final StompSubscription callback) {
        if (!isConnected) return;

        final String id = UUID.randomUUID().toString().substring(0, 8);
        StringBuilder frame = new StringBuilder();
        frame.append("SUBSCRIBE\n");
        frame.append("id:").append(id).append("\n");
        frame.append("destination:").append(destination).append("\n");
        frame.append("ack:auto\n");
        frame.append("\n").append((char) 0);

        if (webSocket != null) {
            webSocket.send(frame.toString());
            subscriptions.put(destination, callback);
        }
    }

    public void send(final String destination, final Object payload) {
        if (!isConnected || webSocket == null) return;

        String jsonBody = gson.toJson(payload);
        StringBuilder frame = new StringBuilder();
        frame.append("SEND\n");
        frame.append("destination:").append(destination).append("\n");
        frame.append("content-type:application/json\n");
        frame.append("content-length:").append(jsonBody.getBytes().length).append("\n");
        frame.append("\n");
        frame.append(jsonBody);
        frame.append((char) 0);

        webSocket.send(frame.toString());
    }

    public void subscribeToChat(final String chatId, final StompSubscription callback) {
        subscribe("/topic/chat/" + chatId, callback);
    }

    public void sendMessage(final String chatId, final String content) {
        CreateMessageRequest request = new CreateMessageRequest();
        request.chatId = Long.parseLong(chatId);
        request.content = content;
        send("/app/chat.sendMessage", request);
    }

    public void sendTyping(final String chatId, final boolean isTyping) {
        TypingNotification notification = new TypingNotification();
        notification.chatId = Long.parseLong(chatId);
        notification.isTyping = isTyping;
        notification.timestamp = new Date();
        notification.userId = 0L;
        notification.username = "";
        send("/app/chat.typing", notification);
    }

    public void markAsRead(final Long messageId, final Long userId) {
        MessageReadRequest request = new MessageReadRequest();
        request.messageId = messageId;
        request.userId = userId;
        request.readAt = new Date();
        send("/app/message.read", request);
    }
}