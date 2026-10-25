package app.wyelink;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decides which browser a link goes to. Pure Java (no Android APIs) so it can be unit tested on the JVM.
 *
 * Order: Browser 1 rules, Browser 2 rules, Auth Tab launch, in-app Custom Tab (optional),
 * sign-in score, then Browser 2. Rules and scoring look at the real destination of known
 * redirect wrappers (Safe Links, Google, Facebook, ...), unwrapped offline.
 */
final class Router {

    static final int BROWSER_1 = 1;
    static final int BROWSER_2 = 2;

    /** A link counts as a sign-in once its signals add up to this. */
    static final int THRESHOLD = 3;
    private static final int CERTAIN = 9;

    static final class Config {
        boolean smartAuth = true;
        boolean customTabsAsAuth = false;
        String rules1 = "";
        String rules2 = "";
    }

    static final class Decision {
        final int target;
        final String reason;

        Decision(int target, String reason) {
            this.target = target;
            this.reason = reason;
        }
    }

    // ---- sign-in signals ----

    /** Hosts (and their subdomains) that only exist to sign people in. */
    private static final String[] AUTH_HOSTS = {
            "accounts.google.com", "accounts.youtube.com",
            "login.microsoftonline.com", "login.microsoft.com", "login.live.com", "account.live.com",
            "appleid.apple.com", "idmsa.apple.com", "account.apple.com",
            "login.yahoo.com", "id.atlassian.com", "auth.atlassian.com",
            "accounts.spotify.com", "auth.openai.com", "login.salesforce.com", "clerk.accounts.dev",
    };

    /** Identity platforms: tenants live on subdomains, the apex and www are the vendor's marketing site. */
    private static final String[] IDP_TENANT_SUFFIXES = {
            "auth0.com", "okta.com", "oktapreview.com", "okta-emea.com", "onelogin.com",
            "amazoncognito.com", "b2clogin.com", "ciamlogin.com", "duosecurity.com", "pingone.com",
            "kinde.com", "frontegg.com", "zitadel.cloud", "logto.app", "oryapis.com", "fusionauth.io",
            "stytch.com", "authgear.cloud", "descope.io", "workos.com",
    };

    /** Well-known sign-in pages that keywords can't see: {domain, path prefix}. */
    private static final String[][] KNOWN_PAGES = {
            {"slack.com", "/z-app-"}, {"notion.so", "/loginwithemail"},
            {"microsoft.com", "/devicelogin"}, {"microsoft.com", "/link"}, {"aka.ms", "/remoteconnect"},
            {"google.com", "/device"}, {"amazon.com", "/code"},
            {"amazon.com", "/ap/"}, {"amazon.co.uk", "/ap/"}, {"amazon.de", "/ap/"},
    };

    /** First host label of a server that does nothing but sign-in, e.g. sso.example.com. */
    private static final Set<String> HOST_AUTH_ONLY = set("login", "signin", "sso", "auth", "oauth", "idp", "adfs");
    private static final Set<String> HOST_STRONG = set("accounts", "sts");
    private static final Set<String> HOST_WEAK = set("account", "id", "identity", "secure");

    private static final Set<String> PATH_STRONG = set(
            "login", "log-in", "logon", "signin", "sign-in", "sign_in", "authorize", "oauth", "oauth2",
            "sso", "saml", "saml2", "adfs", "openid-connect", "magic-link", "magiclink", "magic_link",
            "devicelogin", "2fa", "two-factor", "mfa", "otp", "verify", "verification", "confirm",
            "confirmation", "activate", "activation", "reset-password", "password-reset", "forgot-password");
    private static final Set<String> PATH_WEAK = set(
            "auth", "authentication", "authenticate", "openid", "oidc", "account", "accounts", "session",
            "sessions", "identity", "consent", "callback", "password", "challenge", "action-token");

    /** Two- or three-part segments like "verify-email" or "email_verification" need one of each. */
    private static final Set<String> COMPOUND_HEADS = set(
            "login", "signin", "oauth", "sso", "verify", "verification", "confirm", "confirmation",
            "activate", "activation", "magic", "reset");
    private static final Set<String> COMPOUND_TAILS = set(
            "email", "account", "link", "callback", "token", "user", "code", "identity", "signup",
            "registration", "redirect", "device", "phone", "actions", "password", "onetime", "approve");

    /** Only these extensions are stripped, so "login.php" counts but a source file "login.ts" doesn't. */
    private static final Set<String> SERVER_EXT = set("php", "aspx", "asp", "jsp", "srf", "html", "htm", "do", "cgi", "action");

