package com.atsisqual.livetv;

import android.net.Uri;

import java.net.IDN;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Declared provider profiles for channel-specific behavior.
 *
 * The channel JSON selects a profile explicitly. This registry is intentionally
 * compiled into the app so imported JSON cannot redefine which domains a profile
 * may act on. Unknown or mismatched profiles resolve to generic.
 */
final class ProviderProfiles {

    static final String GENERIC = "generic";
    static final String APITOTAL_TV001 = "apitotal_tv001";
    static final String RDEMBED_V2 = "rdembed_v2";
    static final String WIDEIPTV_DEFAULT = "wideiptv_default";

    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList(
            GENERIC,
            APITOTAL_TV001,
            RDEMBED_V2,
            WIDEIPTV_DEFAULT
    ));

    private ProviderProfiles() {}

    static String normalize(String raw) {
        String value = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        value = value.replaceAll("[^a-z0-9_-]+", "_")
                .replaceAll("^_+|_+$", "");
        return KNOWN.contains(value) ? value : GENERIC;
    }

    static String resolveForSource(String rawProfile, String sourceUrl) {
        String profile = normalize(rawProfile);
        if (GENERIC.equals(profile)) return GENERIC;
        return allowsSource(profile, sourceUrl) ? profile : GENERIC;
    }

    static boolean allowsSource(String profile, String rawUrl) {
        Uri uri = safeHttpUri(rawUrl);
        if (uri == null) return false;
        String host = normalizedHost(uri);
        if (host == null) return false;
        String path = uri.getPath() == null ? "" : uri.getPath();

        switch (normalize(profile)) {
            case APITOTAL_TV001:
                return domainMatches(host, "apitotal.net") && path.startsWith("/0112/tv001/");
            case RDEMBED_V2:
                return "v2.rdembed.sbs".equals(host);
            case WIDEIPTV_DEFAULT:
                return domainMatches(host, "wideiptv.top");
            case GENERIC:
            default:
                return true;
        }
    }

    static boolean allowsPage(String profile, String rawUrl) {
        Uri uri = safeHttpUri(rawUrl);
        if (uri == null) return false;
        String host = normalizedHost(uri);
        if (host == null) return false;

        switch (normalize(profile)) {
            case APITOTAL_TV001:
                return domainMatches(host, "apitotal.net");
            case RDEMBED_V2:
                return "v2.rdembed.sbs".equals(host);
            case WIDEIPTV_DEFAULT:
                return domainMatches(host, "wideiptv.top");
            case GENERIC:
            default:
                return false;
        }
    }

    private static Uri safeHttpUri(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl.trim());
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) return null;
            return uri;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String normalizedHost(Uri uri) {
        String host = uri == null ? null : uri.getHost();
        if (host == null || host.trim().isEmpty()) return null;
        host = host.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return host;
        }
    }

    private static boolean domainMatches(String host, String domain) {
        return host != null && (host.equals(domain) || host.endsWith("." + domain));
    }
}
