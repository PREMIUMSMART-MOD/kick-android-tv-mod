package eu.kickmod.patcher;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.Locale;

/** Testy z prawdziwymi APK, uruchamiane w Androidzie przez osobna instrumentacje. */
final class UpdateProbe {
    private static final String URL = "https://github.com/PREMIUMSMART-MOD/kick-android-tv-mod/releases/download/v0.1.0/Kick-Android-TV-Patcher.apk";
    private interface Action { void run() throws Exception; }

    static JSONObject metadata(File file, long code) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.security.DigestInputStream(new FileInputStream(file), digest)) {
            byte[] bytes = new byte[65536]; while (in.read(bytes) != -1) { }
        }
        StringBuilder sha = new StringBuilder();
        for (byte value : digest.digest()) sha.append(String.format(Locale.ROOT, "%02x", value & 255));
        return new JSONObject().put("format", 1).put("versionCode", code).put("versionName", "0.1.0")
            .put("kickVersion", "1.11.0").put("size", file.length()).put("sha256", sha.toString()).put("apkUrl", URL);
    }

    private static void rejected(String label, Action action) throws Exception {
        boolean refused = false;
        try { action.run(); } catch (Exception expected) { refused = true; }
        if (!refused) throw new AssertionError("Unexpectedly accepted: " + label);
    }

    static int run(Context context) throws Exception {
        File directory = context.getExternalFilesDir(null);
        File valid = new File(directory, "updater-valid.apk");
        File wrongKey = new File(directory, "updater-wrong-key.apk");
        File wrongPackage = new File(directory, "updater-wrong-package.apk");
        JSONObject base = metadata(valid, 3);
        UpdateClient.validate(context, valid, UpdateClient.parse(base));
        int checks = 1;
        for (String url : new String[]{"http://github.com/a.apk", "https://example.org/a.apk",
            URL.replace("PREMIUMSMART-MOD", "other"), URL + "?x=1", URL + "#x", URL.replace("v0.1.0", "..")}) {
            JSONObject bad = new JSONObject(base.toString()).put("apkUrl", url);
            rejected("source URL", () -> UpdateClient.parse(bad)); checks++;
        }
        for (long value : new long[]{-1, 0, Long.MAX_VALUE}) {
            JSONObject bad = new JSONObject(base.toString()).put("versionCode", value);
            rejected("version bounds", () -> UpdateClient.parse(bad)); checks++;
        }
        JSONObject huge = new JSONObject(base.toString()).put("size", 100L * 1024 * 1024);
        rejected("size bound", () -> UpdateClient.parse(huge)); checks++;
        JSONObject badHash = new JSONObject(base.toString()).put("sha256", "0000000000000000000000000000000000000000000000000000000000000000");
        rejected("checksum", () -> UpdateClient.validate(context, valid, UpdateClient.parse(badHash))); checks++;
        rejected("wrong signing key", () -> UpdateClient.validate(context, wrongKey, UpdateClient.parse(metadata(wrongKey, 3)))); checks++;
        rejected("wrong package", () -> UpdateClient.validate(context, wrongPackage, UpdateClient.parse(metadata(wrongPackage, 2)))); checks++;
        File current = new File(context.getApplicationInfo().sourceDir);
        rejected("downgrade or same version", () -> UpdateClient.validate(context, current, UpdateClient.parse(metadata(current, 2)))); checks++;
        File damaged = new File(directory, "updater-tampered.apk");
        try (RandomAccessFile source = new RandomAccessFile(valid, "r"); FileOutputStream out = new FileOutputStream(damaged)) {
            byte[] bytes = new byte[65536]; int read;
            while ((read = source.read(bytes)) != -1) out.write(bytes, 0, read);
        }
        try (RandomAccessFile modify = new RandomAccessFile(damaged, "rw")) {
            modify.seek(300); int value = modify.read(); modify.seek(300); modify.write(value ^ 1);
        }
        rejected("damaged signature", () -> UpdateClient.validate(context, damaged, UpdateClient.parse(metadata(damaged, 3)))); checks++;
        if (!damaged.delete()) throw new IOException("Cannot remove tampered fixture.");
        return checks;
    }
}
