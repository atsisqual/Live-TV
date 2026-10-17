package com.atsisqual.livetv;

import android.webkit.WebView;

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class AdCleaner {
    private AdCleaner() {}

    /*
     * Do NOT inject into rdembed/comeumamao: Cloudflare Challenge Pages require an
     * unmodified browser environment and cannot run inside cross-origin iframes.
     * Native WebView filtering still blocks confirmed ad requests and secondary windows.
     */
    private static final Set<String> PLAYER_ORIGINS = new HashSet<>(Arrays.asList(
            "https://apitotal.net",
            "https://*.apitotal.net",
            "https://wideiptv.top",
            "https://*.wideiptv.top",
            "https://sportsonliine.click",
            "https://*.sportsonliine.click",
            "https://sportsonline.st",
            "https://*.sportsonline.st",
            "https://dlhd.st",
            "https://*.dlhd.st",
            "https://dlive.sx",
            "https://*.dlive.sx",
            "https://dlstreams.st",
            "https://*.dlstreams.st"
    ));

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

          const ATTRS = ['href','src','action','data-href','data-url','data-link','onclick'];

          const isBlockedValue = value => {
            if (!value) return false;
            const s = String(value).toLowerCase();

            if (BLOCKED_DOMAINS.some(d =>
              s === d || s.includes('://' + d) || s.includes('.' + d) || s.includes(d + '/')
            )) return true;

            if (s.includes('s.click.aliexpress.com/e/') && s.includes('dp=')) return true;
            if (s.includes('sccfwxq.png')) return true;
            if (s.includes('rs4k-adbanner.html')) return true;
            if (s.includes('pd.sharethis.com/pd/dtscout')) return true;
            return false;
          };

          const disable = el => {
            try {
              el.style.setProperty('display', 'none', 'important');
              el.style.setProperty('pointer-events', 'none', 'important');
              el.setAttribute('aria-hidden', 'true');
              el.setAttribute('data-livetv-blocked', '1');
            } catch (_) {}
          };

          const attrLooksBlocked = el => {
            if (!el || el.nodeType !== 1 || !el.getAttribute) return false;
            return ATTRS.some(a => isBlockedValue(el.getAttribute(a)));
          };

          const cleanElement = el => {
            if (!el || el.nodeType !== 1) return;
            if (attrLooksBlocked(el)) disable(el);
            if (el.querySelectorAll) {
              el.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]')
                .forEach(node => { if (attrLooksBlocked(node)) disable(node); });
            }
          };

          /*
           * rdembed can return a Cloudflare interstitial, and Cloudflare explicitly does
           * not support Challenge Pages inside cross-origin iframes. When an apitotal
           * wrapper selects rdembed, promote that iframe to the top-level page instead.
           */
          const maybePromoteRdembed = node => {
            if (window.top !== window) return false;
            if (!location.hostname.endsWith('apitotal.net')) return false;

            const frames = [];
            if (node && node.nodeType === 1 && node.matches && node.matches('iframe[src]')) frames.push(node);
            if (node && node.querySelectorAll) node.querySelectorAll('iframe[src]').forEach(f => frames.push(f));

            for (const frame of frames) {
              try {
                const u = new URL(frame.getAttribute('src'), location.href);
                if (u.hostname === 'rdembed.sbs' || u.hostname.endsWith('.rdembed.sbs')) {
                  window.location.replace(u.href);
                  return true;
                }
              } catch (_) {}
            }
            return false;
          };

          const originalOpen = window.open;
          window.open = function(url, ...rest) {
            if (isBlockedValue(url)) return null;
            try { return originalOpen.call(window, url, ...rest); }
            catch (_) { return null; }
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

          const start = () => {
            if (!document.documentElement) {
              setTimeout(start, 10);
              return;
            }

            cleanElement(document.documentElement);
            maybePromoteRdembed(document.documentElement);

            new MutationObserver(mutations => {
              for (const mutation of mutations) {
                if (mutation.type === 'attributes') {
                  cleanElement(mutation.target);
                  if (mutation.attributeName === 'src') maybePromoteRdembed(mutation.target);
                }
                for (const node of mutation.addedNodes || []) {
                  cleanElement(node);
                  maybePromoteRdembed(node);
                }
              }
            }).observe(document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['href','src','onclick','data-href','data-url','data-link']
            });
          };

          start();
        })();
        """;

    static void install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, PLAYER_ORIGINS);
    }
}
