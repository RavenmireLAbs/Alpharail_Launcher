package com.ravenmire.alpharail;

import android.app.*;
import android.app.role.RoleManager;
import android.appwidget.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.text.Collator;
import java.util.*;

public class HomeActivity extends Activity {
    private int paper, ink, moss, dividerColor;
    private static final int PRIMARY = 0xff4039b4;
    private int accent, surface;
    private EditText search;
    private String query = "";
    private int beforeSearch;
    private List<ResolveInfo> apps = Collections.emptyList();
    private Set<String> lockedApps = Collections.emptySet();
    private android.content.SharedPreferences prefs;
    private ScrollView scroll;
    private AlphabetRail indexScroll;
    private TextView letterBubble, selectedLetter;
    private final Runnable hideLetter = () -> showLetter(null);
    private LinearLayout root, list, index;
    private AppWidgetHost host;
    private AppWidgetManager widgets;
    private final Set<AlertDialog> privateChatDialogs = new HashSet<>();
    private void showPrivateChatDialog(AlertDialog dialog) {
        if (prefs.getBoolean("chatBlockScreenshots", true)) dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        privateChatDialogs.add(dialog);
        dialog.setOnDismissListener(ignored -> privateChatDialogs.remove(dialog));
        dialog.show();
    }
    private int pending = -1, pendingRecovery = -1;
    private String pendingLaunch, pendingShortcut;
    private LinearLayout undoBar, appArea;
    private Runnable undoAction;
    private final Runnable expireUndo = this::dismissUndo;
    private static final int EXPORT_SETTINGS = 23, IMPORT_SETTINGS = 24, EXPORT_LOOK = 25, IMPORT_LOOK = 26;
    private static final String[] APPEARANCE = {"iconStyle", "iconSize", "iconShape", "fontSize", "fontShape", "railSide", "theme"};
    private static final int[] DEFAULTS = {0, 1, 0, 1, 0, 0, 0}, MAXIMUMS = {3, 2, 3, 3, 3, 1, 1};
    private boolean pendingLockSettings;
    private static final int CONFIRM_LAUNCH = 21, CONFIRM_LOCK_SETTINGS = 22;
    private boolean welcome;
    private TextView clock, date;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            if (clock != null && date != null) {
                Date now = new Date();
                String time = android.text.format.DateFormat.getTimeFormat(HomeActivity.this).format(now);
                clock.setText(time); clock.setContentDescription(time + ", open Clock");
                String day = android.text.format.DateFormat.format("EEEE, MMMM d", now).toString().toUpperCase(Locale.getDefault());
                date.setText(day); date.setContentDescription(day + ", open Calendar");
            }
            handler.postDelayed(this, 15000);
        }
    };

    @Override public void onCreate(Bundle state) {
        prefs = getSharedPreferences("still", MODE_PRIVATE);
        boolean black = prefs.getInt("theme", 0) == 1;
        if (black) setTheme(getResources().getIdentifier("StillBlackTheme", "style", getPackageName()));
        super.onCreate(state);
        paper = black ? 0xff000000 : 0xffffffff;
        ink = black ? 0xfff3f5fa : 0xff202124;
        moss = black ? 0xffacb5c8 : 0xff545e74;
        dividerColor = black ? 0xff242833 : 0xffe1e4e7;
        accent = black ? 0xffa0adff : PRIMARY;
        surface = black ? 0xff151821 : 0xfff3f5fa;
        host = new AppWidgetHost(this, 101);
        widgets = AppWidgetManager.getInstance(this);
        if (state != null) {
            pending = state.getInt("pending", -1);
            pendingRecovery = state.getInt("pendingRecovery", -1);
            pendingLaunch = state.getString("pendingLaunch");
            pendingShortcut = state.getString("pendingShortcut");
            pendingLockSettings = state.getBoolean("pendingLockSettings");
            query = state.getString("query", "");
            beforeSearch = state.getInt("beforeSearch");
        }
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        getWindow().getDecorView().setSystemUiVisibility(black ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (prefs.getBoolean("welcome", false)) home(state == null ? 0 : state.getInt("scroll")); else welcome();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("pending", pending);
        state.putInt("pendingRecovery", pendingRecovery);
        state.putString("pendingLaunch", pendingLaunch);
        state.putString("pendingShortcut", pendingShortcut);
        state.putBoolean("pendingLockSettings", pendingLockSettings);
        state.putString("query", query);
        state.putInt("beforeSearch", beforeSearch);
        state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    }
    private TextView chatsButton;
    private final Map<String, Long> seenChatNotifications = new HashMap<>();
    private final Runnable updateChatsDot = () -> refreshChatsDot(false);
    private void refreshChatsDot(boolean acknowledge) {
        if (chatsButton == null) return;
        boolean unread = false;
        boolean enabled = chatEnabled() && ChatNotifications.available();
        if (enabled) {
            Set<String> active = new HashSet<>();
            for (StatusBarNotification item : ChatNotifications.current()) {
                if (!chatAllowed(item.getPackageName())) continue;
                String key = item.getKey(); long time = item.getPostTime();
                active.add(key);
                if (acknowledge) seenChatNotifications.put(key, time);
                else if (!Objects.equals(seenChatNotifications.get(key), time)) unread = true;
            }
            seenChatNotifications.keySet().retainAll(active);
        }
        chatsButton.setText(unread ? "Chats  •" : "Chats");
        chatsButton.setContentDescription(unread ? "Chats, new messages" : "Chats");
    }
    @Override protected void onStart() { super.onStart(); host.startListening(); handler.post(tick); ChatNotifications.observers.add(updateChatsDot); updateChatsDot.run(); }
    @Override protected void onStop() { handler.removeCallbacks(tick); ChatNotifications.observers.remove(updateChatsDot);
        for (AlertDialog dialog : new ArrayList<>(privateChatDialogs)) dialog.dismiss();
        privateChatDialogs.clear(); chatAvatars.evictAll(); sessionReplies.evictAll(); host.stopListening(); super.onStop(); }
    @Override protected void onResume() {
        super.onResume(); updateForeground = true;
        if (!welcome && scroll != null) {
            if (reloadApps()) refresh();
            if (prefs.getBoolean("autoUpdates", false)) checkUpdates(false);
        }
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (!welcome && scroll != null) { clearSearch(); scroll.post(() -> scroll.scrollTo(0, 0)); } }

    @Override protected void onDestroy() { handler.removeCallbacks(expireUndo); handler.removeCallbacks(hideLetter); super.onDestroy(); }
    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, int size, int color) {
        TextView v = new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v;
    }
    private TextView action(String value, Runnable call) {
        TextView v = text(value, 15, moss); v.setGravity(Gravity.CENTER_VERTICAL); v.setMinHeight(dp(44));
        android.util.TypedValue attr = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, attr, true);
        v.setBackgroundResource(attr.resourceId); v.setFocusable(true); v.setOnClickListener(x -> call.run()); return v;
    }
    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private void base() {
        root = column(); root.setFocusableInTouchMode(true); root.setBackgroundColor(paper); root.setPadding(dp(18), dp(14), dp(18), 0); setContentView(root);
    }
    private FrameLayout heroFrame() {
        FrameLayout frame = new FrameLayout(this) {
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                paint.setStyle(android.graphics.Paint.Style.FILL); paint.setColor(0xff362969);
                canvas.drawCircle(getWidth() * .93f, getHeight() * .24f, dp(65), paint);
                paint.setStyle(android.graphics.Paint.Style.STROKE); paint.setStrokeWidth(dp(1)); paint.setColor(0xff685a9b);
                for (int r = 38; r <= 110; r += 14) canvas.drawCircle(getWidth() * .93f, getHeight() * .24f, dp(r), paint);
            }
        };
        frame.setBackground(rounded(0xff20194b, 26)); frame.setClipToOutline(true);
        frame.setWillNotDraw(false);
        return frame;
    }
    private void welcome() {
        welcome = true; base(); root.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout cover = heroFrame();
        LinearLayout headline = column(); headline.setPadding(dp(22), dp(24), dp(22), dp(20)); cover.addView(headline);
        TextView wordmark = text("ALPHARAIL  /  INDEX", 12, 0xffd9dcff); wordmark.setLetterSpacing(.19f); headline.addView(wordmark);
        TextView title = text("Make room\nfor less.", 46, 0xffffffff);
        title.setLetterSpacing(-.045f); title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, dp(30), 0, 0); headline.addView(title);
        root.addView(cover, new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text("Every app, one screen.\n\nSearch or scroll the alphabetical list. Hold an empty area for widgets and launcher settings.", 17, moss);
        detail.setPadding(0, dp(14), dp(8), dp(20)); root.addView(detail);
        Button open = new Button(this);
        open.setText("Open launcher"); open.setAllCaps(false);
        open.setTextColor(0xffffffff); open.setTextSize(16);
        open.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable fill = rounded(PRIMARY, 16);
        open.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x40ffffff), fill, null));
        open.setMinHeight(dp(52)); open.setPadding(dp(18), dp(8), dp(18), dp(8));
        open.setOnClickListener(v -> { prefs.edit().putBoolean("welcome", true).apply(); home(0); });
        root.addView(open, new LinearLayout.LayoutParams(-1, -2));
    }
    private static final String[] WALLPAPERS = {"Porcelain", "Fold", "Pearl", "Glacier", "Linen", "Eclipse", "Fault", "Obsidian", "Ink", "Orbit", "Obsidian Amethyst", "Obsidian Copper", "Ink Aurora", "Ink Ember", "Cloud Sect", "Moonblade", "Bamboo Vigil", "Jade Dragon", "Crimson Pass", "Lotus Domain", "Snow Monastery", "Astral Stair", "Lantern River", "Phoenix Peak"};
    private boolean homeShowsWallpaper() { return prefs.getBoolean("showHomeWallpaper", false); }
    private int bundledWallpaper() {
        int index = prefs.getInt("bundledWallpaper", -1);
        return index >= 0 && index < WALLPAPERS.length ? index : -1;
    }
    private boolean lightHomeWallpaper() { return homeShowsWallpaper() && bundledWallpaper() >= 0 && bundledWallpaper() < 5; }
    private int wallpaperRes(int index) {
        return getResources().getIdentifier("wall_" + WALLPAPERS[index].toLowerCase(Locale.ROOT).replace(' ', '_'), "drawable", getPackageName());
    }
    private void applyHomeBackground() {
        if (!homeShowsWallpaper()) return;
        Window window = getWindow();
        int selected = bundledWallpaper();
        if (selected < 0) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER);
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
        } else {
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeResource(getResources(), wallpaperRes(selected));
            if (bitmap != null) window.setBackgroundDrawable(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap) {
                private final android.graphics.RectF crop = new android.graphics.RectF();
                private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
                @Override public void draw(android.graphics.Canvas canvas) {
                    android.graphics.Rect bounds = getBounds();
                    float scale = Math.max((float)bounds.width() / bitmap.getWidth(), (float)bounds.height() / bitmap.getHeight());
                    float w = bitmap.getWidth() * scale, h = bitmap.getHeight() * scale;
                    crop.set(bounds.exactCenterX() - w / 2, bounds.exactCenterY() - h / 2,
                        bounds.exactCenterX() + w / 2, bounds.exactCenterY() + h / 2);
                    canvas.drawBitmap(bitmap, null, crop, paint);
                }
            });
        }
        root.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT);
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 29) { window.setStatusBarContrastEnforced(false); window.setNavigationBarContrastEnforced(false); }
        window.getDecorView().setSystemUiVisibility(lightHomeWallpaper() ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0);
    }
    private void styleWallpaperText(TextView label) {
        if (!homeShowsWallpaper()) return;
        label.setTextColor(lightHomeWallpaper() ? 0xff191919 : 0xffffffff);
        label.setShadowLayer(dp(3), 0, dp(1), lightHomeWallpaper() ? 0xffffffff : 0xff000000);
    }
    private void selectWallpaper(boolean enabled, int bundled) {
        prefs.edit().putBoolean("showHomeWallpaper", enabled).putInt("bundledWallpaper", bundled).apply();
        recreate();
    }
    private void showWallpaperSettings() {
        showMenu("Home wallpaper", Arrays.asList("No wallpaper", "Android wallpaper", "Abstract", "Wuxia & Xianxia"), Arrays.asList(
            () -> selectWallpaper(false, bundledWallpaper()), () -> selectWallpaper(true, -1),
            this::showAbstractWallpapers, () -> showWallpaperGallery(14)));
    }
    private void showAbstractWallpapers() {
        showMenu("Abstract wallpapers", Arrays.asList("Light abstract wallpapers", "Dark abstract wallpapers", "Back"),
            Arrays.asList(() -> showWallpaperGallery(0), () -> showWallpaperGallery(5), this::showWallpaperSettings));
    }
    private void showWallpaperGallery(int start) {
        ScrollView scroll = new ScrollView(this);
        LinearLayout tiles = column(); tiles.setPadding(dp(18), 0, dp(18), dp(12)); scroll.addView(tiles);
        AlertDialog gallery = new AlertDialog.Builder(this).setTitle(start == 0 ? "Light abstract wallpapers" : start == 5 ? "Dark abstract wallpapers" : "Wuxia & Xianxia")
            .setView(scroll).setNegativeButton("Back", (d,w) -> { if (start < 14) showAbstractWallpapers(); else showWallpaperSettings(); }).create();
        LinearLayout row = null;
        int end = start == 0 ? 5 : start == 5 ? 14 : WALLPAPERS.length;
        for (int i = start; i < end; i++) {
            if ((i - start) % 2 == 0) { row = new LinearLayout(this); tiles.addView(row); }
            int selected = i;
            String label = WALLPAPERS[i] + (homeShowsWallpaper() && bundledWallpaper() == i ? " · Active" : "");
            LinearLayout tile = column(); tile.setPadding(dp(5), dp(4), dp(5), dp(4));
            ImageView photo = new ImageView(this);
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options(); options.inSampleSize = 4;
            photo.setImageBitmap(android.graphics.BitmapFactory.decodeResource(getResources(), wallpaperRes(i), options));
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(rounded(surface, 12)); photo.setClipToOutline(true);
            tile.addView(photo, new LinearLayout.LayoutParams(-1, dp(134)));
            tile.addView(action(label, () -> { gallery.dismiss(); previewWallpaper(selected, start); }));
            photo.setOnClickListener(v -> { gallery.dismiss(); previewWallpaper(selected, start); });
            row.addView(tile, new LinearLayout.LayoutParams(0, -2, 1));
        }
        if (row != null && (end - start) % 2 != 0) row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        gallery.show();
    }
    private void previewWallpaper(int selected, int group) {
        ImageView image = new ImageView(this); image.setImageResource(wallpaperRes(selected));
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setAdjustViewBounds(true); image.setMaxHeight(dp(470));
        new AlertDialog.Builder(this).setTitle(WALLPAPERS[selected]).setView(image)
            .setNegativeButton("Back", (d,w) -> showWallpaperGallery(group))
            .setPositiveButton("Apply", (d,w) -> selectWallpaper(true, selected)).show();
    }
    private void home(int y) {
        welcome = false; base(); applyHomeBackground();
        FrameLayout hero = homeShowsWallpaper() ? new FrameLayout(this) : heroFrame();
        LinearLayout face = column(); face.setPadding(dp(18), dp(13), dp(18), dp(9)); hero.addView(face);
        date = text("", 11, 0xffe0e2ff); date.setLetterSpacing(.15f); date.setFocusable(true); date.setOnClickListener(v -> openCalendar()); face.addView(date);
        clock = text("", 56, 0xffffffff); clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setLetterSpacing(-.06f); clock.setSingleLine(true);
        clock.setAutoSizeTextTypeUniformWithConfiguration(26, 56, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        clock.setFocusable(true); clock.setOnClickListener(v -> openClock());
        face.addView(clock, new LinearLayout.LayoutParams(-1, dp(74)));
        styleWallpaperText(date); styleWallpaperText(clock);
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        handler.removeCallbacks(tick); handler.post(tick);
        LinearLayout bar = new LinearLayout(this);
        TextView title = text("ALL APPS", 12, moss); title.setGravity(Gravity.CENTER_VERTICAL); title.setLetterSpacing(.13f);
        styleWallpaperText(title); bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        RoleManager role = android.os.Build.VERSION.SDK_INT >= 29 ? getSystemService(RoleManager.class) : null;
        if (role == null || !role.isRoleHeld(RoleManager.ROLE_HOME)) {
            TextView set = action("Set as Home", this::setHome); styleWallpaperText(set); bar.addView(set);
        }
        TextView chats = action("Chats", this::showChats); chatsButton = chats; chats.setTextColor(accent); updateChatsDot.run();
        chats.setPadding(dp(14), 0, dp(14), 0);
        chats.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x203448ce), rounded(homeShowsWallpaper() ? (lightHomeWallpaper() ? 0x66ffffff : 0x55000000) : surface, 24), null));
        styleWallpaperText(chats); bar.addView(chats);
        root.addView(bar);
        LinearLayout searchBar = new LinearLayout(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setTextSize(15);
        search.setTextColor(ink);
        search.setHintTextColor(moss);
        search.setBackground(rounded(homeShowsWallpaper() ? (lightHomeWallpaper() ? 0x66ffffff : 0x55000000) : surface, 16));
        if (homeShowsWallpaper()) { styleWallpaperText(search); search.setHintTextColor(lightHomeWallpaper() ? 0xff252525 : 0xffeeeeee); }
        search.setPadding(dp(16), 0, dp(12), 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setText(query);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView clear = action("×", this::clearSearch);
        clear.setGravity(Gravity.CENTER);
        clear.setTextSize(22); styleWallpaperText(clear);
        clear.setContentDescription("Clear app search");
        clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
        searchBar.addView(clear, new LinearLayout.LayoutParams(dp(40), dp(46)));
        root.addView(searchBar);
        View empty = new View(this);
        empty.setContentDescription("Launcher options. Touch and hold, or activate with accessibility services.");
        empty.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        empty.setOnClickListener(v -> {
            android.view.accessibility.AccessibilityManager accessibility = getSystemService(android.view.accessibility.AccessibilityManager.class);
            if (accessibility.isTouchExplorationEnabled()) showLauncherOptions();
        });
        empty.setFocusable(true);
        empty.setOnKeyListener((v, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_UP || (code != KeyEvent.KEYCODE_ENTER && code != KeyEvent.KEYCODE_DPAD_CENTER)) return false;
            showLauncherOptions(); return true;
        });
        emptyArea(empty);
        root.addView(empty, new LinearLayout.LayoutParams(-1, dp(24)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence old, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                String next = value.toString();
                if (searchKey(query).isEmpty() && !searchKey(next).isEmpty()) beforeSearch = scroll.getScrollY();
                query = next;
                clear.setVisibility(query.isEmpty() ? View.INVISIBLE : View.VISIBLE);
                populate(searchKey(query).isEmpty() ? beforeSearch : 0);
            }
            public void afterTextChanged(Editable value) {}
        });
        search.setOnEditorActionListener((view, id, event) -> {
            if (event != null) {
                if (event.getKeyCode() != KeyEvent.KEYCODE_ENTER && event.getKeyCode() != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
                if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
            } else if (id != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            String filter = searchKey(query);
            if (!filter.isEmpty()) {
                Set<String> hidden = saved("hidden");
                List<ResolveInfo> results = searchResults(filter, hidden);
                if (!results.isEmpty()) { openApp(results.get(0)); return true; }
            }
            dismissKeyboard(); return true;
        });
        FrameLayout appFrame = new FrameLayout(this); root.addView(appFrame, new LinearLayout.LayoutParams(-1, 0, 1));
        appArea = new LinearLayout(this); appArea.setLayoutDirection(View.LAYOUT_DIRECTION_LTR); appFrame.addView(appArea, new FrameLayout.LayoutParams(-1, -1));
        letterBubble = text("", 30, 0xffffffff); letterBubble.setGravity(Gravity.CENTER);
        letterBubble.setBackground(rounded(PRIMARY, 23)); letterBubble.setVisibility(View.GONE);
        letterBubble.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        appFrame.addView(letterBubble, new FrameLayout.LayoutParams(dp(68), dp(68), Gravity.CENTER));
        scroll = new ScrollView(this); list = column(); scroll.addView(list); list.setLayoutDirection(getResources().getConfiguration().getLayoutDirection());
        appArea.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        indexScroll = new AlphabetRail(); index = indexScroll;
        indexScroll.setBackground(rounded(homeShowsWallpaper() ? android.graphics.Color.TRANSPARENT : surface, 18));
        appArea.addView(indexScroll, new LinearLayout.LayoutParams(dp(48), -1));
        placeRail();
        undoBar = new LinearLayout(this); undoBar.setVisibility(View.GONE); root.addView(undoBar);
        dismissUndo();
        emptyArea(root); emptyArea(list); emptyArea(title);
        root.requestFocus();
        reloadApps();
        populate(y);
    }
    private void openClock() {
        openTool(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS), "Clock");
    }
    private void openCalendar() {
        openTool(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALENDAR), "Calendar");
    }
    private void openTool(Intent intent, String name) {
        Intent targetIntent = intent.getSelector() == null ? intent : intent.getSelector();
        ResolveInfo target = getPackageManager().resolveActivity(targetIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(targetIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (!handlers.isEmpty()) {
            ResolveInfo chosen = handlers.get(0);
            if (target != null && target.activityInfo != null) for (ResolveInfo handler : handlers) {
                if (component(target).equals(component(handler))) { chosen = handler; break; }
            }
            String packageName = chosen.activityInfo.packageName;
            if (saved("locked").contains(packageName)) {
                for (ResolveInfo app : apps) if (packageName.equals(app.activityInfo.packageName)) { openApp(app); return; }
                Toast.makeText(this, name + " is locked", Toast.LENGTH_SHORT).show(); return;
            }
            try { startActivity(targetIntent.setComponent(component(chosen))); return; }
            catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        for (ResolveInfo app : apps) if (name.equalsIgnoreCase(originalLabel(app))) { openApp(app); return; }
        Toast.makeText(this, "No " + name + " app available", Toast.LENGTH_SHORT).show();
    }

    private void dismissKeyboard() {
        getSystemService(InputMethodManager.class).hideSoftInputFromWindow(search.getWindowToken(), 0);
        root.requestFocus();
    }
    private void clearSearch() {
        dismissKeyboard();
        if (!query.isEmpty()) search.setText("");
    }
    @Override public void onBackPressed() {
        if (!welcome && !query.isEmpty()) { clearSearch(); return; }
        // Finishing the default Home activity here makes Android immediately reopen it.
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager roles = getSystemService(RoleManager.class);
            if (roles != null && roles.isRoleHeld(RoleManager.ROLE_HOME)) return;
        }
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolved = getPackageManager().resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (resolved != null && resolved.activityInfo != null && getPackageName().equals(resolved.activityInfo.packageName)) return;
        super.onBackPressed();
    }
    private void setHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager role = getSystemService(RoleManager.class);
            if (role.isRoleAvailable(RoleManager.ROLE_HOME) && !role.isRoleHeld(RoleManager.ROLE_HOME)) {
                startActivityForResult(role.createRequestRoleIntent(RoleManager.ROLE_HOME), 13); return;
            }
        }
        startActivity(new Intent(android.provider.Settings.ACTION_HOME_SETTINGS));
    }
    private Set<String> saved(String key) { return new HashSet<>(prefs.getStringSet(key, Collections.emptySet())); }
    private void store(String key, Set<String> values) { prefs.edit().putStringSet(key, values).apply(); }
    private void refresh() { populate(scroll.getScrollY()); }
    private final Map<String, String> originalLabels = new HashMap<>(), normalizedLabels = new HashMap<>();
    private final android.util.LruCache<String, android.graphics.drawable.Drawable.ConstantState> iconCache = new android.util.LruCache<>(192);
    private final android.util.LruCache<String, View> rowCache = new android.util.LruCache<>(192);
    private final android.util.LruCache<String, AppWidgetHostView> widgetViews = new android.util.LruCache<>(16);
    private String normalized(String value) { return normalizedLabels.computeIfAbsent(value, this::searchKey); }
    private String originalLabel(ResolveInfo app) { return originalLabels.computeIfAbsent(key(app), ignored -> app.loadLabel(getPackageManager()).toString()); }
    private String label(ResolveInfo app) {
        String alias = prefs.getString("appAlias:" + key(app), null);
        return alias == null ? originalLabel(app) : alias;
    }
    private ComponentName component(ResolveInfo app) { return new ComponentName(app.activityInfo.packageName, app.activityInfo.name); }
    private String key(ResolveInfo app) { return component(app).flattenToString(); }
    private boolean reloadApps() {
        List<ResolveInfo> current = new ArrayList<>(getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        current.removeIf(app -> app.activityInfo.packageName.equals(getPackageName()));
        Map<String, String> previousLabels = new HashMap<>(originalLabels);
        originalLabels.clear(); normalizedLabels.clear();
        Collator sorter = Collator.getInstance();
        current.sort((a,b) -> {
            int order = sorter.compare(label(a), label(b));
            return order == 0 ? key(a).compareTo(key(b)) : order;
        });
        boolean changed = current.size() != apps.size();
        for (int i = 0; !changed && i < current.size(); i++) {
            ResolveInfo app = current.get(i), previous = apps.get(i);
            changed = !key(app).equals(key(previous)) || !Objects.equals(originalLabel(app), previousLabels.get(key(previous)))
                || !Objects.equals(app.activityInfo.applicationInfo.sourceDir, previous.activityInfo.applicationInfo.sourceDir);
        }
        if (changed) { iconCache.evictAll(); rowCache.evictAll(); }
        apps = current;
        return changed;
    }
    // Keep the v0.1.0 membership sets and layer a stable user order on top.
    private List<String> ordered(String kind, List<String> available) {
        Set<String> members = saved(kind);
        Set<String> visible = new HashSet<>(available);
        List<String> order = new ArrayList<>();
        for (String key : prefs.getString(kind + "Order", "").split("\\n")) {
            if (visible.contains(key) && members.remove(key)) order.add(key);
        }
        for (String key : available) if (members.remove(key)) order.add(key);
        return order;
    }
    private List<String> favoriteOrder() {
        List<String> keys = new ArrayList<>();
        for (ResolveInfo app : apps) keys.add(key(app));
        return ordered("favorites", keys);
    }
    private List<String> widgetOrder() {
        List<String> ids = new ArrayList<>(saved("widgets"));
        ids.removeIf(id -> widgets.getAppWidgetInfo(Integer.parseInt(id)) == null);
        ids.sort(Comparator.comparingInt(Integer::parseInt));
        return ordered("widgets", ids);
    }
    private void saveOrder(String kind, List<String> order) {
        prefs.edit().putString(kind + "Order", String.join("\n", order)).apply();
    }
    private void move(String kind, List<String> order, String key, String neighbor) {
        int from = order.indexOf(key), to = order.indexOf(neighbor);
        if (from < 0 || to < 0 || to >= order.size()) return;
        Collections.swap(order, from, to);
        saveOrder(kind, order);
        refresh();
    }
    private void populate(int y) {
        handler.removeCallbacks(hideLetter); showLetter(null);
        list.removeAllViews(); index.removeAllViews();
        Set<String> hidden = saved("hidden"), favorites = saved("favorites");
        lockedApps = saved("locked");
        String filter = searchKey(query);
        indexScroll.setVisibility(filter.isEmpty() ? View.VISIBLE : View.GONE);
        if (!filter.isEmpty()) {
            section("SEARCH RESULTS", null, "");
            boolean any = false;
            for (ResolveInfo app : searchResults(filter, hidden)) {
                row(app, favorites.contains(key(app)), false); any = true;
            }
            if (!any) { TextView empty = text("No matching apps", 15, moss); styleWallpaperText(empty); list.addView(empty); }
        } else {
            section("★   FAVORITES", "★", "Favorites");
            Map<String, ResolveInfo> byKey = new HashMap<>();
            for (ResolveInfo app : apps) byKey.put(key(app), app);
            List<String> orderedFavorites = favoriteOrder();
            orderedFavorites.removeIf(hidden::contains);
            for (String key : orderedFavorites) row(byKey.get(key), true, true);
            if (orderedFavorites.isEmpty()) { TextView empty = text("Star an app below to pin it here.", 14, moss); styleWallpaperText(empty); list.addView(empty); }
            if (!collections().isEmpty() || !saved("widgets").isEmpty()) {
                View space = new View(this); list.addView(space, new LinearLayout.LayoutParams(-1, dp(8)));
            }
            addCollections();
            List<String> widgetIds = widgetOrder();
            if (stackWidgets() && widgetIds.size() > 1) widgetStack(widgetIds);
            else for (String id : widgetIds) widgetRow(id, list);

            Set<String> seen = new HashSet<>();
            String previous = null;
            for (ResolveInfo app : apps) {
                if (hidden.contains(key(app))) continue;
                String name = label(app).trim();
                String letter = name.isEmpty() ? "#" : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.getDefault());
                if (!letter.equals(previous)) { section(letter, seen.add(letter) ? letter : null, letter); previous = letter; }
                row(app, favorites.contains(key(app)), false);
            }
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }
    private List<ResolveInfo> searchResults(String filter, Set<String> hidden) {
        List<List<ResolveInfo>> ranked = Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        String[] tokens = filter.split("\\s+");
        for (ResolveInfo app : apps) {
            if (hidden.contains(key(app))) continue;
            String name = normalized(label(app));
            int rank;
            if (name.equals(filter)) rank = 0;
            else if (name.startsWith(filter)) rank = 1;
            else if (name.contains(filter)) rank = 2;
            else if (Arrays.stream(tokens).allMatch(name::contains)) rank = 3;
            else if (normalized(originalLabel(app)).contains(filter) || app.activityInfo.packageName.toLowerCase(Locale.ROOT).contains(filter)) rank = 4;
            else continue;
            ranked.get(rank).add(app);
        }
        List<ResolveInfo> results = new ArrayList<>();
        for (List<ResolveInfo> bucket : ranked) results.addAll(bucket);
        return results;
    }
    private void placeRail() {
        int position = prefs.getInt("railSide", 0) == 1 ? 0 : 1;
        if (appArea.indexOfChild(indexScroll) == position) return;
        appArea.removeView(indexScroll); appArea.addView(indexScroll, position);
    }
    private void dismissUndo() {
        handler.removeCallbacks(expireUndo); undoAction = null;
        if (undoBar != null) { undoBar.setVisibility(View.GONE); undoBar.removeAllViews(); }
    }
    private void showUndo(String message, Runnable restore) {
        dismissUndo(); undoAction = restore;
        TextView label = text(message, 14, ink);
        label.setGravity(Gravity.CENTER_VERTICAL); label.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        undoBar.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
        undoBar.addView(action("Undo", () -> {
            Runnable undo = undoAction; dismissUndo(); if (undo != null) undo.run();
        }));
        undoBar.setVisibility(View.VISIBLE);
        android.view.accessibility.AccessibilityManager manager = getSystemService(android.view.accessibility.AccessibilityManager.class);
        handler.postDelayed(expireUndo, Build.VERSION.SDK_INT >= 29 ? manager.getRecommendedTimeoutMillis(6500, android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT | android.view.accessibility.AccessibilityManager.FLAG_CONTENT_CONTROLS) : 6500);
    }
    private String searchKey(String value) {
        return java.text.Normalizer.normalize(value.trim().toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }
    private void emptyArea(View view) {
        view.setOnLongClickListener(v -> { showLauncherOptions(); return true; });
    }
    private void showLauncherOptions() {
        dismissKeyboard();
        Dialog panel = new Dialog(this);
        panel.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = column();
        GradientDrawable background = new GradientDrawable();
        background.setColor(paper);
        float radius = dp(20);
        background.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        content.setBackground(background);
        content.setPadding(dp(22), dp(12), dp(22), dp(16));
        content.addView(action("Widgets", () -> { panel.dismiss(); showWidgets(); }));
        content.addView(action("Launcher settings", () -> { panel.dismiss(); showSettings(); }));
        panel.setContentView(content);
        panel.show();
        Window window = panel.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
    private void showWidgets() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        labels.add("Add widget"); actions.add(this::pickWidget);
        int remaining = widgetRecovery().length();
        if (remaining > 0) { labels.add("Recover widgets · " + remaining); actions.add(this::showWidgetRecovery); }
        labels.add(stackWidgets() ? "Show widgets separately" : "Stack widgets");
        actions.add(() -> { prefs.edit().putBoolean("stackWidgets", !stackWidgets()).apply(); refresh(); });
        for (String id : widgetOrder()) {
            AppWidgetProviderInfo info = widgets.getAppWidgetInfo(Integer.parseInt(id));
            if (info == null) continue;
            String name = info.loadLabel(getPackageManager());
            labels.add("Manage " + name); actions.add(() -> showWidgetMenu(id, info, name));
        }
        showMenu("Widgets", labels, actions);
    }
    private void showSettings() {
        showSettingsPage("Launcher settings", "Make Alpharail yours", new String[]{"Theme", "Home wallpaper", "Icons", "Fonts", "App list", "Privacy", "Backup and restore", "Updates", "Set as Home"},
            new String[]{homeShowsWallpaper() ? "Unavailable while wallpaper is on" : (prefs.getInt("theme", 0) == 1 ? "Black palette" : "White palette"), homeShowsWallpaper() ? (bundledWallpaper() < 0 ? "Showing Android wallpaper" : WALLPAPERS[bundledWallpaper()]) : "Solid theme background", "Style, size and shape", "Typeface and text size", "Favorites and alphabet placement", "Hidden apps and App Lock", "Move your settings to another device", updateAvailable() ? "Update available" : "Check for new versions", "Choose your default launcher"},
            new String[]{"◐", "▧", "▦", "Aa", "A–Z", "◇", "⇅", "↑", "⌂"}, Arrays.asList(homeShowsWallpaper() ? null : this::showThemeSettings, this::showWallpaperSettings, this::showIconSettings, this::showFontSettings,
                this::showListSettings, this::showPrivacySettings, this::backupMenu, this::showUpdateSettings, this::setHome), null);
    }
    private boolean checkingUpdates, manualUpdateRequested;
    private String installedVersion() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return ""; }
    }
    private boolean updateAvailable() {
        return ReleaseUpdates.newer(prefs.getString("availableUpdate", ""), installedVersion());
    }
    private void showUpdateSettings() {
        boolean automatic = prefs.getBoolean("autoUpdates", false);
        List<String> labels = new ArrayList<>(Arrays.asList("Check for updates", automatic ? "Turn off automatic checks" : "Turn on automatic checks"));
        List<Runnable> actions = new ArrayList<>(Arrays.asList(() -> checkUpdates(true), () -> {
            prefs.edit().putBoolean("autoUpdates", !automatic).apply();
            if (!automatic) checkUpdates(false);
            showUpdateSettings();
        }));
        if (updateAvailable()) {
            String version = prefs.getString("availableUpdate", "");
            labels.add("Download v" + version); actions.add(() -> offerUpdate(version));
        }
        showSettingsPage("Updates", "Installed: v" + installedVersion(), labels.toArray(new String[0]),
            updateAvailable() ? new String[]{"Check GitHub now", "Checks once daily when Alpharail opens", "Open the official APK in your browser"}
                : new String[]{"Check GitHub now", "Checks once daily when Alpharail opens"},
            updateAvailable() ? new String[]{"↻", "◷", "↓"} : new String[]{"↻", "◷"}, actions, this::showSettings);
    }
    private void checkUpdates(boolean manual) {
        if (checkingUpdates) { manualUpdateRequested |= manual; return; }
        long now = System.currentTimeMillis(), last = prefs.getLong("lastUpdateCheck", 0);
        if (!manual && now >= last && now - last < 24 * 60 * 60 * 1000L) return;
        checkingUpdates = true; manualUpdateRequested = manual;
        prefs.edit().putLong("lastUpdateCheck", now).apply();
        if (manual) Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            String result;
            try { result = ReleaseUpdates.latest(); }
            catch (IOException | JSONException | RuntimeException e) { result = null; }
            String version = result;
            runOnUiThread(() -> {
                boolean showResult = manualUpdateRequested;
                checkingUpdates = false; manualUpdateRequested = false;
                if (isDestroyed() || isFinishing()) return;
                String previous = prefs.getString("availableUpdate", "");
                if (version != null) prefs.edit().putString("availableUpdate", version).apply();
                if (!updateForeground) return;
                if (version != null && ReleaseUpdates.newer(version, installedVersion())) {
                    if (showResult) offerUpdate(version);
                    else if (prefs.getBoolean("autoUpdates", false) && !version.equals(previous))
                        Toast.makeText(this, "Alpharail v" + version + " is available in Launcher settings → Updates", Toast.LENGTH_LONG).show();
                } else if (showResult) Toast.makeText(this, version == null ? "Couldn't check for updates. Try again later." : "Alpharail is up to date", Toast.LENGTH_SHORT).show();
            });
        }, "alpharail-update-check").start();
    }
    private boolean updateForeground;
    @Override protected void onPause() { updateForeground = false; super.onPause(); }
    private void offerUpdate(String version) {
        new AlertDialog.Builder(this).setTitle("Alpharail v" + version + " is available")
            .setMessage("Open the official GitHub APK in your browser. Android will ask you to confirm installation.")
            .setNegativeButton("Later", null).setPositiveButton("Open download", (dialog, which) -> {
                try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ReleaseUpdates.downloadUrl(version)))); }
                catch (ActivityNotFoundException | SecurityException ignored) {
                    Toast.makeText(this, "No browser available to open the download", Toast.LENGTH_SHORT).show();
                }
            }).show();
    }
    private void showSettingsPage(String title, String subtitle, String[] labels, String[] details, String[] glyphs, List<Runnable> actions, Runnable back) {
        Dialog page = new Dialog(this);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = column(); content.setBackgroundColor(paper); content.setPadding(dp(18), dp(10), dp(18), dp(8));
        TextView close = action(back == null ? "Close settings" : "‹  Settings", () -> { page.dismiss(); if (back != null) back.run(); });
        close.setTextColor(accent); content.addView(close);
        FrameLayout banner = heroFrame(); LinearLayout heading = column(); heading.setPadding(dp(18), dp(12), dp(18), dp(14));
        TextView kicker = text("ALPHARAIL STUDIO", 10, 0xffd9dcff); kicker.setLetterSpacing(.2f); heading.addView(kicker);
        TextView name = text(title, 28, 0xffffffff); name.setTypeface(null, Typeface.BOLD); heading.addView(name);
        heading.addView(text(subtitle, 13, 0xffe0e2ff)); banner.addView(heading); content.addView(banner, new LinearLayout.LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(this); scroll.setClipToPadding(false); scroll.setPadding(0, dp(12), 0, dp(8));
        LinearLayout cards = column(); scroll.addView(cards); content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < labels.length; i++) {
            Runnable select = actions.get(i);
            LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12), dp(9), dp(12), dp(9));
            row.setMinimumHeight(dp(58)); row.setFocusable(true);
            row.setContentDescription(details[i].isEmpty() ? labels[i] : labels[i] + ", " + details[i]);
            row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x284c42ca), rounded(surface, 16), null));
            if (select != null) row.setOnClickListener(v -> { page.dismiss(); select.run(); });
            else { row.setEnabled(false); row.setFocusable(false); row.setAlpha(.55f); }
            TextView icon = text(glyphs[i], 15, accent); icon.setGravity(Gravity.CENTER); icon.setBackground(rounded(dividerColor, 12));
            row.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
            LinearLayout description = column(); description.setPadding(dp(12), 0, dp(8), 0);
            TextView label = text(labels[i], 15, ink); label.setTypeface(null, Typeface.BOLD); description.addView(label);
            if (!details[i].isEmpty()) description.addView(text(details[i], 12, moss));
            row.addView(description, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text("›", 20, accent));
            for (int child = 0; child < row.getChildCount(); child++) row.getChildAt(child).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(-1, -2); spacing.bottomMargin = dp(7); cards.addView(row, spacing);
        }
        page.setContentView(content); page.show();
        Window window = page.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(paper)); window.setLayout(-1, -1);
            int bars = homeShowsWallpaper() ? 0xff161821 : paper;
            window.setStatusBarColor(bars); window.setNavigationBarColor(bars);
            window.getDecorView().setSystemUiVisibility(homeShowsWallpaper() || prefs.getInt("theme", 0) == 1 ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
    private void showThemeSettings() {
        if (homeShowsWallpaper()) return;
        final int current = prefs.getInt("theme", 0);
        LinearLayout content = column(); content.setPadding(dp(18), dp(8), dp(18), 0);
        RadioGroup options = new RadioGroup(this); options.setOrientation(LinearLayout.HORIZONTAL);
        RadioButton white = new RadioButton(this); white.setId(View.generateViewId()); white.setText("White"); options.addView(white);
        RadioButton black = new RadioButton(this); black.setId(View.generateViewId()); black.setText("Black"); options.addView(black);
        content.addView(options);
        LinearLayout mock = column(); mock.setPadding(dp(14), dp(12), dp(14), dp(8)); content.addView(mock);
        TextView mockClock = text("9:41", 38, 0xffffffff); mockClock.setBackground(rounded(0xff20194b, 14));
        mockClock.setPadding(dp(13), dp(4), dp(13), dp(6)); mock.addView(mockClock);
        TextView sample = text("★  Favorites\n\n    Camera\n\n    Messages", 15, ink); sample.setPadding(0, dp(10), 0, dp(4)); mock.addView(sample);
        options.setOnCheckedChangeListener((group, id) -> {
            boolean dark = id == black.getId(); mock.setBackground(rounded(dark ? 0xff000000 : 0xfff1eff7, 18));
            sample.setTextColor(dark ? 0xfff3f5fa : 0xff202124);
        });
        options.check(current == 1 ? black.getId() : white.getId());
        new AlertDialog.Builder(this).setTitle("Preview theme").setView(content).setNegativeButton("Cancel", null)
            .setPositiveButton("Apply", (d,w) -> {
                int selected = options.getCheckedRadioButtonId() == black.getId() ? 1 : 0;
                if (selected != current) { prefs.edit().putInt("theme", selected).apply(); recreate(); }
            }).show();
    }
    private void showIconSettings() {
        showSettingsGroup("Icons", Arrays.asList("Icon style", "Icon size", "Icon shape"), Arrays.asList(
            () -> setting("Icon style", "iconStyle", new String[]{"Original", "Monochrome", "Initials", "Dots"}, 0, this::showIconSettings),
            () -> setting("Icon size", "iconSize", new String[]{"Small", "Default", "Large"}, 1, this::showIconSettings),
            () -> setting("Icon shape", "iconShape", new String[]{"Original", "Rounded square", "Circle", "Square"}, 0, this::showIconSettings)));
    }
    private void showFontSettings() {
        showSettingsGroup("Fonts", Arrays.asList("Font size", "Typeface"), Arrays.asList(
            () -> setting("Font size", "fontSize", new String[]{"Small", "Default", "Large", "Extra large"}, 1, this::showFontSettings),
            () -> setting("Typeface", "fontShape", new String[]{"System", "Rounded", "Serif", "Monospace"}, 0, this::showFontSettings)));
    }
    private void showListSettings() {
        showSettingsGroup("App list", Arrays.asList("Reorder Favorites", "Collections", "Alphabet side"), Arrays.asList(
            this::reorderFavorites, this::manageCollections, () -> setting("Alphabet side", "railSide", new String[]{"Right", "Left"}, 0, this::showListSettings)));
    }
    private static final class AppCollection {
        final String name;
        final Set<String> members;
        AppCollection(String name, Set<String> members) { this.name = name; this.members = members; }
    }
    private List<AppCollection> collections() {
        List<AppCollection> result = new ArrayList<>();
        try {
            JSONArray stored = new JSONArray(prefs.getString("collections", "[]"));
            for (int i = 0; i < stored.length(); i++) {
                JSONObject item = stored.getJSONObject(i); JSONArray ids = item.getJSONArray("apps");
                Set<String> members = new LinkedHashSet<>();
                for (int j = 0; j < ids.length(); j++) members.add(ids.getString(j));
                result.add(new AppCollection(item.getString("name"), members));
            }
        } catch (JSONException ignored) { }
        return result;
    }
    private void saveCollections(List<AppCollection> groups) {
        JSONArray stored = new JSONArray();
        try {
            for (AppCollection item : groups) stored.put(new JSONObject().put("name", item.name).put("apps", new JSONArray(item.members)));
            prefs.edit().putString("collections", stored.toString()).apply(); refresh();
        } catch (JSONException ignored) { }
    }
    private void addCollections() {
        for (AppCollection group : collections()) {
            TextView card = action("▦   " + group.name + "  ›", () -> openCollection(group));
            card.setContentDescription("Open collection " + group.name);
            card.setTextColor(accent); card.setPadding(dp(10), 0, dp(10), 0);
            card.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x284c42ca), rounded(surface, 14), null));
            LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(-1, -2); margins.bottomMargin = dp(4); list.addView(card, margins);
        }
    }
    private void openCollection(AppCollection group) {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        Set<String> hidden = saved("hidden");
        for (ResolveInfo app : apps) if (group.members.contains(key(app)) && !hidden.contains(key(app))) {
            labels.add(label(app) + (lockedApps.contains(app.activityInfo.packageName) ? "  🔒" : "")); actions.add(() -> openApp(app));
        }
        if (labels.isEmpty()) new AlertDialog.Builder(this).setTitle(group.name).setMessage("No visible apps in this collection.").setPositiveButton("Close", null).show();
        else showMenu(group.name, labels, actions);
    }
    private void manageCollections() {
        List<AppCollection> groups = collections();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        if (groups.size() < 12) { labels.add("New collection"); actions.add(() -> editCollection(groups, -1)); }
        for (int i = 0; i < groups.size(); i++) {
            final int position = i;
            labels.add(groups.get(i).name); actions.add(() -> showMenu(groups.get(position).name, Arrays.asList("Edit", "Remove collection"), Arrays.asList(
                () -> editCollection(groups, position), () -> new AlertDialog.Builder(this).setMessage("Remove this collection? Its apps stay installed.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Remove", (d,w) -> { groups.remove(position); saveCollections(groups); manageCollections(); }).show())));
        }
        showMenu("Collections", labels, actions);
    }
    private void editCollection(List<AppCollection> groups, int position) {
        AppCollection existing = position < 0 ? new AppCollection("", Collections.emptySet()) : groups.get(position);
        EditText name = new EditText(this); name.setSingleLine(true); name.setHint("Collection name"); name.setText(existing.name);
        name.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        new AlertDialog.Builder(this).setTitle(position < 0 ? "New collection" : "Rename or edit collection").setView(name)
            .setNegativeButton("Cancel", null).setPositiveButton("Choose apps", (d,w) -> {
                String title = name.getText().toString().trim();
                if (title.isEmpty()) { Toast.makeText(this, "Enter a collection name", Toast.LENGTH_SHORT).show(); return; }
                Set<String> chosen = new LinkedHashSet<>(existing.members), hidden = saved("hidden");
                List<ResolveInfo> candidates = new ArrayList<>(); for (ResolveInfo app : apps) if (!hidden.contains(key(app))) candidates.add(app);
                String[] labels = new String[candidates.size()]; boolean[] checked = new boolean[labels.length];
                for (int i = 0; i < labels.length; i++) { labels[i] = label(candidates.get(i)); checked[i] = chosen.contains(key(candidates.get(i))); }
                new AlertDialog.Builder(this).setTitle(title).setMultiChoiceItems(labels, checked, (selection,i,on) -> {
                    String id = key(candidates.get(i)); if (on) chosen.add(id); else chosen.remove(id);
                }).setNegativeButton("Cancel", null).setPositiveButton("Save", (selection,button) -> {
                    if (chosen.isEmpty()) { Toast.makeText(this, "Choose at least one app", Toast.LENGTH_SHORT).show(); return; }
                    AppCollection item = new AppCollection(title, chosen);
                    if (position < 0) groups.add(item); else groups.set(position, item);
                    saveCollections(groups); manageCollections();
                }).show();
            }).show();
    }
    private void showPrivacySettings() {
        showSettingsGroup("Privacy", Arrays.asList("Hidden apps", "App lock"), Arrays.asList(this::showHiddenApps, this::requestLockSettings));
    }
    private void showSettingsGroup(String title, List<String> labels, List<Runnable> actions) {
        String[] descriptions = new String[labels.size()], glyphs = new String[labels.size()];
        Arrays.fill(descriptions, ""); Arrays.fill(glyphs, "·");
        showSettingsPage(title, "Customize your launcher", labels.toArray(new String[0]), descriptions, glyphs, actions, this::showSettings);
    }
    private void setting(String title, String key, String[] values, int fallback, Runnable parent) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(values, prefs.getInt(key, fallback), (d,i) -> {
            prefs.edit().putInt(key, i).apply();
            d.dismiss(); placeRail(); refresh(); parent.run();
        }).setNegativeButton("Back", (d,w) -> parent.run()).show();
    }
    private void showHiddenApps() {
        showAppChoices("Hidden apps", "Checked apps are hidden from Alpharail, but stay installed.", "hidden", this::showPrivacySettings);
    }
    private void showAppChoices(String title, String explanation, String pref, Runnable back) {
        Set<String> chosen = saved(pref);
        LinearLayout content = column();
        content.setPadding(dp(20), dp(10), dp(20), 0);
        content.addView(text(explanation, 14, moss));
        ScrollView scrollChoices = new ScrollView(this);
        LinearLayout choices = column(); scrollChoices.addView(choices);
        boolean packageChoices = "locked".equals(pref);
        Set<String> listed = new HashSet<>();
        for (ResolveInfo app : apps) {
            String id = packageChoices ? app.activityInfo.packageName : key(app);
            if (!listed.add(id)) continue;
            CheckBox check = new CheckBox(this);
            check.setText(label(app)); check.setChecked(chosen.contains(id));
            check.setOnCheckedChangeListener((v, checked) -> { if (checked) chosen.add(id); else chosen.remove(id); });
            choices.addView(check);
        }
        if (apps.isEmpty()) choices.addView(text("No apps available", 15, ink));
        content.addView(scrollChoices, new LinearLayout.LayoutParams(-1, dp(Math.min(300, Math.max(60, apps.size() * 48)))));
        new AlertDialog.Builder(this).setTitle(title).setView(content)
            .setNegativeButton("Back", (d,w) -> back.run())
            .setPositiveButton("Save", (d,w) -> {
                Set<String> added = new HashSet<>(chosen); added.removeAll(saved(pref));
                store(pref, chosen); refresh();
                if ("hidden".equals(pref) && !added.isEmpty()) showUndo(added.size() == 1 ? "App hidden" : "Apps hidden", () -> {
                    Set<String> current = saved("hidden"); current.removeAll(added); store("hidden", current); refresh();
                });
            }).show();
    }
    private void reorderFavorites() {
        List<String> order = favoriteOrder(); order.removeAll(saved("hidden"));
        if (order.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Reorder Favorites").setMessage("Star apps to add them to Favorites.")
                .setPositiveButton("OK", null).show();
            return;
        }
        Map<String, ResolveInfo> byKey = new HashMap<>(); for (ResolveInfo app : apps) byKey.put(key(app), app);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (String id : order) { ResolveInfo app = byKey.get(id); labels.add(label(app)); actions.add(() -> showFavoriteMenu(app)); }
        showMenu("Reorder Favorites", labels, actions);
    }
    private void showFavoriteMenu(ResolveInfo app) {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        addMoveActions(labels, actions, "favorites", favoriteOrder(), key(app), saved("hidden"));
        if (labels.isEmpty()) {
            new AlertDialog.Builder(this).setTitle(label(app)).setMessage("Add another favorite before reordering.")
                .setPositiveButton("OK", null).show();
        } else showMenu(label(app), labels, actions);
    }
    private void requestLockSettings() {
        if (pendingLockSettings || pendingLaunch != null) return;
        pendingLockSettings = true;
        if (!requestCredential(CONFIRM_LOCK_SETTINGS, "Manage App Lock")) pendingLockSettings = false;
    }
    private boolean requestCredential(int request, String title) {
        KeyguardManager keyguard = getSystemService(KeyguardManager.class);
        if (!keyguard.isDeviceSecure()) {
            new AlertDialog.Builder(this).setTitle("Secure screen lock required")
                .setMessage("Set a device PIN, pattern, or password in Android Settings to use App Lock.")
                .setPositiveButton("OK", null).show();
            return false;
        }
        Intent confirm = keyguard.createConfirmDeviceCredentialIntent(title, "Confirm your Android screen lock to continue.");
        if (confirm != null) {
            try { startActivityForResult(confirm, request); return true; }
            catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(this, "Couldn't verify your screen lock", Toast.LENGTH_SHORT).show();
        return false;
    }
    private void renameApp(ResolveInfo app) {
        String pref = "appAlias:" + key(app);
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
        field.setText(label(app)); field.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("Rename in Alpharail").setMessage("This changes the name and alphabetical position in Alpharail only.")
            .setView(field).setNegativeButton("Cancel", null)
            .setNeutralButton("Reset name", (d,w) -> { prefs.edit().remove(pref).apply(); reloadApps(); refresh(); })
            .setPositiveButton("Save", (d,w) -> {
                String alias = field.getText().toString().replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
                if (alias.isEmpty()) { Toast.makeText(this, "Name unchanged", Toast.LENGTH_SHORT).show(); return; }
                prefs.edit().putString(pref, alias).apply(); reloadApps(); refresh();
            }).show();
    }
    private void addAppToCollection(ResolveInfo app, List<AppCollection> groups) {
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (AppCollection group : groups) {
            if (group.members.contains(key(app))) continue;
            labels.add(group.name); actions.add(() -> { group.members.add(key(app)); saveCollections(groups); });
        }
        if (labels.isEmpty()) Toast.makeText(this, "Already in every collection", Toast.LENGTH_SHORT).show();
        else showMenu("Add to collection", labels, actions);
    }
    private void hideApp(ResolveInfo app) {
        String id = key(app);
        Set<String> hidden = saved("hidden");
        if (!hidden.add(id)) return;
        store("hidden", hidden); refresh();
        showUndo("Hidden from Alpharail", () -> {
            Set<String> current = saved("hidden"); current.remove(id); store("hidden", current); refresh();
        });
    }
    private void showAppActions(ResolveInfo app, boolean inFavorites) {
        dismissKeyboard();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("App info"); actions.add(() -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.activityInfo.packageName, null))); }
            catch (ActivityNotFoundException | SecurityException ignored) { Toast.makeText(this, "App info unavailable", Toast.LENGTH_SHORT).show(); }
        });
        labels.add("Rename in Alpharail"); actions.add(() -> renameApp(app));
        List<AppCollection> groups = collections();
        if (!groups.isEmpty()) { labels.add("Add to collection"); actions.add(() -> addAppToCollection(app, groups)); }
        labels.add("Hide from Alpharail"); actions.add(() -> hideApp(app));
        if (inFavorites) addMoveActions(labels, actions, "favorites", favoriteOrder(), key(app), saved("hidden"));
        LauncherApps manager = getSystemService(LauncherApps.class);
        try {
            if (manager != null && manager.hasShortcutHostPermission()) {
                LauncherApps.ShortcutQuery request = new LauncherApps.ShortcutQuery().setPackage(app.activityInfo.packageName)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);
                List<ShortcutInfo> shortcuts = manager.getShortcuts(request, android.os.Process.myUserHandle());
                if (shortcuts != null) for (ShortcutInfo shortcut : shortcuts) {
                    if (!shortcut.isEnabled() || shortcut.getShortLabel() == null || (shortcut.getActivity() != null && !component(app).equals(shortcut.getActivity()))) continue;
                    labels.add(shortcut.getShortLabel().toString()); actions.add(() -> openApp(app, shortcut.getId()));
                }
            }
        } catch (IllegalStateException | SecurityException ignored) { }
        showMenu(label(app), labels, actions);
    }
    private void openApp(ResolveInfo app) { openApp(app, null); }
    private void openApp(ResolveInfo app, String shortcut) {
        dismissKeyboard();
        if (!saved("locked").contains(app.activityInfo.packageName)) { launchTarget(component(app), shortcut); return; }
        if (pendingLaunch != null || pendingLockSettings) return;
        pendingLaunch = key(app); pendingShortcut = shortcut;
        if (!requestCredential(CONFIRM_LAUNCH, "Unlock " + label(app))) { pendingLaunch = null; pendingShortcut = null; }
    }
    private void launchTarget(ComponentName component, String shortcut) {
        if (component == null) return;
        if (shortcut == null) { launch(component); return; }
        try { getSystemService(LauncherApps.class).startShortcut(component.getPackageName(), shortcut, null, null, android.os.Process.myUserHandle()); }
        catch (ActivityNotFoundException | IllegalStateException | SecurityException e) {
            Toast.makeText(this, "Shortcut unavailable", Toast.LENGTH_SHORT).show();
        }
    }
    private void launch(ComponentName component) {
        if (component == null) return;
        LauncherApps launcher = getSystemService(LauncherApps.class);
        if (launcher != null) try {
            launcher.startMainActivity(component, android.os.Process.myUserHandle(), null, null);
            return;
        } catch (ActivityNotFoundException | SecurityException | IllegalStateException ignored) {
            // Some devices or launcher entries reject LauncherApps; try the public intent path.
        }
        try { startActivity(Intent.makeMainActivity(component)); return; }
        catch (ActivityNotFoundException | SecurityException ignored) { }
        String packageName = component.getPackageName();
        Intent main = getPackageManager().getLaunchIntentForPackage(packageName);
        if (main != null && main.getComponent() != null && packageName.equals(main.getComponent().getPackageName())
            && !component.equals(main.getComponent())) {
            try { startActivity(main); return; }
            catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        Toast.makeText(this, "Couldn't open " + chatAppName(packageName), Toast.LENGTH_SHORT).show();
        reloadApps(); refresh();
    }
    private void addMoveActions(List<String> labels, List<Runnable> actions, String kind, List<String> order, String key, Set<String> excluded) {
        List<String> visible = new ArrayList<>(order);
        visible.removeAll(excluded);
        int i = visible.indexOf(key);
        if (i > 0) { labels.add("Move up"); actions.add(() -> move(kind, order, key, visible.get(i - 1))); }
        if (i >= 0 && i + 1 < visible.size()) { labels.add("Move down"); actions.add(() -> move(kind, order, key, visible.get(i + 1))); }
    }
    private void showMenu(String title, List<String> labels, List<Runnable> actions) {
        new AlertDialog.Builder(this).setTitle(title).setItems(labels.toArray(new String[0]), (d,i) -> actions.get(i).run())
            .setNegativeButton("Cancel", null).show();
    }
    private int toDp(int px) { return Math.round(px / getResources().getDisplayMetrics().density); }
    private int defaultHeight(AppWidgetProviderInfo info) { return Math.max(80, toDp(info.minHeight)); }
    private int widgetHeight(String id, AppWidgetProviderInfo info) { return prefs.getInt("widgetHeight:" + id, defaultHeight(info)); }
    private void widgetRow(String value, LinearLayout container) {
        int id = Integer.parseInt(value);
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
        if (info == null) return;
        String name = info.loadLabel(getPackageManager());
        TextView edit = action(name + "  ⋯", () -> showWidgetMenu(value, info, name));
        styleWallpaperText(edit); edit.setContentDescription("Manage " + name + " widget");
        container.addView(edit);
        AppWidgetHostView cached = widgetViews.get(value);
        boolean fresh = cached == null;
        AppWidgetHostView view = fresh ? host.createView(this, id, info) : cached;
        if (fresh) widgetViews.put(value, view);
        if (view.getParent() instanceof ViewGroup) ((ViewGroup)view.getParent()).removeView(view);
        int height = widgetHeight(value, info);
        if (fresh) view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r - l > 0 && (r - l != or - ol || b - t != ob - ot)) {
                int width = Math.max(1, toDp(r - l));
                int currentHeight = widgetHeight(value, info);
                view.updateAppWidgetSize(null, width, currentHeight, width, currentHeight);
            }
        });
        container.addView(view, new LinearLayout.LayoutParams(-1, dp(height)));
    }
    private boolean stackWidgets() { return prefs.getBoolean("stackWidgets", false); }
    private void widgetStack(List<String> ids) {
        final int[] selected = {Math.max(0, ids.indexOf(prefs.getString("stackWidget", "")))};
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView status = text("", 13, accent); styleWallpaperText(status); status.setGravity(Gravity.CENTER); status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout active = column();
        java.util.function.IntConsumer flip = step -> {
            selected[0] = (selected[0] + step + ids.size()) % ids.size();
            String id = ids.get(selected[0]); prefs.edit().putString("stackWidget", id).apply();
            status.setText("Widget " + (selected[0] + 1) + " / " + ids.size());
            active.removeAllViews(); widgetRow(id, active);
            active.animate().cancel(); active.setTranslationX(0); active.setAlpha(1);
            if (step != 0 && (Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
                active.setTranslationX(dp(16) * step); active.setAlpha(.8f);
                active.animate().translationX(0).alpha(1).setDuration(160).start();
            }
        };
        TextView prev = action("‹", () -> flip.accept(-1)), next = action("›", () -> flip.accept(1));
        styleWallpaperText(prev); styleWallpaperText(next);
        prev.setContentDescription("Previous widget"); next.setContentDescription("Next widget");
        prev.setGravity(Gravity.CENTER); next.setGravity(Gravity.CENTER);
        controls.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(48)));
        controls.addView(status, new LinearLayout.LayoutParams(0, dp(48), 1));
        controls.addView(next, new LinearLayout.LayoutParams(dp(48), dp(48)));
        list.addView(controls);
        FrameLayout swipe = new FrameLayout(this) {
            float x, y;
            @Override public boolean onInterceptTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { x = event.getX(); y = event.getY(); }
                return event.getActionMasked() == MotionEvent.ACTION_MOVE && Math.abs(event.getX() - x) > dp(28)
                    && Math.abs(event.getX() - x) > Math.abs(event.getY() - y) * 1.4f;
            }
            @Override public boolean onTouchEvent(MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(event.getX() - x) > dp(30)) flip.accept(event.getX() < x ? 1 : -1);
                return true;
            }
        };
        swipe.addView(active, new FrameLayout.LayoutParams(-1, -2)); list.addView(swipe, new LinearLayout.LayoutParams(-1, -2));
        flip.accept(0);
    }
    private void showWidgetMenu(String id, AppWidgetProviderInfo info, String name) {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        addMoveActions(labels, actions, "widgets", widgetOrder(), id, Collections.emptySet());
        if ((info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
            labels.add("Resize height"); actions.add(() -> resizeWidget(id, info));
        }
        labels.add("Remove widget");
        actions.add(() -> new AlertDialog.Builder(this).setMessage("Remove this widget?").setNegativeButton("Cancel", null)
            .setPositiveButton("Remove", (d,w) -> {
                Set<String> ids = saved("widgets"); ids.remove(id); store("widgets", ids);
                List<String> order = widgetOrder(); saveOrder("widgets", order);
                prefs.edit().remove("widgetHeight:" + id).apply();
                widgetViews.remove(id); host.deleteAppWidgetId(Integer.parseInt(id)); refresh();
            }).show());
        showMenu(name, labels, actions);
    }
    private void resizeWidget(String id, AppWidgetProviderInfo info) {
        int min = Math.max(48, info.minResizeHeight > 0 ? toDp(info.minResizeHeight) : defaultHeight(info));
        int max = Math.max(min, defaultHeight(info) + 240);
        if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) max = Math.max(min, toDp(info.maxResizeHeight));
        LinearLayout content = column(); content.setPadding(dp(22), dp(12), dp(22), 0);
        TextView amount = text("", 15, moss); content.addView(amount);
        SeekBar height = new SeekBar(this); height.setMax(max - min);
        height.setProgress(Math.max(0, Math.min(max - min, widgetHeight(id, info) - min)));
        amount.setText((min + height.getProgress()) + " dp tall");
        height.setContentDescription("Widget height");
        height.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { amount.setText((min + progress) + " dp tall"); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        });
        content.addView(height);
        new AlertDialog.Builder(this).setTitle("Resize widget").setView(content).setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (d,w) -> {
                prefs.edit().putInt("widgetHeight:" + id, min + height.getProgress()).apply(); refresh();
            }).show();
    }
    private final class AlphabetRail extends LinearLayout {
        private int pointer = -1;
        AlphabetRail() { super(HomeActivity.this); setOrientation(VERTICAL); }
        @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
            android.view.accessibility.AccessibilityManager accessibility = getSystemService(android.view.accessibility.AccessibilityManager.class);
            return !accessibility.isTouchExplorationEnabled() && getChildCount() > 0;
        }
        @Override public boolean onTouchEvent(android.view.MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pointer = event.getPointerId(0); scrub(event.getY()); return true;
                case MotionEvent.ACTION_MOVE:
                    int i = event.findPointerIndex(pointer);
                    if (i >= 0) scrub(event.getY(i));
                    return true;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerId(event.getActionIndex()) != pointer) return true;
                    // The controlling finger lifted; keep the last selection.
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    pointer = -1; handler.removeCallbacks(hideLetter); handler.postDelayed(hideLetter, 180); return true;
                default: return true;
            }
        }
        private void scrub(float y) {
            if (pointer == -1 || getChildCount() == 0) return;
            int row = 0;
            while (row < getChildCount() - 1 && y >= getChildAt(row).getBottom()) row++;
            TextView letter = (TextView)getChildAt(row);
            if (letter != selectedLetter) jumpToLetter(letter, false);
        }
    }
    private void jumpToLetter(TextView letter, boolean smooth) {
        View heading = (View)letter.getTag();
        if (smooth && android.animation.ValueAnimator.areAnimatorsEnabled()) scroll.smoothScrollTo(0, heading.getTop());
        else scroll.scrollTo(0, heading.getTop());
        handler.removeCallbacks(hideLetter);
        showLetter(letter);
        if (smooth) handler.postDelayed(hideLetter, 420);
    }
    private void showLetter(TextView letter) {
        if (letterBubble == null) return;
        if (selectedLetter != null) selectedLetter.setSelected(false);
        selectedLetter = letter;
        letterBubble.animate().cancel();
        boolean animate = android.animation.ValueAnimator.areAnimatorsEnabled();
        if (letter == null) {
            if (letterBubble.getVisibility() != View.VISIBLE) return;
            if (animate) letterBubble.animate().alpha(0).setDuration(120).withEndAction(() -> letterBubble.setVisibility(View.GONE)).start();
            else letterBubble.setVisibility(View.GONE);
            return;
        }
        letter.setSelected(true);
        letterBubble.setText(letter.getText());
        FrameLayout frame = (FrameLayout)letterBubble.getParent();
        int[] top = new int[2], anchor = new int[2]; frame.getLocationInWindow(top); letter.getLocationInWindow(anchor);
        FrameLayout.LayoutParams bubblePosition = (FrameLayout.LayoutParams)letterBubble.getLayoutParams();
        boolean left = prefs.getInt("railSide", 0) == 1;
        bubblePosition.gravity = Gravity.TOP | (left ? Gravity.LEFT : Gravity.RIGHT);
        int size = dp(68);
        bubblePosition.leftMargin = left ? dp(49) : 0; bubblePosition.rightMargin = left ? 0 : dp(49);
        bubblePosition.topMargin = Math.max(0, Math.min(frame.getHeight() - size, anchor[1] - top[1] + (letter.getHeight() - size) / 2));
        letterBubble.setLayoutParams(bubblePosition); letterBubble.setVisibility(View.VISIBLE);
        letterBubble.setAlpha(1);
        letterBubble.setScaleX(animate ? .86f : 1); letterBubble.setScaleY(animate ? .86f : 1);
        if (animate) letterBubble.animate().scaleX(1).scaleY(1).setDuration(130).start();
    }
    private void section(String name, String shortcut, String description) {
        TextView title = text(name, 12, accent); title.setLetterSpacing(.12f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(13), 0, dp(5)); styleWallpaperText(title); list.addView(title); emptyArea(title);
        if (shortcut != null) {
            TextView jump = action(shortcut, () -> {});
            jump.setTag(title); jump.setOnClickListener(v -> jumpToLetter(jump, true));
            jump.setGravity(Gravity.CENTER); jump.setMinHeight(0); jump.setIncludeFontPadding(false); jump.setTextSize(12);
            jump.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            jump.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}}, new int[]{0xffffffff, homeShowsWallpaper() ? (lightHomeWallpaper() ? 0xff191919 : 0xffffffff) : moss}));
            if (homeShowsWallpaper()) jump.setShadowLayer(dp(3), 0, dp(1), lightHomeWallpaper() ? 0xffffffff : 0xff000000);
            android.graphics.drawable.StateListDrawable state = new android.graphics.drawable.StateListDrawable();
            state.addState(new int[]{android.R.attr.state_selected}, rounded(PRIMARY, 12));
            state.addState(new int[]{}, new ColorDrawable(android.graphics.Color.TRANSPARENT)); jump.setBackground(state);
            jump.setContentDescription("Jump to " + description); index.addView(jump, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }
    private void row(ResolveInfo app, boolean favorite, boolean inFavorites) {
        String rowKey = key(app) + "|" + label(app) + "|" + favorite + "|" + inFavorites + "|" + lockedApps.contains(app.activityInfo.packageName)
            + "|" + prefs.getInt("iconSize", 1) + "|" + prefs.getInt("iconStyle", 0) + "|" + prefs.getInt("iconShape", 0)
            + "|" + prefs.getInt("fontSize", 1) + "|" + prefs.getInt("fontShape", 0);
        View reused = rowCache.get(rowKey);
        if (reused != null && reused.getParent() == null) { list.addView(reused); return; }
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        int iconSize = new int[]{20, 26, 36}[prefs.getInt("iconSize", 1)];
        int fontSize = new int[]{13, 15, 18, 21}[prefs.getInt("fontSize", 1)];
        int height = Math.max(48, iconSize + 14);
        boolean locked = lockedApps.contains(app.activityInfo.packageName);
        LinearLayout launchRow = new LinearLayout(this); launchRow.setGravity(Gravity.CENTER_VERTICAL);
        launchRow.setContentDescription(label(app) + (locked ? ", locked" : ""));
        TextView open = text(label(app) + (locked ? "  🔒" : ""), fontSize, ink);
        open.setGravity(Gravity.CENTER_VERTICAL);
        launchRow.setFocusable(true);
        launchRow.setOnClickListener(v -> openApp(app));
        android.util.TypedValue ripple = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        launchRow.setBackgroundResource(ripple.resourceId);
        open.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        open.setTextColor(ink); open.setTextSize(fontSize); styleWallpaperText(open);
        String font = new String[]{"sans-serif", "sans-serif-rounded", "serif", "monospace"}[prefs.getInt("fontShape", 0)];
        open.setTypeface(Typeface.create(font, Typeface.NORMAL));
        open.setSingleLine(); open.setEllipsize(android.text.TextUtils.TruncateAt.END);
        View icon = appIcon(app, iconSize);
        LinearLayout.LayoutParams iconSpace = new LinearLayout.LayoutParams(dp(iconSize), dp(iconSize));
        iconSpace.setMarginEnd(dp(10)); launchRow.addView(icon, iconSpace);
        launchRow.addView(open, new LinearLayout.LayoutParams(0, -1, 1));
        line.addView(launchRow, new LinearLayout.LayoutParams(0, dp(height), 1));
        launchRow.setOnLongClickListener(v -> { showAppActions(app, inFavorites); return true; });
        TextView star = action(favorite ? "★" : "☆", () -> {
            Set<String> favorites = saved("favorites"); String key = key(app);
            List<String> order = favoriteOrder();
            int oldIndex = order.indexOf(key);
            boolean removed = favorites.remove(key);
            if (removed) order.remove(key); else { favorites.add(key); order.add(key); }
            store("favorites", favorites); saveOrder("favorites", order); refresh();
            if (removed) showUndo("Removed from Favorites", () -> {
                List<String> updated = favoriteOrder(); Set<String> members = saved("favorites");
                if (!members.add(key)) return;
                updated.add(Math.max(0, Math.min(oldIndex, updated.size())), key);
                store("favorites", members); saveOrder("favorites", updated); refresh();
            });
        });
        star.setGravity(Gravity.CENTER); if (favorite) star.setTextColor(accent); styleWallpaperText(star); star.setContentDescription((favorite ? "Unfavorite " : "Favorite ") + label(app)); line.addView(star, new LinearLayout.LayoutParams(dp(44), dp(height)));
        rowCache.put(rowKey, line); list.addView(line);
    }
    private View appIcon(ResolveInfo app, int size) {
        int style = prefs.getInt("iconStyle", 0), shape = prefs.getInt("iconShape", 0);
        View icon;
        if (style == 3) {
            TextView dot = text("●", Math.max(8, size / 3), accent); dot.setGravity(Gravity.CENTER);
            dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); return dot;
        }
        if (style == 2) {
            String name = label(app).trim();
            String initial = name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.getDefault());
            TextView initials = text(initial, Math.max(11, size / 2), ink);
            initials.setGravity(Gravity.CENTER); initials.setTypeface(null, Typeface.BOLD); icon = initials;
        } else {
            ImageView image = new ImageView(this);
            android.graphics.drawable.Drawable.ConstantState state = iconCache.get(key(app));
            android.graphics.drawable.Drawable drawable = state == null ? app.loadIcon(getPackageManager()) : state.newDrawable(getResources());
            if (state == null && drawable.getConstantState() != null) iconCache.put(key(app), drawable.getConstantState());
            image.setImageDrawable(drawable.mutate());
            image.setScaleType(shape == 0 ? ImageView.ScaleType.FIT_CENTER : ImageView.ScaleType.CENTER_CROP);
            if (style == 1) { ColorMatrix grayscale = new ColorMatrix(); grayscale.setSaturation(0); image.setColorFilter(new ColorMatrixColorFilter(grayscale)); }
            icon = image;
        }
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        if (shape != 0 || style == 2) {
            GradientDrawable mask = new GradientDrawable(); mask.setColor(dividerColor);
            mask.setCornerRadius(shape == 2 ? dp(size) : shape == 3 ? 0 : dp(5));
            icon.setBackground(mask); icon.setClipToOutline(true);
        }
        return icon;
    }
    private JSONArray widgetRecovery() {
        try { return new JSONArray(prefs.getString("widgetRecovery", "[]")); }
        catch (JSONException ignored) { return new JSONArray(); }
    }
    private void showWidgetRecovery() { showWidgetRecovery(false); }
    private void showWidgetRecovery(boolean choose) {
        JSONArray queue = widgetRecovery();
        Map<String, AppWidgetProviderInfo> providers = new HashMap<>();
        for (AppWidgetProviderInfo info : widgets.getInstalledProviders()) providers.put(info.provider.flattenToString(), info);
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        for (int i = 0; i < queue.length(); i++) {
            JSONObject entry = queue.optJSONObject(i);
            if (entry == null) continue;
            int slot = i;
            ComponentName component = ComponentName.unflattenFromString(entry.optString("provider"));
            AppWidgetProviderInfo info = component == null ? null : providers.get(component.flattenToString());
            String name = info == null ? entry.optString("provider").split("/", 2)[0] + " · unavailable" : info.loadLabel(getPackageManager());
            labels.add((i + 1) + ". " + name);
            actions.add(() -> new AlertDialog.Builder(this).setTitle(name)
                .setMessage(info == null ? "Install the widget’s app before trying again." : "Re-add this widget? Android may ask for permission and the widget may need to be configured again.")
                .setNeutralButton("Skip", (d,w) -> {
                    if (pending != -1) return;
                    queue.remove(slot); prefs.edit().putString("widgetRecovery", queue.toString()).apply(); showWidgetRecovery(choose);
                }).setNegativeButton("Back", (d,w) -> showWidgets())
                .setPositiveButton(info == null ? "OK" : "Re-add", (d,w) -> { if (info != null) bindWidget(info, slot); }).show());
        }
        if (labels.isEmpty()) { showWidgets(); return; }
        if (choose) showMenu("Recover widgets · " + labels.size() + " remaining", labels, actions);
        else new AlertDialog.Builder(this).setTitle("Recover widgets")
            .setMessage(labels.size() + " remaining. Next: " + labels.get(0) + ". Existing widgets will stay in place.")
            .setPositiveButton("Continue", (d,w) -> actions.get(0).run())
            .setNeutralButton("Choose widget", (d,w) -> showWidgetRecovery(true))
            .setNegativeButton("Later", null).show();
    }
    private void pickWidget() {
        List<AppWidgetProviderInfo> options = new ArrayList<>(widgets.getInstalledProviders());
        options.sort(Comparator.comparing(p -> p.loadLabel(getPackageManager()), Collator.getInstance()));
        if (options.isEmpty()) { Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++) names[i] = options.get(i).loadLabel(getPackageManager());
        new AlertDialog.Builder(this).setTitle("Add widget").setItems(names, (d,i) -> bindWidget(options.get(i))).setNegativeButton("Cancel", null).show();
    }
    private void bindWidget(AppWidgetProviderInfo info) { bindWidget(info, -1); }
    private void bindWidget(AppWidgetProviderInfo info, int recovery) {
        if (pending != -1) return;
        pendingRecovery = recovery;
        pending = host.allocateAppWidgetId();
        if (widgets.bindAppWidgetIdIfAllowed(pending, info.provider)) configureWidget();
        else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            try { startActivityForResult(intent, 11); } catch (ActivityNotFoundException | SecurityException e) { finishWidget(false); }
        }
    }
    private void configureWidget() {
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(pending);
        if (info == null) { finishWidget(false); return; }
        if (info.configure == null) { finishWidget(true); return; }
        try { host.startAppWidgetConfigureActivityForResult(this, pending, 0, 12, null); }
        catch (ActivityNotFoundException | SecurityException e) { finishWidget(false); }
    }
    private void finishWidget(boolean accepted) {
        if (pending == -1) return;
        if (accepted) {
            String id = Integer.toString(pending);
            List<String> order = widgetOrder(); order.add(id);
            Set<String> ids = saved("widgets"); ids.add(id);
            android.content.SharedPreferences.Editor edit = prefs.edit().putStringSet("widgets", ids)
                .putString("widgetsOrder", String.join("\n", order));
            JSONArray queue = widgetRecovery();
            JSONObject entry = queue.optJSONObject(pendingRecovery);
            AppWidgetProviderInfo info = widgets.getAppWidgetInfo(pending);
            if (entry != null && info != null && info.provider.flattenToString().equals(entry.optString("provider"))) {
                if ((info.resizeMode & AppWidgetProviderInfo.RESIZE_VERTICAL) != 0) {
                    int min = Math.max(48, info.minResizeHeight > 0 ? toDp(info.minResizeHeight) : defaultHeight(info));
                    int max = Math.max(min, defaultHeight(info) + 240);
                    if (Build.VERSION.SDK_INT >= 31 && info.maxResizeHeight > 0) max = Math.max(min, toDp(info.maxResizeHeight));
                    edit.putInt("widgetHeight:" + id, Math.max(min, Math.min(max, entry.optInt("height", defaultHeight(info)))));
                }
                queue.remove(pendingRecovery); edit.putString("widgetRecovery", queue.toString());
            }
            edit.apply();
        } else host.deleteAppWidgetId(pending);
        boolean recovering = pendingRecovery >= 0;
        pending = -1; pendingRecovery = -1; refresh();
        if (accepted && recovering) {
            if (widgetRecovery().length() > 0) showWidgetRecovery();
            else Toast.makeText(this, "Widget recovery complete", Toast.LENGTH_SHORT).show();
        }
    }
    private boolean chatAllowed(String app) {
        if (!ChatNotifications.isMessagingApp(this, app) || saved("locked").contains(app) || saved("chatExcluded").contains(app)) return false;
        for (String hidden : saved("hidden")) if (hidden.startsWith(app + "/")) return false;
        return true;
    }
    private String chatAppName(String packageName) {
        try { return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0)).toString(); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return packageName; }
    }
    private boolean chatEnabled() { return prefs.getBoolean("chatsEnabled", false) && getSystemService(NotificationManager.class)
        .isNotificationListenerAccessGranted(new ComponentName(this, ChatNotifications.class)); }
    private void showChats() {
        dismissKeyboard();
        if (!chatEnabled()) {
            new AlertDialog.Builder(this).setTitle("Chats")
                .setMessage("Optional: show active messaging notifications and reply where the app supports it. Android notification access can read all notifications. Alpharail only displays message notifications here, does not save or upload their contents, and cannot retrieve chat history. Photos and unsupported replies open in the original app.")
                .setNegativeButton("Not now", null).setPositiveButton("Enable Chats", (d,w) -> {
                    prefs.edit().putBoolean("chatsEnabled", true).apply();
                    if (chatEnabled()) showChats(); else startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }).show();
            return;
        }
        refreshChatsDot(true);
        LinearLayout content = column(); content.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView note = text("Conversations from active notifications", 13, moss); content.addView(note);
        ScrollView body = new ScrollView(this); LinearLayout rows = column(); body.addView(rows);
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this); content.addView(buttons);
        AlertDialog panel = new AlertDialog.Builder(this).setTitle("Chats").setView(content).setNegativeButton("Close", null).create();
        String[] filter = {null};
        TextView[] filterLabel = {null};
        TextView appFilter = action("App: All", () -> {
            List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
            labels.add("All messaging apps");
            actions.add(() -> { filter[0] = null; filterLabel[0].setText("App: All"); fillChats(rows, null); });
            Set<String> packages = new LinkedHashSet<>();
            for (StatusBarNotification item : ChatNotifications.current()) {
                if (chatAllowed(item.getPackageName())) packages.add(item.getPackageName());
            }
            for (String pkg : packages) {
                labels.add(chatAppName(pkg)); actions.add(() -> { filter[0] = pkg; filterLabel[0].setText("App: " + chatAppName(pkg)); fillChats(rows, pkg); });
            }
            AlertDialog picker = new AlertDialog.Builder(this).setTitle("Filter Chats")
                .setItems(labels.toArray(new String[0]), (d,i) -> actions.get(i).run()).setNegativeButton("Cancel", null).create();
            showPrivateChatDialog(picker);
        });
        filterLabel[0] = appFilter; content.addView(appFilter, 1);
        Runnable redraw = () -> fillChats(rows, filter[0]);
        boolean[] queued = {false};
        Runnable refreshInbox = () -> { queued[0] = false; redraw.run(); };
        Runnable live = () -> { if (!queued[0]) { queued[0] = true; handler.postDelayed(refreshInbox, 180); } };
        ChatNotifications.observers.add(live);
        buttons.addView(action("Refresh", redraw), new LinearLayout.LayoutParams(0, dp(44), 1));
        buttons.addView(action("Chat settings", () -> { panel.dismiss(); showChatSettings(); }), new LinearLayout.LayoutParams(0, dp(44), 1));
        showPrivateChatDialog(panel); panel.getWindow().setLayout(-1, Math.min(dp(640), Math.round(getResources().getDisplayMetrics().heightPixels * .86f)));
        panel.setOnDismissListener(ignored -> {
            privateChatDialogs.remove(panel); ChatNotifications.observers.remove(live); handler.removeCallbacks(refreshInbox);
        });
        redraw.run();
    }
    private String chatPinId(StatusBarNotification item) {
        String shortcut = item.getNotification().getShortcutId();
        if (shortcut == null || shortcut.isEmpty()) return null;
        return UUID.nameUUIDFromBytes(ChatNotifications.conversationKey(item).getBytes(StandardCharsets.UTF_8)).toString();
    }
    private Drawable chatSourceIcon(String pkg) {
        try { return getPackageManager().getApplicationIcon(pkg); }
        catch (android.content.pm.PackageManager.NameNotFoundException ignored) { return getPackageManager().getDefaultActivityIcon(); }
    }
    private final android.util.LruCache<String, Drawable.ConstantState> chatAvatars = new android.util.LruCache<>(24);
    private boolean fromMe(Bundle message, Bundle extras) {
        Object sender = message.get("sender_person");
        Object self = extras.get(Notification.EXTRA_MESSAGING_PERSON);
        if (Build.VERSION.SDK_INT >= 28 && sender instanceof android.app.Person && self instanceof android.app.Person) {
            android.app.Person author = (android.app.Person)sender, user = (android.app.Person)self;
            if (author.getKey() != null && author.getKey().equals(user.getKey())) return true;
            if (author.getUri() != null && author.getUri().equals(user.getUri())) return true;
        }
        // MessagingStyle uses a null sender for the device user's messages.
        return sender == null && message.getCharSequence("sender") == null
            && Notification.MessagingStyle.class.getName().equals(extras.getString(Notification.EXTRA_TEMPLATE));
    }
    private Drawable chatAvatar(StatusBarNotification item, boolean previews) {
        String id = ChatNotifications.conversationKey(item);
        if (Build.VERSION.SDK_INT < 28 || !visibleChatText(item, previews)) { chatAvatars.remove(id); return null; }
        try {
            Bundle extras = item.getNotification().extras;
            Parcelable[] messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (messages != null) for (int i = messages.length - 1; i >= Math.max(0, messages.length - 20); i--) {
                if (!(messages[i] instanceof Bundle)) continue;
                Bundle message = (Bundle)messages[i];
                if (fromMe(message, extras)) continue;
                Parcelable sender = message.getParcelable("sender_person");
                if (!(sender instanceof android.app.Person)) continue;
                android.graphics.drawable.Icon icon = ((android.app.Person)sender).getIcon();
                if (icon == null) continue;
                int type = icon.getType();
                if (type != android.graphics.drawable.Icon.TYPE_BITMAP && type != android.graphics.drawable.Icon.TYPE_RESOURCE
                    && type != android.graphics.drawable.Icon.TYPE_ADAPTIVE_BITMAP) continue;
                Drawable avatar = icon.loadDrawable(this);
                if (avatar == null) continue;
                if (avatar.getConstantState() != null) chatAvatars.put(id, avatar.getConstantState());
                return avatar;
            }
        } catch (RuntimeException ignored) { }
        Drawable.ConstantState previous = chatAvatars.get(id);
        return previous == null ? null : previous.newDrawable(getResources());
    }
    private LinearLayout chatIdentity(StatusBarNotification item, boolean previews) {
        LinearLayout identity = new LinearLayout(this); identity.setGravity(Gravity.CENTER_VERTICAL);
        Drawable avatar = chatAvatar(item, previews);
        if (avatar != null) {
            ImageView photo = new ImageView(this); photo.setImageDrawable(avatar); photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setBackground(rounded(surface, 24)); photo.setClipToOutline(true);
            photo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            identity.addView(photo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }
        ImageView source = new ImageView(this); source.setImageDrawable(chatSourceIcon(item.getPackageName()));
        source.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(dp(avatar == null ? 36 : 23), dp(avatar == null ? 36 : 23));
        size.setMarginStart(dp(avatar == null ? 0 : 5)); size.setMarginEnd(dp(10)); identity.addView(source, size);
        return identity;
    }
    private void fillChats(LinearLayout rows, String filter) {
        rows.removeAllViews();
        boolean showText = prefs.getBoolean("chatPreviews", false);
        Set<String> pins = saved("chatPins");
        List<Map.Entry<String, List<StatusBarNotification>>> entries = new ArrayList<>();
        if (chatEnabled()) entries.addAll(ChatNotifications.conversations().entrySet());
        entries.sort(Comparator.comparing(entry -> !pins.contains(chatPinId(entry.getValue().get(0)))));
        int count = 0;
        for (Map.Entry<String, List<StatusBarNotification>> entry : entries) {
            StatusBarNotification item = entry.getValue().get(0);
            String pkg = item.getPackageName();
            if ((filter != null && !filter.equals(pkg)) || !chatAllowed(pkg)) continue;
            boolean pinned = pins.contains(chatPinId(item));
            LinearLayout row = chatIdentity(item, showText); row.setPadding(0, dp(6), 0, dp(8));
            LinearLayout detail = column(); row.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
            TextView thread = action((pinned ? "★  " : "") + chatTitle(item, showText) + "  ›", () -> showChatThread(entry.getKey(), pkg, () -> fillChats(rows, filter)));
            thread.setMaxLines(2); detail.addView(thread);
            CharSequence preview = visibleChatText(item, showText) ? latestChatText(item.getNotification()) : null;
            TextView subtitle = text(chatAppName(pkg) + (preview == null ? " · Preview hidden or unavailable" : " · " + chatText(preview)), 14, moss);
            subtitle.setMaxLines(2); detail.addView(subtitle); rows.addView(row);
            View separator = new View(this); separator.setBackgroundColor(dividerColor); rows.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
            if (++count == 50) break;
        }
        if (count == 0) rows.addView(text(ChatNotifications.available() ? (filter == null ? "No active messages from included apps." : "No active messages from " + chatAppName(filter) + ".") : "Waiting for notification access. Try Refresh after enabling Alpharail Chats in Android settings.", 15, ink));
    }
    private boolean visibleChatText(StatusBarNotification item, boolean enabled) {
        Notification n = item.getNotification();
        return enabled && n.visibility != Notification.VISIBILITY_SECRET && n.extras != null;
    }
    private String chatTitle(StatusBarNotification item, boolean previews) {
        if (visibleChatText(item, previews)) {
            Bundle extras = item.getNotification().extras;
            CharSequence title = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE);
            if (title == null) title = extras.getCharSequence(Notification.EXTRA_TITLE);
            if (title != null && title.length() > 0) return chatText(title);
        }
        return "Conversation · " + chatAppName(item.getPackageName());
    }
    private CharSequence latestChatText(Notification n) {
        Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
            CharSequence value = ((Bundle)messages[messages.length - 1]).getCharSequence("text");
            if (value != null) return value;
        }
        return n.extras.getCharSequence(Notification.EXTRA_TEXT);
    }
    private static final class SessionReply {
        final String text;
        final long time;
        SessionReply(String text, long time) { this.text = text; this.time = time; }
    }
    private final android.util.LruCache<String, List<SessionReply>> sessionReplies = new android.util.LruCache<>(16);
    private void rememberReply(String id, SessionReply reply) {
        List<SessionReply> replies = sessionReplies.get(id);
        if (replies == null) { replies = new ArrayList<>(); sessionReplies.put(id, replies); }
        replies.add(reply);
        if (replies.size() > 10) replies.remove(0);
    }
    private void showChatThread(String id, String pkg, Runnable refreshInbox) {
        if (!chatEnabled() || !chatAllowed(pkg)) return;
        List<StatusBarNotification> thread = ChatNotifications.conversations().get(id);
        if (thread == null || thread.isEmpty() || !pkg.equals(thread.get(0).getPackageName())) {
            Toast.makeText(this, "This conversation is no longer active", Toast.LENGTH_SHORT).show(); return;
        }
        boolean previews = prefs.getBoolean("chatPreviews", false);
        StatusBarNotification latest = thread.get(0);
        LinearLayout content = column(); content.setPadding(dp(20), dp(8), dp(20), dp(8));
        LinearLayout identity = chatIdentity(latest, previews);
        identity.addView(text(chatAppName(pkg) + " · From active notifications", 13, moss), new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(identity);
        ScrollView scroll = new ScrollView(this); LinearLayout messages = column(); scroll.addView(messages);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, dp(210)));
        Set<String> seen = new HashSet<>();
        List<SessionReply> replies = previews && visibleChatText(latest, true) ? sessionReplies.get(id) : null;
        List<SessionReply> pendingReplies = replies == null ? new ArrayList<>() : new ArrayList<>(replies);
        for (int i = Math.min(20, thread.size()) - 1; i >= 0; i--) {
            StatusBarNotification item = thread.get(i);
            if (!visibleChatText(item, previews)) continue;
            Notification n = item.getNotification();
            Parcelable[] entries = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
            if (entries != null && entries.length > 0) {
                for (int j = Math.max(0, entries.length - 20); j < entries.length; j++) if (entries[j] instanceof Bundle) {
                    Bundle message = (Bundle)entries[j];
                    CharSequence body = message.getCharSequence("text");
                    long time = message.getLong("time", item.getPostTime());
                    boolean outgoing = fromMe(message, n.extras);
                    if (outgoing && body != null) {
                        for (Iterator<SessionReply> iterator = pendingReplies.iterator(); iterator.hasNext();) {
                            SessionReply local = iterator.next();
                            if (local.text.contentEquals(body) && Math.abs(time - local.time) <= 120_000L) {
                                iterator.remove(); break;
                            }
                        }
                    }
                    addThreadMessage(messages, seen, body, time, outgoing, false);
                }
            } else addThreadMessage(messages, seen, latestChatText(n), item.getPostTime(), false, false);
        }
        for (SessionReply local : pendingReplies) addThreadMessage(messages, seen, local.text, local.time, true, true);
        if (seen.isEmpty()) messages.addView(text("Message text is hidden or unavailable. You can change previews in Chat settings.", 15, moss));
        AlertDialog detail = new AlertDialog.Builder(this).setTitle(chatTitle(latest, previews)).setView(content).setNegativeButton("Back", null).create();
        String pin = chatPinId(latest);
        if (pin != null) {
            TextView[] control = {null};
            control[0] = action(saved("chatPins").contains(pin) ? "Unpin conversation" : "Pin conversation", () -> {
                Set<String> pins = saved("chatPins");
                if (!pins.remove(pin)) {
                    if (pins.size() >= 100) { Toast.makeText(this, "Unpin a conversation before adding another", Toast.LENGTH_SHORT).show(); return; }
                    pins.add(pin);
                }
                store("chatPins", pins); control[0].setText(pins.contains(pin) ? "Unpin conversation" : "Pin conversation"); refreshInbox.run();
            });
            content.addView(control[0]);
        }
        content.addView(action("Open " + chatAppName(pkg), () -> openChatApp(pkg)));
        if (chatContentIntent(latest) != null) content.addView(action("Open conversation", () -> openChat(latest.getKey(), id, pkg)));
        for (StatusBarNotification item : thread) if (ChatNotifications.replyAction(item) != null) {
            content.addView(action("Reply via " + chatAppName(pkg), () -> showChatReply(item, reply -> {
                if (!previews || !visibleChatText(item, true) || !chatEnabled() || !chatAllowed(pkg)) return;
                SessionReply local = new SessionReply(reply, System.currentTimeMillis());
                rememberReply(id, local);
                if (seen.isEmpty()) messages.removeAllViews();
                addThreadMessage(messages, seen, local.text, local.time, true, true);
                scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
            }))); break;
        }
        showPrivateChatDialog(detail);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }
    private void addThreadMessage(LinearLayout rows, Set<String> seen, CharSequence body, long time, boolean outgoing, boolean handedOff) {
        if (body == null || body.length() == 0 || (!handedOff && seen.size() >= 50)) return;
        String snippet = chatText(body);
        if (!seen.add(time + ":" + outgoing + ":" + snippet)) return;
        LinearLayout bubble = column(); bubble.setBackground(rounded(outgoing ? dividerColor : surface, 14));
        bubble.setPadding(dp(12), dp(7), dp(12), dp(8));
        String author = outgoing ? (handedOff ? "You · Passed to app" : "You") : "Incoming";
        bubble.addView(text(author, 11, moss)); bubble.addView(text(snippet, 15, ink));
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2);
        layout.gravity = outgoing ? Gravity.RIGHT : Gravity.LEFT;
        layout.setMargins(dp(outgoing ? 38 : 0), dp(4), dp(outgoing ? 0 : 38), dp(4));
        rows.addView(bubble, layout);
        if (rows.getChildCount() > 60) rows.removeViewAt(0);
    }
    private String chatText(CharSequence value) { String text = value.toString(); if (text.length() <= 600) return text; int cut = Character.isHighSurrogate(text.charAt(599)) ? 599 : 600; return text.substring(0, cut) + "…"; }
    private void showChatReply(StatusBarNotification item, java.util.function.Consumer<String> onReply) {
        EditText reply = new EditText(this); reply.setHint("Write a text reply");
        reply.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Reply via " + chatAppName(item.getPackageName())).setView(reply)
            .setNegativeButton("Cancel", null).setPositiveButton("Send", (d,w) -> {
                String message = reply.getText().toString().trim(); if (!message.isEmpty() && performChatAction(item.getKey(), ChatNotifications.conversationKey(item), message)) onReply.accept(message);
            }).create();
        showPrivateChatDialog(dialog);
    }
    private PendingIntent chatContentIntent(StatusBarNotification item) {
        PendingIntent open = item.getNotification().contentIntent;
        if (open == null || !item.getPackageName().equals(open.getCreatorPackage())) return null;
        return Build.VERSION.SDK_INT >= 31 && !open.isActivity() ? null : open;
    }
    private void openChatApp(String packageName) {
        if (!chatEnabled() || !chatAllowed(packageName)) return;
        try {
            Intent target = getPackageManager().getLaunchIntentForPackage(packageName);
            if (target != null) { startActivity(target); return; }
            for (ResolveInfo app : apps) if (packageName.equals(app.activityInfo.packageName)) {
                startActivity(Intent.makeMainActivity(component(app))); return;
            }
        } catch (ActivityNotFoundException | SecurityException ignored) { }
        Toast.makeText(this, "Could not open " + chatAppName(packageName), Toast.LENGTH_SHORT).show();
    }
    private void openChat(String key, String conversation, String packageName) {
        if (!chatEnabled() || !chatAllowed(packageName)) return;
        for (StatusBarNotification item : ChatNotifications.current()) {
            if (!key.equals(item.getKey()) || !conversation.equals(ChatNotifications.conversationKey(item)) || !packageName.equals(item.getPackageName())) continue;
            PendingIntent open = chatContentIntent(item);
            if (open != null) try {
                if (Build.VERSION.SDK_INT >= 34) {
                    ActivityOptions options = ActivityOptions.makeBasic();
                    options.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    open.send(this, 0, null, null, null, null, options.toBundle());
                } else open.send();
                return;
            } catch (PendingIntent.CanceledException | SecurityException ignored) { }
            break;
        }
        Toast.makeText(this, "Could not open this conversation. Use Open app instead.", Toast.LENGTH_SHORT).show();
    }
    private boolean performChatAction(String key, String conversation, String reply) {
        if (chatEnabled()) for (StatusBarNotification item : ChatNotifications.current()) {
            if (!key.equals(item.getKey()) || !conversation.equals(ChatNotifications.conversationKey(item)) || !chatAllowed(item.getPackageName())) continue;
            Notification.Action action = ChatNotifications.replyAction(item);
            if (action != null) try {
                RemoteInput input = ChatNotifications.replyInput(action);
                Intent response = new Intent(); Bundle results = new Bundle(); results.putCharSequence(input.getResultKey(), reply);
                RemoteInput.addResultsToIntent(new RemoteInput[]{input}, response, results);
                action.actionIntent.send(this, 0, response);
                Toast.makeText(this, "Reply passed to " + chatAppName(item.getPackageName()), Toast.LENGTH_SHORT).show(); return true;
            } catch (PendingIntent.CanceledException | SecurityException ignored) { }
            break;
        }
        Toast.makeText(this, "This message action is no longer available", Toast.LENGTH_SHORT).show(); return false;
    }
    private void showChatSettings() {
        showMenu("Chat settings", Arrays.asList(prefs.getBoolean("chatPreviews", false) ? "Hide message previews" : "Show message previews", "Included apps", "Screenshot protection: " + (prefs.getBoolean("chatBlockScreenshots", true) ? "On" : "Off"), "Clear conversation pins", "Android notification access", "Turn off Chats"), Arrays.asList(
            () -> { chatAvatars.evictAll(); sessionReplies.evictAll(); prefs.edit().putBoolean("chatPreviews", !prefs.getBoolean("chatPreviews", false)).apply(); showChats(); },
            this::showChatApps, this::showChatScreenshotSettings,
            () -> new AlertDialog.Builder(this).setTitle("Clear all conversation pins?")
                .setMessage("This only removes pins from Alpharail. Your messages stay in their original apps.")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear pins", (d,w) -> {
                    prefs.edit().remove("chatPins").apply(); showChats();
                }).show(),
            () -> startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            () -> { prefs.edit().putBoolean("chatsEnabled", false).putBoolean("chatPreviews", false).apply(); startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }));
    }
    private void showChatScreenshotSettings() {
        boolean blocked = prefs.getBoolean("chatBlockScreenshots", true);
        new AlertDialog.Builder(this).setTitle("Screenshot protection")
            .setMessage("When protection is on, Android blocks screenshots and screen recordings of Chats. This does not affect the rest of the launcher.")
            .setPositiveButton(blocked ? "Allow screenshots" : "Block screenshots", (dialog, which) -> {
                prefs.edit().putBoolean("chatBlockScreenshots", !blocked).apply();
                showChatSettings();
            }).setNegativeButton("Back", (dialog, which) -> showChatSettings()).show();
    }
    private void showChatApps() {
        Set<String> excluded = saved("chatExcluded"); Set<String> packages = new LinkedHashSet<>();
        for (StatusBarNotification item : ChatNotifications.current()) packages.add(item.getPackageName());
        for (ResolveInfo app : apps) if (ChatNotifications.isMessagingApp(this, app.activityInfo.packageName)) packages.add(app.activityInfo.packageName);
        List<String> entries = new ArrayList<>(packages); entries.sort(Comparator.comparing(this::chatAppName, Collator.getInstance()));
        if (entries.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Included apps").setMessage("No supported messaging apps are installed.")
                .setPositiveButton("Back", (d,w) -> showChatSettings()).show();
            return;
        }
        String[] names = new String[entries.size()]; boolean[] flags = new boolean[entries.size()];
        for (int i = 0; i < names.length; i++) { names[i] = chatAppName(entries.get(i)); flags[i] = !excluded.contains(entries.get(i)); }
        new AlertDialog.Builder(this).setTitle("Include message notifications from")
            .setMultiChoiceItems(names, flags, (d,i,checked) -> { if (checked) excluded.remove(entries.get(i)); else excluded.add(entries.get(i)); })
            .setNegativeButton("Cancel", null).setPositiveButton("Save", (d,w) -> { store("chatExcluded", excluded); showChats(); }).show();
    }
    private void backupMenu() {
        showMenu("Backup and restore", Arrays.asList("Export settings", "Import settings", "Export appearance preset", "Import appearance preset"), Arrays.asList(
            () -> settingsDocument(true, false), () -> settingsDocument(false, false),
            () -> settingsDocument(true, true), () -> settingsDocument(false, true)));
    }
    private void settingsDocument(boolean export, boolean appearanceOnly) {
        Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        if (export) intent.putExtra(Intent.EXTRA_TITLE, appearanceOnly ? "still-appearance.json" : "still-settings.json");
        startActivityForResult(intent, appearanceOnly ? (export ? EXPORT_LOOK : IMPORT_LOOK) : (export ? EXPORT_SETTINGS : IMPORT_SETTINGS));
    }
    private void exportSettings(Uri uri, boolean appearanceOnly) throws IOException, JSONException {
        JSONObject backup = new JSONObject().put("format", appearanceOnly ? "still-appearance" : "still-settings")
            .put("version", appearanceOnly ? 1 : 4);
        JSONObject looks = new JSONObject();
        for (int i = 0; i < APPEARANCE.length; i++) looks.put(APPEARANCE[i], prefs.getInt(APPEARANCE[i], DEFAULTS[i]));
        backup.put("appearance", looks);
        if (!appearanceOnly) {
            List<String> favorites = favoriteOrder();
            for (String id : new TreeSet<>(saved("favorites"))) if (!favorites.contains(id)) favorites.add(id);
            JSONArray recipes = new JSONArray();
            for (String id : widgetOrder()) {
                AppWidgetProviderInfo info = widgets.getAppWidgetInfo(Integer.parseInt(id));
                if (info != null && recipes.length() < 256) recipes.put(new JSONObject().put("provider", info.provider.flattenToString())
                    .put("height", Math.min(10000, Math.max(1, widgetHeight(id, info)))));
            }
            JSONArray queued = widgetRecovery();
            for (int i = 0; i < queued.length() && recipes.length() < 256; i++) recipes.put(queued.getJSONObject(i));
            backup.put("widgetLayout", recipes);
            JSONObject aliases = new JSONObject();
            for (Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
                if (item.getKey().startsWith("appAlias:") && item.getValue() instanceof String)
                    aliases.put(item.getKey().substring("appAlias:".length()), item.getValue());
            }
            backup.put("aliases", aliases);
            backup.put("favorites", new JSONArray(favorites)).put("hidden", new JSONArray(new TreeSet<>(saved("hidden"))))
                .put("collections", new JSONArray(prefs.getString("collections", "[]"))).put("stackWidgets", stackWidgets());
        }
        try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
            if (out == null) throw new IOException("Backup file unavailable");
            out.write(backup.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
    private List<String> backupApps(JSONObject backup, String name) throws JSONException {
        JSONArray values = backup.getJSONArray(name);
        if (values.length() > 5000) throw new JSONException("Too many app entries");
        Set<String> result = new LinkedHashSet<>();
        for (int i = 0; i < values.length(); i++) {
            Object value = values.get(i);
            if (!(value instanceof String) || ((String)value).length() > 512 || !((String)value).matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")) throw new JSONException("Invalid app ID");
            ComponentName id = ComponentName.unflattenFromString((String)value);
            if (id == null) throw new JSONException("Invalid component");
            result.add(id.flattenToString());
        }
        return new ArrayList<>(result);
    }
    private void importSettings(Uri uri, boolean appearanceOnly) throws IOException, JSONException {
        JSONObject backup;
        try (InputStream in = getContentResolver().openInputStream(uri); ByteArrayOutputStream data = new ByteArrayOutputStream()) {
            if (in == null) throw new IOException("Backup file unavailable");
            byte[] block = new byte[4096]; int size;
            while ((size = in.read(block)) != -1) {
                if (data.size() + size > 1024 * 1024) throw new IOException("Backup is too large");
                data.write(block, 0, size);
            }
            backup = new JSONObject(data.toString("UTF-8"));
        }
        Object version = backup.get("version");
        boolean hasAliases = !appearanceOnly && Integer.valueOf(4).equals(version);
        boolean hasWidgets = hasAliases || (!appearanceOnly && Integer.valueOf(3).equals(version));
        boolean extended = hasWidgets || (!appearanceOnly && Integer.valueOf(2).equals(version));
        JSONArray recipes = new JSONArray();
        if (hasWidgets) {
            JSONArray incoming = backup.getJSONArray("widgetLayout");
            if (incoming.length() > 256) throw new JSONException("Too many widgets");
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject entry = incoming.getJSONObject(i);
                Object provider = entry.get("provider"), height = entry.get("height");
                if (!(provider instanceof String) || ((String)provider).length() > 512
                    || !((String)provider).matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+")
                    || !(height instanceof Integer) || (int)height < 1 || (int)height > 10000) throw new JSONException("Invalid widget entry");
                recipes.put(new JSONObject().put("provider", ComponentName.unflattenFromString((String)provider).flattenToString()).put("height", height));
            }
        }
        if (!(appearanceOnly ? "still-appearance" : "still-settings").equals(backup.get("format"))
            || (!extended && !Integer.valueOf(1).equals(version))) throw new JSONException("Unsupported backup format");
        Map<String, String> aliases = new LinkedHashMap<>();
        if (hasAliases) {
            JSONObject incoming = backup.getJSONObject("aliases");
            if (incoming.length() > 5000) throw new JSONException("Too many custom app names");
            Iterator<String> ids = incoming.keys();
            while (ids.hasNext()) {
                String id = ids.next(); Object name = incoming.get(id);
                if (id.length() > 512 || !id.matches("[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+") || !(name instanceof String))
                    throw new JSONException("Invalid custom app name");
                String alias = ((String)name).replaceAll("[\\p{Cc}\\p{Cf}]", "").trim();
                if (alias.isEmpty() || alias.length() > 40) throw new JSONException("Invalid custom app name");
                aliases.put(ComponentName.unflattenFromString(id).flattenToString(), alias);
            }
        }
        List<String> favorites = appearanceOnly ? Collections.emptyList() : backupApps(backup, "favorites");
        List<String> hidden = appearanceOnly ? Collections.emptyList() : backupApps(backup, "hidden");
        JSONArray groups = new JSONArray();
        if (extended) {
            JSONArray incoming = backup.getJSONArray("collections");
            if (incoming.length() > 12 || !(backup.get("stackWidgets") instanceof Boolean)) throw new JSONException("Invalid layout settings");
            for (int i = 0; i < incoming.length(); i++) {
                JSONObject group = incoming.getJSONObject(i);
                Object name = group.get("name");
                if (!(name instanceof String) || ((String)name).trim().isEmpty() || ((String)name).length() > 40)
                    throw new JSONException("Invalid collection name");
                groups.put(new JSONObject().put("name", name).put("apps", new JSONArray(backupApps(group, "apps"))));
            }
        }
        JSONObject looks = backup.getJSONObject("appearance");
        int[] values = new int[APPEARANCE.length];
        for (int i = 0; i < APPEARANCE.length; i++) {
            Object value = "theme".equals(APPEARANCE[i]) && !looks.has("theme") ? prefs.getInt("theme", 0) : looks.get(APPEARANCE[i]);
            if (!(value instanceof Integer) || (int)value < 0 || (int)value > MAXIMUMS[i]) throw new JSONException("Invalid appearance value");
            values[i] = (int)value;
        }
        if (hasWidgets && pending != -1) throw new IOException("Finish adding the current widget before importing");
        String summary = appearanceOnly ? "Use this preset’s " + (values[values.length - 1] == 0 ? "White" : "Black")
            + " theme, icons, fonts, and rail side? Your apps and widgets stay unchanged."
            : "Replace Favorites, hidden apps, and appearance" + (extended ? ", plus Collections and the widget stack preference" : "")
            + "?\n\n" + favorites.size() + " Favorites · " + hidden.size() + " hidden apps"
            + (extended ? " · " + groups.length() + " Collections" : "")
            + (hasAliases ? " · " + aliases.size() + " custom names" : "")
            + (hasWidgets ? " · " + recipes.length() + " widgets to recover" : "")
            + "\n\nInstalled widgets and App Lock stay unchanged."
            + (hasWidgets ? " This replaces the recovery list. To re-add widgets, open Widgets → Recover widgets." : "");
        new AlertDialog.Builder(this).setTitle(appearanceOnly ? "Apply appearance preset?" : "Restore settings?")
            .setMessage(summary).setNegativeButton("Cancel", null).setPositiveButton(appearanceOnly ? "Apply" : "Restore", (d,w) -> {
                android.content.SharedPreferences.Editor edit = prefs.edit();
                if (!appearanceOnly) edit.putStringSet("favorites", new HashSet<>(favorites))
                    .putString("favoritesOrder", String.join("\n", favorites)).putStringSet("hidden", new HashSet<>(hidden));
                if (hasAliases) {
                    for (String pref : prefs.getAll().keySet()) if (pref.startsWith("appAlias:")) edit.remove(pref);
                    for (Map.Entry<String, String> alias : aliases.entrySet()) edit.putString("appAlias:" + alias.getKey(), alias.getValue());
                }
                if (hasWidgets) edit.putString("widgetRecovery", recipes.toString());
                if (extended) edit.putString("collections", groups.toString()).putBoolean("stackWidgets", backup.optBoolean("stackWidgets"));
                for (int i = 0; i < APPEARANCE.length; i++) edit.putInt(APPEARANCE[i], values[i]);
                boolean themeChanged = prefs.getInt("theme", 0) != values[values.length - 1];
                edit.apply(); dismissUndo();
                if (themeChanged) recreate(); else { if (hasAliases) reloadApps(); placeRail(); refresh(); }
            }).show();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 11 && pending != -1) { if (result == RESULT_OK) configureWidget(); else finishWidget(false); }
        if (request == 12) finishWidget(result == RESULT_OK);
        if (request == 13) home(scroll.getScrollY());
        if (request == CONFIRM_LAUNCH) {
            String id = pendingLaunch, shortcut = pendingShortcut; pendingLaunch = null; pendingShortcut = null;
            if (id != null && result == RESULT_OK) launchTarget(ComponentName.unflattenFromString(id), shortcut);
        }
        if ((request == EXPORT_SETTINGS || request == IMPORT_SETTINGS || request == EXPORT_LOOK || request == IMPORT_LOOK) && result == RESULT_OK && data != null && data.getData() != null) {
            boolean exporting = request == EXPORT_SETTINGS || request == EXPORT_LOOK;
            try {
                if (!"content".equals(data.getData().getScheme())) throw new IOException("Expected a document URI");
                if (exporting) { exportSettings(data.getData(), request == EXPORT_LOOK); Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show(); }
                else importSettings(data.getData(), request == IMPORT_LOOK);
            } catch (IOException | JSONException | SecurityException e) {
                Toast.makeText(this, "Couldn't " + (exporting ? "export settings" : "read a valid Alpharail backup"), Toast.LENGTH_LONG).show();
            }
        }
        if (request == CONFIRM_LOCK_SETTINGS) {
            boolean requested = pendingLockSettings; pendingLockSettings = false;
            if (requested && result == RESULT_OK) {
                showAppChoices("App lock", "Checked apps require your device PIN, pattern, or password when opened from Alpharail. This does not block other ways of opening an app.", "locked", this::showPrivacySettings);
            }
        }
    }
}
