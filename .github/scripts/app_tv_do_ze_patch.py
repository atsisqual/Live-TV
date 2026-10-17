from pathlib import Path
import shutil


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"{label}: expected text not found; refusing unsafe patch")
    return text.replace(old, new, 1)


# App name / launcher label.
manifest_path = Path("android/app/src/main/AndroidManifest.xml")
manifest = manifest_path.read_text(encoding="utf-8")
manifest = replace_once(manifest, 'android:label="Live TV"', 'android:label="TV do Zé"', "manifest label")
manifest_path.write_text(manifest, encoding="utf-8")

# Home UI branding + Auto-update toggle.
index_path = Path("android/app/src/main/assets/index.html")
index = index_path.read_text(encoding="utf-8")
index = replace_once(index, "<title>Live TV</title>", "<title>TV do Zé</title>", "HTML title")
index = replace_once(index, '<div class="brandtitle">Live TV</div>', '<div class="brandtitle">TV do Zé</div>', "brand title")
index = replace_once(
    index,
    '    <button data-menu="favorites">★ Mostrar favoritos</button>\n    <button class="danger" data-menu="clear">Apagar canais importados</button>',
    '    <button data-menu="favorites">★ Mostrar favoritos</button>\n    <button data-menu="autoupdate" id="autoUpdateMenu">✓ Procurar atualizações automaticamente</button>\n    <button class="danger" data-menu="clear">Apagar canais importados</button>',
    "auto-update menu button",
)
bridge_helpers = (
    "function readAutoUpdate(){try{return !window.LiveTvSettings||!!window.LiveTvSettings.isAutoUpdateEnabled()}catch(e){return true}}\n"
    "function refreshAutoUpdateMenu(){const b=$('autoUpdateMenu');if(!b)return;const on=readAutoUpdate();b.textContent=(on?'✓ ':'○ ')+'Procurar atualizações automaticamente'}\n"
    "function toggleAutoUpdate(){try{if(!window.LiveTvSettings||typeof window.LiveTvSettings.setAutoUpdateEnabled!=='function'){showToast('Definição indisponível');return}const next=!readAutoUpdate();window.LiveTvSettings.setAutoUpdateEnabled(next);const b=$('autoUpdateMenu');if(b)b.textContent=(next?'✓ ':'○ ')+'Procurar atualizações automaticamente';toggleMenu(false);showToast(next?'Atualizações automáticas ativadas':'Atualizações automáticas desativadas')}catch(e){showToast('Não foi possível alterar a definição')} }\n"
)
index = replace_once(
    index,
    "$('menuBtn').onclick=e=>{e.stopPropagation();toggleMenu()};",
    bridge_helpers + "$('menuBtn').onclick=e=>{e.stopPropagation();refreshAutoUpdateMenu();toggleMenu()};",
    "menu settings helpers",
)
index = replace_once(
    index,
    "if(a==='favorites'){activeFilter='favorites';toggleMenu(false);render()}if(a==='clear')confirmClear()",
    "if(a==='favorites'){activeFilter='favorites';toggleMenu(false);render()}if(a==='autoupdate')toggleAutoUpdate();if(a==='clear')confirmClear()",
    "menu auto-update handler",
)
index_path.write_text(index, encoding="utf-8")

