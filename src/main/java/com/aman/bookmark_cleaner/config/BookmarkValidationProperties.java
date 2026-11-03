package com.aman.bookmark_cleaner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration properties for URL validation, HTTP requests, concurrency, and security.
 */
@Component
@ConfigurationProperties(prefix = "bookmark.validation")
public class BookmarkValidationProperties {

    /**
     * HTTP connection timeout. Default: 5 seconds.
     */
    private Duration connectTimeout = Duration.ofSeconds(5);

    /**
     * HTTP request timeout. Default: 10 seconds.
     */
    private Duration requestTimeout = Duration.ofSeconds(10);

    /**
     * Maximum number of HTTP redirect hops to follow. Default: 10.
     */
    private int maxRedirects = 10;

    /**
     * Maximum retries for transient HTTP failures (e.g. 5xx). Default: 2.
     */
    private int maxRetries = 2;

    /**
     * Delay before retrying a transient failure. Default: 500 ms.
     */
    private Duration retryBackoff = Duration.ofMillis(500);

    /**
     * Maximum global concurrent HTTP requests. Default: 20.
     */
    private int maxConcurrentRequests = 20;

    /**
     * Maximum concurrent requests allowed against a single host. Default: 4.
     */
    private int maxConcurrentRequestsPerHost = 4;

    /**
     * User-Agent header for HTTP requests.
     */
    private String userAgent = "BookmarkCleaner/1.0 (+https://github.com/aman-a25/BookmarkCleaner)";

    /**
     * Whether SSRF protection is enabled. Default: true.
     */
    private boolean ssrfProtectionEnabled = true;

    /**
     * Whether loopback addresses (127.0.0.1, localhost) are permitted for mock testing.
     * Must remain false in production. Default: false.
     */
    private boolean allowLoopbackForTesting = false;

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public Duration getRetryBackoff() {
        return retryBackoff;
    }

    public void setRetryBackoff(Duration retryBackoff) {
        this.retryBackoff = retryBackoff;
    }

    public int getMaxConcurrentRequests() {
        return maxConcurrentRequests;
    }

    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        this.maxConcurrentRequests = maxConcurrentRequests;
    }

    public int getMaxConcurrentRequestsPerHost() {
        return maxConcurrentRequestsPerHost;
    }

    public void setMaxConcurrentRequestsPerHost(int maxConcurrentRequestsPerHost) {
        this.maxConcurrentRequestsPerHost = maxConcurrentRequestsPerHost;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public boolean isSsrfProtectionEnabled() {
        return ssrfProtectionEnabled;
    }

    public void setSsrfProtectionEnabled(boolean ssrfProtectionEnabled) {
        this.ssrfProtectionEnabled = ssrfProtectionEnabled;
    }

    public boolean isAllowLoopbackForTesting() {
        return allowLoopbackForTesting;
    }

    public void setAllowLoopbackForTesting(boolean allowLoopbackForTesting) {
        this.allowLoopbackForTesting = allowLoopbackForTesting;
    }
}
