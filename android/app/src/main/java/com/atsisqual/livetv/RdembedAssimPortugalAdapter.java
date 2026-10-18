package com.atsisqual.livetv;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.webkit.WebView;

import org.json.JSONArray;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicitly authorized adapter for https://v2.rdembed.sbs/assimeportugal.
 *
 * Scope is intentionally exact: no other rdembed route is automated. v1.6.34 adds
 * one bounded native tap at the visual centre of the WebView after the cross-origin
 * player has had time to appear. The point is derived from the WebView dimensions,
 * never from device-specific coordinates. No cross-origin DOM, media URL, request
 * header, cookie, token or playlist endpoint is inspected.
 */
final class RdembedAssimPortugalAdapter implements ProviderAdapter {

    private static final String HOST = "v2.rdembed.sbs";
    private static final String PATH = "/assimeportugal";
    private static final long NATIVE_CENTER_TAP_DELAY_MS = 2800L;
    private static final long NATIVE_CENTER_TAP_RETRY_MS = 700L;
    private static final long[] DIAGNOSTIC_DELAYS_MS = {0L, 2000L, 4000L, 8000L, 12000L};

    private static final Set<Capability> CAPABILITIES = Collections.unmodifiableSet(
            EnumSet.of(Capability.AUTOSTART, Capability.AD_SUPPRESSION));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean diagnosticsScheduled;
    private boolean nativeCenterTapScheduled;

    @Override
    public String id() {
        return "rdembed-assimeportugal-authorized";
    }

    @Override
    public boolean matches(Uri uri) {
        if (uri == null) return false;
        String host = uri.getHost();
        String path = uri.getPath();
        return HOST.equalsIgnoreCase(host == null ? "" : host)
                && PATH.equals(normalizePath(path));
    }

    @Override
    public Set<Capability> capabilities() {
        return CAPABILITIES;
    }

    @Override
    public void onPageStarted(WebView view, Uri uri) {
        if (!matches(uri)) return;
        handler.removeCallbacksAndMessages(null);
        diagnosticsScheduled = false;
        nativeCenterTapScheduled = false;
    }

    @Override
    public void onPageReady(WebView view, Uri uri) {
        installAuthorizedSameOriginAutomation(view, uri);
        scheduleNativeCenterTap(view, uri);
        scheduleDiagnostics(view, uri);
    }

    @Override
    public void onPageFinished(WebView view, Uri uri) {
        installAuthorizedSameOriginAutomation(view, uri);
        scheduleNativeCenterTap(view, uri);
        scheduleDiagnostics(view, uri);
    }

    @Override
    public void onLeaving(WebView view) {
        handler.removeCallbacksAndMessages(null);
        diagnosticsScheduled = false;
        nativeCenterTapScheduled = false;
    }

    private void installAuthorizedSameOriginAutomation(WebView view, Uri uri) {
        if (view == null || !matches(uri)) return;
        view.evaluateJavascript(AUTOMATION_SCRIPT, null);
    }

    private void scheduleNativeCenterTap(WebView view, Uri uri) {
        if (view == null || !matches(uri) || nativeCenterTapScheduled) return;
        nativeCenterTapScheduled = true;
        ProviderDiagnostics.recordAdapterDiagnosticIfActive("native-center-tap-scheduled");
        handler.postDelayed(() -> performNativeCenterTap(view, true), NATIVE_CENTER_TAP_DELAY_MS);
    }

    private void performNativeCenterTap(WebView view, boolean allowRetry) {
        if (view == null || !matchesCurrentUrl(view)) return;
        int width = view.getWidth();
        int height = view.getHeight();
        if (width <= 0 || height <= 0 || !view.isShown()) {
            if (allowRetry) {
                ProviderDiagnostics.recordAdapterDiagnosticIfActive("native-center-tap-deferred");
                handler.postDelayed(() -> performNativeCenterTap(view, false), NATIVE_CENTER_TAP_RETRY_MS);
            } else {
                ProviderDiagnostics.recordAdapterDiagnosticIfActive("native-center-tap-unhandled");
            }
            return;
        }

        float x = width * 0.5f;
        float y = height * 0.5f;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(downTime, downTime + 80L, MotionEvent.ACTION_UP, x, y, 0);
        boolean handled = false;
        try {
            view.requestFocus();
            handled = view.onTouchEvent(down);
            handled = view.onTouchEvent(up) || handled;
        } catch (Exception ignored) {
            handled = false;
        } finally {
            down.recycle();
            up.recycle();
        }
        ProviderDiagnostics.recordAdapterDiagnosticIfActive(
                handled ? "native-center-tap-dispatched" : "native-center-tap-unhandled");
    }

    private boolean matchesCurrentUrl(WebView view) {
        try {
            String raw = view.getUrl();
            return raw != null && matches(Uri.parse(raw));
        } catch (Exception ignored) {
            return false;
        }
    }

    private void scheduleDiagnostics(WebView view, Uri uri) {
        if (view == null || !matches(uri) || diagnosticsScheduled) return;
        diagnosticsScheduled = true;
        for (long delay : DIAGNOSTIC_DELAYS_MS) {
            handler.postDelayed(() -> captureDiagnosticSnapshot(view), delay);
        }
    }

