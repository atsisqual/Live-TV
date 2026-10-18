from __future__ import annotations

import json
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


# 1) Make the shipped external channel catalogue explicitly select the APITOTAL
# profile variant currently used by this repo. Logos remain external to the APK.
catalog_path = ROOT / "data" / "portugal-sports-channels.json"
catalog = json.loads(catalog_path.read_text(encoding="utf-8"))
channels = catalog.get("channels")
if not isinstance(channels, list):
    raise RuntimeError("data/portugal-sports-channels.json has no channels[]")

for channel in channels:
    if not isinstance(channel, dict):
        continue
    raw_url = str(channel.get("url") or "").strip()
    parsed = urlparse(raw_url)
    host = (parsed.hostname or "").lower()
    if (host == "apitotal.net" or host.endswith(".apitotal.net")) and parsed.path.startswith("/0112/tv001/"):
        channel["provider_profile"] = "apitotal_tv001"
    else:
        channel.setdefault("provider_profile", "generic")

catalog_path.write_text(
    json.dumps(catalog, ensure_ascii=False, indent=2) + "\n",
    encoding="utf-8",
)

# 2) Preserve provider_profile through normalize/import/export and notify the
# native layer immediately before a home-card navigation.
logos_path = ROOT / "android" / "app" / "src" / "main" / "assets" / "channel-logos.js"
logos = logos_path.read_text(encoding="utf-8")

marker = "  if (window.__liveTvExternalChannelLogos) return;\n  window.__liveTvExternalChannelLogos = true;\n\n"
profile_js = r'''  if (!window.__liveTvProviderProfileMetadata) {
    window.__liveTvProviderProfileMetadata = true;

    function normalizeProviderProfile(value) {
      var profile = String(value || '').trim().toLowerCase();
      profile = profile.replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '');
      return profile || 'generic';
    }

    var originalNormalizeStored = window.normalizeStored;
    if (typeof originalNormalizeStored === 'function') {
      window.normalizeStored = function (source) {
        var normalized = originalNormalizeStored.apply(this, arguments);
        if (!normalized) return normalized;
        if (source && typeof source === 'object' && !Array.isArray(source)) {
          normalized.provider_profile = normalizeProviderProfile(
            source.provider_profile || source.providerProfile || normalized.provider_profile
          );
        } else if (!normalized.provider_profile) {
          normalized.provider_profile = 'generic';
        }
        return normalized;
      };
    }

    var originalOpenChannel = window.openChannel;
    if (typeof originalOpenChannel === 'function') {
      window.openChannel = function (item) {
        try {
          var source = item || {};
          var profile = normalizeProviderProfile(source.provider_profile);
          if ((!source.provider_profile || profile === 'generic') &&
              typeof loadCustom === 'function' && typeof sameChannel === 'function') {
            var stored = loadCustom();
            var found = stored.find(function (candidate) {
              try { return sameChannel(candidate, source); } catch (ignored) { return false; }
            });
            if (found) profile = normalizeProviderProfile(found.provider_profile);
          }
          if (window.LiveTvProviderProfile &&
              typeof window.LiveTvProviderProfile.select === 'function') {
            window.LiveTvProviderProfile.select(
              profile,
              String(source.source_url || source.url || '')
            );
          }
        } catch (ignored) {}
        return originalOpenChannel.apply(this, arguments);
      };
    }
  }

'''
if "window.__liveTvProviderProfileMetadata" not in logos:
    logos = replace_once(logos, marker, marker + profile_js, "channel-logos profile bootstrap")

logos = replace_once(
    logos,
    "        ['logo', 'logo_dark', 'logo_light'].forEach(function (key) {",
    "        ['logo', 'logo_dark', 'logo_light', 'provider_profile'].forEach(function (key) {",
    "channel-logos import metadata",
)
logos = replace_once(
    logos,
    "        player: x.player || undefined,\n        category: x.category || undefined,",
    "        player: x.player || undefined,\n        provider_profile: x.provider_profile || undefined,\n        category: x.category || undefined,",
    "channel-logos export metadata",
)
logos_path.write_text(logos, encoding="utf-8")

