package com.atsisqual.livetv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class EpgMainActivity extends MainActivity {

    private static final String HOME_URL = "file:///android_asset/index.html";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private String epgScript;

    private final Runnable injector = new Runnable() {
        @Override
        public void run() {
            try {
                if (webView == null) webView = findWebView(getWindow().getDecorView());
                if (webView != null) {
                    String url = webView.getUrl();
                    if (url != null && url.startsWith(HOME_URL)) {
                        webView.evaluateJavascript(loadEpgScript(), null);
                    }
                }
            } catch (Exception ignored) {
                // EPG enhancement must never interfere with channel playback.
            } finally {
                handler.postDelayed(this, 1200);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        handler.postDelayed(injector, 700);
    }

    private WebView findWebView(View view) {
        if (view instanceof WebView) return (WebView) view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            WebView found = findWebView(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    private String loadEpgScript() throws Exception {
        if (epgScript != null) return epgScript;
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open("epg.js"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        epgScript = out.toString();
        return epgScript;
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(injector);
        super.onDestroy();
    }
}