# Native auto-update preference and JS bridge.
main_path = Path("android/app/src/main/java/com/atsisqual/livetv/MainActivity.java")
main = main_path.read_text(encoding="utf-8")
main = replace_once(
    main,
    "import android.webkit.CookieManager;\n",
    "import android.webkit.CookieManager;\nimport android.webkit.JavascriptInterface;\n",
    "JavascriptInterface import",
)
main = replace_once(
    main,
    '    private static final String UPDATE_MANIFEST =\n            "https://atsisqual.github.io/Live-TV/downloads/version.json";\n',
    '    private static final String UPDATE_MANIFEST =\n            "https://atsisqual.github.io/Live-TV/downloads/version.json";\n    private static final String PREFS = "live-tv-settings";\n    private static final String PREF_AUTO_UPDATE = "auto-update-enabled";\n',
    "update preference constants",
)
main = replace_once(
    main,
    "        web.loadUrl(HOME_URL);\n        checkForUpdates();\n",
    "        web.loadUrl(HOME_URL);\n        if (isAutoUpdateEnabled()) checkForUpdates();\n",
    "startup update check",
)
main = replace_once(
    main,
    "        AdCleaner.install(web);\n\n        web.setWebViewClient",
    '        AdCleaner.install(web);\n        web.addJavascriptInterface(new AppSettingsBridge(), "LiveTvSettings");\n\n        web.setWebViewClient',
    "settings JS bridge install",
)
main = replace_once(
    main,
    "    private void checkForUpdates() {\n        background.execute(() -> {",
    "    private void checkForUpdates() {\n        if (!isAutoUpdateEnabled()) return;\n        background.execute(() -> {",
    "update check preference guard",
)
main = replace_once(
    main,
    "                pendingUpdate = new UpdateInfo(versionCode, versionName, apkUrl, notes);",
    "                if (!isAutoUpdateEnabled()) return;\n                pendingUpdate = new UpdateInfo(versionCode, versionName, apkUrl, notes);",
    "in-flight update preference guard",
)
settings_block = "\n".join([
    "    protected boolean isAutoUpdateEnabled() {",
    "        return getSharedPreferences(PREFS, MODE_PRIVATE)",
    "                .getBoolean(PREF_AUTO_UPDATE, true);",
    "    }",
    "",
    "    private void setAutoUpdateEnabled(boolean enabled) {",
    "        getSharedPreferences(PREFS, MODE_PRIVATE).edit()",
    "                .putBoolean(PREF_AUTO_UPDATE, enabled)",
    "                .apply();",
    "        if (!enabled) {",
    "            pendingUpdate = null;",
    "            updateDialogShown = false;",
    "            return;",
    "        }",
    "        updateDialogShown = false;",
    "        checkForUpdates();",
    "    }",
    "",
    "    private final class AppSettingsBridge {",
    "        @JavascriptInterface",
    "        public boolean isAutoUpdateEnabled() {",
    "            return MainActivity.this.isAutoUpdateEnabled();",
    "        }",
    "",
    "        @JavascriptInterface",
    "        public void setAutoUpdateEnabled(boolean enabled) {",
    "            mainHandler.post(() -> {",
    "                if (web == null || !isLocalHome(web.getUrl())) return;",
    "                MainActivity.this.setAutoUpdateEnabled(enabled);",
    "            });",
    "        }",
    "    }",
    "",
    "    private boolean isTrustedUpdateUrl(String value) {",
])
main = replace_once(main, "    private boolean isTrustedUpdateUrl(String value) {", settings_block, "settings bridge implementation")
main = replace_once(
    main,
    "    private void maybeShowPendingUpdate() {\n        if (pendingUpdate == null || updateDialogShown || isFinishing()) return;",
    "    private void maybeShowPendingUpdate() {\n        if (!isAutoUpdateEnabled()) return;\n        if (pendingUpdate == null || updateDialogShown || isFinishing()) return;",
    "pending update preference guard",
)
main_path.write_text(main, encoding="utf-8")

# Direct-player update checks obey the same preference.
epg_path = Path("android/app/src/main/java/com/atsisqual/livetv/EpgMainActivity.java")
epg = epg_path.read_text(encoding="utf-8")
epg = replace_once(
    epg,
    "        if (directPlayerMode) handler.postDelayed(this::checkDirectModeUpdate, 1400);",
    "        if (directPlayerMode && isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);",
    "direct mode startup update guard",
)
epg = replace_once(
    epg,
    "        if (webView != null) webView.loadUrl(url);\n        handler.postDelayed(this::checkDirectModeUpdate, 1400);",
    "        if (webView != null) webView.loadUrl(url);\n        if (isAutoUpdateEnabled()) handler.postDelayed(this::checkDirectModeUpdate, 1400);",
    "direct mode activation update guard",
)
epg = replace_once(
    epg,
    "    private void checkDirectModeUpdate() {\n        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;",
    "    private void checkDirectModeUpdate() {\n        if (!isAutoUpdateEnabled()) return;\n        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;",
    "direct mode update preference guard",
)
epg = replace_once(
    epg,
    "    private void showDirectModeUpdate(int versionCode, String versionName, String apkUrl, String notes) {\n        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;",
    "    private void showDirectModeUpdate(int versionCode, String versionName, String apkUrl, String notes) {\n        if (!isAutoUpdateEnabled()) return;\n        if (!directPlayerMode || directUpdateDialogShown || isFinishing()) return;",
    "direct mode update dialog guard",
)
epg_path.write_text(epg, encoding="utf-8")

