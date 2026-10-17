package com.atsisqual.livetv;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class EpgMainActivity extends MainActivity {

    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final int MAX_XMLTV_BYTES = 12 * 1024 * 1024;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService epgBackground = Executors.newSingleThreadExecutor();
    private WebView webView;
    private String epgScript;
    private String epgUrlScript;
    private boolean bridgeInstalled;

    private final Runnable injector = new Runnable() {
        @Override
        public void run() {
            try {
                if (webView == null) webView = findWebView(getWindow().getDecorView());
                if (webView != null) {
                    if (!bridgeInstalled) {
                        webView.addJavascriptInterface(new EpgBridge(), "LiveTvEpg");
                        bridgeInstalled = true;
                    }
                    String url = webView.getUrl();
                    if (isHome(url)) {
                        webView.evaluateJavascript(loadEpgScript(), null);
                        webView.evaluateJavascript(loadEpgUrlScript(), null);
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

    private boolean isHome(String url) {
        return url != null && url.startsWith(HOME_URL);
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
        if (epgScript == null) epgScript = loadAssetScript("epg.js");
        return epgScript;
    }

    private String loadEpgUrlScript() throws Exception {
        if (epgUrlScript == null) epgUrlScript = loadAssetScript("epg-url.js");
        return epgUrlScript;
    }

    private String loadAssetScript(String name) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(name), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private boolean isValidEpgUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private void sendEpgResult(String requestId, boolean ok, String payload) {
        WebView current = webView;
        if (current == null) return;
        String script = "window.LiveTvEpgNativeResult && window.LiveTvEpgNativeResult("
                + JSONObject.quote(requestId) + ","
                + (ok ? "true" : "false") + ","
                + JSONObject.quote(payload == null ? "" : payload) + ");";
        current.post(() -> {
            if (isHome(current.getUrl())) current.evaluateJavascript(script, null);
        });
    }

    private final class EpgBridge {
        @JavascriptInterface
        public void fetch(String requestId, String value) {
            WebView current = webView;
            if (current == null || !isHome(current.getUrl())) return;
            String id = requestId == null ? "" : requestId.trim();
            String url = value == null ? "" : value.trim();
            if (id.isEmpty()) return;
            if (!isValidEpgUrl(url)) {
                sendEpgResult(id, false, "URL XMLTV inválido ou não HTTPS");
                return;
            }

            epgBackground.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    connection = (HttpURLConnection) new URL(url).openConnection();
                    connection.setConnectTimeout(10_000);
                    connection.setReadTimeout(45_000);
                    connection.setInstanceFollowRedirects(true);
                    connection.setUseCaches(false);
                    connection.setRequestProperty("Accept", "application/xml,text/xml,application/gzip,*/*");
                    connection.setRequestProperty("Accept-Encoding", "gzip");
                    connection.setRequestProperty("User-Agent", "Live-TV/" + BuildConfig.VERSION_NAME);

                    int response = connection.getResponseCode();
                    if (response < 200 || response >= 300) {
                        throw new IllegalStateException("Erro HTTP " + response);
                    }

                    InputStream input = connection.getInputStream();
                    String encoding = connection.getContentEncoding();
                    boolean gzip = (encoding != null && encoding.toLowerCase(Locale.ROOT).contains("gzip"))
                            || url.toLowerCase(Locale.ROOT).endsWith(".gz");
                    if (gzip) input = new GZIPInputStream(input);

                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int total = 0;
                    try (InputStream source = input) {
                        int count;
                        while ((count = source.read(buffer)) != -1) {
                            total += count;
                            if (total > MAX_XMLTV_BYTES) {
                                throw new IllegalStateException("XMLTV demasiado grande (máximo 12 MB)");
                            }
                            output.write(buffer, 0, count);
                        }
                    }

                    String xml = output.toString(StandardCharsets.UTF_8.name());
                    if (!xml.contains("<tv") || !xml.contains("programme")) {
                        throw new IllegalStateException("A resposta não parece ser XMLTV");
                    }
                    sendEpgResult(id, true, xml);
                } catch (Exception e) {
                    String message = e.getMessage();
                    sendEpgResult(id, false,
                            message == null || message.trim().isEmpty()
                                    ? "Não foi possível descarregar o XMLTV"
                                    : message);
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(injector);
        epgBackground.shutdownNow();
        super.onDestroy();
    }
}
