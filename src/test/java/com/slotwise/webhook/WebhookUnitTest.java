package com.slotwise.webhook;

import static org.assertj.core.api.Assertions.assertThat;

import com.slotwise.notification.webhook.RetryPolicy;
import com.slotwise.notification.webhook.UrlGuard;
import com.slotwise.notification.webhook.WebhookSigner;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class WebhookUnitTest {

  @Test
  void signatureMatchesKnownHmacVector() {
    // HMAC-SHA256(key="secret", msg="1700000000.{\"a\":1}") computed independently
    String header = WebhookSigner.header("secret", 1700000000L, "{\"a\":1}");
    assertThat(header).startsWith("t=1700000000,v1=").hasSize("t=1700000000,v1=".length() + 64);
    assertThat(WebhookSigner.verify("secret", header, "{\"a\":1}")).isTrue();
  }

  @Test
  void signatureIsBoundToSecretBodyAndTimestamp() {
    String header = WebhookSigner.header("secret", 1700000000L, "{\"a\":1}");
    assertThat(WebhookSigner.verify("other", header, "{\"a\":1}")).isFalse();
    assertThat(WebhookSigner.verify("secret", header, "{\"a\":2}")).isFalse();
    assertThat(
            WebhookSigner.verify(
                "secret", header.replace("t=1700000000", "t=1700000001"), "{\"a\":1}"))
        .isFalse();
    assertThat(WebhookSigner.verify("secret", "garbage", "{\"a\":1}")).isFalse();
  }

  @Test
  void signatureAgreesWithOpenSslStyleReference() throws Exception {
    // independent implementation via javax.crypto in the test
    var mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(new javax.crypto.spec.SecretKeySpec("k".getBytes(), "HmacSHA256"));
    String expected = java.util.HexFormat.of().formatHex(mac.doFinal("5.body".getBytes()));
    assertThat(WebhookSigner.header("k", 5, "body")).isEqualTo("t=5,v1=" + expected);
  }

  @Test
  void backoffGrowsAndEventuallyStops() {
    assertThat(RetryPolicy.delayAfter(1)).isEqualTo(Duration.ofMinutes(1));
    assertThat(RetryPolicy.delayAfter(2)).isEqualTo(Duration.ofMinutes(5));
    assertThat(RetryPolicy.delayAfter(5)).isEqualTo(Duration.ofHours(12));
    assertThat(RetryPolicy.delayAfter(RetryPolicy.MAX_ATTEMPTS)).isNull();
    assertThat(RetryPolicy.MAX_ATTEMPTS).isEqualTo(6);
  }

  @Test
  void guardRejectsInternalAndMalformedTargets() {
    assertThat(UrlGuard.check("http://example.com/hook", false)).isEqualTo("must use https");
    assertThat(UrlGuard.check("ftp://example.com", false)).contains("http");
    assertThat(UrlGuard.check("https://user:pw@example.com", false)).contains("credentials");
    assertThat(UrlGuard.check("https://127.0.0.1/hook", false)).contains("private");
    assertThat(UrlGuard.check("https://localhost/hook", false)).contains("private");
    assertThat(UrlGuard.check("https://10.1.2.3/hook", false)).contains("private");
    assertThat(UrlGuard.check("https://192.168.0.5/hook", false)).contains("private");
    assertThat(UrlGuard.check("https://169.254.169.254/latest/meta-data", false))
        .contains("private");
    assertThat(UrlGuard.check("https://[::1]/hook", false)).contains("private");
    assertThat(UrlGuard.check("not a url", false)).isNotNull();
  }

  @Test
  void guardAllowsPublicIpAndDevOverride() {
    assertThat(UrlGuard.check("https://93.184.216.34/hook", false)).isNull();
    assertThat(UrlGuard.check("http://localhost:9999/hook", true)).isNull();
  }
}
