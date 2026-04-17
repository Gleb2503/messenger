package com.example.messenger;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.websocket.StompClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class UserStatusManager {

    private static final String TAG = "UserStatusManager";
    private static UserStatusManager instance;
    private Boolean lastSentStatus = null;

    private final Context context;
    private StompClient stompClient;
    private long currentUserId;

    private boolean isAppInBackground = false;


    private final Handler backgroundHandler = new Handler(Looper.getMainLooper());

    private Runnable pendingBackgroundTask = null;

    private ScheduledExecutorService heartbeatScheduler;
    private static final long HEARTBEAT_INTERVAL_MS = 30_000;
    private long lastStatusSentTime = 0;
    private static final long STATUS_DEBOUNCE_MS = 2000;

    private UserStatusManager(Context context) {
        this.context = context.getApplicationContext();
        this.currentUserId = RetrofitClient.getUserId();
    }

    public static synchronized UserStatusManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserStatusManager(context);
        }
        return instance;
    }

    public void initWebSocket(String token) {
        if (token == null || token.isEmpty() || currentUserId <= 0) {
            Log.w(TAG, "Cannot init WebSocket: token=" + (token != null) + ", userId=" + currentUserId);
            return;
        }

        Log.d(TAG, "🔌 Initializing UserStatusManager WebSocket, userId=" + currentUserId);

        stompClient = new StompClient(token);
        stompClient.setListener(new StompClient.StompListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "✅ Status WebSocket connected");
                broadcastStatus(true);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "❌ Status WebSocket disconnected");
            }

            @Override public void onMessage(String destination, Object payload) {}

            @Override
            public void onError(String error) {
                Log.e(TAG, "❌ WS Error: " + error);
            }
        });

        stompClient.connect();
    }


    public void broadcastStatus(boolean online) {
        if (currentUserId <= 0) {
            Log.w(TAG, "Cannot broadcast: userId=" + currentUserId + " (not logged in yet)");
            return;
        }

        if (stompClient == null || !stompClient.isConnected()) {
            Log.w(TAG, "Cannot broadcast: connected=" + (stompClient != null));
            return;
        }

        long now = System.currentTimeMillis();


        if (now - lastStatusSentTime < STATUS_DEBOUNCE_MS) {

            if (lastSentStatus == online) {
                Log.d(TAG, "⏱️ Status send debounced (duplicate): online=" + online);
                return;
            }
            Log.d(TAG, "🔄 Status changed, sending immediately: " + lastSentStatus + " → " + online);
        }

        lastStatusSentTime = now;
        lastSentStatus = online;

        Map<String, Object> status = new HashMap<>();
        status.put("userId", currentUserId);
        status.put("online", online);
        status.put("source", "app_lifecycle");

        Log.d(TAG, "📤 Broadcasting user.status: userId=" + currentUserId + ", online=" + online);
        stompClient.send("/app/user.status", status);
    }

    public void onAppBackground() {
        isAppInBackground = true;
        Log.d(TAG, "📦 App entered background");

        if (stompClient != null && stompClient.isConnected()) {
            broadcastStatus(false);
        } else {
            Log.w(TAG, "⚠️ WebSocket disconnected during background. Server will handle offline via timeout.");
        }
    }



    public void onAppForeground() {
        isAppInBackground = false;
        Log.d(TAG, "🟢 App entered foreground");

        broadcastStatus(true);
    }

    public StompClient getStompClient() {
        return stompClient;
    }

    public long getCurrentUserId() {
        return currentUserId;
    }

    public boolean isAppInBackground() {
        return isAppInBackground;
    }

    public void cleanup() {
        if (pendingBackgroundTask != null) {
            backgroundHandler.removeCallbacks(pendingBackgroundTask);
            pendingBackgroundTask = null;
        }

        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
        instance = null;
    }

    public void updateUserId(long userId) {
        if (userId > 0) {
            this.currentUserId = userId;
            Log.d(TAG, "🔄 userId updated: " + userId);

            if (stompClient != null && stompClient.isConnected()) {
                Log.d(TAG, "📤 userId updated, sending online=true");
                broadcastStatus(true);
            }
        } else {
            Log.w(TAG, "⚠️ Invalid userId: " + userId);
        }
    }
}