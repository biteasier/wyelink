package app.wyelink;

import android.content.Context;
import android.content.SharedPreferences;

/** Thin wrapper over one SharedPreferences file. */
final class Prefs {

    static final String BROWSER_1 = "b1";
    static final String BROWSER_2 = "b2";
    static final String SMART_AUTH = "smart";
    static final String CUSTOM_TABS = "ct";
    static final String TOAST = "toast";
    /** Recent-links history is opt-in; entries are redacted to scheme://host/path. */
    static final String HISTORY = "history";
    static final String RULES_1 = "rules1";
    static final String RULES_2 = "rules2";
    static final String RECENT = "recent";

    private static final int RECENT_MAX = 15;

    private Prefs() {
    }

    static SharedPreferences get(Context c) {
        SharedPreferences p = c.getSharedPreferences("wyelink", Context.MODE_PRIVATE);
        // 1.0 stored full URLs (tokens included) in history; drop them once.
        if (!p.getBoolean("v2", false)) p.edit().remove(RECENT).putBoolean("v2", true).apply();
        return p;
    }

    static Router.Config config(SharedPreferences p) {
        Router.Config c = new Router.Config();
        c.smartAuth = p.getBoolean(SMART_AUTH, true);
        c.customTabsAsAuth = p.getBoolean(CUSTOM_TABS, false);
        c.rules1 = p.getString(RULES_1, "");
        c.rules2 = p.getString(RULES_2, "");
        return c;
    }

    /** Package name of the browser for a target, or "" for "ask every time". */
    static String browser(SharedPreferences p, int target) {
        return p.getString(target == Router.BROWSER_1 ? BROWSER_1 : BROWSER_2, "");
    }

    static void addRecent(SharedPreferences p, String line) {
        String old = p.getString(RECENT, "");
        StringBuilder sb = new StringBuilder(line);
        int kept = 1;
        for (String l : old.split("\n")) {
            if (l.isEmpty() || kept >= RECENT_MAX) continue;
            sb.append('\n').append(l);
            kept++;
        }
        p.edit().putString(RECENT, sb.toString()).apply();
    }
}