# 3) Mention the new field in the import UI without changing import semantics.
index_path = ROOT / "android" / "app" / "src" / "main" / "assets" / "index.html"
index = index_path.read_text(encoding="utf-8")
index = replace_once(
    index,
    "No JSON podes usar <b>player</b> (1–20) e <b>logo_dark</b>/<b>logo_light</b>.",
    "No JSON podes usar <b>player</b> (1–20), <b>provider_profile</b> e <b>logo_dark</b>/<b>logo_light</b>.",
    "index import hint",
)
index_path.write_text(index, encoding="utf-8")

# 4) Add a native allow-list registry. Declared profile selects behavior; host/path
# only validate that the declared profile may act on the current provider.
profiles_java = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderProfiles.java"
profiles_java.write_text(r'''package com.atsisqual.livetv;

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
''', encoding="utf-8")

# 5) Keep the selected profile synchronized for both home-card navigation and
# native sidebar channel switching. No provider-specific actions are enabled here.
synced_path = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "SyncedEpgMainActivity.java"
synced = synced_path.read_text(encoding="utf-8")

synced = replace_once(
    synced,
    "import android.webkit.WebView;\n",
    "import android.webkit.JavascriptInterface;\nimport android.webkit.WebView;\n",
    "SyncedEpg JavascriptInterface import",
)

synced = replace_once(
    synced,
    "    private String externalLogoScript;\n    private ProviderDiagnostics providerDiagnostics;\n",
    "    private String externalLogoScript;\n    private ProviderDiagnostics providerDiagnostics;\n"
    "    private final Map<String, String> providerProfilesByUrl = new HashMap<>();\n"
    "    private volatile String activeProviderProfile = ProviderProfiles.GENERIC;\n"
    "    private WebView providerProfileBridgeView;\n"
    "    private boolean providerProfileBridgeInstalled;\n",
    "SyncedEpg profile fields",
)

synced = replace_once(
    synced,
    "        if (isHomeUrl(url)) {\n            externalLogoSupportInjected = false;\n            logoHandler.removeCallbacksAndMessages(null);\n        }",
    "        if (isHomeUrl(url)) {\n            externalLogoSupportInjected = false;\n            activeProviderProfile = ProviderProfiles.GENERIC;\n            providerProfilesByUrl.clear();\n            logoHandler.removeCallbacksAndMessages(null);\n        }",
    "SyncedEpg home profile reset",
)

synced = replace_once(
    synced,
    "        super.onLiveTvPageCommitVisible(view, url);\n        if (isHomeUrl(url)) injectExternalLogoSupport(view);",
    "        super.onLiveTvPageCommitVisible(view, url);\n        if (isHomeUrl(url)) {\n            installProviderProfileBridge(view);\n            injectExternalLogoSupport(view);\n        }",
    "SyncedEpg commit bridge install",
)

synced = replace_once(
    synced,
    "        if (isHomeUrl(url)) {\n            injectExternalLogoSupport(view);\n            scheduleImportedLogoSync(view);\n        }",
    "        if (isHomeUrl(url)) {\n            installProviderProfileBridge(view);\n            injectExternalLogoSupport(view);\n            scheduleImportedLogoSync(view);\n        }",
    "SyncedEpg finished bridge install",
)

