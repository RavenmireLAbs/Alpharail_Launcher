package com.ravenmire.alpharail;

import android.app.Notification;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Private, bounded notification snapshots. Never serializes pending intents or URI grants. */
final class ChatHistory {
    static final long TTL = 6 * 60 * 60 * 1000L;
    private static File folder(Context c) { File f = new File(c.getNoBackupFilesDir(), "chat-history"); f.mkdirs(); return f; }
    static synchronized JSONArray read(Context c) {
        File dir = folder(c), index = new File(dir, "index.json");
        JSONArray valid = new JSONArray(); long now = System.currentTimeMillis();
        try {
            if (index.length() > 2 * 1024 * 1024) { clear(c); return valid; }
            if (index.exists()) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream in = new FileInputStream(index)) { byte[] b = new byte[4096]; int n; while ((n = in.read(b)) != -1) bytes.write(b, 0, n); }
                JSONArray all = new JSONArray(bytes.toString("UTF-8"));
                for (int i = 0; i < all.length(); i++) {
                    JSONObject item = all.getJSONObject(i); long at = item.getLong("at");
                    if (at <= now && now - at < TTL) valid.put(item);
                }
            }
            save(c, valid);
        } catch (IOException | JSONException e) { clear(c); }
        Set<String> keep = new HashSet<>();
        for (int i = 0; i < valid.length(); i++) keep.add(valid.optJSONObject(i).optString("photo"));
        File[] files = dir.listFiles(); if (files != null) for (File file : files)
            if (file.getName().endsWith(".jpg") && !keep.contains(file.getName())) file.delete();
        return valid;
    }
    private static void save(Context c, JSONArray items) throws IOException {
        android.util.AtomicFile file = new android.util.AtomicFile(new File(folder(c), "index.json"));
        FileOutputStream out = null;
        try { out = file.startWrite(); out.write(items.toString().getBytes(StandardCharsets.UTF_8)); file.finishWrite(out); }
        catch (IOException e) { if (out != null) file.failWrite(out); throw e; }
    }
    static synchronized void clear(Context c) { File[] files = folder(c).listFiles(); if (files != null) for (File f : files) f.delete(); }
    private static String hash(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        StringBuilder id = new StringBuilder(); for (byte b : digest) id.append(String.format(Locale.ROOT, "%02x", b & 255)); return id.toString();
    }
    static synchronized void capture(Context c, StatusBarNotification item) {
        android.content.SharedPreferences prefs = c.getSharedPreferences("still", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("chatHistory", false) || !prefs.getBoolean("chatsEnabled", false)
            || !android.os.Process.myUserHandle().equals(item.getUser()) || !ChatNotifications.isMessagingApp(c, item.getPackageName())) return;
        Notification n = item.getNotification();
        if (n.extras == null || n.visibility == Notification.VISIBILITY_SECRET || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (prefs.getStringSet("chatExcluded", Collections.emptySet()).contains(item.getPackageName()) || prefs.getStringSet("locked", Collections.emptySet()).contains(item.getPackageName()) || prefs.getStringSet("hidden", Collections.emptySet()).contains(item.getPackageName())) return;
        try {
            JSONArray all = read(c);
            String text = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT, n.extras.getCharSequence(Notification.EXTRA_TEXT, "")));
            String title = String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE, "Conversation"));
            String id = hash(item.getKey() + "|" + item.getPostTime() + "|" + text);
            for (int i = 0; i < all.length(); i++) if (id.equals(all.getJSONObject(i).optString("id"))) return;
            JSONObject record = new JSONObject().put("id", id).put("pkg", item.getPackageName()).put("title", title.substring(0, Math.min(title.length(), 300)))
                .put("text", text.substring(0, Math.min(text.length(), 4000))).put("at", item.getPostTime());
            if (System.currentTimeMillis() - item.getPostTime() >= TTL) return;
            Bitmap bitmap = n.extras.getParcelable(Notification.EXTRA_PICTURE);
            if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                float scale = Math.min(1f, 640f / Math.max(bitmap.getWidth(), bitmap.getHeight()));
                Bitmap small = Bitmap.createScaledBitmap(bitmap, Math.max(1, (int)(bitmap.getWidth()*scale)), Math.max(1, (int)(bitmap.getHeight()*scale)), true);
                String photo = id + ".jpg";
                try (OutputStream out = new FileOutputStream(new File(folder(c), photo))) { small.compress(Bitmap.CompressFormat.JPEG, 75, out); }
                record.put("photo", photo);
            }
            android.os.Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            StringBuilder attachments = new StringBuilder();
            if (messages != null) for (Notification.MessagingStyle.Message message : Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages)) {
                if (message.getDataMimeType() != null) attachments.append(message.getDataMimeType()).append(" · ");
            }
            record.put("attachments", attachments.toString()); all.put(record);
            while (all.length() > 100) all.remove(0);
            save(c, all); read(c);
        } catch (Exception ignored) { }
    }
    static synchronized void outgoing(Context c, String pkg, String text) {
        if (!c.getSharedPreferences("still", Context.MODE_PRIVATE).getBoolean("chatHistory", false)) return;
        try {
            JSONArray all = read(c); long now = System.currentTimeMillis();
            all.put(new JSONObject().put("id", hash(pkg + now + text)).put("pkg", pkg).put("title", "You · Reply from Alpharail")
                .put("text", text.substring(0, Math.min(4000, text.length()))).put("at", now));
            while (all.length() > 100) all.remove(0);
            save(c, all); read(c);
        } catch (Exception ignored) { }
    }
    static Bitmap photo(Context c, String name) {
        if (!name.matches("[0-9a-f]{64}\\.jpg")) return null;
        return BitmapFactory.decodeFile(new File(folder(c), name).getPath());
    }
}
