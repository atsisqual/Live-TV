# Android release signing

The Android workflow supports a persistent release key without committing private signing material to this public repository.

## One-time key creation

Create the key on a trusted local machine and keep the `.jks` file backed up securely:

```bash
keytool -genkeypair -v \
  -keystore livetv-release.jks \
  -alias livetv \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000
```

Base64-encode the keystore as a single line:

Linux:

```bash
base64 -w 0 livetv-release.jks
```

macOS:

```bash
base64 livetv-release.jks | tr -d '\n'
```

PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('livetv-release.jks'))
```

## GitHub Actions secrets

Add these repository secrets under **Settings → Secrets and variables → Actions**:

- `ANDROID_SIGNING_KEY_BASE64` — base64 content of `livetv-release.jks`
- `ANDROID_KEYSTORE_PASSWORD` — keystore password
- `ANDROID_KEY_ALIAS` — `livetv` (or the alias chosen above)
- `ANDROID_KEY_PASSWORD` — key password

When all four secrets exist, `.github/workflows/android-apk.yml` automatically builds and publishes a signed release APK. If any secret is missing, CI deliberately falls back to a debug APK so development builds keep working.

## Important

Do not delete or rotate the release key after users install a release signed with it. Android only accepts an in-place update when the new APK is signed by the same signing identity.

Release builds from v1.5.9 onward also include per-channel API Total player selection through the optional JSON `player` field.
