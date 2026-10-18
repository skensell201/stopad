package com.skensel.stopad;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.net.VpnService;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, counters, listStatus, ruleCount, selectionHint, gameSummary, protectionDetail;
    private Button toggle, manualButton, exceptionsButton, updateListButton;
    private final List<CheckBox> choices = new ArrayList<>();
    private int tab;
    private boolean selectedOnly;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) { tab = state.getInt("tab", 0); selectedOnly = state.getBoolean("selectedOnly", false); }
        BlocklistRepository.initialize(this);
    }
    @Override public void onResume() { super.onResume(); render(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("tab", tab); state.putBoolean("selectedOnly", selectedOnly); super.onSaveInstanceState(state);
    }
    private int dp(float size) { return MatteUi.dp(this, size); }
    private TextView label(CharSequence value, float size, int color, boolean bold) { return MatteUi.text(this, value, size, color, bold); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private void gap(LinearLayout parent, int height) { parent.addView(new Space(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void full(LinearLayout parent, View view) { parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); }
    private void navigate(int destination) { tab = destination; render(); }
    private void render() {
        choices.clear();
        status = counters = listStatus = ruleCount = selectionHint = gameSummary = protectionDetail = null;
        toggle = manualButton = exceptionsButton = updateListButton = null;
        LinearLayout shell = column(); shell.setBackgroundColor(MatteUi.BG);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(); content.setPadding(dp(22), dp(18), dp(22), dp(26));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        full(shell, navigation());
        shell.setOnApplyWindowInsetsListener((view, insets) -> {
            shell.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(shell);
        if (tab == 0) home(content); else if (tab == 1) games(content); else settings(content);
        update();
    }
    private void heading(LinearLayout parent, String title, String subtitle, String icon, Runnable action) {
        LinearLayout row = row();
        LinearLayout titles = column();
        full(titles, label(title, 27, MatteUi.TEXT, true)); gap(titles, 5);
        TextView sub = label(subtitle, 12, MatteUi.MUTED, false); full(titles, sub);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        FrameLayout button = new FrameLayout(this); MatteUi.card(button, 14);
        MatteUi.Icon glyph = new MatteUi.Icon(this, icon, MatteUi.TEXT);
        FrameLayout.LayoutParams glyphParams = new FrameLayout.LayoutParams(dp(23), dp(23), Gravity.CENTER);
        button.addView(glyph, glyphParams); button.setOnClickListener(v -> action.run());
        button.setContentDescription(icon.equals("plus") ? getString(R.string.add_game) : getString(R.string.ui_settings));
        button.setFocusable(true); row.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        full(parent, row); gap(parent, 22);
    }
    private void home(LinearLayout parent) {
        heading(parent, getString(R.string.app_name), getString(R.string.ui_header_sub), "settings", () -> navigate(2));
        FrameLayout hero = new FrameLayout(this); MatteUi.card(hero, 23); hero.setClipToOutline(true);
        boolean largeText = getResources().getConfiguration().fontScale > 1.25f;
        MatteUi.Hero artwork = new MatteUi.Hero(this); artwork.setAlpha(largeText ? .22f : 1f);
        hero.addView(artwork, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout copy = column(); copy.setPadding(dp(21), dp(23), 0, dp(20));
        TextView eyebrow = label(getString(R.string.ui_hero_badge), 10, 0xffc3b9df, true); eyebrow.setLetterSpacing(.12f); full(copy, eyebrow);
        gap(copy, 21);
        full(copy, label(getString(R.string.ui_hero_title), 31, MatteUi.TEXT, true)); gap(copy, 13);
        TextView description = label(getString(R.string.ui_hero_sub), 12, 0xffbdc2d1, false); description.setLineSpacing(dp(3), 1); full(copy, description);
        hero.addView(copy, new FrameLayout.LayoutParams(largeText ? -1 : (int) (getResources().getDisplayMetrics().widthPixels * .52f), -1));
        parent.addView(hero, new LinearLayout.LayoutParams(-1, dp(224 * Math.max(1f, getResources().getConfiguration().fontScale)))); gap(parent, 17);

        LinearLayout control = column(); control.setPadding(dp(18), dp(17), dp(18), dp(17)); MatteUi.card(control, 20);
        LinearLayout stateRow = row();
        stateRow.addView(new MatteUi.Icon(this, "shield", MatteUi.TEAL), new LinearLayout.LayoutParams(dp(24), dp(24)));
        TextView title = label(getString(R.string.ui_protection), 16, MatteUi.TEXT, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1); titleParams.leftMargin = dp(10); stateRow.addView(title, titleParams);
        status = label("", 12, MatteUi.MUTED, false); status.setMaxWidth(dp(150)); status.setGravity(Gravity.END); stateRow.addView(status);
        full(control, stateRow); gap(control, 12);
        protectionDetail = label("", 12, MatteUi.MUTED, false); protectionDetail.setLineSpacing(dp(3), 1); full(control, protectionDetail);
        gap(control, 12);
        toggle = MatteUi.button(this, getString(R.string.start), true); toggle.setOnClickListener(v -> toggleProtection()); full(control, toggle);
        gap(control, 11); selectionHint = label("", 11, MatteUi.MUTED, false); selectionHint.setGravity(Gravity.CENTER); full(control, selectionHint);
        full(parent, control); gap(parent, 17);
        LinearLayout stats = row();
        counters = statistic(stats, "—", getString(R.string.ui_requests), "shield", () -> showStats());
        ruleCount = statistic(stats, "—", getString(R.string.ui_domains), "list", () -> navigate(2));
        full(parent, stats); gap(parent, 25);
        LinearLayout section = row(); section.addView(label(getString(R.string.games_title), 20, MatteUi.TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView all = label(getString(R.string.ui_all_games), 13, MatteUi.PURPLE, true); all.setPadding(dp(14), dp(12), 0, dp(12)); all.setOnClickListener(v -> navigate(1)); all.setContentDescription(getString(R.string.ui_all_games)); section.addView(all); full(parent, section); gap(parent, 10);
        List<ApplicationInfo> selected = new ArrayList<>(); Set<String> selectedNames = GameCatalog.selected(this);
        for (ApplicationInfo app : GameCatalog.load(this)) if (selectedNames.contains(app.packageName)) selected.add(app);
        if (selected.isEmpty()) emptyGames(parent, false);
        else gameGrid(parent, selected.subList(0, Math.min(2, selected.size())), selectedNames);
        gap(parent, 20);
        TextView privacy = label(getString(R.string.ui_dns_note), 11, MatteUi.MUTED, false); privacy.setGravity(Gravity.CENTER);
        privacy.setPadding(0, dp(12), 0, dp(12)); privacy.setOnClickListener(v -> info(R.string.ui_privacy_title, R.string.privacy)); full(parent, privacy);
    }
    private TextView statistic(LinearLayout row, String value, String caption, String icon, Runnable action) {
        LinearLayout card = column(); card.setPadding(dp(16), dp(16), dp(16), dp(16)); MatteUi.card(card, 19);
        TextView number = label(value, 27, MatteUi.TEXT, true); full(card, number); gap(card, 9);
        full(card, label(caption, 11, MatteUi.MUTED, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (row.getChildCount() == 0) params.rightMargin = dp(7); else params.leftMargin = dp(7);
        row.addView(card, params); card.setOnClickListener(v -> action.run()); card.setFocusable(true); return number;
    }
    private void games(LinearLayout parent) {
        heading(parent, getString(R.string.games_title), getString(R.string.ui_games_sub), "plus", () -> showGamePicker());
        LinearLayout filters = row();
        Button all = MatteUi.button(this, getString(R.string.ui_filter_all), !selectedOnly);
        Button chosen = MatteUi.button(this, getString(R.string.ui_filter_selected), selectedOnly);
        all.setOnClickListener(v -> { selectedOnly = false; render(); }); chosen.setOnClickListener(v -> { selectedOnly = true; render(); });
        filters.addView(all, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(0, -2, 1); filterParams.leftMargin = dp(12); filters.addView(chosen, filterParams);
        full(parent, filters); gap(parent, 21);
        gameSummary = label("", 12, MatteUi.MUTED, false); full(parent, gameSummary); gap(parent, 16);
        Set<String> selected = GameCatalog.selected(this);
        var prefs = getSharedPreferences("games", MODE_PRIVATE); prefs.edit().putStringSet("selected", new HashSet<>(selected)).apply();
        List<ApplicationInfo> visible = new ArrayList<>();
        for (ApplicationInfo app : GameCatalog.load(this)) if (!selectedOnly || selected.contains(app.packageName)) visible.add(app);
        if (visible.isEmpty()) emptyGames(parent, true); else gameGrid(parent, visible, selected);
        gap(parent, 20);
        manualButton = MatteUi.button(this, getString(R.string.add_game), true); manualButton.setOnClickListener(v -> showGamePicker()); full(parent, manualButton);
        gap(parent, 17); full(parent, label(getString(R.string.games_hint), 12, MatteUi.MUTED, false));
    }
    private void emptyGames(LinearLayout parent, boolean expanded) {
        LinearLayout empty = column(); MatteUi.card(empty, 20); empty.setPadding(dp(20), dp(22), dp(20), dp(22)); empty.setGravity(Gravity.CENTER);
        MatteUi.Icon controller = new MatteUi.Icon(this, "games", MatteUi.PURPLE);
        empty.addView(controller, new LinearLayout.LayoutParams(dp(42), dp(42))); gap(empty, 13);
        TextView title = label(getString(selectedOnly && expanded ? R.string.ui_no_selected : R.string.ui_empty_title), 16, MatteUi.TEXT, true); title.setGravity(Gravity.CENTER); full(empty, title); gap(empty, 8);
        TextView hint = label(getString(R.string.ui_empty_hint), 12, MatteUi.MUTED, false); hint.setGravity(Gravity.CENTER); hint.setLineSpacing(dp(3), 1); full(empty, hint);
        if (!expanded) { empty.setOnClickListener(v -> navigate(1)); empty.setContentDescription(getString(R.string.ui_all_games)); empty.setFocusable(true); }
        full(parent, empty);
    }
    private void gameGrid(LinearLayout parent, List<ApplicationInfo> games, Set<String> selected) {
        boolean single = getResources().getConfiguration().fontScale > 1.25f || getResources().getConfiguration().screenWidthDp < 350;
        for (int i = 0; i < games.size(); i += single ? 1 : 2) {
            LinearLayout pair = row();
            for (int j = 0; j < (single ? 1 : 2); j++) {
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
                if (!single) { if (j == 0) params.rightMargin = dp(7); else params.leftMargin = dp(7); }
                if (i + j < games.size()) pair.addView(gameCard(games.get(i + j), selected), params); else pair.addView(new Space(this), params);
            }
            full(parent, pair); gap(parent, 14);
        }
    }
    private View gameCard(ApplicationInfo app, Set<String> selected) {
        LinearLayout card = column(); card.setPadding(dp(15), dp(15), dp(15), dp(16)); MatteUi.card(card, 20);
        LinearLayout top = row(); ImageView icon = new ImageView(this); icon.setImageDrawable(app.loadIcon(getPackageManager())); icon.setContentDescription(null);
        top.addView(icon, new LinearLayout.LayoutParams(dp(48), dp(48)));
        top.addView(new Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        CheckBox checkbox = new CheckBox(this); checkbox.setButtonTintList(ColorStateList.valueOf(MatteUi.TEAL));
        checkbox.setContentDescription(getString(R.string.game_label, app.loadLabel(getPackageManager()), app.packageName));
        checkbox.setChecked(selected.contains(app.packageName));
        checkbox.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(app.packageName); else selected.remove(app.packageName);
            getSharedPreferences("games", MODE_PRIVATE).edit().putStringSet("selected", new HashSet<>(selected)).apply();
            if (tab == 1 && selectedOnly && !checked) render(); else update();
        });
        choices.add(checkbox); top.addView(checkbox, new LinearLayout.LayoutParams(dp(42), dp(48)));
        full(card, top); gap(card, 18);
        TextView name = label(app.loadLabel(getPackageManager()), 15, MatteUi.TEXT, true); name.setMaxLines(2); name.setEllipsize(TextUtils.TruncateAt.END); full(card, name); gap(card, 6);
        TextView packageName = label(app.packageName, 10, MatteUi.MUTED, false); packageName.setMaxLines(1); packageName.setEllipsize(TextUtils.TruncateAt.MIDDLE); full(card, packageName);
        card.setOnClickListener(v -> { if (checkbox.isEnabled()) checkbox.performClick(); }); return card;
    }
    private void settings(LinearLayout parent) {
        heading(parent, getString(R.string.ui_settings), getString(R.string.ui_settings_sub), "shield", () -> navigate(0));
        LinearLayout list = column(); MatteUi.card(list, 22); list.setPadding(dp(19), dp(20), dp(19), dp(19));
        LinearLayout title = row(); title.addView(new MatteUi.Icon(this, "list", MatteUi.PURPLE), new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView label = label(getString(R.string.list_title), 18, MatteUi.TEXT, true); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2); params.leftMargin = dp(10); title.addView(label, params); full(list, title); gap(list, 17);
        listStatus = label("", 13, MatteUi.MUTED, false); listStatus.setLineSpacing(dp(4), 1); full(list, listStatus); gap(list, 19);
        updateListButton = MatteUi.button(this, getString(R.string.update_list), true); updateListButton.setOnClickListener(v -> { BlocklistRepository.update(this); update(); }); full(list, updateListButton);
        gap(list, 10); Button credits = MatteUi.button(this, getString(R.string.list_credits), false); credits.setOnClickListener(v -> showListCredits()); full(list, credits); full(parent, list); gap(parent, 20);
        int count = getSharedPreferences("games", MODE_PRIVATE).getStringSet("domain_exceptions", Collections.emptySet()).size();
        exceptionsButton = MatteUi.button(this, getString(R.string.exceptions_count, count), false); exceptionsButton.setOnClickListener(v -> showExceptions()); full(parent, exceptionsButton); gap(parent, 10);
        full(parent, label(getString(R.string.ui_exception_note), 12, MatteUi.MUTED, false)); gap(parent, 23);
        settingsRow(parent, "shield", R.string.ui_limitations_title, R.string.ui_limitations_sub, () -> info(R.string.ui_limitations_title, R.string.limitations));
        settingsRow(parent, "info", R.string.ui_privacy_title, R.string.ui_privacy_sub, () -> info(R.string.ui_privacy_title, R.string.privacy));
        settingsRow(parent, "games", R.string.ui_session_title, R.string.ui_session_sub, () -> showStats());
        gap(parent, 21);
        TextView version = label(getString(R.string.ui_version), 11, MatteUi.MUTED, false); version.setGravity(Gravity.CENTER); full(parent, version);
    }
    private void settingsRow(LinearLayout parent, String glyph, int title, int subtitle, Runnable action) {
        LinearLayout item = row(); item.setPadding(dp(17), dp(17), dp(17), dp(17)); MatteUi.card(item, 17);
        item.addView(new MatteUi.Icon(this, glyph, MatteUi.PURPLE), new LinearLayout.LayoutParams(dp(24), dp(24)));
        LinearLayout labels = column(); full(labels, label(getString(title), 14, MatteUi.TEXT, true)); gap(labels, 5); full(labels, label(getString(subtitle), 11, MatteUi.MUTED, false));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.leftMargin = dp(13); item.addView(labels, params);
        item.addView(new MatteUi.Icon(this, "arrow", MatteUi.MUTED), new LinearLayout.LayoutParams(dp(21), dp(21)));
        item.setOnClickListener(v -> action.run()); item.setFocusable(true); full(parent, item); gap(parent, 12);
    }
    private View navigation() {
        LinearLayout holder = column(); holder.setPadding(dp(15), dp(7), dp(15), dp(10));
        LinearLayout bar = row(); MatteUi.card(bar, 24); bar.setPadding(dp(7), dp(9), dp(7), dp(9));
        String[] icons = {"shield", "games", "settings"}; int[] titles = {R.string.ui_home, R.string.games_title, R.string.ui_settings};
        for (int i = 0; i < 3; i++) {
            final int destination = i; boolean active = tab == i;
            LinearLayout item = column(); item.setGravity(Gravity.CENTER); item.setPadding(0, dp(5), 0, dp(4));
            if (active) item.setBackground(MatteUi.surface(this, true, 16));
            item.addView(new MatteUi.Icon(this, icons[i], active ? MatteUi.TEXT : MatteUi.MUTED), new LinearLayout.LayoutParams(dp(23), dp(23))); gap(item, 5);
            full(item, label(getString(titles[i]), 10, active ? MatteUi.TEXT : MatteUi.MUTED, active));
            ((TextView) item.getChildAt(2)).setGravity(Gravity.CENTER);
            item.setContentDescription(getString(titles[i])); item.setSelected(active); item.setFocusable(true); item.setOnClickListener(v -> navigate(destination));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.leftMargin = dp(3); params.rightMargin = dp(3); bar.addView(item, params);
        }
        full(holder, bar); return holder;
    }
    private void toggleProtection() {
        if (AdVpnService.active()) startService(new Intent(this, AdVpnService.class).setAction(AdVpnService.STOP));
        else if (GameCatalog.selected(this).isEmpty()) navigate(1);
        else {
            ProtectionReadiness.State readiness = ProtectionReadiness.check(this, true, BlocklistRepository.snapshot != null);
            if (readiness == ProtectionReadiness.State.PRIVATE_DNS) {
                new AlertDialog.Builder(this).setTitle(R.string.ui_dns_setup).setMessage(R.string.ui_dns_setup_help)
                        .setPositiveButton(R.string.ui_open_settings, (dialog, which) -> openNetworkSettings())
                        .setNegativeButton(android.R.string.cancel, null).show(); return;
            }
            if (readiness != ProtectionReadiness.State.READY) { update(); return; }
            Intent consent = VpnService.prepare(this);
            if (consent == null) startProtection(); else startActivityForResult(consent, 10);
        }
        update();
    }
    private void openNetworkSettings() {
        try { startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS)); }
        catch (ActivityNotFoundException e) { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
    }
    private String number(long value) { return java.text.NumberFormat.getIntegerInstance(new Locale("ru")).format(value); }
    private void showStats() {
        new AlertDialog.Builder(this).setTitle(R.string.ui_session_title)
                .setMessage(AdVpnService.active() ? getString(R.string.stats, AdVpnService.blocked(), AdVpnService.forwarded(), AdVpnService.failed()) : getString(R.string.session_empty))
                .setPositiveButton(android.R.string.ok, null).show();
    }
    private void info(int title, int text) { new AlertDialog.Builder(this).setTitle(title).setMessage(text).setPositiveButton(android.R.string.ok, null).show(); }
    private void update() {
        boolean active = AdVpnService.active(); Set<String> selected = GameCatalog.selected(this); BlocklistSnapshot snapshot = BlocklistRepository.snapshot;
        ProtectionReadiness.State readiness = active ? ProtectionReadiness.State.READY
                : ProtectionReadiness.check(this, !selected.isEmpty(), snapshot != null);
        if (status != null) {
            status.setText(active ? getString(AdVpnService.online() ? R.string.ui_on_short : R.string.ui_waiting_short)
                    : getString(R.string.ui_off_short));
            status.setTextColor(active && AdVpnService.online() ? MatteUi.TEAL : MatteUi.MUTED);
        }
        if (protectionDetail != null) {
            String detail = active ? (AdVpnService.online() ? "" : getString(R.string.waiting_network))
                    : readiness == ProtectionReadiness.State.PRIVATE_DNS ? getString(R.string.ui_dns_required)
                    : readiness == ProtectionReadiness.State.NO_NETWORK ? getString(R.string.ui_network_required)
                    : !AdVpnService.message.isEmpty() ? getString(R.string.ui_last_error, AdVpnService.message) : "";
            protectionDetail.setText(detail); protectionDetail.setVisibility(detail.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (counters != null) counters.setText(active ? number(AdVpnService.blocked()) : "—");
        if (ruleCount != null) ruleCount.setText(snapshot == null ? "—" : number(snapshot.domains.size()));
        if (selectionHint != null) selectionHint.setText(getString(R.string.ui_selection_hint, selected.size()));
        if (gameSummary != null) gameSummary.setText(getString(R.string.ui_game_summary, selected.size()));
        if (toggle != null) {
            toggle.setText(active ? R.string.stop : readiness == ProtectionReadiness.State.NO_GAMES ? R.string.ui_choose_games
                    : readiness == ProtectionReadiness.State.PRIVATE_DNS ? R.string.ui_dns_setup
                    : readiness == ProtectionReadiness.State.NO_NETWORK ? R.string.ui_no_network
                    : readiness == ProtectionReadiness.State.LIST_LOADING ? R.string.list_loading : R.string.start);
            toggle.setEnabled(active || (readiness != ProtectionReadiness.State.LIST_LOADING && readiness != ProtectionReadiness.State.NO_NETWORK)); toggle.setAlpha(toggle.isEnabled() ? 1 : .5f);
        }
        if (listStatus != null) {
            String detail = snapshot == null ? getString(R.string.list_loading) : getString(R.string.ui_list_detail,
                    number(snapshot.domains.size()), snapshot.sha256.substring(0, 12), java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, new Locale("ru")).format(new Date(snapshot.fetchedAt)));
            if (BlocklistRepository.busy && snapshot != null) detail += "\n\n" + getString(R.string.list_updating);
            else if (!BlocklistRepository.message.isEmpty()) detail += "\n\n" + BlocklistRepository.message;
            AdVpnService.Session session = AdVpnService.current;
            if (snapshot != null && session != null && !session.listVersion.equals(snapshot.sha256)) detail += "\n\n" + getString(R.string.list_restart);
            listStatus.setText(detail);
        }
        if (updateListButton != null) { updateListButton.setEnabled(!BlocklistRepository.busy); updateListButton.setAlpha(BlocklistRepository.busy ? .5f : 1); }
        if (manualButton != null) { manualButton.setEnabled(!active); manualButton.setAlpha(active ? .5f : 1); }
        if (exceptionsButton != null) { exceptionsButton.setEnabled(!active); exceptionsButton.setAlpha(active ? .5f : 1); }
        for (CheckBox box : choices) box.setEnabled(!active);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == 10 && result == RESULT_OK) startProtection();
        else if (request == 10) Toast.makeText(this, R.string.vpn_denied, Toast.LENGTH_SHORT).show();
    }
    private void startProtection() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 11);
        try { startForegroundService(new Intent(this, AdVpnService.class).setAction(AdVpnService.START)); }
        catch (RuntimeException e) { AdVpnService.message = getString(R.string.start_failed); }
    }
    private void showGamePicker() {
        if (AdVpnService.active()) return;
        Set<String> known = new HashSet<>();
        for (ApplicationInfo app : GameCatalog.load(this)) known.add(app.packageName);
        List<ApplicationInfo> available = new ArrayList<>();
        for (ApplicationInfo app : GameCatalog.candidates(this)) if (!known.contains(app.packageName)) available.add(app);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        EditText search = new EditText(this); search.setHint(R.string.search_games);
        content.addView(search);
        ListView list = new ListView(this);
        content.addView(list, new LinearLayout.LayoutParams(-1, (int) (300 * getResources().getDisplayMetrics().density)));
        TextView empty = new TextView(this); empty.setText(R.string.no_candidates); content.addView(empty); list.setEmptyView(empty);
        List<ApplicationInfo> filtered = new ArrayList<>(available);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        Runnable filter = () -> {
            filtered.clear(); adapter.clear();
            String term = search.getText().toString().toLowerCase(Locale.ROOT);
            for (ApplicationInfo app : available) {
                String title = getString(R.string.game_label, app.loadLabel(getPackageManager()), app.packageName);
                if (title.toLowerCase(Locale.ROOT).contains(term)) { filtered.add(app); adapter.add(title); }
            }
            adapter.notifyDataSetChanged();
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filter.run(); }
            @Override public void afterTextChanged(Editable e) { }
        });
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.add_game)
                .setView(content).setNegativeButton(android.R.string.cancel, null).create();
        list.setOnItemClickListener((parent, view, position, id) -> {
            if (AdVpnService.active()) return;
            String name = filtered.get(position).packageName;
            var prefs = getSharedPreferences("games", MODE_PRIVATE);
            Set<String> manual = new HashSet<>(prefs.getStringSet("manual", Collections.emptySet()));
            Set<String> selected = new HashSet<>(prefs.getStringSet("selected", Collections.emptySet()));
            manual.add(name); selected.add(name);
            prefs.edit().putStringSet("manual", manual).putStringSet("selected", selected).apply();
            dialog.dismiss(); render();
        });
        filter.run(); dialog.show();
    }
    private void showExceptions() {
        if (AdVpnService.active()) return;
        var prefs = getSharedPreferences("games", MODE_PRIVATE);
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setMinLines(4); input.setMaxLines(10);
        input.setText(String.join("\n", new TreeSet<>(prefs.getStringSet("domain_exceptions", Collections.emptySet()))));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.exceptions_title)
                .setMessage(R.string.exceptions_hint).setView(input)
                .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (AdVpnService.active()) return;
            try {
                Set<String> domains = DomainPolicy.parseExceptions(input.getText().toString());
                prefs.edit().putStringSet("domain_exceptions", domains).apply();
                dialog.dismiss(); render();
            } catch (IllegalArgumentException invalid) { input.setError(getString(R.string.invalid_domains)); }
        }));
        dialog.show();
    }
    private void showListCredits() {
        StringBuilder text = new StringBuilder();
        try {
            for (String asset : new String[]{"blocklist-NOTICE.txt", "blocklist-LICENSE.txt"}) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(getAssets().open(asset), java.nio.charset.StandardCharsets.UTF_8))) {
                    for (String line; (line = reader.readLine()) != null;) text.append(line).append('\n');
                }
                text.append('\n');
            }
            new AlertDialog.Builder(this).setTitle(R.string.list_credits).setMessage(text.toString())
                    .setPositiveButton(android.R.string.ok, null).show();
        } catch (java.io.IOException e) { Toast.makeText(this, R.string.credits_failed, Toast.LENGTH_SHORT).show(); }
    }
}
