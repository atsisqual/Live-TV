from pathlib import Path
import re

ROOT = Path('.')

build = ROOT / 'android/app/build.gradle'
b = build.read_text(encoding='utf-8')
b = b.replace("applicationId 'com.atsisqual.livetv'", "applicationId 'com.atsisqual.livetv.m3utest'", 1)
b = re.sub(r'versionCode\s+\d+', 'versionCode 1', b, count=1)
b = re.sub(r"versionName\s+'[^']+'", "versionName '0.1.0'", b, count=1)
media_deps = '''\n    implementation "androidx.media3:media3-exoplayer:1.5.1"\n    implementation "androidx.media3:media3-exoplayer-hls:1.5.1"\n    implementation "androidx.media3:media3-exoplayer-dash:1.5.1"\n    implementation "androidx.media3:media3-ui:1.5.1"'''
if 'media3-exoplayer' not in b:
    b = b.replace('    implementation "androidx.webkit:webkit:1.15.0"', '    implementation "androidx.webkit:webkit:1.15.0"' + media_deps, 1)
build.write_text(b, encoding='utf-8')

manifest = ROOT / 'android/app/src/main/AndroidManifest.xml'
m = manifest.read_text(encoding='utf-8')
m = m.replace('    <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />\n', '')
m = m.replace('android:label="TV do Zé"', 'android:label="TV do Zé M3U Teste"', 1)
m = m.replace('''        <activity\n            android:name=".UpdateActivity"\n            android:exported="false" />\n''', '')
provider = '''\n        <provider\n            android:name="androidx.core.content.FileProvider"\n            android:authorities="${applicationId}.fileprovider"\n            android:exported="false"\n            android:grantUriPermissions="true">\n            <meta-data\n                android:name="android.support.FILE_PROVIDER_PATHS"\n                android:resource="@xml/update_file_paths" />\n        </provider>'''
m = m.replace(provider, '')
player_activity = '''        <activity\n            android:name=".M3uPlayerActivity"\n            android:configChanges="orientation|screenSize|keyboardHidden"\n            android:exported="false" />\n'''
if '.M3uPlayerActivity' not in m:
    m = m.replace('        <activity\n            android:name=".MainActivity"', player_activity + '        <activity\n            android:name=".MainActivity"', 1)
manifest.write_text(m, encoding='utf-8')

main = ROOT / 'android/app/src/main/java/com/atsisqual/livetv/MainActivity.java'
s = main.read_text(encoding='utf-8')
s = s.replace('        if (isAutoUpdateEnabled()) checkForUpdates();', '        // M3U test app intentionally has no automatic update channel.', 1)
s = s.replace('        web.addJavascriptInterface(new AppSettingsBridge(), "LiveTvSettings");', '        web.addJavascriptInterface(new AppSettingsBridge(), "LiveTvSettings");\n        web.addJavascriptInterface(new M3uPlayerBridge(), "LiveTvM3u");', 1)
s = re.sub(
    r'    protected boolean isAutoUpdateEnabled\(\) \{.*?\n    \}',
    '    protected boolean isAutoUpdateEnabled() {\n        return false;\n    }',
    s,
    count=1,
    flags=re.S,
)
s = re.sub(
    r'    private void setAutoUpdateEnabled\(boolean enabled\) \{.*?\n    \}',
    '    private void setAutoUpdateEnabled(boolean enabled) {\n        pendingUpdate = null;\n        updateDialogShown = false;\n    }',
    s,
    count=1,
    flags=re.S,
)
bridge = r'''
    private final class M3uPlayerBridge {
        @JavascriptInterface
        public void play(String payload) {
            final String json = payload == null ? "" : payload;
            mainHandler.post(() -> {
                try {
                    if (web == null || !isLocalHome(web.getUrl())) return;
                    JSONObject item = new JSONObject(json);
                    String url = item.optString("url", "").trim();
                    Uri uri = Uri.parse(url);
                    String scheme = uri.getScheme();
                    if (uri.getHost() == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                        return;
                    }

                    Intent intent = new Intent(MainActivity.this, M3uPlayerActivity.class);
                    intent.putExtra(M3uPlayerActivity.EXTRA_URL, url);
                    intent.putExtra(M3uPlayerActivity.EXTRA_NAME, item.optString("name", "Canal"));
                    intent.putExtra(M3uPlayerActivity.EXTRA_USER_AGENT, item.optString("user_agent", ""));
                    intent.putExtra(M3uPlayerActivity.EXTRA_REFERRER, item.optString("http_referrer", ""));
                    intent.putExtra(M3uPlayerActivity.EXTRA_ORIGIN, item.optString("http_origin", ""));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }
    }

'''
marker = '    private boolean isTrustedUpdateUrl(String value) {'
if 'class M3uPlayerBridge' not in s:
    if marker not in s:
        raise SystemExit('MainActivity bridge marker not found')
    s = s.replace(marker, bridge + marker, 1)
