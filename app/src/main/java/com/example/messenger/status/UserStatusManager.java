package com.example.messenger.status;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.data.websocket.StompClient;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

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

    public interface OnWebSocketReadyListener {
        void onReady();
    }
    private final java.util.List<OnWebSocketReadyListener> readyListeners = new java.util.ArrayList<>();

    private UserStatusManager(Context context) {
        this.context = context.getApplicationContext();
        refreshUserId();
        Log.d(TAG, "🔐 UserStatusManager created with userId=" + currentUserId);
    }

    public static synchronized UserStatusManager getInstanceIfExists() {
        return instance;
    }

    public static synchronized UserStatusManager getInstance(Context context) {
        if (instance == null) {
            instance = new UserStatusManager(context);
        }
        return instance;
    }

    public void refreshUserId() {
        long savedUserId = RetrofitClient.getUserId();
        if (savedUserId > 0 && savedUserId != this.currentUserId) {
            Log.d(TAG, "🔄 Refreshed userId: " + this.currentUserId + " → " + savedUserId);
            this.currentUserId = savedUserId;
        }
    }

    public void addOnReadyListener(OnWebSocketReadyListener listener) {
        if (stompClient != null && stompClient.isConnected()) {
            listener.onReady();
        } else {
            readyListeners.add(listener);
        }
    }

    public void removeOnReadyListener(OnWebSocketReadyListener listener) {
        readyListeners.remove(listener);
    }

    public void initWebSocket(String token) {
        refreshUserId();

        if (token == null || token.isEmpty() || currentUserId <= 0) {
            Log.w(TAG, "Cannot init WebSocket: token=" + (token != null) + ", userId=" + currentUserId);
            return;
        }

        Log.d(TAG, "🔌 Initializing UserStatusManager WebSocket, userId=" + currentUserId);

        if (stompClient != null && stompClient.isConnected()) {
            Log.d(TAG, "🔄 Closing existing WebSocket before reconnect");
            stompClient.disconnect();
        }

        stompClient = new StompClient(token);
        stompClient.setListener(new StompClient.StompListener() {
            @Override
            public void onConnected() {
                Log.d(TAG, "✅ Status WebSocket connected");

                for (OnWebSocketReadyListener listener : new java.util.ArrayList<>(readyListeners)) {
                    listener.onReady();
                }
                readyListeners.clear();

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
        refreshUserId();

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

        if (RetrofitClient.getToken() == null || RetrofitClient.getUserId() <= 0) {
            Log.d(TAG, "⚠️ Not logged in, skipping status broadcast");
            return;
        }

        if (stompClient != null && stompClient.isConnected()) {
            broadcastStatus(false);
        }
    }

    public void onAppForeground() {
        isAppInBackground = false;
        Log.d(TAG, "🟢 App entered foreground");

        refreshUserId();

        if (currentUserId <= 0) {
            Log.w(TAG, "⚠️ Cannot broadcast: userId not set yet");
            return;
        }

        broadcastStatus(true);
    }

    public StompClient getStompClient() {
        return stompClient;
    }

    public boolean isConnected() {
        return stompClient != null && stompClient.isConnected();
    }

    public long getCurrentUserId() {
        refreshUserId();
        return currentUserId;
    }

    public boolean isAppInBackground() {
        return isAppInBackground;
    }

    public void logout() {
        Log.d(TAG, "🚪 User logging out, sending offline status...");

        if (stompClient != null && stompClient.isConnected() && currentUserId > 0) {
            Map<String, Object> status = new HashMap<>();
            status.put("userId", currentUserId);
            status.put("online", false);
            status.put("source", "logout");

            Log.d(TAG, "📤 Sending offline status on logout: userId=" + currentUserId);
            stompClient.send("/app/user.status", status);

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Log.w(TAG, "⚠️ Interrupted while waiting for status send");
            }
        }


        cleanup();
    }

    public void cleanup() {
        Log.d(TAG, "🧹 Cleaning up UserStatusManager");

        if (pendingBackgroundTask != null) {
            backgroundHandler.removeCallbacks(pendingBackgroundTask);
            pendingBackgroundTask = null;
        }

        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
            Log.d(TAG, "🔌 WebSocket disconnected");
        }

        readyListeners.clear();

        this.currentUserId = -1;
        Log.d(TAG, "🔄 Reset currentUserId to: -1");

        instance = null;
        Log.d(TAG, "✅ UserStatusManager cleaned up");
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