"""Test odtworzenia kazdego wpisu APK i dekodera Java (takiego samego jak na TV)."""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[1]


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apkm", type=Path, required=True)
    parser.add_argument("--tested-mod", type=Path, required=True)
    args = parser.parse_args()
    assets = ROOT / "app/assets/profile"
    profile = json.loads((assets / "profile.json").read_text(encoding="utf-8"))
    jdk = Path("C:/Program Files/Android/Android Studio/jbr/bin")
    scratch = Path(tempfile.gettempdir()) / "codex-scratch"
    scratch.mkdir(exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="kick-profile-test-", dir=scratch))
    subprocess.run([str(jdk / "javac.exe"), "--release", "11", "-d", str(work),
        str(ROOT / "app/src/eu/kickmod/patcher/Delta.java"), str(ROOT / "tests/DeltaProbe.java")], check=True)
    java = [str(jdk / "java.exe"), "-cp", str(work), "DeltaProbe"]
    originals = {}
    count = 0
    with zipfile.ZipFile(args.apkm) as wrapper, zipfile.ZipFile(args.tested_mod) as target:
        for module in profile["modules"]:
            data = wrapper.read(module["name"])
            assert digest(data) == module["sha256"]
            originals[module["name"]] = zipfile.ZipFile(io.BytesIO(data))
        for entry in profile["entries"]:
            if entry["mode"] == "own":
                result = gzip.decompress((assets / entry["asset"]).read_bytes())
            else:
                source = originals[entry["module"]].read(entry["source"])
                if entry["mode"] == "copy":
                    result = source
                else:
                    original = work / "source.bin"
                    output = work / "output.bin"
                    original.write_bytes(source)
                    subprocess.run(java + [str(original), str(assets / entry["asset"]), str(output), "run", str(entry["size"])], check=True)
                    result = output.read_bytes()
            assert len(result) == entry["size"] and digest(result) == entry["sha256"], entry["name"]
            assert result == target.read(entry["name"]), entry["name"]
            count += 1
    source = work / "small.bin"
    source.write_bytes(b"abcdefgh")
    invalid = {
        "outside-source": b"KDP1" + struct.pack(">II", 8, 2) + b"\1" + struct.pack(">II", 7, 2) + b"\0",
        "oversized-output": b"KDP1" + struct.pack(">II", 8, 2) + b"\2" + struct.pack(">I", 3) + b"abc\0",
        "truncated": b"KDP1" + struct.pack(">II", 8, 2) + b"\2" + struct.pack(">I", 2) + b"a",
        "trailing": b"KDP1" + struct.pack(">II", 8, 2) + b"\2" + struct.pack(">I", 2) + b"ab\0x",
    }
    for label, plain in invalid.items():
        patch = work / (label + ".gz")
        patch.write_bytes(gzip.compress(plain))
        proc = subprocess.run(java + [str(source), str(patch), str(work / "bad.bin"), "run", "2"], capture_output=True)
        assert proc.returncode != 0, label
    patch = work / "valid.gz"
    patch.write_bytes(gzip.compress(b"KDP1" + struct.pack(">II", 8, 2) + b"\1" + struct.pack(">II", 1, 2) + b"\0"))
    proc = subprocess.run(java + [str(source), str(patch), str(work / "cancel.bin"), "cancel", "2"], capture_output=True)
    assert proc.returncode != 0 and b"Cancelled" in proc.stderr
    print(f"Potwierdzono {count} wpisow zgodnych bajt w bajt z przetestowanym modem.")
    print("Dekoder Java odrzucil 4 uszkodzone instrukcje oraz anulowana operacje.")
    print(f"Pliki robocze testu do usuniecia: {work}")


if __name__ == "__main__":
    main()