main.write_text(s, encoding='utf-8')

player = ROOT / 'android/app/src/main/java/com/atsisqual/livetv/M3uPlayerActivity.java'
player.write_text(r'''package com.atsisqual.livetv;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public class M3uPlayerActivity extends Activity {
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_NAME = "stream_name";
    public static final String EXTRA_USER_AGENT = "stream_user_agent";
    public static final String EXTRA_REFERRER = "stream_referrer";
    public static final String EXTRA_ORIGIN = "stream_origin";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        buildUi();
        startPlayback();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        Button back = new Button(this);
        back.setText("← Canais");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(14);
        back.setBackgroundColor(Color.argb(185, 18, 22, 29));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(116), dp(48), Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(12), dp(12), 0, 0);
        root.addView(back, backParams);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name != null && !name.trim().isEmpty()) {
            TextView title = new TextView(this);
            title.setText(name.trim());
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            title.setPadding(dp(12), 0, dp(12), 0);
            title.setBackgroundColor(Color.argb(145, 18, 22, 29));
            FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.END);
            titleParams.setMargins(0, dp(12), dp(12), 0);
            root.addView(title, titleParams);
        }

        setContentView(root);
    }

    private void startPlayback() {
        String url = value(EXTRA_URL);
        if (url.isEmpty()) {
            Toast.makeText(this, "URL do canal inválido", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String userAgent = value(EXTRA_USER_AGENT);
        if (userAgent.isEmpty()) userAgent = "TV-do-Ze-M3U-Test/" + BuildConfig.VERSION_NAME;

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(30_000);

        Map<String, String> headers = new HashMap<>();
        String referrer = value(EXTRA_REFERRER);
        String origin = value(EXTRA_ORIGIN);
        if (!referrer.isEmpty()) headers.put("Referer", referrer);
        if (!origin.isEmpty()) headers.put("Origin", origin);
        if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, http);
        DefaultMediaSourceFactory mediaSources = new DefaultMediaSourceFactory(dataSource);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSources)
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String message = error == null || error.getMessage() == null
                        ? "Não foi possível reproduzir este stream"
                        : error.getMessage();
                Toast.makeText(M3uPlayerActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.setPlayWhenReady(true);
        player.setVolume(1f);
    }

    private String value(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
''', encoding='utf-8')

index = ROOT / 'android/app/src/main/assets/index.html'
h = index.read_text(encoding='utf-8')
h = h.replace('<title>TV do Zé</title>', '<title>TV do Zé M3U Teste</title>', 1)
h = h.replace('<div class="brandtitle">TV do Zé</div>', '<div class="brandtitle">TV do Zé M3U</div>', 1)
h = h.replace('    <button data-menu="autoupdate" id="autoUpdateMenu">✓ Procurar atualizações automaticamente</button>\n', '')
h = h.replace('<script>\nconst K_CUSTOM=', '<script src="m3upt-base.js"></script>\n<script>\nconst K_CUSTOM=', 1)

