package com.ravenmire.still;

import android.app.*;
import android.app.role.RoleManager;
import android.appwidget.*;
import android.content.*;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.text.Collator;
import java.util.*;

public class HomeActivity extends Activity {
    private final int paper = Color.rgb(245,247,239), ink = Color.rgb(32,38,30), moss = Color.rgb(76,99,64);
    private android.content.SharedPreferences prefs;
    private ScrollView scroll, indexScroll;
    private LinearLayout root, list, index;
    private AppWidgetHost host;
    private AppWidgetManager widgets;
    private int pending = -1;
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
        super.onCreate(state);
        prefs = getSharedPreferences("still", MODE_PRIVATE);
        host = new AppWidgetHost(this, 101);
        widgets = AppWidgetManager.getInstance(this);
        if (state != null) pending = state.getInt("pending", -1);
        getWindow().setStatusBarColor(paper);
        getWindow().setNavigationBarColor(paper);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (prefs.getBoolean("welcome", false)) home(0); else welcome();
    }
    @Override protected void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putInt("pending", pending); }
    @Override protected void onStart() { super.onStart(); host.startListening(); handler.post(tick); }
    @Override protected void onStop() { handler.removeCallbacks(tick); host.stopListening(); super.onStop(); }
    @Override protected void onResume() { super.onResume(); if (!welcome && scroll != null) refresh(); }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); if (!welcome && scroll != null) scroll.scrollTo(0, 0); }

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
        root = column(); root.setBackgroundColor(paper); root.setPadding(dp(22), dp(10), dp(16), 0); setContentView(root);
    }
    private void welcome() {
        welcome = true; base(); root.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text("✳   WELCOME TO STILL", 15, moss));
        TextView title = text("Make room\nfor less.", 53, ink); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        TextView detail = text("Every app, one screen.\n\nScroll the alphabetical list or jump to a letter. Star the apps you use most and add widgets to Favorites.", 17, moss);
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
        LinearLayout area = new LinearLayout(this); root.addView(area, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(this); list = column(); scroll.addView(list); area.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        indexScroll = new ScrollView(this); indexScroll.setVerticalScrollBarEnabled(false); index = column(); indexScroll.addView(index); area.addView(indexScroll, new LinearLayout.LayoutParams(dp(40), -1));
        populate(y);
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
    private void populate(int y) {
        list.removeAllViews(); index.removeAllViews();
        List<ResolveInfo> apps = new ArrayList<>(getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0));
        apps.removeIf(app -> app.activityInfo.packageName.equals(getPackageName()));
        Collator sorter = Collator.getInstance(); apps.sort((a,b) -> sorter.compare(label(a), label(b)));
        Set<String> favorites = saved("favorites");
        section("★   FAVORITES", "★", "Favorites");
        boolean any = false;
        for (ResolveInfo app : apps) if (favorites.contains(component(app).flattenToString())) { row(app, true, false); any = true; }
        if (!any) list.addView(text("Star an app below to pin it here.", 14, moss));
        for (String value : new TreeSet<>(saved("widgets"))) {
            int id = Integer.parseInt(value);
            AppWidgetProviderInfo info = widgets.getAppWidgetInfo(id);
            if (info == null) continue;
            TextView remove = action("Remove widget · " + info.loadLabel(getPackageManager()), () -> {
                new AlertDialog.Builder(this).setMessage("Remove this widget?").setNegativeButton("Cancel", null).setPositiveButton("Remove", (d,w) -> {
                    Set<String> ids = saved("widgets"); ids.remove(value); store("widgets", ids); host.deleteAppWidgetId(id); refresh();
                }).show();
            });
            list.addView(remove);
            AppWidgetHostView view = host.createView(this, id, info);
            int height = Math.max(dp(80), info.minHeight);
            view.updateAppWidgetSize(null, getResources().getConfiguration().screenWidthDp - 78, (int)(height / getResources().getDisplayMetrics().density), getResources().getConfiguration().screenWidthDp - 78, (int)(height / getResources().getDisplayMetrics().density));
            list.addView(view, new LinearLayout.LayoutParams(-1, height));
        }
        list.addView(action("＋  Add widget", this::pickWidget));
        Set<String> seen = new HashSet<>();
        String previous = null;
        for (ResolveInfo app : apps) {
            String name = label(app).trim(); String letter = name.isEmpty() ? "#" : new String(Character.toChars(name.codePointAt(0))).toUpperCase(Locale.getDefault());
            if (!letter.equals(previous)) { section(letter, seen.add(letter) ? letter : null, letter); previous = letter; }
            row(app, favorites.contains(component(app).flattenToString()), true);
        }
        scroll.post(() -> scroll.scrollTo(0, y));
    }
    private void section(String name, String shortcut, String description) {
        TextView title = text(name, 13, moss); title.setPadding(0, dp(11), 0, dp(4)); list.addView(title);
        if (shortcut != null) {
            TextView jump = action(shortcut, () -> scroll.smoothScrollTo(0, title.getTop()));
            jump.setGravity(Gravity.CENTER); jump.setMinHeight(dp(29)); jump.setTextSize(13); jump.setContentDescription("Jump to " + description); index.addView(jump);
        }
    }
    private void row(ResolveInfo app, boolean favorite, boolean toggle) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView open = action(label(app), () -> {
            try { startActivity(Intent.makeMainActivity(component(app))); }
            catch (ActivityNotFoundException | SecurityException e) { Toast.makeText(this, "App is no longer available", Toast.LENGTH_SHORT).show(); refresh(); }
        });
        android.graphics.drawable.Drawable icon = app.loadIcon(getPackageManager()); icon.setBounds(0, 0, dp(24), dp(24));
        open.setCompoundDrawablesRelative(icon, null, null, null); open.setCompoundDrawablePadding(dp(10)); open.setTextColor(ink);
        open.setSingleLine(); open.setEllipsize(android.text.TextUtils.TruncateAt.END);
        line.addView(open, new LinearLayout.LayoutParams(0, dp(46), 1));
        if (toggle) {
            TextView star = action(favorite ? "★" : "☆", () -> {
                Set<String> favorites = saved("favorites"); String key = component(app).flattenToString();
                if (!favorites.remove(key)) favorites.add(key); store("favorites", favorites); refresh();
            });
            star.setGravity(Gravity.CENTER); star.setContentDescription((favorite ? "Unfavorite " : "Favorite ") + label(app)); line.addView(star, new LinearLayout.LayoutParams(dp(44), dp(46)));
        }
        list.addView(line);
        View divider = new View(this); divider.setBackgroundColor(0xffd9e0d2); list.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
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
    }
}
