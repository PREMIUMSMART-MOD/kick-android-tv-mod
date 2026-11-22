# Patcher updates and new Kick profiles

The public release channel is `PREMIUMSMART-MOD/kick-android-tv-mod`. The app reads `releases/latest/download/update.json` over HTTPS. Only the release app uses this channel; DEBUG does not install public release APKs over itself.

Automatic checks run on opening or resuming the foreground app, with a six-hour success interval and a fifteen-minute failure retry interval. The Updates button checks immediately. Cached available-update information survives process restarts. Connection failure does not block local patching or open an unsolicited dialog.

The user chooses to download an available update. Download progress and cancellation are available in the native UI. Before creating an installation session, the app checks file size, SHA-256, the APK's cryptographic signature, installed-package identity, increasing version code, version name and device minimum-SDK compatibility. The publisher certificate must match the currently installed patcher. HTTP and unexpected redirect hosts are rejected. The checksum is transport integrity information; the matching APK certificate is the trust anchor.

Android asks the user to approve installation. This is not a silent installer. Updates retain patcher data and its AndroidKeyStore key when the same package and publisher certificate are used. Key rotation/import/export is not implemented. Never replace the maintainer's release signing key casually.

## Publishing support for another Kick version

1. Obtain the precise original variant. Port the modifications and test them independently, including playback and device behavior. A new upstream version is not supported merely because it exists.
2. Set the supported version, build and exact APKMirror variant URL in `app/assets/support.json`. Generate the matching profile from the original APKM and the tested mod, then verify it. The generator checks the input package and version against the support metadata. Each profile pins the exact input modules by hash.
3. Increment the patcher version code and name. Build with the same private release keystore. Never commit it or the original/modified Kick APKs.
4. Run relevant regression/device checks, including output entry hashes, signing identity and the update flow. The app binds its private verified output to a fingerprint of the bundled profile. An old or unmarked output is kept on disk but is not presented as a result of the new profile.
5. Generate update metadata from the final signed APK:

```text
python maintainer/build.py --version-code 3 --version-name 0.1.1 --signing-keystore YOUR_PRIVATE_KEYSTORE
python maintainer/prepare_release.py --tag v0.1.1
```

The keystore password is supplied through `PATCHER_STORE_PASSWORD`. Version numbers above are examples. `prepare_release.py` reads the actual APK version and bundled profile, then produces `dist/update.json` and `dist/SHA256SUMS`.

6. Publish a stable GitHub Release containing **Kick-Android-TV-Patcher.apk**, **update.json**, and **SHA256SUMS** together. Stage them in a draft first, and publish only after all assets are uploaded. Do not leave a stable latest release with metadata pointing to a missing or different APK.
7. Download anonymously, verify hashes and metadata, and check the released feed from a TV. Users then receive the tested profile and matching download link through the patcher update.

No original or modified Kick APK is distributed in the release. End users do everything through the Android TV patcher and their TV's browser/file manager.
