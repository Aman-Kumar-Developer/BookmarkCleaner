package com.aman.bookmark_cleaner.security;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Service providing Server-Side Request Forgery (SSRF) protections.
 * Blocks requests to private networks, loopback addresses, localhosts,
 * link-local addresses, and cloud metadata endpoints.
 */
@Service
public class SsrfProtectionService {

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "metadata.google.internal",
            "metadata.internal",
            "instance-data"
    );

    private final BookmarkValidationProperties properties;

    @Autowired
    public SsrfProtectionService(BookmarkValidationProperties properties) {
        this.properties = properties != null ? properties : new BookmarkValidationProperties();
    }

    public SsrfProtectionService() {
        this(new BookmarkValidationProperties());
    }

    /**
     * Validates whether a target URI is permitted under the active SSRF policy.
     *
     * @param uri the URI to validate
     * @return true if permitted, false if blocked
     */
    public boolean isPermitted(URI uri) {
        if (!properties.isSsrfProtectionEnabled()) {
            return true;
        }

        if (uri == null) {
            return false;
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            return false;
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            return false;
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return false;
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT).trim();
        // Remove trailing dot if present
        if (normalizedHost.endsWith(".")) {
            normalizedHost = normalizedHost.substring(0, normalizedHost.length() - 1);
        }

        // Check against known blocked hostnames
        if (BLOCKED_HOSTNAMES.contains(normalizedHost) || normalizedHost.endsWith(".localhost") || normalizedHost.endsWith(".internal")) {
            if (properties.isAllowLoopbackForTesting() && ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost"))) {
                return true;
            }
            return false;
        }

        // Parse and check host as IP address or resolve via DNS
        try {
            InetAddress[] resolvedAddresses = InetAddress.getAllByName(normalizedHost);
            if (resolvedAddresses.length == 0) {
                return false;
            }

            for (InetAddress address : resolvedAddresses) {
                if (isBlockedAddress(address)) {
                    return false;
                }
            }
        } catch (UnknownHostException e) {
            // Unresolvable host is not an SSRF risk, but will fail normally in HTTP client
            return true;
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    /**
     * Determines whether an IP address is blocked under the SSRF policy.
     */
    public boolean isBlockedAddress(InetAddress address) {
        if (address == null) {
            return true;
        }

        if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
            return !properties.isAllowLoopbackForTesting();
        }

        if (address instanceof Inet4Address ipv4) {
            return isBlockedIpv4(ipv4);
        } else if (address instanceof Inet6Address ipv6) {
            return isBlockedIpv6(ipv6);
        }

        return true;
    }

    private boolean isBlockedIpv4(Inet4Address address) {
        byte[] bytes = address.getAddress();
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        int b2 = bytes[2] & 0xFF;
        int b3 = bytes[3] & 0xFF;

        // Loopback: 127.0.0.0/8
        if (b0 == 127) {
            return !properties.isAllowLoopbackForTesting();
        }

        // Private range: 10.0.0.0/8
        if (b0 == 10) {
            return true;
        }

        // Private range: 172.16.0.0/12 (172.16.0.0 to 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) {
            return true;
        }

        // Private range: 192.168.0.0/16
        if (b0 == 192 && b1 == 168) {
            return true;
        }

        // Carrier-grade NAT: 100.64.0.0/10 (100.64.0.0 to 100.127.255.255)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
            return true;
        }

        // Link-local: 169.254.0.0/16
        if (b0 == 169 && b1 == 254) {
            return true;
        }

        // Broadcast: 255.255.255.255 or 0.0.0.0
        if (b0 == 255 && b1 == 255 && b2 == 255 && b3 == 255) {
            return true;
        }
        if (b0 == 0 && b1 == 0 && b2 == 0 && b3 == 0) {
            return !properties.isAllowLoopbackForTesting();
        }

        // Cloud metadata endpoints: 169.254.169.254, 100.100.100.200 (Alibaba)
        if (b0 == 169 && b1 == 254 && b2 == 169 && b3 == 254) {
            return true;
        }
        if (b0 == 100 && b1 == 100 && b2 == 100 && b3 == 200) {
            return true;
        }

        return false;
    }

    private boolean isBlockedIpv6(Inet6Address address) {
        byte[] bytes = address.getAddress();

        // Loopback: ::1
        if (address.isLoopbackAddress()) {
            return !properties.isAllowLoopbackForTesting();
        }

        // Link-local: fe80::/10 (first byte 0xfe, second byte top 2 bits 10 -> 0x80 to 0xbf)
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
            return true;
        }

        // Unique local address: fc00::/7 (covers fc00:: and fd00::)
        if ((b0 & 0xFE) == 0xFC) {
            return true;
        }

        // IPv4-mapped IPv6 address: ::ffff:a.b.c.d
        if (bytes[0] == 0 && bytes[1] == 0 && bytes[2] == 0 && bytes[3] == 0 &&
                bytes[4] == 0 && bytes[5] == 0 && bytes[6] == 0 && bytes[7] == 0 &&
                bytes[8] == 0 && bytes[9] == 0 && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
            try {
                byte[] ipv4Bytes = new byte[]{bytes[12], bytes[13], bytes[14], bytes[15]};
                Inet4Address ipv4 = (Inet4Address) InetAddress.getByAddress(ipv4Bytes);
                return isBlockedIpv4(ipv4);
            } catch (Exception e) {
                return true;
            }
        }

        return false;
    }
}
