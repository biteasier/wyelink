package app.wyelink;

import android.app.Activity;
import android.app.role.RoleManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Settings screen. Everything is saved as soon as it changes. */
public class MainActivity extends Activity {

    private SharedPreferences prefs;
    private final List<String> browserPkgs = new ArrayList<>();
    private final List<String> browserLabels = new ArrayList<>();

    private TextView status, testResult, recent;
    private Button setDefault;
    private Spinner browser1, browser2;
    private CompoundButton smart, customTabs, toast, history;
    private EditText rules1, rules2, test;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = Prefs.get(this);

        View root = findViewById(R.id.root);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int l = 0, t = 0, r = 0, b = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
                l = i.left; t = i.top; r = i.right; b = i.bottom;
            } else {
                l = insets.getSystemWindowInsetLeft(); t = insets.getSystemWindowInsetTop();
                r = insets.getSystemWindowInsetRight(); b = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l, t, r, b);
            return insets;
        });

        status = findViewById(R.id.status);
        setDefault = findViewById(R.id.set_default);
        browser1 = findViewById(R.id.browser1);
        browser2 = findViewById(R.id.browser2);
        smart = findViewById(R.id.smart);
        customTabs = findViewById(R.id.custom_tabs);
        toast = findViewById(R.id.toast);
        history = findViewById(R.id.history);
        rules1 = findViewById(R.id.rules1);
        rules2 = findViewById(R.id.rules2);
        test = findViewById(R.id.test);
        testResult = findViewById(R.id.test_result);
        recent = findViewById(R.id.recent);

        loadBrowsers();
        bindSpinner(browser1, Prefs.BROWSER_1);
        bindSpinner(browser2, Prefs.BROWSER_2);
        bindSwitch(smart, Prefs.SMART_AUTH, true);
        bindSwitch(customTabs, Prefs.CUSTOM_TABS, false);
        bindSwitch(toast, Prefs.TOAST, false);
        history.setChecked(prefs.getBoolean(Prefs.HISTORY, false));
        history.setOnCheckedChangeListener((b, checked) -> {
            SharedPreferences.Editor e = prefs.edit().putBoolean(Prefs.HISTORY, checked);
            if (!checked) e.remove(Prefs.RECENT);
            e.apply();
            updateRecent();
        });
        bindText(rules1, Prefs.RULES_1);
        bindText(rules2, Prefs.RULES_2);
        test.addTextChangedListener(new Watcher(this::updateTest));

        setDefault.setOnClickListener(v -> requestDefault());
        findViewById(R.id.clear_recent).setOnClickListener(v -> {
            prefs.edit().remove(Prefs.RECENT).apply();
            updateRecent();
        });
        ((TextView) findViewById(R.id.version)).setText(getString(R.string.version, versionName()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
        updateRecent();
        updateTest();
    }

    // ---- browsers ----

    private void loadBrowsers() {
        browserPkgs.add("");
        browserLabels.add(getString(R.string.ask_every_time));
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
                .addCategory(Intent.CATEGORY_BROWSABLE);
        List<ResolveInfo> found = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
        List<String[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : found) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
            rows.add(new String[]{ri.loadLabel(pm).toString(), pkg});
        }
        rows.sort((a, b) -> a[0].compareToIgnoreCase(b[0]));
        for (String[] row : rows) {
            browserLabels.add(row[0]);
            browserPkgs.add(row[1]);
        }
    }

    private void bindSpinner(Spinner spinner, String key) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, browserLabels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        int idx = browserPkgs.indexOf(prefs.getString(key, ""));
        spinner.setSelection(Math.max(idx, 0), false);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString(key, browserPkgs.get(position)).apply();
                updateTest();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private String browserName(int target) {
        int idx = browserPkgs.indexOf(Prefs.browser(prefs, target));
        return idx > 0 ? browserLabels.get(idx) : getString(R.string.ask_every_time);
    }

    // ---- default browser ----

    private boolean isDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                return rm.isRoleHeld(RoleManager.ROLE_BROWSER);
            }
        }
        ResolveInfo ri = getPackageManager().resolveActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")), PackageManager.MATCH_DEFAULT_ONLY);
        return ri != null && getPackageName().equals(ri.activityInfo.packageName);
    }

    private void requestDefault() {
        if (Build.VERSION.SDK_INT >= 29 && !isDefaultBrowser()) {
            RoleManager rm = getSystemService(RoleManager.class);
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 1);
                return;
            }
        }
        startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }

    private void updateStatus() {
        boolean on = isDefaultBrowser();
        status.setText(on ? R.string.status_on : R.string.status_off);
        status.setTextColor(getColor(on ? R.color.ok : R.color.warn));
        setDefault.setText(on ? R.string.change_default : R.string.make_default);
    }

    // ---- live preview ----

    private void updateTest() {
        String url = test.getText().toString().trim();
        if (url.isEmpty()) {
            testResult.setText(R.string.test_empty);
            return;
        }
        if (!url.contains("://")) url = "https://" + url;
        Router.Decision d = Router.decide(Prefs.config(prefs), url, null, false);
        testResult.setText(getString(R.string.test_result, d.target, browserName(d.target), d.reason));
    }

    private void updateRecent() {
        if (!prefs.getBoolean(Prefs.HISTORY, false)) {
            recent.setText(R.string.recent_off);
            return;
        }
        String r = prefs.getString(Prefs.RECENT, "");
        recent.setText(r.isEmpty() ? getString(R.string.recent_empty) : r);
    }

    // ---- binding helpers ----

    private void bindSwitch(CompoundButton sw, String key, boolean def) {
        sw.setChecked(prefs.getBoolean(key, def));
        sw.setOnCheckedChangeListener((b, checked) -> {
            prefs.edit().putBoolean(key, checked).apply();
            updateTest();
        });
    }

    private void bindText(EditText et, String key) {
        et.setText(prefs.getString(key, ""));
        et.addTextChangedListener(new Watcher(() -> {
            prefs.edit().putString(key, et.getText().toString()).apply();
            updateTest();
        }));
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "";
        }
    }

    private static final class Watcher implements TextWatcher {
        private final Runnable onChange;

        Watcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChange.run();
        }
    }
}
