package com.guidinglight.nexusquant.demo.registration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Outbox 只持久化随机身份，服务密钥重建邮件令牌；数据库泄露不能恢复令牌。 */
final class DemoTokenCodec {
    private final byte[] key;
    DemoTokenCodec(String secret) { key=secret.getBytes(StandardCharsets.UTF_8); }
    String token(UUID id) { return id+"."+Base64.getUrlEncoder().withoutPadding().encodeToString(mac("verification:"+id)); }
    String fingerprint(String value) { return HexFormat.of().formatHex(mac("registration:"+value)); }
    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    static boolean same(String left,String right) {
        return MessageDigest.isEqual(left.getBytes(StandardCharsets.UTF_8),right.getBytes(StandardCharsets.UTF_8));
    }
    private byte[] mac(String value) {
        try {
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException failure) {throw new IllegalStateException("verification cryptography unavailable",failure);}
    }
}
