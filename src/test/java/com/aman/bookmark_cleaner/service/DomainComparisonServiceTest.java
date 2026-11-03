package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.RedirectType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DomainComparisonServiceTest {

    private DomainComparisonService domainService;

    @BeforeEach
    void setUp() {
        domainService = new DomainComparisonService(new PublicSuffixService());
    }

    @Test
    @DisplayName("Same hostname with different paths classified as SAME_WEBSITE")
    void testSameHostnameDifferentPath() {
        RedirectType type = domainService.classifyRedirect("https://example.com/old-path", "https://example.com/new-path");
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("HTTP to HTTPS on same host classified as SAME_WEBSITE")
    void testHttpToHttps() {
        RedirectType type = domainService.classifyRedirect("http://example.com/docs", "https://example.com/docs");
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("WWW to non-WWW classified as SAME_WEBSITE")
    void testWwwToNonWww() {
        RedirectType type = domainService.classifyRedirect("https://www.example.com/home", "https://example.com/home");
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("Non-WWW to WWW classified as SAME_WEBSITE")
    void testNonWwwToWww() {
        RedirectType type = domainService.classifyRedirect("http://example.com/index", "https://www.example.com/index");
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("Different registrable domains classified as CROSS_DOMAIN")
    void testDifferentRegistrableDomains() {
        RedirectType type = domainService.classifyRedirect("https://example.com/page", "https://different-site.com/page");
        assertEquals(RedirectType.CROSS_DOMAIN, type);
    }

    @Test
    @DisplayName("Different domains under the same multi-part public suffix (.co.uk) classified as CROSS_DOMAIN")
    void testDifferentDomainsUnderSamePublicSuffix() {
        // example.co.uk and another.co.uk are distinct registrable domains
        RedirectType type = domainService.classifyRedirect("https://example.co.uk/blog", "https://another.co.uk/blog");
        assertEquals(RedirectType.CROSS_DOMAIN, type);

        // Same registrable domain with co.uk
        RedirectType sameCoUk = domainService.classifyRedirect("http://example.co.uk/page", "https://www.example.co.uk/page");
        assertEquals(RedirectType.SAME_WEBSITE, sameCoUk);
    }

    @Test
    @DisplayName("Multi-tenant hosting platforms (e.g. github.io) treated as separate registrable domains")
    void testMultiTenantPublicSuffixes() {
        RedirectType type = domainService.classifyRedirect("https://alice.github.io/project", "https://bob.github.io/project");
        assertEquals(RedirectType.CROSS_DOMAIN, type);
    }

    @Test
    @DisplayName("Different subdomains classified as CROSS_DOMAIN to require user review")
    void testDifferentSubdomains() {
        RedirectType type = domainService.classifyRedirect("https://blog.example.com/post", "https://shop.example.com/item");
        assertEquals(RedirectType.CROSS_DOMAIN, type);
    }

    @Test
    @DisplayName("Default port normalization: http:80 and https:443 classified as SAME_WEBSITE")
    void testDefaultPortNormalization() {
        RedirectType type1 = domainService.classifyRedirect("http://example.com:80/a", "http://example.com/a");
        assertEquals(RedirectType.SAME_WEBSITE, type1);

        RedirectType type2 = domainService.classifyRedirect("http://example.com:80/a", "https://example.com:443/a");
        assertEquals(RedirectType.SAME_WEBSITE, type2);
    }

    @Test
    @DisplayName("Internationalized domain names normalized and compared accurately")
    void testInternationalizedDomainNames() {
        // München -> xn--mnchen-3ya.de
        String unicodeUrl = "https://münchen.de/info";
        String punycodeUrl = "https://xn--mnchen-3ya.de/info";

        RedirectType type = domainService.classifyRedirect(unicodeUrl, punycodeUrl);
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("Trailing dots in hostnames handled cleanly")
    void testTrailingDotHostnames() {
        RedirectType type = domainService.classifyRedirect("https://example.com./page", "https://example.com/page");
        assertEquals(RedirectType.SAME_WEBSITE, type);
    }

    @Test
    @DisplayName("Invalid or null URLs produce NONE or CROSS_DOMAIN safely without crashing")
    void testInvalidUrlsHandledSafely() {
        assertEquals(RedirectType.NONE, domainService.classifyRedirect(null, "https://example.com"));
        assertEquals(RedirectType.NONE, domainService.classifyRedirect("https://example.com", null));
        assertEquals(RedirectType.CROSS_DOMAIN, domainService.classifyRedirect("not-a-url", "https://example.com"));
    }
}
