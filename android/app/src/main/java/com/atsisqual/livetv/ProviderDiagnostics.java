package com.atsisqual.livetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.webkit.WebViewRenderProcess;
import android.webkit.WebViewRenderProcessClient;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Low-overhead WebView diagnostics.
 *
 * Privacy invariant: only normalized hostnames, coarse event categories and elapsed
 * timings are retained. No paths, query strings, fragments, headers, cookies,
 * tokens, response bodies, playlists or media endpoint URLs are stored or shown.
 *
 * The diagnostics never click, remove, hide, skip, reload, terminate or otherwise
 * alter provider/player behaviour.
 */
final class ProviderDiagnostics {

    private static final String TAG = "LiveTvProvider";
    private static final String LOCAL_HOME = "file:///android_asset/";
    private static final int MAX_EVENTS = 80;
    private static final int MAX_RECORDED_TOUCHES = 12;
    private static volatile ProviderDiagnostics activeInstance;

    private final Activity activity;
    private final Object lock = new Object();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    private WebView attachedView;
    private ViewTreeObserver.OnGlobalLayoutListener visibilityListener;
    private int lastWebVisibility = Integer.MIN_VALUE;
    private boolean sessionActive;
    private int nativePhase;
    private int recordedTouches;
    private long sessionStartedAt;
    private String sessionHost = "";

    ProviderDiagnostics(Activity activity) {
        this.activity = activity;
        activeInstance = this;
    }

    static void recordRequestErrorIfActive(String rawHost, String rawKind, boolean mainFrame) {
        ProviderDiagnostics current = activeInstance;
        if (current != null) current.recordRequestError(rawHost, rawKind, mainFrame);
    }

    static void recordUiStateIfActive(String rawState) {
        ProviderDiagnostics current = activeInstance;
        if (current != null) current.recordUiState(rawState);
    }

