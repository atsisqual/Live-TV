(function () {
  'use strict';

  if (window.__liveTvExternalChannelLogos) return;
  window.__liveTvExternalChannelLogos = true;

  // Channel logos belong to the imported channel list, not to the APK.
  // Preserve logo/logo_dark/logo_light when an imported JSON updates an existing channel.
  var originalDoImport = window.doImport;
  if (typeof originalDoImport === 'function') {
    window.doImport = function () {
      var input = document.getElementById('importText');
      var raw = input ? String(input.value || '').trim() : '';
      var incoming = [];

      if (raw) {
        try {
          incoming = (raw[0] === '[' || raw[0] === '{') ? parseJson(raw) : parseText(raw);
        } catch (ignored) {}
      }

      originalDoImport();
      if (!incoming.length) return;

      var current = loadCustom();
      var changed = false;
      incoming.forEach(function (source) {
        if (!source) return;
        var index = current.findIndex(function (target) { return sameChannel(target, source); });
        if (index < 0) return;

        ['logo', 'logo_dark', 'logo_light'].forEach(function (key) {
          var value = text(source[key]);
          if (value && text(current[index][key]) !== value) {
            current[index][key] = value;
            changed = true;
          }
        });
      });

      if (changed) {
        localStorage.setItem(K_CUSTOM, JSON.stringify(current));
        render();
      }
    };
  }

  // Full exports/backups must keep the themed logos so the JSON is self-contained.
  window.channelExport = function () {
    return loadCustom().map(function (x) {
      return {
        name: x.name,
        url: x.source_url || x.url,
        slug: x.slug || undefined,
        epg_id: x.epg_id || undefined,
        player: x.player || undefined,
        category: x.category || undefined,
        logo: x.logo || undefined,
        logo_dark: x.logo_dark || undefined,
        logo_light: x.logo_light || undefined
      };
    });
  };
})();
