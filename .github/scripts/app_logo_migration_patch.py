from pathlib import Path


def replace_once(text, old, new, label):
    if old not in text:
        raise SystemExit(f'{label}: expected text not found')
    return text.replace(old, new, 1)

index_path = Path('android/app/src/main/assets/index.html')
index = index_path.read_text(encoding='utf-8')
old_tail = "$('search').addEventListener('input',e=>{query=e.target.value;$('searchBox').classList.toggle('hastext',!!query);render()});$('clearSearch').onclick=()=>{$('search').value='';query='';$('searchBox').classList.remove('hastext');render();$('search').focus()};render();"
new_tail = """$('search').addEventListener('input',e=>{query=e.target.value;$('searchBox').classList.toggle('hastext',!!query);render()});$('clearSearch').onclick=()=>{$('search').value='';query='';$('searchBox').classList.remove('hastext');render();$('search').focus()};
async function applyBundledChannelLogos(){try{const r=await fetch('portugal-sports-channels.json',{cache:'no-store'});if(!r.ok)return;const j=await r.json();if(!j||!Array.isArray(j.channels))return;const map=new Map(j.channels.filter(x=>x&&x.slug).map(x=>[String(x.slug).toLowerCase(),x]));let a=loadCustom(),changed=false;a=a.map(x=>{const k=String(x.slug||slugFromUrl(x.source_url||x.url)||'').toLowerCase(),b=map.get(k);if(!b)return x;const dark=text(b.logo_dark),light=text(b.logo_light);if(x.logo_dark===dark&&x.logo_light===light)return x;changed=true;return {...x,logo_dark:dark,logo_light:light};});if(changed)localStorage.setItem(K_CUSTOM,JSON.stringify(a));render()}catch(e){render()}}
render();applyBundledChannelLogos();"""
index = replace_once(index, old_tail, new_tail, 'automatic bundled logo migration')
index_path.write_text(index, encoding='utf-8')

gradle_path = Path('android/app/build.gradle')
gradle = gradle_path.read_text(encoding='utf-8')
gradle = replace_once(gradle, 'versionCode 36', 'versionCode 37', 'versionCode')
gradle = replace_once(gradle, "versionName '1.6.9'", "versionName '1.6.10'", 'versionName')
gradle_path.write_text(gradle, encoding='utf-8')
