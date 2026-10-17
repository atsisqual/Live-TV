package com.atsisqual.livetv;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AdCleaner {
    private AdCleaner() {}

    private static final Set<String> PLAYER_ORIGINS = new HashSet<>(Arrays.asList(
            "https://apitotal.net",
            "https://*.apitotal.net",
            "https://wideiptv.top",
            "https://*.wideiptv.top",
            "https://sportsonliine.click",
            "https://*.sportsonliine.click",
            "https://rdembed.sbs",
            "https://*.rdembed.sbs",
            "https://tossequip.net",
            "https://*.tossequip.net",
            "https://dembed.st",
            "https://*.dembed.st",
            "https://dlive.sx",
            "https://*.dlive.sx",
            "https://dlstreams.st",
            "https://*.dlstreams.st"
    ));

    /*
     * Runs at document-start in matching frames, including cross-origin iframes.
     * It does not inspect or alter media URLs. It only neutralizes obvious ad-click
     * destinations and transparent click-jacking overlays.
     */
    private static final String SCRIPT = """
        (() => {
          'use strict';

          const BLOCKED = [
            'aliexpress.com',
            'histats.com',
            'crwdcntrl.net',
            'popcash.net',
            'popads.net',
            'adsco.re',
            'adexchangerapid.com',
            'adbpage.com',
            'usrpubtrk.com',
            'cdn4ads.com',
            'premiumvertising.com',
            'xadsmart.com',
            'profitableratecpmnetwork.com',
            'rtmark.net',
            'asdfix.com',
            'rbtget.com',
            'rbtrgr.com',
            'bluetier.top',
            'connatix.com',
            'amung.us',
            'serveproxy.com',
            'protrafficinspector.com'
          ];

          const blockedText = value => {
            if (!value) return false;
            const s = String(value).toLowerCase();
            return BLOCKED.some(d => s === d || s.includes('://' + d) || s.includes('.' + d) || s.includes(d + '/'));
          };

          const originalOpen = window.open;
          window.open = function(url, ...rest) {
            if (blockedText(url)) return null;
            try { return originalOpen.call(window, url, ...rest); }
            catch (_) { return null; }
          };

          const attrLooksBlocked = el => {
            if (!el || el.nodeType !== 1) return false;
            const attrs = ['href','src','action','data-href','data-url','data-link','onclick'];
            return attrs.some(a => blockedText(el.getAttribute && el.getAttribute(a)));
          };

          const hide = el => {
            try {
              el.style.setProperty('display', 'none', 'important');
              el.style.setProperty('pointer-events', 'none', 'important');
              el.setAttribute('aria-hidden', 'true');
            } catch (_) {}
          };

          const isSuspiciousOverlay = el => {
            if (!el || el.nodeType !== 1 || !document.documentElement) return false;
            if (el === document.documentElement || el === document.body) return false;
            if (el.matches && el.matches('video,iframe,canvas,img,button,input,select,textarea')) return false;
            if (el.querySelector && el.querySelector('video,iframe,canvas')) return false;

            let cs, r;
            try {
              cs = getComputedStyle(el);
              r = el.getBoundingClientRect();
            } catch (_) { return false; }

            if (!r || r.width <= 0 || r.height <= 0) return false;
            const vw = Math.max(innerWidth || 0, document.documentElement.clientWidth || 0);
            const vh = Math.max(innerHeight || 0, document.documentElement.clientHeight || 0);
            if (!vw || !vh) return false;

            const cover = (r.width * r.height) / (vw * vh);
            if (cover < 0.45) return false;

            const pos = cs.position;
            if (pos !== 'fixed' && pos !== 'absolute') return false;
            if (cs.pointerEvents === 'none' || cs.visibility === 'hidden' || cs.display === 'none') return false;

            const z = parseInt(cs.zIndex || '0', 10);
            const text = (el.textContent || '').trim();
            const clickable = el.tagName === 'A' || !!el.getAttribute('onclick') || cs.cursor === 'pointer';
            const transparent = parseFloat(cs.opacity || '1') <= 0.05 ||
              cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent';

            return clickable && transparent && text.length < 8 && (Number.isNaN(z) || z >= 1);
          };

          const cleanElement = el => {
            if (!el || el.nodeType !== 1) return;

            if (attrLooksBlocked(el)) {
              hide(el);
              return;
            }

            if (el.querySelectorAll) {
              el.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]').forEach(node => {
                if (attrLooksBlocked(node)) hide(node);
              });
            }

            if (isSuspiciousOverlay(el)) hide(el);
          };

          const sweep = () => {
            try {
              document.querySelectorAll('body *').forEach(el => {
                if (attrLooksBlocked(el) || isSuspiciousOverlay(el)) hide(el);
              });
            } catch (_) {}
          };

          document.addEventListener('click', event => {
            try {
              const path = event.composedPath ? event.composedPath() : [];
              for (const node of path) {
                if (attrLooksBlocked(node)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  hide(node);
                  return false;
                }
              }
            } catch (_) {}
          }, true);

          const startObserver = () => {
            if (!document.documentElement) {
              setTimeout(startObserver, 10);
              return;
            }
            sweep();
            new MutationObserver(mutations => {
              for (const mutation of mutations) {
                for (const node of mutation.addedNodes || []) cleanElement(node);
              }
            }).observe(document.documentElement, {childList:true, subtree:true, attributes:true, attributeFilter:['href','src','style','class','onclick','data-href','data-url']});

            let runs = 0;
            const timer = setInterval(() => {
              sweep();
              if (++runs >= 30) clearInterval(timer);
            }, 500);
          };

          startObserver();
        })();
        """;

    static void install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, PLAYER_ORIGINS);
    }
}
