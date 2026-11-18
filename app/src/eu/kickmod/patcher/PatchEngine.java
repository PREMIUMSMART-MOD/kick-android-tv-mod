package eu.kickmod.patcher;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.*;

public final class PatchEngine {
    public interface Progress { void update(String stage, int percent); }
    private static final long MAX_ENTRY = 128L * 1024 * 1024;
    private final Context context;

    public PatchEngine(Context context) { this.context = context.getApplicationContext(); }

    public File patch(File input, Progress progress, AtomicBoolean cancelled) throws Exception {
        File work = new File(context.getCacheDir(), "patch-work");
        erase(work);
        if (!work.mkdirs()) throw new IOException("Cannot create workspace.");
        File outputDir = new File(context.getFilesDir(), "output");
        if (!outputDir.isDirectory() && !outputDir.mkdirs()) throw new IOException("Cannot create output directory.");
        File signed = new File(outputDir, "kick-mod.apk");
        File signing = new File(work, "signed.apk");
        Map<String, ZipFile> modules = new LinkedHashMap<>();
        try {
            JSONObject profile;
            try (InputStream in = context.getAssets().open("profile/profile.json")) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                transfer(in, bytes, 2 * 1024 * 1024, cancelled);
                profile = new JSONObject(bytes.toString("UTF-8"));
            }
            if (profile.getInt("format") != 1) throw new IOException("Unsupported patch profile.");
            SupportProfile support = SupportProfile.load(context);
            if (!support.version.equals(profile.getString("version")) || support.versionCode != profile.getInt("versionCode"))
                throw new IOException("Bundled support metadata does not match the patch profile.");
            progress.update("Checking original Kick " + support.version + " files", 0);
            JSONArray required = profile.getJSONArray("modules");
            try (ZipFile apkm = new ZipFile(input)) {
                Set<String> seen = new HashSet<>();
                for (Enumeration<? extends ZipEntry> all = apkm.entries(); all.hasMoreElements();) {
                    if (!seen.add(all.nextElement().getName())) throw new IOException("Duplicate APKM entry.");
                }
                for (int i = 0; i < required.length(); i++) {
                    Delta.check(cancelled);
                    JSONObject module = required.getJSONObject(i);
                    String name = module.getString("name");
                    safeName(name);
                    if (name.contains("/")) throw new IOException("Invalid module name.");
                    ZipEntry entry = apkm.getEntry(name);
                    if (entry == null) throw new IOException("This APKM is missing " + name + ". Select the supported original " + support.version + " APKM.");
                    File extracted = new File(work, name);
                    try (InputStream in = apkm.getInputStream(entry); OutputStream out = new FileOutputStream(extracted)) {
                        long length = transfer(in, out, MAX_ENTRY, cancelled);
                        if (length != module.getLong("size")) throw new IOException("Unsupported APKM: " + name + " has a different size.");
                    }
                    if (!hash(extracted, cancelled).equals(module.getString("sha256")))
                        throw new IOException("Unsupported or modified " + name + ". Use the original Kick " + support.version + " (" + support.versionCode + "), 2arch / 1dpi APKM.");
                    modules.put(name, new ZipFile(extracted));
                    progress.update("Original files verified (" + (i + 1) + "/" + required.length() + ")", 3 + i * 3);
                }
            }
            File unsigned = new File(work, "unsigned.apk");
            JSONArray entries = profile.getJSONArray("entries");
            Set<String> outputNames = new HashSet<>();
            long total = 0;
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(unsigned)))) {
                zip.setLevel(6);
                for (int i = 0; i < entries.length(); i++) {
                    Delta.check(cancelled);
                    JSONObject entry = entries.getJSONObject(i);
                    String name = entry.getString("name");
                    safeName(name);
                    if (!outputNames.add(name)) throw new IOException("Duplicate output entry.");
                    long expected = entry.getLong("size");
                    total += expected;
                    if (expected < 0 || expected > MAX_ENTRY || total > 300L * 1024 * 1024) throw new IOException("Output size limit exceeded.");
                    File plain = new File(work, "entry.bin");
                    String mode = entry.getString("mode");
                    if (mode.equals("copy") || mode.equals("delta")) {
                        ZipFile source = modules.get(entry.getString("module"));
                        if (source == null) throw new IOException("Unknown source module.");
                        ZipEntry original = source.getEntry(entry.getString("source"));
                        if (original == null) throw new IOException("Missing source entry.");
                        File destination = mode.equals("copy") ? plain : new File(work, "source.bin");
                        try (InputStream in = source.getInputStream(original); OutputStream out = new FileOutputStream(destination)) {
                            transfer(in, out, MAX_ENTRY, cancelled);
                        }
                        if (mode.equals("delta")) {
                            if (!hash(destination, cancelled).equals(entry.getString("sourceSha256"))) throw new IOException("Delta source mismatch.");
                            File patch = asset(entry, work, cancelled);
                            try (InputStream in = new FileInputStream(patch)) { Delta.apply(destination, in, plain, expected, cancelled); }
                        }
                    } else if (mode.equals("own")) {
                        File payload = asset(entry, work, cancelled);
                        try (InputStream in = new GZIPInputStream(new FileInputStream(payload)); OutputStream out = new FileOutputStream(plain)) {
                            transfer(in, out, expected, cancelled);
                        }
                    } else throw new IOException("Unknown patch operation.");
                    if (plain.length() != expected || !hash(plain, cancelled).equals(entry.getString("sha256")))
                        throw new IOException("Reconstruction failed for " + name);
                    ZipEntry target = new ZipEntry(name);
                    target.setTime(1577836800000L);
                    if (entry.getBoolean("stored")) {
                        CRC32 crc = new CRC32();
                        try (InputStream in = new CheckedInputStream(new FileInputStream(plain), crc)) { transfer(in, null, expected, cancelled); }
                        target.setMethod(ZipEntry.STORED); target.setSize(expected); target.setCompressedSize(expected); target.setCrc(crc.getValue());
                    }
                    zip.putNextEntry(target);
                    try (InputStream in = new FileInputStream(plain)) { transfer(in, zip, expected, cancelled); }
                    zip.closeEntry();
                    progress.update("Applying modifications (" + (i + 1) + "/" + entries.length() + ")", 15 + (i + 1) * 65 / entries.length());
                }
            }
            Delta.check(cancelled);
            progress.update("Signing with this TV's private key", 83);
            LocalSigner.sign(unsigned, signing);
            Delta.check(cancelled);
            progress.update("Checking signed APK contents", 96);
            try (ZipFile verified = new ZipFile(signing)) {
                if (verified.size() != entries.length()) throw new IOException("Signed APK entry count changed.");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.getJSONObject(i);
                    ZipEntry actual = verified.getEntry(entry.getString("name"));
                    if (actual == null || actual.getSize() != entry.getLong("size")) throw new IOException("Signed APK entry mismatch.");
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = new java.security.DigestInputStream(verified.getInputStream(actual), digest)) {
                        transfer(in, null, MAX_ENTRY, cancelled);
                    }
                    if (!hex(digest.digest()).equals(entry.getString("sha256"))) throw new IOException("Signed APK content mismatch.");
                }
            }
            android.system.Os.rename(signing.getAbsolutePath(), signed.getAbsolutePath());
            File marker = new File(outputDir, "profile.sha256.tmp");
            try (FileOutputStream out = new FileOutputStream(marker)) {
                out.write(support.identity.getBytes("US-ASCII"));
                out.getFD().sync();
            }
            android.system.Os.rename(marker.getAbsolutePath(), new File(outputDir, "profile.sha256").getAbsolutePath());
            progress.update("Ready to install", 100);
            return signed;
        } finally {
            for (ZipFile zip : modules.values()) try { zip.close(); } catch (IOException ignored) { }
            erase(work);
        }
    }

    private File asset(JSONObject entry, File work, AtomicBoolean cancelled) throws Exception {
        String name = entry.getString("asset"); safeName(name);
        File target = new File(work, "payload.bin");
        try (InputStream in = context.getAssets().open("profile/" + name); OutputStream out = new FileOutputStream(target)) {
            transfer(in, out, MAX_ENTRY, cancelled);
        }
        if (!hash(target, cancelled).equals(entry.getString("patchSha256"))) throw new IOException("Damaged patch payload.");
        return target;
    }

    static long transfer(InputStream in, OutputStream out, long limit, AtomicBoolean cancelled) throws IOException {
        byte[] buffer = new byte[65536]; long total = 0; int read;
        while ((read = in.read(buffer)) != -1) {
            Delta.check(cancelled); total += read;
            if (total > limit) throw new IOException("File exceeds the supported size limit.");
            if (out != null) out.write(buffer, 0, read);
        }
        return total;
    }

    private static String hash(File file, AtomicBoolean cancelled) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.security.DigestInputStream(new FileInputStream(file), digest)) {
            transfer(in, null, MAX_ENTRY, cancelled);
        }
        return hex(digest.digest());
    }
    private static String hex(byte[] data) {
        StringBuilder result = new StringBuilder();
        for (byte item : data) result.append(String.format(java.util.Locale.ROOT, "%02x", item & 255));
        return result.toString();
    }
    private static void safeName(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains("..") || name.contains(":"))
            throw new IOException("Unsafe ZIP entry name.");
    }
    private static void erase(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot list workspace.");
            for (File child : children) erase(child);
        }
        if (!file.delete()) throw new IOException("Cannot clean workspace.");
    }
}
