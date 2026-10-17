(() => {
  if (window.__liveTvEpgUrlLoaded) return;
  window.__liveTvEpgUrlLoaded = true;

  const K_URL = 'tv-epg-url-v1';
  const K_META = 'tv-epg-url-meta-v1';
  const K_EPG = 'tv-epg-v1';
  const REFRESH_MS = 6 * 60 * 60 * 1000;
  const MAX_PAST_MS = 2 * 60 * 60 * 1000;
  const MAX_FUTURE_MS = 30 * 60 * 60 * 1000;
  const pending = new Map();

  const text = value => String(value || '').trim();

  function parseXmltvTime(value) {
    const s = text(value);
    const m = s.match(/^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?(?:\s*([+-])(\d{2})(\d{2}))?/);
    if (!m) return NaN;
    let utc = Date.UTC(Number(m[1]), Number(m[2]) - 1, Number(m[3]), Number(m[4]), Number(m[5]), Number(m[6] || 0));
    if (m[7]) {
      const offset = (Number(m[8]) * 60 + Number(m[9])) * 60 * 1000;
      utc += m[7] === '+' ? -offset : offset;
    }
    return utc;
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
      const id = text(node.getAttribute('id'));
      if (!id) return;
      aliases[id] = [...node.querySelectorAll('display-name')].map(x => text(x.textContent)).filter(Boolean);
    });

    xml.querySelectorAll('programme[channel][start][stop]').forEach(node => {
      const channel = text(node.getAttribute('channel'));
      const start = parseXmltvTime(node.getAttribute('start'));
      const stop = parseXmltvTime(node.getAttribute('stop'));
      if (!channel || !Number.isFinite(start) || !Number.isFinite(stop) || stop <= min || start >= max) return;
      const title = text(node.querySelector('title')?.textContent) || 'Programa';
      const desc = text(node.querySelector('desc')?.textContent);
      (programmes[channel] ||= []).push({s: start, e: stop, t: title, d: desc});
    });

    Object.values(programmes).forEach(list => list.sort((a, b) => a.s - b.s));
    return {updatedAt: now, aliases, programmes};
  }

  function toast(message) {
    const node = document.getElementById('toast');
    if (!node) return;
    node.textContent = message;
    node.classList.add('show');
    setTimeout(() => node.classList.remove('show'), 2400);
  }

  function closeSheet() {
    document.getElementById('overlay')?.classList.remove('show');
    const body = document.getElementById('sheetBody');
    if (body) body.innerHTML = '';
  }

  function validUrl(value) {
    try {
      const u = new URL(value);
      return u.protocol === 'https:';
    } catch (_) {
      return false;
    }
  }

  function requestXmltv(url, interactive) {
    if (!window.LiveTvEpg || typeof window.LiveTvEpg.fetch !== 'function') {
      if (interactive) toast('Atualização automática indisponível nesta versão');
      return;
    }
    if (!validUrl(url)) {
      if (interactive) toast('Usa um endereço XMLTV HTTPS válido');
      return;
    }
    const id = 'epg-' + Date.now() + '-' + Math.random().toString(36).slice(2);
    pending.set(id, {url, interactive});
    if (interactive) toast('A descarregar EPG…');
    window.LiveTvEpg.fetch(id, url);
  }

  window.LiveTvEpgNativeResult = (id, ok, payload) => {
    const request = pending.get(id);
    if (!request) return;
    pending.delete(id);
    if (!ok) {
      if (request.interactive) toast('Falha no EPG: ' + text(payload || 'erro de download'));
      return;
    }
    try {
      const parsed = parseXmltv(payload);
      localStorage.setItem(K_EPG, JSON.stringify(parsed));
      localStorage.setItem(K_URL, request.url);
      localStorage.setItem(K_META, JSON.stringify({lastSuccess: Date.now()}));
      const count = Object.values(parsed.programmes || {}).reduce((n, list) => n + list.length, 0);
      if (request.interactive) {
        closeSheet();
        toast('EPG atualizado · ' + count + ' programas');
      }
      setTimeout(() => location.reload(), request.interactive ? 450 : 50);
    } catch (e) {
      if (request.interactive) toast('EPG inválido: ' + (e?.message || 'erro desconhecido'));
    }
  };

  function openSourceSheet() {
    const title = document.getElementById('sheetTitle');
    const body = document.getElementById('sheetBody');
    const overlay = document.getElementById('overlay');
    if (!title || !body || !overlay) return;
    const url = localStorage.getItem(K_URL) || '';
    let last = 0;
    try { last = JSON.parse(localStorage.getItem(K_META) || '{}').lastSuccess || 0; } catch (_) {}
    title.textContent = 'Fonte EPG automática';
    body.innerHTML = `
      <div class="form">
        <label class="label">URL XMLTV</label>
        <input class="input" id="epgSourceUrl" inputmode="url" placeholder="https://…/guide.xml" value="${url.replace(/&/g,'&amp;').replace(/"/g,'&quot;').replace(/</g,'&lt;')}">
        <div class="hint">A app atualiza esta fonte automaticamente aproximadamente a cada 6 horas quando voltares ao ecrã de canais. Também suporta XMLTV servido em gzip.</div>
        <div class="hint">Última atualização: <b>${last ? new Date(last).toLocaleString('pt-PT') : 'nunca'}</b></div>
        <div class="actions">
          <button class="action" id="epgSourceRemove">Remover</button>
          <button class="action primary" id="epgSourceRefresh">Guardar e atualizar</button>
        </div>
      </div>`;
    overlay.classList.add('show');
    document.getElementById('menu')?.classList.remove('show');

    document.getElementById('epgSourceRefresh').onclick = () => {
      const value = text(document.getElementById('epgSourceUrl').value);
      if (!validUrl(value)) return toast('Usa um endereço XMLTV HTTPS válido');
      localStorage.setItem(K_URL, value);
      requestXmltv(value, true);
    };
    document.getElementById('epgSourceRemove').onclick = () => {
      localStorage.removeItem(K_URL);
      localStorage.removeItem(K_META);
      closeSheet();
      toast('Fonte EPG automática removida');
    };
  }

  function installMenu() {
    const menu = document.getElementById('menu');
    if (!menu || document.getElementById('epgSourceMenuButton')) return;
    const button = document.createElement('button');
    button.id = 'epgSourceMenuButton';
    button.textContent = '↻ Fonte EPG automática';
    button.onclick = event => {
      event.preventDefault();
      event.stopPropagation();
      openSourceSheet();
    };
    const guide = document.getElementById('epgMenuButton');
    menu.insertBefore(button, guide || menu.querySelector('.danger') || null);
  }

  function autoRefresh() {
    const url = localStorage.getItem(K_URL) || '';
    if (!validUrl(url)) return;
    let last = 0;
    try { last = JSON.parse(localStorage.getItem(K_META) || '{}').lastSuccess || 0; } catch (_) {}
    if (!last || Date.now() - last >= REFRESH_MS) requestXmltv(url, false);
  }

  installMenu();
  setTimeout(autoRefresh, 900);
})();
