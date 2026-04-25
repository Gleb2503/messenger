package com.example.messenger.data.websocket;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.gson.Gson;
import okhttp3.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.lang.ref.WeakReference;

public class StompClient {

    private static final String TAG = "StompClient";
    private static final String BASE_URL = "ws://178.212.12.112:8080/ws/websocket";

    private OkHttpClient httpClient;
    private WebSocket webSocket;
    private final String token;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Gson gson = new Gson();

    private WeakReference<StompListener> listenerRef;
    private boolean isConnected = false;
    private boolean isDisconnected = false;
    private int reconnectAttempts = 0;
    private static final int MAX_RECONNECT = 5;

    private final Map<String, StompSubscription> subscriptions = new ConcurrentHashMap<>();

    public interface StompListener {
        void onConnected();
        void onDisconnected();
        void onMessage(String destination, Object payload);
        void onError(String error);
    }

    public interface StompSubscription {
        void onMessage(Object payload);
    }

    public StompClient(String token) {
        this.token = token;
        this.httpClient = new OkHttpClient.Builder()
                .pingInterval(60, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public void setListener(StompListener listener) {
        this.listenerRef = new WeakReference<>(listener);
    }

    public void connect() {
        Log.d(TAG, "connect() called, isConnected=" + isConnected + ", webSocket=" + webSocket);

        isDisconnected = false;

        if (isConnected && webSocket != null) return;

        Request request = new Request.Builder()
                .url(BASE_URL)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Sec-WebSocket-Protocol", "v10.stomp, v11.stomp, v12.stomp")
                .build();

        Log.d(TAG, "Connecting to " + BASE_URL);
        Log.d(TAG, "Token first 20 chars: " + (token != null && token.length() > 20 ? token.substring(0, 20) + "..." : "null"));

        webSocket = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket ws, Response response) {
                Log.d(TAG, "WebSocket onOpen, code: " + response.code());
                Log.d(TAG, "Response headers: " + response.headers());
                sendConnectFrame();
            }

            @Override
            public void onMessage(WebSocket ws, String text) {
                Log.d(TAG, "Raw STOMP frame: " + truncate(text, 500));
                handleStompFrame(text);
            }

            @Override
            public void onClosing(WebSocket ws, int code, String reason) {
                Log.d(TAG, "Closing: code=" + code + ", reason=" + reason);
                isConnected = false;
                postListenerEvent(() -> {
                    StompListener listener = getListener();
                    if (listener != null) listener.onDisconnected();
                });
                if (code != 1000) {
                    tryReconnect();
                }
            }

            @Override
            public void onFailure(WebSocket ws, Throwable t, Response response) {
                Log.e(TAG, "WebSocket error: " + t.getMessage());
                if (response != null) {
                    Log.e(TAG, "Response code: " + response.code());
                    Log.e(TAG, "Response headers: " + response.headers());
                }
                isConnected = false;
                final String err = t.getMessage();
                postListenerEvent(() -> {
                    StompListener listener = getListener();
                    if (listener != null) listener.onError(err);
                });
                tryReconnect();
            }
        });
    }

    private StompListener getListener() {
        return listenerRef != null ? listenerRef.get() : null;
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "null";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private void sendConnectFrame() {
        StringBuilder frame = new StringBuilder();
        frame.append("CONNECT\n");
        frame.append("accept-version:1.2,1.1,1.0\n");
        frame.append("heart-beat:10000,10000\n");
        frame.append("Authorization:Bearer ").append(token).append("\n");
        frame.append("\n").append((char) 0);

        Log.d(TAG, "Sending CONNECT frame");
        if (webSocket != null) webSocket.send(frame.toString());
    }

    private void handleStompFrame(String text) {
        if (text == null || text.isEmpty()) return;

        int headerEnd = text.indexOf("\n\n");
        if (headerEnd == -1) {
            Log.w(TAG, "Invalid STOMP frame");
            return;
        }

        String headersPart = text.substring(0, headerEnd);
        String bodyPart = text.substring(headerEnd + 2);

        if (bodyPart.endsWith("\0")) {
            bodyPart = bodyPart.substring(0, bodyPart.length() - 1);
        }

        String[] headerLines = headersPart.split("\n");
        if (headerLines.length == 0) return;

        String command = headerLines[0].trim();
        Map<String, String> headers = new java.util.HashMap<>();

        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i].trim();
            if (line.isEmpty()) continue;
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                headers.put(line.substring(0, colonIdx), line.substring(colonIdx + 1));
            }
        }

        Log.d(TAG, "STOMP Command: " + command + " | Destination: " + headers.get("destination"));

        switch (command) {
            case "CONNECTED":
                isConnected = true;
                reconnectAttempts = 0;
                String sessionId = headers.get("session");
                Log.d(TAG, "STOMP connected, session: " + sessionId);
                postListenerEvent(() -> {
                    StompListener listener = getListener();
                    if (listener != null) listener.onConnected();
                });
                resubscribeAll();
                break;

            case "MESSAGE":
                String destination = headers.get("destination");
                if (destination != null && subscriptions.containsKey(destination)) {
                    final StompSubscription sub = subscriptions.get(destination);
                    final String body = bodyPart;
                    Log.d(TAG, "Parsing message body: " + truncate(body, 200));
                    Object payload = gson.fromJson(body, Object.class);
                    Log.d(TAG, "Message received on " + destination + ", payload type: " + (payload != null ? payload.getClass().getSimpleName() : "null"));
                    if (sub != null && payload != null) {
                        mainHandler.post(() -> sub.onMessage(payload));
                    }
                } else {
                    Log.w(TAG, "No subscription for destination: " + destination + " | Available: " + subscriptions.keySet());
                }
                break;

            case "ERROR":
                Log.e(TAG, "STOMP error: " + bodyPart);
                final String errorMsg = bodyPart;
                postListenerEvent(() -> {
                    StompListener listener = getListener();
                    if (listener != null) listener.onError(errorMsg);
                });
                break;

            case "PING":
                if (webSocket != null) {
                    webSocket.send("PONG\n\n\u0000");
                }
                break;

            case "PONG":
                break;

            default:
                Log.w(TAG, "Unknown STOMP command: " + command);
                break;
        }
    }

    private void tryReconnect() {
        if (isDisconnected) {
            Log.d(TAG, "Reconnect cancelled: client was explicitly disconnected");
            return;
        }
        if (reconnectAttempts < MAX_RECONNECT) {
            reconnectAttempts++;
            final long delay = Math.min(1000L * (1L << reconnectAttempts), 30000);
            Log.d(TAG, "Reconnecting in " + delay + "ms (attempt " + reconnectAttempts + ")");
            mainHandler.postDelayed(() -> {
                if (!isDisconnected) {
                    connect();
                }
            }, delay);
        } else {
            Log.e(TAG, "Max reconnect attempts reached");
            postListenerEvent(() -> {
                StompListener listener = getListener();
                if (listener != null) listener.onDisconnected();
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
        isDisconnected = true;
        subscriptions.clear();
        reconnectAttempts = 0;
    }

    public boolean isConnected() {
        return isConnected && webSocket != null;
    }

    public String subscribe(final String destination, final StompSubscription callback) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, cannot subscribe to " + destination);
            return null;
        }

        if (subscriptions.containsKey(destination)) {
            Log.d(TAG, "✅ Already subscribed to " + destination + ", skipping duplicate");
            return null;
        }

        final String id = UUID.randomUUID().toString().substring(0, 8);
        StringBuilder frame = new StringBuilder();
        frame.append("SUBSCRIBE\n");
        frame.append("id:").append(id).append("\n");
        frame.append("destination:").append(destination).append("\n");
        frame.append("ack:auto\n");
        frame.append("\n").append((char) 0);

        Log.d(TAG, "Subscribing to: " + destination + " with id: " + id);
        if (webSocket != null) {
            webSocket.send(frame.toString());
            subscriptions.put(destination, callback);
            return id;
        }
        return null;
    }
    public void unsubscribe(String destination, String subscriptionId) {
        if (webSocket != null && subscriptions.containsKey(destination)) {
            String frame = "UNSUBSCRIBE\nid:" + subscriptionId + "\n\n\u0000";
            webSocket.send(frame);
            subscriptions.remove(destination);
            Log.d(TAG, "🔇 Unsubscribed from " + destination + " (id: " + subscriptionId + ")");
        } else {
            Log.w(TAG, "⚠️ Cannot unsubscribe: destination=" + destination);
        }
    }


    private void resubscribeAll() {
        Log.d(TAG, "Resubscribing to " + subscriptions.size() + " destinations");

        List<Map.Entry<String, StompSubscription>> entries =
                new ArrayList<>(subscriptions.entrySet());


        subscriptions.clear();

        for (Map.Entry<String, StompSubscription> entry : entries) {
            String destination = entry.getKey();
            StompSubscription callback = entry.getValue();
            Log.d(TAG, "Resubscribing to: " + destination);
            subscribe(destination, callback);
        }
    }

    public void send(final String destination, final Object payload) {
        if (!isConnected || webSocket == null) {
            Log.w(TAG, "Not connected, cannot send to " + destination);
            return;
        }

        String jsonBody = gson.toJson(payload);
        StringBuilder frame = new StringBuilder();
        frame.append("SEND\n");
        frame.append("destination:").append(destination).append("\n");
        frame.append("content-type:application/json\n");
        frame.append("content-length:").append(jsonBody.getBytes().length).append("\n");
        frame.append("\n");
        frame.append(jsonBody);
        frame.append((char) 0);

        Log.d(TAG, "Sending to " + destination + " | Body: " + truncate(jsonBody, 100));
        webSocket.send(frame.toString());
    }

    private void postListenerEvent(Runnable event) {
        StompListener listener = getListener();
        if (listener != null) mainHandler.post(event);
    }

    public void subscribeToChat(final String chatId, final StompSubscription callback) {
        subscribe("/topic/chat/" + chatId, callback);
    }
    public void sendMessage(final String chatId, final String content, MessageType type) {
        CreateMessageRequest request = new CreateMessageRequest(
                Long.parseLong(chatId),
                content,
                type != null ? type : MessageType.TEXT
        );
        send("/app/chat.sendMessage", request);
    }
    public void sendImageMessage(final String chatId, final String imageUrl) {
        CreateMessageRequest request = new CreateMessageRequest(
                Long.parseLong(chatId),
                imageUrl,
                MessageType.IMAGE
        );
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