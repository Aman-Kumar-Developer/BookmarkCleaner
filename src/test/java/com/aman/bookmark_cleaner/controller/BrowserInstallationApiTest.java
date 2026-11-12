package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.dto.BackupInfoDto;
import com.aman.bookmark_cleaner.dto.BackupManagementResponse;
import com.aman.bookmark_cleaner.dto.InstallResponse;
import com.aman.bookmark_cleaner.dto.InstallationOperationDto;
import com.aman.bookmark_cleaner.dto.RestoreResponse;
import com.aman.bookmark_cleaner.exception.BackupNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkFileModifiedException;
import com.aman.bookmark_cleaner.exception.BrowserRunningException;
import com.aman.bookmark_cleaner.exception.ScanNotFoundException;
import com.aman.bookmark_cleaner.service.BrowserBookmarkInstallationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BrowserInstallationApiTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BrowserBookmarkInstallationService installationService;

    @Test
    @DisplayName("GET /api/scans/{scanId}/backups returns backup management details")
    void testGetBackups_Success() throws Exception {
        String scanId = "test-scan-123";
        BackupInfoDto backup1 = new BackupInfoDto("1", "Bookmarks.old.1", "Generation 1", 1024, 50, true, 1700000000000L);
        BackupManagementResponse response = new BackupManagementResponse(
                scanId, true, "brave", "Brave", "Default", "Default", true, false, List.of(backup1)
        );

        when(installationService.getBackupManagementInfo(scanId)).thenReturn(response);

        mockMvc.perform(get("/api/scans/{scanId}/backups", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId", equalTo(scanId)))
                .andExpect(jsonPath("$.importedFromBrowser", equalTo(true)))
                .andExpect(jsonPath("$.browserName", equalTo("Brave")))
                .andExpect(jsonPath("$.profileName", equalTo("Default")))
                .andExpect(jsonPath("$.backups", hasSize(1)))
                .andExpect(jsonPath("$.backups[0].filename", equalTo("Bookmarks.old.1")));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/install returns 200 on successful replacement")
    void testInstallCleanedBookmarks_Success() throws Exception {
        String scanId = "test-scan-456";
        InstallResponse response = new InstallResponse(
                true, scanId, "Brave", "Default", 110, "Bookmarks.old.1", false,
                "Bookmarks successfully replaced", List.of()
        );

        when(installationService.installCleanedBookmarks(scanId)).thenReturn(response);

        mockMvc.perform(post("/api/scans/{scanId}/install", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.scanId", equalTo(scanId)))
                .andExpect(jsonPath("$.browserName", equalTo("Brave")))
                .andExpect(jsonPath("$.installedBookmarkCount", equalTo(110)))
                .andExpect(jsonPath("$.backupCreated", equalTo("Bookmarks.old.1")));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/install returns 409 Conflict when browser is running")
    void testInstallCleanedBookmarks_BrowserRunning_Returns409() throws Exception {
        String scanId = "test-scan-789";

        when(installationService.installCleanedBookmarks(scanId))
                .thenThrow(new BrowserRunningException("Please close Brave completely before replacing its bookmarks."));

        mockMvc.perform(post("/api/scans/{scanId}/install", scanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Browser Is Running")))
                .andExpect(jsonPath("$.message", equalTo("Please close Brave completely before replacing its bookmarks.")));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/install returns 409 Conflict when file modified since import")
    void testInstallCleanedBookmarks_FileModified_Returns409() throws Exception {
        String scanId = "test-scan-101";

        when(installationService.installCleanedBookmarks(scanId))
                .thenThrow(new BookmarkFileModifiedException("The browser bookmark file has been modified since it was imported."));

        mockMvc.perform(post("/api/scans/{scanId}/install", scanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status", equalTo(409)))
                .andExpect(jsonPath("$.error", equalTo("Bookmark File Modified")));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/install returns 404 when scanId not found")
    void testInstallCleanedBookmarks_ScanNotFound_Returns404() throws Exception {
        String scanId = "nonexistent-scan";

        when(installationService.installCleanedBookmarks(scanId))
                .thenThrow(new ScanNotFoundException("Scan session 'nonexistent-scan' not found"));

        mockMvc.perform(post("/api/scans/{scanId}/install", scanId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.error", equalTo("Scan Not Found")));
    }

    @Test
    @DisplayName("GET /api/scans/{scanId}/install/status returns current operation state")
    void testGetInstallationStatus_Success() throws Exception {
        String scanId = "test-scan-status-1";
        InstallationOperationDto dto = new InstallationOperationDto(
                "op-123", scanId, "INSTALL", null, "WAITING_FOR_BROWSER_CLOSURE",
                "Brave", "Default", 110, 0, null, false,
                "Waiting for browser to close", null, true, true, List.of(), System.currentTimeMillis()
        );

        when(installationService.getInstallationStatus(scanId)).thenReturn(dto);

        mockMvc.perform(get("/api/scans/{scanId}/install/status", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId", equalTo("op-123")))
                .andExpect(jsonPath("$.state", equalTo("WAITING_FOR_BROWSER_CLOSURE")))
                .andExpect(jsonPath("$.browserName", equalTo("Brave")))
                .andExpect(jsonPath("$.canCancel", equalTo(true)));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/install/cancel cancels a waiting operation")
    void testCancelInstallation_Success() throws Exception {
        String scanId = "test-scan-cancel-1";
        InstallationOperationDto dto = new InstallationOperationDto(
                "op-cancel-123", scanId, "INSTALL", null, "CANCELLED",
                "Brave", "Default", 110, 0, null, false,
                "Cancelled by user", null, false, false, List.of(), System.currentTimeMillis()
        );

        when(installationService.cancelInstallation(scanId)).thenReturn(dto);

        mockMvc.perform(post("/api/scans/{scanId}/install/cancel", scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationId", equalTo("op-cancel-123")))
                .andExpect(jsonPath("$.state", equalTo("CANCELLED")));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/backups/{backupId}/restore returns 200 on success")
    void testRestoreBackup_Success() throws Exception {
        String scanId = "test-scan-202";
        String backupId = "1";
        RestoreResponse response = new RestoreResponse(
                true, scanId, backupId, "Brave", "Default", 100, "Successfully restored backup", List.of()
        );

        when(installationService.restoreBackup(scanId, backupId)).thenReturn(response);

        mockMvc.perform(post("/api/scans/{scanId}/backups/{backupId}/restore", scanId, backupId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", equalTo(true)))
                .andExpect(jsonPath("$.backupId", equalTo("1")))
                .andExpect(jsonPath("$.restoredBookmarkCount", equalTo(100)));
    }

    @Test
    @DisplayName("POST /api/scans/{scanId}/backups/{backupId}/restore returns 404 when backup not found")
    void testRestoreBackup_NotFound_Returns404() throws Exception {
        String scanId = "test-scan-202";
        String backupId = "2";

        when(installationService.restoreBackup(scanId, backupId))
                .thenThrow(new BackupNotFoundException("Requested backup file 'Bookmarks.old.2' not found."));

        mockMvc.perform(post("/api/scans/{scanId}/backups/{backupId}/restore", scanId, backupId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.error", equalTo("File Not Found")));
    }
}
