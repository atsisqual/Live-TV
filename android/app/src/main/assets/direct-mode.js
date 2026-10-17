(function () {
  'use strict';

  if (window.__liveTvDirectModeUi) return;
  window.__liveTvDirectModeUi = true;

  if (!window.LiveTvProviderRegistry) {
    window.LiveTvProviderRegistry = {
      isManaged: function (slug) { return String(slug || '').toLowerCase() === 'canal-11'; },
      resolve: function () { return ''; }
    };
  }

  (function loadProviderRegistry() {
    try {
      if (document.querySelector('script[data-livetv-provider-registry]')) return;
      var script = document.createElement('script');
      script.src = 'file:///android_asset/provider-registry.js';
      script.async = false;
      script.setAttribute('data-livetv-provider-registry', '1');
      (document.head || document.documentElement).appendChild(script);
    } catch (e) {}
  })();

  function slugOf(item) {
    try {
      var slug = String(item && item.slug ? item.slug : '').trim().toLowerCase();
      if (slug) return slug;
      var raw = String(item && item.url ? item.url : '').toLowerCase();
      if (/https?:\/\/apitotal\.net\/[^?#]*\/canal-11(?:[?#]|$)/i.test(raw)) return 'canal-11';
      return '';
    } catch (e) {
      return '';
    }
  }

  function playerNumberFor(item) {
    try {
      var n = parseInt(item && item.player, 10);
      if (isFinite(n) && n >= 1 && n <= 20) return n;
      var raw = String(item && item.url ? item.url : '');
      var match = raw.match(/(?:#|[?&])livetv-player=(\d{1,2})/i);
      n = match ? parseInt(match[1], 10) : 1;
      return isFinite(n) && n >= 1 && n <= 20 ? n : 1;
    } catch (e) {
      return 1;
    }
  }

  function registry() {
    return window.LiveTvProviderRegistry || null;
  }

  function isManaged(item) {
    try {
      var r = registry();
      return !!(r && typeof r.isManaged === 'function' && r.isManaged(slugOf(item)));
    } catch (e) {
      return false;
    }
  }

  function directProviderUrl(item) {
    try {
      var r = registry();
      if (!r || typeof r.resolve !== 'function') return '';
      var value = String(r.resolve(slugOf(item), playerNumberFor(item)) || '').trim();
      return /^https?:\/\//i.test(value) ? value : '';
    } catch (e) {
      return '';
    }
  }

  function unavailableUrl(item) {
    var slug = slugOf(item) || 'canal';
    return 'file:///android_asset/provider-unavailable.html?channel=' + encodeURIComponent(slug) +
      '&player=' + playerNumberFor(item);
  }

  function externalPlayerUrl(item) {
    try {
      if (!item || !item.url) return '';
      if (typeof playerUrl === 'function') {
        return String(playerUrl(item) || item.url || '');
      }
      return String(item.url || '');
    } catch (e) {
      return String(item && item.url ? item.url : '');
    }
  }

  function resolvedPlayerUrl(item) {
    if (isManaged(item)) return directProviderUrl(item) || unavailableUrl(item);
    return externalPlayerUrl(item);
  }

  function installResolvedProviderItems() {
    try {
      if (typeof window.allItems !== 'function') return false;
      if (window.allItems.__liveTvProviderRegistryResolved) return true;

      var originalAllItems = window.allItems;
      var wrappedAllItems = function () {
        var items = originalAllItems.apply(this, arguments);
        if (!Array.isArray(items)) return items;

        return items.map(function (item) {
          if (!item || !item.url) return item;
          var resolved = resolvedPlayerUrl(item);
          if (!resolved || resolved === String(item.url)) return item;
          return Object.assign({}, item, { url: resolved });
        });
      };

      wrappedAllItems.__liveTvProviderRegistryResolved = true;
      wrappedAllItems.__liveTvProviderRegistryOriginal = originalAllItems;
      window.allItems = wrappedAllItems;
      return true;
    } catch (e) {
      return false;
    }
  }

  if (!installResolvedProviderItems()) {
    var installAttempts = 0;
    var installTimer = setInterval(function () {
      installAttempts += 1;
      if (installResolvedProviderItems() || installAttempts >= 40) {
        clearInterval(installTimer);
      }
    }, 250);
  }

  function lastChannelItem() {
    try {
      if (typeof allItems !== 'function' || typeof loadRecent !== 'function' || typeof itemKey !== 'function') return null;
      var items = allItems();
      var byKey = new Map(items.map(function (item) { return [itemKey(item), item]; }));
      var recent = loadRecent();
      for (var i = 0; i < recent.length; i++) {
        var item = byKey.get(recent[i]);
        if (!item) continue;
        if (isManaged(item)) return item;
        if (item.url && /^https?:\/\//i.test(String(item.url))) return item;
      }
    } catch (e) {}
    return null;
  }

  function remember(item) {
    try {
      if (isManaged(item) && !directProviderUrl(item)) return;
      var url = resolvedPlayerUrl(item);
      if (url && /^https?:\/\//i.test(url) && window.LiveTvEpg && typeof window.LiveTvEpg.rememberChannel === 'function') {
        window.LiveTvEpg.rememberChannel(url);
      }
    } catch (e) {}
  }

  if (typeof window.openChannel === 'function' && !window.openChannel.__liveTvDirectWrapped) {
    var originalOpenChannel = window.openChannel;
    var wrappedOpenChannel = function (item) {
      if (isManaged(item)) {
        var direct = directProviderUrl(item);
        try {
          if (typeof rememberRecent === 'function') rememberRecent(item);
          sessionStorage.setItem('tv-scroll-v1', String(window.scrollY || 0));
        } catch (e) {}

        if (!direct) {
          if (typeof showToast === 'function') showToast('Provider direto não configurado para este canal');
          return;
        }

        remember(item);
        window.location.href = direct;
        return;
      }

      remember(item);
      return originalOpenChannel(item);
    };
    wrappedOpenChannel.__liveTvDirectWrapped = true;
    window.openChannel = wrappedOpenChannel;
  }

  window.LiveTvEnableDirectMode = function () {
    var item = lastChannelItem();
    if (!item) {
      if (typeof showToast === 'function') showToast('Abre primeiro um canal para definir o último canal');
      return;
    }

    if (isManaged(item) && !directProviderUrl(item)) {
      if (typeof showToast === 'function') showToast('Provider direto não configurado para este canal');
      return;
    }

    try {
      if (!window.LiveTvEpg || typeof window.LiveTvEpg.enableDirectMode !== 'function') {
        if (typeof showToast === 'function') showToast('O modo direto ainda não está disponível');
        return;
      }
      if (typeof closeSheet === 'function') closeSheet();
      window.LiveTvEpg.enableDirectMode(resolvedPlayerUrl(item));
    } catch (e) {
      if (typeof showToast === 'function') showToast('Não foi possível ativar o modo direto');
    }
  };

  function openDirectModeSheet() {
    var item = lastChannelItem();
    if (!item) {
      if (typeof showToast === 'function') showToast('Abre primeiro um canal para definir o último canal');
      return;
    }

    if (isManaged(item) && !directProviderUrl(item)) {
      if (typeof showToast === 'function') showToast('Provider direto não configurado para este canal');
      return;
    }

    if (typeof openSheet !== 'function') return;
    var safeName = typeof esc === 'function' ? esc(item.name || 'último canal') : String(item.name || 'último canal');
    openSheet('Modo player direto',
      '<div class="form">' +
      '<div class="hint" style="font-size:14px;color:#c7ced8">' +
      'Ao ativar, a Live TV passa a abrir diretamente em <b>' + safeName + '</b> e deixa de mostrar o botão <b>Voltar aos canais</b> no player. ' +
      'Continuas a poder mudar de canal pelo menu lateral. Para voltar à homepage terás de limpar os dados da aplicação.' +
      '</div>' +
      '<div class="actions"><button class="action" onclick="closeSheet()">Cancelar</button>' +
      '<button class="action primary" onclick="LiveTvEnableDirectMode()">Ativar</button></div>' +
      '</div>');
  }

  var menu = document.getElementById('menu');
  if (menu && !document.getElementById('directPlayerModeButton')) {
    var button = document.createElement('button');
    button.id = 'directPlayerModeButton';
    button.type = 'button';
    button.textContent = '▶ Modo player direto';
    button.addEventListener('click', function (event) {
      event.preventDefault();
      event.stopPropagation();
      openDirectModeSheet();
    });

    var clearButton = menu.querySelector('button[data-menu="clear"]');
    if (clearButton) menu.insertBefore(button, clearButton);
    else menu.appendChild(button);
  }
})();
