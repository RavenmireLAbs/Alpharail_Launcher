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
            icon.setBackground(background(accent(), 28));
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
    private boolean expanded;
    private boolean dark() { int mode=prefs().getInt("bubbleTheme",0); return mode==2 || (mode==0 && prefs().getInt("theme",0)==1); }
    private int ink() { return dark()?0xfff5f5fa:0xff202124; }
    private int surface() { return dark()?0xff292b35:0xffeeeff5; }
    private int accent() { return new int[]{0xff4039b4,0xff1769c2,0xff00796b,0xffad285d,0xff8c4828,0xff6554b0}[Math.max(0,Math.min(5,prefs().getInt("bubbleAccent",0)))]; }
    private String appName(String pkg) {
        try { return service.getPackageManager().getApplicationLabel(service.getPackageManager().getApplicationInfo(pkg,0)).toString(); }
        catch(Exception ignored){return pkg;}
    }
    private boolean previews(StatusBarNotification item) {
        return prefs().getBoolean("bubblePreviews",false) && !prefs().getStringSet("chatHidePreviews",Collections.emptySet()).contains(item.getPackageName());
    }
    private String title(StatusBarNotification item) {
        return previews(item) && item.getNotification().extras!=null ? String.valueOf(item.getNotification().extras.getCharSequence(Notification.EXTRA_TITLE,appName(item.getPackageName()))) : appName(item.getPackageName());
    }
    private TextView control(String text,Runnable action) { TextView v=button(text,action);v.setTextColor(ink());return v; }
    private LinearLayout column() { LinearLayout v=new LinearLayout(service);v.setOrientation(1);return v; }
    private TextView avatar(String title) {
        TextView v=button(title.isEmpty()?"?":new String(Character.toChars(title.codePointAt(0))).toUpperCase(Locale.getDefault()),()->{});
        v.setGravity(Gravity.CENTER);v.setBackground(background(accent(),24));v.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return v;
    }
    private void mount(LinearLayout content) {
        WindowManager.LayoutParams p=params(Math.min(dp(expanded?440:350),service.getResources().getDisplayMetrics().widthPixels-dp(24)),
            Math.min(dp(expanded?680:500),service.getResources().getDisplayMetrics().heightPixels-dp(140)),true);p.x=dp(12);p.y=dp(60);
        content.setBackground(background(dark()?0xff191a21:0xfffafaff,24));content.setPadding(dp(10),dp(8),dp(10),dp(10));
        content.setOnKeyListener((v,key,event)->{if(key==KeyEvent.KEYCODE_BACK && event.getAction()==KeyEvent.ACTION_UP){closePanel();return true;}return false;});
        try {windows.addView(content,p);panel=content;content.setFocusableInTouchMode(true);content.requestFocus();}catch(RuntimeException ignored){}
    }
    private void openPanel() { showPanel(null); }
    private void showPanel(StatusBarNotification selected) {
        closePanel();
        List<StatusBarNotification> active=items();
        if(service.getSystemService(KeyguardManager.class).isKeyguardLocked() || !Settings.canDrawOverlays(service))return;
        if(selected!=null) { StatusBarNotification match=null;for(StatusBarNotification item:active)if(item.getKey().equals(selected.getKey()))match=item;selected=match; }
        final StatusBarNotification thread=selected;
        LinearLayout content=column();
        LinearLayout header=new LinearLayout(service);header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(control(thread==null?"Chats":"‹",()->showPanel(null)),new LinearLayout.LayoutParams(0,dp(48),1));
        header.addView(control(expanded?"Shrink":"Expand",()->{expanded=!expanded;showPanel(thread);}));
        header.addView(control("×",this::closePanel));content.addView(header);
        ScrollView scroll=new ScrollView(service);LinearLayout rows=column();scroll.addView(rows);content.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(thread==null) {
            Set<String> seen=new HashSet<>();
            for(StatusBarNotification item:active){
                if(!seen.add(ChatNotifications.conversationKey(item)))continue;
                LinearLayout row=new LinearLayout(service);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),dp(7),dp(4),dp(7));
                row.addView(avatar(title(item)),new LinearLayout.LayoutParams(dp(48),dp(48)));
                TextView label=control(title(item)+"\n"+appName(item.getPackageName()),()->showPanel(item));
                row.addView(label,new LinearLayout.LayoutParams(0,-2,1));row.setOnClickListener(v->showPanel(item));rows.addView(row);
            }
            if(active.isEmpty())rows.addView(control("No active messaging notifications",()->{}));
            content.addView(control("Dismiss until next notification",()->{dismissed=true;closePanel();removeBubble();}));
        } else {
            LinearLayout identity=new LinearLayout(service);identity.setGravity(Gravity.CENTER_VERTICAL);
            identity.addView(avatar(title(thread)),new LinearLayout.LayoutParams(dp(44),dp(44)));
            identity.addView(control(title(thread)+"\n"+appName(thread.getPackageName()),()->{}));rows.addView(identity);
            if(previews(thread)) {
                Notification n=thread.getNotification();
                android.os.Parcelable[] messages=n.extras==null?null:n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if(messages!=null && messages.length>0){
                    List<Notification.MessagingStyle.Message> parsed=Notification.MessagingStyle.Message.getMessagesFromBundleArray(messages);
                    for(int i=Math.max(0,parsed.size()-20);i<parsed.size();i++){
                        Notification.MessagingStyle.Message message=parsed.get(i);
                        CharSequence text=message.getText();
                        String sender=message.getSenderPerson()==null?"":String.valueOf(message.getSenderPerson().getName());
                        message(rows,(sender.isEmpty()?"":sender+"\n")+(text==null?"Attachment · Open original app":text.toString()),false);
                    }
                }else message(rows,n.extras==null?"Preview unavailable":String.valueOf(n.extras.getCharSequence(Notification.EXTRA_TEXT,"Preview unavailable")),false);
            }else message(rows,"Message previews are off. Enable Bubble message previews in settings to display notification text.",false);
            content.addView(control("Open in original app ↗",()->{
                PendingIntent open=thread.getNotification().contentIntent;
                if(open==null || !thread.getPackageName().equals(open.getCreatorPackage()) || (Build.VERSION.SDK_INT>=31 && !open.isActivity())){Toast.makeText(service,"Open action unavailable",0).show();return;}
                try{open.send();closePanel();}catch(PendingIntent.CanceledException e){Toast.makeText(service,"Conversation unavailable",0).show();}
            }));
            Notification.Action reply=ChatNotifications.replyAction(thread);
            if(reply!=null){
                LinearLayout composer=new LinearLayout(service);composer.setGravity(Gravity.CENTER_VERTICAL);composer.setPadding(dp(4),dp(6),dp(4),0);
                EditText draft=new EditText(service);draft.setTextColor(ink());draft.setHintTextColor(dark()?0xffbbbbcc:0xff626575);draft.setHint("Message");draft.setMaxLines(3);
                draft.setBackground(background(surface(),22));draft.setPadding(dp(14),dp(10),dp(14),dp(10));
                draft.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});composer.addView(draft,new LinearLayout.LayoutParams(0,-2,1));
                TextView send=button("Send",()->{});send.setBackground(background(accent(),24));
                send.setOnClickListener(v->{
                    String text=draft.getText().toString().trim();if(text.isEmpty())return;send.setEnabled(false);
                    boolean allowed=false;for(StatusBarNotification item:items())if(item.getKey().equals(thread.getKey()))allowed=true;
                    if(!allowed){draft.setError("Notification no longer available");send.setEnabled(true);return;}
                    try{RemoteInput input=ChatNotifications.replyInput(reply);Bundle result=new Bundle();result.putCharSequence(input.getResultKey(),text);
                        Intent intent=new Intent();RemoteInput.addResultsToIntent(reply.getRemoteInputs(),intent,result);reply.actionIntent.send(service,0,intent);
                        draft.setText("");if(previews(thread))message(rows,"You · Submitted\n"+text,true);
                        scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
                        Toast.makeText(service,"Reply submitted—not a delivery receipt",0).show();
                    }catch(PendingIntent.CanceledException|RuntimeException e){draft.setError("Could not send; draft kept");}send.setEnabled(true);
                });composer.addView(send);content.addView(composer);
            } else content.addView(control("Reply in the original app",()->{}));
        }
        mount(content);
    }
    private void message(LinearLayout rows,String text,boolean outgoing){
        LinearLayout row=new LinearLayout(service);row.setGravity(outgoing?Gravity.RIGHT:Gravity.LEFT);row.setPadding(dp(4),dp(5),dp(4),dp(5));
        TextView body=new TextView(service);body.setText(text.length()>4000?text.substring(0,4000)+"…":text);body.setTextColor(outgoing?Color.WHITE:ink());body.setTextSize(14);
        body.setPadding(dp(14),dp(10),dp(14),dp(10));body.setMaxWidth(dp(expanded?330:260));body.setBackground(background(outgoing?accent():surface(),18));body.setTextIsSelectable(true);
        row.addView(body);rows.addView(row);
    }
    private void closePanel(){if(panel!=null){try{windows.removeView(panel);}catch(RuntimeException ignored){}panel=null;}}
    private void removeBubble(){if(bubble!=null){try{windows.removeView(bubble);}catch(RuntimeException ignored){}bubble=null;}}
    void stop(){handler.removeCallbacks(pulse);closePanel();removeBubble();}
}
