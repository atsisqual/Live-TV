package com.atsisqual.livetv;

import android.net.Uri;
import android.webkit.WebView;

import java.util.Collections;
import java.util.Set;

/**
 * Isolates provider-specific behaviour from the common WebView playback path.
 *
 * Adapters must only be registered for sources where the app is explicitly allowed
 * to automate the corresponding behaviour. The common/default path is intentionally
 * passive and keeps the provider page untouched.
 */
interface ProviderAdapter {

    enum Capability {
        AUTOSTART,
        AD_SUPPRESSION,
        FULLSCREEN,
        FALLBACK
    }

    String id();

    boolean matches(Uri uri);

    default Set<Capability> capabilities() {
        return Collections.emptySet();
    }

    default void onPageStarted(WebView view, Uri uri) {
        // No-op by default.
    }

    default void onPageReady(WebView view, Uri uri) {
        // No-op by default.
    }

    default void onPageFinished(WebView view, Uri uri) {
        // No-op by default.
    }

    default void onLeaving(WebView view) {
        // No-op by default.
    }
}
