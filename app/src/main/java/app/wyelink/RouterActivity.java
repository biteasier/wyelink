package app.wyelink;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Invisible activity registered as a browser: picks a target browser, forwards the link, and exits. */
public class RouterActivity extends Activity {

    private static final String EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION";
    private static final String EXTRA_CUSTOM_TABS_SESSION_ID = "android.support.customtabs.extra.SESSION_ID";
    private static final String EXTRA_LAUNCH_AUTH_TAB = "androidx.browser.auth.extra.LAUNCH_AUTH_TAB";
    private static final String EXTRA_AUTH_REDIRECT_SCHEME = "androidx.browser.auth.extra.REDIRECT_SCHEME";
    private static final String EXTRA_AUTH_REDIRECT_HOST = "androidx.browser.auth.extra.HTTPS_REDIRECT_HOST";

    /** Task flags worth keeping from the caller. URI grants and everything else are dropped. */
    private static final int KEPT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;

    private static final Pattern URL_IN_TEXT = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            route(getIntent());
        } finally {
            finish();
        }
    }

    /** {@link Router#BROWSER_1} or {@link Router#BROWSER_2} to skip the rules (share-sheet targets), 0 to route. */
    int forcedTarget() {
        return 0;
    }

    private void route(Intent in) {
        Uri uri = Intent.ACTION_SEND.equals(in.getAction())
                ? firstUrl(in.getStringExtra(Intent.EXTRA_TEXT))
                : in.getData();
        if (uri == null) return;
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return;

        Bundle extras = readExtras(in);
        boolean customTab = extras != null && extras.containsKey(EXTRA_CUSTOM_TABS_SESSION);
        boolean authTab = extras != null && (extras.getBoolean(EXTRA_LAUNCH_AUTH_TAB, false)
                || extras.containsKey(EXTRA_AUTH_REDIRECT_SCHEME) || extras.containsKey(EXTRA_AUTH_REDIRECT_HOST));

        String source = sourcePackage();
        SharedPreferences prefs = Prefs.get(this);
        Router.Decision d = forcedTarget() != 0
                ? new Router.Decision(forcedTarget(), "shared to Browser " + forcedTarget())
                : Router.decide(Prefs.config(prefs), uri.toString(), source, customTab, authTab);
        String pkg = Prefs.browser(prefs, d.target);

        if (prefs.getBoolean(Prefs.HISTORY, false)) {
            Prefs.addRecent(prefs, "B" + d.target + "  " + Router.redact(uri.toString()) + "\n     " + d.reason
                    + (source != null ? " · from " + source : ""));
        }

        Intent out = forwardIntent(in, uri, extras);
        if (!pkg.isEmpty() && !pkg.equals(getPackageName())) {
            out.setPackage(pkg);
            try {
                startActivity(out);
                if (prefs.getBoolean(Prefs.TOAST, false)) toast(label(pkg) + " · " + d.reason);
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Browser was uninstalled or refuses the intent; fall through to the chooser.
            }
            out.setPackage(null);
        }
        Intent chooser = Intent.createChooser(out, getString(R.string.open_with));
        chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, new ComponentName[]{
                new ComponentName(this, RouterActivity.class),
                new ComponentName(this, ShareToBrowser1Activity.class),
                new ComponentName(this, ShareToBrowser2Activity.class)});
        if (getCallingActivity() != null) chooser.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        startActivity(chooser);
    }

    /**
     * Builds a fresh VIEW intent instead of re-sending the caller's: no component, selector, ClipData
     * or URI grants survive. Extras are kept so in-app tabs (Custom Tabs, Auth Tab) still open as tabs.
     */
    private Intent forwardIntent(Intent in, Uri uri, Bundle extras) {
        Intent out = new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE);
        if (Intent.ACTION_VIEW.equals(in.getAction())) {
            if (extras != null) {
                // The session belongs to Wyelink's pass-through service, not the target browser:
                // hand over a session-less Custom Tab.
                if (extras.containsKey(EXTRA_CUSTOM_TABS_SESSION)) extras.putBinder(EXTRA_CUSTOM_TABS_SESSION, null);
                extras.remove(EXTRA_CUSTOM_TABS_SESSION_ID);
                out.putExtras(extras);
            }
            out.addFlags(in.getFlags() & KEPT_FLAGS);
        }
        // An app waiting for a result (Auth Tab sign-in) gets it straight from the browser.
        if (getCallingActivity() != null) out.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        return out;
    }

    /** A copy of the caller's extras, or null if there are none or they can't be read. */
    private static Bundle readExtras(Intent in) {
        try {
            Bundle b = in.getExtras();
            if (b == null) return null;
            b.size(); // forces unparcelling here, where a bad Parcelable can be caught
            return b;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String sourcePackage() {
        Uri ref = getReferrer();
        if (ref != null && "android-app".equals(ref.getScheme())) {
            String host = ref.getHost();
            if (host != null && !host.equals(getPackageName())) return host;
        }
        return null;
    }

    private String label(String pkg) {
        PackageManager pm = getPackageManager();
        try {
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return pkg;
        }
    }

    private void toast(String text) {
        Toast.makeText(getApplicationContext(), text, Toast.LENGTH_SHORT).show();
    }

    private static Uri firstUrl(String text) {
        if (text == null) return null;
        Matcher m = URL_IN_TEXT.matcher(text);
        return m.find() ? Uri.parse(m.group()) : null;
    }
}
