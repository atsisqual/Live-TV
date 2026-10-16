# Live TV Android

Android WebView client for official/public TV pages and user-supplied channel lists.

## My TV List

Tap **TV List** in the app to:

- add one channel by name + URL;
- paste/import an M3U list;
- paste simple lines in `Name | https://url` format;
- manage or clear user-added channels.

User-added channels are stored locally in Android `SharedPreferences` and are never uploaded by the app.

Long-press a custom channel button to remove that channel.

The app does not scrape or discover streams. It opens URLs supplied by the bundled official list or by the user.
