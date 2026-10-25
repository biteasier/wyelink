<p align="center">
  <img src="art/wyelink-icon-rounded.png" width="120" alt="Wyelink app icon: a Y-shaped junction with a keyhole node">
</p>

<h1 align="center">Wyelink</h1>

<p align="center">
  <b>Android link router: open login links in one browser and everything else in another.</b><br>
  34 KB · no permissions · no internet · open source
</p>

<p align="center">
  <a href="https://github.com/biteasier/wyelink/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/biteasier/wyelink?label=download&color=4f46e5"></a>
  <a href="https://github.com/biteasier/wyelink/actions/workflows/build.yml"><img alt="Build status" src="https://github.com/biteasier/wyelink/actions/workflows/build.yml/badge.svg"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-0d9488">
  <img alt="APK size 34 KB" src="https://img.shields.io/badge/APK-34%20KB-0d9488">
  <img alt="No permissions" src="https://img.shields.io/badge/permissions-none-0d9488">
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/github/license/biteasier/wyelink?color=4f46e5"></a>
</p>

---

Android sends every link to one default browser. **Wyelink** lets you use two: one for **sign-ins** (Google, Microsoft, Apple, GitHub, Okta and other login pages, OAuth/SSO flows, "verify your email", magic links, password resets) and one for **everything else**.

Set Wyelink as your default browser and pick your two browsers. When you tap a link, Wyelink decides in a few milliseconds and hands it over. It never shows a screen of its own.

A *wye* is a Y-shaped railway junction where one track splits into two. Same idea: one link in, the right browser out.

## Why you might want this

- Keep all your logins in **one browser** (say Firefox or Brave with your password manager and sessions) and browse in another (say Chrome).
- Stop "Sign in with Google" in some app from opening in a browser where you aren't signed in.
- Send work links (`app:com.slack`, `mycompany.com`) to a work browser and the rest to a personal one.

## Features

- **Smart sign-in detection.** Scores every link: identity providers, `login.*`/`sso.*` hosts, `/login`, `/oauth2/authorize`, `/saml`, `/verify-email`, `/magic-link` paths, `redirect_uri`, `code_challenge`, `oobCode`, `*_token` parameters. Vendor marketing pages (`okta.com`, `auth0.com/blog`) and docs about authentication stay in Browser 2.
- **Sees through redirect wrappers, offline.** Outlook Safe Links, Proofpoint URL Defense, Google `/url?q=`, Facebook, Instagram, Threads, Messenger, LinkedIn, YouTube, Slack, Steam and VK redirects, Amazon SES and Postmark click tracking.
- **Works with in-app sign-in tabs.** Wyelink offers a pass-through Custom Tabs service, so apps that use AppAuth-style sign-in libraries pick it, and their login tab opens in Browser 1. Android's new **Auth Tab** gets its result passed straight back to the app.
- **Your own rules** beat detection: domains, paths, the app a link came from, text, regular expressions.
- **Try a link**: paste any URL to see which browser it goes to and why.
- **Share-sheet fallback**: share a page to "Open in Browser 1" or "Open in Browser 2" when you want it in the other one.
- **Private by design**: no permissions, no internet, no logging, no backups. History is off by default and never stores tokens.
- **Tiny and native**: pure Java on Android framework APIs, zero libraries, ~34 KB APK. Adaptive and themed (Material You) icon. Light and dark mode.

## Install

