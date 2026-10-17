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
     * 1.3.8 keeps player infrastructure intact and blocks only confirmed ad/tracker
     * resources. Imported channels now open as top-level WebView pages so Cloudflare
     * Challenge Pages can run in a supported context instead of a cross-origin iframe.
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

    /* Hosts that are part of the wrapper/player navigation chain and may legitimately
       replace the current top-level page. Unknown top-level HTTP(S) takeovers are denied. */
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
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        /* Keep the default WebView UA stable throughout a Cloudflare challenge. */
        s.setUserAgentString(s.getUserAgentString());

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        AdCleaner.install(web);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isBlocked(uri)) return true;
                if (!request.isForMainFrame() || !isHttp(uri)) return false;

                String current = view.getUrl();

                /* A channel click from our local home page is an explicit user navigation. */
                if (current != null && (current.startsWith("file:///android_asset/")
                        || current.startsWith("about:"))) {
                    return false;
                }

                /* Allow only known player/wrapper top-level navigation after leaving home. */
                String host = normalizedHost(uri);
                return host == null || !matchesAny(host, PLAYER_HOSTS);
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                /* API 23 does not expose isForMainFrame(); only confirmed ad destinations are denied. */
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
                /* Do not create secondary WebViews/windows: classic popup/popunder ads die here. */
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
