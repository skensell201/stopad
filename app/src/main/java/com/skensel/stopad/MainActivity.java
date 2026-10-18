package com.skensel.stopad;

import android.Manifest;
import android.app.Activity;
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
    private Button toggle;
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
        status.setText(active ? getString(R.string.on) : (AdVpnService.message.isEmpty() ? getString(R.string.off) : AdVpnService.message));
        counters.setText(active ? getString(R.string.stats, AdVpnService.blocked(), AdVpnService.forwarded(), AdVpnService.failed()) : getString(R.string.session_empty));
        toggle.setText(active ? R.string.stop : R.string.start);
        toggle.setEnabled(active || !GameCatalog.selected(this).isEmpty());
        for (CheckBox box : choices) box.setEnabled(!active);
    }
    private TextView text(LinearLayout layout, int resource, int size) {
        TextView view = new TextView(this);
        view.setText(resource); view.setTextSize(size); view.setPadding(0, 12, 0, 12);
        layout.addView(view); return view;
    }
}
