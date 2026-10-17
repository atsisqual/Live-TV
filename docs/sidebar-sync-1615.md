# Sidebar active-channel synchronization

v1.6.15 keeps the native sidebar highlight aligned with the channel the WebView has actually started loading instead of immediately trusting the last tapped row.

It also treats a repeated tap on the same sidebar row as an explicit retry, so a failed navigation does not leave the row permanently blocked by stale selected-channel state.

This release does not change provider routing. Canal 11 remains the only channel managed by the direct-provider registry introduced in v1.6.14.
