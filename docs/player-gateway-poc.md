# Player gateway proof of concept

The Android app includes a local `player-gateway.html` proof of concept for Canal 11.

The gateway owns the Player 1/2/3 selector and embeds the existing HTTP/HTTPS provider rather than extracting, proxying, or re-hosting the media stream. This keeps provider routing replaceable while avoiding any attempt to bypass DRM, anti-bot checks, or access controls.

For v1.6.13 only Canal 11 is routed through the gateway. Other channels keep their existing behavior.

The gateway shows a slow-load prompt after 15 seconds and allows the user to switch player manually. `auto=1` exists for experiments, but is not enabled by the Canal 11 route because cross-origin embeds cannot reliably expose actual playback readiness to the parent page.
