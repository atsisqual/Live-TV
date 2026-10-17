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

    /* Targeted list only. The broad AdGuard DNS list used by 1.3.4 was deliberately
       removed because it also blocked dependencies required by embedded players. */
    private static final Set<String> BLOCKED_HOSTS = unmodifiableSet(
            "aliexpress.com",
            "dlhd.st",
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
            "bluetier.top",
            "rxfexvfreikpo.website",
            "sootoarathus.net",
            "bblaa.com",
            "savantwaxwing.cyou",
            "flushpersist.com",
            "405kk.com",
            "nangasynaxis.cyou",
            "joltieregoizegametic.cyou",
            "sospirochan.cyou",
            "thirstacock.com",
            "peckyspurned.cyou",
            "ylowerhughester.org",
            "thiaminspumous.qpon",
            "skoutydeals.com",
            "cowedd4855ws.sbs",
            "484r.com",
            "ay267.com",
            "connatix.com",
            "luugy.com",
            "realizationnewestfangs.com",
            "llvpn.com",
            "portalfluently.com",
            "protrafficinspector.com",
            "briguefidibus.com",
            "dqqmfckpmfvkk.space",
            "amung.us",
            "serveproxy.com",
            "comeumamao.monster",
            "azureedgeglobalfastlycloudfrontondigitaloceanstackpathcdn.buzz"
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

        /* Executes before page scripts in matching frames, including nested cross-origin iframes. */
        AdCleaner.install(web);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isBlocked(uri)) return true;

                /* Prevent a third-party iframe from taking over the app's top-level page.
                   Sub-frame navigation is allowed unless the destination is explicitly blocked. */
                if (request.isForMainFrame() && isHttp(uri) && !isRtp(uri)) return true;
                return false;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                /* Old WebView callback does not expose isForMainFrame(). Blocking every HTTP URL
                   here broke nested players in 1.3.4, so only explicit ad/tracker hosts are denied. */
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
        return host != null && matchesAny(host, BLOCKED_HOSTS);
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
