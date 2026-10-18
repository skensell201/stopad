package com.skensel.stopad;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.widget.*;
import java.util.*;

/** Project bootstrap: persistent game selection; VPN engine is not connected yet. */
public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad * 2, pad, pad);
        scroll.addView(layout);
        setContentView(scroll);
        text(layout, "StopAd", 32);
        text(layout, "Реклама не должна прерывать игру", 20);
        text(layout, "Подготовка • блокировка пока не реализована", 16);
        text(layout, "Выберите игры для будущей защиты. Браузеры исключены. Выбор сохраняется на устройстве.", 16);
        var prefs = getSharedPreferences("games", MODE_PRIVATE);
        Set<String> selected = new HashSet<>(prefs.getStringSet("selected", Collections.emptySet()));
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE);
        Set<String> browsers = new HashSet<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(web, 0)) browsers.add(info.activityInfo.packageName);
        Map<String, ApplicationInfo> apps = new HashMap<>();
        for (ResolveInfo info : getPackageManager().queryIntentActivities(launch, 0)) {
            ApplicationInfo a = info.activityInfo.applicationInfo;
            if (!browsers.contains(a.packageName) && a.category == ApplicationInfo.CATEGORY_GAME) apps.put(a.packageName, a);
        }
        // Remove stale selections and applications that are no longer classified as games.
        selected.retainAll(apps.keySet());
        prefs.edit().putStringSet("selected", new HashSet<>(selected)).apply();
        ArrayList<ApplicationInfo> games = new ArrayList<>(apps.values());
        games.sort(Comparator.comparing(a -> a.loadLabel(getPackageManager()).toString(), String.CASE_INSENSITIVE_ORDER));
        if (games.isEmpty()) text(layout, "Игры не найдены. Сейчас показываются только приложения с категорией GAME; ручное добавление появится позже.", 16);
        for (ApplicationInfo game : games) {
            CheckBox box = new CheckBox(this);
            box.setText(getString(R.string.game_label, game.loadLabel(getPackageManager()), game.packageName));
            box.setChecked(selected.contains(game.packageName));
            box.setOnCheckedChangeListener((button, checked) -> {
                if (checked) selected.add(game.packageName); else selected.remove(game.packageName);
                prefs.edit().putStringSet("selected", new HashSet<>(selected)).apply();
            });
            layout.addView(box);
        }
        text(layout, "Следующий этап: локальный VPN и фильтрация рекламных доменов. В этой сборке сетевой трафик не обрабатывается.", 14);
    }
    private void text(LinearLayout layout, String value, int size) {
        TextView view = new TextView(this);
        view.setText(value); view.setTextSize(size); view.setPadding(0, 12, 0, 12);
        layout.addView(view);
    }
}
