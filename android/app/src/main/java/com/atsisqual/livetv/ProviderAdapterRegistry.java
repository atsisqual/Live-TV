package com.atsisqual.livetv;

import android.net.Uri;

import java.util.Collections;
import java.util.List;

/**
 * Central registry for provider adapters.
 *
 * Existing providers keep the default passive path unless an explicitly authorized
 * adapter is listed here. v1.6.32 contains one exact-scope authorized adapter for
 * https://v2.rdembed.sbs/assimeportugal.
 */
final class ProviderAdapterRegistry {

    private static final ProviderAdapter NO_OP = new ProviderAdapter() {
        @Override
        public String id() {
            return "default";
        }

        @Override
        public boolean matches(Uri uri) {
            return false;
        }
    };

    private static final List<ProviderAdapter> AUTHORIZED_ADAPTERS =
            Collections.singletonList(new RdembedAssimPortugalAdapter());

    private ProviderAdapterRegistry() {}

    static ProviderAdapter resolve(String rawUrl) {
        Uri uri = parseHttpUri(rawUrl);
        if (uri == null) return NO_OP;

        for (ProviderAdapter adapter : AUTHORIZED_ADAPTERS) {
            if (adapter == null) continue;
            try {
                if (adapter.matches(uri)) return adapter;
            } catch (Exception ignored) {}
        }
        return NO_OP;
    }

    static ProviderAdapter noOp() {
        return NO_OP;
    }

    static boolean hasCapability(ProviderAdapter adapter, ProviderAdapter.Capability capability) {
        if (adapter == null || adapter == NO_OP || capability == null) return false;
        try {
            return adapter.capabilities().contains(capability);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Uri parseHttpUri(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) return null;
        try {
            Uri uri = Uri.parse(rawUrl.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.trim().isEmpty()) return null;
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }
}
