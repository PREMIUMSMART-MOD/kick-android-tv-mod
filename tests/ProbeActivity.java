package eu.kickmod.patcher;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/** Osobna aplikacja testowa; nigdy nie trafia do APK patchera. */
public final class ProbeActivity extends Activity {
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(80,80,80,80);
        layout.setBackgroundColor(0xff0d1110);
        status = new TextView(this); status.setTextColor(0xffedf2ee); status.setTextSize(26);
        status.setText("Test systemowego instalatora — osobna aplikacja bez kodu Kicka"); layout.addView(status);
        Button install = new Button(this); install.setText("Zainstaluj / zaktualizuj probnik"); install.setFocusableInTouchMode(true);
        layout.addView(install); install.setOnClickListener(v -> {
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))); return;
            }
            new Thread(() -> {
                try {
                    Installer.submit(this, new File(getExternalFilesDir(null), "probe.apk"), "eu.kickmod.patcher.probe", new AtomicBoolean());
                    runOnUiThread(() -> status.setText("Oczekiwanie na systemowe potwierdzenie"));
                } catch (Exception error) { runOnUiThread(() -> status.setText("Blad: " + error)); }
            }).start();
        }); setContentView(layout); install.requestFocus();
    }
    @Override protected void onResume() {
        super.onResume();
        String result = getSharedPreferences("install", MODE_PRIVATE).getString("result", null);
        if (result != null) status.setText(result);
    }
}
