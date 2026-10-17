package com.atsisqual.livetv;

import android.app.Activity;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;

import java.io.ByteArrayInputStream;
import java.net.IDN;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class MainActivity extends Activity {

    /*
     * 1.3.6 uses targeted ad/tracker blocking only.
     * Player infrastructure must never be placed in this host blacklist because
     * several providers depend on rotating/nested domains and on the Referer header.
     */
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
            "protrafficinspector.com"
    );

    private FrameLayout root;
    private WebView web;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        root = new FrameLayout(this);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        configureWebView();
        web.loadUrl("file:///android_asset/index.html");
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        /* Runs before page scripts in matching frames, including nested cross-origin iframes. */
        AdCleaner.install(web);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isBlocked(uri)) return true;

                /* Keep third-party frames from replacing the local application page. */
                if (request.isForMainFrame() && isHttp(uri) && !isRtp(uri)) return true;
                return false;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                /* Old callback has no isForMainFrame(), therefore only explicit ads are denied. */
                return isBlocked(Uri.parse(url));
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
                /* No secondary WebView/window: kills classic popup/popunder advertising. */
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
                web.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }

            @Override
            public void onHideCustomView() {
                exitFullscreen();
            }
        });
    }

    private boolean isHttp(Uri uri) {
        if (uri == null || uri.getScheme() == null) return false;
        String scheme = uri.getScheme();
        return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
    }

    private boolean isRtp(Uri uri) {
        String host = normalizedHost(uri);
        return host != null && domainMatches(host, "rtp.pt");
    }

    private boolean isBlocked(Uri uri) {
        String host = normalizedHost(uri);
        if (host == null) return false;

        if (matchesAny(host, BLOCKED_HOSTS)) return true;

        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        String query = uri.getQuery() == null ? "" : uri.getQuery().toLowerCase(Locale.ROOT);

        /* EasyList documents this affiliate URL as popup advertising. */
        if ("s.click.aliexpress.com".equals(host)
                && path.startsWith("/e/")
                && query.contains("dp=")) {
            return true;
        }

        /* Same-origin image observed on the sportsonliine wrapper. */
        if (path.endsWith("/sccfwxq.png")) return true;

        /* EasyList/uBlock documented DaddyLive/DLHD ad banner. */
        if (path.endsWith("/rs4k-adbanner.html")) return true;

        /* Tracker observed on the sportsonliine wrapper. */
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
        web.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen != null) {
            exitFullscreen();
            return;
        }
        if (web.canGoBack()) {
            web.goBack();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
        }
        super.onDestroy();
    }
}
