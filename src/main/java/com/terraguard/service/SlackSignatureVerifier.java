package com.terraguard.service;

import com.terraguard.config.AppProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class SlackSignatureVerifier {

    private final AppProperties props;

    public SlackSignatureVerifier(AppProperties props) {
        this.props = props;
    }

    /**
     * Verifies X-Slack-Signature per Slack's spec:
     * sig = 'v0=' + HMAC_SHA256(signing_secret, 'v0:' + timestamp + ':' + rawBody)
     * Reject requests older than 5 minutes to prevent replay attacks.
     */
    public boolean isValid(String timestamp, String rawBody, String slackSignature) {
        try {
            long ts = Long.parseLong(timestamp);
            long now = System.currentTimeMillis() / 1000;
            if (Math.abs(now - ts) > 60 * 5) {
                return false;
            }

            String baseString = "v0:" + timestamp + ":" + rawBody;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    props.getSlack().getSigningSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            String computed = "v0=" + bytesToHex(hash);

            return MessageDigest.isEqual(
                    computed.getBytes(StandardCharsets.UTF_8),
                    slackSignature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            return false;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
