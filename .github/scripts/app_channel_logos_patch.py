from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f"{label}: expected text not found; refusing unsafe patch")
    return text.replace(old, new, 1)


# --- Native sidebar: carry and render channel logos -------------------------
epg_path = Path("android/app/src/main/java/com/atsisqual/livetv/EpgMainActivity.java")
epg = epg_path.read_text(encoding="utf-8")

epg = replace_once(
    epg,
    "import android.content.Intent;\nimport android.graphics.Color;\n",
    "import android.content.Intent;\nimport android.graphics.Bitmap;\nimport android.graphics.BitmapFactory;\nimport android.graphics.Color;\n",
    "bitmap imports",
)
epg = replace_once(
    epg,
    "import android.os.Looper;\nimport android.view.Gravity;\n",
    "import android.os.Looper;\nimport android.util.Base64;\nimport android.view.Gravity;\n",
    "base64 import",
)
epg = replace_once(
    epg,
    "import android.widget.FrameLayout;\nimport android.widget.LinearLayout;\n",
    "import android.widget.FrameLayout;\nimport android.widget.ImageView;\nimport android.widget.LinearLayout;\n",
    "imageview import",
)
epg = replace_once(
    epg,
    "import java.util.List;\nimport java.util.Locale;\nimport java.util.concurrent.ExecutorService;\n",
    "import java.util.List;\nimport java.util.Locale;\nimport java.util.Map;\nimport java.util.concurrent.ConcurrentHashMap;\nimport java.util.concurrent.ExecutorService;\n",
    "map imports",
)
epg = replace_once(
    epg,
    "    private static final int MAX_XMLTV_BYTES = 12 * 1024 * 1024;\n    private static final int SIDEBAR_WIDTH_DP = 184;\n",
    "    private static final int MAX_XMLTV_BYTES = 12 * 1024 * 1024;\n    private static final int MAX_LOGO_BYTES = 768 * 1024;\n    private static final int SIDEBAR_WIDTH_DP = 216;\n",
    "sidebar/logo constants",
)
epg = replace_once(
    epg,
    "    private final ExecutorService epgBackground = Executors.newSingleThreadExecutor();\n    private final List<ChannelItem> channels = new ArrayList<>();\n",
    "    private final ExecutorService epgBackground = Executors.newSingleThreadExecutor();\n    private final ExecutorService logoBackground = Executors.newFixedThreadPool(3);\n    private final List<ChannelItem> channels = new ArrayList<>();\n    private final Map<String, Bitmap> logoCache = new ConcurrentHashMap<>();\n",
    "logo executor/cache",
)
epg = replace_once(
    epg,
    "+ \"return {name:String(x.name||'Canal'),url:String(x.url||''),\"\n                        + \"category:String(x.category||'Canal')};\"",
    "+ \"return {name:String(x.name||'Canal'),url:String(x.url||''),\"\n                        + \"category:String(x.category||'Canal'),logo:String(x.logo||'')};\"",
    "capture logo javascript",
)
epg = replace_once(
    epg,
    "                        String category = item.optString(\"category\", \"Canal\").trim();\n                        if (url.isEmpty()) continue;\n\n                        channels.add(new ChannelItem(\n                                name.isEmpty() ? \"Canal\" : name,\n                                url,\n                                category.isEmpty() ? \"Canal\" : category\n                        ));",
    "                        String category = item.optString(\"category\", \"Canal\").trim();\n                        String logo = item.optString(\"logo\", \"\").trim();\n                        if (url.isEmpty()) continue;\n\n                        channels.add(new ChannelItem(\n                                name.isEmpty() ? \"Canal\" : name,\n                                url,\n                                category.isEmpty() ? \"Canal\" : category,\n                                logo\n                        ));",
    "parse captured logo",
)
epg = replace_once(
    epg,
    "            row.setOrientation(LinearLayout.VERTICAL);\n            row.setGravity(Gravity.CENTER_VERTICAL);\n            row.setPadding(dp(10), dp(6), dp(10), dp(6));",
    "            row.setOrientation(LinearLayout.HORIZONTAL);\n            row.setGravity(Gravity.CENTER_VERTICAL);\n            row.setPadding(dp(10), dp(5), dp(9), dp(5));",
    "sidebar row orientation",
)
epg = replace_once(
    epg,
    "            TextView name = new TextView(this);",
    "            LinearLayout textWrap = new LinearLayout(this);\n            textWrap.setOrientation(LinearLayout.VERTICAL);\n            textWrap.setGravity(Gravity.CENTER_VERTICAL);\n\n            TextView name = new TextView(this);",
    "sidebar text wrapper",
)
epg = replace_once(
    epg,
    "            row.addView(\n                    name,\n                    new LinearLayout.LayoutParams(\n                            ViewGroup.LayoutParams.MATCH_PARENT,\n                            ViewGroup.LayoutParams.WRAP_CONTENT\n                    )\n            );",
    "            textWrap.addView(\n                    name,\n                    new LinearLayout.LayoutParams(\n                            ViewGroup.LayoutParams.MATCH_PARENT,\n                            ViewGroup.LayoutParams.WRAP_CONTENT\n                    )\n            );",
    "name into text wrapper",
)
epg = replace_once(
    epg,
    "            row.addView(category, categoryParams);\n\n            row.setOnClickListener(v -> openChannelFromSidebar(item));\n\n            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    dp(52)\n            );",
    "            textWrap.addView(category, categoryParams);\n\n            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(\n                    0,\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    1f\n            );\n            row.addView(textWrap, textParams);\n\n            if (!item.logo.isEmpty()) {\n                ImageView logo = new ImageView(this);\n                logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);\n                logo.setAdjustViewBounds(true);\n                logo.setPadding(dp(2), dp(2), dp(2), dp(2));\n                LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(\n                        dp(48),\n                        ViewGroup.LayoutParams.MATCH_PARENT\n                );\n                logoParams.leftMargin = dp(8);\n                row.addView(logo, logoParams);\n                loadChannelLogo(logo, item.logo);\n            }\n\n            row.setOnClickListener(v -> openChannelFromSidebar(item));\n\n            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(\n                    ViewGroup.LayoutParams.MATCH_PARENT,\n                    dp(58)\n            );",
    "sidebar logo placement",
)
epg = replace_once(
    epg,
    "                int target = index * dp(57);\n                channelScroll.smoothScrollTo(0, Math.max(0, target - dp(62)));",
    "                int target = index * dp(63);\n                channelScroll.smoothScrollTo(0, Math.max(0, target - dp(68)));",
    "selected scroll geometry",
)

