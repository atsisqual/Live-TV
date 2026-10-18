import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class RdembedPopupDiagnosticsTests(unittest.TestCase):
    def test_rdembed_probe_records_popup_attempts(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        self.assertIn("v2.rdembed.sbs", source)
        self.assertIn("popup:gesture", source)
        self.assertIn("popup:script", source)
        self.assertIn("popup:link", source)
        self.assertIn("originalOpen.apply(window,arguments)", source)

    def test_webview_popup_policy_is_still_unchanged(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("s.setJavaScriptCanOpenWindowsAutomatically(false);", source)
        self.assertIn("s.setSupportMultipleWindows(false);", source)
        self.assertIn("public boolean onCreateWindow", source)
        self.assertIn("return false;", source)

    def test_release_version(self):
        gradle = (ROOT / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 51", gradle)
        self.assertIn("versionName '1.6.24'", gradle)


if __name__ == "__main__":
    unittest.main()
