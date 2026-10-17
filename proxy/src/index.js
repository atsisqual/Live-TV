const DROP_RESPONSE_HEADERS = new Set([
  "content-security-policy",
  "content-security-policy-report-only",
  "x-frame-options",
  "content-length",
  "transfer-encoding",
  "connection"
]);

const DROP_REQUEST_HEADERS = new Set([
  "host",
  "connection",
  "content-length",
  "cf-connecting-ip",
  "cf-ipcountry",
  "cf-ray",
  "cf-visitor"
]);

function csv(value) {
  return String(value || "")
    .split(",")
    .map(x => x.trim())
    .filter(Boolean);
}

function lowerCsv(value) {
  return csv(value).map(x => x.toLowerCase());
}

function isAllowedHost(host, env) {
  const allowed = lowerCsv(env.ALLOWED_HOSTS);
  return allowed.includes(String(host || "").toLowerCase());
}

function blockedHost(host, env) {
  const value = String(host || "").toLowerCase();
  if (!value) return false;
  return lowerCsv(env.BLOCKED_HOSTS).some(x => value === x || value.endsWith("." + x));
}

function blockedUrl(raw, base, env) {
  try {
    const u = new URL(raw, base);
    if (blockedHost(u.hostname, env)) return true;
    const patterns = lowerCsv(env.BLOCKED_URL_PATTERNS);
    const href = u.href.toLowerCase();
    return patterns.some(p => href.includes(p));
  } catch {
    return false;
  }
}

function toProxyUrl(target, workerOrigin, env) {
  if (!isAllowedHost(target.hostname, env)) return target.href;
  return `${workerOrigin}/p/${encodeURIComponent(target.hostname)}${target.pathname}${target.search}${target.hash}`;
}

function rewriteAttr(element, attr, baseUrl, workerOrigin, env) {
  const raw = element.getAttribute(attr);
  if (!raw || raw.startsWith("data:") || raw.startsWith("blob:") || raw.startsWith("javascript:")) return;

  let target;
  try {
    target = new URL(raw, baseUrl);
  } catch {
    return;
  }

  if (blockedUrl(target.href, baseUrl, env)) {
    element.remove();
    return;
  }

  if ((target.protocol === "http:" || target.protocol === "https:") && isAllowedHost(target.hostname, env)) {
    element.setAttribute(attr, toProxyUrl(target, workerOrigin, env));
  }
}

function cleaningScript(env) {
  const selectors = csv(env.HIDE_SELECTORS);
  const hostPatterns = lowerCsv(env.BLOCKED_HOSTS);
  const urlPatterns = lowerCsv(env.BLOCKED_URL_PATTERNS);

  const selectorsJson = JSON.stringify(selectors);
  const hostsJson = JSON.stringify(hostPatterns);
  const patternsJson = JSON.stringify(urlPatterns);

  return `<script>(function(){
    const selectors=${selectorsJson};
    const blockedHosts=${hostsJson};
    const blockedPatterns=${patternsJson};
    const blocked=(raw)=>{try{const u=new URL(raw,location.href);const h=u.hostname.toLowerCase();const s=u.href.toLowerCase();return blockedHosts.some(x=>h===x||h.endsWith('.'+x))||blockedPatterns.some(x=>s.includes(x));}catch{return false}};
    const clean=(root=document)=>{
      for(const s of selectors){try{root.querySelectorAll(s).forEach(e=>e.remove())}catch{}}
      root.querySelectorAll?.('a[href],iframe[src],script[src],img[src]').forEach(e=>{
        const v=e.getAttribute('href')||e.getAttribute('src');
        if(v&&blocked(v)) e.remove();
      });
    };
    const nativeOpen=window.open;
    window.open=function(url,...args){if(url&&blocked(url))return null;return nativeOpen.call(window,url,...args)};
    document.addEventListener('click',e=>{const a=e.target.closest?.('a[href]');if(a&&blocked(a.href)){e.preventDefault();e.stopImmediatePropagation();}},true);
    clean();
    new MutationObserver(()=>clean()).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['href','src','style','class']});
  })();</script>`;
}

class RemoveIfBlocked {
  constructor(attr, baseUrl, workerOrigin, env) {
    this.attr = attr;
    this.baseUrl = baseUrl;
    this.workerOrigin = workerOrigin;
    this.env = env;
  }
  element(element) {
    rewriteAttr(element, this.attr, this.baseUrl, this.workerOrigin, this.env);
  }
}

