package eu.kickmod.patcher;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import com.android.apksig.ApkSigner;
import com.android.apksig.ApkVerifier;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Date;
import javax.security.auth.x500.X500Principal;

public final class LocalSigner {
    private static final String ALIAS = "kick_mod_signing_v1";

    public static void sign(File unsignedApk, File signedApk) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
            generator.initialize(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                .setKeySize(2048)
                .setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .setCertificateSubject(new X500Principal("CN=Kick Android TV Local Patcher"))
                .setCertificateSerialNumber(BigInteger.ONE)
                .setCertificateNotBefore(new Date(1577836800000L))
                .setCertificateNotAfter(new Date(4102444800000L))
                .setUserAuthenticationRequired(false).build());
            generator.generateKeyPair();
        }
        PrivateKey key = (PrivateKey) store.getKey(ALIAS, null);
        X509Certificate certificate = (X509Certificate) store.getCertificate(ALIAS);
        ApkSigner.SignerConfig config = new ApkSigner.SignerConfig.Builder("local", key,
            Collections.singletonList(certificate)).build();
        new ApkSigner.Builder(Collections.singletonList(config))
            .setInputApk(unsignedApk).setOutputApk(signedApk).setMinSdkVersion(26)
            .setV1SigningEnabled(false).setV2SigningEnabled(true)
            .setV3SigningEnabled(false).setV4SigningEnabled(false)
            .setOtherSignersSignaturesPreserved(false).setAlignmentPreserved(false)
            .setLibraryPageAlignmentBytes(16384).build().sign();
        ApkVerifier.Result verified = new ApkVerifier.Builder(signedApk)
            .setMinCheckedPlatformVersion(26).setMaxCheckedPlatformVersion(35).build().verify();
        if (!verified.isVerified() || !verified.isVerifiedUsingV2Scheme())
            throw new IllegalStateException("APK signature verification failed: " + verified.getErrors());
    }
}