    private static final Map<String, Integer> QUERY_KEYS = new HashMap<>();
    private static final Map<String, Set<String>> QUERY_VALUES = new HashMap<>();

    static {
        for (String k : new String[]{"redirect_uri", "response_type", "code_challenge", "samlrequest",
                "samlresponse", "oauth_token", "login_hint", "id_token_hint", "oobcode", "confirmation_token",
                "verification_token", "reset_password_token", "magic_token", "login_token", "auth_token",
                "signin_token", "token_hash", "openid.mode", "openid.return_to", "invitation_token", "invite_token"}) {
            QUERY_KEYS.put(k, 3);
        }
        for (String k : new String[]{"client_id", "client_key", "token", "otp", "magic", "nonce"}) {
            QUERY_KEYS.put(k, 2);
        }
        for (String k : new String[]{"scope", "state", "code", "key", "continue", "return_to", "returnurl",
                "returnuri", "callbackurl", "redirect_to", "next"}) {
            QUERY_KEYS.put(k, 1);
        }
        QUERY_VALUES.put("mode", set("signin", "verifyemail", "resetpassword", "recoveremail"));
        QUERY_VALUES.put("type", set("magiclink", "signup", "recovery", "invite", "email_change", "email"));
        QUERY_VALUES.put("operation", set("login", "register"));
    }

    private Router() {
    }

    static Decision decide(Config c, String url, String sourcePkg, boolean customTab, boolean authTab) {
        Url outer = Url.parse(url);
        String innerRaw = unwrap(outer.raw);
        Url u = innerRaw.equals(outer.raw) ? outer : Url.parse(innerRaw);
        String via = u == outer ? "" : " (inside " + outer.host + ")";

        String hit = firstMatch(c.rules1, u, outer, sourcePkg);
        if (hit != null) return new Decision(BROWSER_1, "rule: " + hit + via);
        hit = firstMatch(c.rules2, u, outer, sourcePkg);
        if (hit != null) return new Decision(BROWSER_2, "rule: " + hit + via);

        if (authTab) return new Decision(BROWSER_1, "app sign-in tab");
        if (c.customTabsAsAuth && customTab) return new Decision(BROWSER_1, "in-app browser tab");

        if (c.smartAuth) {
            StringBuilder why = new StringBuilder();
            if (score(u, why) >= THRESHOLD) return new Decision(BROWSER_1, "sign-in: " + why + via);
        }
        return new Decision(BROWSER_2, "default");
    }

    static Decision decide(Config c, String url, String sourcePkg, boolean customTab) {
        return decide(c, url, sourcePkg, customTab, false);
    }

    /** scheme://host/path with query and fragment dropped, so stored history never holds tokens. */
    static String redact(String url) {
        Url u = Url.parse(url);
        String raw = u.raw;
        int s = raw.indexOf("://");
        String scheme = s > 0 ? raw.substring(0, s).toLowerCase(Locale.ROOT) + "://" : "";
        String path = u.path.length() > 48 ? u.path.substring(0, 48) + "…" : u.path;
        return scheme + u.host + path;
    }

    // ---- user rules ----

    /** Returns the first rule line that matches the destination or the link as tapped, or null. */
    static String firstMatch(String rules, Url u, Url outer, String sourcePkg) {
        if (rules == null || rules.isEmpty()) return null;
        for (String raw : rules.split("\n")) {
            String rule = raw.trim();
            if (rule.isEmpty() || rule.startsWith("#")) continue;
            if (matches(rule, u, sourcePkg) || (outer != u && matches(rule, outer, sourcePkg))) return rule;
        }
        return null;
    }

    static boolean matches(String rule, Url u, String sourcePkg) {
        String lower = rule.toLowerCase(Locale.ROOT);
        if (lower.startsWith("app:")) {
            String pkg = rule.substring(4).trim();
            return sourcePkg != null && !pkg.isEmpty() && sourcePkg.equalsIgnoreCase(pkg);
        }
        if (lower.startsWith("has:")) {
            String needle = lower.substring(4).trim();
            return !needle.isEmpty() && u.full.contains(needle);
        }
        if (lower.startsWith("re:")) {
            try {
                return Pattern.compile(rule.substring(3).trim(), Pattern.CASE_INSENSITIVE).matcher(u.raw).find();
            } catch (PatternSyntaxException e) {
                return false;
            }
        }
        // Plain rule: domain or domain/path-prefix. Tolerate pasted schemes and "*." wildcards.
        int scheme = lower.indexOf("://");
        if (scheme >= 0) lower = lower.substring(scheme + 3);
        if (lower.startsWith("*.")) lower = lower.substring(2);
        int slash = lower.indexOf('/');
        String domain = slash >= 0 ? lower.substring(0, slash) : lower;
        String path = slash >= 0 ? lower.substring(slash) : null;
        if (domain.startsWith("www.")) domain = domain.substring(4);
        if (domain.isEmpty() || !hostMatches(u.host, domain)) return false;
        return path == null || u.path.startsWith(path);
    }

