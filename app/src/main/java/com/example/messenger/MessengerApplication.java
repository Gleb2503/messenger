package com.example.messenger;

import android.app.Application;
import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import com.example.messenger.data.api.RetrofitClient;
import com.example.messenger.status.UserStatusManager;

public class MessengerApplication extends Application {

    private static final String TAG = "MessengerApp";
    private int resumedActivities = 0;
    private UserStatusManager statusManager;

    @Override
    public void onCreate() {
        super.onCreate();

        RetrofitClient.init(this);

        statusManager = UserStatusManager.getInstance(this);

        String token = RetrofitClient.getToken();
        long userId = RetrofitClient.getUserId();
        if (token != null && !token.isEmpty() && userId > 0) {
            statusManager.initWebSocket(token);
        }
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                resumedActivities++;
                if (resumedActivities == 1) {
                    Log.d(TAG, "🟢 App entered foreground");
                    if (statusManager != null) {
                        statusManager.onAppForeground();
                    }
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                resumedActivities--;
                if (resumedActivities == 0) {
                    Log.d(TAG, "⚪ App entered background");
                    if (statusManager != null) {
                        statusManager.onAppBackground();
                    }
                }
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }
    public UserStatusManager getStatusManager() {
        return statusManager;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (statusManager != null) {
            statusManager.cleanup();
        }
    }
}