# Hide a small top-left LIVE badge on wideiptv when it is DOM UI, not video pixels.
cleaner_path = Path("android/app/src/main/java/com/atsisqual/livetv/AdCleaner.java")
cleaner = cleaner_path.read_text(encoding="utf-8")
live_helper = r'''          const hideWideIptvLiveBadge = root => {
            if (!IS_WIDEIPTV) return;
            const scope = root && root.querySelectorAll ? root : document;
            const maybeHide = el => {
              try {
                if (!el || el.nodeType !== 1 || isMediaContainer(el)) return;
                const text = (el.innerText || el.textContent || '').replace(/\s+/g, ' ').trim().toUpperCase();
                if (text !== 'LIVE' && text !== '● LIVE' && text !== 'LIVE ●') return;
                const cs = getComputedStyle(el);
                const r = el.getBoundingClientRect();
                const vw = Math.max(document.documentElement.clientWidth || 0, innerWidth || 0);
                const vh = Math.max(document.documentElement.clientHeight || 0, innerHeight || 0);
                if (!vw || !vh || r.width <= 0 || r.height <= 0 || r.width > 180 || r.height > 90) return;
                if (r.left > Math.max(180, vw * 0.30) || r.top > Math.max(110, vh * 0.20)) return;
                const zi = parseInt(cs.zIndex, 10);
                const overlay = cs.position === 'fixed' || cs.position === 'absolute' || (Number.isFinite(zi) && zi > 0);
                if (overlay) disable(el);
              } catch (_) {}
            };
            try {
              if (scope.nodeType === 1) maybeHide(scope);
              scope.querySelectorAll('div,span,p,label,b,strong').forEach(maybeHide);
            } catch (_) {}
          };

'''
cleaner = replace_once(cleaner, "          const selectRequestedPlayer = () => {", live_helper + "          const selectRequestedPlayer = () => {", "wideiptv LIVE helper")
cleaner = replace_once(cleaner, "            forceSound(document.documentElement);\n            selectRequestedPlayer();", "            forceSound(document.documentElement);\n            hideWideIptvLiveBadge(document.documentElement);\n            selectRequestedPlayer();", "initial LIVE cleanup")
cleaner = replace_once(cleaner, "                  forceSound(mutation.target);\n                  selectRequestedPlayer();", "                  forceSound(mutation.target);\n                  hideWideIptvLiveBadge(mutation.target);\n                  selectRequestedPlayer();", "attribute LIVE cleanup")
cleaner = replace_once(cleaner, "                  forceSound(node);\n                  selectRequestedPlayer();", "                  forceSound(node);\n                  hideWideIptvLiveBadge(node);\n                  selectRequestedPlayer();", "added-node LIVE cleanup")
cleaner = replace_once(cleaner, "                forceSound(document);\n                soundAttempts++;", "                forceSound(document);\n                hideWideIptvLiveBadge(document);\n                soundAttempts++;", "timed LIVE cleanup")
cleaner_path.write_text(cleaner, encoding="utf-8")

# Selected TV do Zé icon.
staged_icon = Path(".staging/tv_do_ze_icon.png")
if not staged_icon.is_file():
    raise SystemExit("staged TV do Zé icon is missing")
icon_dir = Path("android/app/src/main/res/drawable-nodpi")
icon_dir.mkdir(parents=True, exist_ok=True)
shutil.copyfile(staged_icon, icon_dir / "tv_do_ze_icon_bitmap.png")
Path("android/app/src/main/res/drawable/tv_do_ze_icon.xml").write_text(
    """<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/tv_do_ze_icon_bitmap"
    android:gravity="fill"
    android:filter="true" />
""",
    encoding="utf-8",
)
legacy_icon = """<?xml version="1.0" encoding="utf-8"?>
<selector xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@drawable/tv_do_ze_icon" />
</selector>
"""
for p in [Path("android/app/src/main/res/mipmap-anydpi/ic_launcher.xml"), Path("android/app/src/main/res/mipmap-anydpi/ic_launcher_round.xml")]:
    p.write_text(legacy_icon, encoding="utf-8")
adaptive_icon = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/live_tv_bg" />
    <foreground android:drawable="@drawable/tv_do_ze_icon" />
</adaptive-icon>
"""
for p in [Path("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"), Path("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")]:
    p.write_text(adaptive_icon, encoding="utf-8")
shutil.rmtree(".staging")

# Release version.
gradle_path = Path("android/app/build.gradle")
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode 33", "versionCode 34", "versionCode")
gradle = replace_once(gradle, "versionName '1.6.6'", "versionName '1.6.7'", "versionName")
gradle_path.write_text(gradle, encoding="utf-8")
