package com.atsisqual.livetv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Compatibility activity kept for the existing manifest entry.
 *
 * v1.6.20 keeps the event-driven sidebar selection from v1.6.18, but channel logos
 * now come exclusively from the imported channel JSON. The APK contains no channel
 * logo catalogue. Imported data logos are decoded off the UI thread and cached by
 * the existing native bitmap cache before the sidebar is refreshed.
 *
 * v1.6.21 adds bounded, redacted provider-chain diagnostics. It records only
 * hostnames, resource categories and elapsed timings; no media endpoint/path/query
 * is persisted or displayed. Long-press any sidebar channel (or the back button) to
 * inspect the current provider chain.
 */
public class SyncedEpgMainActivity extends EpgMainActivity {

    private static final String PREFS = "live-tv-settings";
    private static final String PREF_LAST_CHANNEL = "last-channel-url";
    private static final String HOME_URL = "file:///android_asset/index.html";
    private static final int MAX_LOGO_BYTES = 768 * 1024;

    private final Handler logoHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService logoPreloader = Executors.newSingleThreadExecutor();

    private Field selectedChannelUrlField;
    private Field channelsField;
    private Field channelListField;
    private Field channelScrollField;
    private Field sidebarBackButtonField;
    private Field logoCacheField;
    private Field channelItemUrlField;
    private Field channelItemNameField;
    private Field channelItemCategoryField;
    private Field channelItemLogoField;
    private Constructor<?> channelItemConstructor;
    private Method matchesActiveChannelMethod;
    private Method renderChannelListMethod;
    private boolean reflectionReady;
    private boolean externalLogoSupportInjected;
    private String externalLogoScript;
    private ProviderDiagnostics providerDiagnostics;

    @Override
    protected void onLiveTvPageStarted(WebView view, String url) {
        ensureProviderDiagnostics();
        providerDiagnostics.onPageStarted(view, url);

        if (isHomeUrl(url)) {
            externalLogoSupportInjected = false;
            logoHandler.removeCallbacksAndMessages(null);
        }
        preconfirmSelection(url);
        super.onLiveTvPageStarted(view, url);
        installDiagnosticShortcuts();
    }

    @Override
    protected void onLiveTvPageCommitVisible(WebView view, String url) {
        ensureProviderDiagnostics();
        providerDiagnostics.onPageReady(view, url);
        preconfirmSelection(url);
        super.onLiveTvPageCommitVisible(view, url);
        if (isHomeUrl(url)) injectExternalLogoSupport(view);
        installDiagnosticShortcuts();
    }

    @Override
    protected void onLiveTvPageFinished(WebView view, String url) {
        ensureProviderDiagnostics();
        providerDiagnostics.onPageReady(view, url);
        preconfirmSelection(url);
        super.onLiveTvPageFinished(view, url);
        if (isHomeUrl(url)) {
            injectExternalLogoSupport(view);
            scheduleImportedLogoSync(view);
        }
        installDiagnosticShortcuts();
    }

    @Override
    protected void onLiveTvResourceObserved(String host, String kind) {
        ensureProviderDiagnostics();
        providerDiagnostics.recordNativeResource(kind, host);
        super.onLiveTvResourceObserved(host, kind);
    }

    @Override
    protected void onLiveTvUserTouch(String host) {
        ensureProviderDiagnostics();
        providerDiagnostics.recordUserTouch(host);
        super.onLiveTvUserTouch(host);
    }

    private void ensureProviderDiagnostics() {
        if (providerDiagnostics == null) providerDiagnostics = new ProviderDiagnostics(this);
    }

