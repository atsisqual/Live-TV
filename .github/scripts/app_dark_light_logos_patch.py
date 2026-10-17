from pathlib import Path
import base64, io, json, math
from PIL import Image, ImageDraw, ImageFont

ROOT = Path('.')
FONT_BOLD = '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf'
FONT_REG = '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'
W, H = 360, 120
YELLOW = (255, 230, 0, 255)
GREEN = (0, 135, 75, 255)
RED = (232, 35, 48, 255)


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'{label}: expected text not found')
    return text.replace(old, new, 1)


def fit_font(text, maxw, size=72):
    while size > 10:
        f = ImageFont.truetype(FONT_BOLD, size)
        if f.getbbox(text)[2] - f.getbbox(text)[0] <= maxw:
            return f
        size -= 1
    return ImageFont.truetype(FONT_BOLD, 10)


def to_data_uri(im):
    buf = io.BytesIO()
    im.save(buf, format='PNG', optimize=True)
    return 'data:image/png;base64,' + base64.b64encode(buf.getvalue()).decode('ascii')


def dazn_logo(num, dark=True):
    fg = (255,255,255,255) if dark else (10,10,10,255)
    im = Image.new('RGBA', (W,H), (0,0,0,0)); d = ImageDraw.Draw(im)
    x0,y0,x1,y1 = 18,16,132,104
    pts = [(x0,y0),(x1,y0),(x1,42),(x1-12,60),(x1,78),(x1,y1),(x0,y1),(x0,78),(x0+12,60),(x0,42),(x0,y0)]
    d.line(pts, fill=fg, width=8, joint='curve')
    f = ImageFont.truetype(FONT_BOLD,34)
    d.text((46,17),'DA',font=f,fill=fg)
    d.text((47,52),'ZN',font=f,fill=fg)
    fnum = ImageFont.truetype(FONT_BOLD,72)
    d.text((165,19),str(num),font=fnum,fill=fg)
    return im


def sporttv_logo(num, dark=True):
    fg = (255,255,255,255) if dark else (10,10,10,255)
    im = Image.new('RGBA', (W,H), (0,0,0,0)); d = ImageDraw.Draw(im)
    f = fit_font('sport.tv',255,62)
    x,y = 15,29
    d.text((x,y),'sport.tv',font=f,fill=fg)
    xo = x + d.textlength('sp',font=f)
    d.text((xo,y),'o',font=f,fill=YELLOW)
    xdot = x + d.textlength('sport',font=f)
    d.ellipse([xdot+5,58,xdot+17,70],fill=YELLOW)
    cx,cy,r = 320,60,28
    d.ellipse([cx-r,cy-r,cx+r,cy+r],fill=YELLOW)
    fn = ImageFont.truetype(FONT_BOLD,38)
    bb = d.textbbox((0,0),str(num),font=fn)
    tw,th = bb[2]-bb[0],bb[3]-bb[1]
    d.text((cx-tw/2,cy-th/2-4),str(num),font=fn,fill=(10,10,10,255))
    return im


def canal11_logo(dark=True):
    fg = (255,255,255,255) if dark else (20,20,20,255)
    im = Image.new('RGBA',(W,H),(0,0,0,0)); d = ImageDraw.Draw(im)
    f = ImageFont.truetype(FONT_BOLD,86)
    bb = d.textbbox((0,0),'11',font=f); tw = bb[2]-bb[0]
    x,y = (W-tw)//2,8
    d.text((x+4,y+2),'11',font=f,fill=GREEN)
    d.text((x,y),'11',font=f,fill=fg,stroke_width=2,stroke_fill=fg)
    d.text((x+2,y),'11',font=f,fill=fg,stroke_width=1,stroke_fill=GREEN)
    return im


