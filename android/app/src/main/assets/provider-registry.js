(function () {
  'use strict';

  // The registry remains available for future direct providers, but no channel
  // is managed by it unless a working direct source is explicitly configured.
  var CHANNELS = Object.freeze({});

  function normalizeSlug(value) {
    return String(value || '').trim().toLowerCase();
  }

  function playerNumber(value) {
    var n = parseInt(value, 10);
    return isFinite(n) && n >= 1 && n <= 20 ? n : 1;
  }

  function isHttpUrl(value) {
    return /^https?:\/\//i.test(String(value || ''));
  }

  function isManaged(slug) {
    return Object.prototype.hasOwnProperty.call(CHANNELS, normalizeSlug(slug));
  }

  function resolve(slug, player) {
    var cfg = CHANNELS[normalizeSlug(slug)];
    if (!cfg) return '';
    var url = String(cfg.players[playerNumber(player)] || '').trim();
    return isHttpUrl(url) ? url : '';
  }

  window.LiveTvProviderRegistry = Object.freeze({
    channels: CHANNELS,
    isManaged: isManaged,
    resolve: resolve
  });
})();
