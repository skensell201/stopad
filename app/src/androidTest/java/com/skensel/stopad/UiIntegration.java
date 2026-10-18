package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import java.io.*;

/** Navigation smoke test and screenshots of the actual running Activity. */
public final class UiIntegration extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            Activity activity = startActivitySync(new Intent(getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            runOnMainSync(() -> { activity.setTurnScreenOn(true); activity.setShowWhenLocked(true);
                activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); });
            long deadline = SystemClock.elapsedRealtime() + 30000;
            while (BlocklistRepository.snapshot == null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100);
            if (BlocklistRepository.snapshot == null) throw new AssertionError("Bundled list unavailable");
            Thread.sleep(1200);
            capture("home");
            click(activity, "Ваши игры"); capture("games");
            click(activity, "Настройки"); capture("settings");
            click(activity, "Главная"); capture("home");
            if (AdVpnService.active()) throw new AssertionError("Navigation must not start VPN");
            result.putString("stream", "PASS: home, games, settings navigation; VPN remains off; screenshots saved\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable error) { result.putString("stream", "FAIL: " + error + "\n"); finish(Activity.RESULT_CANCELED, result); }
    }
    private void click(Activity activity, String description) {
        runOnMainSync(() -> {
            View view = find(activity.getWindow().getDecorView(), description);
            if (view == null || !view.performClick()) throw new AssertionError("Missing navigation: " + description);
        }); waitForIdleSync();
    }
    private View find(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription()) && view.isClickable()) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), description); if (found != null) return found; }
        return null;
    }
    private void capture(String name) throws Exception {
        Thread.sleep(700);
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream output = getTargetContext().openFileOutput("ui-" + name + ".png", 0)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
    }
}
