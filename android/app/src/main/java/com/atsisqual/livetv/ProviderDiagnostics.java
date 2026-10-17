package com.atsisqual.livetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Best-effort provider-chain diagnostics for the WebView player.
 *
 * Privacy/safety invariant: only hostnames, event categories and elapsed timings are
 * retained. Paths, query strings, fragments, media endpoints and request headers are
 * discarded immediately after classification.
 */
final class ProviderDiagnostics {

    private static final String TAG = "LiveTvProvider";
    private static final String LOCAL_HOME = "file:///android_asset/";
    private static final int MAX_EVENTS = 80;

    private final Activity activity;
    private final Object lock = new Object();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    private WebView attachedView;
    private boolean sessionActive;
    private long sessionStartedAt;

    ProviderDiagnostics(Activity activity) {
        this.activity = activity;
    }

    void onPageStarted(WebView view, String url) {
        attach(view);
        if (isLocal(url)) {
            sessionActive = false;
            return;
        }

        synchronized (lock) {
            if (!sessionActive) {
                entries.clear();
                seen.clear();
                sessionStartedAt = SystemClock.elapsedRealtime();
                sessionActive = true;
            }
        }
        record("main-frame", url);
    }

    void onPageReady(WebView view, String url) {
        attach(view);
        if (view == null || isLocal(url)) return;
        injectProbe(view);
    }

    void showReport() {
        activity.runOnUiThread(() -> {
            String report = buildReport();
            new AlertDialog.Builder(activity)
                    .setTitle("Diagnóstico do provider")
                    .setMessage(report)
                    .setNegativeButton("Fechar", null)
                    .setNeutralButton("Copiar", (dialog, which) -> copy(report))
                    .show();
        });
    }

    void destroy() {
        attachedView = null;
        synchronized (lock) {
            entries.clear();
            seen.clear();
            sessionActive = false;
        }
    }

    private void attach(WebView view) {
        if (view == null || attachedView == view) return;
        attachedView = view;
        try {
            view.addJavascriptInterface(new Bridge(), "LiveTvDiag");
        } catch (Exception ignored) {}
    }

    private void injectProbe(WebView view) {
        String script =
                "(function(){try{"
                        + "if(window.__liveTvProviderDiagInstalled)return;"
                        + "window.__liveTvProviderDiagInstalled=true;"
                        + "var sent={};"
                        + "function bucket(k,u){try{var x=new URL(String(u||''),location.href);var p=(x.pathname||'').toLowerCase();"
                        + "if(/\\.m3u8$/.test(p))return 'hls';if(/\\.mpd$/.test(p))return 'dash';"
                        + "if(/\\.(m4s|ts|mp4|webm|aac|mp3)$/.test(p))return 'media';"
                        + "return String(k||'resource').toLowerCase();}catch(e){return String(k||'resource').toLowerCase();}}"
                        + "function send(k,u){try{var s=String(u||'');if(!s)return;var x=new URL(s,location.href);var h=String(x.hostname||'').toLowerCase();if(!h)return;"
                        + "var key=bucket(k,s)+'|'+h;if(sent[key])return;sent[key]=1;"
                        + "if(window.LiveTvDiag&&LiveTvDiag.event)LiveTvDiag.event(String(k||'resource'),s);}catch(e){}}"
                        + "function scan(n){try{if(!n||n.nodeType!==1)return;"
                        + "if(n.matches&&n.matches('iframe[src]'))send('iframe',n.src);"
                        + "if(n.matches&&n.matches('script[src]'))send('script',n.src);"
                        + "if(n.matches&&n.matches('source[src]'))send('media',n.src);"
                        + "if(n.matches&&n.matches('video,audio'))send(n.tagName.toLowerCase(),n.currentSrc||n.src||'');"
                        + "if(n.querySelectorAll)n.querySelectorAll('iframe[src],script[src],source[src],video,audio').forEach(scan);"
                        + "}catch(e){}}"
                        + "scan(document.documentElement);"
                        + "try{(performance.getEntriesByType('resource')||[]).slice(-120).forEach(function(e){send('resource:'+(e.initiatorType||'other'),e.name);});}catch(e){}"
                        + "var po=null,mo=null;"
                        + "try{po=new PerformanceObserver(function(l){l.getEntries().forEach(function(e){send('resource:'+(e.initiatorType||'other'),e.name);});});po.observe({entryTypes:['resource']});}catch(e){}"
                        + "try{mo=new MutationObserver(function(ms){ms.forEach(function(m){m.addedNodes&&m.addedNodes.forEach(scan);});});mo.observe(document.documentElement,{childList:true,subtree:true});}catch(e){}"
                        + "setTimeout(function(){try{if(po)po.disconnect();}catch(e){}try{if(mo)mo.disconnect();}catch(e){}},15000);"
                        + "}catch(e){}})();";
        try {
            view.evaluateJavascript(script, null);
        } catch (Exception ignored) {}
    }

