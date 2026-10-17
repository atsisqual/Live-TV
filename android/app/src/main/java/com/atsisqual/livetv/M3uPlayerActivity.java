package com.atsisqual.livetv;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import java.util.HashMap;
import java.util.Map;

public class M3uPlayerActivity extends Activity {
    public static final String EXTRA_URL = "stream_url";
    public static final String EXTRA_NAME = "stream_name";
    public static final String EXTRA_USER_AGENT = "stream_user_agent";
    public static final String EXTRA_REFERRER = "stream_referrer";
    public static final String EXTRA_ORIGIN = "stream_origin";

    private ExoPlayer player;
    private PlayerView playerView;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();
        buildUi();
        startPlayback();
    }

    private void buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(this);
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        root.addView(playerView, new FrameLayout.LayoutParams(-1, -1));

        Button back = new Button(this);
        back.setText("← Canais");
        back.setAllCaps(false);
        back.setTextColor(Color.WHITE);
        back.setTextSize(14);
        back.setBackgroundColor(Color.argb(185, 18, 22, 29));
        back.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(dp(116), dp(48), Gravity.TOP | Gravity.START);
        backParams.setMargins(dp(12), dp(12), 0, 0);
        root.addView(back, backParams);

        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (name != null && !name.trim().isEmpty()) {
            TextView title = new TextView(this);
            title.setText(name.trim());
            title.setTextColor(Color.WHITE);
            title.setTextSize(14);
            title.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
            title.setPadding(dp(12), 0, dp(12), 0);
            title.setBackgroundColor(Color.argb(145, 18, 22, 29));
            FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(-2, dp(48), Gravity.TOP | Gravity.END);
            titleParams.setMargins(0, dp(12), dp(12), 0);
            root.addView(title, titleParams);
        }

        setContentView(root);
    }

    private void startPlayback() {
        String url = value(EXTRA_URL);
        if (url.isEmpty()) {
            Toast.makeText(this, "URL do canal inválido", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String userAgent = value(EXTRA_USER_AGENT);
        if (userAgent.isEmpty()) userAgent = "TV-do-Ze-M3U-Test/" + BuildConfig.VERSION_NAME;

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(12_000)
                .setReadTimeoutMs(30_000);

        Map<String, String> headers = new HashMap<>();
        String referrer = value(EXTRA_REFERRER);
        String origin = value(EXTRA_ORIGIN);
        if (!referrer.isEmpty()) headers.put("Referer", referrer);
        if (!origin.isEmpty()) headers.put("Origin", origin);
        if (!headers.isEmpty()) http.setDefaultRequestProperties(headers);

        DefaultDataSource.Factory dataSource = new DefaultDataSource.Factory(this, http);
        DefaultMediaSourceFactory mediaSources = new DefaultMediaSourceFactory(dataSource);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSources)
                .build();
        playerView.setPlayer(player);
        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(PlaybackException error) {
                String message = error == null || error.getMessage() == null
                        ? "Não foi possível reproduzir este stream"
                        : error.getMessage();
                Toast.makeText(M3uPlayerActivity.this, message, Toast.LENGTH_LONG).show();
            }
        });
        player.setMediaItem(MediaItem.fromUri(url));
        player.prepare();
        player.setPlayWhenReady(true);
        player.setVolume(1f);
    }

    private String value(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value.trim();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (player != null) {
            player.release();
            player = null;
        }
        super.onDestroy();
    }
}
