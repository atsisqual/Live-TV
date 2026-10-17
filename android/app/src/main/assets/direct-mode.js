(function () {
  'use strict';

  if (window.__liveTvDirectModeUi) return;
  window.__liveTvDirectModeUi = true;

  function resolvedPlayerUrl(item) {
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

  function installResolvedPlayerItems() {
    try {
      if (typeof window.allItems !== 'function') return false;
      if (window.allItems.__liveTvPlayerResolved) return true;

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

      wrappedAllItems.__liveTvPlayerResolved = true;
      wrappedAllItems.__liveTvOriginal = originalAllItems;
      window.allItems = wrappedAllItems;
      return true;
    } catch (e) {
      return false;
    }
  }

  if (!installResolvedPlayerItems()) {
    var installAttempts = 0;
    var installTimer = setInterval(function () {
      installAttempts += 1;
      if (installResolvedPlayerItems() || installAttempts >= 40) {
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
        if (item && item.url && /^https?:\/\//i.test(String(item.url))) return item;
      }
    } catch (e) {}
    return null;
  }

  function remember(item) {
    try {
      var url = resolvedPlayerUrl(item);
      if (url && window.LiveTvEpg && typeof window.LiveTvEpg.rememberChannel === 'function') {
        window.LiveTvEpg.rememberChannel(url);
      }
    } catch (e) {}
  }

  if (typeof window.openChannel === 'function' && !window.openChannel.__liveTvDirectWrapped) {
    var originalOpenChannel = window.openChannel;
    var wrappedOpenChannel = function (item) {
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
