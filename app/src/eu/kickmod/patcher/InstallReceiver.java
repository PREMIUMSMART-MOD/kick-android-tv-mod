package eu.kickmod.patcher;

import android.content.*;
import android.content.pm.PackageInstaller;

public final class InstallReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                try { context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return; }
                catch (RuntimeException error) { android.util.Log.e("KickPatcher", "Cannot show system installer", error); }
            }
        }
        String message = status == PackageInstaller.STATUS_SUCCESS ? "Installation completed" :
            "Installation not completed: " + intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        context.getSharedPreferences("install", Context.MODE_PRIVATE).edit().putString("result", message).apply();
        Intent returnToApp = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (returnToApp != null) context.startActivity(returnToApp.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }
}
