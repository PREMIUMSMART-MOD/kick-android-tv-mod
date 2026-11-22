"""Budowanie aplikacji patchera. Uzytkownik patchuje Kicka na TV, nie tym skryptem."""
from __future__ import annotations

import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
APKSIG_SHA = "c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89"
APKSIG_URL = "https://dl.google.com/dl/android/maven2/com/android/tools/build/apksig/8.7.2/apksig-8.7.2.jar"


def run(command: list[str], env: dict[str, str] | None = None) -> None:
    subprocess.run(command, check=True, env=env, timeout=300)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk")))
    parser.add_argument("--jdk", type=Path, default=Path(os.environ.get("JAVA_HOME", "C:/Program Files/Android/Android Studio/jbr")))
    parser.add_argument("--debug", action="store_true")
    parser.add_argument("--signing-keystore", type=Path)
    parser.add_argument("--signing-alias", default="patcher")
    parser.add_argument("--version-code", type=int, default=3)
    parser.add_argument("--version-name", default="0.1.1")
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    if args.version_code < 1:
        raise SystemExit("Numer wersji musi byc dodatni.")
    if not args.debug and not args.signing_keystore:
        raise SystemExit("Wydanie wymaga wlasnego --signing-keystore. Do testow uzyj --debug.")
    if not (ROOT / "app/assets/profile/profile.json").is_file():
        raise SystemExit("Najpierw przygotuj zweryfikowany profil (maintainer/prepare_profile.py).")
    scratch = Path(tempfile.gettempdir()) / "codex-scratch"
    scratch.mkdir(exist_ok=True)
    build = Path(tempfile.mkdtemp(prefix="kick-tv-patcher-build-", dir=scratch))
    print(f"Katalog budowania: {build}", flush=True)
    local = ROOT / ".local"
    local.mkdir(exist_ok=True)
    dependency = local / "apksig-8.7.2.jar"
    if not dependency.exists():
        cache = Path.home() / ".gradle/caches/modules-2/files-2.1/com.android.tools.build/apksig/8.7.2"
        found = list(cache.glob("*/*.jar"))
        if found:
            shutil.copyfile(found[0], dependency)
        else:
            with urllib.request.urlopen(APKSIG_URL, timeout=60) as response:
                dependency.write_bytes(response.read(2 * 1024 * 1024))
    if hashlib.sha256(dependency.read_bytes()).hexdigest() != APKSIG_SHA:
        raise SystemExit("Nieprawidlowy hash biblioteki apksig.")
    suffix = ".exe" if os.name == "nt" else ""
    if not (args.jdk / "bin" / ("javac" + suffix)).exists():
        args.jdk = Path("C:/Program Files/Android/Android Studio/jbr")
    bt = args.sdk / "build-tools/35.0.0"
    android = args.sdk / "platforms/android-35/android.jar"
    java = args.jdk / "bin" / ("java" + suffix)
    javac = args.jdk / "bin" / ("javac" + suffix)
    jar = args.jdk / "bin" / ("jar" + suffix)
    for directory in ("gen", "classes", "dex"):
        (build / directory).mkdir()
    manifest = (ROOT / "app/AndroidManifest.xml").read_text(encoding="utf-8")
    if args.debug:
        manifest = manifest.replace('<application ', '<application android:debuggable="true" ', 1)
        manifest = manifest.replace('package="eu.kickmod.patcher"', 'package="eu.kickmod.patcher.debug"')
        manifest = manifest.replace('android:name=".MainActivity"', 'android:name="eu.kickmod.patcher.MainActivity"')
        manifest = manifest.replace('android:name=".InstallReceiver"', 'android:name="eu.kickmod.patcher.InstallReceiver"')
        manifest = manifest.replace('android:label="Kick Android TV Patcher"', 'android:label="Kick Patcher DEBUG"')
    (build / "AndroidManifest.xml").write_text(manifest, encoding="utf-8")
    run([str(bt / ("aapt2" + suffix)), "compile", "--dir", str(ROOT / "app/res"), "-o", str(build / "res.zip")])
    run([str(bt / ("aapt2" + suffix)), "link", "-o", str(build / "unsigned.apk"), "-I", str(android),
         "--manifest", str(build / "AndroidManifest.xml"), "--java", str(build / "gen"),
         "--min-sdk-version", "26", "--target-sdk-version", "35", "--version-code", str(args.version_code), "--version-name", args.version_name, str(build / "res.zip")])
    sources = sorted((ROOT / "app/src").rglob("*.java")) + sorted((build / "gen").rglob("*.java"))
    source_list = build / "sources.txt"
    source_list.write_text("\n".join('"' + str(path).replace("\\", "/") + '"' for path in sources), encoding="utf-8")
    run([str(javac), "--release", "11", "-encoding", "UTF-8", "-classpath", os.pathsep.join((str(android), str(dependency))),
         "-d", str(build / "classes"), "@" + str(source_list)])
    run([str(jar), "cf", str(build / "classes.jar"), "-C", str(build / "classes"), "."])
    run([str(java), "-Xmx1200m", "-cp", str(bt / "lib/d8.jar"), "com.android.tools.r8.D8", "--release", "--min-api", "26",
         "--lib", str(android), "--output", str(build / "dex"), str(build / "classes.jar"), str(dependency)])
    with zipfile.ZipFile(build / "unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
        for dex in sorted((build / "dex").glob("*.dex")):
            apk.write(dex, dex.name)
        for asset in sorted((ROOT / "app/assets").rglob("*")):
            if asset.is_file():
                apk.write(asset, "assets/" + asset.relative_to(ROOT / "app/assets").as_posix())
    run([str(bt / ("zipalign" + suffix)), "-f", "4", str(build / "unsigned.apk"), str(build / "aligned.apk")])
    keystore = args.signing_keystore or local / "development.keystore"
    signing_env = os.environ.copy()
    if args.signing_keystore:
        if not os.environ.get("PATCHER_STORE_PASSWORD"):
            raise SystemExit("Podaj haslo klucza wydania w PATCHER_STORE_PASSWORD (nie w repozytorium).")
    else:
        signing_env["PATCHER_STORE_PASSWORD"] = "android"
        if not keystore.exists():
            run([str(args.jdk / "bin" / ("keytool" + suffix)), "-genkeypair", "-keystore", str(keystore),
                 "-storepass:env", "PATCHER_STORE_PASSWORD", "-keypass:env", "PATCHER_STORE_PASSWORD", "-alias", "patcher",
                 "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000", "-dname", "CN=Kick Android TV Patcher Development"], signing_env)
    distribution = ROOT / "dist"
    distribution.mkdir(exist_ok=True)
    output = args.out or distribution / ("Kick-Android-TV-Patcher-DEBUG.apk" if args.debug else "Kick-Android-TV-Patcher.apk")
    output.parent.mkdir(parents=True, exist_ok=True)
    signer = [str(java), "-jar", str(bt / "lib/apksigner.jar")]
    run(signer + ["sign", "--ks", str(keystore), "--ks-key-alias", args.signing_alias,
        "--ks-pass", "env:PATCHER_STORE_PASSWORD", "--key-pass", "env:PATCHER_STORE_PASSWORD", "--out", str(output), str(build / "aligned.apk")], signing_env)
    run(signer + ["verify", "--verbose", str(output)])
    print(f"Gotowe: {output}\nSHA256: {hashlib.sha256(output.read_bytes()).hexdigest()}\nWlasne pliki robocze do usuniecia: {build}")


if __name__ == "__main__":
    main()
