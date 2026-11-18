"""Pilot i odczyt widocznych etykiet na autoryzowanym urzadzeniu testowym."""
from __future__ import annotations
import argparse
import os
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ADB = os.environ.get("ADB") or shutil.which("adb") or "adb"
DEVICE = os.environ.get("ANDROID_SERIAL", "")


def adb(*args: str) -> bytes:
    serial = ["-s", DEVICE] if DEVICE else []
    return subprocess.check_output([ADB, *serial, *args], timeout=60)


def dump() -> list[dict[str, str]]:
    for attempt in range(3):
        adb("shell", "rm", "-f", "/sdcard/kick-patcher-ui.xml")
        result = adb("shell", "uiautomator", "dump", "/sdcard/kick-patcher-ui.xml")
        if b"dumped" in result:
            raw = adb("exec-out", "cat", "/sdcard/kick-patcher-ui.xml")
            nodes = [node.attrib for node in ET.fromstring(raw).iter("node")]
            if nodes:
                return nodes
        time.sleep(0.5)
    raise RuntimeError("Brak swiezego drzewa UI; nie uzywam poprzedniego zrzutu.")


def main() -> None:
    global DEVICE
    import sys
    sys.stdout.reconfigure(encoding="utf-8")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", default=DEVICE, help="Numer seryjny ADB lub ANDROID_SERIAL")
    parser.add_argument("--display")
    parser.add_argument("--keys", nargs="*")
    parser.add_argument("--filter", default=".*")
    args = parser.parse_args()
    DEVICE = args.serial
    if args.keys:
        prefix = ["shell", "input"] + (["-d", args.display] if args.display else [])
        for key in args.keys:
            adb(*prefix, "keyevent", key)
    for node in dump():
        value = node.get("text") or node.get("content-desc") or ""
        if (value and re.search(args.filter, value, re.I)) or node.get("focused") == "true":
            print({k: node.get(k) for k in ("text", "content-desc", "focused", "checked", "bounds")})


if __name__ == "__main__":
    main()
