(function () {
  'use strict';

  if (window.__liveTvExternalChannelLogos) return;
  window.__liveTvExternalChannelLogos = true;

  if (!window.__liveTvProviderProfileMetadata) {
    window.__liveTvProviderProfileMetadata = true;

    function normalizeProviderProfile(value) {
      var profile = String(value || '').trim().toLowerCase();
      profile = profile.replace(/[^a-z0-9_-]+/g, '_').replace(/^_+|_+$/g, '');
      return profile || 'generic';
    }

    var originalNormalizeStored = window.normalizeStored;
    if (typeof originalNormalizeStored === 'function') {
      window.normalizeStored = function (source) {
        var normalized = originalNormalizeStored.apply(this, arguments);
        if (!normalized) return normalized;
        if (source && typeof source === 'object' && !Array.isArray(source)) {
          normalized.provider_profile = normalizeProviderProfile(
            source.provider_profile || source.providerProfile || normalized.provider_profile
          );
        } else if (!normalized.provider_profile) {
          normalized.provider_profile = 'generic';
        }
        return normalized;
      };
    }

    var originalOpenChannel = window.openChannel;
    if (typeof originalOpenChannel === 'function') {
      window.openChannel = function (item) {
        try {
          var source = item || {};
          var profile = normalizeProviderProfile(source.provider_profile);
          if ((!source.provider_profile || profile === 'generic') &&
              typeof loadCustom === 'function' && typeof sameChannel === 'function') {
            var stored = loadCustom();
            var found = stored.find(function (candidate) {
              try { return sameChannel(candidate, source); } catch (ignored) { return false; }
            });
            if (found) profile = normalizeProviderProfile(found.provider_profile);
          }
          if (window.LiveTvProviderProfile &&
              typeof window.LiveTvProviderProfile.select === 'function') {
            window.LiveTvProviderProfile.select(
              profile,
              String(source.source_url || source.url || '')
            );
          }
        } catch (ignored) {}
        return originalOpenChannel.apply(this, arguments);
      };
    }
  }

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

        ['logo', 'logo_dark', 'logo_light', 'provider_profile'].forEach(function (key) {
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

      // Reload the local grid after an import so the native sidebar recaptures
      // the same self-contained channel list and caches its logos as well.
      setTimeout(function () { window.location.reload(); }, 120);
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
        provider_profile: x.provider_profile || undefined,
        category: x.category || undefined,
        logo: x.logo || undefined,
        logo_dark: x.logo_dark || undefined,
        logo_light: x.logo_light || undefined
      };
    });
  };
})();