logo_methods = r'''    private void loadChannelLogo(ImageView view, String value) {
        if (view == null) return;
        String logo = value == null ? "" : value.trim();
        if (logo.isEmpty()) return;

        view.setTag(logo);
        Bitmap cached = logoCache.get(logo);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }

        if (logo.regionMatches(true, 0, "data:image/", 0, 11)) {
            Bitmap bitmap = decodeDataLogo(logo);
            if (bitmap != null) {
                logoCache.put(logo, bitmap);
                view.setImageBitmap(bitmap);
            }
            return;
        }

        if (!isValidLogoUrl(logo)) return;
        logoBackground.execute(() -> {
            Bitmap bitmap = downloadLogo(logo);
            if (bitmap == null) return;
            logoCache.put(logo, bitmap);
            view.post(() -> {
                Object tag = view.getTag();
                if (tag != null && logo.equals(tag.toString())) view.setImageBitmap(bitmap);
            });
        });
    }

    private Bitmap decodeDataLogo(String value) {
        try {
            int comma = value.indexOf(',');
            if (comma <= 0 || !value.substring(0, comma).toLowerCase(Locale.ROOT).contains(";base64")) {
                return null;
            }
            byte[] bytes = Base64.decode(value.substring(comma + 1), Base64.DEFAULT);
            if (bytes.length == 0 || bytes.length > MAX_LOGO_BYTES) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isValidLogoUrl(String value) {
        try {
            Uri uri = Uri.parse(value == null ? "" : value.trim());
            return "https".equalsIgnoreCase(uri.getScheme())
                    && uri.getHost() != null
                    && !uri.getHost().trim().isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Bitmap downloadLogo(String value) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setUseCaches(true);
            connection.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            connection.setRequestProperty("User-Agent", "TV-do-Ze/" + BuildConfig.VERSION_NAME);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int total = 0;
            try (InputStream input = connection.getInputStream()) {
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > MAX_LOGO_BYTES) return null;
                    output.write(buffer, 0, count);
                }
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) return null;
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

'''
epg = replace_once(
    epg,
    "    private boolean matchesActiveChannel(String channelUrl, String activeUrl) {",
    logo_methods + "    private boolean matchesActiveChannel(String channelUrl, String activeUrl) {",
    "logo loading methods",
)
epg = replace_once(
    epg,
    "        epgBackground.shutdownNow();\n        super.onDestroy();",
    "        epgBackground.shutdownNow();\n        logoBackground.shutdownNow();\n        logoCache.clear();\n        super.onDestroy();",
    "logo executor cleanup",
)
epg = replace_once(
    epg,
    "        final String category;\n\n        ChannelItem(String name, String url, String category) {\n            this.name = name;\n            this.url = url;\n            this.category = category;\n        }",
    "        final String category;\n        final String logo;\n\n        ChannelItem(String name, String url, String category, String logo) {\n            this.name = name;\n            this.url = url;\n            this.category = category;\n            this.logo = logo == null ? \"\" : logo;\n        }",
    "channel item logo field",
)
epg_path.write_text(epg, encoding="utf-8")


