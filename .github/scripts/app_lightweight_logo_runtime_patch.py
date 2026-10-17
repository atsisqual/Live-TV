from pathlib import Path

index = Path('android/app/src/main/assets/index.html')
text = index.read_text(encoding='utf-8')

old = "let activeFilter='all',query='',toastTimer=null,scrollRestored=false;const $=id=>document.getElementById(id);"
new = "let activeFilter='all',query='',toastTimer=null,scrollRestored=false,bundledLogoMap=new Map();const $=id=>document.getElementById(id);"
if old not in text:
    raise SystemExit('state marker not found')
text = text.replace(old, new, 1)

old = "function categoryList(items){return [...new Set(items.map(x=>x.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt'))}function prefersDark(){try{return !window.matchMedia||window.matchMedia('(prefers-color-scheme: dark)').matches}catch(e){return true}}function pickLogo(item){return prefersDark()?(item.logo_dark||item.logo||item.logo_light||''):(item.logo_light||item.logo||item.logo_dark||'')}function makeFallback(item){const d=document.createElement('div');d.className='fallback';d.textContent=initials(item.name);return d}"
new = "function categoryList(items){return [...new Set(items.map(x=>x.category).filter(Boolean))].sort((a,b)=>a.localeCompare(b,'pt'))}function prefersDark(){try{return !window.matchMedia||window.matchMedia('(prefers-color-scheme: dark)').matches}catch(e){return true}}function pickLogo(item){const slug=String(item.slug||slugFromUrl(item.source_url||item.url)||'').toLowerCase(),b=bundledLogoMap.get(slug)||{};return prefersDark()?(text(b.logo_dark)||item.logo_dark||item.logo||text(b.logo_light)||item.logo_light||''):(text(b.logo_light)||item.logo_light||item.logo||text(b.logo_dark)||item.logo_dark||'')}function makeFallback(item){const d=document.createElement('div');d.className='fallback';d.textContent=initials(item.name);return d}"
if old not in text:
    raise SystemExit('pickLogo marker not found')
text = text.replace(old, new, 1)

old = "async function applyBundledChannelLogos(){try{const r=await fetch('portugal-sports-channels.json',{cache:'no-store'});if(!r.ok)return;const j=await r.json();if(!j||!Array.isArray(j.channels))return;const map=new Map(j.channels.filter(x=>x&&x.slug).map(x=>[String(x.slug).toLowerCase(),x]));let a=loadCustom(),changed=false;a=a.map(x=>{const k=String(x.slug||slugFromUrl(x.source_url||x.url)||'').toLowerCase(),b=map.get(k);if(!b)return x;const dark=text(b.logo_dark),light=text(b.logo_light);if(x.logo_dark===dark&&x.logo_light===light)return x;changed=true;return {...x,logo_dark:dark,logo_light:light};});if(changed)localStorage.setItem(K_CUSTOM,JSON.stringify(a));render()}catch(e){render()}}\nrender();applyBundledChannelLogos();"
new = "async function loadBundledChannelLogos(){try{const r=await fetch('portugal-sports-channels.json',{cache:'no-store'});if(!r.ok){render();return}const j=await r.json();if(!j||!Array.isArray(j.channels)){render();return}bundledLogoMap=new Map(j.channels.filter(x=>x&&x.slug).map(x=>[String(x.slug).toLowerCase(),x]));let a=loadCustom(),changed=false;a=a.map(x=>{const k=String(x.slug||slugFromUrl(x.source_url||x.url)||'').toLowerCase();if(!bundledLogoMap.has(k)||(!x.logo_dark&&!x.logo_light))return x;const y={...x};delete y.logo_dark;delete y.logo_light;changed=true;return y});if(changed)localStorage.setItem(K_CUSTOM,JSON.stringify(a));render()}catch(e){render()}}\nrender();loadBundledChannelLogos();"
if old not in text:
    raise SystemExit('migration marker not found')
text = text.replace(old, new, 1)
index.write_text(text, encoding='utf-8')

build = Path('android/app/build.gradle')
b = build.read_text(encoding='utf-8')
b = b.replace('versionCode 37', 'versionCode 38', 1).replace("versionName '1.6.10'", "versionName '1.6.11'", 1)
build.write_text(b, encoding='utf-8')