class InjectHead {
  constructor(env) { this.env = env; }
  element(element) {
    element.append(cleaningScript(this.env), { html: true });
  }
}

function cloneRequestHeaders(request) {
  const headers = new Headers();
  for (const [k, v] of request.headers) {
    if (!DROP_REQUEST_HEADERS.has(k.toLowerCase())) headers.set(k, v);
  }
  return headers;
}

function cloneResponseHeaders(source) {
  const headers = new Headers();
  for (const [k, v] of source.headers) {
    if (!DROP_RESPONSE_HEADERS.has(k.toLowerCase())) headers.append(k, v);
  }
  headers.set("access-control-allow-origin", "*");
  headers.set("x-content-type-options", "nosniff");
  headers.set("referrer-policy", "no-referrer");
  return headers;
}

function parseTarget(request, env) {
  const incoming = new URL(request.url);
  if (incoming.pathname === "/health") return { health: true };
  if (!incoming.pathname.startsWith("/p/")) return null;

  const rest = incoming.pathname.slice(3);
  const slash = rest.indexOf("/");
  const rawHost = slash === -1 ? rest : rest.slice(0, slash);
  const path = slash === -1 ? "/" : rest.slice(slash);
  const host = decodeURIComponent(rawHost).toLowerCase();

  if (!isAllowedHost(host, env)) return { forbidden: true, host };
  return { url: new URL(`https://${host}${path}${incoming.search}`) };
}

function rewriteLocation(location, baseUrl, workerOrigin, env) {
  if (!location) return null;
  try {
    const u = new URL(location, baseUrl);
    if (blockedUrl(u.href, baseUrl, env)) return null;
    if (!isAllowedHost(u.hostname, env)) return null;
    return toProxyUrl(u, workerOrigin, env);
  } catch {
    return null;
  }
}

async function proxy(request, env) {
  const parsed = parseTarget(request, env);
  if (!parsed) return new Response("Use /p/<authorized-host>/<path>", { status: 400 });
  if (parsed.health) {
    return Response.json({ ok: true, allowedHosts: lowerCsv(env.ALLOWED_HOSTS).length });
  }
  if (parsed.forbidden) return new Response("Host not authorized", { status: 403 });

  const target = parsed.url;
  const incoming = new URL(request.url);
  const workerOrigin = incoming.origin;

  const init = {
    method: request.method,
    headers: cloneRequestHeaders(request),
    redirect: "manual"
  };
  if (!['GET','HEAD'].includes(request.method)) init.body = request.body;

  let upstream;
  try {
    upstream = await fetch(target.href, init);
  } catch (e) {
    return new Response(`Upstream fetch failed: ${e.message}`, { status: 502 });
  }

  if (upstream.status >= 300 && upstream.status < 400) {
    const next = rewriteLocation(upstream.headers.get("location"), target, workerOrigin, env);
    if (!next) return new Response("Redirect blocked", { status: 502 });
    return new Response(null, { status: upstream.status, headers: { location: next, "cache-control": "no-store" } });
  }

  const headers = cloneResponseHeaders(upstream.headers);
  const type = (upstream.headers.get("content-type") || "").toLowerCase();

  if (type.includes("text/html")) {
    let rewriter = new HTMLRewriter()
      .on("head", new InjectHead(env))
      .on("a[href]", new RemoveIfBlocked("href", target, workerOrigin, env))
      .on("iframe[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("script[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("img[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("link[href]", new RemoveIfBlocked("href", target, workerOrigin, env))
      .on("source[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("video[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("audio[src]", new RemoveIfBlocked("src", target, workerOrigin, env))
      .on("form[action]", new RemoveIfBlocked("action", target, workerOrigin, env));

    for (const selector of csv(env.HIDE_SELECTORS)) {
      try {
        rewriter = rewriter.on(selector, { element(e) { e.remove(); } });
      } catch {}
    }

    const transformed = rewriter.transform(upstream);
    return new Response(transformed.body, { status: upstream.status, headers });
  }

  return new Response(upstream.body, { status: upstream.status, headers });
}

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET,HEAD,POST,PUT,PATCH,DELETE,OPTIONS",
          "access-control-allow-headers": "*"
        }
      });
    }
    return proxy(request, env);
  }
};
