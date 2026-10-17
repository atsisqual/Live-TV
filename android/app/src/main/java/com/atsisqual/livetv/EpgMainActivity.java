package com.atsisqual.livetv;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class EpgMainActivity extends MainActivity {

    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final String UPDATE_MANIFEST =
            "https://atsisqual.github.io/Live-TV/downloads/version.json";
    private static final String PREFS = "live-tv-settings";
    private static final String PREF_DIRECT_MODE = "direct-player-mode";
    private static final String PREF_LAST_CHANNEL = "last-channel-url";
    private static final int MAX_XMLTV_BYTES = 12 * 1024 * 1024;
    private static final int MAX_LOGO_BYTES = 768 * 1024;
    private static final int SIDEBAR_WIDTH_DP = 216;
    private static final int MAX_CHANNEL_CAPTURE_RETRIES = 8;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService epgBackground = Executors.newSingleThreadExecutor();
    private final ExecutorService logoBackground = Executors.newFixedThreadPool(3);
    private final List<ChannelItem> channels = new ArrayList<>();
    private final Map<String, Bitmap> logoCache = new ConcurrentHashMap<>();
    private final Map<String, String> bundledLogoDark = new ConcurrentHashMap<>();
    private final Map<String, String> bundledLogoLight = new ConcurrentHashMap<>();
    private boolean bundledLogosLoaded;

    private WebView webView;
    private FrameLayout playerRoot;
    private View statusOverlay;
    private Button legacyBackButton;
    private Button statusHomeButton;

    private LinearLayout sidebar;
    private LinearLayout channelList;
    private ScrollView channelScroll;
    private Button sidebarBackButton;

    private String epgScript;
    private String epgUrlScript;
    private String directModeScript;
    private boolean bridgeInstalled;
    private boolean sidebarBuilt;
    private boolean sidebarShowing;
    private boolean channelCaptureRunning;
    private boolean homeScriptsInjected;
    private boolean directPlayerMode;
    private boolean directLaunchPending;
    private boolean directUpdateDialogShown;
    private int channelCaptureRetryCount;
    private long lastChannelCaptureAt;
    private String selectedChannelUrl = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadBundledChannelLogos();

        directPlayerMode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean(PREF_DIRECT_MODE, false);
        selectedChannelUrl = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(PREF_LAST_CHANNEL, "");
        if (selectedChannelUrl == null) selectedChannelUrl = "";

        if (directPlayerMode && !isValidChannelUrl(selectedChannelUrl)) {
            directPlayerMode = false;
            selectedChannelUrl = "";
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(PREF_DIRECT_MODE, false)
                    .remove(PREF_LAST_CHANNEL)
                    .apply();
        }

        directLaunchPending = directPlayerMode;
        initializeEventDrivenUi();
        if (directPlayerMode && isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);
        if (directLaunchPending) handler.postDelayed(this::launchDirectPlayerFallback, 1100);
    }

    private void initializeEventDrivenUi() {
        ensureWebView();
        ensureBridgeInstalled();
        ensureSidebar();
        applyPersistentPlayerChrome();

        if (webView == null) return;
        String url = webView.getUrl();
        if (isHome(url)) {
            hideSidebar();
        } else {
            showSidebar();
            confirmLoadedChannel(url);
        }
    }

    private void ensureWebView() {
        if (webView == null) webView = findWebView(getWindow().getDecorView());
        if (webView != null && playerRoot == null && webView.getParent() instanceof FrameLayout) {
            playerRoot = (FrameLayout) webView.getParent();
        }
    }

    private void ensureBridgeInstalled() {
        if (bridgeInstalled || webView == null) return;
        webView.addJavascriptInterface(new EpgBridge(), "LiveTvEpg");
        bridgeInstalled = true;
    }

    private void applyPersistentPlayerChrome() {
        if (legacyBackButton != null) legacyBackButton.setVisibility(View.GONE);
        if (sidebarBackButton != null) {
            sidebarBackButton.setVisibility(directPlayerMode ? View.GONE : View.VISIBLE);
        }
        if (statusHomeButton != null && directPlayerMode) {
            statusHomeButton.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onLiveTvPageStarted(WebView view, String url) {
        super.onLiveTvPageStarted(view, url);
        ensureWebView();
        ensureBridgeInstalled();
        ensureSidebar();
        applyPersistentPlayerChrome();

        if (isHome(url)) {
            homeScriptsInjected = false;
            channelCaptureRetryCount = 0;
            hideSidebar();
        } else {
            showSidebar();
            confirmLoadedChannel(url);
        }
    }

    @Override
    protected void onLiveTvPageCommitVisible(WebView view, String url) {
        super.onLiveTvPageCommitVisible(view, url);
        ensureWebView();
        ensureSidebar();
        applyPersistentPlayerChrome();

        if (isHome(url)) {
            hideSidebar();
        } else {
            showSidebar();
            confirmLoadedChannel(url);
        }
    }

    @Override
    protected void onLiveTvPageFinished(WebView view, String url) {
        super.onLiveTvPageFinished(view, url);
        ensureWebView();
        ensureBridgeInstalled();
        ensureSidebar();
        applyPersistentPlayerChrome();

        if (isHome(url)) {
            hideSidebar();
            injectHomeEnhancementsIfNeeded();
        } else {
            showSidebar();
            confirmLoadedChannel(url);
        }
    }

    @Override
    protected void onLiveTvFullscreenChanged(boolean isFullscreen) {
        super.onLiveTvFullscreenChanged(isFullscreen);
        ensureWebView();
        ensureSidebar();
        if (isFullscreen || webView == null || isHome(webView.getUrl())) {
            hideSidebar();
        } else {
            showSidebar();
            confirmLoadedChannel(webView.getUrl());
        }
    }

    private void injectHomeEnhancementsIfNeeded() {
        if (homeScriptsInjected || webView == null || !isHome(webView.getUrl())) return;
        homeScriptsInjected = true;
        channelCaptureRetryCount = 0;

        try {
            String epg = loadEpgScript();
            String epgUrl = loadEpgUrlScript();
            String direct = loadDirectModeScript();

            webView.evaluateJavascript(epg, ignored -> {
                if (webView == null || !isHome(webView.getUrl())) return;
                webView.evaluateJavascript(epgUrl, ignored2 -> {
                    if (webView == null || !isHome(webView.getUrl())) return;
                    webView.evaluateJavascript(direct, ignored3 -> {
                        if (webView == null || !isHome(webView.getUrl())) return;
                        scheduleChannelCaptureRetry(80L);
                    });
                });
            });
        } catch (Exception ignored) {
            homeScriptsInjected = false;
        }
    }

    private void scheduleChannelCaptureRetry(long delayMs) {
        if (channelCaptureRetryCount >= MAX_CHANNEL_CAPTURE_RETRIES) return;
        channelCaptureRetryCount += 1;
        handler.postDelayed(() -> {
            if (webView == null || !isHome(webView.getUrl())) return;
            captureChannelsIfNeeded();
        }, delayMs);
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

    private Button findButtonByText(View view, String text) {
        if (view instanceof Button) {
            CharSequence label = ((Button) view).getText();
            if (label != null && label.toString().contains(text)) return (Button) view;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButtonByText(group.getChildAt(i), text);
            if (found != null) return found;
        }
        return null;
    }

    private void ensureSidebar() {
        if (sidebarBuilt || playerRoot == null) return;

        locateBasePlayerViews();

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(7), dp(7), dp(7), dp(7));
        sidebar.setBackgroundColor(Color.rgb(12, 15, 20));
        sidebar.setVisibility(View.GONE);

        sidebarBackButton = new Button(this);
        sidebarBackButton.setText("← Voltar");
        sidebarBackButton.setAllCaps(false);
        sidebarBackButton.setTextSize(14);
        sidebarBackButton.setTextColor(Color.WHITE);
        sidebarBackButton.setTypeface(sidebarBackButton.getTypeface(), android.graphics.Typeface.BOLD);
        sidebarBackButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        sidebarBackButton.setPadding(dp(11), 0, dp(9), 0);
        sidebarBackButton.setBackground(roundedBackground(Color.rgb(29, 35, 45), Color.rgb(56, 66, 82)));
        sidebarBackButton.setOnClickListener(v -> returnToChannelGrid());
        sidebarBackButton.setVisibility(directPlayerMode ? View.GONE : View.VISIBLE);

        streamChannelListSetup();

        FrameLayout.LayoutParams sidebarParams = new FrameLayout.LayoutParams(
                dp(SIDEBAR_WIDTH_DP),
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.END
        );
        playerRoot.addView(sidebar, sidebarParams);

        sidebarBuilt = true;
        renderChannelList();
    }

    private void streamChannelListSetup() {
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        sidebar.addView(sidebarBackButton, backParams);

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setVerticalScrollBarEnabled(true);
        channelScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelList.setPadding(0, dp(7), 0, dp(4));

        channelScroll.addView(
                channelList,
                new ScrollView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        sidebar.addView(channelScroll, scrollParams);
    }

    private void locateBasePlayerViews() {
        if (playerRoot == null) return;

        for (int i = 0; i < playerRoot.getChildCount(); i++) {
            View child = playerRoot.getChildAt(i);
            if (child == webView) continue;

            if (child instanceof Button) {
                Button button = (Button) child;
                CharSequence label = button.getText();
                if (label != null && label.toString().contains("Voltar")) {
                    legacyBackButton = button;
                    legacyBackButton.setVisibility(View.GONE);
                }
                continue;
            }

            if (statusOverlay == null && child instanceof FrameLayout) {
                statusOverlay = child;
            }
        }

        if (statusHomeButton == null) {
            statusHomeButton = findButtonByText(playerRoot, "Voltar aos canais");
            if (statusHomeButton != null && directPlayerMode) {
                statusHomeButton.setVisibility(View.GONE);
            }
        }
    }

    private void returnToChannelGrid() {
        if (directPlayerMode) return;
        if (legacyBackButton != null) {
            legacyBackButton.performClick();
            return;
        }
        if (webView != null) webView.loadUrl(HOME_URL);
    }

    private void showSidebar() {
        if (!sidebarBuilt || sidebar == null || webView == null) return;

        applyPlayerRightMargin(dp(SIDEBAR_WIDTH_DP));
        if (sidebarBackButton != null) {
            sidebarBackButton.setVisibility(directPlayerMode ? View.GONE : View.VISIBLE);
        }
        if (sidebar.getVisibility() != View.VISIBLE) {
            sidebar.setVisibility(View.VISIBLE);
            sidebarShowing = true;
            renderChannelList();
        }
        sidebar.bringToFront();
    }

    private void hideSidebar() {
        if (webView == null) return;

        applyPlayerRightMargin(0);
        if (sidebar != null && sidebar.getVisibility() != View.GONE) {
            sidebar.setVisibility(View.GONE);
        }
        sidebarShowing = false;
    }

    private void applyPlayerRightMargin(int margin) {
        if (webView != null && webView.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) webView.getLayoutParams();
            if (params.rightMargin != margin) {
                params.rightMargin = margin;
                webView.setLayoutParams(params);
            }
        }

        if (statusOverlay != null && statusOverlay.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) statusOverlay.getLayoutParams();
            if (params.rightMargin != margin) {
                params.rightMargin = margin;
                statusOverlay.setLayoutParams(params);
            }
        }
    }

    private void loadBundledChannelLogos() {
        if (bundledLogosLoaded) return;
        bundledLogosLoaded = true;
        try (InputStream input = getAssets().open("portugal-sports-channels.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            JSONArray items = root.optJSONArray("channels");
            if (items == null) return;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String slug = item.optString("slug", "").trim().toLowerCase(Locale.ROOT);
                if (slug.isEmpty()) continue;
                String dark = item.optString("logo_dark", "").trim();
                String light = item.optString("logo_light", "").trim();
                if (!dark.isEmpty()) bundledLogoDark.put(slug, dark);
                if (!light.isEmpty()) bundledLogoLight.put(slug, light);
            }
        } catch (Exception ignored) {
            // Channel list must remain usable even if optional logos cannot be read.
        }
    }

    private void captureChannelsIfNeeded() {
        if (webView == null || !isHome(webView.getUrl()) || channelCaptureRunning) return;

        long now = System.currentTimeMillis();
        if (now - lastChannelCaptureAt < 250) {
            scheduleChannelCaptureRetry(280L);
            return;
        }

        lastChannelCaptureAt = now;
        channelCaptureRunning = true;

        String script =
                "(function(){try{"
                        + "if(typeof allItems!=='function')return {ready:false,items:[]};"
                        + "var a=allItems();"
                        + "if(typeof sortItems==='function')a=sortItems(a);"
                        + "return {ready:true,items:a.filter(function(x){return x&&x.url;}).map(function(x){"
                        + "return {name:String(x.name||'Canal'),url:String(x.url||''),"
                        + "category:String(x.category||'Canal'),slug:String(x.slug||'')};"
                        + "})};"
                        + "}catch(e){return {ready:false,items:[]};}})()";

        webView.evaluateJavascript(script, value -> {
            channelCaptureRunning = false;
            try {
                JSONObject result = new JSONObject(value == null || value.trim().isEmpty() ? "{}" : value);
                if (!result.optBoolean("ready", false)) {
                    scheduleChannelCaptureRetry(320L);
                    return;
                }

                JSONArray items = result.optJSONArray("items");
                channels.clear();

                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        String name = item.optString("name", "Canal").trim();
                        String url = item.optString("url", "").trim();
                        String category = item.optString("category", "Canal").trim();
                        String slug = item.optString("slug", "").trim().toLowerCase(Locale.ROOT);
                        String logo = bundledLogoDark.getOrDefault(slug, "");
                        if (url.isEmpty()) continue;

                        channels.add(new ChannelItem(
                                name.isEmpty() ? "Canal" : name,
                                url,
                                category.isEmpty() ? "Canal" : category,
                                logo
                        ));
                    }
                }

                renderChannelList();
                maybeLaunchDirectPlayer();
                if (channels.isEmpty()) scheduleChannelCaptureRetry(380L);
            } catch (Exception ignored) {
                scheduleChannelCaptureRetry(380L);
            }
        });
    }

    private void maybeLaunchDirectPlayer() {
        if (!directLaunchPending || !directPlayerMode || webView == null) return;
        if (!isValidChannelUrl(selectedChannelUrl)) return;
        directLaunchPending = false;
        webView.loadUrl(selectedChannelUrl);
    }

    private void launchDirectPlayerFallback() {
        ensureWebView();
        maybeLaunchDirectPlayer();
    }

    private void confirmLoadedChannel(String activeUrl) {
        if (webView == null || activeUrl == null || activeUrl.trim().isEmpty() || isHome(activeUrl)) return;
        String active = activeUrl.trim();

        for (ChannelItem item : channels) {
            if (!matchesActiveChannel(item.url, active)) continue;
            if (!matchesActiveChannel(item.url, selectedChannelUrl)) {
                selectedChannelUrl = item.url;
                persistConfirmedChannel(item.url);
                if (sidebarShowing) renderChannelList();
            }
            return;
        }
    }

    private void persistConfirmedChannel(String value) {
        String url = value == null ? "" : value.trim();
        if (!isValidChannelUrl(url)) return;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_LAST_CHANNEL, url)
                .apply();
    }

    private String currentStreamIdentity() {
        if (webView == null) return "";

        String current = webView.getUrl();
        if (current != null && !current.trim().isEmpty() && !isHome(current)) {
            return current.trim();
        }

        String original = webView.getOriginalUrl();
        if (original != null && !original.trim().isEmpty() && !isHome(original)) {
            return original.trim();
        }

        return "";
    }

    private void renderChannelList() {
        if (channelList == null) return;
        channelList.removeAllViews();

        if (channels.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sem canais na lista");
            empty.setTextColor(Color.rgb(141, 150, 165));
            empty.setTextSize(12);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            channelList.addView(
                    empty,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            return;
        }

        String active = selectedChannelUrl.isEmpty() ? currentStreamIdentity() : selectedChannelUrl;
        int selectedIndex = -1;

        for (int i = 0; i < channels.size(); i++) {
            ChannelItem item = channels.get(i);
            boolean selected = matchesActiveChannel(item.url, active);
            if (selected) selectedIndex = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10), dp(5), dp(9), dp(5));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(roundedBackground(
                    selected ? Color.rgb(66, 26, 36) : Color.rgb(23, 28, 36),
                    selected ? Color.rgb(255, 48, 79) : Color.rgb(41, 49, 61)
            ));

            LinearLayout textWrap = new LinearLayout(this);
            textWrap.setOrientation(LinearLayout.VERTICAL);
            textWrap.setGravity(Gravity.CENTER_VERTICAL);

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(13);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTypeface(
                    name.getTypeface(),
                    selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL
            );
            textWrap.addView(
                    name,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );

            TextView category = new TextView(this);
            category.setText(item.category);
            category.setTextColor(
                    selected ? Color.rgb(255, 167, 180) : Color.rgb(141, 150, 165)
            );
            category.setTextSize(9);
            category.setSingleLine(true);
            category.setEllipsize(android.text.TextUtils.TruncateAt.END);

            LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            categoryParams.topMargin = dp(1);
            textWrap.addView(category, categoryParams);

            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1f
            );
            row.addView(textWrap, textParams);

            if (!item.logo.isEmpty()) {
                ImageView logo = new ImageView(this);
                logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                logo.setAdjustViewBounds(true);
                logo.setPadding(dp(2), dp(2), dp(2), dp(2));
                LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(
                        dp(48),
                        ViewGroup.LayoutParams.MATCH_PARENT
                );
                logoParams.leftMargin = dp(8);
                row.addView(logo, logoParams);
                loadChannelLogo(logo, item.logo);
            }

            row.setOnClickListener(v -> openChannelFromSidebar(item));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(58)
            );
            rowParams.bottomMargin = dp(5);
            channelList.addView(row, rowParams);
        }

        if (selectedIndex >= 0 && channelScroll != null) {
            final int index = selectedIndex;
            channelScroll.post(() -> {
                int target = index * dp(63);
                channelScroll.smoothScrollTo(0, Math.max(0, target - dp(68)));
            });
        }
    }

    private void loadChannelLogo(ImageView view, String value) {
        if (view == null) return;
        String logo = value == null ? "" : value.trim();
        if (logo.isEmpty()) return;

        view.setTag(logo);
        Bitmap cached = logoCache.get(logo);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        if (logo.regionMatches(true, 0, "data:image/", 0, 11)) {
            Bitmap bitmap = decodeDataLogo(logo);
            if (bitmap != null) {
                logoCache.put(logo, bitmap);
                view.setImageBitmap(bitmap);
            }
            return;
        }

        if (!isValidLogoUrl(logo)) return;
        logoBackground.execute(() -> {
            Bitmap bitmap = downloadLogo(logo);
            if (bitmap == null) return;
            logoCache.put(logo, bitmap);
            view.post(() -> {
                Object tag = view.getTag();
                if (tag != null && logo.equals(tag.toString())) view.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap decodeDataLogo(String value) {
        try {
            int comma = value.indexOf(',');
            if (comma <= 0 || !value.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
                return null;
            }
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidLogoUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Bitmap downloadLogo(String value) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("User-Agent", "TV-do-Ze/" + BuildConfig.VERSION_NAME);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            try (InputStream input = connection.getInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_LOGO_BYTES) return null;
                    output.write(buffer, 0, count);
                }
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean matchesActiveChannel(String channelUrl, String activeUrl) {
        if (channelUrl == null || activeUrl == null) return false;

        String left = channelUrl.trim();
        String right = activeUrl.trim();
        if (left.equals(right)) return true;

        try {
            Uri a = Uri.parse(left);
            Uri b = Uri.parse(right);
            if (a.getHost() == null || b.getHost() == null) return false;

            String ah = a.getHost().toLowerCase(Locale.ROOT);
            String bh = b.getHost().toLowerCase(Locale.ROOT);
            String ap = a.getPath() == null ? "" : a.getPath();
            String bp = b.getPath() == null ? "" : b.getPath();

            return ah.equals(bh) && ap.equals(bp);
        } catch (Exception ignored) {
            return false;
        }
    }

    private void openChannelFromSidebar(ChannelItem item) {
        if (webView == null || item == null || item.url.isEmpty()) return;

        // Always load the row that was tapped. If it is already active this is an explicit retry.
        // Selection/persistence is only confirmed by the WebView page-start/commit events.
        webView.loadUrl(item.url);
    }

    private void rememberChannel(String value) {
        String url = value == null ? "" : value.trim();
        if (!isValidChannelUrl(url)) return;
        selectedChannelUrl = url;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putString(PREF_LAST_CHANNEL, url)
                .apply();
    }

    private void enableDirectPlayerMode(String value) {
        String url = value == null ? "" : value.trim();
        if (!isValidChannelUrl(url)) return;

        rememberChannel(url);
        directPlayerMode = true;
        directLaunchPending = false;
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(PREF_DIRECT_MODE, true)
                .putString(PREF_LAST_CHANNEL, url)
                .apply();

        if (sidebarBackButton != null) sidebarBackButton.setVisibility(View.GONE);
        if (statusHomeButton != null) statusHomeButton.setVisibility(View.GONE);
        if (legacyBackButton != null) legacyBackButton.setVisibility(View.GONE);
        if (webView != null) webView.loadUrl(url);
        if (isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);
    }

    private boolean isValidChannelUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value.trim());
            String scheme = uri.getScheme();
            return uri.getHost() != null
                    && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (Exception ignored) {
            return false;
        }
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(11));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String loadEpgScript() throws Exception {
        if (epgScript == null) epgScript = loadAssetScript("epg.js");
        return epgScript;
    }

    private String loadEpgUrlScript() throws Exception {
        if (epgUrlScript == null) epgUrlScript = loadAssetScript("epg-url.js");
        return epgUrlScript;
    }

    private String loadDirectModeScript() throws Exception {
        if (directModeScript == null) directModeScript = loadAssetScript("direct-mode.js");
        return directModeScript;
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

    private void checkDirectModeUpdate() {
        if (!isAutoUpdateEnabled()) return;
        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;

        epgBackground.execute(() -> {
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

                handler.post(() -> showDirectModeUpdate(versionCode, versionName, apkUrl, notes));
            } catch (Exception ignored) {
                // Update checks must never interrupt playback.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void showDirectModeUpdate(int versionCode, String versionName, String apkUrl, String notes) {
        if (!isAutoUpdateEnabled()) return;
        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;
        directUpdateDialogShown = true;

        String version = versionName.isEmpty() ? String.valueOf(versionCode) : versionName;
        String message = "Está disponível a versão " + version + ".";
        if (!notes.isEmpty()) message += "\n\n" + notes;

        new AlertDialog.Builder(this)
                .setTitle("Nova versão disponível")
                .setMessage(message)
                .setNegativeButton("Mais tarde", null)
                .setPositiveButton("Atualizar", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(this, UpdateActivity.class);
                        intent.putExtra(UpdateActivity.EXTRA_APK_URL, apkUrl);
                        intent.putExtra(UpdateActivity.EXTRA_VERSION, version);
                        startActivity(intent);
                    } catch (Exception ignored) {}
                })
                .show();
    }

    private boolean isTrustedUpdateUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return false;
            host = IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
            return host.equals("raw.githubusercontent.com")
                    || host.endsWith(".raw.githubusercontent.com")
                    || host.equals("atsisqual.github.io");
        } catch (Exception ignored) {
            return false;
        }
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
                    sendEpgResult(
                            id,
                            false,
                            message == null || message.trim().isEmpty()
                                    ? "Não foi possível descarregar o XMLTV"
                                    : message
                    );
                } finally {
                    if (connection != null) connection.disconnect();
                }
            });
        }

        @JavascriptInterface
        public void rememberChannel(String value) {
            WebView current = webView;
            if (current == null) return;
            current.post(() -> {
                if (!isHome(current.getUrl())) return;
                EpgMainActivity.this.rememberChannel(value);
            });
        }

        @JavascriptInterface
        public void enableDirectMode(String value) {
            WebView current = webView;
            if (current == null) return;
            current.post(() -> {
                if (!isHome(current.getUrl())) return;
                enableDirectPlayerMode(value);
            });
        }
    }

    @Override
    public void onBackPressed() {
        if (directPlayerMode) {
            ensureWebView();
            if (webView != null && webView.getVisibility() != View.VISIBLE) {
                super.onBackPressed();
                return;
            }
            moveTaskToBack(true);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        epgBackground.shutdownNow();
        logoBackground.shutdownNow();
        logoCache.clear();
        super.onDestroy();
    }

    private static final class ChannelItem {
        final String name;
        final String url;
        final String category;
        final String logo;

        ChannelItem(String name, String url, String category, String logo) {
            this.name = name;
            this.url = url;
            this.category = category;
            this.logo = logo == null ? "" : logo;
        }
    }
}
