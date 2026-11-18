package eu.kickmod.patcher;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainActivity extends Activity {
    private static final int PICK = 11, EXPORT = 12, STORAGE = 13;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService updateExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean stopCheck = new AtomicBoolean();
    private SupportProfile support;
    private UpdateClient.Candidate availableUpdate;
    private boolean checkingUpdate;
    private AtomicBoolean cancelled = new AtomicBoolean();
    private boolean busy;
    private volatile boolean closed;
    private TextView status, detail, percentLabel;
    private StudioLayout layout;
    private ProgressBar progress;
    private Button choose, downloads, cancel, install, export, downloadLink;
    private File result;
    private int activeStep;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        support = SupportProfile.load(this);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        makeUi();
        File previous = new File(getFilesDir(), "output/kick-mod.apk");
        if (supportedOutput(previous)) {
            result = previous; enableOutput(); progress.setProgress(100);
            percentLabel.setText("APK READY");
            status.setText("Patched APK available on this TV");
            detail.setText("Install or save the previously verified result, or choose another original APKM to patch again.");
        }
        incoming(getIntent());
        try {
            String cached = getSharedPreferences("updates", MODE_PRIVATE).getString("candidate", null);
            if (cached != null) setAvailableUpdate(UpdateClient.parse(new org.json.JSONObject(cached)));
        } catch (Exception error) { getSharedPreferences("updates", MODE_PRIVATE).edit().remove("candidate").apply(); }
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); incoming(intent); }
    @Override protected void onResume() {
        super.onResume();
        String message = getSharedPreferences("install", MODE_PRIVATE).getString("result", null);
        if (message != null) {
            getSharedPreferences("install", MODE_PRIVATE).edit().remove("result").apply();
            status.setText(message);
        }
        checkUpdates(false);
    }

    private void makeUi() {
        layout = new StudioLayout(this, support);
        downloadLink = layout.download; choose = layout.choose; downloads = layout.downloads;
        install = layout.install; export = layout.save; cancel = layout.cancel;
        status = layout.status; detail = layout.detail; progress = layout.progress; percentLabel = layout.percent;
        layout.select = section -> { if (!busy) showContext(section == 0 ? 0 : section == 1 ? 1 : 3); };
        downloadLink.setOnClickListener(v -> openDownload());
        choose.setOnClickListener(v -> pick()); downloads.setOnClickListener(v -> downloads());
        install.setOnClickListener(v -> install()); export.setOnClickListener(v -> export());
        layout.haveFile.setOnClickListener(v -> { showContext(1); choose.requestFocus(); });
        layout.updates.setOnClickListener(v -> openUpdates());
        cancel.setOnClickListener(v -> { cancelled.set(true); status.setText("Cancelling after the current operation…"); cancel.setEnabled(false); });
        setContentView(layout.root); layout.nav[0].requestFocus();
    }
    private void showContext(int index) { activeStep = index; layout.present(index, result); }
    private void openDownload() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(support.downloadUrl))); }
        catch (ActivityNotFoundException error) {
            show("A web browser is needed", "Open APKMirror on another device and select Kick Android TV " + support.version + " (" + support.versionCode + "), arm64-v8a + armeabi-v7a, 320dpi, Android 8.0+. Copy the APKM to this TV's Download folder.");
        }
    }
    private void incoming(Intent intent) {
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        if (Intent.ACTION_SEND.equals(intent.getAction())) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        if (uri != null) start(uri);
    }
    private void pick() {
        if (launchPicker(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE), PICK)) return;
        if (launchPicker(new Intent(Intent.ACTION_GET_CONTENT).setType("application/octet-stream").addCategory(Intent.CATEGORY_OPENABLE), PICK)) return;
        if (Build.VERSION.SDK_INT <= 29) downloads();
        else show("File picker unavailable", "Open the original APKM with this patcher from a file manager that grants access to the file.");
    }
    private boolean launchPicker(Intent intent, int request) {
        List<ResolveInfo> handlers = new ArrayList<>(getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY));
        ArrayList<ComponentName> excluded = new ArrayList<>();
        for (ResolveInfo info : handlers) if (info.activityInfo.packageName.equals("com.google.android.tv.frameworkpackagestubs"))
            excluded.add(new ComponentName(info.activityInfo.packageName, info.activityInfo.name));
        handlers.removeIf(info -> info.activityInfo.packageName.equals("com.google.android.tv.frameworkpackagestubs"));
        if (handlers.isEmpty()) return false;
        if (handlers.size() == 1) {
            ResolveInfo handler = handlers.get(0);
            intent.setComponent(new ComponentName(handler.activityInfo.packageName, handler.activityInfo.name));
        } else {
            Intent chooser = Intent.createChooser(intent, "Choose a file");
            chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, excluded.toArray(new ComponentName[0]));
            intent = chooser;
        }
        try { startActivityForResult(intent, request); return true; }
        catch (ActivityNotFoundException unavailable) { return false; }
    }
    private void downloads() {
        if (Build.VERSION.SDK_INT > 29) { pick(); return; }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, STORAGE); return;
        }
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = directory.listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".apkm"));
        if (files == null || files.length == 0) { show("No APKM files found", "Place the original APKM in the TV's Download folder, or use Choose original APKM."); return; }
        Arrays.sort(files, Comparator.comparing(File::getName));
        String[] names = new String[files.length]; for (int i = 0; i < files.length; i++) names[i] = files[i].getName();
        new AlertDialog.Builder(this).setTitle("Original APKM in Downloads").setItems(names, (dialog, which) -> start(Uri.fromFile(files[which])))
            .setNegativeButton("Back", null).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request == STORAGE && grants.length > 0 && grants[0] == PackageManager.PERMISSION_GRANTED) downloads();
    }
    private void start(Uri uri) {
        if (busy) { show("Already working", "Wait for the current operation or cancel it first."); return; }
        if (!"content".equals(uri.getScheme()) && !"file".equals(uri.getScheme())) { show("Unsupported file", "Select a local APKM file."); return; }
        busy = true; result = null; cancelled = new AtomicBoolean(); final AtomicBoolean signal = cancelled;
        downloadLink.setEnabled(false); choose.setEnabled(false); downloads.setEnabled(false); install.setEnabled(false); export.setEnabled(false);
        cancel.setVisibility(View.VISIBLE); cancel.setEnabled(true); cancel.requestFocus();
        status.setText("Reading selected file"); progress.setProgress(0);
        percentLabel.setText("0%");
        detail.setText("Your original file stays unchanged. Keep the patcher open while it works.");
        layout.working(true);
        executor.execute(() -> {
            File input = new File(getCacheDir(), "selected.apkm");
            try {
                if (getCacheDir().getUsableSpace() < 450L * 1024 * 1024) throw new IOException("At least 450 MB of free app storage is needed.");
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(input)) {
                    if (in == null) throw new IOException("Cannot open this file.");
                    PatchEngine.transfer(in, out, 256L * 1024 * 1024, signal);
                }
                long started = SystemClock.elapsedRealtime();
                File completed = new PatchEngine(this).patch(input, (stage, percent) -> ui(() -> {
                    status.setText(stage); progress.setProgress(percent); percentLabel.setText(percent + "%"); layout.phase(percent);
                }), signal);
                ui(() -> {
                    result = completed; finishWork(); enableOutput();
                    status.setText("Patched and signature verified");
                    detail.setText("Completed on this TV in " + ((SystemClock.elapsedRealtime() - started) / 1000) + " seconds. The original file is unchanged.");
                    showContext(3); install.post(() -> install.requestFocus());
                });
            } catch (Exception error) {
                android.util.Log.e("KickPatcher", "Patch failed", error);
                ui(() -> {
                    finishWork();
                    File previous = new File(getFilesDir(), "output/kick-mod.apk");
                    if (supportedOutput(previous)) { result = previous; enableOutput(); }
                    status.setText(signal.get() ? "Cancelled" : "Could not patch this file");
                    detail.setText(signal.get() ? "No application was installed. You can choose another file." : message(error)); showContext(1); choose.post(() -> choose.requestFocus());
                });
            } finally { if (input.exists() && !input.delete()) android.util.Log.w("KickPatcher", "Cannot remove temporary input"); }
        });
    }
    private void ui(Runnable action) { runOnUiThread(() -> { if (!closed) action.run(); }); }
    private void finishWork() { busy = false; downloadLink.setEnabled(true); choose.setEnabled(true); downloads.setEnabled(true); cancel.setVisibility(View.GONE); layout.working(false); layout.nav[activeStep == 0 ? 0 : activeStep <= 2 ? 1 : 2].requestFocus(); }
    private void enableOutput() { install.setEnabled(true); export.setEnabled(true); if(result!=null)percentLabel.setText("APK READY"); showContext(activeStep); }
    private String message(Exception error) { String value = error.getMessage(); return value == null ? error.getClass().getSimpleName() : value; }
    private void show(String title, String message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("OK", null).show(); }

    private boolean signatureCompatible() throws Exception {
        PackageManager manager = getPackageManager();
        int flags = PackageManager.GET_SIGNATURES;
        if (Build.VERSION.SDK_INT >= 28) flags |= PackageManager.GET_SIGNING_CERTIFICATES;
        PackageInfo candidate = manager.getPackageArchiveInfo(result.getAbsolutePath(), flags);
        if (candidate == null || !"com.kick.mobile.mod".equals(candidate.packageName) || signatures(candidate) == null)
            throw new IOException("The output is not a signed Kick MOD APK.");
        try {
            PackageInfo installed = manager.getPackageInfo(candidate.packageName, flags);
            if (!Arrays.equals(signatures(installed), signatures(candidate))) {
                show("A different signing key is already installed", "Your current Kick MOD uses a different key. Android cannot update it with this TV's key. Your installed app and its data have not been changed. Save the APK if you want to keep it."); return false;
            }
        } catch (PackageManager.NameNotFoundException absent) { /* Pierwsza instalacja. */ }
        return true;
    }
    private Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null) return info.signingInfo.getApkContentsSigners();
        return info.signatures;
    }
    private void install() {
        if (busy) return;
        if (result == null) { showContext(3); status.setText("Choose an original APKM first"); detail.setText("Use the Patch stage to create your app, then come back to Install."); return; }
        try {
            if (!signatureCompatible()) return;
            if (!getPackageManager().canRequestPackageInstalls()) {
                startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
                show("Allow installation", "Allow this patcher to install apps in system settings, then select Install patched app again."); return;
            }
            busy = true; cancelled = new AtomicBoolean(); downloadLink.setEnabled(false); choose.setEnabled(false); downloads.setEnabled(false); install.setEnabled(false); export.setEnabled(false);
            status.setText("Preparing system installer");
            executor.execute(() -> {
                try {
                    Installer.submit(this, result, "com.kick.mobile.mod", cancelled);
                    ui(() -> { finishWork(); enableOutput(); status.setText("Waiting for system installation approval"); });
                } catch (Exception error) {
                    ui(() -> { finishWork(); enableOutput(); show("Installation could not start", message(error)); });
                }
            });
        } catch (Exception error) { show("Cannot install", message(error)); }
    }
    private void export() {
        if (busy) return;
        if (result == null) { showContext(4); status.setText("No patched APK to save yet"); detail.setText("Use the Patch stage to create a verified APK before saving it."); return; }
        if (launchPicker(new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/vnd.android.package-archive")
            .addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, support.outputName()), EXPORT)) return;
        if (Build.VERSION.SDK_INT < 29) { show("File picker unavailable", "Install a document provider to save a copy. You can still install the app directly from the patcher."); return; }
        try {
            ContentValues values = new ContentValues();
            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, support.outputName());
            values.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive");
            values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/KickPatcher");
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("Cannot create the output file in Downloads.");
            saveTo(uri, true);
        } catch (Exception error) { show("Could not save APK", message(error)); }
    }
    @Override protected void onActivityResult(int request, int response, Intent data) {
        super.onActivityResult(request, response, data);
        if (response != RESULT_OK || data == null || data.getData() == null) return;
        if (request == PICK) start(data.getData());
        else if (request == EXPORT && result != null) saveTo(data.getData(), false);
    }
    private void saveTo(Uri uri, boolean publishDownload) {
            File source = result;
            busy = true; cancelled = new AtomicBoolean();
            downloadLink.setEnabled(false); choose.setEnabled(false); downloads.setEnabled(false);
            install.setEnabled(false); export.setEnabled(false); status.setText("Saving patched APK");
            layout.saving();
            cancel.setVisibility(View.VISIBLE); cancel.setEnabled(true); cancel.requestFocus();
            executor.execute(() -> {
                try {
                    try (InputStream in = new FileInputStream(source); OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                        if (out == null) throw new IOException("Cannot write selected destination.");
                        PatchEngine.transfer(in, out, 300L * 1024 * 1024, cancelled);
                    }
                    if (publishDownload && Build.VERSION.SDK_INT >= 29) {
                        ContentValues values = new ContentValues(); values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                        getContentResolver().update(uri, values, null, null);
                    }
                    ui(() -> { finishWork(); enableOutput(); status.setText("Patched APK saved"); detail.setText(publishDownload ? "Saved to Download / KickPatcher on this TV." : "Saved to the destination you selected."); });
                } catch (Exception error) {
                    if (publishDownload) try { getContentResolver().delete(uri, null, null); } catch (RuntimeException cleanup) { android.util.Log.w("KickPatcher", "Cannot remove incomplete export", cleanup); }
                    ui(() -> { finishWork(); enableOutput(); show("Could not save APK", message(error)); });
                }
            });
    }
    @Override public void onBackPressed() {
        if (busy) new AlertDialog.Builder(this).setTitle("Cancel operation?").setMessage("The current operation will be cancelled. No app has been installed.")
            .setPositiveButton("Cancel operation", (dialog, which) -> cancelled.set(true)).setNegativeButton("Keep working", null).show();
        else if (activeStep > 0) { int section = activeStep <= 2 ? 0 : 1; showContext(section == 0 ? 0 : 1); layout.nav[section].requestFocus(); }
        else super.onBackPressed();
    }
    private boolean supportedOutput(File apk) {
        if (!apk.isFile()) return false;
        File marker = new File(apk.getParentFile(), "profile.sha256");
        if (marker.length() != 64) return false;
        try (DataInputStream in = new DataInputStream(new FileInputStream(marker))) {
            byte[] identity = new byte[64]; in.readFully(identity);
            if (!support.identity.equals(new String(identity, "US-ASCII"))) return false;
        } catch (IOException error) { return false; }
        PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        return info != null && "com.kick.mobile.mod".equals(info.packageName) && UpdateClient.code(info) == support.versionCode;
    }

    private void setAvailableUpdate(UpdateClient.Candidate candidate) throws Exception {
        long current = UpdateClient.code(getPackageManager().getPackageInfo(getPackageName(), 0));
        availableUpdate = candidate.code > current ? candidate : null;
        layout.updates.setText(availableUpdate == null ? "Updates" : "Update ready");
    }

    private void openUpdates() {
        if (busy) return;
        if (getPackageName().endsWith(".debug")) {
            show("Development build", "Automatic updates are available in the release patcher. DEBUG stays separate from the release app."); return;
        }
        if (availableUpdate == null) { checkUpdates(true); return; }
        UpdateClient.Candidate candidate = availableUpdate;
        new AlertDialog.Builder(this).setTitle("Patcher " + candidate.version + " is available")
            .setMessage("Includes a tested profile for Kick Android TV " + candidate.kickVersion + ". Download and verify the update on this TV, then approve Android's installation dialog. Your output signing key and patcher data are kept. New Kick versions need a tested profile before they can be patched.")
            .setPositiveButton("Download update", (dialog, which) -> installUpdate(candidate))
            .setNegativeButton("Later", null).show();
    }

    private void checkUpdates(boolean manual) {
        if (closed || busy || getPackageName().endsWith(".debug")) return;
        if (checkingUpdate) { if (manual) Toast.makeText(this, "Checking for updates…", Toast.LENGTH_SHORT).show(); return; }
        SharedPreferences prefs = getSharedPreferences("updates", MODE_PRIVATE);
        long now = System.currentTimeMillis(), last = prefs.getLong("checked", 0);
        long interval = prefs.getBoolean("ok", false) ? 6 * 60 * 60 * 1000L : 15 * 60 * 1000L;
        if (!manual && now >= last && now - last < interval) return;
        checkingUpdate = true;
        if (manual) layout.updates.setText("Checking…");
        updateExecutor.execute(() -> {
            try {
                UpdateClient.Candidate candidate = UpdateClient.check(stopCheck);
                org.json.JSONObject cached = new org.json.JSONObject();
                cached.put("format", 1).put("versionCode", candidate.code).put("versionName", candidate.version)
                    .put("kickVersion", candidate.kickVersion).put("size", candidate.size).put("apkUrl", candidate.url).put("sha256", candidate.sha256);
                prefs.edit().putLong("checked", System.currentTimeMillis()).putBoolean("ok", true).putString("candidate", cached.toString()).apply();
                ui(() -> {
                    checkingUpdate = false;
                    try {
                        setAvailableUpdate(candidate);
                        if (manual && !busy) {
                            if (availableUpdate != null) openUpdates();
                            else show("Your patcher is up to date", "Installed patcher supports Kick Android TV " + support.version + ". Checks run automatically every six hours while opening or resuming the app. A newer Kick needs a tested patcher release first.");
                        }
                    } catch (Exception error) { android.util.Log.w("KickPatcher", "Cannot compare update versions", error); }
                });
            } catch (Exception error) {
                prefs.edit().putLong("checked", System.currentTimeMillis()).putBoolean("ok", false).apply();
                ui(() -> {
                    checkingUpdate = false;
                    layout.updates.setText(availableUpdate == null ? "Updates" : "Update ready");
                    if (manual && !busy) show("Could not check for updates", "Patching still works offline with the bundled profile. Try again later.\n\n" + message(error));
                });
            }
        });
    }

    private void installUpdate(UpdateClient.Candidate candidate) {
        if (busy) return;
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName())));
            show("Allow patcher updates", "Allow this patcher as an installation source, then select Download update again."); return;
        }
        busy = true; cancelled = new AtomicBoolean();
        final AtomicBoolean signal = cancelled;
        downloadLink.setEnabled(false); choose.setEnabled(false); downloads.setEnabled(false);
        install.setEnabled(false); export.setEnabled(false);
        showContext(1); layout.updating();
        cancel.setVisibility(View.VISIBLE); cancel.setEnabled(true); cancel.requestFocus();
        progress.setProgress(0); status.setText("Downloading patcher " + candidate.version);
        detail.setText("Kick " + candidate.kickVersion + " support. The APK checksum and publisher signature will be verified.");
        updateExecutor.execute(() -> {
            File apk = null;
            try {
                apk = UpdateClient.download(this, candidate, value -> ui(() -> {
                    progress.setProgress(value); percentLabel.setText(value + "%");
                    if (value >= 94) status.setText("Verifying patcher update signature");
                }), signal);
                Delta.check(signal);
                Installer.submit(this, apk, getPackageName(), signal);
                ui(() -> { finishWork(); enableOutput(); status.setText("Approve the patcher update in Android's dialog"); });
            } catch (Exception error) {
                android.util.Log.w("KickPatcher", "Patcher update failed", error);
                ui(() -> { finishWork(); enableOutput(); status.setText(signal.get() ? "Update cancelled" : "Could not update the patcher"); detail.setText(message(error)); });
            } finally { if (apk != null && apk.exists() && !apk.delete()) android.util.Log.w("KickPatcher", "Cannot remove downloaded update."); }
        });
    }

    @Override protected void onDestroy() {
        closed = true; cancelled.set(true); stopCheck.set(true);
        executor.shutdownNow(); updateExecutor.shutdownNow(); super.onDestroy();
    }
}
