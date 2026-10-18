package com.atsisqual.livetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.net.Uri;
import android.os.SystemClock;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Best-effort provider-chain diagnostics for the WebView player.
 *
 * Privacy/safety invariant: only hostnames, event categories and elapsed timings are
 * retained. Paths, query strings, fragments, media endpoints and request headers are
 * discarded immediately after classification.
 */
final class ProviderDiagnostics {

    private static final String TAG = "LiveTvProvider";
    private static final String LOCAL_HOME = "file:///android_asset/";
    private static final int MAX_EVENTS = 80;

    private final Activity activity;
    private final Object lock = new Object();
    private final List<Entry> entries = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    private WebView attachedView;
    private boolean sessionActive;
    private long sessionStartedAt;

    ProviderDiagnostics(Activity activity) {
        this.activity = activity;
    }

    void onPageStarted(WebView view, String url) {
        attach(view);
        if (isLocal(url)) {
            sessionActive = false;
            return;
        }

        synchronized (lock) {
            if (!sessionActive) {
                entries.clear();
                seen.clear();
                sessionStartedAt = SystemClock.elapsedRealtime();
                sessionActive = true;
            }
        }
        record("main-frame", url);
    }

    void onPageReady(WebView view, String url) {
        attach(view);
        if (view == null || isLocal(url)) return;
        injectProbe(view);
    }

    void showReport() {
        activity.runOnUiThread(() -> {
            String report = buildReport();
            new AlertDialog.Builder(activity)
                    .setTitle("Diagnóstico do provider")
                    .setMessage(report)
                    .setNegativeButton("Fechar", null)
                    .setNeutralButton("Copiar", (dialog, which) -> copy(report))
                    .show();
        });
    }

    void destroy() {
        attachedView = null;
        synchronized (lock) {
            entries.clear();
            seen.clear();
            sessionActive = false;
        }
    }

    private void attach(WebView view) {
        if (view == null || attachedView == view) return;
        attachedView = view;
        try {
            view.addJavascriptInterface(new Bridge(), "LiveTvDiag");
        } catch (Exception ignored) {}
    }

