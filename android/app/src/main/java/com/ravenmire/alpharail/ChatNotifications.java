package com.ravenmire.alpharail;

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
    private ChatBubble bubble;
    static void refreshBubble() { if (connected != null) { if (connected.bubble == null) connected.bubble = new ChatBubble(connected); connected.bubble.stop(); connected.bubble.update(); } }
    static final Set<Runnable> observers = new HashSet<>();
    private void changed() { if (bubble != null) bubble.changed(); dotPackages = null; for (Runnable observer : new ArrayList<>(observers)) observer.run(); }
    @Override public void onNotificationPosted(StatusBarNotification item) {
        if (item != null) { ChatHistory.capture(this, item); changed(); }
    }
    @Override public void onNotificationRemoved(StatusBarNotification item) { onNotificationPosted(item); }
    private static ChatNotifications connected;
    @Override public void onListenerConnected() { connected = this; bubble = new ChatBubble(this); changed(); }
    @Override public void onListenerDisconnected() { if (bubble != null) bubble.stop(); if (connected == this) connected = null; changed(); }
    @Override public void onDestroy() { if (bubble != null) bubble.stop(); if (connected == this) { connected = null; changed(); } super.onDestroy(); }

    static boolean available() { return connected != null; }
    private static Set<String> dotPackages;
    static boolean hasNotification(String packageName) {
        if (dotPackages == null) {
            dotPackages = new HashSet<>();
            if (connected != null) try {
                StatusBarNotification[] items = connected.getActiveNotifications();
                if (items != null) for (StatusBarNotification item : items)
                    if (Process.myUserHandle().equals(item.getUser()) && !item.isOngoing()
                        && (item.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) == 0) dotPackages.add(item.getPackageName());
            } catch (RuntimeException ignored) { }
        }
        return dotPackages.contains(packageName);
    }
    static List<StatusBarNotification> current() {
        List<StatusBarNotification> messages = new ArrayList<>();
        ChatNotifications listener = connected;
        if (listener == null || !listener.getSharedPreferences("still", Context.MODE_PRIVATE).getBoolean("chatsEnabled", false)) return messages;
        try {
            StatusBarNotification[] active = listener.getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                Notification n = item.getNotification();
                if (!isMessagingApp(listener, item.getPackageName()) || !Process.myUserHandle().equals(item.getUser()) || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) continue;
                if (Notification.CATEGORY_MESSAGE.equals(n.category) || (n.extras != null && Notification.MessagingStyle.class.getName().equals(n.extras.getString(Notification.EXTRA_TEMPLATE)))) messages.add(item);
            }
        } catch (RuntimeException ignored) { }
        messages.sort(Comparator.comparingLong(StatusBarNotification::getPostTime).reversed());
        return messages;
    }
    static String conversationKey(StatusBarNotification item) {
        String shortcut = item.getNotification().getShortcutId();
        // A notification key is safer than guessing identity from a person's display name.
        return shortcut == null || shortcut.isEmpty() ? "notification:" + item.getKey()
            : "shortcut:" + item.getPackageName() + ":" + shortcut;
    }
    static Map<String, List<StatusBarNotification>> conversations() {
        Map<String, List<StatusBarNotification>> threads = new LinkedHashMap<>();
        for (StatusBarNotification item : current())
            threads.computeIfAbsent(conversationKey(item), ignored -> new ArrayList<>()).add(item);
        return threads;
    }
    static Notification.Action replyAction(StatusBarNotification item) {
        Notification.Action[] actions = item.getNotification().actions;
        if (actions != null) for (Notification.Action action : actions) {
            if (action == null || action.actionIntent == null || !item.getPackageName().equals(action.actionIntent.getCreatorPackage()) || replyInput(action) == null) continue;
            if (android.os.Build.VERSION.SDK_INT >= 31 && action.actionIntent.isImmutable()) continue;
            if (android.os.Build.VERSION.SDK_INT >= 28 && action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_NONE
                && action.getSemanticAction() != Notification.Action.SEMANTIC_ACTION_REPLY) continue;
            return action;
        }
        return null;
    }
    static RemoteInput replyInput(Notification.Action action) {
        RemoteInput[] inputs = action.getRemoteInputs();
        if (inputs != null) for (RemoteInput input : inputs) if (input.getAllowFreeFormInput()) return input;
        return null;
    }
}
