package com.atsisqual.livetv;

import android.webkit.WebView;

/**
 * Launcher activity that wires the provider-adapter lifecycle around the existing
 * stable SyncedEpgMainActivity implementation.
 *
 * The registry is empty by default, so this class is behaviourally equivalent to
 * SyncedEpgMainActivity until an explicitly authorized adapter is registered.
 */
public class AdapterEpgMainActivity extends SyncedEpgMainActivity {

    private final ProviderAdapterSession providerAdapters = new ProviderAdapterSession();
    private WebView adapterWebView;

    @Override
    protected void onLiveTvPageStarted(WebView view, String url) {
        adapterWebView = view;
        super.onLiveTvPageStarted(view, url);
        providerAdapters.onPageStarted(view, url);
    }

    @Override
    protected void onLiveTvPageCommitVisible(WebView view, String url) {
        adapterWebView = view;
        super.onLiveTvPageCommitVisible(view, url);
        providerAdapters.onPageReady(view, url);
    }

    @Override
    protected void onLiveTvPageFinished(WebView view, String url) {
        adapterWebView = view;
        super.onLiveTvPageFinished(view, url);
        providerAdapters.onPageFinished(view, url);
    }

    @Override
    protected void onDestroy() {
        providerAdapters.destroy(adapterWebView);
        adapterWebView = null;
        super.onDestroy();
    }
}
