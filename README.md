# Live-TV

Utilities for auditing **authorized** web-player integrations, plus a small Android viewer for official/public live-TV pages.

## Android app

The Android app lives in `android/` and presents a channel bar above an embedded `WebView` player. It currently includes official RTP Play live pages for:

- RTP 1
- RTP 2
- RTP Notícias
- RTP Memória
- RTP Açores
- RTP Madeira
- RTP África
- RTP Mundo

The app does not extract underlying stream URLs. Navigation stays inside RTP-owned hosts; external links are handed to the device browser.

### Build locally

Requirements: Android SDK 35, Java 17 and Gradle 8.7.

```bash
cd android
gradle :app:assembleDebug
```

The APK is produced at:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

GitHub Actions also builds the debug APK automatically and uploads it as the `Live-TV-debug-apk` workflow artifact.

## Offline embed auditor

`live-tv-embed-audit` inspects a saved HTML file and reports embedded player containers such as `iframe`, `embed`, and `object` references.

It deliberately works offline: it does not crawl websites, fetch discovered player hosts, extract direct media/playlist URLs, or bypass authentication, browser verification, DRM, referer/origin checks, tokens, or other access controls.

### Install

```bash
python -m pip install -e .
```

### Example

Save an authorized page as `page.html`, then run:

```bash
live-tv-embed-audit page.html \
  --base-url https://tv.example.test/category
```

JSON output:

```bash
live-tv-embed-audit page.html \
  --base-url https://tv.example.test/category \
  --json
```
