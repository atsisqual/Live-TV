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

    def test_rdembed_frame_map_and_generic_clicks_are_observed(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        for marker in (
            "frame-depth-",
            "same-origin",
            "cross-origin",
            "inaccessible",
            "controls-present-depth-",
            "video-present-depth-",
            "classifyClickTarget",
            "click('+clickKind+')-depth-",
            "frameSourceHost",
        ):
            self.assertIn(marker, source)
        self.assertIn("frame.contentDocument", source)
        self.assertIn("frame.contentWindow", source)
        self.assertIn("frame.getAttribute('src')", source)

    def test_existing_rdembed_media_states_remain_observed(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        for marker in (
            "frame-ready",
            "play-visible",
            "play-click",
            "ad-visible",
            "ad-hidden",
            "video-created",
            "media-loadedmetadata",
            "media-canplay",
            "media-playing",
            "media-waiting",
            "media-stalled",
            "media-error",
        ):
            self.assertIn(marker, source)

    def test_native_rdembed_diagnostics_are_hostname_only(self):
        main = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        synced = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "SyncedEpgMainActivity.java").read_text(encoding="utf-8")
        diagnostics = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")

        self.assertIn("onLiveTvResourceObserved(host, classifyObservedResource(uri, request.isForMainFrame()))", main)
        self.assertIn("onLiveTvUserTouch(host)", main)
        self.assertIn("recordNativeResource(kind, host)", synced)
        self.assertIn("recordUserTouch(host)", synced)
        self.assertIn("nativePhase", diagnostics)
        self.assertIn("native-", diagnostics)
        self.assertIn("user-touch", diagnostics)
        self.assertNotIn("getRequestHeaders", main)
        self.assertNotIn("getRequestHeaders", diagnostics)
        self.assertNotIn("CookieManager.getCookie", diagnostics)
        self.assertNotIn("getQueryParameter", diagnostics)

    def test_rdembed_http_network_and_ui_state_diagnostics_are_redacted(self):
        main = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        diagnostics = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")

        self.assertIn("recordRequestErrorIfActive", main)
        self.assertIn('"http-" + errorResponse.getStatusCode()', main)
        self.assertIn('"net(" + error.getErrorCode() + ")"', main)
        self.assertIn("recordUiStateIfActive", main)
        self.assertIn('"fullscreen-enter"', main)
        self.assertIn('"fullscreen-exit"', main)
        self.assertIn('"window-focus"', main)
        self.assertIn('"window-blur"', main)

        self.assertIn("activeInstance", diagnostics)
        self.assertIn("normalizedErrorKind", diagnostics)
        self.assertIn("normalizedUiState", diagnostics)
        self.assertIn("native-main-", diagnostics)
        self.assertIn("native-http/native-net", diagnostics)
        self.assertIn("ui-*", diagnostics)
        self.assertNotIn("getRequestHeaders", diagnostics)
        self.assertNotIn("CookieManager.getCookie", diagnostics)
        self.assertNotIn("getQueryParameter", diagnostics)

    def test_native_diagnostics_do_not_change_request_policy(self):
        main = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("if (isBlocked(uri)) return emptyResponse();", main)
        self.assertIn("return super.shouldInterceptRequest(view, request);", main)
        self.assertIn("return false;", main)

    def test_diagnostics_do_not_click_or_block_rdembed_player(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        self.assertNotIn("play.click()", source)
        self.assertNotIn("preventDefault()", source)
        self.assertNotIn("stopImmediatePropagation()", source)
        self.assertNotIn("remove()", source)
        self.assertNotIn("getRequestHeaders", source)
        self.assertNotIn("CookieManager.getCookie", source)

    def test_webview_popup_policy_is_still_unchanged(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("s.setJavaScriptCanOpenWindowsAutomatically(false);", source)
        self.assertIn("s.setSupportMultipleWindows(false);", source)
        self.assertIn("public boolean onCreateWindow", source)
        self.assertIn("return false;", source)

    def test_release_version(self):
        gradle = (ROOT / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 55", gradle)
        self.assertIn("versionName '1.6.28'", gradle)


if __name__ == "__main__":
    unittest.main()
