package com.aman.bookmark_cleaner.dto;

import java.util.Collections;
import java.util.List;

/**
 * Top-level response DTO for the browser discovery endpoint {@code GET /api/browsers}.
 */
public class BrowserDiscoveryResponse {

    private final List<BrowserInfoDto> browsers;

    public BrowserDiscoveryResponse(List<BrowserInfoDto> browsers) {
        this.browsers = browsers != null ? Collections.unmodifiableList(browsers) : Collections.emptyList();
    }

    public List<BrowserInfoDto> getBrowsers() {
        return browsers;
    }
}
