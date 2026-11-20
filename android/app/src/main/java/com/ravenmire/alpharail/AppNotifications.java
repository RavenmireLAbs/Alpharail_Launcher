package com.ravenmire.alpharail;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Active package-only notification state for optional app dots. No message data is retained. */
public final class AppNotifications extends NotificationListenerService {
    static final Set<Runnable> observers = new HashSet<>();
    private static AppNotifications connected;
    private static Set<String> packages;
    private void changed() { packages = null; for (Runnable observer : new ArrayList<>(observers)) observer.run(); }
    @Override public void onListenerConnected() { connected = this; changed(); }
    @Override public void onListenerDisconnected() { if (connected == this) connected = null; changed(); }
    @Override public void onNotificationPosted(StatusBarNotification item) { changed(); }
    @Override public void onNotificationRemoved(StatusBarNotification item) { changed(); }
    @Override public void onDestroy() { if (connected == this) connected = null; changed(); super.onDestroy(); }
    static boolean hasNotification(String pkg) {
        if (packages == null) {
            packages = new HashSet<>();
            if (connected != null) try {
                StatusBarNotification[] active = connected.getActiveNotifications();
                if (active != null) for (StatusBarNotification item : active)
                    if (android.os.Process.myUserHandle().equals(item.getUser()) && !item.isOngoing()
                        && (item.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) packages.add(item.getPackageName());
            } catch (RuntimeException ignored) { }
        }
        return packages.contains(pkg);
    }
}
