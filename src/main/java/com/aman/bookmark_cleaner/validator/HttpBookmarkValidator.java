package com.aman.bookmark_cleaner.validator;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.model.RedirectChain;
import com.aman.bookmark_cleaner.model.RedirectStep;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.security.SsrfProtectionService;
import com.aman.bookmark_cleaner.service.DomainComparisonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLHandshakeException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 * HTTP-based implementation of {@link BookmarkValidator}.
 * Uses Java 21's {@link HttpClient} with controlled redirect following,
 * HEAD preference with GET fallback, SSRF pre-validation, bounded retries,
 * and structured outcome classification.
 */
@Component
public class HttpBookmarkValidator implements BookmarkValidator {

    private final HttpClient httpClient;
    private final BookmarkValidationProperties properties;
    private final SsrfProtectionService ssrfProtectionService;
    private final DomainComparisonService domainComparisonService;

    @Autowired
    public HttpBookmarkValidator(BookmarkValidationProperties properties,
                                 SsrfProtectionService ssrfProtectionService,
                                 DomainComparisonService domainComparisonService) {
        this(createDefaultHttpClient(properties), properties, ssrfProtectionService, domainComparisonService);
    }

    public HttpBookmarkValidator(HttpClient httpClient,
                                 BookmarkValidationProperties properties,
                                 SsrfProtectionService ssrfProtectionService,
                                 DomainComparisonService domainComparisonService) {
        this.properties = properties != null ? properties : new BookmarkValidationProperties();
        this.httpClient = httpClient != null ? httpClient : createDefaultHttpClient(this.properties);
        this.ssrfProtectionService = ssrfProtectionService != null ? ssrfProtectionService : new SsrfProtectionService(this.properties);
        this.domainComparisonService = domainComparisonService != null ? domainComparisonService : new DomainComparisonService();
    }

