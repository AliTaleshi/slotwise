package com.slotwise.notification.webhook;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 request signing. The header looks like {@code t=1700000000,v1=<hex>} where the
 * signature covers {@code "<t>." + body}; including the timestamp lets receivers reject replays.
 */
public final class WebhookSigner {

  private WebhookSigner() {}

  public static String header(String secret, long timestampSeconds, String body) {
    return "t=" + timestampSeconds + ",v1=" + hmacHex(secret, timestampSeconds + "." + body);
  }

  /** Receiver-side check, also used by tests. Constant-time comparison. */
  public static boolean verify(String secret, String header, String body) {
    try {
      String t = null;
      String v1 = null;
      for (String part : header.split(",")) {
        String[] kv = part.trim().split("=", 2);
        if (kv.length == 2 && kv[0].equals("t")) t = kv[1];
        if (kv.length == 2 && kv[0].equals("v1")) v1 = kv[1];
      }
      if (t == null || v1 == null) {
        return false;
      }
      String expected = hmacHex(secret, t + "." + body);
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.UTF_8), v1.getBytes(StandardCharsets.UTF_8));
    } catch (RuntimeException e) {
      return false;
    }
  }

  static String hmacHex(String secret, String message) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(e);
    }
  }
}
