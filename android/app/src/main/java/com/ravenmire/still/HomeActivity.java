package com.ravenmire.still;

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
    private int pending = -1;
    private String pendingLaunch, pendingShortcut;
    private LinearLayout undoBar, appArea;
    private Runnable undoAction;
    private final Runnable expireUndo = this::dismissUndo;
    private static final int EXPORT_SETTINGS = 23, IMPORT_SETTINGS = 24;
    private static final String[] APPEARANCE = {"iconStyle", "iconSize", "iconShape", "fontSize", "fontShape", "railSide", "theme"};
    private static final int[] DEFAULTS = {0, 1, 0, 1, 0, 0, 0}, MAXIMUMS = {2, 2, 3, 3, 3, 1, 1};
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
                date.setText(android.text.format.DateFormat.format("EEEE, MMMM d", now).toString().toUpperCase(Locale.getDefault()));
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
        state.putString("pendingLaunch", pendingLaunch);
        state.putString("pendingShortcut", pendingShortcut);
        state.putBoolean("pendingLockSettings", pendingLockSettings);
        state.putString("query", query);
        state.putInt("beforeSearch", beforeSearch);
        state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    }
    @Override protected void onStart() { super.onStart(); host.startListening(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); host.stopListening(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (!welcome && scroll != null && reloadApps()) refresh(); }
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
        TextView wordmark = text("STILL  /  INDEX", 12, 0xffd9dcff); wordmark.setLetterSpacing(.19f); headline.addView(wordmark);
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
    private void home(int y) {
        welcome = false; base();
        FrameLayout hero = heroFrame();
        LinearLayout face = column(); face.setPadding(dp(18), dp(13), dp(18), dp(9)); hero.addView(face);
        date = text("", 11, 0xffe0e2ff); date.setLetterSpacing(.15f); face.addView(date);
        clock = text("", 56, 0xffffffff); clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setLetterSpacing(-.06f); clock.setSingleLine(true);
        clock.setAutoSizeTextTypeUniformWithConfiguration(26, 56, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        clock.setFocusable(true); clock.setOnClickListener(v -> openClock());
        face.addView(clock, new LinearLayout.LayoutParams(-1, dp(74)));
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
        handler.removeCallbacks(tick); handler.post(tick);
        LinearLayout bar = new LinearLayout(this);
        TextView title = text("ALL APPS", 12, moss); title.setGravity(Gravity.CENTER_VERTICAL); title.setLetterSpacing(.13f);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        RoleManager role = android.os.Build.VERSION.SDK_INT >= 29 ? getSystemService(RoleManager.class) : null;
        if (role == null || !role.isRoleHeld(RoleManager.ROLE_HOME)) bar.addView(action("Set as Home", this::setHome));
        TextView chats = action("Chats", this::showChats); chats.setTextColor(accent);
        chats.setPadding(dp(14), 0, dp(14), 0);
        chats.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(0x203448ce), rounded(surface, 24), null));
        bar.addView(chats);
        root.addView(bar);
        LinearLayout searchBar = new LinearLayout(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setTextSize(15);
        search.setTextColor(ink);
        search.setHintTextColor(moss);
        search.setBackground(rounded(surface, 16));
        search.setPadding(dp(16), 0, dp(12), 0);
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        search.setText(query);
        searchBar.addView(search, new LinearLayout.LayoutParams(0, dp(46), 1));
        TextView clear = action("×", this::clearSearch);
        clear.setGravity(Gravity.CENTER);
        clear.setTextSize(22);
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
                for (ResolveInfo app : apps) if (matches(app, filter, hidden)) { openApp(app); return true; }
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
        indexScroll.setBackground(rounded(surface, 18));
        appArea.addView(indexScroll, new LinearLayout.LayoutParams(dp(40), -1));
        placeRail();
        undoBar = new LinearLayout(this); undoBar.setVisibility(View.GONE); root.addView(undoBar);
        dismissUndo();
        emptyArea(root); emptyArea(list); emptyArea(date); emptyArea(title);
        root.requestFocus();
        reloadApps();
        populate(y);
    }
    private void openClock() {
        Intent alarms = new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS);
        ResolveInfo target = getPackageManager().resolveActivity(alarms, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        List<ResolveInfo> handlers = getPackageManager().queryIntentActivities(alarms, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        if (!handlers.isEmpty()) {
            ResolveInfo chosen = handlers.get(0);
            if (target != null && target.activityInfo != null) for (ResolveInfo handler : handlers) {
                if (component(target).equals(component(handler))) { chosen = handler; break; }
            }
            String packageName = chosen.activityInfo.packageName;
            if (saved("locked").contains(packageName)) {
                for (ResolveInfo app : apps) if (packageName.equals(app.activityInfo.packageName)) { openApp(app); return; }
                Toast.makeText(this, "Clock is locked", Toast.LENGTH_SHORT).show(); return;
            }
            try { startActivity(alarms.setComponent(component(chosen))); return; }
            catch (ActivityNotFoundException | SecurityException ignored) { }
        }
        for (ResolveInfo app : apps) if ("Clock".equalsIgnoreCase(label(app))) { openApp(app); return; }
        Toast.makeText(this, "No Clock app available", Toast.LENGTH_SHORT).show();
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
        if (!welcome && !query.isEmpty()) clearSearch();
        else super.onBackPressed();
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
    private String label(ResolveInfo app) { return app.loadLabel(getPackageManager()).toString(); }
    private ComponentName component(ResolveInfo app) { return new ComponentName(app.activityInfo.packageName, app.activityInfo.name); }
    private String key(ResolveInfo app) { return component(app).flattenToString(); }
    private boolean reloadApps() {
        List<ResolveInfo> current = new ArrayList<>(getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        current.removeIf(app -> app.activityInfo.packageName.equals(getPackageName()));
        Collator sorter = Collator.getInstance();
        current.sort((a,b) -> {
            int order = sorter.compare(label(a), label(b));
            return order == 0 ? key(a).compareTo(key(b)) : order;
        });
        boolean changed = current.size() != apps.size();
        for (int i = 0; !changed && i < current.size(); i++) {
            ResolveInfo app = current.get(i), previous = apps.get(i);
            changed = !key(app).equals(key(previous)) || !label(app).equals(label(previous))
                || !Objects.equals(app.activityInfo.applicationInfo.sourceDir, previous.activityInfo.applicationInfo.sourceDir);
        }
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
            for (ResolveInfo app : apps) if (matches(app, filter, hidden)) {
                row(app, favorites.contains(key(app)), false); any = true;
            }
            if (!any) list.addView(text("No matching apps", 15, moss));
        } else {
            section("★   FAVORITES", "★", "Favorites");
            Map<String, ResolveInfo> byKey = new HashMap<>();
            for (ResolveInfo app : apps) byKey.put(key(app), app);
            List<String> orderedFavorites = favoriteOrder();
            orderedFavorites.removeIf(hidden::contains);
            for (String key : orderedFavorites) row(byKey.get(key), true, true);
            if (orderedFavorites.isEmpty()) list.addView(text("Star an app below to pin it here.", 14, moss));
            for (String id : widgetOrder()) widgetRow(id);

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
    private boolean matches(ResolveInfo app, String filter, Set<String> hidden) {
        return !hidden.contains(key(app)) && searchKey(label(app)).contains(filter);
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
        for (String id : widgetOrder()) {
            AppWidgetProviderInfo info = widgets.getAppWidgetInfo(Integer.parseInt(id));
            if (info == null) continue;
            String name = info.loadLabel(getPackageManager());
            labels.add("Manage " + name); actions.add(() -> showWidgetMenu(id, info, name));
        }
        showMenu("Widgets", labels, actions);
    }
    private void showSettings() {
        showSettingsPage("Launcher settings", "Make Still yours", new String[]{"Theme", "Icons", "Fonts", "App list", "Privacy", "Backup and restore", "Set as Home"},
            new String[]{prefs.getInt("theme", 0) == 1 ? "Black palette" : "White palette", "Style, size and shape", "Typeface and text size", "Favorites and alphabet placement", "Hidden apps and App Lock", "Move your settings to another device", "Choose your default launcher"},
            new String[]{"◐", "▦", "Aa", "A–Z", "◇", "⇅", "⌂"}, Arrays.asList(this::showThemeSettings, this::showIconSettings, this::showFontSettings,
                this::showListSettings, this::showPrivacySettings, this::backupMenu, this::setHome), null);
    }
    private void showSettingsPage(String title, String subtitle, String[] labels, String[] details, String[] glyphs, List<Runnable> actions, Runnable back) {
        Dialog page = new Dialog(this);
        page.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout content = column(); content.setBackgroundColor(paper); content.setPadding(dp(18), dp(10), dp(18), dp(8));
        TextView close = action(back == null ? "Close settings" : "‹  Settings", () -> { page.dismiss(); if (back != null) back.run(); });
        close.setTextColor(accent); content.addView(close);
        FrameLayout banner = heroFrame(); LinearLayout heading = column(); heading.setPadding(dp(18), dp(12), dp(18), dp(14));
        TextView kicker = text("STILL STUDIO", 10, 0xffd9dcff); kicker.setLetterSpacing(.2f); heading.addView(kicker);
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
            row.setOnClickListener(v -> { page.dismiss(); select.run(); });
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
            window.setStatusBarColor(paper); window.setNavigationBarColor(paper);
            window.getDecorView().setSystemUiVisibility(prefs.getInt("theme", 0) == 1 ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
    }
    private void showThemeSettings() {
        new AlertDialog.Builder(this).setTitle("Theme").setSingleChoiceItems(new String[]{"White", "Black"}, prefs.getInt("theme", 0), (dialog, selection) -> {
            dialog.dismiss();
            if (selection != prefs.getInt("theme", 0)) {
                prefs.edit().putInt("theme", selection).apply(); recreate();
            } else showSettings();
        }).setNegativeButton("Back", (d,w) -> showSettings()).show();
    }
    private void showIconSettings() {
        showSettingsGroup("Icons", Arrays.asList("Icon style", "Icon size", "Icon shape"), Arrays.asList(
            () -> setting("Icon style", "iconStyle", new String[]{"Original", "Monochrome", "Initials"}, 0, this::showIconSettings),
            () -> setting("Icon size", "iconSize", new String[]{"Small", "Default", "Large"}, 1, this::showIconSettings),
            () -> setting("Icon shape", "iconShape", new String[]{"Original", "Rounded square", "Circle", "Square"}, 0, this::showIconSettings)));
    }
    private void showFontSettings() {
        showSettingsGroup("Fonts", Arrays.asList("Font size", "Typeface"), Arrays.asList(
            () -> setting("Font size", "fontSize", new String[]{"Small", "Default", "Large", "Extra large"}, 1, this::showFontSettings),
            () -> setting("Typeface", "fontShape", new String[]{"System", "Rounded", "Serif", "Monospace"}, 0, this::showFontSettings)));
    }
    private void showListSettings() {
        showSettingsGroup("App list", Arrays.asList("Reorder Favorites", "Alphabet side"), Arrays.asList(
            this::reorderFavorites, () -> setting("Alphabet side", "railSide", new String[]{"Right", "Left"}, 0, this::showListSettings)));
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
        showAppChoices("Hidden apps", "Checked apps are hidden from Still, but stay installed.", "hidden", this::showPrivacySettings);
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
    private void showAppActions(ResolveInfo app, boolean inFavorites) {
        dismissKeyboard();
        List<String> labels = new ArrayList<>(); List<Runnable> actions = new ArrayList<>();
        labels.add("App info"); actions.add(() -> {
            try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.activityInfo.packageName, null))); }
            catch (ActivityNotFoundException | SecurityException ignored) { Toast.makeText(this, "App info unavailable", Toast.LENGTH_SHORT).show(); }
        });
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
        if (!lockedApps.contains(app.activityInfo.packageName)) { launchTarget(component(app), shortcut); return; }
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
        try { startActivity(Intent.makeMainActivity(component)); }
        catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show(); reloadApps(); refresh();
        }
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
    private void widgetRow(String value) {
        int id = Integer.parseInt(value);
        AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
        if (info == null) return;
        String name = info.loadLabel(getPackageManager());
        TextView edit = action(name + "  ⋯", () -> showWidgetMenu(value, info, name));
        edit.setContentDescription("Manage " + name + " widget");
        list.addView(edit);
        AppWidgetHostView view = host.createView(this, id, info);
        int height = widgetHeight(value, info);
        view.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> {
            if (r - l > 0 && r - l != or - ol) {
                int width = Math.max(1, toDp(r - l));
                view.updateAppWidgetSize(null, width, height, width, height);
            }
        });
        list.addView(view, new LinearLayout.LayoutParams(-1, dp(height)));
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
                host.deleteAppWidgetId(Integer.parseInt(id)); refresh();
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
        letterBubble.setText(letter.getText()); letterBubble.setVisibility(View.VISIBLE);
        letterBubble.setAlpha(1);
        letterBubble.setScaleX(animate ? .86f : 1); letterBubble.setScaleY(animate ? .86f : 1);
        if (animate) letterBubble.animate().scaleX(1).scaleY(1).setDuration(130).start();
    }
    private void section(String name, String shortcut, String description) {
        TextView title = text(name, 12, accent); title.setLetterSpacing(.12f); title.setTypeface(null, Typeface.BOLD); title.setPadding(0, dp(13), 0, dp(5)); list.addView(title); emptyArea(title);
        if (shortcut != null) {
            TextView jump = action(shortcut, () -> {});
            jump.setTag(title); jump.setOnClickListener(v -> jumpToLetter(jump, true));
            jump.setGravity(Gravity.CENTER); jump.setMinHeight(0); jump.setIncludeFontPadding(false); jump.setTextSize(12);
            jump.setAutoSizeTextTypeUniformWithConfiguration(8, 12, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
            jump.setTextColor(new ColorStateList(new int[][]{new int[]{android.R.attr.state_selected}, new int[]{}}, new int[]{0xffffffff, moss}));
            android.graphics.drawable.StateListDrawable state = new android.graphics.drawable.StateListDrawable();
            state.addState(new int[]{android.R.attr.state_selected}, rounded(PRIMARY, 12));
            state.addState(new int[]{}, new ColorDrawable(android.graphics.Color.TRANSPARENT)); jump.setBackground(state);
            jump.setContentDescription("Jump to " + description); index.addView(jump, new LinearLayout.LayoutParams(-1, 0, 1));
        }
    }
    private void row(ResolveInfo app, boolean favorite, boolean inFavorites) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        int iconSize = new int[]{20, 26, 36}[prefs.getInt("iconSize", 1)];
        int fontSize = new int[]{13, 15, 18, 21}[prefs.getInt("fontSize", 1)];
        int height = Math.max(48, iconSize + 14);
        boolean locked = lockedApps.contains(app.activityInfo.packageName);
        LinearLayout launchRow = new LinearLayout(this); launchRow.setGravity(Gravity.CENTER_VERTICAL);
        launchRow.setContentDescription(label(app) + (locked ? ", locked" : ""));
        TextView open = action(label(app) + (locked ? "  🔒" : ""), () -> openApp(app));
        launchRow.setFocusable(true);
        launchRow.setOnClickListener(v -> openApp(app));
        android.graphics.drawable.Drawable ripple = open.getBackground();
        open.setBackground(null); open.setClickable(false); open.setFocusable(false);
        launchRow.setBackground(ripple);
        open.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        open.setTextColor(ink); open.setTextSize(fontSize);
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
        star.setGravity(Gravity.CENTER); if (favorite) star.setTextColor(accent); star.setContentDescription((favorite ? "Unfavorite " : "Favorite ") + label(app)); line.addView(star, new LinearLayout.LayoutParams(dp(44), dp(height)));
        list.addView(line);
    }
    private View appIcon(ResolveInfo app, int size) {
        int style = prefs.getInt("iconStyle", 0), shape = prefs.getInt("iconShape", 0);
        View icon;
        if (style == 2) {
            String name = label(app).trim();
            String initial = name.isEmpty() ? "?" : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.getDefault());
            TextView initials = text(initial, Math.max(11, size / 2), ink);
            initials.setGravity(Gravity.CENTER); initials.setTypeface(null, Typeface.BOLD); icon = initials;
        } else {
            ImageView image = new ImageView(this); image.setImageDrawable(app.loadIcon(getPackageManager()));
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
    private void pickWidget() {
        List<AppWidgetProviderInfo> options = new ArrayList<>(widgets.getInstalledProviders());
        options.sort(Comparator.comparing(p -> p.loadLabel(getPackageManager()), Collator.getInstance()));
        if (options.isEmpty()) { Toast.makeText(this, "No widgets available", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[options.size()];
        for (int i = 0; i < names.length; i++) names[i] = options.get(i).loadLabel(getPackageManager());
        new AlertDialog.Builder(this).setTitle("Add widget").setItems(names, (d,i) -> bindWidget(options.get(i))).setNegativeButton("Cancel", null).show();
    }
    private void bindWidget(AppWidgetProviderInfo info) {
        if (pending != -1) return;
        pending = host.allocateAppWidgetId();
        if (widgets.bindAppWidgetIdIfAllowed(pending, info.provider)) configureWidget();
        else {
            Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pending).putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider);
            try { startActivityForResult(intent, 11); } catch (ActivityNotFoundException e) { finishWidget(false); }
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
        if (accepted) { Set<String> ids = saved("widgets"); ids.add(Integer.toString(pending)); store("widgets", ids); }
        else host.deleteAppWidgetId(pending);
        pending = -1; refresh();
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
                .setMessage("Optional: show active messaging notifications and reply where the app supports it. Android notification access can read all notifications. Still only displays message notifications here, does not save or upload their contents, and cannot retrieve chat history. Photos and unsupported replies open in the original app.")
                .setNegativeButton("Not now", null).setPositiveButton("Enable Chats", (d,w) -> {
                    prefs.edit().putBoolean("chatsEnabled", true).apply();
                    if (chatEnabled()) showChats(); else startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                }).show();
            return;
        }
        LinearLayout content = column(); content.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView note = text("Active notifications only", 13, moss); content.addView(note);
        ScrollView body = new ScrollView(this); LinearLayout rows = column(); body.addView(rows);
        content.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout buttons = new LinearLayout(this); content.addView(buttons);
        AlertDialog panel = new AlertDialog.Builder(this).setTitle("Chats").setView(content).setNegativeButton("Close", null).create();
        Runnable redraw = () -> fillChats(rows);
        buttons.addView(action("Refresh", redraw), new LinearLayout.LayoutParams(0, dp(44), 1));
        buttons.addView(action("Chat settings", () -> { panel.dismiss(); showChatSettings(); }), new LinearLayout.LayoutParams(0, dp(44), 1));
        panel.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        panel.show(); panel.getWindow().setLayout(-1, Math.min(dp(640), Math.round(getResources().getDisplayMetrics().heightPixels * .86f)));
        redraw.run();
    }
    private void fillChats(LinearLayout rows) {
        rows.removeAllViews();
        boolean showText = prefs.getBoolean("chatPreviews", false);
        int count = 0;
        if (chatEnabled()) for (StatusBarNotification item : ChatNotifications.current()) {
            if (!chatAllowed(item.getPackageName())) continue;
            Notification n = item.getNotification();
            rows.addView(text(chatAppName(item.getPackageName()), 15, moss));
            if (showText && n.visibility != Notification.VISIBILITY_SECRET && n.extras != null) {
                CharSequence title = n.extras.getCharSequence(Notification.EXTRA_TITLE), body = n.extras.getCharSequence(Notification.EXTRA_TEXT);
                Parcelable[] messages = n.extras.getParcelableArray(Notification.EXTRA_MESSAGES);
                if (messages != null && messages.length > 0 && messages[messages.length - 1] instanceof Bundle) {
                    CharSequence latest = ((Bundle)messages[messages.length - 1]).getCharSequence("text");
                    if (latest != null) body = latest;
                }
                if (title != null) rows.addView(text(chatText(title), 15, ink));
                if (body != null) { TextView preview = text(chatText(body), 14, ink); preview.setMaxLines(4); rows.addView(preview); }
            } else rows.addView(text("Message preview hidden", 14, ink));
            LinearLayout actions = new LinearLayout(this); rows.addView(actions);
            actions.addView(action("Open app", () -> openChatApp(item.getPackageName())), new LinearLayout.LayoutParams(0, dp(44), 1));
            if (chatContentIntent(item) != null) actions.addView(action("Open chat", () -> openChat(item.getKey(), item.getPackageName())), new LinearLayout.LayoutParams(0, dp(44), 1));
            if (ChatNotifications.replyAction(item) != null) actions.addView(action("Reply", () -> showChatReply(item)), new LinearLayout.LayoutParams(0, dp(44), 1));
            View separator = new View(this); separator.setBackgroundColor(dividerColor); rows.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
            if (++count == 50) break;
        }
        if (count == 0) rows.addView(text(ChatNotifications.available() ? "No active messages from included apps." : "Waiting for notification access. Try Refresh after enabling Still Chats in Android settings.", 15, ink));
    }
    private String chatText(CharSequence value) { String text = value.toString(); return text.length() > 600 ? text.substring(0, 600) + "…" : text; }
    private void showChatReply(StatusBarNotification item) {
        EditText reply = new EditText(this); reply.setHint("Write a text reply");
        reply.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4000)});
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Reply via " + chatAppName(item.getPackageName())).setView(reply)
            .setNegativeButton("Cancel", null).setPositiveButton("Send", (d,w) -> {
                String message = reply.getText().toString().trim(); if (!message.isEmpty()) performChatAction(item.getKey(), message);
            }).create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE); dialog.show();
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
    private void openChat(String key, String packageName) {
        if (!chatEnabled() || !chatAllowed(packageName)) return;
        for (StatusBarNotification item : ChatNotifications.current()) {
            if (!key.equals(item.getKey()) || !packageName.equals(item.getPackageName())) continue;
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
        openChatApp(packageName);
    }
    private void performChatAction(String key, String reply) {
        if (chatEnabled()) for (StatusBarNotification item : ChatNotifications.current()) {
            if (!key.equals(item.getKey()) || !chatAllowed(item.getPackageName())) continue;
            Notification.Action action = ChatNotifications.replyAction(item);
            if (action != null) try {
                RemoteInput input = ChatNotifications.replyInput(action);
                Intent response = new Intent(); Bundle results = new Bundle(); results.putCharSequence(input.getResultKey(), reply);
                RemoteInput.addResultsToIntent(new RemoteInput[]{input}, response, results);
                action.actionIntent.send(this, 0, response);
                Toast.makeText(this, "Reply passed to " + chatAppName(item.getPackageName()), Toast.LENGTH_SHORT).show(); return;
            } catch (PendingIntent.CanceledException | SecurityException ignored) { }
            break;
        }
        Toast.makeText(this, "This message action is no longer available", Toast.LENGTH_SHORT).show();
    }
    private void showChatSettings() {
        showMenu("Chat settings", Arrays.asList(prefs.getBoolean("chatPreviews", false) ? "Hide message previews" : "Show message previews", "Included apps", "Android notification access", "Turn off Chats"), Arrays.asList(
            () -> { prefs.edit().putBoolean("chatPreviews", !prefs.getBoolean("chatPreviews", false)).apply(); showChats(); },
            this::showChatApps,
            () -> startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)),
            () -> { prefs.edit().putBoolean("chatsEnabled", false).putBoolean("chatPreviews", false).apply(); startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); }));
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
        showMenu("Backup and restore", Arrays.asList("Export settings", "Import settings"), Arrays.asList(
            () -> startActivityForResult(new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "still-settings.json"), EXPORT_SETTINGS),
            () -> startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json"), IMPORT_SETTINGS)));
    }
    private void exportSettings(Uri uri) throws IOException, JSONException {
        JSONObject backup = new JSONObject(); backup.put("format", "still-settings"); backup.put("version", 1);
        JSONObject looks = new JSONObject();
        for (int i = 0; i < APPEARANCE.length; i++) looks.put(APPEARANCE[i], prefs.getInt(APPEARANCE[i], DEFAULTS[i]));
        backup.put("appearance", looks);
        List<String> favorites = favoriteOrder();
        for (String id : new TreeSet<>(saved("favorites"))) if (!favorites.contains(id)) favorites.add(id);
        backup.put("favorites", new JSONArray(favorites)); backup.put("hidden", new JSONArray(new TreeSet<>(saved("hidden"))));
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
    private void importSettings(Uri uri) throws IOException, JSONException {
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
        if (!"still-settings".equals(backup.get("format")) || !Integer.valueOf(1).equals(backup.get("version"))) throw new JSONException("Unsupported backup format");
        List<String> favorites = backupApps(backup, "favorites"), hidden = backupApps(backup, "hidden");
        JSONObject looks = backup.getJSONObject("appearance");
        int[] values = new int[APPEARANCE.length];
        for (int i = 0; i < APPEARANCE.length; i++) {
            Object value = "theme".equals(APPEARANCE[i]) && !looks.has("theme") ? prefs.getInt("theme", 0) : looks.get(APPEARANCE[i]);
            if (!(value instanceof Integer) || (int)value < 0 || (int)value > MAXIMUMS[i]) throw new JSONException("Invalid appearance value");
            values[i] = (int)value;
        }
        new AlertDialog.Builder(this).setTitle("Restore settings?")
            .setMessage("Replace Favorites, hidden apps, and appearance with this backup? Widgets and App Lock will stay unchanged.")
            .setNegativeButton("Cancel", null).setPositiveButton("Restore", (d,w) -> {
                android.content.SharedPreferences.Editor edit = prefs.edit().putStringSet("favorites", new HashSet<>(favorites))
                    .putString("favoritesOrder", String.join("\n", favorites)).putStringSet("hidden", new HashSet<>(hidden));
                for (int i = 0; i < APPEARANCE.length; i++) edit.putInt(APPEARANCE[i], values[i]);
                boolean themeChanged = prefs.getInt("theme", 0) != values[values.length - 1];
                edit.apply(); dismissUndo();
                if (themeChanged) recreate(); else { placeRail(); refresh(); }
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
        if ((request == EXPORT_SETTINGS || request == IMPORT_SETTINGS) && result == RESULT_OK && data != null && data.getData() != null) {
            try {
                if (request == EXPORT_SETTINGS) { exportSettings(data.getData()); Toast.makeText(this, "Settings exported", Toast.LENGTH_SHORT).show(); }
                else importSettings(data.getData());
            } catch (IOException | JSONException | SecurityException e) {
                Toast.makeText(this, "Couldn't " + (request == EXPORT_SETTINGS ? "export settings" : "read a valid Still backup"), Toast.LENGTH_LONG).show();
            }
        }
        if (request == CONFIRM_LOCK_SETTINGS) {
            boolean requested = pendingLockSettings; pendingLockSettings = false;
            if (requested && result == RESULT_OK) {
                showAppChoices("App lock", "Checked apps require your device PIN, pattern, or password when opened from Still. This does not block other ways of opening an app.", "locked", this::showPrivacySettings);
            }
        }
    }
}
