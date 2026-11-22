"""Przygotowanie profilu aplikacji TV z lokalnego oryginalnego APKM i przetestowanego moda.

To narzedzie maintenera; uzytkownik koncowy uruchamia wylacznie aplikacje Android TV.
Profil zawiera zakresy COPY/LITERAL i wlasne dodatki, nigdy wejsciowe APK.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
from pathlib import Path
import re
import struct
import zipfile
import zlib


SIGNATURE = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.I)
OWN_FILES = {"classes3.dex", "assets/icons/ffz-comp-off.svg", "assets/icons/ffz-comp-on.svg",
             "assets/licenses/Apache-2.0.txt", "assets/licenses/blink-dynamics-compressor.txt",
             "assets/licenses/ffz-icons.txt"}


def sha(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def delta(source: bytes, target: bytes) -> bytes:
    """Indeks blokow 32B. Hash sluzy tylko do wyszukiwania; bajty sa porownywane."""
    width = 32
    index = {zlib.crc32(source[p:p + width]): p for p in range(0, len(source) - width + 1, 8)}
    stream = io.BytesIO()
    stream.write(b"KDP1" + struct.pack(">II", len(source), len(target)))
    position = 0
    literal_start = 0
    while position + width <= len(target):
        block = target[position:position + width]
        original = index.get(zlib.crc32(block))
        if original is None or source[original:original + width] != block:
            position += 1
            continue
        length = width
        limit = min(len(source) - original, len(target) - position)
        while length + 1024 <= limit and source[original + length:original + length + 1024] == target[position + length:position + length + 1024]:
            length += 1024
        while length < limit and source[original + length] == target[position + length]:
            length += 1
        if literal_start < position:
            literal = target[literal_start:position]
            stream.write(b"\x02" + struct.pack(">I", len(literal)) + literal)
        stream.write(b"\x01" + struct.pack(">II", original, length))
        position += length
        literal_start = position
    if literal_start < len(target):
        literal = target[literal_start:]
        stream.write(b"\x02" + struct.pack(">I", len(literal)) + literal)
    stream.write(b"\x00")
    return gzip.compress(stream.getvalue(), compresslevel=9, mtime=0)


def apply_delta(source: bytes, patch: bytes) -> bytes:
    """Niezalezny dekoder kontrolny przed zapisaniem profilu."""
    stream = io.BytesIO(gzip.decompress(patch))
    if stream.read(4) != b"KDP1":
        raise ValueError("Nieprawidlowy naglowek")
    source_size, expected = struct.unpack(">II", stream.read(8))
    if source_size != len(source):
        raise ValueError("Zly rozmiar zrodla")
    result = bytearray()
    while True:
        opcode = stream.read(1)
        if opcode == b"\0":
            break
        if opcode == b"\1":
            offset, length = struct.unpack(">II", stream.read(8))
            result.extend(source[offset:offset + length])
        elif opcode == b"\2":
            length, = struct.unpack(">I", stream.read(4))
            result.extend(stream.read(length))
        else:
            raise ValueError("Nieprawidlowy opcode")
    if len(result) != expected or stream.read():
        raise ValueError("Nieprawidlowy rozmiar wyniku")
    return bytes(result)


def prepare(apkm: Path, mod: Path, output: Path) -> None:
    output.mkdir(parents=True, exist_ok=True)
    modules: list[dict] = []
    originals: dict[str, zipfile.ZipFile] = {}
    exact: dict[str, tuple[str, str]] = {}
    with zipfile.ZipFile(apkm) as wrapper:
        support = json.loads((Path(__file__).resolve().parents[1] / "app/assets/support.json").read_text(encoding="utf-8"))
        source_info = json.loads(wrapper.read("info.json"))
        if (source_info["pname"], source_info["release_version"], int(source_info["versioncode"])) != (
                "com.kick.mobile", support["version"], support["versionCode"]):
            raise ValueError("Oryginal nie pasuje do wersji w support.json")
        for name in wrapper.namelist():
            if not name.endswith(".apk"):
                continue
            if "/" in name or "\\" in name:
                raise ValueError("Niestandardowa struktura APKM")
            data = wrapper.read(name)
            modules.append({"name": name, "sha256": sha(data), "size": len(data)})
            archive = zipfile.ZipFile(io.BytesIO(data))
            originals[name] = archive
            for item in archive.infolist():
                if not item.is_dir() and not SIGNATURE.fullmatch(item.filename):
                    exact.setdefault(sha(archive.read(item)), (name, item.filename))
    entries = []
    with zipfile.ZipFile(mod) as patched:
        for item in patched.infolist():
            name = item.filename
            if item.is_dir() or SIGNATURE.fullmatch(name) or name in {"stamp-cert-sha256"}:
                continue
            data = patched.read(item)
            digest = sha(data)
            record = {"name": name, "sha256": digest, "size": len(data),
                      "stored": name.endswith(".so") or name == "resources.arsc" or item.compress_type == 0}
            if digest in exact:
                module, source_name = exact[digest]
                record.update(mode="copy", module=module, source=source_name)
            elif name in originals["base.apk"].namelist():
                source = originals["base.apk"].read(name)
                patch = delta(source, data)
                if apply_delta(source, patch) != data:
                    raise ValueError(f"Blad odtworzenia {name}")
                asset = f"delta-{len(entries):04d}.kdp.gz"
                (output / asset).write_bytes(patch)
                record.update(mode="delta", module="base.apk", source=name, asset=asset,
                              sourceSha256=sha(source), patchSha256=sha(patch))
                print(f"Delta {name}: {len(source)} -> {len(data)}, profil {len(patch)} B", flush=True)
            elif name in OWN_FILES:
                asset = f"own-{len(entries):04d}.gz"
                payload = gzip.compress(data, compresslevel=9, mtime=0)
                (output / asset).write_bytes(payload)
                record.update(mode="own", asset=asset, patchSha256=sha(payload))
            else:
                raise ValueError(f"Brak zrodla; nie dolaczam obcego pliku w calosci: {name}")
            entries.append(record)
    profile = {"format": 1, "version": support["version"], "versionCode": support["versionCode"],
               "package": "com.kick.mobile.mod", "modules": modules, "entries": entries}
    (output / "profile.json").write_text(json.dumps(profile, indent=2) + "\n", encoding="utf-8")
    print(f"Gotowe: {len(entries)} wpisow, {len(modules)} oryginalne moduly, {output}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apkm", type=Path, required=True)
    parser.add_argument("--tested-mod", type=Path, required=True)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    prepare(args.apkm, args.tested_mod, args.out)
