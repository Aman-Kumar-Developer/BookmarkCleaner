package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.ExportResponse;
import com.aman.bookmark_cleaner.exception.ExportException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.ScanSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Service responsible for generating Chromium-compatible JSON exports
 * from scan session decisions.
 */
@Service
public class BookmarkExportService {

    private final BookmarkTreeReconstructionService reconstructionService;
    private final ScanSessionManager sessionManager;
    private final ObjectMapper objectMapper;

    @Autowired
    public BookmarkExportService(BookmarkTreeReconstructionService reconstructionService,
                                 ScanSessionManager sessionManager,
                                 ObjectMapper objectMapper) {
        this.reconstructionService = reconstructionService;
        this.sessionManager = sessionManager;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates the cleaned JSON export for the specified scan session.
     */
    public ExportResponse generateExport(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);

        try {
            BookmarkTreeReconstructionService.ReconstructionResult result = reconstructionService.reconstruct(session);
            BookmarkDocument reconstructedDoc = result.reconstructedDocument();
            int changesApplied = result.changesAppliedCount();

            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(reconstructedDoc);

            session.setExportJsonBytes(jsonBytes);
            session.setChangesAppliedCount(changesApplied);

            int totalExported = reconstructedDoc.getTotalBookmarkCount();
            String downloadUrl = "/api/scans/" + scanId + "/export";

            boolean importedFromBrowser = session.isImportedFromBrowser();
            String browserId = session.getBrowserType() != null ? session.getBrowserType().getId() : null;
            String browserName = session.getBrowserType() != null ? session.getBrowserType().getDisplayName() : null;
            String profileId = session.getProfileId();
            String profileName = session.getProfileDisplayName();

            return new ExportResponse(scanId, session.getExportFilename(), totalExported, changesApplied, downloadUrl,
                    importedFromBrowser, browserId, browserName, profileId, profileName);
        } catch (Exception e) {
            throw new ExportException("Failed to generate export JSON for scan '" + scanId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Retrieves the generated export JSON bytes for download.
     */
    public byte[] getExportBytes(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);
        byte[] bytes = session.getExportJsonBytes();
        if (bytes == null) {
            // Generate on demand if not yet generated
            generateExport(scanId);
            bytes = session.getExportJsonBytes();
        }
        return bytes;
    }
}
