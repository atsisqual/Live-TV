(() => {
  if (window.__liveTvEpgLoaded) return;
  window.__liveTvEpgLoaded = true;

  const K_EPG = 'tv-epg-v1';
  const K_CUSTOM = 'tv-custom';
  const MAX_PAST_MS = 2 * 60 * 60 * 1000;
  const MAX_FUTURE_MS = 30 * 60 * 60 * 1000;

  const norm = value => String(value || '')
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();

  function parseXmltvTime(value) {
    const s = String(value || '').trim();
    const m = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?(?:\s*([+-])(\d{2})(\d{2}))?/);
    if (!m) return NaN;
    const year = Number(m[1]);
    const month = Number(m[2]) - 1;
    const day = Number(m[3]);
    const hour = Number(m[4]);
    const minute = Number(m[5]);
    const second = Number(m[6] || 0);
    let utc = Date.UTC(year, month, day, hour, minute, second);
    if (m[7]) {
      const offset = (Number(m[8]) * 60 + Number(m[9])) * 60 * 1000;
      utc += m[7] === '+' ? -offset : offset;
    }
    return utc;
  }

  function loadChannels() {
    try {
      const value = JSON.parse(localStorage.getItem(K_CUSTOM) || '[]');
      return Array.isArray(value) ? value : [];
    } catch (_) {
      return [];
    }
  }

  function loadEpg() {
    try {
      const value = JSON.parse(localStorage.getItem(K_EPG) || '{}');
      return value && typeof value === 'object' ? value : {};
    } catch (_) {
      return {};
    }
  }

  function saveEpg(value) {
    localStorage.setItem(K_EPG, JSON.stringify(value));
  }

  function formatTime(ms) {
    try {
      return new Intl.DateTimeFormat('pt-PT', {hour: '2-digit', minute: '2-digit'}).format(new Date(ms));
    } catch (_) {
      const d = new Date(ms);
      return String(d.getHours()).padStart(2, '0') + ':' + String(d.getMinutes()).padStart(2, '0');
    }
  }

  function parseXmltv(raw) {
    const xml = new DOMParser().parseFromString(raw, 'application/xml');
    if (xml.querySelector('parsererror')) throw new Error('XMLTV inválido');

    const now = Date.now();
    const min = now - MAX_PAST_MS;
    const max = now + MAX_FUTURE_MS;
    const aliases = {};
    const programmes = {};

    xml.querySelectorAll('channel[id]').forEach(node => {
      const id = String(node.getAttribute('id') || '').trim();
      if (!id) return;
      const names = [...node.querySelectorAll('display-name')]
        .map(x => String(x.textContent || '').trim())
        .filter(Boolean);
      aliases[id] = names;
    });

    xml.querySelectorAll('programme[channel][start][stop]').forEach(node => {
      const channel = String(node.getAttribute('channel') || '').trim();
      if (!channel) return;
      const start = parseXmltvTime(node.getAttribute('start'));
      const stop = parseXmltvTime(node.getAttribute('stop'));
      if (!Number.isFinite(start) || !Number.isFinite(stop) || stop <= min || start >= max) return;
      const titleNode = node.querySelector('title');
      const descNode = node.querySelector('desc');
      const title = String(titleNode ? titleNode.textContent : '').trim() || 'Programa';
      const desc = String(descNode ? descNode.textContent : '').trim();
      (programmes[channel] ||= []).push({s: start, e: stop, t: title, d: desc});
    });

    Object.values(programmes).forEach(list => list.sort((a, b) => a.s - b.s));
    return {updatedAt: now, aliases, programmes};
  }

  function resolveChannelId(item, epg) {
    const explicit = String(item.epg_id || item.epgId || item.tvg_id || item['tvg-id'] || '').trim();
    if (explicit && epg.programmes && epg.programmes[explicit]) return explicit;
    const wanted = norm(item.name);
    if (!wanted) return '';
    for (const [id, names] of Object.entries(epg.aliases || {})) {
      if ((names || []).some(name => norm(name) === wanted) && epg.programmes && epg.programmes[id]) return id;
    }
    return '';
  }

  function currentNext(item, epg, now) {
    const id = resolveChannelId(item, epg);
    if (!id) return null;
    const list = (epg.programmes && epg.programmes[id]) || [];
    let current = null;
    let next = null;
    for (const p of list) {
      if (p.s <= now && p.e > now) current = p;
      else if (p.s > now) {
        next = p;
        break;
      }
    }
    if (!current && !next) return null;
    return {current, next};
  }

  function channelForCard(card, channels) {
    const name = card.querySelector('.cardname');
    if (!name) return null;
    const wanted = norm(name.textContent);
    return channels.find(x => norm(x.name) === wanted) || null;
  }

  function refreshCards() {
    const epg = loadEpg();
    const channels = loadChannels();
    const now = Date.now();
    document.querySelectorAll('.card').forEach(card => {
      let box = card.querySelector('.epgbox');
      if (box) box.remove();
      const item = channelForCard(card, channels);
      if (!item) return;
      const info = currentNext(item, epg, now);
      if (!info) return;

      box = document.createElement('div');
      box.className = 'epgbox';
      if (info.current) {
        const row = document.createElement('div');
        row.className = 'epgrow now';
        row.innerHTML = '<span class="epglabel">AGORA</span><span class="epgtitle"></span><span class="epgtime"></span>';
        row.querySelector('.epgtitle').textContent = info.current.t;
        row.querySelector('.epgtime').textContent = formatTime(info.current.e);
        box.appendChild(row);
      }
      if (info.next) {
        const row = document.createElement('div');
        row.className = 'epgrow';
        row.innerHTML = '<span class="epglabel">A SEGUIR</span><span class="epgtitle"></span><span class="epgtime"></span>';
        row.querySelector('.epgtitle').textContent = info.next.t;
        row.querySelector('.epgtime').textContent = formatTime(info.next.s);
        box.appendChild(row);
      }
      const body = card.querySelector('.cardbody');
      if (body) body.appendChild(box);
    });
  }

  function showToast(message) {
    const toast = document.getElementById('toast');
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    setTimeout(() => toast.classList.remove('show'), 2400);
  }

  function closeSheet() {
    const overlay = document.getElementById('overlay');
    const body = document.getElementById('sheetBody');
    if (overlay) overlay.classList.remove('show');
    if (body) body.innerHTML = '';
  }

  function openEpgSheet() {
    const title = document.getElementById('sheetTitle');
    const body = document.getElementById('sheetBody');
    const overlay = document.getElementById('overlay');
    const epg = loadEpg();
    if (!title || !body || !overlay) return;
    title.textContent = 'Guia TV (EPG)';
    const updated = epg.updatedAt ? new Date(epg.updatedAt).toLocaleString('pt-PT') : 'Ainda não importado';
    const programmeCount = Object.values(epg.programmes || {}).reduce((n, list) => n + list.length, 0);
    body.innerHTML = `
      <div class="form">
        <div class="hint epgstatus"><b>Estado:</b> ${updated}<br>${programmeCount ? programmeCount + ' programas na janela atual.' : 'Sem programação guardada.'}</div>
        <label class="label">XMLTV</label>
        <textarea class="textarea epgtextarea" id="epgXml" placeholder="Cola aqui o conteúdo XMLTV…"></textarea>
        <div class="hint">O guia guarda apenas cerca de 2 horas anteriores e 30 horas futuras. Os canais são associados primeiro por <b>tvg-id</b> e depois pelo nome do canal.</div>
        <div class="actions">
          <button class="action" id="epgClear">Limpar EPG</button>
          <button class="action primary" id="epgImport">Importar</button>
        </div>
      </div>`;
    overlay.classList.add('show');
    const menu = document.getElementById('menu');
    if (menu) menu.classList.remove('show');

    document.getElementById('epgImport').onclick = () => {
      const raw = String(document.getElementById('epgXml').value || '').trim();
      if (!raw) return showToast('Cola primeiro um XMLTV');
      try {
        const parsed = parseXmltv(raw);
        const count = Object.values(parsed.programmes || {}).reduce((n, list) => n + list.length, 0);
        saveEpg(parsed);
        closeSheet();
        refreshCards();
        showToast('EPG importado · ' + count + ' programas');
      } catch (e) {
        showToast('EPG inválido: ' + (e && e.message ? e.message : 'erro desconhecido'));
      }
    };

    document.getElementById('epgClear').onclick = () => {
      localStorage.removeItem(K_EPG);
      closeSheet();
      refreshCards();
      showToast('EPG removido');
    };
  }

  function installStyles() {
    if (document.getElementById('epgStyles')) return;
    const style = document.createElement('style');
    style.id = 'epgStyles';
    style.textContent = `
      .epgbox{margin-top:10px;padding-top:9px;border-top:1px solid rgba(255,255,255,.07);display:grid;gap:6px}
      .epgrow{display:grid;grid-template-columns:auto minmax(0,1fr) auto;align-items:center;gap:6px;font-size:10px;color:#8d96a5;min-width:0}
      .epgrow.now{color:#cbd2dc}.epglabel{font-size:8px;font-weight:850;letter-spacing:.45px;color:#768091}
      .epgrow.now .epglabel{color:#ff5b72}.epgtitle{white-space:nowrap;overflow:hidden;text-overflow:ellipsis}.epgtime{font-variant-numeric:tabular-nums;color:#747e8d}
      .epgtextarea{min-height:230px}.epgstatus{padding:11px 12px;border:1px solid #29313d;border-radius:13px;background:#0d1117;margin-bottom:15px}
    `;
    document.head.appendChild(style);
  }

  function installMenu() {
    const menu = document.getElementById('menu');
    if (!menu || document.getElementById('epgMenuButton')) return;
    const button = document.createElement('button');
    button.id = 'epgMenuButton';
    button.textContent = '▤ Guia TV (EPG)';
    button.onclick = event => {
      event.preventDefault();
      event.stopPropagation();
      openEpgSheet();
    };
    const danger = menu.querySelector('.danger');
    menu.insertBefore(button, danger || null);
  }

  installStyles();
  installMenu();
  refreshCards();

  const observer = new MutationObserver(() => {
    clearTimeout(window.__liveTvEpgRefreshTimer);
    window.__liveTvEpgRefreshTimer = setTimeout(refreshCards, 60);
  });
  const channels = document.getElementById('channels');
  const recent = document.getElementById('recent');
  if (channels) observer.observe(channels, {childList: true, subtree: true});
  if (recent) observer.observe(recent, {childList: true, subtree: true});
  setInterval(refreshCards, 60 * 1000);
})();
