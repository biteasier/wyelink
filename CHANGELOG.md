# Changelog

All notable changes to Wyelink. Versions follow [Semantic Versioning](https://semver.org/).

## [1.1.0] - 2026-10-25

First public release.

### Added
- **Score-based sign-in detection.** Sign-in providers, identity-platform tenants, login, OAuth, SAML, verify and confirm pages, magic-link, reset and invitation tokens each add points. A link needs 3 points to count as a sign-in.
- **Offline unwrapping of redirect wrappers:** Outlook Safe Links, Proofpoint URL Defense v2/v3, Google, Facebook, Instagram, Threads, Messenger, LinkedIn, YouTube, Slack, Steam, VK, Amazon SES and Postmark. Rules and detection see the real destination.
- **Custom Tabs pass-through service.** Apps whose sign-in libraries (AppAuth and SDKs built on it) only pick tab-capable browsers now choose Wyelink, so their sign-in tabs open in Browser 1.
- **Auth Tab support.** An app's Auth Tab sign-in always goes to Browser 1, and the result is passed straight back to the app.
- **Share-sheet targets** "Open in Browser 1" and "Open in Browser 2" for a page that opened in the wrong browser.
- Test corpus of 137 real-world sign-in links and 71 ordinary links.

### Changed
- Recent links is opt-in and keeps only `scheme://host/path`, never query strings or fragments. History saved by 1.0 is deleted on first launch.
- Backups are off: no cloud backup and no phone-to-phone transfer.
- Links are forwarded as a fresh intent. The caller's component, selector, ClipData and URI-permission grants are dropped.
- Far fewer false positives. Vendor marketing sites (`okta.com`, `auth0.com/blog`), documentation pages about authentication and `/login_issue`-style paths now go to Browser 2.

## [1.0.0] - 2026-10-25

Initial private build: two-browser routing, rules (`domain`, `app:`, `has:`, `re:`), keyword sign-in detection, "Try a link" preview, adaptive and themed icon.

[1.1.0]: https://github.com/biteasier/wyelink/releases/tag/v1.1.0
[1.0.0]: https://github.com/biteasier/wyelink/blob/main/CHANGELOG.md#100---2026-10-25