# --- Home cards: allow logo data URIs as well as URLs -----------------------
index_path = Path("android/app/src/main/assets/index.html")
index = index_path.read_text(encoding="utf-8")
index = replace_once(
    index,
    "function ok(u){return /^https?:\\/\\//i.test(String(u||''))}function text(v)",
    "function ok(u){return /^https?:\\/\\//i.test(String(u||''))}function validLogo(u){return ok(u)||/^data:image\\/(?:png|jpe?g|webp|gif);base64,/i.test(String(u||''))}function text(v)",
    "valid logo helper",
)
index = replace_once(
    index,
    "if(item.logo&&ok(item.logo)){const img=document.createElement('img');",
    "if(item.logo&&validLogo(item.logo)){const img=document.createElement('img');",
    "homepage base64 logo support",
)
index_path.write_text(index, encoding="utf-8")


# --- Adaptive launcher safe area -------------------------------------------
drawable = Path("android/app/src/main/res/drawable/tv_do_ze_icon_foreground.xml")
drawable.write_text(
    '''<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:drawable="@drawable/tv_do_ze_icon"
    android:insetLeft="18dp"
    android:insetTop="18dp"
    android:insetRight="18dp"
    android:insetBottom="18dp" />
''',
    encoding="utf-8",
)

adaptive = '''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/live_tv_bg" />
    <foreground android:drawable="@drawable/tv_do_ze_icon_foreground" />
</adaptive-icon>
'''
for path in [
    Path("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"),
    Path("android/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml"),
]:
    path.write_text(adaptive, encoding="utf-8")


# --- Keep an importable JSON example in assets -----------------------------
channels_json = r'''{
  "channels": [
    {"name":"Canal 11","slug":"canal-11","epg_id":"Canal11.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/canal-11","player":1,"logo":"https://www.canal11.pt/images/customer/logo.png"},
    {"name":"Dazn","slug":"dazn","epg_id":"DAZN1.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/dazn","player":1,"logo":"https://www.meo.pt/PublishingImages/canais/dazn-eleven-1-meo-logo.webp"},
    {"name":"Dazn2","slug":"dazn2","epg_id":"DAZN2.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/dazn2","player":1,"logo":"https://www.meo.pt/PublishingImages/canais/dazn-eleven-2-meo-logo.webp"},
    {"name":"Dazn3","slug":"dazn3","epg_id":"DAZN3.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/dazn3","player":1,"logo":"https://www.meo.pt/PublishingImages/canais/dazn-eleven-3-meo-logo.webp"},
    {"name":"Sport TV","slug":"sport-tv","epg_id":"SportTV1.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sport TV 2","slug":"sport-tv-2","epg_id":"SportTV2.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv-2","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sport TV 3","slug":"sport-tv-3","epg_id":"SportTV3.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv-3","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sport TV 4","slug":"sport-tv-4","epg_id":"SportTV4.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv-4","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sport TV 5","slug":"sport-tv-5","epg_id":"SportTV5.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv-5","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sport TV 6","slug":"sport-tv-6","epg_id":"SportTV6.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sport-tv-6","player":1,"logo":"https://www.meo.pt/PublishingImages/logos/sport-tv-logo-desktop.webp"},
    {"name":"Sporting TV","slug":"sporting-tv","epg_id":"SportingTV.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/sporting-tv","player":1,"logo":"https://upload.wikimedia.org/wikipedia/pt/b/b4/Sporting_TV.png"},
    {"name":"Benfica TV","slug":"benfica-tv","epg_id":"BenficaTV.pt","category":"Portugal","url":"https://apitotal.net/0112/tv001/benfica-tv","player":3,"logo":"https://www.meo.pt/PublishingImages/logos/btv-logo.png"}
  ]
}
'''
Path("android/app/src/main/assets/portugal-sports-channels.json").write_text(channels_json, encoding="utf-8")


# --- Release version --------------------------------------------------------
gradle_path = Path("android/app/build.gradle")
gradle = gradle_path.read_text(encoding="utf-8")
gradle = replace_once(gradle, "versionCode 34", "versionCode 35", "versionCode")
gradle = replace_once(gradle, "versionName '1.6.7'", "versionName '1.6.8'", "versionName")
gradle_path.write_text(gradle, encoding="utf-8")
