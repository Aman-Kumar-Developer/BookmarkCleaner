package com.aman.bookmark_cleaner.model;

import java.util.Objects;

/**
 * Details of an individual redirect hop within a redirect chain.
 */
public class RedirectStep {

    private final int statusCode;
    private final String fromUrl;
    private final String toUrl;
    private final String locationHeader;

    public RedirectStep(int statusCode, String fromUrl, String toUrl, String locationHeader) {
        this.statusCode = statusCode;
        this.fromUrl = fromUrl;
        this.toUrl = toUrl;
        this.locationHeader = locationHeader;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getFromUrl() {
        return fromUrl;
    }

    public String getToUrl() {
        return toUrl;
    }

    public String getLocationHeader() {
        return locationHeader;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RedirectStep that = (RedirectStep) o;
        return statusCode == that.statusCode &&
                Objects.equals(fromUrl, that.fromUrl) &&
                Objects.equals(toUrl, that.toUrl) &&
                Objects.equals(locationHeader, that.locationHeader);
    }

    @Override
    public int hashCode() {
        return Objects.hash(statusCode, fromUrl, toUrl, locationHeader);
    }

    @Override
    public String toString() {
        return "RedirectStep{" +
                "statusCode=" + statusCode +
                ", fromUrl='" + fromUrl + '\'' +
                ", toUrl='" + toUrl + '\'' +
                '}';
    }
}