def sporting_logo(dark=True):
    fg = (255,255,255,255) if dark else (20,20,20,255)
    im = Image.new('RGBA',(W,H),(0,0,0,0)); d = ImageDraw.Draw(im)
    d.ellipse([18,22,94,98],fill=GREEN,outline=(255,255,255,255) if dark else GREEN,width=3)
    fscp = ImageFont.truetype(FONT_BOLD,24)
    d.text((29,45),'SCP',font=fscp,fill=(255,255,255,255))
    f = fit_font('SPORTING',210,42)
    d.text((106,31),'SPORTING',font=f,fill=fg)
    ftv = ImageFont.truetype(FONT_BOLD,33)
    d.rounded_rectangle([244,67,341,103],radius=7,fill=GREEN)
    d.text((259,66),'TV',font=ftv,fill=(255,255,255,255))
    return im


def btv_logo(dark=True):
    fg = (255,255,255,255) if dark else (20,20,20,255)
    im = Image.new('RGBA',(W,H),(0,0,0,0)); d = ImageDraw.Draw(im)
    fb = ImageFont.truetype(FONT_BOLD,82); ftv = ImageFont.truetype(FONT_BOLD,68)
    d.text((60,12),'B',font=fb,fill=RED)
    d.text((135,22),'TV',font=ftv,fill=fg)
    return im


def pair(builder):
    return to_data_uri(builder(True)), to_data_uri(builder(False))

# Generate compact dark/light assets.
assets = {}
assets['canal-11'] = pair(canal11_logo)
for n,slug in [(1,'dazn'),(2,'dazn2'),(3,'dazn3')]:
    assets[slug] = pair(lambda dark,n=n: dazn_logo(n,dark))
for n,slug in [(1,'sport-tv'),(2,'sport-tv-2'),(3,'sport-tv-3'),(4,'sport-tv-4'),(5,'sport-tv-5'),(6,'sport-tv-6')]:
    assets[slug] = pair(lambda dark,n=n: sporttv_logo(n,dark))
assets['sporting-tv'] = pair(sporting_logo)
assets['benfica-tv'] = pair(btv_logo)

channels = [
    ('Canal 11','canal-11','Canal11.pt','https://apitotal.net/0112/tv001/canal-11',1),
    ('Dazn','dazn','DAZN1.pt','https://apitotal.net/0112/tv001/dazn',1),
    ('Dazn2','dazn2','DAZN2.pt','https://apitotal.net/0112/tv001/dazn2',1),
    ('Dazn3','dazn3','DAZN3.pt','https://apitotal.net/0112/tv001/dazn3',1),
    ('Sport TV','sport-tv','SportTV1.pt','https://apitotal.net/0112/tv001/sport-tv',1),
    ('Sport TV 2','sport-tv-2','SportTV2.pt','https://apitotal.net/0112/tv001/sport-tv-2',1),
    ('Sport TV 3','sport-tv-3','SportTV3.pt','https://apitotal.net/0112/tv001/sport-tv-3',1),
    ('Sport TV 4','sport-tv-4','SportTV4.pt','https://apitotal.net/0112/tv001/sport-tv-4',1),
    ('Sport TV 5','sport-tv-5','SportTV5.pt','https://apitotal.net/0112/tv001/sport-tv-5',1),
    ('Sport TV 6','sport-tv-6','SportTV6.pt','https://apitotal.net/0112/tv001/sport-tv-6',1),
    ('Sporting TV','sporting-tv','SportingTV.pt','https://apitotal.net/0112/tv001/sporting-tv',1),
    ('Benfica TV','benfica-tv','BenficaTV.pt','https://apitotal.net/0112/tv001/benfica-tv',3),
]
obj = {'channels': []}
for name,slug,epg,url,player in channels:
    dark,light = assets[slug]
    obj['channels'].append({
        'name':name,'slug':slug,'epg_id':epg,'category':'Portugal','url':url,'player':player,
        'logo_dark':dark,'logo_light':light
    })
asset_json = Path('android/app/src/main/assets/portugal-sports-channels.json')
asset_json.write_text(json.dumps(obj, ensure_ascii=False, indent=2), encoding='utf-8')

