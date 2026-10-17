package com.atsisqual.livetv;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.IDN;
import java.net.URL;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UpdateActivity extends Activity {

    public static final String EXTRA_APK_URL = "apk_url";
    public static final String EXTRA_VERSION = "version";

    private final ExecutorService background = Executors.newSingleThreadExecutor();

    private ProgressBar progress;
    private TextView title;
    private TextView detail;
    private Button retry;
    private Button cancel;

    private String apkUrl;
    private String version;
    private boolean settingsOpened;
    private boolean downloadStarted;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();

        apkUrl = getIntent().getStringExtra(EXTRA_APK_URL);
        version = getIntent().getStringExtra(EXTRA_VERSION);
        if (version == null || version.trim().isEmpty()) version = "nova versão";

        if (!isTrustedUpdateUrl(apkUrl)) {
            showError("Atualização inválida", "O endereço do APK não pertence a uma origem autorizada.");
            return;
        }

        maybeStartUpdate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsOpened && !downloadStarted && canInstallPackages()) {
            settingsOpened = false;
            startDownload();
        }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(30), dp(30), dp(30), dp(30));
        root.setBackgroundColor(Color.rgb(9, 11, 15));

        progress = new ProgressBar(this);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        progressParams.bottomMargin = dp(20);
        root.addView(progress, progressParams);

        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(21);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        detail = new TextView(this);
        detail.setTextColor(Color.rgb(141, 150, 165));
        detail.setTextSize(14);
        detail.setGravity(Gravity.CENTER);
        detail.setPadding(0, dp(10), 0, dp(22));
        root.addView(detail, new LinearLayout.LayoutParams(-1, -2));

        retry = makeButton("Tentar novamente", true);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> maybeStartUpdate());
        root.addView(retry, buttonParams());

        cancel = makeButton("Cancelar", false);
        cancel.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams cancelParams = buttonParams();
        cancelParams.topMargin = dp(10);
        root.addView(cancel, cancelParams);

        setContentView(root);
    }

    private Button makeButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(primary ? Color.BLACK : Color.WHITE);
        button.setBackgroundColor(primary ? Color.rgb(240, 242, 245) : Color.rgb(42, 49, 60));
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        return new LinearLayout.LayoutParams(dp(230), dp(50));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void maybeStartUpdate() {
        retry.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);

        if (!canInstallPackages()) {
            title.setText("Permissão necessária");
            detail.setText("Ativa “Permitir desta fonte” para a Live TV. Ao voltares, o download começa automaticamente.");
            openInstallPermission();
            return;
        }

        startDownload();
    }

    private boolean canInstallPackages() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O
                || getPackageManager().canRequestPackageInstalls();
    }

    private void openInstallPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            startDownload();
            return;
        }

        try {
            settingsOpened = true;
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            settingsOpened = false;
            showError("Não foi possível abrir as definições",
                    "Ativa manualmente a instalação de apps desconhecidas para a Live TV.");
        }
    }

    private void startDownload() {
        if (downloadStarted) return;
        downloadStarted = true;
        progress.setVisibility(View.VISIBLE);
        retry.setVisibility(View.GONE);
        title.setText("A descarregar " + version + "…");
        detail.setText("A atualização é descarregada diretamente do repositório oficial.");

        background.execute(() -> {
            HttpURLConnection connection = null;
            FileOutputStream output = null;
            try {
                File directory = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
                if (directory == null) throw new IllegalStateException("Armazenamento indisponível");
                if (!directory.exists() && !directory.mkdirs()) {
                    throw new IllegalStateException("Não foi possível criar a pasta de atualização");
                }

                File apk = new File(directory, "Live-TV-update.apk");
                if (apk.exists() && !apk.delete()) {
                    throw new IllegalStateException("Não foi possível substituir a atualização anterior");
                }

                URL url = new URL(apkUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(true);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "application/vnd.android.package-archive,*/*");
                connection.setRequestProperty("User-Agent", "Live-TV/" + BuildConfig.VERSION_NAME);

                int response = connection.getResponseCode();
                if (response < 200 || response >= 300) {
                    throw new IllegalStateException("Erro HTTP " + response);
                }

                long total = connection.getContentLengthLong();
                long read = 0;
                int lastPercent = -1;
                byte[] buffer = new byte[64 * 1024];

                try (InputStream input = connection.getInputStream()) {
                    output = new FileOutputStream(apk);
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                        read += count;
                        if (total > 0) {
                            int percent = (int) Math.min(100, (read * 100L) / total);
                            if (percent != lastPercent && (percent % 5 == 0 || percent == 100)) {
                                lastPercent = percent;
                                final int shown = percent;
                                runOnUiThread(() -> detail.setText("Download: " + shown + "%"));
                            }
                        }
                    }
                    output.flush();
                }
                output = null;

                if (!apk.isFile() || apk.length() < 100_000) {
                    throw new IllegalStateException("O APK descarregado parece incompleto");
                }

                runOnUiThread(() -> {
                    progress.setVisibility(View.VISIBLE);
                    title.setText("A validar atualização…");
                    detail.setText("A confirmar versão, pacote e assinatura digital.");
                });

                validateDownloadedApk(apk);
                runOnUiThread(() -> installApk(apk));
            } catch (Exception e) {
                String message = e.getMessage();
                runOnUiThread(() -> showError("Falha ao atualizar",
                        message == null || message.trim().isEmpty()
                                ? "Não foi possível descarregar ou validar a atualização."
                                : message));
            } finally {
                try {
                    if (output != null) output.close();
                } catch (Exception ignored) {}
                if (connection != null) connection.disconnect();
                downloadStarted = false;
            }
        });
    }

    private void validateDownloadedApk(File apk) throws Exception {
        PackageManager packageManager = getPackageManager();
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? PackageManager.GET_SIGNING_CERTIFICATES
                : PackageManager.GET_SIGNATURES;

        PackageInfo installed = packageManager.getPackageInfo(getPackageName(), flags);
        PackageInfo candidate = packageManager.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        if (candidate == null) {
            throw new IllegalStateException("O ficheiro descarregado não é um APK Android válido");
        }
        if (!getPackageName().equals(candidate.packageName)) {
            throw new IllegalStateException("O APK pertence a outra aplicação");
        }

        long installedVersion = packageVersionCode(installed);
        long candidateVersion = packageVersionCode(candidate);
        if (candidateVersion <= installedVersion) {
            throw new IllegalStateException("A atualização descarregada não é mais recente");
        }

        Set<String> installedSigners = signerDigests(installed);
        Set<String> candidateSigners = signerDigests(candidate);
        if (installedSigners.isEmpty() || candidateSigners.isEmpty()
                || !installedSigners.equals(candidateSigners)) {
            throw new IllegalStateException("A assinatura digital da atualização não corresponde à Live TV instalada");
        }
    }

    private long packageVersionCode(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return info.getLongVersionCode();
        }
        return info.versionCode;
    }

    private Set<String> signerDigests(PackageInfo info) throws Exception {
        Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (info.signingInfo == null) return new HashSet<>();
            signatures = info.signingInfo.getApkContentsSigners();
        } else {
            signatures = info.signatures;
        }

        Set<String> result = new HashSet<>();
        if (signatures == null) return result;
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (Signature signature : signatures) {
            byte[] bytes = digest.digest(signature.toByteArray());
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int b = value & 0xff;
                hex.append(Character.forDigit((b >>> 4) & 0xf, 16));
                hex.append(Character.forDigit(b & 0xf, 16));
            }
            result.add(hex.toString());
            digest.reset();
        }
        return result;
    }

    private void installApk(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    apk);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.android.package-archive");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (Exception e) {
            showError("Não foi possível abrir o instalador",
                    "O APK foi descarregado e validado, mas o Android não conseguiu abrir o instalador.");
        }
    }

    private void showError(String heading, String message) {
        downloadStarted = false;
        progress.setVisibility(View.GONE);
        title.setText(heading);
        detail.setText(message);
        retry.setVisibility(View.VISIBLE);
    }

    private boolean isTrustedUpdateUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value);
            if (!"https".equalsIgnoreCase(uri.getScheme())) return false;
            String host = uri.getHost();
            if (host == null || host.trim().isEmpty()) return false;
            host = IDN.toASCII(host.trim().toLowerCase(Locale.ROOT));
            return host.equals("raw.githubusercontent.com")
                    || host.endsWith(".raw.githubusercontent.com")
                    || host.equals("atsisqual.github.io");
        } catch (Exception ignored) {
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        background.shutdownNow();
        super.onDestroy();
    }
}