1. Download **`Wyelink-1.1.0.apk`** from [**Releases**](https://github.com/biteasier/wyelink/releases/latest) and open it on your phone (allow "install unknown apps" when asked).
2. Open Wyelink and tap **Make default**.
3. Pick **Browser 1** (sign-ins) and **Browser 2** (everything else).
4. Optional: add rules, then paste a link into **Try a link** to check where it goes.

Needs Android 8.0 (API 26) or newer. The SHA-256 checksum is listed with each release.

## How a link is routed

The first match wins:

1. **Always Browser 1** rules
2. **Always Browser 2** rules
3. An app's **Auth Tab** sign-in → Browser 1
4. *(optional)* any in-app browser tab (Custom Tab) → Browser 1
5. **Sign-in score** of 3 or more → Browser 1
6. Everything else → Browser 2

Wrapped links are unwrapped before steps 1–5, so your rules and the score apply to the real destination. The link you tapped is still the one that opens.

<details>
<summary><b>What adds up to a sign-in?</b></summary>

| Signal | Examples | Points |
|---|---|---|
| Sign-in host | `accounts.google.com`, `login.microsoftonline.com`, `appleid.apple.com`, `login.live.com`, `login.yahoo.com` | certain |
| Identity-platform tenant | `*.okta.com`, `*.auth0.com`, Cognito, Azure B2C, OneLogin, Duo, PingOne, Kinde, Frontegg, Zitadel, Logto, Ory, Stytch, WorkOS | certain |
| Known sign-in page | `microsoft.com/devicelogin`, `google.com/device`, `amazon.com/ap/…`, Slack and Notion magic links | certain |
| Host label | `login.*`, `sso.*`, `auth.*`, `idp.*` · `accounts.*` · `account.*`, `id.*` | 3 · 2 · 1 |
| Path | `/login`, `/signin`, `/authorize`, `/oauth2`, `/saml`, `/verify`, `/confirm`, `/magic-link`, `/2fa`, `verify-email`, `email_verification` · `/reset` · `/auth`, `/callback`, `/password` | 3 · 2 · 1 |
| Query | `redirect_uri`, `response_type`, `code_challenge`, `SAMLRequest`, `oobCode`, `confirmation_token` · `client_id`, `token`, `otp`, any `*_token` · `scope`, `state`, `code`, `next` | 3 · 2 · 1 |

Path points are capped at 3 per link. The full lists are in [`Router.java`](app/src/main/java/app/wyelink/Router.java).
</details>

### Rule syntax

One rule per line:

```text
example.com           domain and all subdomains
example.com/login     domain + path prefix
app:com.slack         links opened from that app
has:invoice           URL contains the text
re:^https://.*\.gov/  regular expression
# comment
```

## Privacy and security

- **No permissions at all**, including no `INTERNET`, so the app can't send anything anywhere.
- No logging, analytics, crash reporting or third-party code.
- Backups are off: no Google cloud backup and no phone-to-phone transfer.
- **Recent links** is off by default. When on, it keeps the last 15 links as `scheme://host/path` only, never the part after `?` where tokens live.
- Links are forwarded as a fresh intent. The caller's component, selector, ClipData and URI-permission grants are dropped.
- `app:` rules rely on the referrer Android reports, which the sending app can set itself. Don't treat them as a security boundary.

See [SECURITY.md](SECURITY.md) to report a problem privately.

## Known limits

These come from Android and other apps, not from Wyelink:

- **Verified App Links** (for example, YouTube links when the YouTube app is installed) open the app directly. Android never asks the default browser about them.
- Microsoft's sign-in library (MSAL) only uses browsers on its built-in safe list, and a few apps always open Chrome. Those links never reach Wyelink.
- Native sign-ins (Google one-tap, passkeys, Credential Manager) don't use a browser at all.
- Trackers that hide the destination entirely (SendGrid, Mailgun, HubSpot, bit.ly) can only be read by going online. Wyelink stays offline, so they're routed by their visible URL.
- Keyword detection can't be perfect. A bare `/verify` path such as `example.com/verify/blog` still counts as a sign-in. Use a rule or the share sheet for the rare miss, and [report it](https://github.com/biteasier/wyelink/issues/new?template=wrong_browser.yml).

## FAQ

**How do I open certain links in a specific browser on Android?**
Install Wyelink, make it the default browser, choose Browser 1 and Browser 2, and add the sites to **Always Browser 1** or **Always Browser 2**.

**Can I use Firefox for logins and Chrome for everything else?**
Yes. Set Browser 1 to Firefox and Browser 2 to Chrome. Any pair of installed browsers works, for example Brave, Edge, Samsung Internet, DuckDuckGo, Vivaldi or Chrome.

**Does Wyelink slow links down?**
No. It decides in a few milliseconds, with no network request. In-app sign-in tabs skip the browser's prefetching, so the first page can load slightly slower than a direct tab.

**Does it work with "Sign in with Google" inside apps?**
When the app opens its sign-in page in a browser or an in-app tab, yes. Apps that sign in natively (Google one-tap, Credential Manager) don't open a browser at all.

**Is it on Google Play or F-Droid?**
Not yet. Download the APK from [Releases](https://github.com/biteasier/wyelink/releases/latest).

## Build from source

```bash
git clone https://github.com/biteasier/wyelink.git
cd wyelink
./gradlew testDebugUnitTest assembleRelease
```

The APK is written to `app/build/outputs/apk/release/app-release.apk`. Needs JDK 17 and the Android SDK (platform 35). Release builds are signed with your local debug key so they can be sideloaded right away. Add your own `signingConfig` before distributing.

### Project layout

```text
app/src/main/java/app/wyelink/
  Router.java                        rules, sign-in scoring, offline unwrapping (pure Java)
  RouterActivity.java                invisible "browser" that forwards each link
  ShareToBrowser1/2Activity.java     share-sheet "Open in Browser 1/2"
  CustomTabsPassThroughService.java  lets AppAuth-style sign-ins choose Wyelink
  MainActivity.java                  settings screen
  Prefs.java                         settings storage
app/src/main/aidl/…/ICustomTabsService.aidl   Custom Tabs contract (AndroidX, Apache 2.0)
app/src/test/java/app/wyelink/
  RouterTest.java                    behaviour tests
  CorpusTest.java                    137 real-world sign-in links + 71 ordinary links
art/                                 icon source (SVG) and PNG exports
```

## Contributing

Found a link that went to the wrong browser? [Open a "wrong browser" issue](https://github.com/biteasier/wyelink/issues/new?template=wrong_browser.yml) with the tokens removed. Pull requests are welcome. Please add the link to `CorpusTest.java` and keep the app free of permissions and libraries.

## License

[MIT](LICENSE) © biteasier. Includes the AndroidX Custom Tabs AIDL under Apache 2.0, see [NOTICE](NOTICE).

---

<p align="center">Made by <a href="https://www.youtube.com/@biteasier">biteasier</a>, tutorials on automation and small tools that make life easier.</p>
