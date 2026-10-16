# Live-TV

Small utilities for auditing **authorized** live-TV/web-player integrations.

## Embed auditor

`live-tv-embed-audit` inspects a **local HTML file** and reports player container URLs found in `iframe`, `embed`, and `object` elements.

It deliberately does not crawl websites, fetch discovered player hosts, extract direct media/playlist URLs, or bypass authentication, browser verification, DRM, tokens, referer/origin checks, or other access controls.

### Install

```bash
python -m pip install -e .
```

### Save authorized HTML

Export or save the HTML from a page you own or are authorized to inspect, for example as `page.html`.

### Inspect embeds

```bash
live-tv-embed-audit page.html \
  --base-url https://tv.example.test/category
```

The optional `--base-url` resolves relative paths and marks embeds on another host as external.

JSON output:

```bash
live-tv-embed-audit page.html \
  --base-url https://tv.example.test/category \
  --json
```

Example output:

```text
[iframe/external] https://player.example.test/embed/1
[embed/same-host/unknown] https://tv.example.test/legacy/player.html
```
