package eu.kickmod.patcher;

import android.app.Instrumentation;
import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Uruchamiane tylko przez osobny testowy APK, nie dolaczane do wydania patchera. */
public final class TestInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args) { arguments = args; start(); }
    @Override public void onStart() {
        Bundle report = new Bundle();
        long started = SystemClock.elapsedRealtime();
        try {
            Context context = getTargetContext();
            if ("online-update".equals(arguments.getString("mode"))) {
                UpdateClient.Candidate candidate = UpdateClient.check(new AtomicBoolean());
                Context releaseIdentity = new android.content.ContextWrapper(context) {
                    @Override public String getPackageName() { return "eu.kickmod.patcher"; }
                };
                File downloaded = UpdateClient.download(releaseIdentity, candidate, percent -> {}, new AtomicBoolean());
                report.putString("release", candidate.version);
                report.putLong("versionCode", candidate.code);
                report.putLong("verifiedBytes", downloaded.length());
                if (!downloaded.delete()) throw new IOException("Cannot remove verified update test download.");
                finish(Activity.RESULT_OK, report); return;
            }
            if ("updates".equals(arguments.getString("mode"))) {
                report.putInt("checks", UpdateProbe.run(context));
                finish(Activity.RESULT_OK, report); return;
            }
            if ("install-self-update".equals(arguments.getString("mode"))) {
                File apk = new File(context.getExternalFilesDir(null), "updater-valid.apk");
                UpdateClient.validate(context, apk, UpdateClient.parse(UpdateProbe.metadata(apk, 3)));
                startActivitySync(new android.content.Intent().setClassName(context.getPackageName(), MainActivity.class.getName())
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK));
                Installer.submit(context, apk, context.getPackageName(), new AtomicBoolean());
                SystemClock.sleep(2500);
                report.putString("result", "Submitted verified DEBUG self-update for system approval");
                finish(Activity.RESULT_OK, report); return;
            }
            if ("inspect".equals(arguments.getString("mode"))) {
                File apk = new File(context.getFilesDir(), "output/kick-mod.apk");
                for (int flags : new int[]{0, android.content.pm.PackageManager.GET_SIGNATURES, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES}) {
                    android.content.pm.PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), flags);
                    report.putString("flags" + flags, info == null ? "null" : info.packageName + " legacy=" + (info.signatures == null ? -1 : info.signatures.length) + " signingInfo=" + (info.signingInfo != null));
                }
                finish(Activity.RESULT_OK, report); return;
            }
            if (!"sign-fixture".equals(arguments.getString("mode"))) {
                File input = new File(context.getExternalFilesDir(null), "source.apkm");
                File output = new PatchEngine(context).patch(input, (stage, percent) -> {
                    if (percent % 10 == 0) android.util.Log.i("KickPatcherTest", percent + "% " + stage);
                }, new AtomicBoolean());
                report.putString("output", output.getAbsolutePath());
            }
            File fixture = new File(context.getExternalFilesDir(null), "fixture-unsigned.apk");
            if (fixture.isFile()) {
                LocalSigner.sign(fixture, new File(context.getExternalFilesDir(null), "fixture-signed.apk"));
                LocalSigner.sign(fixture, new File(context.getExternalFilesDir(null), "fixture-update.apk"));
            }
            report.putLong("elapsedMs", SystemClock.elapsedRealtime() - started);
            report.putLong("maxHeapBytes", Runtime.getRuntime().maxMemory());
            android.os.Debug.MemoryInfo memory = new android.os.Debug.MemoryInfo();
            android.os.Debug.getMemoryInfo(memory);
            report.putInt("pssKb", memory.getTotalPss());
            finish(Activity.RESULT_OK, report);
        } catch (Throwable error) {
            android.util.Log.e("KickPatcherTest", "Failure", error);
            report.putString("error", error.toString()); finish(Activity.RESULT_CANCELED, report);
        }
    }
}