# Home UI: preserve dark/light fields and select the correct variant.
index_path = Path('android/app/src/main/assets/index.html')
index = index_path.read_text(encoding='utf-8')
old_norm = "function normalizeStored(x){if(Array.isArray(x)){const raw={name:text(x[0]||'Channel'),url:text(x[1])};return{name:raw.name,url:canonicalUrl(raw),source_url:raw.url,category:'Outros'}}if(x&&typeof x==='object'){const source=urlFromValue(x.source_url||x.page_url||x.url||x.embed||x.player),slug=text(x.slug).toLowerCase()||slugFromUrl(source);return{name:text(x.name||x.title||'Channel'),url:canonicalUrl(x),source_url:source,slug,epg_id:text(x.epg_id||x.epgId||x.tvg_id||x['tvg-id']),player:playerNumber(x.player||x.player_index||x.playerIndex),category:text(x.category||x.group||x.group_title||x.groupTitle||x['group-title']),logo:text(x.logo||x.logo_url||x.logoUrl||x.icon||x.tvg_logo||x['tvg-logo'])}}return null}"
new_norm = "function normalizeStored(x){if(Array.isArray(x)){const raw={name:text(x[0]||'Channel'),url:text(x[1])};return{name:raw.name,url:canonicalUrl(raw),source_url:raw.url,category:'Outros'}}if(x&&typeof x==='object'){const source=urlFromValue(x.source_url||x.page_url||x.url||x.embed||x.player),slug=text(x.slug).toLowerCase()||slugFromUrl(source);return{name:text(x.name||x.title||'Channel'),url:canonicalUrl(x),source_url:source,slug,epg_id:text(x.epg_id||x.epgId||x.tvg_id||x['tvg-id']),player:playerNumber(x.player||x.player_index||x.playerIndex),category:text(x.category||x.group||x.group_title||x.groupTitle||x['group-title']),logo:text(x.logo||x.logo_url||x.logoUrl||x.icon||x.tvg_logo||x['tvg-logo']),logo_dark:text(x.logo_dark||x.logoDark),logo_light:text(x.logo_light||x.logoLight)}}return null}"
index = replace_once(index, old_norm, new_norm, 'normalizeStored dark/light')
old_fallback = "function categoryList(items){return [...new Set(items.map(x=>x.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt'))}function makeFallback(item){const d=document.createElement('div');d.className='fallback';d.textContent=initials(item.name);return d}"
new_fallback = "function categoryList(items){return [...new Set(items.map(x=>x.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt'))}function prefersDark(){try{return !window.matchMedia||window.matchMedia('(prefers-color-scheme: dark)').matches}catch(e){return true}}function pickLogo(item){return prefersDark()?(item.logo_dark||item.logo||item.logo_light||''):(item.logo_light||item.logo||item.logo_dark||'')}function makeFallback(item){const d=document.createElement('div');d.className='fallback';d.textContent=initials(item.name);return d}"
index = replace_once(index, old_fallback, new_fallback, 'pickLogo helper')
old_card_start = "function makeCard(item){const f=loadFav(),k=itemKey(item),card=document.createElement('article');card.className='card'+(!ok(item.url)?' disabled':'');card.tabIndex=0;card.setAttribute('role','button');card.setAttribute('aria-label','Abrir '+item.name);card.onclick=()=>openChannel(item);card.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openChannel(item)}};const visual=document.createElement('div');visual.className='visual';if(item.logo&&validLogo(item.logo)){const img=document.createElement('img');img.src=item.logo;"
new_card_start = "function makeCard(item){const f=loadFav(),k=itemKey(item),card=document.createElement('article');card.className='card'+(!ok(item.url)?' disabled':'');card.tabIndex=0;card.setAttribute('role','button');card.setAttribute('aria-label','Abrir '+item.name);card.onclick=()=>openChannel(item);card.onkeydown=e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();openChannel(item)}};const visual=document.createElement('div');visual.className='visual';const chosenLogo=pickLogo(item);if(chosenLogo&&validLogo(chosenLogo)){const img=document.createElement('img');img.src=chosenLogo;"
index = replace_once(index, old_card_start, new_card_start, 'card logo selection')
index = index.replace("No JSON podes usar <b>player</b> (1–20) para escolher a alternativa API Total.", "No JSON podes usar <b>player</b> (1–20) e <b>logo_dark</b>/<b>logo_light</b>.")
index_path.write_text(index, encoding='utf-8')

