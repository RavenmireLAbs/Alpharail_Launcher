package com.ravenmire.still;

import android.app.*;
import android.app.role.RoleManager;
import android.appwidget.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
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
    private EditText search;
    private String query = "";
    private int beforeSearch;
    private List<ResolveInfo> apps = Collections.emptyList();
    private Set<String> lockedApps = Collections.emptySet();
    private android.content.SharedPreferences prefs;
    private ScrollView scroll, indexScroll;
    private LinearLayout root, list, index;
    private AppWidgetHost host;
    private AppWidgetManager widgets;
    private int pending = -1;
    private String pendingLaunch;
    private boolean pendingLockSettings;
    private static final int CONFIRM_LAUNCH = 21, CONFIRM_LOCK_SETTINGS = 22;
    private boolean welcome;
    private TextView clock, date;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        public void run() {
            if (clock != null && date != null) {
                Date now = new Date();
                clock.setText(android.text.format.DateFormat.getTimeFormat(HomeActivity.this).format(now));
                date.setText(android.text.format.DateFormat.format("EEEE, MMMM d", now).toString().toUpperCase(Locale.getDefault()));
            }
            handler.postDelayed(this, 15000);
        }
    };

    @Override public void onCreate(Bundle state) {
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (dark) setTheme(android.R.style.Theme_Material_NoActionBar);
        super.onCreate(state);
        paper = dark ? 0xff1b221a : 0xfff5f7ef;
        ink = dark ? 0xffecf2e7 : 0xff20261e;
        moss = dark ? 0xffb8cea9 : 0xff4c6340;
        dividerColor = dark ? 0xff394638 : 0xffd9e0d2;
        prefs = getSharedPreferences("still", MODE_PRIVATE);
        host = new AppWidgetHost(this, 101);
        widgets = AppWidgetManager.getInstance(this);
        if (state != null) {
            pending = state.getInt("pending", -1);
            pendingLaunch = state.getString("pendingLaunch");
            pendingLockSettings = state.getBoolean("pendingLockSettings");
            query = state.getString("query", "");
            beforeSearch = state.getInt("beforeSearch");
        }
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        getWindow().getDecorView().setSystemUiVisibility(dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (prefs.getBoolean("welcome", false)) home(state == null ? 0 : state.getInt("scroll")); else welcome();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("pending", pending);
        state.putString("pendingLaunch", pendingLaunch);
        state.putBoolean("pendingLockSettings", pendingLockSettings);
        state.putString("query", query);
        state.putInt("beforeSearch", beforeSearch);
        state.putInt("scroll", scroll == null ? 0 : scroll.getScrollY());
    }
    @Override protected void onStart() { super.onStart(); host.startListening(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); host.stopListening(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (!welcome && scroll != null && reloadApps()) refresh(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (!welcome && scroll != null) { clearSearch(); scroll.post(() -> scroll.scrollTo(0, 0)); } }

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
    private void base() {
        root = column(); root.setFocusableInTouchMode(true); root.setBackgroundColor(paper); root.setPadding(dp(22), dp(10), dp(16), 0); setContentView(root);
    }
    private void welcome() {
        welcome = true; base(); root.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text("✳   WELCOME TO STILL", 15, moss));
        TextView title = text("Make room\nfor less.", 53, ink); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView detail = text("Every app, one screen.\n\nSearch or scroll the alphabetical list. Hold an empty area for widgets and launcher settings.", 17, moss);
        detail.setPadding(0, dp(14), dp(8), dp(20)); root.addView(detail);
        root.addView(action("Open launcher  →", () -> { prefs.edit().putBoolean("welcome", true).apply(); home(0); }));
    }
    private void home(int y) {
        welcome = false; base();
        date = text("", 12, moss); date.setLetterSpacing(.12f); root.addView(date);
        clock = text("", 60, ink); clock.setTypeface(null, Typeface.BOLD); root.addView(clock);
        handler.removeCallbacks(tick); handler.post(tick);
        LinearLayout bar = new LinearLayout(this);
        TextView title = text("ALL APPS", 12, moss); title.setGravity(Gravity.CENTER_VERTICAL); title.setLetterSpacing(.13f);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(44), 1));
        RoleManager role = android.os.Build.VERSION.SDK_INT >= 29 ? getSystemService(RoleManager.class) : null;
        if (role == null || !role.isRoleHeld(RoleManager.ROLE_HOME)) bar.addView(action("Set as Home", this::setHome));
        root.addView(bar);
        LinearLayout searchBar = new LinearLayout(this);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("Search apps");
        search.setTextSize(15);
        search.setTextColor(ink);
        search.setHintTextColor(moss);
        search.setBackgroundTintList(ColorStateList.valueOf(moss));
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
        root.addView(empty, new LinearLayout.LayoutParams(-1, dp(32)));
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
            if (id != android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) return false;
            dismissKeyboard(); return true;
        });
        LinearLayout area = new LinearLayout(this); root.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(this); list = column(); scroll.addView(list); area.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        indexScroll = new ScrollView(this); indexScroll.setVerticalScrollBarEnabled(false); index = column(); indexScroll.addView(index); area.addView(indexScroll, new LinearLayout.LayoutParams(dp(40), -1));
        emptyArea(root); emptyArea(list); emptyArea(date); emptyArea(clock); emptyArea(title);
        root.requestFocus();
        reloadApps();
        populate(y);
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
        list.removeAllViews(); index.removeAllViews();
        Set<String> hidden = saved("hidden"), favorites = saved("favorites");
        lockedApps = saved("locked");
        String filter = searchKey(query);
        indexScroll.setVisibility(filter.isEmpty() ? View.VISIBLE : View.GONE);
        if (!filter.isEmpty()) {
            section("SEARCH RESULTS", null, "");
            boolean any = false;
            for (ResolveInfo app : apps) if (!hidden.contains(key(app)) && searchKey(label(app)).contains(filter)) {
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
        showMenu("Launcher settings", Arrays.asList("Icon style", "Icon size", "Icon shape", "Font size", "Font shape", "Hidden apps", "App lock", "Reorder Favorites", "Set as Home"), Arrays.asList(
            () -> setting("Icon style", "iconStyle", new String[]{"Original", "Monochrome", "Initials"}, 0),
            () -> setting("Icon size", "iconSize", new String[]{"Small", "Default", "Large"}, 1),
            () -> setting("Icon shape", "iconShape", new String[]{"Original", "Rounded square", "Circle", "Square"}, 0),
            () -> setting("Font size", "fontSize", new String[]{"Small", "Default", "Large", "Extra large"}, 1),
            () -> setting("Font shape", "fontShape", new String[]{"System", "Rounded", "Serif", "Monospace"}, 0),
            this::showHiddenApps, this::requestLockSettings, this::reorderFavorites, this::setHome));
    }
    private void setting(String title, String key, String[] values, int fallback) {
        new AlertDialog.Builder(this).setTitle(title).setSingleChoiceItems(values, prefs.getInt(key, fallback), (d,i) -> {
            prefs.edit().putInt(key, i).apply();
            d.dismiss(); refresh(); showSettings();
        }).setNegativeButton("Back", (d,w) -> showSettings()).show();
    }
    private void showHiddenApps() {
        showAppChoices("Hidden apps", "Checked apps are hidden from Still, but stay installed.", "hidden", this::showSettings);
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
            .setPositiveButton("Save", (d,w) -> { store(pref, chosen); refresh(); }).show();
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
    private void openApp(ResolveInfo app) {
        dismissKeyboard();
        String id = key(app);
        if (!lockedApps.contains(app.activityInfo.packageName)) { launch(component(app)); return; }
        if (pendingLaunch != null || pendingLockSettings) return;
        pendingLaunch = id;
        if (!requestCredential(CONFIRM_LAUNCH, "Unlock " + label(app))) pendingLaunch = null;
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
    private void section(String name, String shortcut, String description) {
        TextView title = text(name, 13, moss); title.setPadding(0, dp(11), 0, dp(4)); list.addView(title); emptyArea(title);
        if (shortcut != null) {
            TextView jump = action(shortcut, () -> scroll.smoothScrollTo(0, title.getTop()));
            jump.setGravity(Gravity.CENTER); jump.setMinHeight(dp(29)); jump.setTextSize(13); jump.setContentDescription("Jump to " + description); index.addView(jump);
        }
    }
    private void row(ResolveInfo app, boolean favorite, boolean inFavorites) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        int iconSize = new int[]{20, 26, 36}[prefs.getInt("iconSize", 1)];
        int fontSize = new int[]{13, 15, 18, 21}[prefs.getInt("fontSize", 1)];
        int height = Math.max(46, iconSize + 14);
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
        if (inFavorites) launchRow.setOnLongClickListener(v -> { showFavoriteMenu(app); return true; });
        TextView star = action(favorite ? "★" : "☆", () -> {
            Set<String> favorites = saved("favorites"); String key = key(app);
            List<String> order = favoriteOrder();
            if (favorites.remove(key)) order.remove(key); else { favorites.add(key); order.add(key); }
            store("favorites", favorites); saveOrder("favorites", order); refresh();
        });
        star.setGravity(Gravity.CENTER); star.setContentDescription((favorite ? "Unfavorite " : "Favorite ") + label(app)); line.addView(star, new LinearLayout.LayoutParams(dp(44), dp(height)));
        list.addView(line);
        View divider = new View(this); divider.setBackgroundColor(dividerColor); list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
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
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 11 && pending != -1) { if (result == RESULT_OK) configureWidget(); else finishWidget(false); }
        if (request == 12) finishWidget(result == RESULT_OK);
        if (request == 13) home(scroll.getScrollY());
        if (request == CONFIRM_LAUNCH) {
            String id = pendingLaunch; pendingLaunch = null;
            if (id != null && result == RESULT_OK) launch(ComponentName.unflattenFromString(id));
        }
        if (request == CONFIRM_LOCK_SETTINGS) {
            boolean requested = pendingLockSettings; pendingLockSettings = false;
            if (requested && result == RESULT_OK) {
                showAppChoices("App lock", "Checked apps require your device PIN, pattern, or password when opened from Still. This does not block other ways of opening an app.", "locked", this::showSettings);
            }
        }
    }
}