old_norm = "function normalizeStored(x){if(Array.isArray(x)){const raw={name:text(x[0]||'Channel'),url:text(x[1])};return{name:raw.name,url:canonicalUrl(raw),source_url:raw.url,category:'Outros'}}if(x&&typeof x==='object'){const source=urlFromValue(x.source_url||x.page_url||x.url||x.embed||x.player),slug=text(x.slug).toLowerCase()||slugFromUrl(source);return{name:text(x.name||x.title||'Channel'),url:canonicalUrl(x),source_url:source,slug,epg_id:text(x.epg_id||x.epgId||x.tvg_id||x['tvg-id']),player:playerNumber(x.player||x.player_index||x.playerIndex),category:text(x.category||x.group||x.group_title||x.groupTitle||x['group-title']),logo:text(x.logo||x.logo_url||x.logoUrl||x.icon||x.tvg_logo||x['tvg-logo']),logo_dark:text(x.logo_dark||x.logoDark),logo_light:text(x.logo_light||x.logoLight)}}return null}"
new_norm = "function normalizeStored(x){if(Array.isArray(x)){const raw={name:text(x[0]||'Channel'),url:text(x[1])};return{name:raw.name,url:canonicalUrl(raw),source_url:raw.url,category:'Outros'}}if(x&&typeof x==='object'){const source=urlFromValue(x.source_url||x.page_url||x.url||x.embed||x.player),slug=text(x.slug).toLowerCase()||slugFromUrl(source);return{name:text(x.name||x.title||'Channel'),url:canonicalUrl(x),source_url:source,slug,epg_id:text(x.epg_id||x.epgId||x.tvg_id||x['tvg-id']),player:playerNumber(x.player||x.player_index||x.playerIndex),category:text(x.category||x.group||x.group_title||x.groupTitle||x['group-title']),logo:text(x.logo||x.logo_url||x.logoUrl||x.icon||x.tvg_logo||x['tvg-logo']),logo_dark:text(x.logo_dark||x.logoDark),logo_light:text(x.logo_light||x.logoLight),user_agent:text(x.user_agent||x.http_user_agent||x.userAgent),http_referrer:text(x.http_referrer||x.referrer||x.httpReferrer),http_origin:text(x.http_origin||x.origin||x.httpOrigin),m3u_source:!!(x.m3u_source||x.m3uSource)}}return null}"
if old_norm not in h:
    raise SystemExit('normalizeStored marker not found')
h = h.replace(old_norm, new_norm, 1)

old_open = "function rememberRecent(item){let a=loadRecent().filter(k=>k!==itemKey(item));a.unshift(itemKey(item));localStorage.setItem(K_RECENT,JSON.stringify(a.slice(0,8)))}function playerUrl(item){if(!ok(item.url))return item.url;const p=playerNumber(item.player);if(!p||!isApiTotal(item.url))return item.url;try{const u=new URL(item.url);u.hash='livetv-player='+p;return u.href}catch(e){return item.url}}function openChannel(item){if(!ok(item.url)){openEdit(item);return}rememberRecent(item);sessionStorage.setItem(K_SCROLL,String(window.scrollY||0));window.location.href=playerUrl(item)}function toggleFav(item)"
new_open = "function rememberRecent(item){let a=loadRecent().filter(k=>k!==itemKey(item));a.unshift(itemKey(item));localStorage.setItem(K_RECENT,JSON.stringify(a.slice(0,8)))}function playerUrl(item){if(!ok(item.url))return item.url;const p=playerNumber(item.player);if(!p||!isApiTotal(item.url))return item.url;try{const u=new URL(item.url);u.hash='livetv-player='+p;return u.href}catch(e){return item.url}}function isDirectStream(u){return /\\.(?:m3u8|mpd|mp4|mp3|aac|m4a|ogg|opus)(?:[?#]|$)/i.test(String(u||''))}function openChannel(item){if(!ok(item.url)){openEdit(item);return}rememberRecent(item);sessionStorage.setItem(K_SCROLL,String(window.scrollY||0));if((item.m3u_source||isDirectStream(item.url))&&window.LiveTvM3u&&typeof window.LiveTvM3u.play==='function'){try{window.LiveTvM3u.play(JSON.stringify({name:item.name,url:item.url,user_agent:item.user_agent||'',http_referrer:item.http_referrer||'',http_origin:item.http_origin||''}));return}catch(e){}}window.location.href=playerUrl(item)}function toggleFav(item)"
if old_open not in h:
    raise SystemExit('openChannel marker not found')
h = h.replace(old_open, new_open, 1)