    private void captureDiagnosticSnapshot(WebView view) {
        if (view == null || !matchesCurrentUrl(view)) return;
        view.evaluateJavascript(DIAGNOSTIC_SCRIPT, this::recordDiagnosticSnapshot);
    }

    private void recordDiagnosticSnapshot(String rawResult) {
        if (rawResult == null || rawResult.trim().isEmpty() || "null".equals(rawResult.trim())) return;
        try {
            JSONArray values = new JSONArray(rawResult);
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i, "").trim();
                if (!value.isEmpty()) ProviderDiagnostics.recordAdapterDiagnosticIfActive(value);
            }
        } catch (Exception ignored) {
            ProviderDiagnostics.recordAdapterDiagnosticIfActive("scan-error");
        }
    }

    private static String normalizePath(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "/";
        String path = raw.trim().toLowerCase(Locale.ROOT);
        if (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    private static final String AUTOMATION_SCRIPT =
            "(function(){try{"
                    + "if(location.hostname!=='v2.rdembed.sbs'||location.pathname.replace(/\\/$/,'')!=='/assimeportugal')return;"
                    + "if(window.__liveTvRdembedAutomationV1634)return;"
                    + "window.__liveTvRdembedAutomationV1634=true;window.__liveTvRdembedClickAttempts=0;"
                    + "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();"
                    + "return s.display!=='none'&&s.visibility!=='hidden'&&s.pointerEvents!=='none'&&r.width>0&&r.height>0;}"
                    + "function playing(){var v=document.getElementById('channel-player');return !!(v&&!v.paused&&!v.ended&&v.readyState>=2);}"
                    + "function tryPlay(){if(playing())return;var b=document.getElementById('center-play-btn');if(visible(b)){window.__liveTvRdembedClickAttempts++;b.click();}}"
                    + "function norm(v){return String(v||'').toLowerCase().normalize('NFD').replace(/[\\u0300-\\u036f]/g,'').replace(/\\s+/g,' ').trim();}"
                    + "var skip=['skip ad','skip ads','close ad','close advertisement','pular anuncio','fechar anuncio','continuar para o video'];"
                    + "function adActionText(el){return norm((el.innerText||el.textContent||'')+' '+(el.getAttribute('aria-label')||'')+' '+(el.getAttribute('title')||''));}"
                    + "function scanAdActions(){document.querySelectorAll('button,a,[role=button]').forEach(function(el){"
                    + "if(!visible(el)||el.getAttribute('data-livetv-ad-action')==='1')return;var t=adActionText(el);"
                    + "if(skip.some(function(x){return t===x||t.indexOf(x)>=0;})){el.setAttribute('data-livetv-ad-action','1');el.click();}});"
                    + "document.querySelectorAll('[data-ad-container],[data-advertisement],.advertisement,.ad-overlay,#ad-overlay,#ad-container,.vast-ad,.ima-ad-container').forEach(function(el){"
                    + "if(el&&el.parentNode){el.style.setProperty('display','none','important');el.setAttribute('aria-hidden','true');}});"
                    + "}"
                    + "[0,4000,8000].forEach(function(ms){setTimeout(tryPlay,ms);});"
                    + "scanAdActions();"
                    + "var obs=new MutationObserver(scanAdActions);obs.observe(document.documentElement,{childList:true,subtree:true});"
                    + "var ticks=0;var timer=setInterval(function(){scanAdActions();if(++ticks>=90){clearInterval(timer);obs.disconnect();}},500);"
                    + "}catch(e){}})();";

    private static final String DIAGNOSTIC_SCRIPT =
            "(function(){try{"
                    + "if(location.hostname!=='v2.rdembed.sbs'||location.pathname.replace(/\\/$/,'')!=='/assimeportugal')return [];"
                    + "var total=0,same=0,cross=0,playDepth=-1,playing=false;"
                    + "function cap(n){return n>=4?'3plus':String(Math.max(0,n));}"
                    + "function walk(doc,depth){if(!doc||depth>3)return;"
                    + "if(playDepth<0&&doc.getElementById('center-play-btn'))playDepth=depth;"
                    + "var v=doc.getElementById('channel-player');if(v&&!v.paused&&!v.ended&&v.readyState>=2)playing=true;"
                    + "var frames=doc.querySelectorAll('iframe');for(var i=0;i<frames.length;i++){total++;var child=null;"
                    + "try{child=frames[i].contentDocument;}catch(e){child=null;}"
                    + "if(child){same++;walk(child,depth+1);}else{cross++;}}}"
                    + "walk(document,0);"
                    + "var top=document.getElementById('center-play-btn')?'present':'absent';"
                    + "var clicks=Number(window.__liveTvRdembedClickAttempts||0);"
                    + "var depth=playDepth<0?'none':(playDepth>=3?'3plus':String(playDepth));"
                    + "return ['top-play-'+top,'frames-'+cap(total),'same-origin-frames-'+cap(same),'cross-origin-frames-'+cap(cross),'play-depth-'+depth,'click-attempts-'+cap(clicks),'playing-'+(playing?'yes':'no')];"
                    + "}catch(e){return ['scan-error'];}})();";
}
