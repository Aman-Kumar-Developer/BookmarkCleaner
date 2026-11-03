package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.RedirectType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;

/**
 * Service responsible for comparing URLs and classifying redirects as
 * SAME_WEBSITE versus CROSS_DOMAIN.
 */
@Service
public class DomainComparisonService {

    private final PublicSuffixService publicSuffixService;

    @Autowired
    public DomainComparisonService(PublicSuffixService publicSuffixService) {
        this.publicSuffixService = publicSuffixService != null ? publicSuffixService : new PublicSuffixService();
    }

    public DomainComparisonService() {
        this(new PublicSuffixService());
    }

    /**
     * Classifies a redirect from an original URL to a destination URL.
     */
    public RedirectType classifyRedirect(String originalUrl, String finalUrl) {
        if (originalUrl == null || finalUrl == null) {
            return RedirectType.NONE;
        }

        String origHost = extractHostFromUrl(originalUrl);
        String finalHost = extractHostFromUrl(finalUrl);

        if (origHost.isEmpty() || finalHost.isEmpty()) {
            return RedirectType.CROSS_DOMAIN;
        }

        // Exact host match (handles same host with different paths, or HTTP -> HTTPS on same host)
        if (origHost.equals(finalHost)) {
            return RedirectType.SAME_WEBSITE;
        }

        // WWW normalization: www.example.com <-> example.com
        String origWithoutWww = stripWww(origHost);
        String finalWithoutWww = stripWww(finalHost);

        if (origWithoutWww.equals(finalWithoutWww)) {
            return RedirectType.SAME_WEBSITE;
        }

        // Check registrable domains via Public Suffix List
        String origRegDomain = publicSuffixService.getRegistrableDomain(origHost);
        String finalRegDomain = publicSuffixService.getRegistrableDomain(finalHost);

        if (!origRegDomain.equalsIgnoreCase(finalRegDomain)) {
            // Distinct registrable domains (e.g. example.co.uk vs another.co.uk, or siteA.com vs siteB.com)
            return RedirectType.CROSS_DOMAIN;
        }

        // If registrable domains match but subdomains differ (and neither is merely www difference),
        // treat as cross-domain / separate service to ensure user review (e.g. blog.example.com vs shop.example.com)
        return RedirectType.CROSS_DOMAIN;
    }

    /**
     * Checks if two URLs belong to the same website.
     */
    public boolean isSameWebsite(String url1, String url2) {
        return classifyRedirect(url1, url2) == RedirectType.SAME_WEBSITE;
    }

    /**
     * Normalizes a host string, removing www prefix if present.
     */
    public String stripWww(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("www.")) {
            return normalized.substring(4);
        }
        return normalized;
    }

    /**
     * Extracts and normalizes the host from a raw URL string, supporting IDN / Punycode.
     */
    public String extractHostFromUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        URI uri = safeParseUri(rawUrl);
        if (uri != null && uri.getHost() != null) {
            return publicSuffixService.normalizeHostname(uri.getHost());
        }

        // Fallback for non-ASCII/IDN URLs that standard java.net.URI fails to parse
        String s = rawUrl.trim();
        int schemeIdx = s.indexOf("://");
        if (schemeIdx != -1) {
            s = s.substring(schemeIdx + 3);
        }
        int slashIdx = s.indexOf('/');
        if (slashIdx != -1) {
            s = s.substring(0, slashIdx);
        }
        int qIdx = s.indexOf('?');
        if (qIdx != -1) {
            s = s.substring(0, qIdx);
        }
        int hIdx = s.indexOf('#');
        if (hIdx != -1) {
            s = s.substring(0, hIdx);
        }
        int portIdx = s.lastIndexOf(':');
        if (portIdx != -1 && !s.contains("]")) {
            s = s.substring(0, portIdx);
        }

        return publicSuffixService.normalizeHostname(s);
    }

    /**
     * Extracts and normalizes the host from a URI.
     */
    public String extractAndNormalizeHost(URI uri) {
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        return publicSuffixService.normalizeHostname(uri.getHost());
    }

    /**
     * Gets the registrable domain for a given host or URL.
     */
    public String getRegistrableDomain(String hostOrUrl) {
        if (hostOrUrl == null) {
            return "";
        }
        String host = extractHostFromUrl(hostOrUrl);
        if (host.isEmpty()) {
            host = hostOrUrl;
        }
        return publicSuffixService.getRegistrableDomain(host);
    }

    private URI safeParseUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawUrl.trim());
        } catch (Exception e) {
            try {
                return new URI(rawUrl.trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
