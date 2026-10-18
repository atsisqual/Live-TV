import json
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
