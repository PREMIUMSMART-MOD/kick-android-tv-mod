package eu.kickmod.patcher;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import java.io.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Ten sam mechanizm instalacji jest uzywany w aplikacji i osobnym probniku testowym. */
public final class Installer {
    private Installer() {}

    public static void submit(Context context, File apk, String packageName, AtomicBoolean cancelled) throws Exception {
        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        int id = -1;
        try {
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(packageName); params.setSize(apk.length());
            if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED);
            id = installer.createSession(params);
            try (PackageInstaller.Session session = installer.openSession(id)) {
                try (InputStream in = new FileInputStream(apk); OutputStream out = session.openWrite("base.apk", 0, apk.length())) {
                    byte[] buffer = new byte[65536]; long total = 0; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new InterruptedIOException("Cancelled.");
                        total += count;
                        if (total > 300L * 1024 * 1024) throw new IOException("APK exceeds size limit.");
                        out.write(buffer, 0, count);
                    }
                    session.fsync(out);
                }
                int flags = PendingIntent.FLAG_UPDATE_CURRENT;
                if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
                Intent callback = new Intent(context, InstallReceiver.class).setAction(context.getPackageName() + ".INSTALL_RESULT");
                session.commit(PendingIntent.getBroadcast(context, id, callback, flags).getIntentSender());
            }
        } catch (Exception error) {
            if (id >= 0) try { installer.abandonSession(id); } catch (Exception ignored) { }
            throw error;
        }
    }
}
