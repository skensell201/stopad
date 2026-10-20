package com.skensel.stopad;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.os.Bundle;
import java.util.Locale;

/** Real Android resource resolution, including unsupported-language fallback and format arguments. */
final class LocalizationIntegration {
    static void run(Instrumentation runner) {
        Bundle result = new Bundle(); boolean ok = false;
        try {
            Context en = localized(runner.getTargetContext(), "en");
            Context ru = localized(runner.getTargetContext(), "ru");
            Context fallback = localized(runner.getTargetContext(), "de");
            require(en.getString(R.string.ui_choose_games).equals("Choose games"), "English game-selection action missing");
            require(ru.getString(R.string.ui_choose_games).equals("Выбрать игры"), "Russian game-selection action missing");
            for (var field : R.string.class.getFields()) {
                int id = field.getInt(null);
                String english = en.getString(id);
                require(!english.matches("(?s).*[А-Яа-яЁё].*"), "Russian text in English resource: " + field.getName());
                require(fallback.getString(id).equals(english), "Unsupported-language fallback differs: " + field.getName());
                require(!ru.getString(id).isBlank(), "Empty Russian resource: " + field.getName());
            }
            require(en.getString(R.string.stats, 2, 3, 1).contains("blocked 2"), "English formatted session counters");
            require(ru.getString(R.string.stats, 2, 3, 1).contains("заблокировано 2"), "Russian formatted session counters");
            result.putString("stream", "PASS: English/Russian resources, English fallback, formatted session counters\n"); ok = true;
        } catch (Throwable error) { result.putString("stream", "FAIL: " + error + "\n"); }
        runner.finish(ok ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
    }
    private static Context localized(Context context, String language) {
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(Locale.forLanguageTag(language));
        return context.createConfigurationContext(config);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
