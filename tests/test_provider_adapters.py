import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv"


class ProviderAdapterArchitectureTests(unittest.TestCase):
    def test_adapter_contract_has_explicit_capabilities(self):
        source = (JAVA / "ProviderAdapter.java").read_text(encoding="utf-8")
        for marker in ("AUTOSTART", "AD_SUPPRESSION", "FULLSCREEN", "FALLBACK", "onPageStarted", "onPageReady", "onPageFinished", "onLeaving"):
            self.assertIn(marker, source)
        self.assertNotIn("evaluateJavascript", source)
        self.assertNotIn(".click()", source)
        self.assertNotIn("remove()", source)

    def test_registry_contains_only_the_explicitly_authorized_route_adapter(self):
        source = (JAVA / "ProviderAdapterRegistry.java").read_text(encoding="utf-8")
        self.assertIn("new RdembedAssimPortugalAdapter()", source)
        self.assertIn('return "default"', source)
        for provider in ("apitotal", "comeumamao", "wideiptv", "dlhd"):
            self.assertNotIn(provider, source.lower())

    def test_authorized_adapter_is_exact_scope_and_same_origin_only(self):
        source = (JAVA / "RdembedAssimPortugalAdapter.java").read_text(encoding="utf-8")
        self.assertIn('HOST = "v2.rdembed.sbs"', source)
        self.assertIn('PATH = "/assimeportugal"', source)
        self.assertIn("Capability.AUTOSTART", source)
        self.assertIn("Capability.AD_SUPPRESSION", source)
        self.assertIn("center-play-btn", source)
        self.assertIn("[0,4000,8000]", source)
        self.assertIn("MutationObserver", source)
        self.assertIn("skip ad", source)
        self.assertIn("pular anuncio", source)
        self.assertNotIn("contentDocument", source)
        self.assertNotIn("contentWindow", source)
        self.assertNotIn("getRequestHeaders", source)
        self.assertNotIn("CookieManager", source)
        self.assertNotIn(".m3u8", source)
        self.assertNotIn(".mpd", source)

    def test_launcher_wires_adapter_session_without_replacing_stable_activity(self):
        adapter = (JAVA / "AdapterEpgMainActivity.java").read_text(encoding="utf-8")
        manifest = (ROOT / "android" / "app" / "src" / "main" / "AndroidManifest.xml").read_text(encoding="utf-8")
        self.assertIn("extends SyncedEpgMainActivity", adapter)
        self.assertIn("ProviderAdapterSession", adapter)
        self.assertIn("providerAdapters.onPageStarted", adapter)
        self.assertIn("providerAdapters.onPageReady", adapter)
        self.assertIn("providerAdapters.onPageFinished", adapter)
        self.assertIn('android:name=".AdapterEpgMainActivity"', manifest)
        self.assertIn('android:name=".SyncedEpgMainActivity"', manifest)

    def test_common_main_activity_remains_adapter_agnostic(self):
        main = (JAVA / "MainActivity.java").read_text(encoding="utf-8")
        self.assertNotIn("ProviderAdapterRegistry", main)
        self.assertNotIn("ProviderAdapterSession", main)
        self.assertNotIn("RdembedAssimPortugalAdapter", main)

    def test_release_version(self):
        gradle = (ROOT / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 59", gradle)
        self.assertIn("versionName '1.6.32'", gradle)


if __name__ == "__main__":
    unittest.main()
