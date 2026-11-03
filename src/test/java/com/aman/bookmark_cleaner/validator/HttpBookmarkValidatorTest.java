package com.aman.bookmark_cleaner.validator;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.security.SsrfProtectionService;
import com.aman.bookmark_cleaner.service.DomainComparisonService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpBookmarkValidatorTest {

    private static HttpServer server;
    private static int port;
    private static String baseUrl;

    private static HttpBookmarkValidator validator;
    private static BookmarkValidationProperties properties;

    @BeforeAll
    static void startServer() throws IOException {
        // Start lightweight mock HTTP server on an ephemeral loopback port
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        baseUrl = "http://127.0.0.1:" + port;

        // 200 OK handler
        server.createContext("/ok-200", exchange -> {
            byte[] response = "OK".getBytes();
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        });

        // 204 No Content handler
        server.createContext("/ok-204", exchange -> {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });

        // 301 Redirect -> /ok-200
        server.createContext("/redirect-301", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/ok-200");
            exchange.sendResponseHeaders(301, -1);
            exchange.close();
        });

        // 302 Found -> /ok-200
        server.createContext("/redirect-302", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/ok-200");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // 307 Temporary Redirect -> /ok-200
        server.createContext("/redirect-307", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/ok-200");
            exchange.sendResponseHeaders(307, -1);
            exchange.close();
        });

        // 308 Permanent Redirect -> /ok-200
        server.createContext("/redirect-308", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/ok-200");
            exchange.sendResponseHeaders(308, -1);
            exchange.close();
        });

        // 404 Not Found
        server.createContext("/broken-404", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        // 410 Gone
        server.createContext("/broken-410", exchange -> {
            exchange.sendResponseHeaders(410, -1);
            exchange.close();
        });

        // 401 Unauthorized
        server.createContext("/restricted-401", exchange -> {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });

        // 403 Forbidden
        server.createContext("/restricted-403", exchange -> {
            exchange.sendResponseHeaders(403, -1);
            exchange.close();
        });

        // 429 Too Many Requests
        server.createContext("/rate-limited-429", exchange -> {
            exchange.sendResponseHeaders(429, -1);
            exchange.close();
        });

        // 500 Server Error
        server.createContext("/error-500", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        // 302 Missing Location Header
        server.createContext("/missing-location", exchange -> {
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // Redirect Loop: /loop-a -> /loop-b -> /loop-a
        server.createContext("/loop-a", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/loop-b");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/loop-b", exchange -> {
            exchange.getResponseHeaders().set("Location", baseUrl + "/loop-a");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // Excessive Redirects: /hop-1 -> /hop-2 -> ... -> /hop-12
        for (int i = 1; i <= 12; i++) {
            final int hop = i;
            server.createContext("/hop-" + hop, exchange -> {
                exchange.getResponseHeaders().set("Location", baseUrl + "/hop-" + (hop + 1));
                exchange.sendResponseHeaders(302, -1);
                exchange.close();
            });
        }

        // HEAD Rejection (405) with GET Fallback (200)
        server.createContext("/head-rejected", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
            } else {
                byte[] resp = "GET works".getBytes();
                exchange.sendResponseHeaders(200, resp.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(resp);
                }
            }
            exchange.close();
        });

        // HEAD Rejection (405) with large streaming GET body to test bounded read
        server.createContext("/large-stream", exchange -> {
            if ("HEAD".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
                return;
            }
            exchange.sendResponseHeaders(200, 0); // chunked transfer
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] chunk = new byte[8192];
                for (int i = 0; i < 32; i++) {
                    os.write(chunk);
                    os.flush();
                }
            } catch (IOException ignored) {
                // Client subscription canceled/closed early, as expected
            }
        });

        server.setExecutor(null);
        server.start();

        // Setup validator with allowLoopbackForTesting enabled for the mock server
        properties = new BookmarkValidationProperties();
        properties.setAllowLoopbackForTesting(true);
        properties.setRequestTimeout(Duration.ofSeconds(3));
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setMaxRedirects(5);
        properties.setMaxRetries(1);
        properties.setRetryBackoff(Duration.ofMillis(50));

        SsrfProtectionService ssrfService = new SsrfProtectionService(properties);
        DomainComparisonService domainService = new DomainComparisonService();

        validator = new HttpBookmarkValidator(properties, ssrfService, domainService);
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("HTTP 200 OK is classified as WORKING")
    void testHttp200IsWorking() {
        ValidationResult result = validator.validate(baseUrl + "/ok-200");
        assertEquals(ValidationOutcome.WORKING, result.getOutcome());
        assertEquals(200, result.getHttpStatusCode());
        assertFalse(result.isRedirected());
    }

    @Test
    @DisplayName("HTTP 204 No Content is classified as WORKING")
    void testHttp204IsWorking() {
        ValidationResult result = validator.validate(baseUrl + "/ok-204");
        assertEquals(ValidationOutcome.WORKING, result.getOutcome());
        assertEquals(204, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("HTTP 301 redirect is classified as REDIRECTED with complete chain")
    void testHttp301IsRedirected() {
        ValidationResult result = validator.validate(baseUrl + "/redirect-301");
        assertEquals(ValidationOutcome.REDIRECTED, result.getOutcome());
        assertEquals(baseUrl + "/redirect-301", result.getOriginalUrl());
        assertEquals(baseUrl + "/ok-200", result.getFinalUrl());
        assertEquals(1, result.getRedirectChain().getHopCount());
        assertEquals(301, result.getRedirectChain().getSteps().get(0).getStatusCode());
    }

    @Test
    @DisplayName("HTTP 302, 307, 308 redirects are classified as REDIRECTED")
    void testOtherRedirectCodes() {
        assertEquals(ValidationOutcome.REDIRECTED, validator.validate(baseUrl + "/redirect-302").getOutcome());
        assertEquals(ValidationOutcome.REDIRECTED, validator.validate(baseUrl + "/redirect-307").getOutcome());
        assertEquals(ValidationOutcome.REDIRECTED, validator.validate(baseUrl + "/redirect-308").getOutcome());
    }

    @Test
    @DisplayName("HTTP 404 Not Found is classified as BROKEN")
    void testHttp404IsBroken() {
        ValidationResult result = validator.validate(baseUrl + "/broken-404");
        assertEquals(ValidationOutcome.BROKEN, result.getOutcome());
        assertEquals(404, result.getHttpStatusCode());
        assertTrue(result.getMessage().contains("404"));
    }

    @Test
    @DisplayName("HTTP 410 Gone is classified as BROKEN")
    void testHttp410IsBroken() {
        ValidationResult result = validator.validate(baseUrl + "/broken-410");
        assertEquals(ValidationOutcome.BROKEN, result.getOutcome());
        assertEquals(410, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("HTTP 401 Unauthorized is classified as UNKNOWN (Access Restricted)")
    void testHttp401IsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/restricted-401");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertEquals(401, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("HTTP 403 Forbidden is classified as UNKNOWN (Access Restricted)")
    void testHttp403IsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/restricted-403");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertEquals(403, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("HTTP 429 Rate Limited is classified as UNKNOWN without aggressive retrying")
    void testHttp429IsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/rate-limited-429");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertEquals(429, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("HTTP 500 Server Error is classified as UNKNOWN")
    void testHttp500IsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/error-500");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertEquals(500, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("Redirect with missing Location header produces UNKNOWN")
    void testMissingLocationHeaderIsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/missing-location");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertTrue(result.getMessage().contains("missing Location"));
    }

    @Test
    @DisplayName("Redirect loop produces UNKNOWN with loop detection message")
    void testRedirectLoopIsUnknown() {
        ValidationResult result = validator.validate(baseUrl + "/loop-a");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertTrue(result.getMessage().toLowerCase().contains("loop"));
    }

    @Test
    @DisplayName("Exceeding maximum redirect limit produces UNKNOWN")
    void testMaxRedirectsExceeded() {
        ValidationResult result = validator.validate(baseUrl + "/hop-1");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertTrue(result.getMessage().contains("Maximum redirect hops exceeded"));
    }

    @Test
    @DisplayName("HEAD rejection (405) falls back to GET and succeeds with WORKING")
    void testHeadRejectionGetFallback() {
        ValidationResult result = validator.validate(baseUrl + "/head-rejected");
        assertEquals(ValidationOutcome.WORKING, result.getOutcome());
        assertEquals(200, result.getHttpStatusCode());
    }

    @Test
    @DisplayName("Unsupported URL scheme (e.g. javascript:, file:) produces UNKNOWN")
    void testUnsupportedScheme() {
        ValidationResult jsResult = validator.validate("javascript:void(0)");
        assertEquals(ValidationOutcome.UNKNOWN, jsResult.getOutcome());
        assertTrue(jsResult.getMessage().contains("Unsupported URL scheme"));

        ValidationResult fileResult = validator.validate("file:///etc/hosts");
        assertEquals(ValidationOutcome.UNKNOWN, fileResult.getOutcome());
    }

    @Test
    @DisplayName("Private address target blocked by SSRF produces UNKNOWN, never BROKEN")
    void testPrivateTargetBlockedProducesUnknown() {
        ValidationResult result = validator.validate("http://10.0.0.1/admin");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertTrue(result.getMessage().contains("blocked by SSRF policy"));
    }

    @Test
    @DisplayName("Connection refused produces UNKNOWN, never BROKEN")
    void testConnectionRefusedProducesUnknown() {
        // Unused local port
        ValidationResult result = validator.validate("http://127.0.0.1:59999/down");
        assertEquals(ValidationOutcome.UNKNOWN, result.getOutcome());
        assertTrue(result.getMessage().contains("Connection failed"));
    }

    @Test
    @DisplayName("GET fallback on large stream is safely bounded and reports WORKING")
    void testLargeStreamBounded() {
        ValidationResult result = validator.validate(baseUrl + "/large-stream");
        assertEquals(ValidationOutcome.WORKING, result.getOutcome());
        assertEquals(200, result.getHttpStatusCode());
    }
}
