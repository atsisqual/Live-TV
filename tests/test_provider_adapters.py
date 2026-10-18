import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv"


class ProviderAdapterArchitectureTests(unittest.TestCase):
    def test_adapter_contract_has_explicit_capabilities(self):
        source = (JAVA / "ProviderAdapter.java").read_text(encoding="utf-8")
        for marker in ("AUTOSTART", "FULLSCREEN", "FALLBACK", "onPageStarted", "onPageReady", "onPageFinished", "onLeaving"):
            self.assertIn(marker, source)
        self.assertNotIn("evaluateJavascript", source)
        self.assertNotIn(".click()", source)
        self.assertNotIn("remove()", source)

    def test_registry_is_empty_and_no_current_provider_is_automated(self):
        source = (JAVA / "ProviderAdapterRegistry.java").read_text(encoding="utf-8")
        self.assertIn("AUTHORIZED_ADAPTERS = Collections.emptyList()", source)
        self.assertIn('return "default"', source)
        for provider in ("rdembed", "apitotal", "comeumamao", "wideiptv", "dlhd"):
            self.assertNotIn(provider, source.lower())

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

    def test_release_version(self):
        gradle = (ROOT / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 58", gradle)
        self.assertIn("versionName '1.6.31'", gradle)


if __name__ == "__main__":
    unittest.main()