    private void injectProbe(WebView view) {
        String script = """
                (function(){try{
                  if(window.__liveTvProviderDiagInstalled)return;
                  window.__liveTvProviderDiagInstalled=true;
                  var sent={};

                  function bucket(k,u){
                    try{
                      var x=new URL(String(u||''),location.href);
                      var p=String(x.pathname||'').toLowerCase();
                      if(p.endsWith('.m3u8'))return 'hls';
                      if(p.endsWith('.mpd'))return 'dash';
                      if(['.m4s','.ts','.mp4','.webm','.aac','.mp3'].some(function(ext){return p.endsWith(ext);}))return 'media';
                      return String(k||'resource').toLowerCase();
                    }catch(e){return String(k||'resource').toLowerCase();}
                  }

                  function send(k,u){
                    try{
                      var s=String(u||'');
                      if(!s)return;
                      var x=new URL(s,location.href);
                      var h=String(x.hostname||'').toLowerCase();
                      if(!h)return;
                      var key=bucket(k,s)+'|'+h;
                      if(sent[key])return;
                      sent[key]=1;
                      if(window.LiveTvDiag&&LiveTvDiag.event)LiveTvDiag.event(String(k||'resource'),s);
                    }catch(e){}
                  }

                  function sendState(kind,host){
                    try{
                      var h=String(host||'v2.rdembed.sbs').toLowerCase();
                      if(!h||!/^[a-z0-9.-]+$/.test(h))return;
                      var k='state:'+String(kind||'');
                      var key=k+'|'+h;
                      if(sent[key])return;
                      sent[key]=1;
                      if(window.LiveTvDiag&&LiveTvDiag.event){
                        LiveTvDiag.event(k,'https://'+h+'/');
                      }
                    }catch(e){}
                  }

                  function scan(n){
                    try{
                      if(!n||n.nodeType!==1)return;
                      if(n.matches&&n.matches('iframe[src]'))send('iframe',n.src);
                      if(n.matches&&n.matches('script[src]'))send('script',n.src);
                      if(n.matches&&n.matches('source[src]'))send('media',n.src);
                      if(n.matches&&n.matches('video,audio'))send(n.tagName.toLowerCase(),n.currentSrc||n.src||'');
                      if(n.querySelectorAll)n.querySelectorAll('iframe[src],script[src],source[src],video,audio').forEach(scan);
                    }catch(e){}
                  }

                  scan(document.documentElement);
                  try{
                    (performance.getEntriesByType('resource')||[]).slice(-120).forEach(function(e){
                      send('resource:'+(e.initiatorType||'other'),e.name);
                    });
                  }catch(e){}

                  var po=null,mo=null;
                  try{
                    po=new PerformanceObserver(function(l){
                      l.getEntries().forEach(function(e){send('resource:'+(e.initiatorType||'other'),e.name);});
                    });
                    po.observe({entryTypes:['resource']});
                  }catch(e){}
                  try{
                    mo=new MutationObserver(function(ms){
                      ms.forEach(function(m){m.addedNodes&&m.addedNodes.forEach(scan);});
                    });
                    mo.observe(document.documentElement,{childList:true,subtree:true});
                  }catch(e){}

                  try{
                    var hn=String(location.hostname||'').toLowerCase();
                    if(hn==='v2.rdembed.sbs'){
                      var lastGesture=0;
                      ['pointerdown','mousedown','touchstart','click','keydown'].forEach(function(t){
                        document.addEventListener(t,function(){lastGesture=Date.now();},true);
                      });
                      var originalOpen=window.open;
                      window.open=function(u){
                        try{
                          var k=((Date.now()-lastGesture)<=1200)?'popup:gesture':'popup:script';
                          send(k,u||location.href);
                        }catch(e){}
                        try{return originalOpen.apply(window,arguments);}catch(e){return null;}
                      };
                      document.addEventListener('click',function(ev){
                        try{
                          var n=ev.target;
                          var a=n&&n.closest?n.closest('a[target="_blank"],a[target="_new"]'):null;
                          if(a)send('popup:link',a.href||location.href);
                        }catch(e){}
                      },true);

                      var watchedDocs=new WeakSet();
                      var watchedFrames=new WeakSet();
                      var watchedMedia=new WeakSet();

                      function visible(el,win){
                        try{
                          if(!el||!win)return false;
                          var cs=win.getComputedStyle(el);
                          if(!cs||cs.display==='none'||cs.visibility==='hidden'||Number(cs.opacity||1)===0)return false;
                          var r=el.getBoundingClientRect();
                          return r.width>1&&r.height>1;
                        }catch(e){return false;}
                      }

                      function adVisible(doc){
                        try{
                          var body=doc&&doc.body;
                          if(!body)return false;
                          var t=String(body.innerText||body.textContent||'').toLowerCase();
                          return t.indexOf('pular anúncio')>=0||t.indexOf('skip ad')>=0||
                                 t.indexOf('advertisement')>=0||t.indexOf('ad :')>=0||t.indexOf('ad:')>=0;
                        }catch(e){return false;}
                      }

                      function currentHost(doc){
                        try{
                          return String((doc&&doc.defaultView&&doc.defaultView.location.hostname)||'v2.rdembed.sbs').toLowerCase();
                        }catch(e){return 'v2.rdembed.sbs';}
                      }

                      function frameSourceHost(frame,parentDoc){
                        try{
                          var raw=String(frame.getAttribute('src')||'').trim();
                          if(!raw)return '';
                          return String(new URL(raw,parentDoc.location.href).hostname||'').toLowerCase();
                        }catch(e){return '';}
                      }

                      function classifyClickTarget(node){
                        try{
                          if(!node||!node.closest)return 'other';
                          if(node.closest('button,[role="button"],input[type="button"],input[type="submit"]'))return 'button';
                          if(node.closest('a[href]'))return 'link';
                        }catch(e){}
                        return 'other';
                      }

                      function watchMediaElement(media,depth,host){
                        try{
                          if(!media||watchedMedia.has(media))return;
                          watchedMedia.add(media);
                          sendState('video-created',host);
                          sendState('video-present-depth-'+depth,host);
                          ['play','loadedmetadata','canplay','playing','waiting','stalled','error'].forEach(function(name){
                            media.addEventListener(name,function(){sendState('media-'+name,host);});
                          });
                        }catch(e){}
                      }

                      function inspectFrame(frame,depth,parentDoc){
                        var nextDepth=depth+1;
                        var sourceHost=frameSourceHost(frame,parentDoc);
                        try{
                          var win=frame.contentWindow;
                          var doc=frame.contentDocument;
                          if(!win||!doc){
                            var parentHost=currentHost(parentDoc);
                            if(sourceHost&&sourceHost!==parentHost){
                              sendState('frame-depth-'+nextDepth+'-cross-origin',sourceHost);
                            }else{
                              sendState('frame-depth-'+nextDepth+'-inaccessible',sourceHost||parentHost);
                            }
                            return;
                          }
                          var parentHost=currentHost(parentDoc);
                          var actualHost=String(win.location.hostname||'').toLowerCase();
                          if(!actualHost&&sourceHost&&sourceHost!==parentHost){
                            sendState('frame-depth-'+nextDepth+'-cross-origin',sourceHost);
                            return;
                          }
                          var frameHost=actualHost||sourceHost||parentHost;
                          sendState('frame-depth-'+nextDepth+'-same-origin',frameHost);
                          sendState('frame-ready',frameHost);
                          watchDocument(doc,nextDepth);
                        }catch(e){
                          if(sourceHost){
                            sendState('frame-depth-'+nextDepth+'-cross-origin',sourceHost);
                          }else{
                            sendState('frame-depth-'+nextDepth+'-inaccessible',currentHost(parentDoc));
                          }
                        }
                      }

                      function watchFrame(frame,depth,parentDoc){
                        try{
                          if(!frame)return;
                          if(!watchedFrames.has(frame)){
                            watchedFrames.add(frame);
                            frame.addEventListener('load',function(){inspectFrame(frame,depth,parentDoc);});
                          }
                          inspectFrame(frame,depth,parentDoc);
                        }catch(e){}
                      }

                      function watchDocument(doc,depth){
                        try{
                          if(!doc||watchedDocs.has(doc))return;
                          watchedDocs.add(doc);
                          var win=doc.defaultView;
                          if(!win)return;
                          var host=currentHost(doc);

                          var adWasVisible=false;
                          var uiQueued=false;

                          function updateUi(){
                            uiQueued=false;
                            try{
                              var play=doc.querySelector('#center-play-btn,.center-play-btn');
                              if(play&&visible(play,win))sendState('play-visible',host);
                              if(doc.querySelector('button,[role="button"],input[type="button"],input[type="submit"],a[href]')){
                                sendState('controls-present-depth-'+depth,host);
                              }
                              if(doc.querySelector('video,audio')){
                                sendState('video-present-depth-'+depth,host);
                              }
                              var nowAd=adVisible(doc);
                              if(nowAd&&!adWasVisible)sendState('ad-visible',host);
                              if(!nowAd&&adWasVisible)sendState('ad-hidden',host);
                              adWasVisible=nowAd;
                            }catch(e){}
                          }

                          function queueUi(){
                            if(uiQueued)return;
                            uiQueued=true;
                            try{win.requestAnimationFrame(updateUi);}catch(e){setTimeout(updateUi,0);}
                          }

                          function scanDocNode(node){
                            try{
                              if(!node||node.nodeType!==1)return;
                              if(node.matches&&node.matches('video,audio'))watchMediaElement(node,depth,host);
                              if(node.matches&&node.matches('iframe'))watchFrame(node,depth,doc);
                              if(node.querySelectorAll){
                                node.querySelectorAll('video,audio').forEach(function(m){watchMediaElement(m,depth,host);});
                                node.querySelectorAll('iframe').forEach(function(f){watchFrame(f,depth,doc);});
                              }
                            }catch(e){}
                          }

                          doc.addEventListener('click',function(ev){
                            try{
                              var n=ev.target;
                              var clickKind=classifyClickTarget(n);
                              sendState('click('+clickKind+')-depth-'+depth,host);
                              var play=n&&n.closest?n.closest('#center-play-btn,.center-play-btn'):null;
                              if(play)sendState('play-click',host);
                            }catch(e){}
                          },true);

                          scanDocNode(doc.documentElement);
                          updateUi();

                          var observer=null;
                          try{
                            var Observer=win.MutationObserver||MutationObserver;
                            observer=new Observer(function(ms){
                              ms.forEach(function(m){
                                m.addedNodes&&m.addedNodes.forEach(scanDocNode);
                              });
                              queueUi();
                            });
                            observer.observe(doc.documentElement,{childList:true,subtree:true,attributes:true,characterData:true});
                          }catch(e){}

                          setTimeout(function(){try{if(observer)observer.disconnect();}catch(e){}},20000);
                        }catch(e){}
                      }

                      watchDocument(document,0);
                    }
                  }catch(e){}

                  setTimeout(function(){
                    try{if(po)po.disconnect();}catch(e){}
                    try{if(mo)mo.disconnect();}catch(e){}
                  },15000);
                }catch(e){}})();
                """;
        try {
            view.evaluateJavascript(script, null);
        } catch (Exception ignored) {}
    }