    void onPageStarted(WebView view, String url) {
        attach(view);
        if (isLocal(url)) {
            synchronized (lock) {
                sessionActive = false;
                nativePhase = 0;
                recordedTouches = 0;
                sessionHost = "";
            }
            return;
        }

        String host = normalizedHost(url);
        if (host == null) return;

        long elapsed;
        synchronized (lock) {
            if (!sessionActive) {
                entries.clear();
                seen.clear();
                sessionStartedAt = SystemClock.elapsedRealtime();
                sessionActive = true;
                nativePhase = 0;
                recordedTouches = 0;
            }
            sessionHost = host;
            if (entries.size() >= MAX_EVENTS || !seen.add("main-frame|" + host)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, "main-frame", host));
        }
        Log.d(TAG, "+" + elapsed + "ms main-frame " + host);
    }

    void onPageReady(WebView view, String url) {
        if (view == null || isLocal(url)) return;
        attach(view);
    }

    void recordNativeResource(String rawKind, String rawHost) {
        String host = normalizedHostValue(rawHost);
        String kind = normalizedNativeKind(rawKind);
        if (host == null || kind == null) return;

        long elapsed;
        String displayKind = "native-" + kind;
        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS) return;
            String key = "native:" + nativePhase + '|' + displayKind + '|' + host;
            if (!seen.add(key)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, displayKind, host));
        }
        Log.d(TAG, "+" + elapsed + "ms " + displayKind + " " + host);
    }

    void recordUserTouch(String rawHost) {
        String host = normalizedHostValue(rawHost);
        if (host == null) return;

        long elapsed = 0L;
        boolean recorded = false;
        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS) return;
            nativePhase++;
            if (recordedTouches < MAX_RECORDED_TOUCHES) {
                recordedTouches++;
                elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
                entries.add(new Entry(elapsed, "user-touch", host));
                recorded = true;
            }
        }
        if (recorded) Log.d(TAG, "+" + elapsed + "ms user-touch " + host);
    }

    private void recordRequestError(String rawHost, String rawKind, boolean mainFrame) {
        String host = normalizedHostValue(rawHost);
        String kind = normalizedErrorKind(rawKind);
        if (host == null || kind == null) return;

        long elapsed;
        String displayKind = (mainFrame ? "native-main-" : "native-") + kind;
        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS) return;
            String key = "native-error:" + nativePhase + '|' + displayKind + '|' + host;
            if (!seen.add(key)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, displayKind, host));
        }
        Log.d(TAG, "+" + elapsed + "ms " + displayKind + " " + host);
    }

    private void recordUiState(String rawState) {
        String state = normalizedUiState(rawState);
        if (state == null) return;

        long elapsed;
        String host;
        String displayKind = "ui-" + state;
        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS) return;
            host = sessionHost;
            if (host == null || host.isEmpty()) return;
            String key = "ui:" + nativePhase + '|' + displayKind;
            if (!seen.add(key)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, displayKind, host));
        }
        Log.d(TAG, "+" + elapsed + "ms " + displayKind + " " + host);
    }

    void showReport() {
        activity.runOnUiThread(() -> {
            String report = buildReport();
            new AlertDialog.Builder(activity)
                    .setTitle("Diagnóstico do player")
                    .setMessage(report)
                    .setNegativeButton("Fechar", null)
                    .setNeutralButton("Copiar", (dialog, which) -> copy(report))
                    .show();
        });
    }

    void destroy() {
        detachViewObserver();
        attachedView = null;
        if (activeInstance == this) activeInstance = null;
        synchronized (lock) {
            entries.clear();
            seen.clear();
            sessionActive = false;
            nativePhase = 0;
            recordedTouches = 0;
            sessionHost = "";
        }
    }

    private void attach(WebView view) {
        if (view == null || attachedView == view) return;
        detachViewObserver();
        attachedView = view;
        installVisibilityObserver(view);
        installRendererObserver(view);
    }

    private void installVisibilityObserver(WebView view) {
        try {
            lastWebVisibility = webVisibility(view);
            visibilityListener = () -> {
                int current = webVisibility(view);
                if (current == lastWebVisibility) return;
                lastWebVisibility = current;
                recordUiState(current == 1 ? "web-visible" : "web-hidden");
            };
            ViewTreeObserver observer = view.getViewTreeObserver();
            if (observer.isAlive()) observer.addOnGlobalLayoutListener(visibilityListener);
        } catch (Exception ignored) {
            visibilityListener = null;
            lastWebVisibility = Integer.MIN_VALUE;
        }
    }

    private void installRendererObserver(WebView view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            view.setWebViewRenderProcessClient(new WebViewRenderProcessClient() {
                @Override
                public void onRenderProcessUnresponsive(WebView view, WebViewRenderProcess renderer) {
                    recordUiState("renderer-unresponsive");
                }

                @Override
                public void onRenderProcessResponsive(WebView view, WebViewRenderProcess renderer) {
                    recordUiState("renderer-responsive");
                }
            });
        } catch (Exception ignored) {}
    }

    private int webVisibility(WebView view) {
        try {
            return view != null && view.getVisibility() == View.VISIBLE && view.isShown() ? 1 : 0;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void detachViewObserver() {
        WebView view = attachedView;
        ViewTreeObserver.OnGlobalLayoutListener listener = visibilityListener;
        visibilityListener = null;
        lastWebVisibility = Integer.MIN_VALUE;
        if (view == null || listener == null) return;
        try {
            ViewTreeObserver observer = view.getViewTreeObserver();
            if (observer.isAlive()) observer.removeOnGlobalLayoutListener(listener);
        } catch (Exception ignored) {}
    }

    private String normalizedNativeKind(String rawKind) {
        if (rawKind == null) return null;
        String value = rawKind.trim();
        if (value.equals("main-frame") || value.equals("resource") || value.equals("media")
                || value.equals("HLS") || value.equals("DASH")) {
            return value;
        }
        return null;
    }

    private String normalizedErrorKind(String rawKind) {
        if (rawKind == null) return null;
        String value = rawKind.trim().toLowerCase(Locale.ROOT);
        if (value.matches("http-[1-5][0-9]{2}")) return value;
        if (value.matches("net\\(-?[0-9]{1,4}\\)")) return value;
        return null;
    }

    private String normalizedUiState(String rawState) {
        if (rawState == null) return null;
        String value = rawState.trim().toLowerCase(Locale.ROOT);
        switch (value) {
            case "fullscreen-enter":
            case "fullscreen-exit":
            case "window-focus":
            case "window-blur":
            case "web-visible":
            case "web-hidden":
            case "renderer-responsive":
            case "renderer-unresponsive":
                return value;
            default:
                return null;
        }
    }

    private String normalizedHost(String rawUrl) {
        try {
            return normalizedHost(Uri.parse(rawUrl == null ? "" : rawUrl.trim()));
        } catch (Exception ignored) {
            return null;
        }
    }

    private String normalizedHost(Uri uri) {
        if (uri == null) return null;
        return normalizedHostValue(uri.getHost());
    }

    private String normalizedHostValue(String rawHost) {
        if (rawHost == null) return null;
        String host = rawHost.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty()) return null;
        try {
            host = IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {}
        if (!host.matches("^[a-z0-9.-]+$")) return null;
        return host;
    }

    private boolean isLocal(String url) {
        return url != null && url.startsWith(LOCAL_HOME);
    }

    private String buildReport() {
        List<Entry> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(entries);
        }

        if (snapshot.isEmpty()) {
            return "Ainda não há eventos nesta sessão. Abre um canal e espera alguns segundos antes de consultar novamente.";
        }

        StringBuilder out = new StringBuilder();
        out.append("Diagnóstico genérico do WebView; não entra no DOM de iframes nem altera o player.\n")
                .append("Só são mostrados hostnames, categorias aproximadas e tempos; paths/endpoints de media são omitidos.\n")
                .append("native-http/native-net mostram apenas hostname + código de erro.\n")
                .append("ui-* mostra fullscreen/foco/visibilidade e responsive/unresponsive do renderer.\n")
                .append("user-touch não guarda coordenadas e não provoca cliques automáticos.\n\n");
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
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("Player diagnostics", report));
            }
        } catch (Exception ignored) {}
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
