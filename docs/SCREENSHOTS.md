# Screenshot provenance

The gallery shows actual applications, with presentation frames and English captions, not UI mockups. The supported Kick version is Android TV 1.11.0 (210).

- **patcher.png:** release patcher, captured on 18 November 2026 from physical display 0 of the Canal+ Skyworth Android 10 box through a passive scrcpy 5.0.1 recording. The remote remained available. This is a UI capture, not proof of installation.
- **settings.png:** real English-language Kick DEBUG preferences, captured on 17 November 2026 from physical display 0 of the same box. The account sidebar was cropped out. Pools and Just Sleeping are off, ASMR and the audio compressor are on, and the selected target is 3 seconds. These are captured preferences, not asserted defaults.
- **player.png:** real Kick DEBUG player captured on 17 November 2026 on the same box through an earlier scrcpy virtual-display recording. The scene and control strip are separate crops of one frame. The measured live offset is 4.83 seconds, separate from the configured target. Stream titles and creator overlays retain their original language. No in-app text, switch state or numeric reading was translated or retouched.
- **banner.png:** project artwork, not a screenshot. Icon and launcher-banner SVGs in artwork/ are artwork as well.

No account-containing raw screen or recording is included. The earlier virtual display was closed. Subsequent device work and final patcher screenshots used physical display 0 only. Kick DEBUG was returned to Polish after the English preferences capture.

`maintainer/prepare_gallery.py` produces presentation HTML from private PNG inputs. Render with automatic browser darkening disabled, check crops for account information, and publish only the resulting images. The preference input must already be cropped to 1080 by 830 pixels.
