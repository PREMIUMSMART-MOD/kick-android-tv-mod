"""Oprawa prawdziwych zrzutow w stylu zaakceptowanego banera.

Nie zmienia tekstu interfejsu ani stanow kontrolek. Wejscie ustawien musi byc
juz wyciete do panelu bez konta (1080x830). HTML zapisuj w prywatnym TEMP.
"""
from __future__ import annotations
import argparse
import base64
import html
from pathlib import Path
import struct


def encoded(path: Path) -> str:
    data = path.read_bytes()
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("Wymagany plik PNG")
    return "data:image/png;base64," + base64.b64encode(data).decode("ascii")


def image(path: Path, alt: str) -> str:
    return f'<img src="{encoded(path)}" alt="{html.escape(alt)}">'


def card(identifier: str, section: str, title: str, subtitle: str, picture: str, notes: list[tuple[str, str]], footer: str) -> str:
    blocks = "".join(f'<div><b>{html.escape(label)}</b><span>{html.escape(value)}</span></div>' for label, value in notes)
    return f'''<article id="{identifier}"><header><span class="brand">KICK <i>ANDROID TV MOD</i></span><span class="section">{section}</span></header>
<div class="rule"></div><h1>{title}</h1><p class="intro">{subtitle}</p><figure class="{identifier}">{picture}</figure>
<div class="notes">{blocks}</div><footer>{footer}</footer></article>'''


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--player", type=Path)
    parser.add_argument("--settings", type=Path)
    parser.add_argument("--patcher", type=Path)
    parser.add_argument("--patcher-device", default="Android emulator · Development build")
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    cards = []
    if args.player:
        source = encoded(args.player)
        player_view = f'<div class="scene"><img src="{source}" alt="Actual player scene, upper crop"></div><div class="control-label">PLAYER CONTROLS / ORIGINAL PIXELS</div><div class="controlbar"><img src="{source}" alt="Actual player control bar, tooltip area outside crop"></div>'
        cards.append(card("player", "PLAYER / 1.11.0", 'A clear view.<br><em>A little more control.</em>',
            "Measured live offset and an audio compressor, inside the familiar player.",
            player_view,
            [("Live offset", "The current measurement, next to the stream timer."),
             ("Audio compressor", "A separate button in the player toolbar."),
             ("1080p by default", "When this quality is available on the stream.")],
            "Actual Kick DEBUG 1.11.0 capture · Canal+ Android TV box<br>Scene and control bar are separate crops from one frame. Measured latency is an example, not a guaranteed delay."))
    if args.settings:
        dimensions = struct.unpack(">II", args.settings.read_bytes()[16:24])
        if dimensions != (1080, 830):
            raise ValueError("Preferencje musza byc kadrem 1080x830 bez danych konta.")
        cards.append(card("settings", "PREFERENCES / 1.11.0", 'Your preferences.<br><em>Separate controls.</em>',
            "Category filters, audio compression and target latency in the native TV settings.",
            image(args.settings, "Actual English-language preferences, account sidebar excluded"),
            [("Category filters", "Just Sleeping and ASMR have their own controls."),
             ("Audio compressor", "The same setting as the player button."),
             ("3 / 5 / 8 seconds", "Target latency for the next stream.")],
            "Actual Kick DEBUG 1.11.0 capture · Canal+ Android TV box · English interface<br>Account sidebar cropped out. Shown switch states are the captured preferences."))
    if args.patcher:
        cards.append(card("patcher", "LOCAL PATCHER", 'Made for the TV.<br><em>Ready for your remote.</em>',
            "Choose the supported original APKM. The patcher handles the files locally.",
            image(args.patcher, "Actual Android patcher application"),
            [("The right version", "A direct link to the supported APKMirror variant."),
             ("Local processing", "Verify, patch and sign on the Android device."),
             ("Your approval", "Installation goes through Android's system dialog.")],
            html.escape(args.patcher_device) + "<br>Actual application capture. This image does not demonstrate an installation result."))
    if not cards:
        raise ValueError("Podaj co najmniej jeden zrzut.")
    root = Path(__file__).resolve().parents[1]
    fonts = ''.join('@font-face{font-family:' + name + ';src:url(data:font/ttf;base64,' + base64.b64encode((root / 'app/assets/fonts' / (name + '.ttf')).read_bytes()).decode('ascii') + ')}' for name in ('Sora', 'Onest'))
    args.out.write_text(HEAD.replace('FONT_RULES', fonts) + "\n".join(cards) + "</body></html>", encoding="utf-8")


HEAD = '''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="color-scheme" content="dark">
<title>Kick Android TV MOD — actual app captures</title><style>
FONT_RULES
*{box-sizing:border-box}html,body{margin:0;background:#090d0b;color:#f0f3f0;font-family:Onest,sans-serif;color-scheme:dark}
article{width:1600px;padding:54px 70px 40px;margin-bottom:32px;background:radial-gradient(ellipse at 100% 0%,#17271a88,transparent 50%),#0b100d}
header{display:flex;align-items:center;justify-content:space-between;gap:30px}.brand{font-size:27px;font-weight:800;letter-spacing:.5px;color:#53fc18}.brand i{color:#e9eeea;font-size:24px;font-style:normal;font-weight:500;margin-left:10px}.section{font-size:14px;letter-spacing:2px;color:#a0afa4}
.rule{width:96px;height:4px;background:#53fc18;border-radius:3px;margin:25px 0 30px}h1{font-family:Sora,sans-serif;font-size:44px;line-height:1.2;letter-spacing:-1.5px;margin:0 0 18px;font-weight:500}h1 em{font-style:normal;color:#c2cfc6;font-weight:400}.intro{font-size:23px;color:#a9b8ae;margin:0 0 34px;line-height:1.45}
figure{margin:0;border:1px solid #36453b;border-radius:18px;overflow:hidden;background:#000;box-shadow:0 18px 48px #0005}figure>img{display:block;width:100%;height:auto}figure.settings{width:1130px;margin:auto}figure.settings>img{width:1130px}
.scene{position:relative;height:640px;overflow:hidden}.scene img{width:1460px;height:auto;display:block}.control-label{padding:20px 30px 12px;font-size:13px;letter-spacing:2px;color:#94ac99}.controlbar{position:relative;height:80px;overflow:hidden;margin:0 0 16px}.controlbar img{position:absolute;width:1460px;height:815.16px;left:0;top:-696px;max-width:none}
.notes{display:flex;gap:18px;margin-top:27px}.notes>div{flex:1;min-width:0;border:1px solid #2d3b32;border-radius:14px;padding:21px;background:linear-gradient(135deg,#18221b,#101812)}.notes b{display:block;font-size:23px;color:#c5f8b4;font-weight:500;margin-bottom:9px}.notes span{font-size:19px;color:#aabbb0;line-height:1.4}footer{font-size:16px;line-height:1.65;color:#819488;margin-top:26px}
</style><body>'''


if __name__ == "__main__":
    main()
