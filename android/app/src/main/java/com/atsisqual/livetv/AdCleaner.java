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
     * Player pages are intentionally left in their normal embedding context.
     * In particular, wideiptv Player 1 can behave differently when opened as a
     * top-level page, so API Total remains the wrapper and we only lock its UI
     * to Player 1. rdembed is still promoted when Cloudflare requires top-level.
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
          let apiTotalPlayer1Activated = false;

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

          const isApiTotal = () =>
            location.hostname === 'apitotal.net' || location.hostname.endsWith('.apitotal.net');

          const normalizedText = el =>
            String((el && (el.innerText || el.textContent)) || '')
              .replace(/\s+/g, ' ')
              .trim()
              .toUpperCase();

          const hidePlayerChoiceUi = () => {
            if (!isApiTotal()) return;

            document.querySelectorAll('button,a,[role="button"],[role="option"],[role="menuitem"],option,label')
              .forEach(el => {
                const t = normalizedText(el);
                if (/^PLAYER\s*\d+$/.test(t)) disable(el);
              });

            document.querySelectorAll('h1,h2,h3,h4,p,span,div')
              .forEach(el => {
                const t = normalizedText(el);
                if (!t || t.length > 90) return;
                if (!t.includes('ESCOLHA OUTRO PLAYER') && !t.includes('ESCOLHER OUTRO PLAYER')) return;
                if (el.querySelector && el.querySelector('iframe,video')) return;
                disable(el);
              });
          };

          const ensurePlayer1 = () => {
            if (!isApiTotal() || apiTotalPlayer1Activated) {
              hidePlayerChoiceUi();
              return;
            }

            /* API Total currently defaults to Player 1 by mounting its iframe. */
            const liveFrame = [...document.querySelectorAll('iframe[src]')].find(frame => {
              try {
                const u = new URL(frame.getAttribute('src'), location.href);
                return /^https?:$/.test(u.protocol) && !isBlockedValue(u.href);
              } catch (_) { return false; }
            });

            if (liveFrame) {
              apiTotalPlayer1Activated = true;
              hidePlayerChoiceUi();
              return;
            }

            /* Fallback for layouts that wait for a selection before creating the iframe. */
            const controls = [...document.querySelectorAll('button,a,[role="button"],[role="option"],[role="menuitem"],option,label')];
            const first = controls.find(el => /^PLAYER\s*1$/.test(normalizedText(el)));
            if (first) {
              apiTotalPlayer1Activated = true;
              try { first.click(); } catch (_) {}
            }
            hidePlayerChoiceUi();
          };

          /*
           * Cloudflare Challenge Pages do not work reliably inside a cross-origin
           * iframe. If API Total's selected Player 1 is rdembed, promote only that
           * iframe after API Total has selected it.
           */
          const maybePromoteRdembed = node => {
            if (window.top !== window) return false;
            if (!isApiTotal()) return false;

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

                if (isApiTotal() && node && node.nodeType === 1) {
                  const t = normalizedText(node);
                  if (/^PLAYER\s*[2-9]\d*$/.test(t)) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    return false;
                  }
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
            ensurePlayer1();
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
              ensurePlayer1();
            }).observe(document.documentElement, {
              childList: true,
              subtree: true,
              attributes: true,
              attributeFilter: ['href','src','onclick','data-href','data-url','data-link']
            });

            setTimeout(ensurePlayer1, 250);
            setTimeout(ensurePlayer1, 800);
            setTimeout(ensurePlayer1, 1800);
          };

          start();
        })();
        """;

    static void install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, PLAYER_ORIGINS);
    }
}
