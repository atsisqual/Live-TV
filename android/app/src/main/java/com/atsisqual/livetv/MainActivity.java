package com.atsisqual.livetv;

import android.app.Activity;
import android.content.SharedPreferences;
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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
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

public class MainActivity extends Activity {
    private static final String FILTER_URL =
            "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt";
    private static final String FILTER_ASSET = "adguard-dns-filter-bootstrap.txt";
    private static final String FILTER_FILE = "adguard-dns-filter.txt";
    private static final String PREFS = "live_tv_adblock";
    private static final String KEY_FILTER_UPDATED = "filter_updated_at";
    private static final long FILTER_REFRESH_MS = 24L * 60L * 60L * 1000L;

    /* Always active from process start, before the dynamic AdGuard list is refreshed.
       Includes domains observed in the 2026-10-17 Reqable HAR plus known popup/tracker hosts. */
    private static final Set<String> FORCED_BLOCKED_HOSTS = unmodifiableSet(
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

    /* Hosts required by the page/player path. They are protected from broad public-list rules,
       while the forced blacklist above still wins first. */
    private static final Set<String> CORE_PLAYER_HOSTS = unmodifiableSet(
            "apitotal.net",
            "wideiptv.top",
            "sportsonliine.click",
            "rdembed.sbs",
            "tossequip.net",
            "dembed.st",
            "dlive.sx",
            "dlstreams.st",
            "rtp.pt",
            "video-auth7.iol.pt",
            "jwplayer.com",
            "jwpcdn.com",
            "jwpltx.com",
            "groxtv.com",
            "pull-live-156-1.global.ssl.fastly.net"
    );

    private volatile Set<String> adGuardBlockedHosts = Collections.emptySet();
    private volatile Set<String> adGuardAllowedHosts = Collections.emptySet();

    private FrameLayout root;
    private WebView web;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        /* Bootstrap is packaged into the APK by the build. Cached newer copy wins when present. */
        loadBundledFilter();
        loadCachedFilter();
        refreshFilterIfNeeded();

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

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (isBlocked(uri)) return true;

                if (request.isForMainFrame() && isHttp(uri) && !isRtp(uri)) return true;
                return false;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Uri uri = Uri.parse(url);
                if (isBlocked(uri)) return true;
                if (isHttp(uri) && !isRtp(uri)) return true;
                return false;
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
        if (host == null) return false;

        if (matchesAny(host, FORCED_BLOCKED_HOSTS)) return true;
        if (matchesAny(host, CORE_PLAYER_HOSTS)) return false;
        if (matchesAny(host, adGuardAllowedHosts)) return false;
        return matchesAny(host, adGuardBlockedHosts);
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

    private void loadBundledFilter() {
        try (InputStream input = getAssets().open(FILTER_ASSET)) {
            applyFilter(input);
        } catch (Exception ignored) {
            /* The forced HAR-derived list remains active even if an asset is absent/corrupt. */
        }
    }

    private void loadCachedFilter() {
        File file = new File(getFilesDir(), FILTER_FILE);
        if (!file.isFile()) return;
        try (InputStream input = new FileInputStream(file)) {
            applyFilter(input);
        } catch (Exception ignored) {
            /* Keep the bundled filter instead of clearing it on a bad cache. */
        }
    }

    private void refreshFilterIfNeeded() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        long updatedAt = prefs.getLong(KEY_FILTER_UPDATED, 0L);
        File target = new File(getFilesDir(), FILTER_FILE);
        if (target.isFile() && System.currentTimeMillis() - updatedAt < FILTER_REFRESH_MS) return;

        new Thread(() -> {
            HttpURLConnection connection = null;
            File temp = new File(getFilesDir(), FILTER_FILE + ".tmp");
            try {
                connection = (HttpURLConnection) new URL(FILTER_URL).openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(20000);
                connection.setInstanceFollowRedirects(true);
                connection.setRequestProperty("User-Agent", "Live-TV/1.3.4 Android");

                int status = connection.getResponseCode();
                if (status < 200 || status >= 300) return;

                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(temp)) {
                    byte[] buffer = new byte[16384];
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                    output.flush();
                }

                try (InputStream input = new FileInputStream(temp)) {
                    applyFilter(input);
                }

                if (target.exists() && !target.delete()) return;
                if (!temp.renameTo(target)) return;
                prefs.edit().putLong(KEY_FILTER_UPDATED, System.currentTimeMillis()).apply();
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                if (temp.exists() && !temp.equals(target)) temp.delete();
            }
        }, "AdGuardFilterRefresh").start();
    }

    private void applyFilter(InputStream input) throws Exception {
        Set<String> blocked = new HashSet<>();
        Set<String> allowed = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String raw;
            while ((raw = reader.readLine()) != null) {
                parseRule(raw, blocked, allowed);
            }
        }

        if (blocked.size() < 100) throw new IllegalStateException("AdGuard list too small");

        adGuardBlockedHosts = Collections.unmodifiableSet(blocked);
        adGuardAllowedHosts = Collections.unmodifiableSet(allowed);
    }

    private void parseRule(String raw, Set<String> blocked, Set<String> allowed) {
        String line = raw == null ? "" : raw.trim();
        if (line.isEmpty() || line.startsWith("!") || line.startsWith("[") || line.startsWith("#")) return;

        boolean exception = false;
        if (line.startsWith("@@")) {
            exception = true;
            line = line.substring(2);
        }

        int modifier = line.indexOf('$');
        if (modifier >= 0) line = line.substring(0, modifier);

        String domain = null;
        if (line.startsWith("||")) {
            domain = line.substring(2);
            int cut = firstDelimiter(domain);
            if (cut >= 0) domain = domain.substring(0, cut);
        } else if (line.startsWith("0.0.0.0 ") || line.startsWith("127.0.0.1 ")) {
            int space = line.indexOf(' ');
            domain = line.substring(space + 1).trim();
        } else if (looksLikePlainDomain(line)) {
            domain = line;
        }

        domain = cleanDomain(domain);
        if (domain == null) return;

        if (exception) allowed.add(domain);
        else blocked.add(domain);
    }

    private int firstDelimiter(String value) {
        int best = -1;
        char[] delimiters = {'^', '/', '|', '*'};
        for (char delimiter : delimiters) {
            int at = value.indexOf(delimiter);
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    private boolean looksLikePlainDomain(String value) {
        return value.indexOf('.') > 0
                && value.indexOf('/') < 0
                && value.indexOf('*') < 0
                && value.indexOf('^') < 0
                && value.indexOf('|') < 0
                && value.indexOf(' ') < 0;
    }

    private String cleanDomain(String value) {
        if (value == null) return null;
        String domain = value.trim().toLowerCase(Locale.ROOT);
        while (domain.startsWith(".")) domain = domain.substring(1);
        while (domain.endsWith(".")) domain = domain.substring(0, domain.length() - 1);
        if (domain.isEmpty() || domain.equals("localhost") || domain.indexOf('.') < 0) return null;
        try {
            domain = IDN.toASCII(domain).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return null;
        }
        if (!domain.matches("[a-z0-9._-]+")) return null;
        return domain;
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
