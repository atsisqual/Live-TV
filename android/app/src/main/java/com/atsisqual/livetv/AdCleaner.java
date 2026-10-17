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
     * 1.3.7: behavioural popup/click-catcher blocking.
     *
     * 1.3.6 only removed elements when an observed ad URL was already visible in
     * href/src/onclick. That misses transparent overlays whose click destination is
     * calculated dynamically by a JavaScript event handler. This version blocks
     * cross-origin popup/link navigation from player frames and removes only a very
     * narrow class of large transparent click-catchers while explicitly preserving
     * known player controls and video layers.
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

          const CONTROL_SELECTOR = [
            'video', 'audio', 'iframe', 'canvas',
            '#channel-player', '#center-play-btn', '#play-pause-btn', '#mute-btn',
            '#pip-btn', '#cast-button', '#fullscreen-btn', '#refresh-btn',
            '.controls-overlay', '.top-bar', '.bottom-bar', '.controls-left',
            '.controls-right', '.control-btn', '.center-play-btn', '.source-select',
            '.source-select-wrap', '.channel-heading', '.channel-heading-live',
            '.watermark', '.loadingOverlay', '.v-overlay-container',
            '[class*="jwplayer"]', '[class*="jw-control"]', '[class*="vjs-"]'
          ].join(',');

          const ATTRS = ['href','src','action','data-href','data-url','data-link','onclick'];

          const toUrl = value => {
            if (!value) return null;
            try { return new URL(String(value), location.href); }
            catch (_) { return null; }
          };

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

          const isExternalHttp = value => {
            const u = toUrl(value);
            if (!u) return false;
            if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
            return u.origin !== location.origin;
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

          const externalAnchor = el => {
            if (!el || el.nodeType !== 1) return null;
            try {
              const a = el.matches && el.matches('a[href]')
                ? el
                : (el.closest ? el.closest('a[href]') : null);
              if (!a) return null;
              return isExternalHttp(a.getAttribute('href')) ? a : null;
            } catch (_) { return null; }
          };

          const isKnownPlayerUi = el => {
            if (!el || el.nodeType !== 1) return false;
            try {
              if (el.matches && el.matches(CONTROL_SELECTOR)) return true;
              if (el.closest && el.closest(CONTROL_SELECTOR)) return true;
              if (el.querySelector && el.querySelector('video,iframe,canvas,audio')) return true;
            } catch (_) {}
            return false;
          };

          const isSuspiciousClickCatcher = el => {
            if (!el || el.nodeType !== 1 || !document.documentElement) return false;
            if (el === document.documentElement || el === document.body) return false;
            if (isKnownPlayerUi(el)) return false;

            let cs, r;
            try {
              cs = getComputedStyle(el);
              r = el.getBoundingClientRect();
            } catch (_) { return false; }

            if (!r || r.width <= 0 || r.height <= 0) return false;

            const vw = Math.max(innerWidth || 0, document.documentElement.clientWidth || 0);
            const vh = Math.max(innerHeight || 0, document.documentElement.clientHeight || 0);
            if (!vw || !vh) return false;

            const coverage = (r.width * r.height) / (vw * vh);
            if (coverage < 0.60) return false;

            const coversCenter = r.left <= vw * 0.5 && r.right >= vw * 0.5 &&
                                 r.top <= vh * 0.5 && r.bottom >= vh * 0.5;
            if (!coversCenter) return false;

            if (!['fixed','absolute','sticky'].includes(cs.position)) return false;
            if (cs.pointerEvents === 'none' || cs.display === 'none' || cs.visibility === 'hidden') return false;

            const z = parseInt(cs.zIndex || '0', 10);
            if (!Number.isNaN(z) && z < 50) return false;

            const opacity = parseFloat(cs.opacity || '1');
            const bg = String(cs.backgroundColor || '').replace(/\s+/g, '').toLowerCase();
            const transparent = opacity <= 0.15 ||
              bg === 'transparent' || bg === 'rgba(0,0,0,0)' || bg.endsWith(',0)');

            const text = String(el.textContent || '').trim();
            if (!transparent || text.length > 24) return false;

            return true;
          };

          const cleanElement = el => {
            if (!el || el.nodeType !== 1) return;

            if (attrLooksBlocked(el) || isSuspiciousClickCatcher(el)) {
              disable(el);
              return;
            }

            if (el.querySelectorAll) {
              el.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]')
                .forEach(node => {
                  if (attrLooksBlocked(node)) disable(node);
                });
            }
          };

          const blockEvent = event => {
            try {
              const path = event.composedPath ? event.composedPath() : [];

              for (const node of path) {
                if (!node || node.nodeType !== 1) continue;

                if (attrLooksBlocked(node)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  disable(node);
                  return false;
                }

                const a = externalAnchor(node);
                if (a) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  if (isSuspiciousClickCatcher(a) || attrLooksBlocked(a)) disable(a);
                  return false;
                }

                if (isSuspiciousClickCatcher(node)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  disable(node);
                  return false;
                }
              }
            } catch (_) {}
            return true;
          };

          const originalOpen = window.open;
          window.open = function(url, ...rest) {
            if (!url) return null;
            if (isBlockedValue(url) || isExternalHttp(url)) return null;
            try { return originalOpen.call(window, url, ...rest); }
            catch (_) { return null; }
          };

          ['pointerdown','mousedown','touchstart','click'].forEach(type => {
            document.addEventListener(type, blockEvent, true);
          });

          const sweep = () => {
            try {
              document.querySelectorAll('body *').forEach(node => {
                if (attrLooksBlocked(node) || isSuspiciousClickCatcher(node)) disable(node);
              });
            } catch (_) {}
          };

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
              attributeFilter: ['href','src','style','class','onclick','data-href','data-url','data-link']
            });

            let passes = 0;
            const timer = setInterval(() => {
              sweep();
              if (++passes >= 40) clearInterval(timer);
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