bridge_methods = r'''
    private void installProviderProfileBridge(WebView view) {
        if (view == null) return;
        providerProfileBridgeView = view;
        if (providerProfileBridgeInstalled) return;
        try {
            view.addJavascriptInterface(new ProviderProfileBridge(), "LiveTvProviderProfile");
            providerProfileBridgeInstalled = true;
        } catch (Exception ignored) {}
    }

    private final class ProviderProfileBridge {
        @JavascriptInterface
        public void select(String profile, String sourceUrl) {
            WebView view = providerProfileBridgeView;
            logoHandler.post(() -> {
                // External pages retain the JS interface, so only the local home is
                // allowed to select a profile for the next navigation.
                if (view == null || !isHomeUrl(view.getUrl())) return;
                activeProviderProfile = ProviderProfiles.resolveForSource(profile, sourceUrl);
            });
        }
    }

    private void rememberProviderProfiles(Map<String, String> profiles) {
        providerProfilesByUrl.clear();
        if (profiles == null) return;
        for (Map.Entry<String, String> entry : profiles.entrySet()) {
            String url = safe(entry.getKey());
            if (url.isEmpty()) continue;
            providerProfilesByUrl.put(
                    url,
                    ProviderProfiles.resolveForSource(entry.getValue(), url)
            );
        }
    }

    private void activateProfileForChannelUrl(String channelUrl) {
        String direct = providerProfilesByUrl.get(channelUrl);
        if (direct != null) {
            activeProviderProfile = direct;
            return;
        }
        try {
            for (Map.Entry<String, String> entry : providerProfilesByUrl.entrySet()) {
                if (matches(entry.getKey(), channelUrl)) {
                    activeProviderProfile = entry.getValue();
                    return;
                }
            }
        } catch (Exception ignored) {}
        activeProviderProfile = ProviderProfiles.GENERIC;
    }

    protected String getActiveProviderProfile() {
        return activeProviderProfile;
    }

    protected boolean activeProfileAllowsPage(String profile, String url) {
        String normalized = ProviderProfiles.normalize(profile);
        return normalized.equals(activeProviderProfile)
                && ProviderProfiles.allowsPage(normalized, url);
    }
'''

synced = replace_once(
    synced,
    "    private boolean isHomeUrl(String url) {\n        return url != null && url.startsWith(HOME_URL);\n    }\n",
    "    private boolean isHomeUrl(String url) {\n        return url != null && url.startsWith(HOME_URL);\n    }\n" + bridge_methods,
    "SyncedEpg bridge methods",
)

synced = replace_once(
    synced,
    "                        + \"return {url:String(x.url||''),logo:String(logo||'')};\"",
    "                        + \"return {url:String(x.url||''),logo:String(logo||''),provider_profile:String(x.provider_profile||'generic')};\"",
    "SyncedEpg capture profile",
)

synced = replace_once(
    synced,
    "                Map<String, String> logos = new HashMap<>();\n                Set<String> preload = new HashSet<>();",
    "                Map<String, String> logos = new HashMap<>();\n                Map<String, String> profiles = new HashMap<>();\n                Set<String> preload = new HashSet<>();",
    "SyncedEpg profile map",
)

synced = replace_once(
    synced,
    "                    String url = item.optString(\"url\", \"\").trim();\n                    String logo = item.optString(\"logo\", \"\").trim();\n                    if (url.isEmpty() || logo.isEmpty()) continue;\n                    logos.put(url, logo);",
    "                    String url = item.optString(\"url\", \"\").trim();\n                    String logo = item.optString(\"logo\", \"\").trim();\n                    String profile = item.optString(\"provider_profile\", \"generic\").trim();\n                    if (!url.isEmpty()) profiles.put(url, profile);\n                    if (url.isEmpty() || logo.isEmpty()) continue;\n                    logos.put(url, logo);",
    "SyncedEpg parse profile",
)

synced = replace_once(
    synced,
    "                if (logos.isEmpty()) return;\n\n                logoPreloader.execute(() -> {",
    "                rememberProviderProfiles(profiles);\n                if (logos.isEmpty()) return;\n\n                logoPreloader.execute(() -> {",
    "SyncedEpg remember profiles",
)

