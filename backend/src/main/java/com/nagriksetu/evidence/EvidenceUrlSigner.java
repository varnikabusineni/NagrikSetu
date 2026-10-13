package com.nagriksetu.evidence;

import com.nagriksetu.config.NagrikProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Issues short-lived, user-bound URLs for evidence images so browsers can load them in {@code <img>}
 * tags without exposing the session token. The content endpoint re-checks the bound user's permission
 * on every request, so a leaked URL is useless to anyone else and expires within minutes.
 */
@Component
public class EvidenceUrlSigner {

    private final byte[] key;
    private final long ttlSeconds;

    public EvidenceUrlSigner(NagrikProperties properties) {
        this.key = hmac(properties.security().jwtSecret().getBytes(StandardCharsets.UTF_8),
                "nagrik-setu/evidence-url/v1");
        this.ttlSeconds = properties.security().evidenceUrlTtlSeconds();
    }

    public String signedUrl(UUID evidenceId, long userId) {
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        // Round expiry up to the minute so repeated requests produce cacheable, identical URLs.
        exp = ((exp / 60) + 1) * 60;
        return "/api/evidence/" + evidenceId + "/content?u=" + userId + "&exp=" + exp + "&sig=" + sign(evidenceId, userId, exp);
    }

    /** @return true when the signature matches and has not expired. */
    public boolean verify(UUID evidenceId, long userId, long exp, String signature) {
        if (signature == null || exp < Instant.now().getEpochSecond()) {
            return false;
        }
        byte[] expected = sign(evidenceId, userId, exp).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }

    private String sign(UUID evidenceId, long userId, long exp) {
        byte[] mac = hmac(key, evidenceId + ":" + userId + ":" + exp);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac);
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }
}
