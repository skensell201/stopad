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
    private Activity currentActivity;
    private boolean largeFonts;
    void run(boolean largeFonts) {
        this.largeFonts=largeFonts;
        String originalScale=null;
        Bundle result = new Bundle();
        android.content.SharedPreferences prefs = runner.getTargetContext().getSharedPreferences("games", 0);
        java.util.Set<String> original = new java.util.HashSet<>(prefs.getStringSet("selected", java.util.Collections.emptySet()));
        boolean ok = false;
        try {
            if (AdVpnService.active()) throw new AssertionError("Run UI test with VPN off");
            if(largeFonts) {
                originalScale=shell("settings get system font_scale").trim();
                shell("settings put system font_scale 1.6");
                long configDeadline=SystemClock.elapsedRealtime()+5000;
                while(runner.getTargetContext().getResources().getConfiguration().fontScale<1.5f && SystemClock.elapsedRealtime()<configDeadline)Thread.sleep(100);
            }
            prefs.edit().putStringSet("selected", java.util.Collections.emptySet()).commit();
            Activity activity = runner.startActivitySync(new Intent(runner.getTargetContext(), MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            currentActivity=activity;
            if(largeFonts && activity.getResources().getConfiguration().fontScale<1.5f)throw new AssertionError("Large font configuration not applied");
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
            result.putString("stream", "PASS: home, games, settings navigation and empty-selection action; VPN remains off; screenshots saved"+(largeFonts?"; 160% text without clipping":"")+"\n");
            ok = true;
        } catch (Throwable error) { result.putString("stream", "FAIL: " + error + "\n"); }
        finally {
            prefs.edit().putStringSet("selected", original).commit();
            if(originalScale!=null)try {
                if(originalScale.equals("null"))shell("settings delete system font_scale");
                else if(originalScale.matches("[0-9]+(\\.[0-9]+)?"))shell("settings put system font_scale "+originalScale);
            } catch(Exception error) { result.putString("stream","FAIL: restoring font scale: "+error);ok=false; }
        }
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
    private void assertTextFits(View view) {
        if(!view.isShown())return;
        if(view instanceof android.widget.TextView text && text.getLayout()!=null && text.getEllipsize()==null) {
            android.text.Layout layout=text.getLayout();
            if(layout.getHeight()>text.getHeight()-text.getCompoundPaddingTop()-text.getCompoundPaddingBottom()+2)
                throw new AssertionError("Text clipped vertically: "+text.getText());
            String digits=text.getText().toString().replace("\u00a0","").replace("\u202f","").replace(" ","");
            if(digits.matches("[0-9]{5,}") && layout.getLineCount()!=1)
                throw new AssertionError("Domain count must remain readable as one number: "+text.getText());
            for(int line=0;line<layout.getLineCount();line++)if(layout.getLineWidth(line)>text.getWidth()-text.getCompoundPaddingLeft()-text.getCompoundPaddingRight()+2)
                throw new AssertionError("Text clipped horizontally: "+text.getText());
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)assertTextFits(group.getChildAt(i));
    }
    private String shell(String command)throws Exception {
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(runner.getUiAutomation().executeShellCommand(command))) {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[1024];
            for(int count;(count=input.read(buffer))!=-1;)bytes.write(buffer,0,count);
            return bytes.toString("UTF-8");
        }
    }
    private void capture(String name) throws Exception {
        Thread.sleep(700);
        if(largeFonts)runner.runOnMainSync(()->assertTextFits(currentActivity.getWindow().getDecorView()));
        Bitmap bitmap = runner.getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new AssertionError("Screenshot unavailable");
        try (FileOutputStream output = runner.getTargetContext().openFileOutput("ui-" + name + ".png", 0)) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, output); }
        bitmap.recycle();
    }
}
