# Android TV patcher 0.1.0

Patch the supported original Kick APKM directly on an Android TV, using your remote. This release supports **Kick Android TV 1.11.0 (210), dual ARM, 320dpi, Android 8.0+ variant**.

## Included

- Native English TV interface with glass focus states and separate Get APKM, Patch and Install screens.
- Direct link to the exact supported APKMirror variant, source-file checks, local patching and AndroidKeyStore signing.
- Automatic patcher update checks on opening/resuming, an Updates button, verified update downloads and Android's installation dialog. Future Kick versions need a new tested profile and patcher release first.
- Live latency controls, automatic raids, audio compressor, category filters, hidden chat and 1080p defaults in the bundled mod profile. See README for the full scope.
- Local APK export and protection against updating an existing different-key Kick MOD.

Download **Kick-Android-TV-Patcher.apk**, not the automatically generated source-code ZIP, to install the patcher. `update.json` is used by its updater, and `SHA256SUMS` contains file checksums. No original or modified Kick APK is distributed here.

## Verified and limited

Tested on a physical Canal+ Android 10 box: native remote navigation, local reconstruction/signing/export, all 926 output entry hashes, signing-key conflict protection and the system installation/upgrade path using separate test apps. The patcher self-update validator passed 16 Android checks; a DEBUG update through Android's dialog kept its data and output signing key.

Keep the patcher and its data to preserve this TV's signing key. A locally signed result cannot update an existing mod signed with another key. The patcher never uninstalls that mod or clears its data. Playback of a freshly signed result over the user's existing different-key mod was intentionally not performed. Detailed evidence and limits are in docs/TESTING.md.
