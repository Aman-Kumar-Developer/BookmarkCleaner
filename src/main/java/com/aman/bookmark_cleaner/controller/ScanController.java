package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.dto.ComparisonResultsPageResponse;
import com.aman.bookmark_cleaner.dto.DecisionUpdateRequest;
import com.aman.bookmark_cleaner.dto.DecisionUpdateResponse;
import com.aman.bookmark_cleaner.dto.ExportResponse;
import com.aman.bookmark_cleaner.dto.ScanStatusResponse;
import com.aman.bookmark_cleaner.dto.StartScanRequest;
import com.aman.bookmark_cleaner.service.BookmarkExportService;
import com.aman.bookmark_cleaner.service.DecisionService;
import com.aman.bookmark_cleaner.service.ScanOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing REST endpoints for scan execution, progress inspection,
 * comparison results retrieval, decision updates, and JSON export.
 */
@RestController
@RequestMapping("/api/scans")
public class ScanController {

    private final ScanOrchestrationService orchestrationService;
    private final DecisionService decisionService;
    private final BookmarkExportService exportService;

    @Autowired
    public ScanController(ScanOrchestrationService orchestrationService,
                          DecisionService decisionService,
                          BookmarkExportService exportService) {
        this.orchestrationService = orchestrationService;
        this.decisionService = decisionService;
        this.exportService = exportService;
    }

    /**
     * Starts bookmark analysis for the uploaded session.
     */
    @PostMapping
    public ResponseEntity<ScanStatusResponse> startScan(@RequestBody(required = false) StartScanRequest request,
                                                        @RequestParam(value = "scanId", required = false) String scanIdParam) {
        String targetScanId = (request != null && request.getScanId() != null && !request.getScanId().isBlank())
                ? request.getScanId().trim()
                : scanIdParam;

        if (targetScanId == null || targetScanId.isBlank()) {
            throw new IllegalArgumentException("Scan ID must be provided in request body or query parameter");
        }

        ScanStatusResponse status = orchestrationService.startScan(targetScanId);
        return ResponseEntity.ok(status);
    }

    /**
     * Retrieves current scan progress, status, and summary counts.
     */
    @GetMapping("/{scanId}")
    public ResponseEntity<ScanStatusResponse> getScanStatus(@PathVariable("scanId") String scanId) {
        ScanStatusResponse status = orchestrationService.getScanStatus(scanId);
        return ResponseEntity.ok(status);
    }

    /**
     * Retrieves paginated comparison items with priority filtering and search.
     */
    @GetMapping("/{scanId}/results")
    public ResponseEntity<ComparisonResultsPageResponse> getResults(
            @PathVariable("scanId") String scanId,
            @RequestParam(value = "priority", defaultValue = "ALL") String priority,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {

        ComparisonResultsPageResponse results = orchestrationService.getComparisonResults(scanId, priority, search, page, size);
        return ResponseEntity.ok(results);
    }

    /**
     * Updates user decision (ON/OFF) for a specific comparison item.
     */
    @PatchMapping("/{scanId}/decisions/{itemId}")
    public ResponseEntity<DecisionUpdateResponse> updateDecision(
            @PathVariable("scanId") String scanId,
            @PathVariable("itemId") String itemId,
            @RequestBody DecisionUpdateRequest request) {

        DecisionUpdateResponse response = decisionService.updateDecision(scanId, itemId, request.getEnabled());
        return ResponseEntity.ok(response);
    }

    /**
     * Generates a separate Chromium-compatible cleaned JSON export based on accepted decisions.
     */
    @PostMapping("/{scanId}/export")
    public ResponseEntity<ExportResponse> generateExport(@PathVariable("scanId") String scanId) {
        ExportResponse response = exportService.generateExport(scanId);
        return ResponseEntity.ok(response);
    }

    /**
     * Downloads the generated bookmarks-cleaned.json file.
     */
    @GetMapping("/{scanId}/export")
    public ResponseEntity<byte[]> downloadExport(@PathVariable("scanId") String scanId) {
        byte[] exportBytes = exportService.getExportBytes(scanId);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookmarks-cleaned.json\"")
                .body(exportBytes);
    }
}
