package com.atsisqual.livetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String PREFS = "live_tv";
    private static final String KEY_CHANNELS = "custom_channels";

    private static final class Channel {
        final String name, url;
        final boolean custom;
        Channel(String name, String url, boolean custom) {
            this.name = name;
            this.url = url;
            this.custom = custom;
        }
    }

    private static final List<Channel> BUILT_INS = Arrays.asList(
            new Channel("RTP 1", "https://www.rtp.pt/play/direto/rtp1", false),
            new Channel("RTP 2", "https://www.rtp.pt/play/direto/rtp2", false),
            new Channel("RTP Notícias", "https://www.rtp.pt/play/direto/rtp3", false),
            new Channel("RTP Memória", "https://www.rtp.pt/play/direto/rtpmemoria", false),
            new Channel("RTP Açores", "https://www.rtp.pt/play/direto/rtpacores", false),
            new Channel("RTP Madeira", "https://www.rtp.pt/play/direto/rtpmadeira", false),
            new Channel("RTP África", "https://www.rtp.pt/play/direto/rtpafrica", false),
            new Channel("RTP Mundo", "https://www.rtp.pt/play/direto/rtpinternacional", false)
    );

    private final List<Channel> customChannels = new ArrayList<>();
    private FrameLayout root;
    private LinearLayout main, buttons;
    private TextView title;
    private WebView web;
    private Channel active;
    private View fullscreen;
    private WebChromeClient.CustomViewCallback fullscreenCallback;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadSavedChannels();

        root = new FrameLayout(this);
        main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        root.addView(main, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(6), dp(8), dp(4));
        title = new TextView(this);
        title.setTextSize(18);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        Button list = new Button(this);
        list.setText("TV List");
        list.setAllCaps(false);
        list.setOnClickListener(v -> showListMenu());
        header.addView(list);
        main.addView(header);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        buttons = new LinearLayout(this);
        buttons.setPadding(dp(8), 0, dp(8), dp(6));
        scroller.addView(buttons, new HorizontalScrollView.LayoutParams(-2, -2));
        main.addView(scroller);

        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        main.addView(web, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        configureWebView();
        rebuildButtons();
        play(BUILT_INS.get(0));
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                return navigate(req.getUrl());
            }
            @Override @SuppressWarnings("deprecation") public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return navigate(Uri.parse(url));
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                if (fullscreen != null) return;
                fullscreen = view;
                fullscreenCallback = callback;
                main.setVisibility(View.GONE);
                root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            }
            @Override public void onHideCustomView() { exitFullscreen(); }
        });
    }

    private boolean navigate(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme == null) return true;
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
            return true;
        }
        if (active != null && active.custom) return false;
        String host = uri.getHost();
        if (host != null) {
            host = host.toLowerCase(Locale.ROOT);
            if (host.equals("rtp.pt") || host.endsWith(".rtp.pt")) return false;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); } catch (Exception ignored) {}
        return true;
    }

    private void play(Channel c) {
        active = c;
        title.setText(c.name + (c.custom ? " • My List" : ""));
        web.loadUrl(c.url);
    }

    private void rebuildButtons() {
        buttons.removeAllViews();
        for (Channel c : BUILT_INS) addButton(c);
        for (Channel c : customChannels) addButton(c);
        Button add = new Button(this);
        add.setAllCaps(false);
        add.setText("+ Add");
        add.setOnClickListener(v -> addChannelDialog());
        buttons.addView(add);
    }

    private void addButton(Channel c) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(c.name);
        b.setOnClickListener(v -> play(c));
        b.setOnLongClickListener(v -> {
            if (!c.custom) return false;
            new AlertDialog.Builder(this).setTitle("Remove channel?").setMessage(c.name)
                    .setPositiveButton("Remove", (d, w) -> {
                        customChannels.remove(c); saveChannels(); rebuildButtons();
                        if (active == c) play(BUILT_INS.get(0));
                    }).setNegativeButton("Cancel", null).show();
            return true;
        });
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginEnd(dp(6));
        buttons.addView(b, p);
    }

    private void showListMenu() {
        String[] items = {"Add one channel", "Paste / import TV list", "My channels", "Clear my TV list"};
        new AlertDialog.Builder(this).setTitle("My TV List").setItems(items, (d, which) -> {
            if (which == 0) addChannelDialog();
            else if (which == 1) importDialog();
            else if (which == 2) manageDialog();
            else clearDialog();
        }).setNegativeButton("Close", null).show();
    }

    private LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(20), dp(4), dp(20), dp(4));
        return l;
    }

    private ScrollView scroll(View v) {
        ScrollView s = new ScrollView(this); s.addView(v); return s;
    }

    private void addChannelDialog() {
        LinearLayout f = form();
        EditText name = new EditText(this); name.setHint("Channel name"); f.addView(name);
        EditText url = new EditText(this); url.setHint("https://example.com/player");
        url.setSingleLine(true); url.setInputType(InputType.TYPE_TEXT_VARIATION_URI); f.addView(url);
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Add channel").setView(scroll(f))
                .setPositiveButton("Add", null).setNegativeButton("Cancel", null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String n = name.getText().toString().trim(), u = url.getText().toString().trim();
            if (n.isEmpty()) { name.setError("Enter a name"); return; }
            if (!validUrl(u)) { url.setError("Enter an http:// or https:// URL"); return; }
            Channel c = new Channel(n, u, true); customChannels.add(c); saveChannels(); rebuildButtons(); play(c); dlg.dismiss();
        }));
        dlg.show();
    }

    private void importDialog() {
        LinearLayout f = form();
        TextView help = new TextView(this);
        help.setText("Paste M3U, or one entry per line:  Name | https://url");
        f.addView(help);
        EditText input = new EditText(this);
        input.setGravity(Gravity.TOP);
        input.setMinLines(12);
        input.setHint("#EXTM3U\n#EXTINF:-1,My Channel\nhttps://example.com/player\n\nMy Other Channel | https://example.com/player2");
        f.addView(input, new LinearLayout.LayoutParams(-1, dp(320)));
        AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Import TV list").setView(scroll(f))
                .setPositiveButton("Import", null).setNegativeButton("Cancel", null).create();
        dlg.setOnShowListener(x -> dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            List<Channel> found = parseList(input.getText().toString());
            if (found.isEmpty()) { input.setError("No valid channels found"); return; }
            int added = 0;
            for (Channel c : found) if (!containsUrl(c.url)) { customChannels.add(c); added++; }
            saveChannels(); rebuildButtons();
            Toast.makeText(this, added + " channel(s) added", Toast.LENGTH_SHORT).show();
            dlg.dismiss();
        }));
        dlg.show();
    }

    private List<Channel> parseList(String raw) {
        List<Channel> out = new ArrayList<>();
        String pending = null;
        for (String x : raw.replace("\r", "").split("\n")) {
            String line = x.trim();
            if (line.isEmpty() || line.equalsIgnoreCase("#EXTM3U")) continue;
            if (line.startsWith("#EXTINF")) {
                int comma = line.lastIndexOf(',');
                pending = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
                continue;
            }
            if (line.startsWith("#")) continue;
            if (validUrl(line)) {
                String host = Uri.parse(line).getHost();
                out.add(new Channel(pending == null || pending.isEmpty() ? (host == null ? "Channel" : host) : pending, line, true));
                pending = null;
                continue;
            }
            int bar = line.indexOf('|');
            if (bar > 0) {
                String n = line.substring(0, bar).trim(), u = line.substring(bar + 1).trim();
                if (!n.isEmpty() && validUrl(u)) out.add(new Channel(n, u, true));
            }
        }
        return out;
    }

    private boolean containsUrl(String url) {
        for (Channel c : customChannels) if (c.url.equalsIgnoreCase(url)) return true;
        return false;
    }

    private boolean validUrl(String value) {
        Uri u = Uri.parse(value);
        String s = u.getScheme();
        return u.getHost() != null && ("http".equalsIgnoreCase(s) || "https".equalsIgnoreCase(s));
    }

    private void manageDialog() {
        if (customChannels.isEmpty()) { Toast.makeText(this, "Your TV list is empty", Toast.LENGTH_SHORT).show(); return; }
        String[] names = new String[customChannels.size()];
        for (int i = 0; i < names.length; i++) names[i] = customChannels.get(i).name;
        new AlertDialog.Builder(this).setTitle("My channels").setItems(names, (d, i) -> play(customChannels.get(i)))
                .setNegativeButton("Close", null).show();
    }

    private void clearDialog() {
        if (customChannels.isEmpty()) return;
        new AlertDialog.Builder(this).setTitle("Clear My TV List?")
                .setPositiveButton("Clear", (d, w) -> {
                    customChannels.clear(); saveChannels(); rebuildButtons();
                    if (active != null && active.custom) play(BUILT_INS.get(0));
                }).setNegativeButton("Cancel", null).show();
    }

    private void loadSavedChannels() {
        try {
            String raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_CHANNELS, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String n = o.optString("name").trim(), u = o.optString("url").trim();
                if (!n.isEmpty() && validUrl(u)) customChannels.add(new Channel(n, u, true));
            }
        } catch (Exception ignored) { customChannels.clear(); }
    }

    private void saveChannels() {
        JSONArray a = new JSONArray();
        for (Channel c : customChannels) {
            try { JSONObject o = new JSONObject(); o.put("name", c.name); o.put("url", c.url); a.put(o); } catch (Exception ignored) {}
        }
        SharedPreferences.Editor e = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        e.putString(KEY_CHANNELS, a.toString()).apply();
    }

    private void exitFullscreen() {
        if (fullscreen == null) return;
        root.removeView(fullscreen); fullscreen = null; main.setVisibility(View.VISIBLE);
        if (fullscreenCallback != null) { fullscreenCallback.onCustomViewHidden(); fullscreenCallback = null; }
    }

    @Override public void onBackPressed() {
        if (fullscreen != null) exitFullscreen();
        else if (web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) { web.stopLoading(); web.destroy(); }
        super.onDestroy();
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
