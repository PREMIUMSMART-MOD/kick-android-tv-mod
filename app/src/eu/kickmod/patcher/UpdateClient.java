package eu.kickmod.patcher;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import com.android.apksig.ApkVerifier;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** HTTPS dostarcza pliki; zaufanie do aktualizacji wynika z podpisu zainstalowanej aplikacji. */
public final class UpdateClient {
    static final String RELEASES = "https://github.com/PREMIUMSMART-MOD/kick-android-tv-mod/releases/";
    static final String FEED = RELEASES + "latest/download/update.json";
    static final long MAX_APK = 48L * 1024 * 1024;
    public interface Progress { void update(int percent); }

    public static final class Candidate {
        public final long code, size;
        public final String version, kickVersion, url, sha256;
        private Candidate(long code, long size, String version, String kickVersion, String url, String sha256) {
            this.code = code; this.size = size; this.version = version;
            this.kickVersion = kickVersion; this.url = url; this.sha256 = sha256;
        }
    }

    public static Candidate parse(JSONObject json) throws Exception {
        long code = json.getLong("versionCode"), size = json.getLong("size");
        String version = json.getString("versionName"), kick = json.getString("kickVersion");
        String url = json.getString("apkUrl"), sha = json.getString("sha256");
        URL source = new URL(url);
        if (json.getInt("format") != 1 || code < 1 || code > Integer.MAX_VALUE || size < 1024 || size > MAX_APK
            || !version.matches("[0-9]+(?:\\.[0-9]+){1,3}") || !kick.matches("[0-9]+(?:\\.[0-9]+){1,3}")
            || !sha.matches("[a-f0-9]{64}") || !url.startsWith(RELEASES + "download/")
            || !source.getPath().matches("/PREMIUMSMART-MOD/kick-android-tv-mod/releases/download/v[0-9]+(?:\\.[0-9]+){1,3}(?:-[A-Za-z0-9.-]+)?/Kick-Android-TV-Patcher\\.apk")
            || source.getQuery() != null || source.getRef() != null || source.getUserInfo() != null || source.getPort() != -1)
            throw new IOException("Invalid update metadata.");
        return new Candidate(code, size, version, kick, url, sha);
    }

    public static Candidate check(AtomicBoolean cancelled) throws Exception {
        HttpURLConnection connection = open(FEED, cancelled);
        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            PatchEngine.transfer(in, bytes, 65536, cancelled);
            return parse(new JSONObject(bytes.toString("UTF-8")));
        } finally { connection.disconnect(); }
    }

    private static HttpURLConnection open(String address, AtomicBoolean cancelled) throws Exception {
        for (int redirects = 0; redirects < 6; redirects++) {
            Delta.check(cancelled);
            URL url = new URL(address);
            String host = url.getHost();
            if (!url.getProtocol().equals("https") || url.getUserInfo() != null || (url.getPort() != -1 && url.getPort() != 443) || !(host.equals("github.com")
                || host.equals("release-assets.githubusercontent.com") || host.equals("objects.githubusercontent.com")))
                throw new IOException("Unexpected update download destination.");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(12000); connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "Kick-Android-TV-Patcher");
            connection.setRequestProperty("Accept-Encoding", "identity");
            int status;
            try { status = connection.getResponseCode(); }
            catch (Exception error) { connection.disconnect(); throw error; }
            if (status == 200) return connection;
            String next = connection.getHeaderField("Location");
            connection.disconnect();
            if ((status == 301 || status == 302 || status == 303 || status == 307 || status == 308) && next != null) {
                address = new URL(url, next).toString();
            } else throw new IOException("Update server returned HTTP " + status + ". Try again later.");
        }
        throw new IOException("Too many update download redirects.");
    }

    public static File download(Context context, Candidate candidate, Progress progress, AtomicBoolean cancelled) throws Exception {
        File file = new File(context.getCacheDir(), "patcher-update.apk");
        if (file.exists() && !file.delete()) throw new IOException("Cannot clear previous update download.");
        boolean complete = false;
        try {
            if (context.getCacheDir().getUsableSpace() < candidate.size * 3) throw new IOException("Not enough free storage for this update.");
            HttpURLConnection connection = open(candidate.url, cancelled);
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(file)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[65536]; int read, last = -1; long total = 0;
                while ((read = in.read(buffer)) != -1) {
                    Delta.check(cancelled); total += read;
                    if (total > candidate.size) throw new IOException("Update download exceeds its declared size.");
                    out.write(buffer, 0, read); digest.update(buffer, 0, read);
                    int percent = (int) (total * 90 / candidate.size);
                    if (percent != last) { progress.update(percent); last = percent; }
                }
                if (total != candidate.size || !hex(digest.digest()).equals(candidate.sha256)) throw new IOException("Update download checksum mismatch.");
            } finally { connection.disconnect(); }
            progress.update(94);
            validate(context, file, candidate);
            Delta.check(cancelled);
            complete = true; progress.update(100);
            return file;
        } finally {
            if (!complete && file.exists() && !file.delete()) android.util.Log.w("KickPatcher", "Cannot remove incomplete update.");
        }
    }

    public static void validate(Context context, File file, Candidate candidate) throws Exception {
        if (file.length() != candidate.size) throw new IOException("Update APK size mismatch.");
        MessageDigest checksum = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.security.DigestInputStream(new FileInputStream(file), checksum)) {
            PatchEngine.transfer(in, null, MAX_APK, new AtomicBoolean());
        }
        if (!hex(checksum.digest()).equals(candidate.sha256)) throw new IOException("Update APK checksum mismatch.");
        ApkVerifier.Result verified = new ApkVerifier.Builder(file).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify();
        if (!verified.isVerified()) throw new IOException("Update APK signature is invalid.");
        PackageManager manager = context.getPackageManager();
        int flags = PackageManager.GET_SIGNATURES | (Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : 0);
        PackageInfo installed = manager.getPackageInfo(context.getPackageName(), flags);
        PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), 0);
        if (archive == null || !context.getPackageName().equals(archive.packageName) || code(archive) != candidate.code
            || !candidate.version.equals(archive.versionName) || candidate.code <= code(installed)
            || archive.applicationInfo == null || archive.applicationInfo.minSdkVersion > Build.VERSION.SDK_INT)
            throw new IOException("Update package or version does not match this patcher.");
        Signature[] signatures = Build.VERSION.SDK_INT >= 28 && installed.signingInfo != null
            ? installed.signingInfo.getApkContentsSigners() : installed.signatures;
        if (signatures == null || signatures.length == 0) throw new IOException("Cannot read the installed patcher signing certificate.");
        Set<String> trusted = new HashSet<>(), received = new HashSet<>();
        for (Signature signature : signatures) trusted.add(hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));
        for (X509Certificate cert : verified.getSignerCertificates()) received.add(hex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded())));
        if (!trusted.equals(received)) throw new IOException("Update was signed with a different patcher key.");
    }

    static long code(PackageInfo info) { return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode; }
    private static String hex(byte[] data) {
        StringBuilder value = new StringBuilder();
        for (byte item : data) value.append(String.format(Locale.ROOT, "%02x", item & 255));
        return value.toString();
    }
}
