package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.dto.BrowserDiscoveryResponse;
import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.service.BrowserDiscoveryService;
import com.aman.bookmark_cleaner.service.BrowserImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for automatic browser discovery and direct bookmark import.
 */
@RestController
@RequestMapping("/api/browsers")
public class BrowserDiscoveryController {

    private final BrowserDiscoveryService discoveryService;
    private final BrowserImportService importService;

    @Autowired
    public BrowserDiscoveryController(BrowserDiscoveryService discoveryService,
                                      BrowserImportService importService) {
        this.discoveryService = discoveryService;
        this.importService = importService;
    }

    /**
     * Discovers supported installed browsers and available profiles containing readable bookmark files.
     */
    @GetMapping
    public ResponseEntity<BrowserDiscoveryResponse> discoverBrowsers() {
        BrowserDiscoveryResponse response = discoveryService.discoverBrowsers();
        return ResponseEntity.ok(response);
    }

    /**
     * Imports bookmarks directly from a selected browser and profile into an active scan session.
     *
     * @param browserId the browser identifier (e.g. "chrome", "edge", "brave")
     * @param profileId the profile identifier (e.g. "chrome-default", "Default", "Personal")
     * @return the {@link UploadResponse} compatible with the existing upload workflow
     */
    @PostMapping("/{browserId}/profiles/{profileId}/import")
    public ResponseEntity<UploadResponse> importBookmarks(
            @PathVariable("browserId") String browserId,
            @PathVariable("profileId") String profileId) {

        UploadResponse response = importService.importBookmarks(browserId, profileId);
        return ResponseEntity.ok(response);
    }
}
