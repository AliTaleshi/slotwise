package com.slotwise.notification.webhook;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Keeps owner-supplied webhook URLs from reaching internal infrastructure (SSRF). Checked when an
 * endpoint is registered and again before every delivery, since DNS can change.
 */
public final class UrlGuard {

  private UrlGuard() {}

  /**
   * @return null if acceptable, otherwise the reason it is rejected
   */
  public static String check(String url, boolean allowPrivateTargets) {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      return "not a valid URL";
    }
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equals("https") || scheme.equals("http"))) {
      return "must use http or https";
    }
    if (uri.getHost() == null) {
      return "must include a host";
    }
    if (uri.getUserInfo() != null) {
      return "must not contain credentials";
    }
    if (allowPrivateTargets) {
      return null;
    }
    if (!scheme.equals("https")) {
      return "must use https";
    }
    try {
      for (InetAddress a : InetAddress.getAllByName(uri.getHost())) {
        if (a.isLoopbackAddress()
            || a.isAnyLocalAddress()
            || a.isSiteLocalAddress()
            || a.isLinkLocalAddress()
            || a.isMulticastAddress()
            || isUniqueLocalIpv6(a)) {
          return "must not point to a private or internal address";
        }
      }
    } catch (UnknownHostException e) {
      return "host cannot be resolved";
    }
    return null;
  }

  private static boolean isUniqueLocalIpv6(InetAddress a) {
    byte[] b = a.getAddress();
    return b.length == 16 && (b[0] & 0xFE) == 0xFC;
  }
}