# Native sidebar: capture only compact channel metadata, then resolve logos by slug from bundled JSON.
epg_path = Path('android/app/src/main/java/com/atsisqual/livetv/EpgMainActivity.java')
epg = epg_path.read_text(encoding='utf-8')
epg = replace_once(epg,
    "    private final Map<String, Bitmap> logoCache = new ConcurrentHashMap<>();\n",
    "    private final Map<String, Bitmap> logoCache = new ConcurrentHashMap<>();\n    private final Map<String, String> bundledLogoDark = new ConcurrentHashMap<>();\n    private final Map<String, String> bundledLogoLight = new ConcurrentHashMap<>();\n    private boolean bundledLogosLoaded;\n",
    'bundled logo maps')
epg = replace_once(epg,
    "        super.onCreate(state);\n\n        directPlayerMode =",
    "        super.onCreate(state);\n        loadBundledChannelLogos();\n\n        directPlayerMode =",
    'load bundled logos on create')
old_capture = "                        + \"return {name:String(x.name||'Canal'),url:String(x.url||''),\"\n                        + \"category:String(x.category||'Canal'),logo:String(x.logo||'')};\""
new_capture = "                        + \"return {name:String(x.name||'Canal'),url:String(x.url||''),\"\n                        + \"category:String(x.category||'Canal'),slug:String(x.slug||'')};\""
epg = replace_once(epg, old_capture, new_capture, 'compact sidebar capture')
old_parse = "                        String category = item.optString(\"category\", \"Canal\").trim();\n                        String logo = item.optString(\"logo\", \"\").trim();\n                        if (url.isEmpty()) continue;\n\n                        channels.add(new ChannelItem(\n                                name.isEmpty() ? \"Canal\" : name,\n                                url,\n                                category.isEmpty() ? \"Canal\" : category,\n                                logo\n                        ));"
new_parse = "                        String category = item.optString(\"category\", \"Canal\").trim();\n                        String slug = item.optString(\"slug\", \"\").trim().toLowerCase(Locale.ROOT);\n                        String logo = bundledLogoDark.getOrDefault(slug, \"\");\n                        if (url.isEmpty()) continue;\n\n                        channels.add(new ChannelItem(\n                                name.isEmpty() ? \"Canal\" : name,\n                                url,\n                                category.isEmpty() ? \"Canal\" : category,\n                                logo\n                        ));"
epg = replace_once(epg, old_parse, new_parse, 'sidebar bundled logo lookup')
marker = "    private void captureChannelsIfNeeded() {\n"
loader = '''    private void loadBundledChannelLogos() {
        if (bundledLogosLoaded) return;
        bundledLogosLoaded = true;
        try (InputStream input = getAssets().open("portugal-sports-channels.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[16 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            JSONArray items = root.optJSONArray("channels");
            if (items == null) return;
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                String slug = item.optString("slug", "").trim().toLowerCase(Locale.ROOT);
                if (slug.isEmpty()) continue;
                String dark = item.optString("logo_dark", "").trim();
                String light = item.optString("logo_light", "").trim();
                if (!dark.isEmpty()) bundledLogoDark.put(slug, dark);
                if (!light.isEmpty()) bundledLogoLight.put(slug, light);
            }
        } catch (Exception ignored) {
            // Channel list must remain usable even if optional logos cannot be read.
        }
    }

'''
epg = replace_once(epg, marker, loader + marker, 'bundled logo loader')
epg_path.write_text(epg, encoding='utf-8')

# Release version.
gradle_path = Path('android/app/build.gradle')
gradle = gradle_path.read_text(encoding='utf-8')
gradle = replace_once(gradle, 'versionCode 35', 'versionCode 36', 'versionCode')
gradle = replace_once(gradle, "versionName '1.6.8'", "versionName '1.6.9'", 'versionName')
gradle_path.write_text(gradle, encoding='utf-8')

print('Prepared TV do Zé v1.6.9 dark/light numbered channel logos.')