synced = replace_once(
    synced,
    "            int oldIndex = findChannelIndex(channels, currentSelection);\n            int newIndex = findChannelIndex(channels, activeUrl.trim());\n            if (newIndex < 0 || newIndex == oldIndex) return;\n\n            prepareChannelItemReflection(channels.get(newIndex).getClass());\n            String confirmedUrl = safe(channelItemUrlField.get(channels.get(newIndex)));\n            if (confirmedUrl.isEmpty()) return;",
    "            int oldIndex = findChannelIndex(channels, currentSelection);\n            int newIndex = findChannelIndex(channels, activeUrl.trim());\n            if (newIndex < 0) return;\n\n            prepareChannelItemReflection(channels.get(newIndex).getClass());\n            String confirmedUrl = safe(channelItemUrlField.get(channels.get(newIndex)));\n            if (confirmedUrl.isEmpty()) return;\n            activateProfileForChannelUrl(confirmedUrl);\n            if (newIndex == oldIndex) return;",
    "SyncedEpg sidebar profile activation",
)

synced = replace_once(
    synced,
    "        logoHandler.removeCallbacksAndMessages(null);\n        logoPreloader.shutdownNow();",
    "        logoHandler.removeCallbacksAndMessages(null);\n        providerProfilesByUrl.clear();\n        activeProviderProfile = ProviderProfiles.GENERIC;\n        providerProfileBridgeView = null;\n        logoPreloader.shutdownNow();",
    "SyncedEpg destroy profile cleanup",
)

synced_path.write_text(synced, encoding="utf-8")

# 6) Version bump.
gradle_path = ROOT / "android" / "app" / "build.gradle"
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(
    gradle,
    "        // v1.6.21 adds bounded, hostname-only provider-chain diagnostics.\n        versionCode 48\n        versionName '1.6.21'",
    "        // v1.6.22 adds declared provider profiles with host/path safety guards.\n        versionCode 49\n        versionName '1.6.22'",
    "Gradle version bump",
)
gradle_path.write_text(gradle, encoding="utf-8")

# 7) Regression test for the external JSON declaration and runtime guard registry.
test_path = ROOT / "tests" / "test_provider_profiles.py"
test_path.write_text(r'''import json
import unittest
from pathlib import Path
from urllib.parse import urlparse


ROOT = Path(__file__).resolve().parents[1]


class ProviderProfilesTests(unittest.TestCase):
    def test_portugal_catalog_declares_supported_profile(self):
        payload = json.loads((ROOT / "data" / "portugal-sports-channels.json").read_text(encoding="utf-8"))
        channels = payload["channels"]
        self.assertTrue(channels)
        for channel in channels:
            self.assertIn("provider_profile", channel)
            profile = channel["provider_profile"]
            self.assertIn(profile, {"generic", "apitotal_tv001", "rdembed_v2", "wideiptv_default"})
            if profile == "apitotal_tv001":
                parsed = urlparse(channel["url"])
                self.assertEqual(parsed.hostname, "apitotal.net")
                self.assertTrue(parsed.path.startswith("/0112/tv001/"))

    def test_import_export_keeps_provider_profile(self):
        script = (ROOT / "android" / "app" / "src" / "main" / "assets" / "channel-logos.js").read_text(encoding="utf-8")
        self.assertIn("provider_profile", script)
        self.assertIn("LiveTvProviderProfile", script)

    def test_native_registry_has_guarded_profiles(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderProfiles.java").read_text(encoding="utf-8")
        self.assertIn('APITOTAL_TV001 = "apitotal_tv001"', source)
        self.assertIn('RDEMBED_V2 = "rdembed_v2"', source)
        self.assertIn('WIDEIPTV_DEFAULT = "wideiptv_default"', source)
        self.assertIn('/0112/tv001/', source)
        self.assertIn('v2.rdembed.sbs', source)


if __name__ == "__main__":
    unittest.main()
''', encoding="utf-8")

print(f"Updated {len(channels)} channels with declared provider profiles")
