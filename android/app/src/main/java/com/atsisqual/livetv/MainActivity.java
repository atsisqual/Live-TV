package com.atsisqual.livetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final String UPDATE_MANIFEST =
            "https://atsisqual.github.io/Live-TV/downloads/version.json";
    private static final String PREFS = "live-tv-settings";
    private static final String PREF_AUTO_UPDATE = "auto-update-enabled";

    private static final Set<String> BLOCKED_HOSTS = unmodifiableSet(
            "histats.com",
            "crwdcntrl.net",
            "popcash.net",
            "popads.net",
            "acscdn.com",
            "acscdn.net",
            "adsco.re",
            "adexchangerapid.com",
            "adbpage.com",
            "usrpubtrk.com",
            "cdn4ads.com",
            "premiumvertising.com",
            "xadsmart.com",
            "profitableratecpmnetwork.com",
            "rtmark.net",
            "asdfix.com",
            "rbtget.com",
            "rbtrgr.com",
            "connatix.com",
            "amung.us",
            "serveproxy.com",
            "protrafficinspector.com",
            "cvjqrgyifkets.online",
            "jashxfsiqkksm.space",
            "fensi.shop"
    );

    private static final Set<String> PLAYER_HOSTS = unmodifiableSet(
            "apitotal.net",
            "reidosembeds.lat",
            "wideiptv.top",
            "sportsonliine.click",
            "sportsonline.st",
            "rdembed.sbs",
            "tossequip.net",
            "xstream.to",
            "dlhd.st",
            "dlive.sx",
            "dlstreams.st",
            "comeumamao.monster",
            "rtp.pt",
            "challenges.cloudflare.com"
    );

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    private FrameLayout root;
    private WebView web;
    private FrameLayout statusOverlay;
    private ProgressBar statusSpinner;
    private TextView statusTitle;
    private TextView statusDetail;
    private Button retryButton;
    private Button homeButton;
    private Button streamBackButton;

    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private boolean mainFrameFailed;
    private boolean updateDialogShown;
    private UpdateInfo pendingUpdate;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(9, 11, 15));

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(9, 11, 15));
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        buildStatusOverlay();
        buildStreamBackButton();
        setContentView(root);

        configureSystemBars();
        configureWebView();
        web.loadUrl(HOME_URL);
        // M3U test app intentionally has no automatic update channel.
    }

    private void buildStatusOverlay() {
        statusOverlay = new FrameLayout(this);
        statusOverlay.setBackgroundColor(Color.rgb(9, 11, 15));
        statusOverlay.setVisibility(View.GONE);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(dp(28), dp(30), dp(28), dp(30));

        statusSpinner = new ProgressBar(this);
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        spinnerParams.bottomMargin = dp(18);
        panel.addView(statusSpinner, spinnerParams);

        statusTitle = new TextView(this);
        statusTitle.setTextColor(Color.WHITE);
        statusTitle.setTextSize(20);
        statusTitle.setGravity(Gravity.CENTER);
        statusTitle.setTypeface(statusTitle.getTypeface(), android.graphics.Typeface.BOLD);
        panel.addView(statusTitle, new LinearLayout.LayoutParams(-1, -2));

        statusDetail = new TextView(this);
        statusDetail.setTextColor(Color.rgb(141, 150, 165));
        statusDetail.setTextSize(14);
        statusDetail.setGravity(Gravity.CENTER);
        statusDetail.setPadding(0, dp(9), 0, dp(20));
        panel.addView(statusDetail, new LinearLayout.LayoutParams(-1, -2));

        retryButton = makeStatusButton("Tentar novamente", true);
        retryButton.setOnClickListener(v -> {
            mainFrameFailed = false;
            showLoading(web.getUrl());
            web.reload();
        });
        panel.addView(retryButton, buttonLayoutParams());

        homeButton = makeStatusButton("Voltar aos canais", false);
        homeButton.setOnClickListener(v -> returnToChannels());
        LinearLayout.LayoutParams homeParams = buttonLayoutParams();
        homeParams.topMargin = dp(9);
        panel.addView(homeButton, homeParams);

        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        statusOverlay.addView(panel, panelParams);
        root.addView(statusOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void buildStreamBackButton() {
        streamBackButton = new Button(this);
        streamBackButton.setText("← Voltar");
        streamBackButton.setAllCaps(false);
        streamBackButton.setTextSize(18);
        streamBackButton.setTextColor(Color.WHITE);
        streamBackButton.setTypeface(streamBackButton.getTypeface(), android.graphics.Typeface.BOLD);
        streamBackButton.setBackgroundColor(Color.rgb(22, 27, 35));
        streamBackButton.setMinHeight(dp(56));
        streamBackButton.setPadding(dp(18), 0, dp(18), 0);
        streamBackButton.setVisibility(View.GONE);
        streamBackButton.setOnClickListener(v -> returnToChannels());

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(132), dp(56), Gravity.TOP | Gravity.START);
        params.setMargins(dp(12), dp(12), 0, 0);
        root.addView(streamBackButton, params);
    }

    private Button makeStatusButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(14);
        button.setAllCaps(false);
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        button.setBackgroundColor(primary ? Color.rgb(240, 242, 245) : Color.rgb(42, 49, 60));
        button.setMinHeight(dp(48));
        return button;
    }

    private LinearLayout.LayoutParams buttonLayoutParams() {
        return new LinearLayout.LayoutParams(dp(220), dp(50));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void configureSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                getWindow().setNavigationBarContrastEnforced(false);
            }

            root.setOnApplyWindowInsetsListener((view, windowInsets) -> {
                if (fullscreen != null) {
                    root.setPadding(0, 0, 0, 0);
                    return windowInsets;
                }

                Insets safe = windowInsets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                root.setPadding(safe.left, safe.top, safe.right, safe.bottom);
                return windowInsets;
            });
            root.requestApplyInsets();
        } else {
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
        }
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUserAgentString(s.getUserAgentString());

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        AdCleaner.install(web);
        web.addJavascriptInterface(new AppSettingsBridge(), "LiveTvSettings");
        web.addJavascriptInterface(new M3uPlayerBridge(), "LiveTvM3u");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isBlocked(uri)) return true;
                if (!request.isForMainFrame() || !isHttp(uri)) return false;

                String current = view.getUrl();
                if (isLocalHome(current) || (current != null && current.startsWith("about:"))) {
                    return false;
                }

                String host = normalizedHost(uri);
                return host == null || !matchesAny(host, PLAYER_HOSTS);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return isBlocked(Uri.parse(url));
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                mainFrameFailed = false;

                if (isLocalHome(url)) {
                    enterHomeMode();
                    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    hideStatus();
                } else {
                    enterStreamMode();
                    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                    showLoading(url);
                }
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                super.onPageCommitVisible(view, url);
                if (!mainFrameFailed) hideStatus();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (!mainFrameFailed) hideStatus();
                applySimpleModeToPage(url);
                if (isLocalHome(url)) maybeShowPendingUpdate();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame()) {
                    mainFrameFailed = true;
                    CharSequence description = error == null ? null : error.getDescription();
                    showError("Não foi possível abrir o canal",
                            description == null ? "Verifica a ligação e tenta novamente." : description.toString());
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                super.onReceivedHttpError(view, request, errorResponse);
                if (!request.isForMainFrame() || errorResponse == null) return;

                int code = errorResponse.getStatusCode();
                if (code >= 500 || code == 404 || code == 410) {
                    mainFrameFailed = true;
                    showError("O canal não respondeu", "Erro HTTP " + code + ". Podes tentar novamente.");
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (isBlocked(request.getUrl())) return emptyResponse();
                return super.shouldInterceptRequest(view, request);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                          android.os.Message resultMsg) {
                return false;
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) {
                    callback.onCustomViewHidden();
                    return;
                }

                fullscreen = view;
                fullscreenCallback = callback;
                statusOverlay.setVisibility(View.GONE);
                web.setVisibility(View.GONE);
                root.setPadding(0, 0, 0, 0);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
                if (streamBackButton != null) {
                    streamBackButton.setVisibility(View.VISIBLE);
                    streamBackButton.bringToFront();
                }
                hideSystemBars();
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private void enterHomeMode() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        if (streamBackButton != null) streamBackButton.setVisibility(View.GONE);
        showSystemBars();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) root.requestApplyInsets();
    }

    private void enterStreamMode() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        if (streamBackButton != null) {
            streamBackButton.setVisibility(View.VISIBLE);
            streamBackButton.bringToFront();
        }
    }

    private void returnToChannels() {
        mainFrameFailed = false;
        hideStatus();
        if (fullscreen != null) exitFullscreen();
        web.loadUrl(HOME_URL);
    }

    private void applySimpleModeToPage(String url) {
        if (url == null) return;

        if (isLocalHome(url)) {
            web.evaluateJavascript(
                    "(function(){var s=document.getElementById('livetv-simple-style');"
                            + "if(!s){s=document.createElement('style');s.id='livetv-simple-style';"
                            + "s.textContent='.livebadge{display:none!important}';"
                            + "(document.head||document.documentElement).appendChild(s);}})();",
                    null);
            return;
        }

        web.evaluateJavascript(
                "(function(){if(window.__liveTvAudioHelper)return;window.__liveTvAudioHelper=true;"
                        + "function p(r){try{(r||document).querySelectorAll('video,audio').forEach(function(m){"
                        + "try{if(!m.dataset.livetvSound){m.muted=false;m.defaultMuted=false;m.volume=1;"
                        + "m.dataset.livetvSound='1';}}catch(e){}});}catch(e){}}"
                        + "p(document);new MutationObserver(function(ms){ms.forEach(function(x){"
                        + "x.addedNodes&&x.addedNodes.forEach(function(n){if(n.nodeType===1){"
                        + "if(n.matches&&n.matches('video,audio')){try{n.muted=false;n.defaultMuted=false;n.volume=1;}catch(e){}}p(n);}});});"
                        + "}).observe(document.documentElement,{childList:true,subtree:true});})();",
                null);
    }

    private void showLoading(String url) {
        if (fullscreen != null) return;
        String host = "";
        try {
            Uri uri = Uri.parse(url == null ? "" : url);
            String h = normalizedHost(uri);
            if (h != null) host = h;
        } catch (Exception ignored) {}

        statusSpinner.setVisibility(View.VISIBLE);
        statusTitle.setText("A abrir canal…");
        statusDetail.setText(host.isEmpty() ? "A preparar o player" : host);
        retryButton.setVisibility(View.GONE);
        homeButton.setVisibility(View.GONE);
        statusOverlay.setVisibility(View.VISIBLE);
        statusOverlay.bringToFront();
        if (streamBackButton != null) streamBackButton.bringToFront();
    }

    private void showError(String title, String detail) {
        if (fullscreen != null) return;
        statusSpinner.setVisibility(View.GONE);
        statusTitle.setText(title);
        statusDetail.setText(detail);
        retryButton.setVisibility(View.VISIBLE);
        homeButton.setVisibility(View.VISIBLE);
        statusOverlay.setVisibility(View.VISIBLE);
        statusOverlay.bringToFront();
        if (streamBackButton != null) streamBackButton.bringToFront();
    }

    private void hideStatus() {
        statusOverlay.setVisibility(View.GONE);
    }

    private void checkForUpdates() {
        if (!isAutoUpdateEnabled()) return;
        background.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(UPDATE_MANIFEST + "?v=" + System.currentTimeMillis());
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("User-Agent", "Live-TV/" + BuildConfig.VERSION_NAME);

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return;

                StringBuilder json = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                        connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) json.append(line);
                }

                JSONObject obj = new JSONObject(json.toString());
                int versionCode = obj.optInt("versionCode", 0);
                String versionName = obj.optString("versionName", "").trim();
                String apkUrl = obj.optString("apkUrl", "").trim();
                String notes = obj.optString("notes", "").trim();

                if (versionCode <= BuildConfig.VERSION_CODE || !isTrustedUpdateUrl(apkUrl)) return;

                if (!isAutoUpdateEnabled()) return;
                pendingUpdate = new UpdateInfo(versionCode, versionName, apkUrl, notes);
                mainHandler.post(this::maybeShowPendingUpdate);
            } catch (Exception ignored) {
                // Update checks must never interfere with watching TV.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    protected boolean isAutoUpdateEnabled() {
        return false;
    }

    private void setAutoUpdateEnabled(boolean enabled) {
        pendingUpdate = null;
        updateDialogShown = false;
    }

    private final class AppSettingsBridge {
        @JavascriptInterface
        public boolean isAutoUpdateEnabled() {
            return MainActivity.this.isAutoUpdateEnabled();
        }

        @JavascriptInterface
        public void setAutoUpdateEnabled(boolean enabled) {
            mainHandler.post(() -> {
                if (web == null || !isLocalHome(web.getUrl())) return;
                MainActivity.this.setAutoUpdateEnabled(enabled);
            });
        }
    }


    private final class M3uPlayerBridge {
        @JavascriptInterface
        public void play(String payload) {
            final String json = payload == null ? "" : payload;
            mainHandler.post(() -> {
                try {
                    if (web == null || !isLocalHome(web.getUrl())) return;
                    JSONObject item = new JSONObject(json);
                    String url = item.optString("url", "").trim();
                    Uri uri = Uri.parse(url);
                    String scheme = uri.getScheme();
                    if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                        return;
                    }

                    Intent intent = new Intent(MainActivity.this, M3uPlayerActivity.class);
                    intent.putExtra(M3uPlayerActivity.EXTRA_URL, url);
                    intent.putExtra(M3uPlayerActivity.EXTRA_NAME, item.optString("name", "Canal"));
                    intent.putExtra(M3uPlayerActivity.EXTRA_USER_AGENT, item.optString("user_agent", ""));
                    intent.putExtra(M3uPlayerActivity.EXTRA_REFERRER, item.optString("http_referrer", ""));
                    intent.putExtra(M3uPlayerActivity.EXTRA_ORIGIN, item.optString("http_origin", ""));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private boolean isTrustedUpdateUrl(String value) {
        try {
            Uri uri = Uri.parse(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = normalizedHost(uri);
            return domainMatches(host, "raw.githubusercontent.com")
                    || domainMatches(host, "atsisqual.github.io");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void maybeShowPendingUpdate() {
        if (!isAutoUpdateEnabled()) return;
        if (pendingUpdate == null || updateDialogShown || isFinishing()) return;
        if (!isLocalHome(web.getUrl())) return;

        updateDialogShown = true;
        UpdateInfo update = pendingUpdate;
        String version = update.versionName.isEmpty() ? String.valueOf(update.versionCode) : update.versionName;
        String message = "Está disponível a versão " + version + ".";
        if (!update.notes.isEmpty()) message += "\n\n" + update.notes;

        new AlertDialog.Builder(this)
                .setTitle("Nova versão disponível")
                .setMessage(message)
                .setNegativeButton("Mais tarde", null)
                .setPositiveButton("Atualizar", (dialog, which) -> openUpdateUrl(update.apkUrl))
                .show();
    }

    private void openUpdateUrl(String url) {
        try {
            Intent intent = new Intent(this, UpdateActivity.class);
            intent.putExtra(UpdateActivity.EXTRA_APK_URL, url);
            if (pendingUpdate != null) {
                intent.putExtra(UpdateActivity.EXTRA_VERSION,
                        pendingUpdate.versionName.isEmpty()
                                ? String.valueOf(pendingUpdate.versionCode)
                                : pendingUpdate.versionName);
            }
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private boolean isLocalHome(String url) {
        return url != null && url.startsWith("file:///android_asset/");
    }

    private void hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private void showSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) controller.show(WindowInsets.Type.systemBars());
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private boolean isHttp(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isBlocked(Uri uri) {
        String host = normalizedHost(uri);
        if (host == null) return false;

        if (matchesAny(host, BLOCKED_HOSTS)) return true;

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);

        if ("s.click.aliexpress.com".equals(host)
                && path.startsWith("/e/")
                && query.contains("dp=")) {
            return true;
        }

        if (path.endsWith("/sccfwxq.png")) return true;
        if (path.endsWith("/rs4k-adbanner.html")) return true;
        if (domainMatches(host, "sharethis.com") && path.startsWith("/pd/dtscout")) return true;

        return false;
    }

    private String normalizedHost(Uri uri) {
        if (uri == null) return null;
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) return null;
        host = host.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return host;
        }
    }

    private boolean matchesAny(String host, Set<String> rules) {
        String candidate = host;
        while (candidate != null && !candidate.isEmpty()) {
            if (rules.contains(candidate)) return true;
            int dot = candidate.indexOf('.');
            if (dot < 0) break;
            candidate = candidate.substring(dot + 1);
        }
        return false;
    }

    private boolean domainMatches(String host, String domain) {
        if (host == null) return false;
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private WebResourceResponse emptyResponse() {
        return new WebResourceResponse(
                "text/plain",
                "UTF-8",
                204,
                "No Content",
                Collections.emptyMap(),
                new ByteArrayInputStream(new byte[0])
        );
    }

    private static Set<String> unmodifiableSet(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    private void exitFullscreen() {
        if (fullscreen == null) return;

        root.removeView(fullscreen);
        fullscreen = null;
        showSystemBars();
        web.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) root.requestApplyInsets();
        if (streamBackButton != null && !isLocalHome(web.getUrl())) {
            streamBackButton.setVisibility(View.VISIBLE);
            streamBackButton.bringToFront();
        }

        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && fullscreen != null) hideSystemBars();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen != null) {
            exitFullscreen();
            return;
        }
        if (!isLocalHome(web.getUrl())) {
            returnToChannels();
            return;
        }
        if (web.canGoBack()) {
            mainFrameFailed = false;
            hideStatus();
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        background.shutdownNow();
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }

    private static final class UpdateInfo {
        final int versionCode;
        final String versionName;
        final String apkUrl;
        final String notes;

        UpdateInfo(int versionCode, String versionName, String apkUrl, String notes) {
            this.versionCode = versionCode;
            this.versionName = versionName;
            this.apkUrl = apkUrl;
            this.notes = notes;
        }
    }
}
