package com.aman.bookmark_cleaner.model;

import java.util.Objects;

/**
 * The structured result of validating a bookmark URL.
 */
public class ValidationResult {

    private final String originalUrl;
    private final String finalUrl;
    private final ValidationOutcome outcome;
    private final int httpStatusCode;
    private final RedirectChain redirectChain;
    private final String message;
    private final long responseTimeMs;

    public ValidationResult(String originalUrl,
                            String finalUrl,
                            ValidationOutcome outcome,
                            int httpStatusCode,
                            RedirectChain redirectChain,
                            String message,
                            long responseTimeMs) {
        this.originalUrl = originalUrl;
        this.finalUrl = finalUrl != null ? finalUrl : originalUrl;
        this.outcome = outcome;
        this.httpStatusCode = httpStatusCode;
        this.redirectChain = redirectChain != null ? redirectChain : RedirectChain.none(originalUrl);
        this.message = message;
        this.responseTimeMs = responseTimeMs;
    }

    public static ValidationResult working(String url, int statusCode, long responseTimeMs) {
        return new ValidationResult(url, url, ValidationOutcome.WORKING, statusCode, RedirectChain.none(url), "HTTP " + statusCode + " OK", responseTimeMs);
    }

    public static ValidationResult redirected(String originalUrl, String finalUrl, int statusCode, RedirectChain chain, long responseTimeMs) {
        return new ValidationResult(originalUrl, finalUrl, ValidationOutcome.REDIRECTED, statusCode, chain, "Redirected to " + finalUrl, responseTimeMs);
    }

    public static ValidationResult broken(String url, int statusCode, String message, long responseTimeMs) {
        return new ValidationResult(url, url, ValidationOutcome.BROKEN, statusCode, RedirectChain.none(url), message, responseTimeMs);
    }

    public static ValidationResult unknown(String url, int statusCode, String message, long responseTimeMs) {
        return new ValidationResult(url, url, ValidationOutcome.UNKNOWN, statusCode, RedirectChain.none(url), message, responseTimeMs);
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public ValidationOutcome getOutcome() {
        return outcome;
    }

    public int getHttpStatusCode() {
        return httpStatusCode;
    }

    public RedirectChain getRedirectChain() {
        return redirectChain;
    }

    public String getMessage() {
        return message;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public boolean isWorking() {
        return outcome == ValidationOutcome.WORKING;
    }

    public boolean isRedirected() {
        return outcome == ValidationOutcome.REDIRECTED;
    }

    public boolean isBroken() {
        return outcome == ValidationOutcome.BROKEN;
    }

    public boolean isUnknown() {
        return outcome == ValidationOutcome.UNKNOWN;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValidationResult that = (ValidationResult) o;
        return httpStatusCode == that.httpStatusCode &&
                Objects.equals(originalUrl, that.originalUrl) &&
                Objects.equals(finalUrl, that.finalUrl) &&
                outcome == that.outcome &&
                Objects.equals(redirectChain, that.redirectChain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalUrl, finalUrl, outcome, httpStatusCode, redirectChain);
    }

    @Override
    public String toString() {
        return "ValidationResult{" +
                "outcome=" + outcome +
                ", httpStatusCode=" + httpStatusCode +
                ", originalUrl='" + originalUrl + '\'' +
                ", finalUrl='" + finalUrl + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
