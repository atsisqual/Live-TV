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
     * unmodified browser environment. Native WebView filtering still blocks confirmed
     * ad requests and secondary windows.
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
            'histats.com','crwdcntrl.net','popcash.net','popads.net','acscdn.com','acscdn.net',
            'adsco.re','adexchangerapid.com','adbpage.com','usrpubtrk.com','cdn4ads.com',
            'premiumvertising.com','xadsmart.com','profitableratecpmnetwork.com','rtmark.net',
            'asdfix.com','rbtget.com','rbtrgr.com','connatix.com','amung.us','serveproxy.com',
            'protrafficinspector.com'
          ];

          const ATTRS = ['href','src','action','data-href','data-url','data-link','onclick'];
          const host = String(location.hostname || '').toLowerCase();
          const IS_DLIVE =
            host === 'dlive.sx' || host.endsWith('.dlive.sx') ||
            host === 'dlhd.st' || host.endsWith('.dlhd.st') ||
            host === 'dlstreams.st' || host.endsWith('.dlstreams.st');

          const REQUESTED_PLAYER = (() => {
            try {
              const m = String(location.hash || '').match(/(?:^#|[&#])livetv-player=(\\d{1,2})(?:&|$)/i);
              const n = m ? parseInt(m[1], 10) : 1;
              return Number.isFinite(n) && n >= 1 && n <= 20 ? n : 1;
            } catch (_) { return 1; }
          })();
          let playerSelectionDone = REQUESTED_PLAYER <= 1;
          let playerSelectionBusy = false;

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
              el.style.setProperty('visibility', 'hidden', 'important');
              el.style.setProperty('pointer-events', 'none', 'important');
              el.setAttribute('aria-hidden', 'true');
              el.setAttribute('data-livetv-blocked', '1');
            } catch (_) {}
          };

          const attrLooksBlocked = el => {
            if (!el || el.nodeType !== 1 || !el.getAttribute) return false;
            return ATTRS.some(a => isBlockedValue(el.getAttribute(a)));
          };

          const isMediaContainer = el => {
            try {
              if (!el || el.nodeType !== 1) return false;
              if (el.matches && el.matches('video,audio,iframe,source,track')) return true;
              return !!(el.querySelector && el.querySelector('video,audio,iframe,source,track'));
            } catch (_) { return false; }
          };

          /* Remove transparent click-catchers without touching the actual media tree. */
          const looksLikeClickCurtain = el => {
            if (!IS_DLIVE || !el || el.nodeType !== 1 || !window.getComputedStyle) return false;
            if (el === document.documentElement || el === document.body || isMediaContainer(el)) return false;
            try {
              const cs = getComputedStyle(el);
              if (cs.display === 'none' || cs.visibility === 'hidden' || cs.pointerEvents === 'none') return false;
              if (cs.position !== 'fixed' && cs.position !== 'absolute') return false;
              const r = el.getBoundingClientRect();
              const vw = Math.max(document.documentElement.clientWidth || 0, innerWidth || 0);
              const vh = Math.max(document.documentElement.clientHeight || 0, innerHeight || 0);
              if (!vw || !vh) return false;
              const coverage = (Math.max(0, r.width) * Math.max(0, r.height)) / (vw * vh);
              if (coverage < 0.55) return false;
              const zi = parseInt(cs.zIndex, 10);
              const clickable = el.hasAttribute('onclick') || el.hasAttribute('href') ||
                el.getAttribute('role') === 'button' || cs.cursor === 'pointer' || el.tagName === 'A';
              const nearlyInvisible = parseFloat(cs.opacity || '1') < 0.25 ||
                cs.backgroundColor === 'rgba(0, 0, 0, 0)' || cs.backgroundColor === 'transparent';
              const noUsefulText = !(el.innerText || '').trim();
              return Number.isFinite(zi) && zi >= 5 && (clickable || nearlyInvisible || noUsefulText);
            } catch (_) { return false; }
          };

          const cleanDlive = root => {
            if (!IS_DLIVE) return;
            const scope = root && root.querySelectorAll ? root : document;
            try {
              if (scope.nodeType === 1 && looksLikeClickCurtain(scope)) disable(scope);
              scope.querySelectorAll('div,a,span,button,ins').forEach(el => {
                if (looksLikeClickCurtain(el)) disable(el);
              });
            } catch (_) {}
            try {
              scope.querySelectorAll('a[target="_blank"],a[target="_new"],form[target="_blank"]').forEach(el => {
                el.removeAttribute('target');
                if (el.hasAttribute('onclick') && !isMediaContainer(el)) el.removeAttribute('onclick');
              });
            } catch (_) {}
          };

          const cleanElement = el => {
            if (!el || el.nodeType !== 1) return;
            if (attrLooksBlocked(el) || looksLikeClickCurtain(el)) disable(el);
            if (el.querySelectorAll) {
              el.querySelectorAll('[href],[src],[action],[data-href],[data-url],[data-link],[onclick]')
                .forEach(node => {
                  if (attrLooksBlocked(node) || looksLikeClickCurtain(node)) disable(node);
                });
            }
          };

          const selectRequestedPlayer = () => {
            if (window.top !== window) return false;
            if (!location.hostname.endsWith('apitotal.net')) return false;
            if (REQUESTED_PLAYER <= 1 || playerSelectionDone || playerSelectionBusy) return playerSelectionDone;
            let chooser = null;
            try {
              chooser = [...document.querySelectorAll('.v-select,.v-input,[role="combobox"]')].find(el => {
                const t = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                return t.includes('PLAYER') && (t.includes('ESCOLHA OUTRO PLAYER') || t.includes('TRAVOU'));
              }) || null;
            } catch (_) {}
            if (!chooser) return false;
            const wanted = 'PLAYER ' + REQUESTED_PLAYER;
            try {
              const current = (chooser.innerText || chooser.textContent || '').replace(/\\s+/g, ' ').trim().toUpperCase();
              if (current.includes(wanted)) { playerSelectionDone = true; return true; }
            } catch (_) {}
            playerSelectionBusy = true;
            try {
              const control = chooser.querySelector('[role="combobox"],input,.v-field') || chooser;
              try { control.dispatchEvent(new MouseEvent('mousedown', {bubbles:true,cancelable:true,view:window})); } catch (_) {}
              try { control.click(); } catch (_) {}
            } catch (_) {}
            setTimeout(() => {
              try {
                const options = [...document.querySelectorAll('[role="option"],.v-list-item,.v-list-item-title')];
                const option = options.find(el => {
                  const t = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                  return t === wanted;
                });
                if (option) {
                  const target = option.closest('[role="option"],.v-list-item') || option;
                  target.click();
                  playerSelectionDone = true;
                }
              } catch (_) {}
              playerSelectionBusy = false;
              if (playerSelectionDone) setTimeout(() => hidePlayerChooser(document), 60);
            }, 140);
            return false;
          };

          const hidePlayerChooser = root => {
            if (window.top !== window) return;
            if (!location.hostname.endsWith('apitotal.net')) return;
            if (REQUESTED_PLAYER > 1 && !playerSelectionDone) return;
            const scope = root && root.querySelectorAll ? root : document;
            const candidates = [];
            try {
              if (scope.matches && scope.matches('.v-select,.v-input,[role="combobox"]')) candidates.push(scope);
              scope.querySelectorAll('.v-select,.v-input,[role="combobox"]').forEach(el => candidates.push(el));
            } catch (_) {}
            for (const el of candidates) {
              try {
                const t = (el.innerText || el.textContent || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                if (/PLAYER\\s+\\d+/.test(t) && (t.includes('ESCOLHA OUTRO PLAYER') || t.includes('TRAVOU'))) {
                  disable(el.closest('.v-input') || el);
                }
              } catch (_) {}
            }
            try {
              scope.querySelectorAll('div,span,label').forEach(el => {
                const t = (el.textContent || '').replace(/\\s+/g, ' ').trim().toUpperCase();
                if (!t || !t.includes('ESCOLHA OUTRO PLAYER')) return;
                const target = el.closest('.v-input,.v-select') || el;
                if (target.querySelector && target.querySelector('iframe')) return;
                disable(target);
              });
            } catch (_) {}
          };

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
            if (IS_DLIVE || isBlockedValue(url)) return null;
            try { return originalOpen.call(window, url, ...rest); }
            catch (_) { return null; }
          };

          const stopAdGesture = event => {
            if (!IS_DLIVE) return;
            try {
              const path = event.composedPath ? event.composedPath() : [];
              for (const node of path) {
                if (!node || node.nodeType !== 1) continue;
                if (attrLooksBlocked(node) || looksLikeClickCurtain(node)) {
                  event.preventDefault();
                  event.stopImmediatePropagation();
                  disable(node);
                  return false;
                }
                if (node.tagName === 'A') {
                  const target = (node.getAttribute('target') || '').toLowerCase();
                  const href = node.getAttribute('href') || '';
                  if (target === '_blank' || target === '_new' || /^javascript:/i.test(href)) {
                    event.preventDefault();
                    event.stopImmediatePropagation();
                    node.removeAttribute('target');
                    if (node.hasAttribute('onclick')) node.removeAttribute('onclick');
                    return false;
                  }
                }
              }
            } catch (_) {}
          };
          ['pointerdown','mousedown','touchstart','click'].forEach(type => {
            document.addEventListener(type, stopAdGesture, true);
          });

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
            if (!document.documentElement) { setTimeout(start, 10); return; }
            cleanElement(document.documentElement);
            cleanDlive(document);
            selectRequestedPlayer();
            hidePlayerChooser(document.documentElement);
            maybePromoteRdembed(document.documentElement);
            new MutationObserver(mutations => {
              for (const mutation of mutations) {
                if (mutation.type === 'attributes') {
                  cleanElement(mutation.target);
                  cleanDlive(mutation.target);
                  selectRequestedPlayer();
                  hidePlayerChooser(mutation.target);
                  if (mutation.attributeName === 'src') maybePromoteRdembed(mutation.target);
                }
                for (const node of mutation.addedNodes || []) {
                  cleanElement(node);
                  cleanDlive(node);
                  selectRequestedPlayer();
                  hidePlayerChooser(node);
                  maybePromoteRdembed(node);
                }
              }
            }).observe(document.documentElement, {
              childList:true,subtree:true,attributes:true,
              attributeFilter:['href','src','onclick','target','style','class','data-href','data-url','data-link']
            });
            if (IS_DLIVE) setInterval(() => cleanDlive(document), 1200);
          };

          start();
        })();
        """;

    static void install(WebView webView) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) return;
        WebViewCompat.addDocumentStartJavaScript(webView, SCRIPT, PLAYER_ORIGINS);
    }
}
