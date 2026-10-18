package com.atsisqual.livetv;

import android.net.Uri;
import android.webkit.WebView;

/**
 * Keeps one active adapter for the currently loaded top-level provider page.
 */
final class ProviderAdapterSession {

    private ProviderAdapter active = ProviderAdapterRegistry.noOp();
    private String activeUrl = "";

    void onPageStarted(WebView view, String rawUrl) {
        if (!sameUrl(activeUrl, rawUrl)) {
            try {
                active.onLeaving(view);
            } catch (Exception ignored) {}
            active = ProviderAdapterRegistry.resolve(rawUrl);
            activeUrl = rawUrl == null ? "" : rawUrl;
        }
        Uri uri = parse(rawUrl);
        if (uri == null) return;
        try {
            active.onPageStarted(view, uri);
        } catch (Exception ignored) {}
    }

    void onPageReady(WebView view, String rawUrl) {
        Uri uri = parse(rawUrl);
        if (uri == null) return;
        try {
            active.onPageReady(view, uri);
        } catch (Exception ignored) {}
    }

    void onPageFinished(WebView view, String rawUrl) {
        Uri uri = parse(rawUrl);
        if (uri == null) return;
        try {
            active.onPageFinished(view, uri);
        } catch (Exception ignored) {}
    }

    String activeAdapterId() {
        try {
            return active.id();
        } catch (Exception ignored) {
            return "default";
        }
    }

    boolean supports(ProviderAdapter.Capability capability) {
        return ProviderAdapterRegistry.hasCapability(active, capability);
    }

    void destroy(WebView view) {
        try {
            active.onLeaving(view);
        } catch (Exception ignored) {}
        active = ProviderAdapterRegistry.noOp();
        activeUrl = "";
    }

    private boolean sameUrl(String left, String right) {
        String a = left == null ? "" : left.trim();
        String b = right == null ? "" : right.trim();
        return a.equals(b);
    }

    private Uri parse(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            return Uri.parse(rawUrl.trim());
        } catch (Exception ignored) {
            return null;
        }
    }
}
