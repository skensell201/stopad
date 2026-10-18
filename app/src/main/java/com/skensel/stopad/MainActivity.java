package com.skensel.stopad;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.InputType;
import android.content.*;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.VpnService;
import android.os.*;
import android.view.View;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status, counters;
    private Button toggle, manualButton, exceptionsButton;
    private final List<CheckBox> choices = new ArrayList<>();
    private final Runnable refresh = new Runnable() {
        @Override public void run() { update(); handler.postDelayed(this, 1000); }
    };
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() { super.onResume(); render(); handler.removeCallbacks(refresh); handler.post(refresh); }
    @Override public void onPause() { handler.removeCallbacks(refresh); super.onPause(); }

    private void render() {
        choices.clear();
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad);
        scroll.addView(layout);
        // Respect Android 15+ edge-to-edge system bars.
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            layout.setPadding(pad, pad + insets.getSystemWindowInsetTop(), pad, pad + insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text(layout, R.string.app_name, 32).setTextColor(0xffa78bfa);
        text(layout, R.string.tagline, 22);
        status = text(layout, R.string.off, 18);
        status.setTextColor(0xff5eead4);
        counters = text(layout, R.string.session_empty, 14);
        toggle = new Button(this);
        toggle.setOnClickListener(v -> {
            if (AdVpnService.active()) startService(new Intent(this, AdVpnService.class).setAction(AdVpnService.STOP));
            else {
                if (GameCatalog.selected(this).isEmpty()) {
                    Toast.makeText(this, R.string.choose_first, Toast.LENGTH_SHORT).show(); return;
                }
                Intent consent = VpnService.prepare(this);
                if (consent == null) startProtection();
                else startActivityForResult(consent, 10);
            }
            update();
        });
        layout.addView(toggle);
        text(layout, R.string.games_title, 20);
        text(layout, R.string.games_hint, 14);
        var prefs = getSharedPreferences("games", MODE_PRIVATE);
        Set<String> selected = GameCatalog.selected(this);
        prefs.edit().putStringSet("selected", new HashSet<>(selected)).apply();
        List<ApplicationInfo> games = GameCatalog.load(this);
        if (games.isEmpty()) text(layout, R.string.no_games, 16);
        for (ApplicationInfo game : games) {
            CheckBox box = new CheckBox(this);
            box.setText(getString(R.string.game_label, game.loadLabel(getPackageManager()), game.packageName));
            box.setChecked(selected.contains(game.packageName));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(game.packageName); else selected.remove(game.packageName);
                prefs.edit().putStringSet("selected", new HashSet<>(selected)).apply();
                update();
            });
            choices.add(box); layout.addView(box);
        }
        manualButton = new Button(this);
        manualButton.setText(R.string.add_game);
        manualButton.setOnClickListener(v -> showGamePicker());
        layout.addView(manualButton);
        exceptionsButton = new Button(this);
        exceptionsButton.setText(getString(R.string.exceptions_count, prefs.getStringSet("domain_exceptions", Collections.emptySet()).size()));
        exceptionsButton.setOnClickListener(v -> showExceptions());
        layout.addView(exceptionsButton);
        text(layout, R.string.limitations, 14);
        text(layout, R.string.privacy, 14);
        update();
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
    private void update() {
        if (status == null) return;
        boolean active = AdVpnService.active();
        status.setText(active ? getString(AdVpnService.online() ? R.string.on : R.string.waiting_network) : (AdVpnService.message.isEmpty() ? getString(R.string.off) : AdVpnService.message));
        counters.setText(active ? getString(R.string.stats, AdVpnService.blocked(), AdVpnService.forwarded(), AdVpnService.failed()) : getString(R.string.session_empty));
        toggle.setText(active ? R.string.stop : R.string.start);
        toggle.setEnabled(active || !GameCatalog.selected(this).isEmpty());
        if (manualButton != null) manualButton.setEnabled(!active);
        if (exceptionsButton != null) exceptionsButton.setEnabled(!active);
        for (CheckBox box : choices) box.setEnabled(!active);
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
    private TextView text(LinearLayout layout, int resource, int size) {
        TextView view = new TextView(this);
        view.setText(resource); view.setTextSize(size); view.setPadding(0, 12, 0, 12);
        layout.addView(view); return view;
    }
}
