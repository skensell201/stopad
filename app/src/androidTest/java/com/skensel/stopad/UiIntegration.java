package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;

/** Navigation smoke test and screenshots of the actual running Activity. */
final class UiIntegration {
    private final Instrumentation runner;
    UiIntegration(Instrumentation runner) { this.runner = runner; }
    void run() {
        Bundle result = new Bundle();
        android.content.SharedPreferences prefs = runner.getTargetContext().getSharedPreferences("games", 0);
        java.util.Set<String> original = new java.util.HashSet<>(prefs.getStringSet("selected", java.util.Collections.emptySet()));
        boolean ok = false;
        try {
            if (AdVpnService.active()) throw new AssertionError("Run UI test with VPN off");
            prefs.edit().putStringSet("selected", java.util.Collections.emptySet()).commit();
            Activity activity = runner.startActivitySync(new Intent(runner.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runner.runOnMainSync(() -> { activity.setTurnScreenOn(true); activity.setShowWhenLocked(true);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (BlocklistRepository.snapshot == null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            if (BlocklistRepository.snapshot == null) throw new AssertionError("Bundled list unavailable");
            Thread.sleep(1200);
            capture("home");
            {
                runner.runOnMainSync(() -> {
                    View choose = text(activity.getWindow().getDecorView(), activity.getString(R.string.ui_choose_games));
                    if (choose == null || !choose.performClick()) throw new AssertionError("Choose games action unavailable");
                });
                runner.waitForIdleSync();
                runner.runOnMainSync(() -> {
                    if (text(activity.getWindow().getDecorView(), activity.getString(R.string.add_game)) == null)
                        throw new AssertionError("Empty selection must navigate to game selection");
                });
            }
            capture("games");
            click(activity, "Настройки"); capture("settings");
            click(activity, "Главная"); capture("home");
            if (AdVpnService.active()) throw new AssertionError("Navigation must not start VPN");
            result.putString("stream", "PASS: home, games, settings navigation and empty-selection action; VPN remains off; screenshots saved\n");
            ok = true;
        } catch (Throwable error) { result.putString("stream", "FAIL: " + error + "\n"); }
        finally { prefs.edit().putStringSet("selected", original).commit(); }
        runner.finish(ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private void click(Activity activity, String description) {
        runner.runOnMainSync(() -> {
            View view = find(activity.getWindow().getDecorView(), description);
            if (view == null || !view.performClick()) throw new AssertionError("Missing navigation: " + description);
        }); runner.waitForIdleSync();
    }
    private View text(View view, String value) {
        if (view instanceof android.widget.Button button && value.contentEquals(button.getText())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View found = text(group.getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()) && view.isClickable()) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), description); if (found != null) return found; }
        return null;
    }
    private void capture(String name) throws Exception {
        Thread.sleep(700);
        Bitmap bitmap = runner.getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream output = runner.getTargetContext().openFileOutput("ui-" + name + ".png", 0)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
    }
}
