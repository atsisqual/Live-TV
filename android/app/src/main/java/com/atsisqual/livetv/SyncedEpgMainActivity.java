package com.atsisqual.livetv;

/**
 * Compatibility activity kept for the existing manifest entry.
 *
 * Sidebar selection and retry behavior now live directly in EpgMainActivity and
 * are driven by WebView lifecycle events, so no reflection or periodic polling
 * is required here.
 */
public class SyncedEpgMainActivity extends EpgMainActivity {
}
