package app.wyelink;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RouterTest {

    private static int route(Router.Config c, String url) {
        return Router.decide(c, url, null, false).target;
    }

    private static int route(String url) {
        return route(new Router.Config(), url);
    }

    @Test
    public void signInLinksGoToBrowser1() {
        assertEquals(1, route("https://accounts.google.com/o/oauth2/v2/auth?client_id=x"));
        assertEquals(1, route("https://login.microsoftonline.com/common/oauth2/authorize"));
        assertEquals(1, route("https://dev-123.okta.com/app/foo"));
        assertEquals(1, route("https://github.com/login/oauth/authorize?client_id=abc"));
        assertEquals(1, route("https://login.mycompany.com/"));
        assertEquals(1, route("https://example.com/account/verify-email?id=5"));
        assertEquals(1, route("https://shop.example.com/users/confirmation?confirmation_token=abc"));
        assertEquals(1, route("https://app.example.com/start?redirect_uri=myapp%3A%2F%2Fcb&response_type=code"));
        assertEquals(1, route("https://example.com/signin.php"));
        assertEquals(1, route("https://discord.com/oauth2/authorize?client_id=1"));
    }

    @Test
    public void ordinaryLinksGoToBrowser2() {
        assertEquals(2, route("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertEquals(2, route("https://en.wikipedia.org/wiki/Wye_(rail)"));
        assertEquals(2, route("https://news.ycombinator.com/item?id=1"));
        assertEquals(2, route("https://example.com/author/jane"));
        assertEquals(2, route("https://google.com/search?q=login"));
        assertEquals(2, route("https://docs.example.com/authentication-guide-v2/overview"));
    }

    @Test
    public void vendorMarketingSitesAreNotSignIns() {
        assertEquals(2, route("https://auth0.com/blog/what-is-oauth-2"));
        assertEquals(2, route("https://www.okta.com/pricing/"));
        assertEquals(1, route("https://acme.us.auth0.com/authorize?response_type=code"));
    }

    @Test
    public void compoundSegmentsNeedAnAuthWordAndAKnownTail() {
        assertEquals(1, route("https://example.com/email-verification?token=abc"));
        assertEquals(1, route("https://example.com/login-callback"));
        assertEquals(2, route("https://www.reddit.com/r/android/comments/abc/login_issue/"));
        assertEquals(2, route("https://example.com/products/verify-pro-sensor"));
        assertEquals(2, route("https://example.com/blog/how-to-verify-your-identity"));
    }

    @Test
    public void sourceFileExtensionsAreNotStripped() {
        assertEquals(2, route("https://github.com/user/repo/blob/main/src/auth/login.ts"));
        assertEquals(1, route("https://example.com/auth/login.aspx"));
    }

    @Test
    public void smartAuthCanBeTurnedOff() {
        Router.Config c = new Router.Config();
        c.smartAuth = false;
        assertEquals(2, route(c, "https://accounts.google.com/signin"));
    }

    @Test
    public void rulesBeatSmartRouting() {
        Router.Config c = new Router.Config();
        c.rules1 = "# my bank\nmybank.com\nhas:invoice";
        c.rules2 = "accounts.google.com/b/1\n*.example.org/login";
        assertEquals(1, route(c, "https://secure.mybank.com/home"));
        assertEquals(1, route(c, "https://store.com/pay?invoice=7"));
        assertEquals(2, route(c, "https://accounts.google.com/b/1/settings"));
        assertEquals(2, route(c, "https://www.example.org/login"));
        assertEquals(2, route(c, "https://notmybank.com.evil.io/"));
    }

    @Test
    public void appAndRegexRules() {
        Router.Config c = new Router.Config();
        c.rules1 = "app:com.slack\nre:^https://[^/]+\\.gov/";
        assertEquals(1, Router.decide(c, "https://youtube.com/x", "com.slack", false).target);
        assertEquals(2, Router.decide(c, "https://youtube.com/x", "com.whatsapp", false).target);
        assertEquals(1, route(c, "https://www.irs.gov/forms"));
        c.rules1 = "re:([bad";
        assertEquals(2, route(c, "https://youtube.com"));
    }

    @Test
    public void customTabsOptIn() {
        Router.Config c = new Router.Config();
        assertEquals(2, Router.decide(c, "https://youtube.com", "com.x", true).target);
        c.customTabsAsAuth = true;
        assertEquals(1, Router.decide(c, "https://youtube.com", "com.x", true).target);
    }

    @Test
    public void authTabLaunchIsAlwaysASignIn() {
        Router.Config c = new Router.Config();
        c.smartAuth = false;
        assertEquals(1, Router.decide(c, "https://example.com/anything", "com.x", true, true).target);
        // Explicit rules still win.
        c.rules2 = "example.com";
        assertEquals(2, Router.decide(c, "https://example.com/anything", "com.x", true, true).target);
    }

    @Test
    public void unwrapsRedirectWrappersOffline() {
        assertEquals("https://example.com/verify-email?token=abc", Router.unwrap(
                "https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Fverify-email%3Ftoken%3Dabc&data=05%7C01&reserved=0"));
        assertEquals("https://example.com/login", Router.unwrap("https://l.facebook.com/l.php?u=https%3A%2F%2Fexample.com%2Flogin&h=x"));
        assertEquals("https://example.com/login", Router.unwrap("https://urldefense.proofpoint.com/v2/url?u=https-3A__example.com_login&d=x"));
        assertEquals("https://example.com/password-reset?token=abc",
                Router.unwrap("https://urldefense.com/v3/__https://example.com/password-reset?token=abc__;!!abc$"));
        assertEquals("https://example.com/confirm?token=abc",
                Router.unwrap("https://r.us-east-1.awstrack.me/L0/https:%2F%2Fexample.com%2Fconfirm%3Ftoken=abc/1/0100-abc/xyz=123"));
        // Nested wrappers.
        assertEquals("https://example.com/login", Router.unwrap("https://www.google.com/url?q="
                + "https%3A%2F%2Fl.facebook.com%2Fl.php%3Fu%3Dhttps%253A%252F%252Fexample.com%252Flogin"));
        // Not a wrapper, or a wrapper pointing somewhere that isn't http(s).
        assertEquals("https://example.com/?url=https://x.com", Router.unwrap("https://example.com/?url=https://x.com"));
        assertEquals("https://www.google.com/url?q=javascript:alert(1)", Router.unwrap("https://www.google.com/url?q=javascript:alert(1)"));
    }

    @Test
    public void rulesAndScoringSeeThroughWrappers() {
        String wrapped = "https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fwww.bbc.co.uk%2Fnews&data=x";
        assertEquals(2, route(wrapped));
        Router.Config c = new Router.Config();
        c.rules1 = "bbc.co.uk";
        Router.Decision d = Router.decide(c, wrapped, null, false);
        assertEquals(1, d.target);
        assertTrue(d.reason.contains("inside"));
        // A rule on the wrapper itself still works.
        c.rules1 = "";
        c.rules2 = "safelinks.protection.outlook.com";
        assertEquals(2, route(c, "https://nam12.safelinks.protection.outlook.com/?url=https%3A%2F%2Fexample.com%2Flogin"));
    }

    @Test
    public void redactDropsQueryAndFragment() {
        assertEquals("https://example.com/verify-email",
                Router.redact("https://Example.com/verify-email?token=SECRET#frag"));
        String r = Router.redact("https://abcd.supabase.co/auth/v1/verify?token=SECRET&type=magiclink");
        assertFalse(r.contains("SECRET"));
        assertTrue(Router.redact("https://example.com/" + "a".repeat(100)).endsWith("…"));
    }

    @Test
    public void urlParsing() {
        Router.Url u = Router.Url.parse("HTTPS://user:pw@Login.Example.COM:8443/A/b?x=1#frag");
        assertEquals("login.example.com", u.host);
        assertEquals("/a/b", u.path);
        assertEquals("x=1", u.query);
    }
}
