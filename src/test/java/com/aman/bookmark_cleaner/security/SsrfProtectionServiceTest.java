package com.aman.bookmark_cleaner.security;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SsrfProtectionServiceTest {

    private SsrfProtectionService ssrfService;
    private BookmarkValidationProperties properties;

    @BeforeEach
    void setUp() {
        properties = new BookmarkValidationProperties();
        properties.setAllowLoopbackForTesting(false); // Strict production SSRF by default
        properties.setSsrfProtectionEnabled(true);
        ssrfService = new SsrfProtectionService(properties);
    }

    @Test
    @DisplayName("Public internet domain URLs are permitted")
    void testPublicInternetUrlsPermitted() {
        assertTrue(ssrfService.isPermitted(URI.create("https://example.com/page")));
        assertTrue(ssrfService.isPermitted(URI.create("https://www.google.com")));
        assertTrue(ssrfService.isPermitted(URI.create("http://spring.io/projects")));
    }

    @Test
    @DisplayName("Localhost and .localhost domains are strictly blocked")
    void testLocalhostBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://localhost:8080/admin")));
        assertFalse(ssrfService.isPermitted(URI.create("https://localhost/api")));
        assertFalse(ssrfService.isPermitted(URI.create("http://test.localhost/app")));
    }

    @Test
    @DisplayName("IPv4 loopback addresses (127.0.0.0/8) are strictly blocked")
    void testIpv4LoopbackBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://127.0.0.1/status")));
        assertFalse(ssrfService.isPermitted(URI.create("http://127.0.0.2:8000/")));
        assertFalse(ssrfService.isPermitted(URI.create("http://127.255.255.254/secret")));
    }

    @Test
    @DisplayName("IPv6 loopback addresses ([::1]) are strictly blocked")
    void testIpv6LoopbackBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://[::1]:8080/debug")));
        assertFalse(ssrfService.isPermitted(URI.create("https://[0:0:0:0:0:0:0:1]/api")));
    }

    @Test
    @DisplayName("Private IPv4 range 10.0.0.0/8 is strictly blocked")
    void testPrivateIpv4ClassABlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://10.0.0.1/internal")));
        assertFalse(ssrfService.isPermitted(URI.create("http://10.254.10.5:9000/metrics")));
    }

    @Test
    @DisplayName("Private IPv4 range 172.16.0.0/12 is strictly blocked")
    void testPrivateIpv4ClassBBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://172.16.0.1/dashboard")));
        assertFalse(ssrfService.isPermitted(URI.create("http://172.20.100.5/api")));
        assertFalse(ssrfService.isPermitted(URI.create("http://172.31.255.254/admin")));
    }

    @Test
    @DisplayName("Private IPv4 range 192.168.0.0/16 is strictly blocked")
    void testPrivateIpv4ClassCBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://192.168.1.1/router")));
        assertFalse(ssrfService.isPermitted(URI.create("http://192.168.0.100:3000/")));
    }

    @Test
    @DisplayName("Carrier-grade NAT (100.64.0.0/10) is strictly blocked")
    void testCgnatBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://100.64.0.1/cgnat")));
        assertFalse(ssrfService.isPermitted(URI.create("http://100.127.255.254/cgnat")));
    }

    @Test
    @DisplayName("Link-local IPv4 range (169.254.0.0/16) is strictly blocked")
    void testLinkLocalIpv4Blocked() {
        assertFalse(ssrfService.isPermitted(URI.create("http://169.254.1.1/link-local")));
    }

    @Test
    @DisplayName("Cloud metadata endpoints are strictly blocked")
    void testCloudMetadataBlocked() {
        // AWS, Azure, GCP metadata IP
        assertFalse(ssrfService.isPermitted(URI.create("http://169.254.169.254/latest/meta-data/")));
        // GCP metadata DNS
        assertFalse(ssrfService.isPermitted(URI.create("http://metadata.google.internal/computeMetadata/v1/")));
        // Generic cloud metadata DNS
        assertFalse(ssrfService.isPermitted(URI.create("http://metadata.internal/info")));
        // Alibaba metadata IP
        assertFalse(ssrfService.isPermitted(URI.create("http://100.100.100.200/latest/meta-data/")));
    }

    @Test
    @DisplayName("Non-HTTP and non-HTTPS URL schemes are rejected")
    void testNonHttpSchemesBlocked() {
        assertFalse(ssrfService.isPermitted(URI.create("file:///etc/passwd")));
        assertFalse(ssrfService.isPermitted(URI.create("javascript:alert(1)")));
        assertFalse(ssrfService.isPermitted(URI.create("ftp://ftp.example.com")));
        assertFalse(ssrfService.isPermitted(URI.create("data:text/html,hello")));
    }

    @Test
    @DisplayName("When allowLoopbackForTesting is true, loopback is allowed but private/metadata remain blocked")
    void testAllowLoopbackForTestingConfiguration() {
        properties.setAllowLoopbackForTesting(true);

        assertTrue(ssrfService.isPermitted(URI.create("http://127.0.0.1:8080/test")));
        assertTrue(ssrfService.isPermitted(URI.create("http://localhost:3000/test")));

        // Private networks and cloud metadata must STILL remain blocked!
        assertFalse(ssrfService.isPermitted(URI.create("http://10.0.0.1/private")));
        assertFalse(ssrfService.isPermitted(URI.create("http://192.168.1.1/router")));
        assertFalse(ssrfService.isPermitted(URI.create("http://169.254.169.254/latest/meta-data/")));
    }
}
