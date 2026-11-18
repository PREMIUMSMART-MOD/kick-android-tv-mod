package eu.kickmod.patcher;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Metadane dostarczane razem ze sprawdzonym profilem w podpisanej aplikacji. */
final class SupportProfile {
    final String version, downloadUrl, identity;
    final int versionCode;

    private SupportProfile(JSONObject json, String identity) throws Exception {
        this.identity = identity;
        version = json.getString("version");
        versionCode = json.getInt("versionCode");
        downloadUrl = json.getString("apkmirrorUrl");
        URL url = new URL(downloadUrl);
        if (!version.matches("[0-9]+(?:\\.[0-9]+){1,3}") || versionCode <= 0
            || !url.getProtocol().equals("https") || !url.getHost().equals("www.apkmirror.com"))
            throw new IOException("Invalid bundled support metadata.");
    }

    static SupportProfile load(Context context) {
        try (InputStream in = context.getAssets().open("support.json")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PatchEngine.transfer(in, out, 65536, new AtomicBoolean());
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream profile = new java.security.DigestInputStream(context.getAssets().open("profile/profile.json"), digest)) {
                PatchEngine.transfer(profile, null, 2 * 1024 * 1024, new AtomicBoolean());
            }
            StringBuilder identity = new StringBuilder();
            for (byte value : digest.digest()) identity.append(String.format(Locale.ROOT, "%02x", value & 255));
            return new SupportProfile(new JSONObject(out.toString("UTF-8")), identity.toString());
        } catch (Exception error) { throw new IllegalStateException("Cannot read the bundled support profile.", error); }
    }

    String outputName() { return "Kick-Android-TV-MOD-" + version + ".apk"; }
}
