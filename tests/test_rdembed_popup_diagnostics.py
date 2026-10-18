import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


class ProviderDiagnosticsTests(unittest.TestCase):
    def test_deep_provider_dom_diagnostics_are_removed(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")

        for marker in (
            "evaluateJavascript",
            "addJavascriptInterface",
            "JavascriptInterface",
            "frame.contentDocument",
            "frame.contentWindow",
            "frame-depth-",
            "controls-present-depth-",
            "video-present-depth-",
            "center-play-btn",
            "play-click",
            "ad-visible",
            "ad-hidden",
            "MutationObserver",
            "PerformanceObserver",
            "window.open",
        ):
            self.assertNotIn(marker, source)

    def test_native_diagnostics_remain_hostname_only(self):
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
        self.assertIn("normalizedHostValue", diagnostics)
        self.assertNotIn("getRequestHeaders", main)
        self.assertNotIn("getRequestHeaders", diagnostics)
        self.assertNotIn("CookieManager.getCookie", diagnostics)
        self.assertNotIn("getQueryParameter", diagnostics)

    def test_http_network_and_ui_state_diagnostics_are_redacted(self):
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

    def test_renderer_and_webview_visibility_are_observed_without_actions(self):
        diagnostics = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")

        self.assertIn("setWebViewRenderProcessClient", diagnostics)
        self.assertIn("onRenderProcessUnresponsive", diagnostics)
        self.assertIn("onRenderProcessResponsive", diagnostics)
        self.assertIn('"renderer-unresponsive"', diagnostics)
        self.assertIn('"renderer-responsive"', diagnostics)
        self.assertIn("addOnGlobalLayoutListener", diagnostics)
        self.assertIn('"web-visible"', diagnostics)
        self.assertIn('"web-hidden"', diagnostics)
        self.assertIn("removeOnGlobalLayoutListener", diagnostics)
        self.assertNotIn("renderer.terminate", diagnostics)
        self.assertNotIn("terminate()", diagnostics)

    def test_diagnostics_do_not_click_block_or_modify_player(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        for marker in (
            "play.click()",
            "preventDefault()",
            "stopImmediatePropagation()",
            ".remove()",
            "reload()",
            "loadUrl(",
        ):
            self.assertNotIn(marker, source)

    def test_adapter_diagnostics_are_coarse_and_reportable(self):
        diagnostics = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "ProviderDiagnostics.java").read_text(encoding="utf-8")
        self.assertIn("recordAdapterDiagnosticIfActive", diagnostics)
        self.assertIn("normalizedAdapterState", diagnostics)
        self.assertIn("adapter-*", diagnostics)
        self.assertIn("same-origin", diagnostics)
        self.assertIn("cross-origin", diagnostics)
        for state in (
            "native-center-tap-scheduled",
            "native-center-tap-deferred",
            "native-center-tap-dispatched",
            "native-center-tap-unhandled",
        ):
            self.assertIn(state, diagnostics)
        self.assertNotIn("getRequestHeaders", diagnostics)
        self.assertNotIn("CookieManager.getCookie", diagnostics)
        self.assertNotIn("getQueryParameter", diagnostics)

    def test_native_diagnostics_do_not_change_request_policy(self):
        main = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("if (isBlocked(uri)) return emptyResponse();", main)
        self.assertIn("return super.shouldInterceptRequest(view, request);", main)

    def test_webview_popup_policy_is_still_unchanged(self):
        source = (ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "atsisqual" / "livetv" / "MainActivity.java").read_text(encoding="utf-8")
        self.assertIn("s.setJavaScriptCanOpenWindowsAutomatically(false);", source)
        self.assertIn("s.setSupportMultipleWindows(false);", source)
        self.assertIn("public boolean onCreateWindow", source)
        self.assertIn("return false;", source)

    def test_release_version(self):
        gradle = (ROOT / "android" / "app" / "build.gradle").read_text(encoding="utf-8")
        self.assertIn("versionCode 62", gradle)
        self.assertIn("versionName '1.6.35'", gradle)


if __name__ == "__main__":
    unittest.main()
