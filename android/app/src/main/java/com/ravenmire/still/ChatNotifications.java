package com.ravenmire.still;

import android.app.Notification;
import android.app.RemoteInput;
import android.os.Process;
import android.content.Context;
import android.provider.Telephony;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.*;

/** Reads only currently active message notifications; no message database. */
public final class ChatNotifications extends NotificationListenerService {
    private static final Set<String> MESSAGING_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
        "com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "com.facebook.mlite",
        "com.instagram.android", "com.instagram.lite", "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thunderdog.challegram", "org.thoughtcrime.securesms", "com.discord", "com.viber.voip",
        "com.snapchat.android", "jp.naver.line.android", "com.tencent.mm", "com.kakao.talk",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.messaging",
        "com.android.mms", "com.microsoft.teams", "com.Slack", "im.vector.app", "io.element.android.x")));
    static boolean isMessagingApp(Context context, String packageName) {
        return MESSAGING_PACKAGES.contains(packageName) || packageName.equals(Telephony.Sms.getDefaultSmsPackage(context));
    }
    private static ChatNotifications connected;
    @Override public void onListenerConnected() { connected = this; }
    @Override public void onListenerDisconnected() { if (connected == this) connected = null; }
    @Override public void onDestroy() { if (connected == this) connected = null; super.onDestroy(); }

    static boolean available() { return connected != null; }
    static List<StatusBarNotification> current() {
        List<StatusBarNotification> messages = new ArrayList<>();
        if (connected == null) return messages;
        try {
            StatusBarNotification[] active = connected.getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                Notification n = item.getNotification();
                if (!isMessagingApp(connected, item.getPackageName()) || !Process.myUserHandle().equals(item.getUser()) || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if (Notification.CATEGORY_MESSAGE.equals(n.category) || (n.extras != null && Notification.MessagingStyle.class.getName().equals(n.extras.getString(Notification.EXTRA_TEMPLATE)))) messages.add(item);
            }
        } catch (RuntimeException ignored) { }
        messages.sort(Comparator.comparingLong(StatusBarNotification::getPostTime).reversed());
        return messages;
    }
    static Notification.Action replyAction(StatusBarNotification item) {
        Notification.Action[] actions = item.getNotification().actions;
        if (actions != null) for (Notification.Action action : actions) {
            if (action.actionIntent != null && item.getPackageName().equals(action.actionIntent.getCreatorPackage()) && replyInput(action) != null) return action;
        }
        return null;
    }
    static RemoteInput replyInput(Notification.Action action) {
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs != null) for (RemoteInput input : inputs) if (input.getAllowFreeFormInput()) return input;
        return null;
    }
}
