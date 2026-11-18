"""Metadane aktualizacji z gotowego podpisanego APK, bez recznego wpisywania hashy."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def prepare(apk: Path, sdk: Path, tag: str) -> dict:
    if not re.fullmatch(r"v\d+(?:\.\d+){1,3}(?:-[A-Za-z0-9.-]+)?", tag):
        raise ValueError("Nieprawidlowy tag wydania.")
    badging = subprocess.check_output([str(sdk / "build-tools/35.0.0/aapt2.exe"), "dump", "badging", str(apk)], text=True, encoding="utf-8")
    found = re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'", badging)
    if not found or found[1] != "eu.kickmod.patcher" or "application-debuggable" in badging:
        raise ValueError("Metadane wydania wymagaja produkcyjnego APK patchera.")
    if tag != "v" + found[3]:
        raise ValueError("Tag nie zgadza sie z wersja aplikacji.")
    with zipfile.ZipFile(apk) as archive:
        support = json.loads(archive.read("assets/support.json"))
        profile = json.loads(archive.read("assets/profile/profile.json"))
    if support["version"] != profile["version"] or support["versionCode"] != profile["versionCode"]:
        raise ValueError("Metadane nie zgadzaja sie z profilem.")
    sha = hashlib.sha256(apk.read_bytes()).hexdigest()
    metadata = {"format": 1, "versionCode": int(found[2]), "versionName": found[3],
                "kickVersion": support["version"], "size": apk.stat().st_size,
                "apkUrl": f"https://github.com/PREMIUMSMART-MOD/kick-android-tv-mod/releases/download/{tag}/Kick-Android-TV-Patcher.apk",
                "sha256": sha}
    destination = apk.parent / "update.json"
    destination.write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
    sums = sha + "  " + apk.name + "\n"
    sums += hashlib.sha256(destination.read_bytes()).hexdigest() + "  update.json\n"
    (apk.parent / "SHA256SUMS").write_text(sums, encoding="ascii")
    return metadata


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, default=ROOT / "dist/Kick-Android-TV-Patcher.apk")
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")))
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    print(json.dumps(prepare(args.apk, args.sdk, args.tag), indent=2))
