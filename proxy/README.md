# Live TV Clean Proxy

Cloudflare Worker for **sites/players you own or are authorized to proxy**. It is intentionally deny-by-default: only exact hosts listed in `ALLOWED_HOSTS` are reachable.

## What it does

- Proxies authorized HTTPS hosts through `/p/<host>/<path>`.
- Blocks redirects to non-authorized hosts.
- Removes HTML elements that point to `BLOCKED_HOSTS` / `BLOCKED_URL_PATTERNS`.
- Removes configured `HIDE_SELECTORS` from initial HTML.
- Injects a `MutationObserver` that removes matching overlays/ads created later by JavaScript.
- Suppresses blocked `window.open()` calls and blocked ad-link clicks.
- Rewrites static HTML asset URLs for authorized hosts back through the Worker.

## Deploy

1. Create a Cloudflare account / Workers project.
2. Copy the example config:

   ```bash
   cd proxy
   cp wrangler.toml.example wrangler.toml
   ```

3. Edit `wrangler.toml` and replace the example hosts/selectors with domains you control or are authorized to proxy.
4. Install and authenticate Wrangler:

   ```bash
   npm install
   npx wrangler login
   ```

5. Test locally:

   ```bash
   npm run dev
   ```

6. Deploy:

   ```bash
   npm run deploy
   ```

7. Verify:

   ```text
   https://<worker>.workers.dev/health
   ```

   Then load an authorized page:

   ```text
   https://<worker>.workers.dev/p/player.example.com/path/to/page
   ```

## Configuration

`ALLOWED_HOSTS`
: Exact authorized HTTPS hosts. This is the SSRF / abuse boundary. Do not use `*`.

`BLOCKED_HOSTS`
: Ad/tracker/affiliate hosts to remove and block.

`BLOCKED_URL_PATTERNS`
: Case-insensitive URL substrings to block.

`HIDE_SELECTORS`
: Comma-separated CSS selectors to remove from the HTML and keep removing with a `MutationObserver`.

Example:

```toml
[vars]
ALLOWED_HOSTS = "player.example.com,static.example.com"
BLOCKED_HOSTS = "ads.example.com,tracker.example.com,s.click.aliexpress.com"
BLOCKED_URL_PATTERNS = "/ads/,affiliate,tracking"
HIDE_SELECTORS = ".ad-overlay,.advertisement,[data-ad-slot]"
```

## Browser integration

If the Worker URL is:

```text
https://clean-proxy.example.workers.dev
```

and an authorized player is:

```text
https://player.example.com/watch/123
```

embed:

```html
<iframe
  sandbox="allow-scripts allow-same-origin allow-forms allow-presentation allow-pointer-lock"
  src="https://clean-proxy.example.workers.dev/p/player.example.com/watch/123">
</iframe>
```

## Limitations

This handles normal HTML, static asset URLs, redirects and dynamic DOM overlays well. Highly complex sites may additionally require rewriting JavaScript-generated absolute/root-relative requests, WebSockets, cookies, CSP assumptions, or signed URLs. Add those only for sites you are authorized to modify/proxy.
