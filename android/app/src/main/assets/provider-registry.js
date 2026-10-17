(function () {
  'use strict';

  var CHANNELS = Object.freeze({
    'canal-11': Object.freeze({
      name: 'Canal 11',
      players: Object.freeze({
        1: '',
        2: '',
        3: ''
      })
    })
  });

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
