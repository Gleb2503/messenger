package com.example.messenger.status;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class AppStatusManager {

    private static final String TAG = "AppStatusManager";

    private static volatile AppStatusManager instance;


    private final Map<Long, UserStatusEntry> statusCache = new ConcurrentHashMap<>();


    private final CopyOnWriteArrayList<StatusChangeListener> listeners = new CopyOnWriteArrayList<>();

    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    private AppStatusManager() {
        Log.d(TAG, "🔧 AppStatusManager initialized");
    }

    public static AppStatusManager getInstance() {
        if (instance == null) {
            synchronized (AppStatusManager.class) {
                if (instance == null) {
                    instance = new AppStatusManager();
                }
            }
        }
        return instance;
    }

    public interface StatusChangeListener {
        void onStatusChanged(Long userId, boolean online, String source);
    }

    private static class UserStatusEntry {
        final boolean online;
        final long timestamp;
        final String source; // "app", "chat", "request", "broadcast"

        UserStatusEntry(boolean online, String source) {
            this.online = online;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_TTL_MS;
        }
    }

    public void updateStatus(Long userId, boolean online, String source) {
        if (userId == null || userId <= 0) {
            Log.w(TAG, "⚠️ Cannot update status: invalid userId=" + userId);
            return;
        }
        if (source == null) source = "unknown";

        UserStatusEntry entry = new UserStatusEntry(online, source);
        statusCache.put(userId, entry);

        Log.d(TAG, "💾 Updated: user=" + userId + ", online=" + online + ", source=" + source);

        notifyListeners(userId, online, source);
    }

    public void cacheUserStatus(Long userId, boolean online) {
        updateStatus(userId, online, "cache");
    }

    public Boolean getStatus(Long userId) {
        if (userId == null || userId <= 0) return null;

        UserStatusEntry entry = statusCache.get(userId);
        if (entry == null) {
            Log.d(TAG, "🔍 No cache for user " + userId);
            return null;
        }

        if (entry.isExpired()) {
            Log.d(TAG, "⚠️ Cache expired for user " + userId + ", but returning value: " + entry.online);
            return entry.online;
        }

        Log.d(TAG, "📦 Cache hit: user=" + userId + ", online=" + entry.online + ", source=" + entry.source);
        return entry.online;
    }

    public Boolean getCachedStatus(Long userId) {
        return getStatus(userId);
    }

    public void forceUpdate(Long userId, boolean online, String source) {
        if (userId == null || userId <= 0) return;

        statusCache.remove(userId);

        updateStatus(userId, online, source != null ? source + "_force" : "force");
    }
    public void addStatusListener(StatusChangeListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
            Log.d(TAG, "👂 Listener added: " + listener.getClass().getSimpleName());
        }
    }
    public void removeStatusListener(StatusChangeListener listener) {
        if (listener != null) {
            boolean removed = listeners.remove(listener);
            if (removed) {
                Log.d(TAG, "🔇 Listener removed: " + listener.getClass().getSimpleName());
            }
        }
    }
    private void notifyListeners(Long userId, boolean online, String source) {
        if (listeners.isEmpty()) return;

        for (StatusChangeListener listener : listeners) {
            try {
                listener.onStatusChanged(userId, online, source);
            } catch (Exception e) {
                Log.e(TAG, "❌ Error notifying listener: " + listener, e);
            }
        }
    }

    public boolean hasFreshStatus(Long userId) {
        return getStatus(userId) != null;
    }

    public void invalidateStatus(Long userId) {
        if (userId != null && userId > 0) {
            UserStatusEntry removed = statusCache.remove(userId);
            if (removed != null) {
                Log.d(TAG, "🗑️ Invalidated: user=" + userId + ", was online=" + removed.online);

                notifyListeners(userId, false, "invalidated");
            }
        }
    }

    public void clearAll() {
        int count = statusCache.size();
        statusCache.clear();
        Log.d(TAG, "🧹 Cleared cache: " + count + " entries");

    }

    public Map<Long, Boolean> getAllStatuses() {
        Map<Long, Boolean> result = new ConcurrentHashMap<>();
        for (Map.Entry<Long, UserStatusEntry> entry : statusCache.entrySet()) {
            if (!entry.getValue().isExpired()) {
                result.put(entry.getKey(), entry.getValue().online);
            }
        }
        return result;
    }


    public int getCacheSize() {
        return (int) statusCache.values().stream()
                .filter(e -> !e.isExpired())
                .count();
    }

    public int getListenerCount() {
        return listeners.size();
    }


    public static void resetForTesting() {
        if (instance != null) {
            instance.clearAll();
            instance.listeners.clear();
            instance = null;
            Log.d(TAG, "🔄 Reset for testing");
        }
    }


    public String getDebugInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("AppStatusManager:\n");
        sb.append("  Cache size: ").append(getCacheSize()).append("\n");
        sb.append("  Listeners: ").append(getListenerCount()).append("\n");
        for (Map.Entry<Long, UserStatusEntry> entry : statusCache.entrySet()) {
            UserStatusEntry e = entry.getValue();
            if (!e.isExpired()) {
                long age = System.currentTimeMillis() - e.timestamp;
                sb.append("  User ").append(entry.getKey())
                        .append(": online=").append(e.online)
                        .append(", source=").append(e.source)
                        .append(", age=").append(age / 1000).append("s\n");
            }
        }
        return sb.toString();
    }
}