    private void installDiagnosticShortcuts() {
        try {
            prepareReflection();
            View.OnLongClickListener listener = v -> {
                ensureProviderDiagnostics();
                providerDiagnostics.showReport();
                return true;
            };

            Button back = (Button) sidebarBackButtonField.get(this);
            if (back != null) back.setOnLongClickListener(listener);

            LinearLayout list = (LinearLayout) channelListField.get(this);
            if (list == null) return;
            list.setOnLongClickListener(listener);
            for (int i = 0; i < list.getChildCount(); i++) {
                View row = list.getChildAt(i);
                if (row != null) row.setOnLongClickListener(listener);
            }
        } catch (Exception ignored) {}
    }

    private boolean isHomeUrl(String url) {
        return url != null && url.startsWith(HOME_URL);
    }

    private void injectExternalLogoSupport(WebView view) {
        if (view == null || externalLogoSupportInjected) return;
        try {
            if (externalLogoScript == null) externalLogoScript = loadAsset("channel-logos.js");
            externalLogoSupportInjected = true;
            view.evaluateJavascript(externalLogoScript, null);
        } catch (Exception ignored) {
            externalLogoSupportInjected = false;
        }
    }

    private String loadAsset(String name) throws Exception {
        StringBuilder out = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                getAssets().open(name), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) out.append(line).append('\n');
        }
        return out.toString();
    }

    private void scheduleImportedLogoSync(WebView view) {
        long[] delays = {120L, 320L, 700L, 1200L, 2200L};
        for (long delay : delays) {
            logoHandler.postDelayed(() -> syncImportedLogosFromHome(view), delay);
        }
    }

    private void syncImportedLogosFromHome(WebView view) {
        if (view == null || !isHomeUrl(view.getUrl())) return;

        String script =
                "(function(){try{"
                        + "if(typeof allItems!=='function')return [];"
                        + "return allItems().filter(function(x){return x&&x.url;}).map(function(x){"
                        + "var logo='';"
                        + "try{logo=(typeof pickLogo==='function'?pickLogo(x):(x.logo_dark||x.logo||x.logo_light||''));}catch(e){}"
                        + "return {url:String(x.url||''),logo:String(logo||'')};"
                        + "});"
                        + "}catch(e){return [];}})()";

        view.evaluateJavascript(script, value -> {
            try {
                JSONArray items = new JSONArray(value == null || value.trim().isEmpty() ? "[]" : value);
                Map<String, String> logos = new HashMap<>();
                Set<String> preload = new HashSet<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.optJSONObject(i);
                    if (item == null) continue;
                    String url = item.optString("url", "").trim();
                    String logo = item.optString("logo", "").trim();
                    if (url.isEmpty() || logo.isEmpty()) continue;
                    logos.put(url, logo);
                    if (logo.regionMatches(true, 0, "data:image/", 0, 11)) preload.add(logo);
                }
                if (logos.isEmpty()) return;

                logoPreloader.execute(() -> {
                    preloadDataLogos(preload);
                    logoHandler.post(() -> applyImportedLogos(logos));
                });
            } catch (Exception ignored) {}
        });
    }

    @SuppressWarnings("unchecked")
    private void preloadDataLogos(Set<String> logos) {
        if (logos == null || logos.isEmpty()) return;
        try {
            prepareReflection();
            Map<String, Bitmap> cache = (Map<String, Bitmap>) logoCacheField.get(this);
            if (cache == null) return;
            for (String logo : logos) {
                if (cache.containsKey(logo)) continue;
                Bitmap bitmap = decodeDataLogo(logo);
                if (bitmap != null) cache.put(logo, bitmap);
            }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private void applyImportedLogos(Map<String, String> logos) {
        if (logos == null || logos.isEmpty()) return;
        try {
            prepareReflection();
            List<Object> channels = (List<Object>) channelsField.get(this);
            if (channels == null || channels.isEmpty()) return;

            boolean changed = false;
            for (int i = 0; i < channels.size(); i++) {
                Object item = channels.get(i);
                if (item == null) continue;
                prepareChannelItemReflection(item.getClass());

                String url = safe(channelItemUrlField.get(item));
                String newLogo = findLogoForUrl(logos, url);
                if (newLogo.isEmpty()) continue;
                String oldLogo = safe(channelItemLogoField.get(item));
                if (newLogo.equals(oldLogo)) continue;

                Object replacement = channelItemConstructor.newInstance(
                        safe(channelItemNameField.get(item)),
                        url,
                        safe(channelItemCategoryField.get(item)),
                        newLogo
                );
                channels.set(i, replacement);
                changed = true;
            }

            if (changed) {
                renderChannelListMethod.invoke(this);
                installDiagnosticShortcuts();
            }
        } catch (Exception ignored) {}
    }

    private String findLogoForUrl(Map<String, String> logos, String channelUrl) throws Exception {
        String direct = logos.get(channelUrl);
        if (direct != null && !direct.trim().isEmpty()) return direct.trim();
        for (Map.Entry<String, String> entry : logos.entrySet()) {
            if (matches(entry.getKey(), channelUrl)) return safe(entry.getValue());
        }
        return "";
    }

    private void prepareReflection() throws Exception {
        if (reflectionReady) return;

        Class<?> cls = EpgMainActivity.class;
        selectedChannelUrlField = cls.getDeclaredField("selectedChannelUrl");
        channelsField = cls.getDeclaredField("channels");
        channelListField = cls.getDeclaredField("channelList");
        channelScrollField = cls.getDeclaredField("channelScroll");
        sidebarBackButtonField = cls.getDeclaredField("sidebarBackButton");
        logoCacheField = cls.getDeclaredField("logoCache");
        matchesActiveChannelMethod = cls.getDeclaredMethod(
                "matchesActiveChannel", String.class, String.class);
        renderChannelListMethod = cls.getDeclaredMethod("renderChannelList");

        selectedChannelUrlField.setAccessible(true);
        channelsField.setAccessible(true);
        channelListField.setAccessible(true);
        channelScrollField.setAccessible(true);
        sidebarBackButtonField.setAccessible(true);
        logoCacheField.setAccessible(true);
        matchesActiveChannelMethod.setAccessible(true);
        renderChannelListMethod.setAccessible(true);
        reflectionReady = true;
    }

    private void prepareChannelItemReflection(Class<?> itemClass) throws Exception {
        if (channelItemConstructor != null && channelItemConstructor.getDeclaringClass() == itemClass) return;

        channelItemUrlField = itemClass.getDeclaredField("url");
        channelItemNameField = itemClass.getDeclaredField("name");
        channelItemCategoryField = itemClass.getDeclaredField("category");
        channelItemLogoField = itemClass.getDeclaredField("logo");
        channelItemConstructor = itemClass.getDeclaredConstructor(
                String.class, String.class, String.class, String.class);

        channelItemUrlField.setAccessible(true);
        channelItemNameField.setAccessible(true);
        channelItemCategoryField.setAccessible(true);
        channelItemLogoField.setAccessible(true);
        channelItemConstructor.setAccessible(true);
    }

    private void preconfirmSelection(String activeUrl) {
        if (activeUrl == null || activeUrl.trim().isEmpty() || isHomeUrl(activeUrl)) return;

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

            prepareChannelItemReflection(channels.get(newIndex).getClass());
            String confirmedUrl = safe(channelItemUrlField.get(channels.get(newIndex)));
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
            Object item = channels.get(i);
            if (item == null) continue;
            prepareChannelItemReflection(item.getClass());
            String url = safe(channelItemUrlField.get(item));
            if (!url.isEmpty() && matches(url, activeUrl)) return i;
        }
        return -1;
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

    private Bitmap decodeDataLogo(String value) {
        try {
            int comma = value.indexOf(',');
            if (comma <= 0 || !value.substring(0, comma).toLowerCase().contains(";base64")) return null;
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @Override
    protected void onDestroy() {
        logoHandler.removeCallbacksAndMessages(null);
        logoPreloader.shutdownNow();
        if (providerDiagnostics != null) providerDiagnostics.destroy();
        super.onDestroy();
    }
}
