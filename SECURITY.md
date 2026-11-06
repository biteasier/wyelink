# Security policy

Wyelink sees every link you open, so it is built to hold as little as possible:

- no permissions, including no internet access
- no logging, analytics or third-party libraries
- backups and phone-to-phone transfer are disabled
- link history is off by default, never stores query strings, and hides long token-like path parts

## Reporting a vulnerability

Please **don't open a public issue** for security problems. Use GitHub's private reporting instead: **Security → Report a vulnerability** on this repository. Include the Android version, the browsers involved and steps to reproduce.

You should get a reply within a week. Fixes ship as a new release, and reporters are credited in the changelog unless they prefer not to be.

## Verifying a download

Official APKs from 1.1.1 on are signed with this certificate (SHA-256):

```text
989ede8b395b6d0b89bcd97fffa6244b0a6dd5638c270527fbeb95d385585702
```

Run `apksigner verify --print-certs Wyelink-*.apk` and compare the digest.

## Supported versions

Only the latest release gets security fixes.