    // ---- sign-in scoring ----

    /** Adds up sign-in signals; appends the strongest one to {@code why}. */
    static int score(Url u, StringBuilder why) {
        for (String h : AUTH_HOSTS) {
            if (hostMatches(u.host, h)) return certain(why, "sign-in host");
        }
        for (String s : IDP_TENANT_SUFFIXES) {
            if (u.host.endsWith("." + s) && !u.host.startsWith("www.")) return certain(why, "identity provider");
        }
        for (String[] page : KNOWN_PAGES) {
            if (hostMatches(u.host, page[0]) && u.path.startsWith(page[1])) return certain(why, "known sign-in page");
        }

        int total = 0, bestPts = 0;
        String best = null;

        int dot = u.host.indexOf('.');
        if (dot > 0 && u.host.indexOf('.', dot + 1) > 0) {
            String label = u.host.substring(0, dot);
            int pts = HOST_AUTH_ONLY.contains(label) ? 3 : HOST_STRONG.contains(label) ? 2 : HOST_WEAK.contains(label) ? 1 : 0;
            total += pts;
            if (pts > 0) {
                bestPts = pts;
                best = label + ".*";
            }
        }

        int pathPts = 0;
        String prev = "";
        for (String seg : u.path.split("/")) {
            if (seg.isEmpty()) continue;
            int ext = seg.lastIndexOf('.');
            if (ext > 0 && SERVER_EXT.contains(seg.substring(ext + 1))) seg = seg.substring(0, ext);
            int pts = segmentPoints(seg, prev);
            if (pts > bestPts) {
                bestPts = pts;
                best = "/" + seg;
            }
            pathPts += pts;
            prev = seg;
        }
        total += Math.min(pathPts, 3);

        for (String pair : u.query.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            String value = eq >= 0 ? pair.substring(eq + 1) : "";
            Integer known = QUERY_KEYS.get(key);
            int pts = known != null ? known : key.endsWith("_token") ? 2 : 0;
            Set<String> values = QUERY_VALUES.get(key);
            if (values != null && values.contains(value)) pts = Math.max(pts, 2);
            if (pts > bestPts) {
                bestPts = pts;
                best = "?" + key;
            }
            total += pts;
        }

        if (total >= THRESHOLD && best != null) why.append(best);
        return total;
    }

    private static int segmentPoints(String seg, String prev) {
        if (PATH_STRONG.contains(seg)) return 3;
        if (seg.equals("new") && (prev.equals("session") || prev.equals("sessions"))) return 3; // /users/session/new
        if (seg.equals("reset")) return 2;
        if (PATH_WEAK.contains(seg)) return 1;
        String[] parts = seg.split("[-_]");
        if (parts.length < 2 || parts.length > 3) return 0;
        boolean head = false, tail = false;
        for (String p : parts) {
            if (COMPOUND_HEADS.contains(p)) head = true;
            else if (COMPOUND_TAILS.contains(p)) tail = true;
        }
        return head && tail ? 3 : 0;
    }

    private static int certain(StringBuilder why, String reason) {
        why.append(reason);
        return CERTAIN;
    }

    // ---- offline unwrapping of redirect wrappers ----

    /** Follows known wrappers whose destination is written inside the link (up to 3 levels). */
    static String unwrap(String url) {
        for (int i = 0; i < 3; i++) {
            String inner = inner(url);
            if (inner == null) break;
            url = inner;
        }
        return url;
    }

