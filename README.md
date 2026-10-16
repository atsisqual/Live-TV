# Live-TV

Small utilities for auditing **authorized** live-TV/web-player integrations.

## Embed auditor

`live-tv-embed-audit` crawls pages on domains you explicitly allow and reports embedded player URLs such as `iframe`, `embed`, `object`, `video`, and `source` references.

The crawler deliberately has a narrow boundary:

- it only fetches pages whose host matches `--allow-domain`;
- discovered third-party player/embed URLs are reported but are **not fetched** unless their host is also explicitly allowlisted;
- it does not bypass authentication, browser verification, DRM, referer/origin checks, tokens, or other access controls;
- it is intended for websites you own or are authorized to inspect.

### Install

```bash
python -m pip install -e .
```

### Example

```bash
live-tv-embed-audit \
  https://tv.example.test/?cat=portugal \
  --allow-domain tv.example.test \
  --max-pages 100
```

JSON output:

```bash
live-tv-embed-audit \
  https://tv.example.test/?cat=portugal \
  --allow-domain tv.example.test \
  --json
```

To inspect an authorized player hosted on a second domain, explicitly allow it too:

```bash
live-tv-embed-audit \
  https://tv.example.test/ \
  --allow-domain tv.example.test \
  --allow-domain player.example.test
```

The tool never follows a discovered URL on a non-allowlisted host.
