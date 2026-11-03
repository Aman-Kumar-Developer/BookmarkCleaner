package com.aman.bookmark_cleaner.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Encapsulates the complete series of redirect hops from an original URL to the final destination.
 */
public class RedirectChain {

    private final String originalUrl;
    private final String finalUrl;
    private final List<RedirectStep> steps;
    private final RedirectType redirectType;

    public RedirectChain(String originalUrl, String finalUrl, List<RedirectStep> steps, RedirectType redirectType) {
        this.originalUrl = originalUrl;
        this.finalUrl = finalUrl;
        this.steps = steps != null ? Collections.unmodifiableList(new ArrayList<>(steps)) : Collections.emptyList();
        this.redirectType = redirectType != null ? redirectType : RedirectType.NONE;
    }

    public static RedirectChain none(String url) {
        return new RedirectChain(url, url, Collections.emptyList(), RedirectType.NONE);
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getFinalUrl() {
        return finalUrl;
    }

    public List<RedirectStep> getSteps() {
        return steps;
    }

    public RedirectType getRedirectType() {
        return redirectType;
    }

    public int getHopCount() {
        return steps.size();
    }

    public boolean isRedirected() {
        return !steps.isEmpty() || redirectType != RedirectType.NONE;
    }

    public boolean isCrossDomain() {
        return redirectType == RedirectType.CROSS_DOMAIN;
    }

    public boolean isSameWebsite() {
        return redirectType == RedirectType.SAME_WEBSITE;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedirectChain that = (RedirectChain) o;
        return Objects.equals(originalUrl, that.originalUrl) &&
                Objects.equals(finalUrl, that.finalUrl) &&
                Objects.equals(steps, that.steps) &&
                redirectType == that.redirectType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(originalUrl, finalUrl, steps, redirectType);
    }

    @Override
    public String toString() {
        return "RedirectChain{" +
                "originalUrl='" + originalUrl + '\'' +
                ", finalUrl='" + finalUrl + '\'' +
                ", hopCount=" + steps.size() +
                ", redirectType=" + redirectType +
                '}';
    }
}
