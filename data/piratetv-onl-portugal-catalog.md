# PirateTV / API Total — Portugal catalogue and player trace snapshot

Snapshot date: `2026-10-16`

## Source and scope

This snapshot is extracted from the supplied API Total frontend bundle `index-lJDoyy0G.js` (the bundle loaded by `https://apitotal.net/0112/tv001/...`). The embedded channel catalogue contains **125 channels total**; **19 records include category `16`**, which is the Portugal category in this bundle.

The bundle declares the route `/tv001/:slug`, so each catalogue slug maps to an API Total channel page at `https://apitotal.net/0112/tv001/<slug>`. The `acf.iframes` array contains the player/embed alternatives exposed by the frontend.

This document records only public channel/player/embed page URLs. Direct media manifests (`.m3u8`, `.mpd`, `.ts`), temporary tokens, cookies, signed media URLs and DRM/license material are intentionally not recorded.

## Example trace — Sport TV 5

```text
piratetv.onl/canal/sport-tv-5-ao-vivo-001
  → apitotal.net/0112/tv001/sport-tv-5
  → PLAYER 1: wideiptv.top/player/SPT5
  → HLS.js/video player layer
  → direct media manifest/token layer (not recorded here)
```

The supplied source for `wideiptv.top/player/SPT5` showed that this page is already a player container: it creates a `<video>` element, loads HLS.js, and refreshes a short-lived token. There is no additional iframe between that player page and the media layer.

## Portugal catalogue extracted from the bundle

