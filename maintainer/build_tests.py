"""Buduje osobny APK instrumentalny i bezkodowy fixture podpisu; tylko dla maintenera."""
from __future__ import annotations
import os
from pathlib import Path
import tempfile
import zipfile
from build import ROOT, run


def main() -> None:
    sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))
    jdk = Path("C:/Program Files/Android/Android Studio/jbr/bin")
    bt = sdk / "build-tools/35.0.0"
    android = sdk / "platforms/android-35/android.jar"
    work = Path(tempfile.mkdtemp(prefix="kick-patcher-instrument-", dir=Path(tempfile.gettempdir()) / "codex-scratch"))
    (work / "classes").mkdir()
    (work / "dex").mkdir()
    compiled_target = list((Path(tempfile.gettempdir()) / "codex-scratch").glob("kick-tv-patcher-build-*/classes"))
    compiled_target.sort(key=lambda p: p.stat().st_mtime)
    manifest = work / "AndroidManifest.xml"
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="eu.kickmod.patcher.test">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/>
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
<application android:label="Kick Patcher Tests" android:allowBackup="false">
<activity android:name="eu.kickmod.patcher.ProbeActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/><category android:name="android.intent.category.LEANBACK_LAUNCHER"/></intent-filter></activity>
<receiver android:name="eu.kickmod.patcher.InstallReceiver" android:exported="false"/>
</application>
<instrumentation android:name="eu.kickmod.patcher.TestInstrumentation" android:targetPackage="eu.kickmod.patcher.debug" android:functionalTest="true"/>
</manifest>''', encoding="utf-8")
    run([str(bt / "aapt2.exe"), "link", "-o", str(work / "tests-unsigned.apk"), "-I", str(android), "--manifest", str(manifest)])
    run([str(jdk / "javac.exe"), "--release", "11", "-encoding", "UTF-8", "-classpath", os.pathsep.join((str(android), str(compiled_target[-1]))),
        "-d", str(work / "classes"), str(ROOT / "tests/TestInstrumentation.java"), str(ROOT / "tests/ProbeActivity.java"), str(ROOT / "tests/UpdateProbe.java"),
        str(ROOT / "app/src/eu/kickmod/patcher/Installer.java"), str(ROOT / "app/src/eu/kickmod/patcher/InstallReceiver.java")])
    run([str(jdk / "jar.exe"), "cf", str(work / "tests.jar"), "-C", str(work / "classes"), "."])
    run([str(jdk / "java.exe"), "-cp", str(bt / "lib/d8.jar"), "com.android.tools.r8.D8", "--release", "--min-api", "26", "--lib", str(android),
        "--classpath", str(compiled_target[-1]), "--output", str(work / "dex"), str(work / "tests.jar")])
    with zipfile.ZipFile(work / "tests-unsigned.apk", "a", zipfile.ZIP_DEFLATED) as apk:
        apk.write(work / "dex/classes.dex", "classes.dex")
    out = ROOT / "dist/Kick-Patcher-Tests.apk"
    run([str(jdk / "java.exe"), "-jar", str(bt / "lib/apksigner.jar"), "sign", "--ks", str(ROOT / ".local/development.keystore"),
        "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", str(out), str(work / "tests-unsigned.apk")])
    manifest.write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="eu.kickmod.patcher.probe" android:versionCode="1" android:versionName="1">
<uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="Kick Patcher Signing Probe" android:hasCode="false" android:allowBackup="false"/></manifest>''', encoding="utf-8")
    run([str(bt / "aapt2.exe"), "link", "-o", str(ROOT / "dist/fixture-unsigned.apk"), "-I", str(android), "--manifest", str(manifest)])
    manifest.write_text(manifest.read_text(encoding="utf-8").replace("eu.kickmod.patcher.probe", "com.kick.mobile.mod"), encoding="utf-8")
    run([str(bt / "aapt2.exe"), "link", "-o", str(work / "conflict-unsigned.apk"), "-I", str(android), "--manifest", str(manifest)])
    run([str(jdk / "java.exe"), "-jar", str(bt / "lib/apksigner.jar"), "sign", "--ks", str(ROOT / ".local/development.keystore"),
        "--ks-pass", "pass:android", "--key-pass", "pass:android", "--out", str(ROOT / "dist/conflict-fixture.apk"), str(work / "conflict-unsigned.apk")])
    print(f"Testowy APK: {out}\nPliki robocze: {work}")


if __name__ == "__main__":
    main()