    private static HttpClient createDefaultHttpClient(BookmarkValidationProperties props) {
        Duration connectTimeout = (props != null && props.getConnectTimeout() != null)
                ? props.getConnectTimeout()
                : Duration.ofSeconds(5);

        return HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NEVER) // Manual redirect inspection required
                .build();
    }

    @Override
    public ValidationResult validate(String rawUrl) {
        long startTime = System.currentTimeMillis();

        if (rawUrl == null || rawUrl.isBlank()) {
            return ValidationResult.unknown(rawUrl, -1, "Missing or blank URL", 0);
        }

        String originalUrl = rawUrl.trim();
        URI currentUri;
        try {
            currentUri = URI.create(originalUrl);
        } catch (Exception e) {
            return ValidationResult.unknown(originalUrl, -1, "Invalid URL syntax: " + e.getMessage(), 0);
        }

        String scheme = currentUri.getScheme();
        if (scheme == null) {
            return ValidationResult.unknown(originalUrl, -1, "Missing URL scheme", 0);
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        if (!"http".equals(normalizedScheme) && !"https".equals(normalizedScheme)) {
            return ValidationResult.unknown(originalUrl, -1, "Unsupported URL scheme: " + scheme, 0);
        }

        if (currentUri.getHost() == null || currentUri.getHost().isBlank()) {
            return ValidationResult.unknown(originalUrl, -1, "Missing host in URL", 0);
        }

        // Initial SSRF protection check
        if (!ssrfProtectionService.isPermitted(currentUri)) {
            return ValidationResult.unknown(originalUrl, -1, "Target blocked by SSRF policy: " + currentUri.getHost(), 0);
        }

        List<RedirectStep> redirectSteps = new ArrayList<>();
        Set<String> visitedUrls = new HashSet<>();
        String currentUrl = originalUrl;

        int maxRedirects = properties.getMaxRedirects();

        while (true) {
            if (!visitedUrls.add(currentUrl)) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "Redirect loop detected at " + currentUrl, duration);
            }

            HttpResponse<Void> response;
            try {
                response = executeWithFallback(currentUri);
            } catch (HttpTimeoutException e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "Connection/request timed out: " + e.getMessage(), duration);
            } catch (UnknownHostException e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "DNS resolution failure: " + e.getMessage(), duration);
            } catch (ConnectException e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "Connection failed / refused: " + e.getMessage(), duration);
            } catch (SSLHandshakeException e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "TLS/Certificate error: " + e.getMessage(), duration);
            } catch (IOException e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "I/O error during request: " + e.getMessage(), duration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "Request interrupted", duration);
            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                return ValidationResult.unknown(originalUrl, -1, "Unexpected error: " + e.getMessage(), duration);
            }

            int statusCode = response.statusCode();

            // Handle HTTP Redirects (301, 302, 303, 307, 308)
            if (isRedirectStatus(statusCode)) {
                if (redirectSteps.size() >= maxRedirects) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ValidationResult.unknown(originalUrl, statusCode, "Maximum redirect hops exceeded (" + maxRedirects + ")", duration);
                }

                Optional<String> locationHeaderOpt = response.headers().firstValue("Location");
                if (locationHeaderOpt.isEmpty() || locationHeaderOpt.get().isBlank()) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ValidationResult.unknown(originalUrl, statusCode, "Redirect status " + statusCode + " but missing Location header", duration);
                }

                String location = locationHeaderOpt.get().trim();
                URI nextUri;
                try {
                    nextUri = currentUri.resolve(location);
                } catch (Exception e) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ValidationResult.unknown(originalUrl, statusCode, "Invalid redirect destination: " + location, duration);
                }

                String nextScheme = nextUri.getScheme();
                if (nextScheme == null || (!"http".equalsIgnoreCase(nextScheme) && !"https".equalsIgnoreCase(nextScheme))) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ValidationResult.unknown(originalUrl, statusCode, "Redirect to unsupported scheme: " + location, duration);
                }

                // SSRF check on redirect target
                if (!ssrfProtectionService.isPermitted(nextUri)) {
                    long duration = System.currentTimeMillis() - startTime;
                    return ValidationResult.unknown(originalUrl, statusCode, "Redirect destination blocked by SSRF policy: " + nextUri.getHost(), duration);
                }

                String nextUrl = nextUri.toString();
                redirectSteps.add(new RedirectStep(statusCode, currentUrl, nextUrl, location));

                currentUrl = nextUrl;
                currentUri = nextUri;
                continue;
            }

            long duration = System.currentTimeMillis() - startTime;

            // Handle 2xx Success
            if (statusCode >= 200 && statusCode < 300) {
                if (!redirectSteps.isEmpty()) {
                    RedirectType redirectType = domainComparisonService.classifyRedirect(originalUrl, currentUrl);
                    RedirectChain chain = new RedirectChain(originalUrl, currentUrl, redirectSteps, redirectType);
                    return ValidationResult.redirected(originalUrl, currentUrl, statusCode, chain, duration);
                } else {
                    return ValidationResult.working(originalUrl, statusCode, duration);
                }
            }

            // Handle 404 / 410 (Confirmed Broken)
            if (statusCode == 404 || statusCode == 410) {
                String desc = (statusCode == 404) ? "Not Found" : "Gone";
                return ValidationResult.broken(originalUrl, statusCode, "HTTP " + statusCode + " " + desc, duration);
            }

            // Handle 401 / 403 (Access Restricted -> UNKNOWN)
            if (statusCode == 401 || statusCode == 403) {
                String desc = (statusCode == 401) ? "Unauthorized" : "Forbidden";
                return ValidationResult.unknown(originalUrl, statusCode, "HTTP " + statusCode + " " + desc + " (Access Restricted)", duration);
            }

            // Handle 429 (Rate Limited -> UNKNOWN, no aggressive retry)
            if (statusCode == 429) {
                return ValidationResult.unknown(originalUrl, statusCode, "HTTP 429 Too Many Requests (Rate Limited)", duration);
            }

            // Handle 5xx (Server Error -> UNKNOWN)
            if (statusCode >= 500 && statusCode < 600) {
                return ValidationResult.unknown(originalUrl, statusCode, "HTTP " + statusCode + " Server Error", duration);
            }

            // Other 4xx or unusual status codes -> UNKNOWN
            return ValidationResult.unknown(originalUrl, statusCode, "HTTP " + statusCode, duration);
        }
    }

    private HttpResponse<Void> executeWithFallback(URI uri) throws IOException, InterruptedException {
        Duration requestTimeout = properties.getRequestTimeout() != null ? properties.getRequestTimeout() : Duration.ofSeconds(10);
        String userAgent = properties.getUserAgent() != null ? properties.getUserAgent() : "BookmarkCleaner/1.0";

        // 1. Attempt HEAD request
        HttpRequest headRequest = HttpRequest.newBuilder(uri)
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .build();

        try {
            HttpResponse<Void> headResponse = httpClient.send(headRequest, HttpResponse.BodyHandlers.discarding());
            int code = headResponse.statusCode();

            // If HEAD succeeded, or is a redirect, or is confirmed broken (404/410), return it
            if ((code >= 200 && code < 400) || code == 404 || code == 410) {
                return headResponse;
            }

            // If 405 Method Not Allowed, 501 Not Implemented, or 403 (some servers reject HEAD), fall back to GET
        } catch (Exception e) {
            // If HEAD threw exception (e.g. server closed connection on HEAD), try GET fallback
        }

        // 2. Controlled GET fallback with bounded retries for transient 5xx
        HttpRequest getRequest = HttpRequest.newBuilder(uri)
                .GET()
                .timeout(requestTimeout)
                .header("User-Agent", userAgent)
                .header("Range", "bytes=0-1024") // Request minimal body chunk if supported
                .build();

        int attempts = 0;
        int maxRetries = properties.getMaxRetries();
        long retryBackoffMs = properties.getRetryBackoff() != null ? properties.getRetryBackoff().toMillis() : 500;

        while (true) {
            HttpResponse<Void> getResponse = httpClient.send(getRequest, boundedDiscarding(MAX_RESPONSE_BYTES));
            int code = getResponse.statusCode();

            // If 5xx and we have retries remaining, retry with backoff
            if (code >= 500 && code < 600 && attempts < maxRetries) {
                attempts++;
                Thread.sleep(retryBackoffMs);
                continue;
            }

            return getResponse;
        }
    }

    public static final long MAX_RESPONSE_BYTES = 65536; // 64 KB response bandwidth limit

    /**
     * Bounded body subscriber that limits response body consumption to {@code maxBytes}.
     * If the remote server returns more than {@code maxBytes} (e.g. ignoring Range request),
     * the subscription is canceled immediately, closing the underlying connection.
     */
    public static HttpResponse.BodyHandler<Void> boundedDiscarding(long maxBytes) {
        return responseInfo -> new HttpResponse.BodySubscriber<Void>() {
            private Flow.Subscription subscription;
            private final CompletableFuture<Void> future = new CompletableFuture<>();
            private long bytesReceived = 0;

            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                this.subscription = subscription;
                subscription.request(1);
            }

            @Override
            public void onNext(List<ByteBuffer> buffers) {
                if (buffers != null) {
                    for (ByteBuffer buf : buffers) {
                        bytesReceived += buf.remaining();
                    }
                }
                if (bytesReceived >= maxBytes) {
                    subscription.cancel();
                    future.complete(null);
                } else {
                    subscription.request(1);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                future.completeExceptionally(throwable);
            }

            @Override
            public void onComplete() {
                future.complete(null);
            }

            @Override
            public CompletionStage<Void> getBody() {
                return future;
            }
        };
    }

    private boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }
}