| Channel | Slug | EPG ID | API Total page | Player / embed pages | Direct media entry in bundle |
|---|---|---|---|---|---|
| Canal 11 | `canal-11` | `Canal11.pt` | `https://apitotal.net/0112/tv001/canal-11` | `https://wideiptv.top/player/Canal11`<br>`https://v2.rdembed.sbs/canal11`<br>`https://dlhd.st/stream/stream-540.php` | No |
| Dazn | `dazn` | `DAZN1.pt` | `https://apitotal.net/0112/tv001/dazn` | `https://wideiptv.top/player/ELEVEN1`<br>`https://w6.sportsonliine.click/channels/pt/eleven1.php`<br>`https://v2.rdembed.sbs/dazn`<br>`https://dlhd.st/stream/stream-455.php` | No |
| Dazn2 | `dazn2` | `DAZN2.pt` | `https://apitotal.net/0112/tv001/dazn2` | `https://wideiptv.top/player/ELEVEN2`<br>`https://w6.sportsonliine.click/channels/pt/eleven2.php`<br>`https://v2.rdembed.sbs/dazn2`<br>`https://dlhd.st/stream/stream-456.php` | No |
| Dazn3 | `dazn3` | `DAZN3.pt` | `https://apitotal.net/0112/tv001/dazn3` | `https://wideiptv.top/player/ELEVEN3`<br>`https://w6.sportsonliine.click/channels/pt/eleven3.php`<br>`https://v2.rdembed.sbs/dazn3`<br>`https://dlhd.st/stream/stream-457.php` | No |
| Porto Canal | `porto-canal` | `PortoCanal.pt` | `https://apitotal.net/0112/tv001/porto-canal` | `https://v2.rdembed.sbs/portocanal` | Yes — 1 URL(s) omitted |
| RTP1 | `rtp1` | `RTP1.pt` | `https://apitotal.net/0112/tv001/rtp1` | `https://dlhd.st/stream/stream-719.php`<br>`https://wideiptv.top/player.php?stream=RTP1` | Yes — 1 URL(s) omitted |
| RTP2 | `rtp2` | `RTP2.pt` | `https://apitotal.net/0112/tv001/rtp2` | `https://dlhd.st/stream/stream-720.php`<br>`https://wideiptv.top/player.php?stream=RTP2` | Yes — 1 URL(s) omitted |
| RTP3 | `rtp3` | `RTP3.pt` | `https://apitotal.net/0112/tv001/rtp3` | `https://dlhd.st/stream/stream-721.php` | Yes — 1 URL(s) omitted |
| Sport TV | `sport-tv` | `SportTV1.pt` | `https://apitotal.net/0112/tv001/sport-tv` | `https://wideiptv.top/player/SPT1`<br>`https://w6.sportsonliine.click/channels/pt/sporttv1.php`<br>`https://v2.rdembed.sbs/sporttv` | No |
| Sport TV 2 | `sport-tv-2` | `SportTV2.pt` | `https://apitotal.net/0112/tv001/sport-tv-2` | `https://wideiptv.top/player/SPT2`<br>`https://w6.sportsonliine.click/channels/pt/sporttv2.php`<br>`https://v2.rdembed.sbs/sporttv2` | No |
| Sport TV 3 | `sport-tv-3` | `SportTV3.pt` | `https://apitotal.net/0112/tv001/sport-tv-3` | `https://wideiptv.top/player/SPT3`<br>`https://w6.sportsonliine.click/channels/pt/sporttv3.php`<br>`https://v2.rdembed.sbs/sporttv3` | No |
| Sport TV 4 | `sport-tv-4` | `SportTV4.pt` | `https://apitotal.net/0112/tv001/sport-tv-4` | `https://wideiptv.top/player/SPT4`<br>`https://w6.sportsonliine.click/channels/pt/sporttv4.php`<br>`https://v2.rdembed.sbs/sporttv4` | No |
| Sport TV 5 | `sport-tv-5` | `SportTV5.pt` | `https://apitotal.net/0112/tv001/sport-tv-5` | `https://wideiptv.top/player/SPT5`<br>`https://w6.sportsonliine.click/channels/pt/sporttv5.php`<br>`https://v2.rdembed.sbs/sporttv5` | No |
| Sport TV 6 | `sport-tv-6` | `SportTV6.pt` | `https://apitotal.net/0112/tv001/sport-tv-6` | `https://wideiptv.top/player/SPT6`<br>`https://v2.rdembed.sbs/sporttv6` | No |
| Sporting TV | `sporting-tv` | `SportingTV.pt` | `https://apitotal.net/0112/tv001/sporting-tv` | `https://wideiptv.top/player/sporting` | No |
| Benfica TV | `benfica-tv` | `BenficaTV.pt` | `https://apitotal.net/0112/tv001/benfica-tv` | `https://dlhd.st/stream/stream-380.php`<br>`https://w6.sportsonliine.click/channels/pt/btv.php`<br>`https://wideiptv.top/player/BTV1` | No |
| TVI | `tvi` | `TVI.pt` | `https://apitotal.net/0112/tv001/tvi` | — | Yes — 1 URL(s) omitted |
| TVI Reality | `tvi-reality` | `TVIReality.pt` | `https://apitotal.net/0112/tv001/tvi-reality` | — | Yes — 1 URL(s) omitted |
| V+ TVI | `vmais-tvi` | `VPlusTVI.pt` | `https://apitotal.net/0112/tv001/vmais-tvi` | — | Yes — 1 URL(s) omitted |

## Extracted player/embed pages by channel

### Canal 11

- API Total: `https://apitotal.net/0112/tv001/canal-11`
- Player/embed 1: `https://wideiptv.top/player/Canal11`
- Player/embed 2: `https://v2.rdembed.sbs/canal11`
- Player/embed 3: `https://dlhd.st/stream/stream-540.php`

### Dazn

- API Total: `https://apitotal.net/0112/tv001/dazn`
- Player/embed 1: `https://wideiptv.top/player/ELEVEN1`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/eleven1.php`
- Player/embed 3: `https://v2.rdembed.sbs/dazn`
- Player/embed 4: `https://dlhd.st/stream/stream-455.php`

### Dazn2

- API Total: `https://apitotal.net/0112/tv001/dazn2`
- Player/embed 1: `https://wideiptv.top/player/ELEVEN2`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/eleven2.php`
- Player/embed 3: `https://v2.rdembed.sbs/dazn2`
- Player/embed 4: `https://dlhd.st/stream/stream-456.php`

### Dazn3

- API Total: `https://apitotal.net/0112/tv001/dazn3`
- Player/embed 1: `https://wideiptv.top/player/ELEVEN3`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/eleven3.php`
- Player/embed 3: `https://v2.rdembed.sbs/dazn3`
- Player/embed 4: `https://dlhd.st/stream/stream-457.php`

