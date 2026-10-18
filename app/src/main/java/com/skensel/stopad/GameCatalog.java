package com.skensel.stopad;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.*;

/** Revalidated by both UI and service. Only launchable GAME apps, excluding web handlers. */
final class GameCatalog {
    static List<ApplicationInfo> load(Context context) {
        var pm = context.getPackageManager();
        Set<String> browsers = new HashSet<>();
        for (String scheme : new String[]{"http", "https"}) {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            for (ResolveInfo info : pm.queryIntentActivities(web, android.content.pm.PackageManager.MATCH_ALL)) browsers.add(info.activityInfo.packageName);
        }
        Map<String, ApplicationInfo> games = new HashMap<>();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launch, 0)) {
            var app = info.activityInfo.applicationInfo;
            if (app.category == ApplicationInfo.CATEGORY_GAME && !browsers.contains(app.packageName)
                    && !app.packageName.equals(context.getPackageName())) games.put(app.packageName, app);
        }
        var result = new ArrayList<>(games.values());
        result.sort(Comparator.comparing(a -> a.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    static Set<String> selected(Context context) {
        Set<String> result = new HashSet<>(context.getSharedPreferences("games", Context.MODE_PRIVATE)
                .getStringSet("selected", Collections.emptySet()));
        Set<String> installed = new HashSet<>();
        for (ApplicationInfo app : load(context)) installed.add(app.packageName);
        result.retainAll(installed);
        return result;
    }
}
