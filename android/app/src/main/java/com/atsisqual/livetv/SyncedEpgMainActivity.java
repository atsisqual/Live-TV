package com.atsisqual.livetv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Compatibility activity kept for the existing manifest entry.
 *
 * v1.6.18 keeps the event-driven player model from v1.6.17, but avoids rebuilding
 * the complete native channel list when only the selected row changed. It also
 * warms the embedded-logo bitmap cache off the UI thread before the sidebar needs it.
 *
 * v1.6.19 makes portugal-sports-channels.json the single logo source for both the
 * native sidebar and the local home grid. The catalogue is parsed once by the native
 * layer, cached in memory, then injected into the home page once per document.
 */
public class SyncedEpgMainActivity extends EpgMainActivity {

    private static final String PREFS = "live-tv-settings";
    private static final String PREF_LAST_CHANNEL = "last-channel-url";
    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final int MAX_LOGO_BYTES = 768 * 1024;

    private final ExecutorService logoPreloader = Executors.newSingleThreadExecutor();

    private Field selectedChannelUrlField;
    private Field channelsField;
    private Field channelListField;
    private Field channelScrollField;
    private Field channelItemUrlField;
    private Field bundledLogoDarkField;
    private Field bundledLogoLightField;
    private Field logoCacheField;
    private Method matchesActiveChannelMethod;
    private boolean reflectionReady;
    private boolean homeLogosInjected;
    private String homeLogoCatalogJson;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preloadBundledLogos();
    }

    @Override
    protected void onLiveTvPageStarted(WebView view, String url) {
        if (isHomeUrl(url)) homeLogosInjected = false;
        preconfirmSelection(url);
        super.onLiveTvPageStarted(view, url);
    }

    @Override
    protected void onLiveTvPageCommitVisible(WebView view, String url) {
        preconfirmSelection(url);
        super.onLiveTvPageCommitVisible(view, url);
        injectBundledLogosIntoHome(view, url);
    }

    @Override
    protected void onLiveTvPageFinished(WebView view, String url) {
        preconfirmSelection(url);
        super.onLiveTvPageFinished(view, url);
        injectBundledLogosIntoHome(view, url);
    }

    private boolean isHomeUrl(String url) {
        return url != null && url.startsWith(HOME_URL);
    }

    private void injectBundledLogosIntoHome(WebView view, String url) {
        if (view == null || !isHomeUrl(url) || homeLogosInjected) return;

        String catalog = getHomeLogoCatalogJson();
        if (catalog == null || catalog.equals("{}")) return;
        homeLogosInjected = true;

        String script =
                "(function(){try{"
                        + "var catalog=" + catalog + ";"
                        + "window.__liveTvBundledLogoCatalog=catalog;"
                        + "function apply(){try{"
                        + "if(typeof bundledLogoMap==='undefined')return false;"
                        + "bundledLogoMap=new Map(Object.keys(catalog).map(function(k){"
                        + "return [String(k).toLowerCase(),catalog[k]];" 
                        + "}));"
                        + "if(typeof render==='function')render();"
                        + "return true;"
                        + "}catch(e){return false;}}"
                        + "if(apply())return true;"
                        + "var attempts=0;var timer=setInterval(function(){"
                        + "attempts++;if(apply()||attempts>=20)clearInterval(timer);"
                        + "},25);return true;"
                        + "}catch(e){return false;}})()";
        view.evaluateJavascript(script, null);
    }

    private String getHomeLogoCatalogJson() {
        if (homeLogoCatalogJson != null) return homeLogoCatalogJson;
        try {
            prepareReflection();
            @SuppressWarnings("unchecked")
            Map<String, String> dark = (Map<String, String>) bundledLogoDarkField.get(this);
            @SuppressWarnings("unchecked")
            Map<String, String> light = (Map<String, String>) bundledLogoLightField.get(this);

            JSONObject catalog = new JSONObject();
            Set<String> slugs = new HashSet<>();
            if (dark != null) slugs.addAll(dark.keySet());
            if (light != null) slugs.addAll(light.keySet());

            for (String slug : slugs) {
                if (slug == null || slug.trim().isEmpty()) continue;
                JSONObject logos = new JSONObject();
                String darkValue = dark == null ? "" : safe(dark.get(slug));
                String lightValue = light == null ? "" : safe(light.get(slug));
                if (!darkValue.isEmpty()) logos.put("logo_dark", darkValue);
                if (!lightValue.isEmpty()) logos.put("logo_light", lightValue);
                if (logos.length() > 0) catalog.put(slug.toLowerCase(), logos);
            }

            homeLogoCatalogJson = catalog.toString();
        } catch (Exception ignored) {
            homeLogoCatalogJson = "{}";
        }
        return homeLogoCatalogJson;
    }

    private void prepareReflection() throws Exception {
        if (reflectionReady) return;

        Class<?> cls = EpgMainActivity.class;
        selectedChannelUrlField = cls.getDeclaredField("selectedChannelUrl");
        channelsField = cls.getDeclaredField("channels");
        channelListField = cls.getDeclaredField("channelList");
        channelScrollField = cls.getDeclaredField("channelScroll");
        bundledLogoDarkField = cls.getDeclaredField("bundledLogoDark");
        bundledLogoLightField = cls.getDeclaredField("bundledLogoLight");
        logoCacheField = cls.getDeclaredField("logoCache");
        matchesActiveChannelMethod = cls.getDeclaredMethod(
                "matchesActiveChannel", String.class, String.class);

        selectedChannelUrlField.setAccessible(true);
        channelsField.setAccessible(true);
        channelListField.setAccessible(true);
        channelScrollField.setAccessible(true);
        bundledLogoDarkField.setAccessible(true);
        bundledLogoLightField.setAccessible(true);
        logoCacheField.setAccessible(true);
        matchesActiveChannelMethod.setAccessible(true);
        reflectionReady = true;
    }

    private void preconfirmSelection(String activeUrl) {
        if (activeUrl == null || activeUrl.trim().isEmpty()
                || activeUrl.startsWith("file:///android_asset/")) return;

        try {
            prepareReflection();

            @SuppressWarnings("unchecked")
            List<Object> channels = (List<Object>) channelsField.get(this);
            LinearLayout channelList = (LinearLayout) channelListField.get(this);
            if (channels == null || channels.isEmpty() || channelList == null
                    || channelList.getChildCount() != channels.size()) {
                return;
            }

            String currentSelection = safe(selectedChannelUrlField.get(this));
            int oldIndex = findChannelIndex(channels, currentSelection);
            int newIndex = findChannelIndex(channels, activeUrl.trim());
            if (newIndex < 0 || newIndex == oldIndex) return;

            String confirmedUrl = channelUrl(channels.get(newIndex));
            if (confirmedUrl.isEmpty()) return;

            selectedChannelUrlField.set(this, confirmedUrl);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(PREF_LAST_CHANNEL, confirmedUrl)
                    .apply();

            if (oldIndex >= 0) styleRow(channelList, oldIndex, false);
            styleRow(channelList, newIndex, true);
            scrollSelectedRow(newIndex);
        } catch (Exception ignored) {
            // Optimization only: EpgMainActivity keeps the full-render fallback.
        }
    }

    private int findChannelIndex(List<Object> channels, String activeUrl) throws Exception {
        if (activeUrl == null || activeUrl.trim().isEmpty()) return -1;
        for (int i = 0; i < channels.size(); i++) {
            String url = channelUrl(channels.get(i));
            if (!url.isEmpty() && matches(url, activeUrl)) return i;
        }
        return -1;
    }

    private String channelUrl(Object item) throws Exception {
        if (item == null) return "";
        if (channelItemUrlField == null || channelItemUrlField.getDeclaringClass() != item.getClass()) {
            channelItemUrlField = item.getClass().getDeclaredField("url");
            channelItemUrlField.setAccessible(true);
        }
        return safe(channelItemUrlField.get(item));
    }

    private boolean matches(String left, String right) throws Exception {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) return false;
        Object result = matchesActiveChannelMethod.invoke(this, left, right);
        return result instanceof Boolean && (Boolean) result;
    }

    private void styleRow(LinearLayout channelList, int index, boolean selected) {
        if (index < 0 || index >= channelList.getChildCount()) return;
        View view = channelList.getChildAt(index);
        if (!(view instanceof LinearLayout)) return;

        LinearLayout row = (LinearLayout) view;
        row.setBackground(roundedBackground(
                selected ? Color.rgb(66, 26, 36) : Color.rgb(23, 28, 36),
                selected ? Color.rgb(255, 48, 79) : Color.rgb(41, 49, 61)
        ));

        if (row.getChildCount() == 0 || !(row.getChildAt(0) instanceof LinearLayout)) return;
        LinearLayout textWrap = (LinearLayout) row.getChildAt(0);
        if (textWrap.getChildCount() > 0 && textWrap.getChildAt(0) instanceof TextView) {
            TextView name = (TextView) textWrap.getChildAt(0);
            name.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (textWrap.getChildCount() > 1 && textWrap.getChildAt(1) instanceof TextView) {
            TextView category = (TextView) textWrap.getChildAt(1);
            category.setTextColor(
                    selected ? Color.rgb(255, 167, 180) : Color.rgb(141, 150, 165)
            );
        }
    }

    private GradientDrawable roundedBackground(int fillColor, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(11));
        drawable.setStroke(dp(1), strokeColor);
        return drawable;
    }

    private void scrollSelectedRow(int index) {
        try {
            ScrollView scroll = (ScrollView) channelScrollField.get(this);
            if (scroll == null) return;
            scroll.post(() -> {
                int target = index * dp(63);
                scroll.smoothScrollTo(0, Math.max(0, target - dp(68)));
            });
        } catch (Exception ignored) {}
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void preloadBundledLogos() {
        logoPreloader.execute(() -> {
            try {
                prepareReflection();
                @SuppressWarnings("unchecked")
                Map<String, String> dark = (Map<String, String>) bundledLogoDarkField.get(this);
                @SuppressWarnings("unchecked")
                Map<String, String> light = (Map<String, String>) bundledLogoLightField.get(this);
                @SuppressWarnings("unchecked")
                Map<String, Bitmap> cache = (Map<String, Bitmap>) logoCacheField.get(this);
                if (cache == null) return;

                Set<String> logoValues = new HashSet<>();
                if (dark != null) logoValues.addAll(dark.values());
                if (light != null) logoValues.addAll(light.values());

                for (String value : logoValues) {
                    String logo = value == null ? "" : value.trim();
                    if (!logo.regionMatches(true, 0, "data:image/", 0, 11)
                            || cache.containsKey(logo)) continue;
                    Bitmap bitmap = decodeDataLogo(logo);
                    if (bitmap != null) cache.put(logo, bitmap);
                }
            } catch (Exception ignored) {
                // The normal lazy logo loader remains the fallback.
            }
        });
    }

    private Bitmap decodeDataLogo(String value) {
        try {
            int comma = value.indexOf(',');
            if (comma <= 0 || !value.substring(0, comma).toLowerCase().contains(";base64")) {
                return null;
            }
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @Override
    protected void onDestroy() {
        logoPreloader.shutdownNow();
        super.onDestroy();
    }
}
