package com.atsisqual.livetv;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.FrameLayout;
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
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;

public class EpgMainActivity extends MainActivity {

    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final int MAX_XMLTV_BYTES = 12 * 1024 * 1024;
    private static final int SIDEBAR_WIDTH_DP = 224;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService epgBackground = Executors.newSingleThreadExecutor();
    private final List<ChannelItem> channels = new ArrayList<>();

    private WebView webView;
    private FrameLayout playerRoot;
    private View statusOverlay;
    private Button legacyBackButton;

    private LinearLayout sidebar;
    private LinearLayout channelList;
    private ScrollView channelScroll;
    private Button sidebarBackButton;

    private String epgScript;
    private String epgUrlScript;
    private boolean bridgeInstalled;
    private boolean sidebarBuilt;
    private boolean sidebarShowing;
    private boolean channelCaptureRunning;
    private long lastChannelCaptureAt;
    private String lastActiveChannel = "";

    private final Runnable injector = new Runnable() {
        @Override
        public void run() {
            try {
                ensureWebView();
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

    private final Runnable sidebarWatcher = new Runnable() {
        @Override
        public void run() {
            try {
                ensureWebView();
                if (webView != null) {
                    ensureSidebar();

                    if (legacyBackButton != null) {
                        legacyBackButton.setVisibility(View.GONE);
                    }

                    boolean fullscreen = webView.getVisibility() != View.VISIBLE;
                    String url = webView.getUrl();
                    boolean home = isHome(url);

                    if (fullscreen || home) {
                        hideSidebar();
                        if (home) captureChannelsIfNeeded();
                    } else {
                        showSidebar();
                        refreshActiveChannelIfNeeded();
                    }
                }
            } catch (Exception ignored) {
                // Sidebar is optional and must never interrupt playback.
            } finally {
                handler.postDelayed(this, 180);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        handler.postDelayed(injector, 700);
        handler.postDelayed(sidebarWatcher, 300);
    }

    private void ensureWebView() {
        if (webView == null) webView = findWebView(getWindow().getDecorView());
        if (webView != null && playerRoot == null && webView.getParent() instanceof FrameLayout) {
            playerRoot = (FrameLayout) webView.getParent();
        }
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

    private void ensureSidebar() {
        if (sidebarBuilt || playerRoot == null) return;

        locateBasePlayerViews();

        sidebar = new LinearLayout(this);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(8), dp(8), dp(8), dp(8));
        sidebar.setBackgroundColor(Color.rgb(12, 15, 20));
        sidebar.setVisibility(View.GONE);

        sidebarBackButton = new Button(this);
        sidebarBackButton.setText("← Voltar");
        sidebarBackButton.setAllCaps(false);
        sidebarBackButton.setTextSize(16);
        sidebarBackButton.setTextColor(Color.WHITE);
        sidebarBackButton.setTypeface(sidebarBackButton.getTypeface(), android.graphics.Typeface.BOLD);
        sidebarBackButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        sidebarBackButton.setPadding(dp(14), 0, dp(12), 0);
        sidebarBackButton.setBackground(roundedBackground(Color.rgb(29, 35, 45), Color.rgb(56, 66, 82)));
        sidebarBackButton.setOnClickListener(v -> returnToChannelGrid());

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
                dp(52)
        );
        sidebar.addView(sidebarBackButton, backParams);

        channelScroll = new ScrollView(this);
        channelScroll.setFillViewport(true);
        channelScroll.setVerticalScrollBarEnabled(true);
        channelScroll.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);

        channelList = new LinearLayout(this);
        channelList.setOrientation(LinearLayout.VERTICAL);
        channelList.setPadding(0, dp(8), 0, dp(4));

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
    }

    private void returnToChannelGrid() {
        if (legacyBackButton != null) {
            legacyBackButton.performClick();
            return;
        }
        if (webView != null) webView.loadUrl(HOME_URL);
    }

    private void showSidebar() {
        if (!sidebarBuilt || sidebar == null || webView == null) return;

        applyPlayerRightMargin(dp(SIDEBAR_WIDTH_DP));
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
        lastActiveChannel = "";
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

    private void captureChannelsIfNeeded() {
        if (webView == null || !isHome(webView.getUrl()) || channelCaptureRunning) return;

        long now = System.currentTimeMillis();
        if (now - lastChannelCaptureAt < 1500) return;

        lastChannelCaptureAt = now;
        channelCaptureRunning = true;

        String script =
                "(function(){try{"
                        + "if(typeof allItems!=='function')return {ready:false,items:[]};"
                        + "var a=allItems();"
                        + "if(typeof sortItems==='function')a=sortItems(a);"
                        + "return {ready:true,items:a.filter(function(x){return x&&x.url;}).map(function(x){"
                        + "return {name:String(x.name||'Canal'),url:String(x.url||''),"
                        + "category:String(x.category||'Canal')};"
                        + "})};"
                        + "}catch(e){return {ready:false,items:[]};}})()";

        webView.evaluateJavascript(script, value -> {
            channelCaptureRunning = false;
            try {
                JSONObject result = new JSONObject(value == null || value.trim().isEmpty() ? "{}" : value);
                if (!result.optBoolean("ready", false)) return;

                JSONArray items = result.optJSONArray("items");
                channels.clear();

                if (items != null) {
                    for (int i = 0; i < items.length(); i++) {
                        JSONObject item = items.optJSONObject(i);
                        if (item == null) continue;

                        String name = item.optString("name", "Canal").trim();
                        String url = item.optString("url", "").trim();
                        String category = item.optString("category", "Canal").trim();
                        if (url.isEmpty()) continue;

                        channels.add(new ChannelItem(
                                name.isEmpty() ? "Canal" : name,
                                url,
                                category.isEmpty() ? "Canal" : category
                        ));
                    }
                }

                renderChannelList();
            } catch (Exception ignored) {
                // A stale/partial JS result is harmless; the next pass retries.
            }
        });
    }

    private void refreshActiveChannelIfNeeded() {
        if (!sidebarShowing || webView == null) return;
        String active = currentStreamIdentity();
        if (!active.equals(lastActiveChannel)) {
            lastActiveChannel = active;
            renderChannelList();
        }
    }

    private String currentStreamIdentity() {
        if (webView == null) return "";

        String original = webView.getOriginalUrl();
        if (original != null && !original.trim().isEmpty() && !isHome(original)) {
            return original.trim();
        }

        String current = webView.getUrl();
        return current == null ? "" : current.trim();
    }

    private void renderChannelList() {
        if (channelList == null) return;
        channelList.removeAllViews();

        if (channels.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Sem canais na lista");
            empty.setTextColor(Color.rgb(141, 150, 165));
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(24), dp(10), dp(24));
            channelList.addView(
                    empty,
                    new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    )
            );
            return;
        }

        String active = currentStreamIdentity();
        int selectedIndex = -1;

        for (int i = 0; i < channels.size(); i++) {
            ChannelItem item = channels.get(i);
            boolean selected = matchesActiveChannel(item.url, active);
            if (selected) selectedIndex = i;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(7), dp(12), dp(7));
            row.setClickable(true);
            row.setFocusable(true);
            row.setBackground(roundedBackground(
                    selected ? Color.rgb(66, 26, 36) : Color.rgb(23, 28, 36),
                    selected ? Color.rgb(255, 48, 79) : Color.rgb(41, 49, 61)
            ));

            TextView name = new TextView(this);
            name.setText(item.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            name.setTypeface(
                    name.getTypeface(),
                    selected ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL
            );
            row.addView(
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
            category.setTextSize(10);
            category.setSingleLine(true);
            category.setEllipsize(android.text.TextUtils.TruncateAt.END);

            LinearLayout.LayoutParams categoryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            categoryParams.topMargin = dp(2);
            row.addView(category, categoryParams);

            row.setOnClickListener(v -> openChannelFromSidebar(item));

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(58)
            );
            rowParams.bottomMargin = dp(6);
            channelList.addView(row, rowParams);
        }

        if (selectedIndex >= 0 && channelScroll != null) {
            final int index = selectedIndex;
            channelScroll.post(() -> {
                int target = index * dp(64);
                channelScroll.smoothScrollTo(0, Math.max(0, target - dp(72)));
            });
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

        String active = currentStreamIdentity();
        if (matchesActiveChannel(item.url, active)) return;

        lastActiveChannel = item.url;
        webView.loadUrl(item.url);
        renderChannelList();
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(12));
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
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(injector);
        handler.removeCallbacks(sidebarWatcher);
        epgBackground.shutdownNow();
        super.onDestroy();
    }

    private static final class ChannelItem {
        final String name;
        final String url;
        final String category;

        ChannelItem(String name, String url, String category) {
            this.name = name;
            this.url = url;
            this.category = category;
        }
    }
}
