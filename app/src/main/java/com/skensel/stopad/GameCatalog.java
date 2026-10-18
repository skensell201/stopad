package com.skensel.stopad;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import java.util.*;

/** User-added games are revalidated against the same browser exclusion as automatic detection. */
final class GameCatalog {
    static List<ApplicationInfo> candidates(Context context) {
        var pm = context.getPackageManager();
        Set<String> browsers = new HashSet<>();
        for (String scheme : new String[]{"http", "https"}) {
            Intent web = new Intent(Intent.ACTION_VIEW, Uri.parse(scheme + "://example.com"))
                    .addCategory(Intent.CATEGORY_BROWSABLE);
            for (ResolveInfo info : pm.queryIntentActivities(web, PackageManager.MATCH_ALL)) browsers.add(info.activityInfo.packageName);
        }
        Map<String, ApplicationInfo> apps = new HashMap<>();
        Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo info : pm.queryIntentActivities(launch, 0)) {
            var app = info.activityInfo.applicationInfo;
            boolean userApp = (app.flags & ApplicationInfo.FLAG_SYSTEM) == 0;
            if ((userApp || app.category == ApplicationInfo.CATEGORY_GAME) && !browsers.contains(app.packageName)
                    && !app.packageName.equals(context.getPackageName())) apps.put(app.packageName, app);
        }
        var result = new ArrayList<>(apps.values());
        result.sort(Comparator.comparing(a -> a.loadLabel(pm).toString(), String.CASE_INSENSITIVE_ORDER));
        return result;
    }
    static List<ApplicationInfo> load(Context context) {
        Set<String> manual = context.getSharedPreferences("games", Context.MODE_PRIVATE)
                .getStringSet("manual", Collections.emptySet());
        List<ApplicationInfo> games = new ArrayList<>();
        for (ApplicationInfo app : candidates(context)) {
            if (app.category == ApplicationInfo.CATEGORY_GAME || manual.contains(app.packageName)) games.add(app);
        }
        return games;
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
