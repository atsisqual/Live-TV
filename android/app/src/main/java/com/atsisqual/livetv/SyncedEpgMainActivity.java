package com.atsisqual.livetv;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.webkit.WebView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Keeps the native sidebar selection aligned with the page that the WebView
 * has actually started loading. It also makes a tap on the current row act as
 * an explicit retry instead of being ignored because of stale selection state.
 */
public class SyncedEpgMainActivity extends EpgMainActivity {

    private static final String PREFS = "live-tv-settings";
    private static final String PREF_LAST_CHANNEL = "last-channel-url";
    private static final long SYNC_INTERVAL_MS = 160L;

    private final Handler sidebarSyncHandler = new Handler(Looper.getMainLooper());

    private Field webViewField;
    private Field selectedChannelUrlField;
    private Field channelsField;
    private Field channelListField;
    private Field channelItemUrlField;
    private Method matchesActiveChannelMethod;
    private Method renderChannelListMethod;
    private Method openChannelFromSidebarMethod;
    private boolean reflectionReady;

    private final Runnable sidebarSync = new Runnable() {
        @Override
        public void run() {
            try {
                syncSidebarState();
            } catch (Exception ignored) {
                // Sidebar synchronization must never interfere with playback.
            } finally {
                sidebarSyncHandler.postDelayed(this, SYNC_INTERVAL_MS);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sidebarSyncHandler.postDelayed(sidebarSync, 260L);
    }

    private void prepareReflection() throws Exception {
        if (reflectionReady) return;

        Class<?> cls = EpgMainActivity.class;

        webViewField = cls.getDeclaredField("webView");
        selectedChannelUrlField = cls.getDeclaredField("selectedChannelUrl");
        channelsField = cls.getDeclaredField("channels");
        channelListField = cls.getDeclaredField("channelList");

        webViewField.setAccessible(true);
        selectedChannelUrlField.setAccessible(true);
        channelsField.setAccessible(true);
        channelListField.setAccessible(true);

        matchesActiveChannelMethod = cls.getDeclaredMethod(
                "matchesActiveChannel", String.class, String.class);
        renderChannelListMethod = cls.getDeclaredMethod("renderChannelList");

        for (Method method : cls.getDeclaredMethods()) {
            if ("openChannelFromSidebar".equals(method.getName())
                    && method.getParameterTypes().length == 1) {
                openChannelFromSidebarMethod = method;
                break;
            }
        }

        if (openChannelFromSidebarMethod == null) {
            throw new NoSuchMethodException("openChannelFromSidebar");
        }

        matchesActiveChannelMethod.setAccessible(true);
        renderChannelListMethod.setAccessible(true);
        openChannelFromSidebarMethod.setAccessible(true);
        reflectionReady = true;
    }

    private void syncSidebarState() throws Exception {
        prepareReflection();

        WebView webView = (WebView) webViewField.get(this);
        @SuppressWarnings("unchecked")
        List<Object> channels = (List<Object>) channelsField.get(this);
        if (webView == null || channels == null || channels.isEmpty()) return;

        String loadedChannelUrl = findLoadedChannelUrl(webView, channels);
        String selectedChannelUrl = String.valueOf(selectedChannelUrlField.get(this));
        if ("null".equals(selectedChannelUrl)) selectedChannelUrl = "";

        if (!loadedChannelUrl.isEmpty()
                && !matches(loadedChannelUrl, selectedChannelUrl)) {
            selectedChannelUrlField.set(this, loadedChannelUrl);
            persistConfirmedChannel(loadedChannelUrl);
            renderChannelListMethod.invoke(this);
        }

        bindRetryableRows(channels);
    }

    private String findLoadedChannelUrl(WebView webView, List<Object> channels) throws Exception {
        String current = safe(webView.getUrl());
        String original = safe(webView.getOriginalUrl());

        for (Object item : channels) {
            String channelUrl = channelUrl(item);
            if (channelUrl.isEmpty()) continue;
            if ((!current.isEmpty() && matches(channelUrl, current))
                    || (!original.isEmpty() && matches(channelUrl, original))) {
                return channelUrl;
            }
        }
        return "";
    }

    private void bindRetryableRows(List<Object> channels) throws Exception {
        LinearLayout channelList = (LinearLayout) channelListField.get(this);
        if (channelList == null) return;

        int count = Math.min(channelList.getChildCount(), channels.size());
        for (int i = 0; i < count; i++) {
            View row = channelList.getChildAt(i);
            if (!(row instanceof LinearLayout)) continue;

            Object item = channels.get(i);
            Object tag = row.getTag();
            if (tag instanceof RetryBinding && ((RetryBinding) tag).item == item) continue;

            row.setTag(new RetryBinding(item));
            row.setOnClickListener(v -> openChannelWithRetry(item));
        }
    }

    private void openChannelWithRetry(Object item) {
        try {
            // openChannelFromSidebar normally ignores a click if its cached selected URL
            // already matches the row. A sentinel forces the click to be treated as an
            // explicit navigation/retry; the synchronizer then restores the confirmed URL.
            selectedChannelUrlField.set(this, "__livetv_retry__");
            openChannelFromSidebarMethod.invoke(this, item);
        } catch (Exception ignored) {
            // Keep the existing sidebar behavior if the compatibility hook ever fails.
        }
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

    private void persistConfirmedChannel(String url) {
        if (url == null || url.isEmpty()) return;
        if (url.startsWith("http://") || url.startsWith("https://")) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putString(PREF_LAST_CHANNEL, url)
                    .apply();
        } else {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .remove(PREF_LAST_CHANNEL)
                    .apply();
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @Override
    protected void onDestroy() {
        sidebarSyncHandler.removeCallbacks(sidebarSync);
        super.onDestroy();
    }

    private static final class RetryBinding {
        final Object item;

        RetryBinding(Object item) {
            this.item = item;
        }
    }
}