    private static String inner(String raw) {
        Url u = Url.parse(raw);
        String h = u.host;
        String dest = null;
        if (h.endsWith(".safelinks.protection.outlook.com")) {
            dest = param(raw, "url");
        } else if ((h.equals("www.google.com") || h.equals("google.com")) && u.path.equals("/url")) {
            dest = param(raw, "q");
            if (dest == null) dest = param(raw, "url");
        } else if (h.equals("l.facebook.com") || h.equals("lm.facebook.com") || h.equals("l.messenger.com")
                || h.equals("l.instagram.com") || h.equals("l.threads.com")) {
            dest = param(raw, "u");
        } else if (hostMatches(h, "linkedin.com") && u.path.startsWith("/redir/")) {
            dest = param(raw, "url");
        } else if (hostMatches(h, "youtube.com") && u.path.equals("/redirect")) {
            dest = param(raw, "q");
        } else if (h.equals("slack-redir.net")) {
            dest = param(raw, "url");
        } else if (h.equals("steamcommunity.com") && u.path.startsWith("/linkfilter")) {
            dest = param(raw, "u");
            if (dest == null) dest = param(raw, "url");
        } else if (h.equals("away.vk.com") || (hostMatches(h, "vk.com") && u.path.equals("/away.php"))) {
            dest = param(raw, "to");
        } else if (h.equals("urldefense.proofpoint.com") && u.path.startsWith("/v2/url")) {
            String v = rawParam(raw, "u");
            if (v != null) dest = decode(v.replace('_', '/').replace('-', '%'));
        } else if (h.equals("urldefense.com") && u.path.startsWith("/v3/__")) {
            int a = raw.indexOf("/v3/__") + 6;
            int b = raw.indexOf("__;", a);
            if (b < 0) b = raw.lastIndexOf("__");
            if (b > a) dest = raw.substring(a, b);
        } else if (h.endsWith(".awstrack.me") && u.path.startsWith("/l0/")) {
            int a = raw.toLowerCase(Locale.ROOT).indexOf("/l0/") + 4;
            int b = raw.indexOf('/', a);
            dest = decode(b > 0 ? raw.substring(a, b) : raw.substring(a));
        } else if (h.equals("click.pstmrk.it")) {
            for (String part : raw.split("/")) {
                if (part.toLowerCase(Locale.ROOT).startsWith("http")) {
                    dest = decode(part);
                    break;
                }
            }
        }
        if (dest == null || !dest.toLowerCase(Locale.ROOT).startsWith("http")) return null;
        return dest;
    }

    private static String rawParam(String raw, String key) {
        int q = raw.indexOf('?');
        if (q < 0) return null;
        String qs = raw.substring(q + 1);
        int hash = qs.indexOf('#');
        if (hash >= 0) qs = qs.substring(0, hash);
        for (String pair : qs.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0 && pair.substring(0, eq).equalsIgnoreCase(key)) return pair.substring(eq + 1);
        }
        return null;
    }

    private static String param(String raw, String key) {
        String v = rawParam(raw, key);
        return v == null ? null : decode(v);
    }

    private static String decode(String s) {
        try {
            return URLDecoder.decode(s.replace("+", "%2B"), "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return s;
        }
    }

    // ---- helpers ----

    static boolean hostMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static Set<String> set(String... items) {
        return new HashSet<>(Arrays.asList(items));
    }

    /** Minimal, forgiving URL split. Everything except {@link #raw} is lowercased. */
    static final class Url {
        final String raw, full, host, path, query;

        private Url(String raw, String full, String host, String path, String query) {
            this.raw = raw;
            this.full = full;
            this.host = host;
            this.path = path;
            this.query = query;
        }

        static Url parse(String url) {
            String raw = url == null ? "" : url.trim();
            String full = raw.toLowerCase(Locale.ROOT);
            int s = full.indexOf("://");
            String rest = s >= 0 ? full.substring(s + 3) : full;

            int end = rest.length();
            for (int i = 0; i < rest.length(); i++) {
                char ch = rest.charAt(i);
                if (ch == '/' || ch == '?' || ch == '#') {
                    end = i;
                    break;
                }
            }
            String host = rest.substring(0, end);
            int at = host.lastIndexOf('@');
            if (at >= 0) host = host.substring(at + 1);
            if (!host.startsWith("[")) {
                int colon = host.indexOf(':');
                if (colon >= 0) host = host.substring(0, colon);
            }
            if (host.endsWith(".")) host = host.substring(0, host.length() - 1);

            String tail = rest.substring(end);
            int hash = tail.indexOf('#');
            if (hash >= 0) tail = tail.substring(0, hash);
            int q = tail.indexOf('?');
            String path = q >= 0 ? tail.substring(0, q) : tail;
            String query = q >= 0 ? tail.substring(q + 1) : "";
            return new Url(raw, full, host, path, query);
        }
    }
}
