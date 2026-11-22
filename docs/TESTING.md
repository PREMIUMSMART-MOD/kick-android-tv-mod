# Validation — Android TV patcher

## Patcher 0.1.1 with Kick 1.12.0 (211)

On 22 November 2026 the DEBUG patcher reconstructed, signed and verified the APK on a physical Canal+ Skyworth Android 10 box in 27.1 seconds. The instrumented test returned success. An independent comparison of the APK retrieved from the box confirmed all 926 unsigned entries matched the tested mod byte for byte, and desktop apksigner verified its v2 signature. The release patcher certificate matched the installed 0.1.0 release. This run exercised local patching and signing while preserving the existing Kick installation.

## Patcher 0.1.0 with Kick 1.11.0 (210)

Checks were performed on 17–18 November 2026. The physical device was a Canal+ Skyworth Android 10 box, ARM32, 2 GB RAM. This is a native Android application.

## Completed checks

| Check | Result and scope |
| --- | --- |
| Supported input | All four modules of Kick Android TV 1.11.0 (210), dual ARM, 320dpi, matched pinned SHA-256 hashes. |
| Reconstruction | All 926 unsigned output entries matched the locally tested 1.11.0 mod byte for byte. |
| Decoder | Java reconstruction passed. Out-of-range copy, oversized output, truncated input, trailing data and cancellation were rejected. Three synthetic Python delta tests passed. |
| Runtime | Reconstruction and AndroidKeyStore signing passed on an Android 13 x86_64 emulator and the physical Android 10 box. |
| Physical TV flow | Choosing a local input, patching, signing and verifying completed through the native UI in approximately 18 seconds. An earlier instrumented run took 25,435 ms. These are individual timings, not benchmarks. |
| Output export | Save a copy wrote a 23,859,625-byte APK to Download/KickPatcher through MediaStore on the physical TV. All 926 entry hashes matched; desktop apksigner accepted v2 and zipalign -c -P 16 4 passed. |
| System installation | A separate code-free probe exercised the production Installer and InstallReceiver path. Initial installation and same-key replacement were approved through Android's actual system dialogs. The Kick MOD package was not replaced. |
| Key conflict | The physical TV showed the signing-key conflict dialog for the existing different-key mod and stopped before installation. |
| File picker fallback | Vendor DocumentsStub components were excluded. Local Downloads input and Android 10 output export were checked on the box. Android 11+ input still requires a real document provider or a file-manager URI. |
| Download button | The physical TV browser received a VIEW intent containing the supported APKMirror variant URL. Browser download completion and browser checks are outside the patcher's control. |
| Navigation | Native DPAD navigation, stage changes, Back, cancellation and post-export focus were checked. Final patcher UI captures use physical display 0; no virtual display is left running. |
| Vendor signing metadata | GET_SIGNING_CERTIFICATES returned null signingInfo on Android 10. The legacy GET_SIGNATURES fallback was verified on the box. |
| Resource observation | An instrumented end-of-run sample reported 20,859 KB PSS. This is not a measured peak. The configured Java heap limit was 256 MB. |
| Refined UI | Glass focus, larger descriptions and distinct source/profile/output panels were checked on physical display 0. Full local patching completed in 21 seconds; all 926 output entries and the APK signature passed independent checks. |
| Update validation | 16 Android checks passed: a valid signed future-build fixture was accepted, while source URL, version, size, checksum, signing-key, package, downgrade/same-version and damaged-signature cases were rejected. |
| Self-update and key continuity | The DEBUG patcher updated from version code 2 to 3 through Android's actual installation dialog. Its private verified output survived. A new signing operation after that update produced the same certificate as the pre-update output. The user's Kick MOD still had its original hash. |
| Public update feed | Android fetched the published 0.1.0 metadata and downloaded all 6,531,044 bytes of the actual release APK from GitHub. SHA-256, package/version and publisher signature verified against the previously installed release. The production release's Updates button then showed that the installed patcher was up to date. |
| Profile changes | A legacy unmarked private output was not offered as a current result. Repatching with the fingerprint guard completed in 25 seconds, wrote the profile marker and reproduced all 926 verified entries. |

The published patcher is version 0.1.0, version code 2. The release SHA-256 is `e679bc50d04c6d3024bccf96975e0166cba256893cb80149d5e1100269e27c76`. Future downloaded APKs still require Android installation approval; no future Kick port is implied by these updater checks.

## Existing app and limits

The user's installed `com.kick.mobile.mod` was not updated, uninstalled or cleared. Its installed base APK matched the original local release SHA-256 `be63f9c9c1ea467b240b072c94fa375b5018a029dfb34e2bd56eab8a87eec14b`. The separate Kick DEBUG app was used for screenshots; its interface language was restored to Polish afterwards.

The freshly signed output was not installed over that existing different-key mod. Matching all entry hashes verifies reconstruction, not a fresh playback or real-raid test of that signed output. Android's installation path was exercised with the separate probe instead.

Only the specified 1.11.0 input profile is supported. Key import/export is not implemented; clearing patcher data, uninstalling it or resetting the TV loses the locally generated output signing key. MultiView keeps the source mod's own limits. A Home featured preview can still display its mature-content warning; player gate changes do not remove every warning in every entry point.

## Repeating maintainer checks

```text
python -m unittest discover -s tests -p test_*.py
python tests/verify_profile.py --apkm ORIGINAL.apkm --tested-mod LOCALLY_TESTED_MOD.apk
python maintainer/build.py --debug
python maintainer/build_tests.py
```

The instrumented test targets `eu.kickmod.patcher.debug`. Copy the supported original as `source.apkm` and `dist/fixture-unsigned.apk` to its external-files directory, install the DEBUG and test APKs with the same development key, then run:

```text
adb -s DEVICE shell am instrument -w eu.kickmod.patcher.test/eu.kickmod.patcher.TestInstrumentation
```

Only instrumentation result code `-1` means success. Retrieve and independently verify the output. `build_tests.py` also creates a different-key conflict fixture using the Kick MOD package ID: use that fixture only on an isolated emulator with no real Kick MOD installed.
