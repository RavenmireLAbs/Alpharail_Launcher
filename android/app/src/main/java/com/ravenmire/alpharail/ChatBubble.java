package com.ravenmire.alpharail;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Optional notification-listener-owned overlay; contains no persistent message cache. */
final class ChatBubble {
    private final ChatNotifications service;
    private final WindowManager windows;
    private View bubble, panel;
    private boolean dismissed;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pulse = () -> update();
    ChatBubble(ChatNotifications service) { this.service = service; windows = service.getSystemService(WindowManager.class); }
    private android.content.SharedPreferences prefs() { return service.getSharedPreferences("still", Context.MODE_PRIVATE); }
    private int dp(int n) { return Math.round(n * service.getResources().getDisplayMetrics().density); }
    private List<StatusBarNotification> items() {
        List<StatusBarNotification> out = new ArrayList<>();
        try {
            StatusBarNotification[] active = service.getActiveNotifications();
            if (active != null) for (StatusBarNotification item : active) {
                String pkg = item.getPackageName(); Notification n = item.getNotification();
                if (!android.os.Process.myUserHandle().equals(item.getUser()) || !ChatNotifications.isMessagingApp(service, pkg)
                    || n.visibility == Notification.VISIBILITY_SECRET || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0
                    || prefs().getStringSet("locked", Collections.emptySet()).contains(pkg)
                    || prefs().getStringSet("chatExcluded", Collections.emptySet()).contains(pkg)) continue;
                boolean hidden = false;
                for (String key : prefs().getStringSet("hidden", Collections.emptySet())) if (key.startsWith(pkg + "/")) hidden = true;
                if (!hidden && (Notification.CATEGORY_MESSAGE.equals(n.category) || (n.extras != null && Notification.MessagingStyle.class.getName().equals(n.extras.getString(Notification.EXTRA_TEMPLATE))))) out.add(item);
            }
        } catch (RuntimeException ignored) { }
        out.sort(Comparator.comparingLong(StatusBarNotification::getPostTime).reversed()); return out;
    }
    private GradientDrawable background(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private TextView button(String text, Runnable action) {
        TextView view = new TextView(service); view.setText(text); view.setTextColor(Color.WHITE); view.setTextSize(15);
        view.setPadding(dp(12), dp(10), dp(12), dp(10)); view.setMinHeight(dp(48)); view.setFocusable(true); view.setOnClickListener(v -> action.run()); return view;
    }
    private WindowManager.LayoutParams params(int width, int height, boolean focus) {
        WindowManager.LayoutParams p = new WindowManager.LayoutParams(width, height, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_SECURE | (focus ? 0 : WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE), PixelFormat.TRANSLUCENT);
        p.gravity = Gravity.TOP | Gravity.LEFT; p.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE; return p;
    }
    void changed() { dismissed = false; if (panel != null) closePanel(); update(); }
    void update() {
        handler.removeCallbacks(pulse);
        boolean enabled = ChatNotifications.available() && prefs().getBoolean("chatBubble", false) && Settings.canDrawOverlays(service);
        boolean locked = service.getSystemService(KeyguardManager.class).isKeyguardLocked();
        if (!enabled || locked || dismissed || items().isEmpty()) { removeBubble(); closePanel(); }
        else if (bubble == null) {
            TextView icon = button("●", this::openPanel); icon.setGravity(Gravity.CENTER); icon.setContentDescription("Messaging bubble. Tap to open; drag to move.");
            icon.setBackground(background(0xff35324b, 28));
            WindowManager.LayoutParams p = params(dp(56), dp(56), false); p.x = prefs().getInt("bubbleX", dp(8)); p.y = prefs().getInt("bubbleY", dp(180));
            p.x = Math.max(0, Math.min(p.x, service.getResources().getDisplayMetrics().widthPixels - dp(56)));
            p.y = Math.max(dp(28), Math.min(p.y, service.getResources().getDisplayMetrics().heightPixels - dp(100)));
            icon.setOnTouchListener(new View.OnTouchListener() {
                float x,y; int ox,oy; boolean moved;
                public boolean onTouch(View v, android.view.MotionEvent e) {
                    if (e.getAction() == 0) { x=e.getRawX(); y=e.getRawY(); ox=p.x; oy=p.y; moved=false; return true; }
                    if (e.getAction() == 2) {
                        if (Math.abs(e.getRawX()-x)+Math.abs(e.getRawY()-y)>dp(8)) moved=true;
                        p.x=Math.max(0,Math.min(service.getResources().getDisplayMetrics().widthPixels-dp(56),ox+(int)(e.getRawX()-x)));
                        p.y=Math.max(dp(28),Math.min(service.getResources().getDisplayMetrics().heightPixels-dp(100),oy+(int)(e.getRawY()-y)));
                        try { windows.updateViewLayout(v,p); } catch (RuntimeException ignored) { } return true;
                    }
                    if (e.getAction() == 1) { if (!moved) v.performClick(); else prefs().edit().putInt("bubbleX",p.x).putInt("bubbleY",p.y).apply(); return true; }
                    return false;
                }
            });
            try { windows.addView(icon,p); bubble=icon; } catch (RuntimeException ignored) { }
        }
        if (enabled) handler.postDelayed(pulse, 1000);
    }
    private void openPanel() {
        closePanel();
        if (service.getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        LinearLayout content = new LinearLayout(service); content.setOrientation(1); content.setPadding(dp(8),dp(8),dp(8),dp(8)); content.setBackground(background(0xff202126,20));
        content.addView(button("Close",this::closePanel));
        ScrollView scroll = new ScrollView(service); LinearLayout rows = new LinearLayout(service); rows.setOrientation(1); scroll.addView(rows); content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        for (StatusBarNotification item : items()) {
            Notification n=item.getNotification(); String pkg=item.getPackageName(); String name=pkg;
            try { name=service.getPackageManager().getApplicationLabel(service.getPackageManager().getApplicationInfo(pkg,0)).toString(); } catch (Exception ignored) { }
            boolean preview=prefs().getBoolean("bubblePreviews",false) && !prefs().getStringSet("chatHidePreviews",Collections.emptySet()).contains(pkg);
            String title=preview && n.extras!=null ? String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TITLE,name)) : name;
            PendingIntent open=n.contentIntent;
            rows.addView(button(title, () -> {
                if (open==null || !pkg.equals(open.getCreatorPackage()) || (Build.VERSION.SDK_INT>=31 && !open.isActivity())) return;
                try { open.send(); closePanel(); } catch (PendingIntent.CanceledException ignored) { Toast.makeText(service,"Conversation unavailable",0).show(); }
            }));
            if (preview && n.extras!=null) { TextView body=button(String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT,"")),()->{}); body.setMaxLines(3); rows.addView(body); }
            Notification.Action reply=ChatNotifications.replyAction(item);
            if (reply!=null) {
                EditText draft=new EditText(service); draft.setTextColor(Color.WHITE); draft.setHintTextColor(0xffbbbbbb); draft.setHint("Reply");
                draft.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)}); rows.addView(draft);
                TextView send=button("Send",()->{}); send.setOnClickListener(v->{
                    String text=draft.getText().toString().trim(); if(text.isEmpty())return; send.setEnabled(false);
                    try { RemoteInput input=ChatNotifications.replyInput(reply); Bundle results=new Bundle();results.putCharSequence(input.getResultKey(),text);
                        Intent intent=new Intent();RemoteInput.addResultsToIntent(reply.getRemoteInputs(),intent,results);reply.actionIntent.send(service,0,intent);
                        draft.setText(""); Toast.makeText(service,"Reply submitted",0).show();
                    } catch (PendingIntent.CanceledException | RuntimeException e) { draft.setError("Could not send; draft kept"); }
                    send.setEnabled(true);
                });rows.addView(send);
            }
        }
        content.addView(button("Dismiss bubble until next notification",()->{dismissed=true;closePanel();removeBubble();}));
        WindowManager.LayoutParams p=params(Math.min(dp(350),service.getResources().getDisplayMetrics().widthPixels-dp(24)),Math.min(dp(500),service.getResources().getDisplayMetrics().heightPixels-dp(140)),true);p.x=dp(12);p.y=dp(60);
        content.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK && event.getAction()==KeyEvent.ACTION_UP){closePanel();return true;}return false;});
        try { windows.addView(content,p);panel=content;content.setFocusableInTouchMode(true);content.requestFocus(); } catch(RuntimeException ignored) { }
    }
    private void closePanel(){if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}}
    private void removeBubble(){if(bubble!=null){try{windows.removeView(bubble);}catch(RuntimeException ignored){}bubble=null;}}
    void stop(){handler.removeCallbacks(pulse);closePanel();removeBubble();}
}