### Porto Canal

- API Total: `https://apitotal.net/0112/tv001/porto-canal`
- Player/embed 1: `https://v2.rdembed.sbs/portocanal`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### RTP1

- API Total: `https://apitotal.net/0112/tv001/rtp1`
- Player/embed 1: `https://dlhd.st/stream/stream-719.php`
- Player/embed 2: `https://wideiptv.top/player.php?stream=RTP1`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### RTP2

- API Total: `https://apitotal.net/0112/tv001/rtp2`
- Player/embed 1: `https://dlhd.st/stream/stream-720.php`
- Player/embed 2: `https://wideiptv.top/player.php?stream=RTP2`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### RTP3

- API Total: `https://apitotal.net/0112/tv001/rtp3`
- Player/embed 1: `https://dlhd.st/stream/stream-721.php`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### Sport TV

- API Total: `https://apitotal.net/0112/tv001/sport-tv`
- Player/embed 1: `https://wideiptv.top/player/SPT1`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/sporttv1.php`
- Player/embed 3: `https://v2.rdembed.sbs/sporttv`

### Sport TV 2

- API Total: `https://apitotal.net/0112/tv001/sport-tv-2`
- Player/embed 1: `https://wideiptv.top/player/SPT2`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/sporttv2.php`
- Player/embed 3: `https://v2.rdembed.sbs/sporttv2`

### Sport TV 3

- API Total: `https://apitotal.net/0112/tv001/sport-tv-3`
- Player/embed 1: `https://wideiptv.top/player/SPT3`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/sporttv3.php`
- Player/embed 3: `https://v2.rdembed.sbs/sporttv3`

### Sport TV 4

- API Total: `https://apitotal.net/0112/tv001/sport-tv-4`
- Player/embed 1: `https://wideiptv.top/player/SPT4`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/sporttv4.php`
- Player/embed 3: `https://v2.rdembed.sbs/sporttv4`

### Sport TV 5

- API Total: `https://apitotal.net/0112/tv001/sport-tv-5`
- Player/embed 1: `https://wideiptv.top/player/SPT5`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/sporttv5.php`
- Player/embed 3: `https://v2.rdembed.sbs/sporttv5`

### Sport TV 6

- API Total: `https://apitotal.net/0112/tv001/sport-tv-6`
- Player/embed 1: `https://wideiptv.top/player/SPT6`
- Player/embed 2: `https://v2.rdembed.sbs/sporttv6`

### Sporting TV

- API Total: `https://apitotal.net/0112/tv001/sporting-tv`
- Player/embed 1: `https://wideiptv.top/player/sporting`

### Benfica TV

- API Total: `https://apitotal.net/0112/tv001/benfica-tv`
- Player/embed 1: `https://dlhd.st/stream/stream-380.php`
- Player/embed 2: `https://w6.sportsonliine.click/channels/pt/btv.php`
- Player/embed 3: `https://wideiptv.top/player/BTV1`

### TVI

- API Total: `https://apitotal.net/0112/tv001/tvi`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### TVI Reality

- API Total: `https://apitotal.net/0112/tv001/tvi-reality`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

### V+ TVI

- API Total: `https://apitotal.net/0112/tv001/vmais-tvi`
- Direct media: 1 manifest/media URL(s) present in the bundle, intentionally omitted.

## Notes

- Channel names, slugs, EPG IDs and player/embed alternatives above come directly from the supplied `index-lJDoyy0G.js` bundle.
- `Sport TV` in the bundle corresponds to `SportTV1.pt` / slug `sport-tv`; channels 2–6 are separate records.
- `Dazn`, `Dazn2` and `Dazn3` still use legacy `ELEVEN1/2/3` identifiers on the `wideiptv.top` player URLs.
- Some free channels have a direct media manifest as one of the frontend choices. Those media URLs are not reproduced here; any alternative page/embed URL is retained when present.
- This is a source-code snapshot, not a guarantee that any third-party player URL remains online or authorized.
