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
            "https://sportsonline.st",
            "https://*.sportsonline.st",
            "https://rdembed.sbs",
            "https://*.rdembed.sbs",
            "https://tossequip.net",
            "https://*.tossequip.net",
            "https://xstream.to",
            "https://*.xstream.to",
            "https://dlhd.st",
            "https://*.dlhd.st",
            "https://dlive.sx",
            "https://*.dlive.sx",
            "https://dlstreams.st",
            "https://*.dlstreams.st",
            "https://comeumamao.monster",
            "https://*.comeumamao.monster"
    ));

    /*
     * 1.3.6 intentionally avoids geometry/z-index heuristics. The previous version
     * could mistake legitimate player layers for transparent advertising overlays.
     * Only explicit, observed ad/tracker destinations are touched here.
     */
    private static final String SCRIPT = """
        (() => {
          'use strict';

          const BLOCKED_DOMAINS = [
            'histats.com',
            'crwdcntrl.net',
            'popcash.net',
            'popads.net',
            'acscdn.com',
            'acscdn.net',
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
            'connatix.com',
            'amung.us',
            'serveproxy.com',
            'protrafficinspector.com'
          ];

          const containsBlockedDomain = value => {
            if (!value) return false;
            const s = String(value).toLowerCase();
            return BLOCKED_DOMAINS.some(d =>
              s === d || s.includes('://' + d) || s.includes('.' + d) || s.includes(d + '/')
            );
          };

          const isBlockedValue = value => {
            if (!value) return false;
            const s = String(value).toLowerCase();

            if (containsBlockedDomain(s)) return true;
            if (s.includes('s.click.aliexpress.com/e/') && s.includes('dp=')) return true;
            if (s.includes('sccfwxq.png')) return true;
            if (s.includes('rs4k-adbanner.html')) return true;
            if (s.includes('pd.sharethis.com/pd/dtscout')) return true;

            return false;
          };

          const originalOpen = window.open;
          window.open = function(url, ...rest) {
            if (isBlockedValue(url)) return null;
            try { return originalOpen.call(window, url, ...rest); }
            catch (_) { return null; }
          };

          const attrs = ['href','src','action','data-href','data-url','data-link','onclick'];

          const attrLooksBlocked = el => {
            if (!el || el.nodeType !== 1 || !el.getAttribute) return false;
            return attrs.some(a => isBlockedValue(el.getAttribute(a)));
          };

          const disable = el => {
            try {
              el.style.setProperty('display', 'none', 'important');
              el.style.setProperty('pointer-events', 'none', 'important');
              el.setAttribute('aria-hidden', 'true');
            } catch (_) {}
          };

          const cleanElement = el => {
            if (!el || el.nodeType !== 1) return;

            if (attrLooksBlocked(el)) disable(el);

            if (el.querySelectorAll) {
              el.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]')
                .forEach(node => {
                  if (attrLooksBlocked(node)) disable(node);
                });
            }
          };

          const sweep = () => {
            try {
              document.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]')
                .forEach(node => {
                  if (attrLooksBlocked(node)) disable(node);
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
                  disable(node);
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
                if (mutation.type === 'attributes') cleanElement(mutation.target);
                for (const node of mutation.addedNodes || []) cleanElement(node);
              }
            }).observe(document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: attrs
            });
          };

          startObserver();
        })();
        """;

    static void install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, PLAYER_ORIGINS);
    }
}
