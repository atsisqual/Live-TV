package com.atsisqual.livetv;

import android.net.Uri;
import android.webkit.WebView;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Explicitly authorized adapter for https://v2.rdembed.sbs/assimeportugal.
 *
 * Scope is intentionally exact: no other rdembed route is automated. The adapter
 * only manipulates the same-origin page DOM. It never inspects cross-origin iframe
 * contents, media URLs, request headers, cookies, tokens or playlist endpoints.
 */
final class RdembedAssimPortugalAdapter implements ProviderAdapter {

    private static final String HOST = "v2.rdembed.sbs";
    private static final String PATH = "/assimeportugal";

    private static final Set<Capability> CAPABILITIES = Collections.unmodifiableSet(
            EnumSet.of(Capability.AUTOSTART, Capability.AD_SUPPRESSION));

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
    public void onPageReady(WebView view, Uri uri) {
        installAuthorizedAutomation(view, uri);
    }

    @Override
    public void onPageFinished(WebView view, Uri uri) {
        installAuthorizedAutomation(view, uri);
    }

    private void installAuthorizedAutomation(WebView view, Uri uri) {
        if (view == null || !matches(uri)) return;
        view.evaluateJavascript(AUTOMATION_SCRIPT, null);
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
                    + "if(window.__liveTvRdembedAutomationV1632)return;"
                    + "window.__liveTvRdembedAutomationV1632=true;"
                    + "function visible(el){if(!el)return false;var s=getComputedStyle(el),r=el.getBoundingClientRect();"
                    + "return s.display!=='none'&&s.visibility!=='hidden'&&s.pointerEvents!=='none'&&r.width>0&&r.height>0;}"
                    + "function playing(){var v=document.getElementById('channel-player');return !!(v&&!v.paused&&!v.ended&&v.readyState>=2);}"
                    + "function tryPlay(){if(playing())return;var b=document.getElementById('center-play-btn');if(visible(b))b.click();}"
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
}
