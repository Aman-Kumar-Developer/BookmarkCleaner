package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.service.ScanOrchestrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller handling bookmark file upload and session initialization.
 */
@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkUploadController {

    private final ScanOrchestrationService orchestrationService;

    @Autowired
    public BookmarkUploadController(ScanOrchestrationService orchestrationService) {
        this.orchestrationService = orchestrationService;
    }

    /**
     * Uploads and parses a Chromium bookmark JSON file, creating an active scan session.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        UploadResponse response = orchestrationService.uploadBookmarkFile(file);
        return ResponseEntity.ok(response);
    }
}