old_parse = "function parseText(raw){let lines=raw.replace(/\\r/g,'').split('\\n'),out=[],pending=null;for(const s of lines){const line=s.trim();if(!line||line.toUpperCase()==='#EXTM3U')continue;if(line.startsWith('#EXTINF')){pending=parseExtInf(line);continue}if(line.startsWith('#'))continue;if(ok(line)){let meta=pending||{},name=meta.name;try{if(!name)name=new URL(line).hostname}catch(e){}out.push(normalizeStored({name:name||'Channel',url:line,category:meta.category||'Outros',logo:meta.logo||'',epg_id:meta.epg_id||''}));pending=null;continue}const p=line.indexOf('|');if(p>0){const name=line.slice(0,p).trim(),url=line.slice(p+1).trim();if(name&&ok(url))out.push(normalizeStored({name,url,category:'Outros'}))}}return out}"
new_parse = "function parseText(raw){let lines=raw.replace(/\\r/g,'').split('\\n'),out=[],pending=null;for(const s of lines){const line=s.trim();if(!line)continue;if(line.startsWith('#EXTINF')){pending=parseExtInf(line);pending.user_agent='';pending.http_referrer='';pending.http_origin='';continue}if(line.startsWith('#EXTVLCOPT:')){if(!pending)pending={};const opt=line.slice(11),p=opt.indexOf('='),k=(p>=0?opt.slice(0,p):opt).toLowerCase(),v=p>=0?opt.slice(p+1):'';if(k==='http-user-agent')pending.user_agent=v;else if(k==='http-referrer')pending.http_referrer=v;else if(k==='http-origin')pending.http_origin=v;continue}if(line.startsWith('#'))continue;if(ok(line)){let meta=pending||{},name=meta.name;try{if(!name)name=new URL(line).hostname}catch(e){}out.push(normalizeStored({name:name||'Channel',url:line,category:meta.category||'Outros',logo:meta.logo||'',epg_id:meta.epg_id||'',user_agent:meta.user_agent||'',http_referrer:meta.http_referrer||'',http_origin:meta.http_origin||'',m3u_source:true}));pending=null;continue}const p=line.indexOf('|');if(p>0){const name=line.slice(0,p).trim(),url=line.slice(p+1).trim();if(name&&ok(url))out.push(normalizeStored({name,url,category:'Outros',m3u_source:true}))}}return out}"
if old_parse not in h:
    raise SystemExit('parseText marker not found')
h = h.replace(old_parse, new_parse, 1)

old_export = "function channelExport(){return loadCustom().map(x=>({name:x.name,url:x.source_url||x.url,slug:x.slug||undefined,epg_id:x.epg_id||undefined,player:x.player||undefined,category:x.category||undefined,logo:x.logo||undefined}))}"
new_export = "function channelExport(){return loadCustom().map(x=>({name:x.name,url:x.source_url||x.url,slug:x.slug||undefined,epg_id:x.epg_id||undefined,player:x.player||undefined,category:x.category||undefined,logo:x.logo||undefined,user_agent:x.user_agent||undefined,http_referrer:x.http_referrer||undefined,http_origin:x.http_origin||undefined,m3u_source:x.m3u_source||undefined}))}"
h = h.replace(old_export, new_export, 1)

old_tail = "render();loadBundledChannelLogos();"
new_tail = "async function loadBundledM3uDefault(){try{const key='m3upt-base-seeded-v1';if(localStorage.getItem(key)==='1'){render();return}const raw=String(window.M3UPT_BASE_M3U||'');if(!raw){render();return}const found=parseText(raw);if(!loadCustom().length&&found.length){localStorage.setItem(K_CUSTOM,JSON.stringify(found));localStorage.setItem(K_SORT,'manual')}localStorage.setItem(key,'1');render()}catch(e){render()}}\nrender();loadBundledM3uDefault();"
if old_tail not in h:
    raise SystemExit('tail marker not found')
h = h.replace(old_tail, new_tail, 1)
index.write_text(h, encoding='utf-8')

epg = ROOT / 'android/app/src/main/java/com/atsisqual/livetv/EpgMainActivity.java'
e = epg.read_text(encoding='utf-8')
e = e.replace('                        webView.evaluateJavascript(loadDirectModeScript(), null);', '                        // Direct-player mode is disabled in the isolated M3U test app.', 1)
e = e.replace('        if (directPlayerMode && isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);', '        // No update checks in the isolated M3U test app.', 1)
e = e.replace('        if (isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);', '        // No update checks in the isolated M3U test app.', 1)
epg.write_text(e, encoding='utf-8')

print('M3U test app source patch applied.')
