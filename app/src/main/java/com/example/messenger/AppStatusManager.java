
package com.example.messenger;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class AppStatusManager {

    private static final String TAG = "AppStatusManager";

    private static volatile AppStatusManager instance;


    private final Map<Long, Boolean> knownUserStatuses = new ConcurrentHashMap<>();


    private final Map<Long, Long> lastUpdateTimestamps = new ConcurrentHashMap<>();


    private static final long CACHE_TTL_MS = 10 * 60 * 1000;

    private AppStatusManager() {

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


    public void cacheUserStatus(Long userId, boolean online) {
        if (userId == null || userId <= 0) {
            Log.w(TAG, "⚠️ Cannot cache status: invalid userId=" + userId);
            return;
        }

        knownUserStatuses.put(userId, online);
        lastUpdateTimestamps.put(userId, System.currentTimeMillis());

        Log.d(TAG, "💾 Cached status: user=" + userId + ", online=" + online);
    }

    public Boolean getCachedStatus(Long userId) {
        if (userId == null || userId <= 0) {
            return null;
        }


        Long lastUpdate = lastUpdateTimestamps.get(userId);
        if (lastUpdate != null &&
                System.currentTimeMillis() - lastUpdate > CACHE_TTL_MS) {
            Log.d(TAG, "⏰ Cache expired for user " + userId + ", returning null");
            knownUserStatuses.remove(userId);
            lastUpdateTimestamps.remove(userId);
            return null;
        }

        Boolean status = knownUserStatuses.get(userId);
        if (status != null) {
            Log.d(TAG, "📦 Retrieved cached status: user=" + userId + ", online=" + status);
        }
        return status;
    }


    public boolean hasFreshCache(Long userId) {
        return getCachedStatus(userId) != null;
    }


    public void invalidateCache(Long userId) {
        if (userId != null && userId > 0) {
            knownUserStatuses.remove(userId);
            lastUpdateTimestamps.remove(userId);
            Log.d(TAG, "🗑️ Invalidated cache for user " + userId);
        }
    }

    public void clearCache() {
        int cleared = knownUserStatuses.size();
        knownUserStatuses.clear();
        lastUpdateTimestamps.clear();
        Log.d(TAG, "🧹 Cleared cache: " + cleared + " entries removed");
    }


    public Map<Long, Boolean> getAllCachedStatuses() {
        return new ConcurrentHashMap<>(knownUserStatuses);
    }


    public int getCacheSize() {
        return knownUserStatuses.size();
    }

    public static void resetInstance() {
        if (instance != null) {
            instance.clearCache();
            instance = null;
            Log.d(TAG, "🔄 Instance reset");
        }
    }
}