    private void record(String rawKind, String rawUrl) {
        Uri uri;
        try {
            uri = Uri.parse(rawUrl == null ? "" : rawUrl.trim());
        } catch (Exception ignored) {
            return;
        }

        String host = normalizedHost(uri);
        if (host == null || host.isEmpty()) return;
        String kind = classify(rawKind, uri);
        String key = kind + "|" + host;
        long elapsed;

        synchronized (lock) {
            if (!sessionActive || entries.size() >= MAX_EVENTS || !seen.add(key)) return;
            elapsed = Math.max(0L, SystemClock.elapsedRealtime() - sessionStartedAt);
            entries.add(new Entry(elapsed, kind, host));
        }
        Log.d(TAG, "+" + elapsed + "ms " + kind + " " + host);
    }

    private String classify(String rawKind, Uri uri) {
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.ROOT);
        if (path.endsWith(".m3u8")) return "HLS";
        if (path.endsWith(".mpd")) return "DASH";
        if (path.endsWith(".m4s") || path.endsWith(".ts") || path.endsWith(".mp4")
                || path.endsWith(".webm") || path.endsWith(".aac") || path.endsWith(".mp3")) {
            return "media";
        }

        String kind = rawKind == null ? "resource" : rawKind.trim().toLowerCase(Locale.ROOT);
        if (kind.startsWith("state:")) {
            String state = classifyRdembedState(kind.substring("state:".length()));
            if (state != null) return state;
        }
        if (kind.startsWith("resource:")) kind = kind.substring("resource:".length());
        if (kind.equals("popup:gesture")) return "popup(user)";
        if (kind.equals("popup:script")) return "popup(script)";
        if (kind.equals("popup:link")) return "popup(link)";
        if (kind.equals("xmlhttprequest")) return "XHR";
        if (kind.equals("fetch")) return "fetch";
        if (kind.equals("iframe")) return "iframe";
        if (kind.equals("script")) return "script";
        if (kind.equals("video") || kind.equals("audio") || kind.equals("media")) return "media";
        if (kind.equals("img") || kind.equals("image")) return "image";
        if (kind.equals("css") || kind.equals("link")) return "style";
        if (kind.equals("main-frame")) return "main-frame";
        return "resource";
    }

    private String classifyRdembedState(String state) {
        switch (state) {
            case "frame-ready":
            case "play-visible":
            case "play-click":
            case "ad-visible":
            case "ad-hidden":
            case "video-created":
            case "media-play":
            case "media-loadedmetadata":
            case "media-canplay":
            case "media-playing":
            case "media-waiting":
            case "media-stalled":
            case "media-error":
                return state;
            default:
                break;
        }

        if (state.matches("frame-depth-\\d+-(same-origin|cross-origin|inaccessible)")) return state;
        if (state.matches("controls-present-depth-\\d+")) return state;
        if (state.matches("video-present-depth-\\d+")) return state;
        if (state.matches("click\\((button|link|other)\\)-depth-\\d+")) return state;
        return null;
    }

    private String buildReport() {
        List<Entry> snapshot;
        synchronized (lock) {
            snapshot = new ArrayList<>(entries);
        }

        if (snapshot.isEmpty()) {
            return "Ainda não há eventos nesta sessão. Abre o canal e espera alguns segundos antes de consultar novamente.";
        }

        StringBuilder out = new StringBuilder();
        out.append("Só são mostrados hostnames; paths e endpoints de media são omitidos.\n")
                .append("popup(...) indica uma tentativa de abrir uma segunda janela; a política atual do WebView não é alterada.\n")
                .append("No rdembed, frame-depth/controls/video/click são apenas estados observados; o diagnóstico não clica, bloqueia ou altera o player.\n\n");
        for (Entry entry : snapshot) {
            out.append('+').append(entry.elapsedMs).append(" ms  ")
                    .append(entry.kind).append("  ")
                    .append(entry.host).append('\n');
        }
        return out.toString().trim();
    }

    private void copy(String report) {
        try {
            ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("Provider diagnostics", report));
        } catch (Exception ignored) {}
    }

    private boolean isLocal(String url) {
        return url != null && url.startsWith(LOCAL_HOME);
    }

    private String normalizedHost(Uri uri) {
        if (uri == null) return null;
        String host = uri.getHost();
        if (host == null || host.trim().isEmpty()) return null;
        host = host.trim().toLowerCase(Locale.ROOT);
        try {
            return IDN.toASCII(host).toLowerCase(Locale.ROOT);
        } catch (Exception ignored) {
            return host;
        }
    }

    private final class Bridge {
        @JavascriptInterface
        public void event(String kind, String url) {
            record(kind, url);
        }
    }

    private static final class Entry {
        final long elapsedMs;
        final String kind;
        final String host;

        Entry(long elapsedMs, String kind, String host) {
            this.elapsedMs = elapsedMs;
            this.kind = kind;
            this.host = host;
        }
    }
}