    private void record(String rawKind, String rawUrl) {
        Uri uri;
        try {
            uri = Uri.parse(rawUrl == null ? "" : rawUrl.trim());
        } catch (Exception ignored) {
            return;
        }

        String host = normalizedHost(uri);
        if (host == null || host.isEmpty()) return;
        String kind = classify(rawKind, uri);
        String key = kind + "|" + host;
        long elapsed;

        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS || !seen.add(key)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, kind, host));
        }
        Log.d(TAG, "+" + elapsed + "ms " + kind + " " + host);
    }

    private String classify(String rawKind, Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) return "HLS";
        if (path.endsWith(".mpd")) return "DASH";
        if (path.endsWith(".m4s") || path.endsWith(".ts") || path.endsWith(".mp4")
                || path.endsWith(".webm") || path.endsWith(".aac") || path.endsWith(".mp3")) {
            return "media";
        }

        String kind = rawKind == null ? "resource" : rawKind.trim().toLowerCase(Locale.ROOT);
        if (kind.startsWith("resource:")) kind = kind.substring("resource:".length());
        if (kind.equals("xmlhttprequest")) return "XHR";
        if (kind.equals("fetch")) return "fetch";
        if (kind.equals("iframe")) return "iframe";
        if (kind.equals("script")) return "script";
        if (kind.equals("video") || kind.equals("audio") || kind.equals("media")) return "media";
        if (kind.equals("img") || kind.equals("image")) return "image";
        if (kind.equals("css") || kind.equals("link")) return "style";
        if (kind.equals("main-frame")) return "main-frame";
        return "resource";
    }

    private String buildReport() {
        List<Entry> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(entries);
        }

        if (snapshot.isEmpty()) {
            return "Ainda não há eventos nesta sessão. Abre o canal e espera alguns segundos antes de consultar novamente.";
        }

        StringBuilder out = new StringBuilder();
        out.append("Só são mostrados hostnames; paths e endpoints de media são omitidos.\n\n");
        for (Entry entry : snapshot) {
            out.append('+').append(entry.elapsedMs).append(" ms  ")
                    .append(entry.kind).append("  ")
                    .append(entry.host).append('\n');
        }
        return out.toString().trim();
    }

    private void copy(String report) {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Provider diagnostics", report));
        } catch (Exception ignored) {}
    }

    private boolean isLocal(String url) {
        return url != null && url.startsWith(LOCAL_HOME);
    }

    private String normalizedHost(Uri uri) {
        if (uri == null) return null;
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) return null;
        host = host.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return host;
        }
    }

    private final class Bridge {
        @JavascriptInterface
        public void event(String kind, String url) {
            record(kind, url);
        }
    }

    private static final class Entry {
        final long elapsedMs;
        final String kind;
        final String host;

        Entry(long elapsedMs, String kind, String host) {
            this.elapsedMs = elapsedMs;
            this.kind = kind;
            this.host = host;
        }
    }
}
