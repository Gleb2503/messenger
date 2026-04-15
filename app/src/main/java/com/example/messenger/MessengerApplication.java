package com.example.messenger;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.util.Log;

public class MessengerApplication extends Application {

    private static final String TAG = "MessengerApp";

    private static boolean isAppInBackground = false;

    private int resumedActivities = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityResumed(Activity activity) {
                resumedActivities++;
                if (resumedActivities == 1) {
                    isAppInBackground = false;
                    Log.d(TAG, "🟢 App entered foreground");
                }
            }

            @Override
            public void onActivityPaused(Activity activity) {
                resumedActivities--;
                if (resumedActivities == 0) {
                    isAppInBackground = true;
                    Log.d(TAG, "⚪ App entered background");
                }
            }

            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityStarted(Activity a) {}
            @Override public void onActivityStopped(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
    }


    public static boolean isAppInBackground() {
        return isAppInBackground;
    }
}