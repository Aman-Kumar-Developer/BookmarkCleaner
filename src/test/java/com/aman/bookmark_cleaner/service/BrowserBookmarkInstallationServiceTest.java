package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.BackupInfoDto;
import com.aman.bookmark_cleaner.dto.BackupManagementResponse;
import com.aman.bookmark_cleaner.dto.InstallResponse;
import com.aman.bookmark_cleaner.dto.InstallationOperationDto;
import com.aman.bookmark_cleaner.dto.RestoreResponse;
import com.aman.bookmark_cleaner.exception.BackupNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkFileModifiedException;
import com.aman.bookmark_cleaner.exception.BookmarkInstallationException;
import com.aman.bookmark_cleaner.exception.BrowserRunningException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.InstallationOperation;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserBookmarkInstallationServiceTest {

    @TempDir
    Path tempDir;

    private Path braveDataDir;
    private Path defaultProfileDir;
    private Path originalBookmarkFile;

    private ObjectMapper objectMapper;
    private JsonBookmarkReader bookmarkReader;
    private ScanSessionManager sessionManager;
    private BrowserDiscoveryService discoveryService;
    private BookmarkExportService exportService;
    private MockBrowserProcessDetector processDetector;
    private BrowserBookmarkInstallationService installationService;

    private static final String ORIGINAL_BOOKMARKS_JSON = """
            {
              "checksum": "original-checksum",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks Bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "GitHub",
                      "type": "url",
                      "url": "https://github.com"
                    }
                  ]
                }
              },
              "version": 1
            }
            """;

    private static final String CLEANED_BOOKMARKS_JSON_V1 = """
            {
              "checksum": "cleaned-checksum-v1",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks Bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "GitHub Cleaned",
                      "type": "url",
                      "url": "https://github.com"
                    },
                    {
                      "id": "3",
                      "name": "Google",
                      "type": "url",
                      "url": "https://www.google.com"
                    }
                  ]
                }
              },
              "version": 1
            }
            """;

    private static final String CLEANED_BOOKMARKS_JSON_V2 = """
            {
              "checksum": "cleaned-checksum-v2",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks Bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "GitHub Cleaned V2",
                      "type": "url",
                      "url": "https://github.com"
                    }
                  ]
                }
              },
              "version": 1
            }
            """;

    static class MockBrowserProcessDetector implements BrowserProcessDetector {
        private boolean running = false;

        public void setRunning(boolean running) {
            this.running = running;
        }

        @Override
        public boolean isBrowserRunning(BrowserType browserType) {
            return running;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        bookmarkReader = new JsonBookmarkReader(objectMapper, null);
        sessionManager = new ScanSessionManager();

        // Create synthetic browser profile directory structure: tempDir/Brave-Browser/User Data/Default
        braveDataDir = tempDir.resolve("Brave-Browser").resolve("User Data");
        defaultProfileDir = braveDataDir.resolve("Default");
        Files.createDirectories(defaultProfileDir);

        originalBookmarkFile = defaultProfileDir.resolve("Bookmarks");
        Files.writeString(originalBookmarkFile, ORIGINAL_BOOKMARKS_JSON, StandardCharsets.UTF_8);

        Map<BrowserType, Path> paths = new HashMap<>();
        paths.put(BrowserType.BRAVE, braveDataDir);
        BrowserPathResolver testPathResolver = new BrowserPathResolver() {
            @Override
            public Path resolveDataDirectory(BrowserType browserType) {
                return paths.get(browserType);
            }

            @Override
            public com.aman.bookmark_cleaner.model.OperatingSystem getOperatingSystem() {
                return com.aman.bookmark_cleaner.model.OperatingSystem.WINDOWS;
            }
        };

        discoveryService = new BrowserDiscoveryService(testPathResolver, objectMapper);
        BookmarkTreeReconstructionService reconstructionService = new BookmarkTreeReconstructionService();
        exportService = new BookmarkExportService(reconstructionService, sessionManager, objectMapper);
        processDetector = new MockBrowserProcessDetector();

        installationService = new BrowserBookmarkInstallationService(
                sessionManager,
                discoveryService,
                exportService,
                bookmarkReader,
                processDetector,
                objectMapper
        );
    }

    private ScanSession createBrowserSession(String originalHash, byte[] exportBytes) throws Exception {
        BookmarkDocument originalDoc = bookmarkReader.read(originalBookmarkFile);
        ScanSession session = sessionManager.createSession("Brave (Default)", originalDoc);
        session.setImportedFromBrowser(true);
        session.setBrowserType(BrowserType.BRAVE);
        session.setProfileId("brave-default");
        session.setProfileDirectoryName("Default");
        session.setProfileDisplayName("Default");
        session.setOriginalBookmarkPath(originalBookmarkFile);
        session.setOriginalFileHash(originalHash != null ? originalHash : BrowserImportService.computeSha256(originalBookmarkFile));
        session.setExportJsonBytes(exportBytes != null ? exportBytes : CLEANED_BOOKMARKS_JSON_V1.getBytes(StandardCharsets.UTF_8));
        return session;
    }

    @Test
    @DisplayName("Successfully install cleaned bookmarks and create first backup (Bookmarks.old.1)")
    void testInstallCleanedBookmarks_Success_CreatesFirstBackup() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        InstallResponse response = installationService.installCleanedBookmarks(session.getScanId());

        assertTrue(response.isSuccess());
        assertEquals("Brave", response.getBrowserName());
        assertEquals("Default", response.getProfileName());
        assertEquals(2, response.getInstalledBookmarkCount());
        assertEquals("Bookmarks.old.1", response.getBackupCreated());
        assertFalse(response.isBackupRotated());

        // Verify Bookmarks now contains cleaned v1 content
        BookmarkDocument installedDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(2, installedDoc.getTotalBookmarkCount());

        // Verify Bookmarks.old.1 exists with original content
        Path backup1 = defaultProfileDir.resolve("Bookmarks.old.1");
        assertTrue(Files.exists(backup1));
        BookmarkDocument backupDoc = bookmarkReader.read(backup1);
        assertEquals(1, backupDoc.getTotalBookmarkCount());

        // Verify Bookmarks.old.2 does NOT exist yet
        Path backup2 = defaultProfileDir.resolve("Bookmarks.old.2");
        assertFalse(Files.exists(backup2));
    }

    @Test
    @DisplayName("Automatic 2-generation backup rotation: retains max 2 backups and rotates properly")
    void testInstallCleanedBookmarks_TwoGenerationRotation() throws Exception {
        // Generation 1: Original has 1 bookmark. Install v1 (2 bookmarks).
        ScanSession session1 = createBrowserSession(null, CLEANED_BOOKMARKS_JSON_V1.getBytes(StandardCharsets.UTF_8));
        InstallResponse response1 = installationService.installCleanedBookmarks(session1.getScanId());
        assertTrue(response1.isSuccess());
        assertFalse(response1.isBackupRotated());

        // Generation 2: Active has 2 bookmarks. Install v2 (1 bookmark).
        ScanSession session2 = createBrowserSession(null, CLEANED_BOOKMARKS_JSON_V2.getBytes(StandardCharsets.UTF_8));
        InstallResponse response2 = installationService.installCleanedBookmarks(session2.getScanId());
        assertTrue(response2.isSuccess());
        assertTrue(response2.isBackupRotated());

        // Bookmarks should be v2 (1 bookmark)
        BookmarkDocument activeDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, activeDoc.getTotalBookmarkCount());

        // Bookmarks.old.1 should be v1 (2 bookmarks)
        Path backup1 = defaultProfileDir.resolve("Bookmarks.old.1");
        assertTrue(Files.exists(backup1));
        BookmarkDocument b1Doc = bookmarkReader.read(backup1);
        assertEquals(2, b1Doc.getTotalBookmarkCount());

        // Bookmarks.old.2 should be original (1 bookmark)
        Path backup2 = defaultProfileDir.resolve("Bookmarks.old.2");
        assertTrue(Files.exists(backup2));
        BookmarkDocument b2Doc = bookmarkReader.read(backup2);
        assertEquals(1, b2Doc.getTotalBookmarkCount());

        // Generation 3: Install again. Ensure max 2 backups are retained
        ScanSession session3 = createBrowserSession(null, CLEANED_BOOKMARKS_JSON_V1.getBytes(StandardCharsets.UTF_8));
        InstallResponse response3 = installationService.installCleanedBookmarks(session3.getScanId());
        assertTrue(response3.isSuccess());
        assertTrue(response3.isBackupRotated());

        // Ensure no Bookmarks.old.3 exists
        assertFalse(Files.exists(defaultProfileDir.resolve("Bookmarks.old.3")));
    }

    @Test
    @DisplayName("When browser is running, enters WAITING_FOR_BROWSER_CLOSURE state without modifying bookmarks, and finishes upon browser closure")
    void testInstallCleanedBookmarks_BrowserRunning_EntersWaitingStateAndCompletesOnClosure() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        // Simulate browser running
        processDetector.setRunning(true);

        InstallResponse response = installationService.installCleanedBookmarks(session.getScanId());

        assertTrue(response.isSuccess());
        assertEquals("WAITING_FOR_BROWSER_CLOSURE", response.getState());
        assertNotNull(response.getOperationId());
        assertTrue(response.getMessage().contains("Close Brave completely"));

        // Verify active file is completely untouched while browser is running
        BookmarkDocument currentDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, currentDoc.getTotalBookmarkCount());

        // Verify no backups created yet
        assertFalse(Files.exists(defaultProfileDir.resolve("Bookmarks.old.1")));

        // Now simulate browser closure
        processDetector.setRunning(false);

        // Retrieve operation and execute transaction
        InstallationOperationDto statusBefore = installationService.getInstallationStatus(session.getScanId());
        assertEquals("WAITING_FOR_BROWSER_CLOSURE", statusBefore.getState());

        InstallationOperation op = installationService.getOperationForScan(session.getScanId());
        assertNotNull(op);
        installationService.executeInstallationTransaction(op);

        // Verify completion
        assertEquals("COMPLETED", op.getState().name());
        BookmarkDocument installedDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(2, installedDoc.getTotalBookmarkCount());

        // Verify backup was created upon installation
        assertTrue(Files.exists(defaultProfileDir.resolve("Bookmarks.old.1")));
    }

    @Test
    @DisplayName("Cancel pending operation in WAITING_FOR_BROWSER_CLOSURE cleans up and marks CANCELLED")
    void testCancelInstallation_WaitingState_CancelsSuccessfully() throws Exception {
        ScanSession session = createBrowserSession(null, null);
        processDetector.setRunning(true);

        installationService.installCleanedBookmarks(session.getScanId());

        InstallationOperationDto cancelledDto = installationService.cancelInstallation(session.getScanId());
        assertEquals("CANCELLED", cancelledDto.getState());

        // Original file untouched
        BookmarkDocument doc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, doc.getTotalBookmarkCount());
    }

    @Test
    @DisplayName("Blocks replacement when original bookmark file was modified since import")
    void testInstallCleanedBookmarks_FileModifiedSinceImport_ThrowsException() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        // Modify file on disk after import
        Files.writeString(originalBookmarkFile, CLEANED_BOOKMARKS_JSON_V2, StandardCharsets.UTF_8);

        BookmarkFileModifiedException ex = assertThrows(BookmarkFileModifiedException.class, () ->
                installationService.installCleanedBookmarks(session.getScanId())
        );

        assertTrue(ex.getMessage().contains("has been modified since it was imported"));

        // Verify no backup was created
        assertFalse(Files.exists(defaultProfileDir.resolve("Bookmarks.old.1")));
    }

    @Test
    @DisplayName("Rejects direct replacement for sessions uploaded via file upload")
    void testInstallCleanedBookmarks_NotImportedFromBrowser_ThrowsException() throws Exception {
        BookmarkDocument doc = bookmarkReader.read(originalBookmarkFile);
        ScanSession uploadSession = sessionManager.createSession("UploadedBookmarks.json", doc);
        // importedFromBrowser is false by default

        InvalidBookmarkException ex = assertThrows(InvalidBookmarkException.class, () ->
                installationService.installCleanedBookmarks(uploadSession.getScanId())
        );

        assertTrue(ex.getMessage().contains("not imported from a browser profile"));
    }

    @Test
    @DisplayName("Rejects replacement if staged export JSON is invalid and leaves original intact")
    void testInstallCleanedBookmarks_InvalidExportData_ThrowsExceptionAndKeepsOriginal() throws Exception {
        byte[] invalidJsonBytes = "{ broken json: true ".getBytes(StandardCharsets.UTF_8);
        ScanSession session = createBrowserSession(null, invalidJsonBytes);

        BookmarkInstallationException ex = assertThrows(BookmarkInstallationException.class, () ->
                installationService.installCleanedBookmarks(session.getScanId())
        );

        assertTrue(ex.getMessage().contains("failed validation"));

        // Verify original Bookmarks untouched
        BookmarkDocument doc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, doc.getTotalBookmarkCount());
        assertFalse(Files.exists(defaultProfileDir.resolve("Bookmarks.old.1")));
    }

    @Test
    @DisplayName("Restore backup generation 1 successfully into active Bookmarks")
    void testRestoreBackup_Generation1_Success() throws Exception {
        // Prepare: install once so Bookmarks.old.1 has original (1 bookmark) and Bookmarks has v1 (2 bookmarks)
        ScanSession session = createBrowserSession(null, null);
        installationService.installCleanedBookmarks(session.getScanId());

        // Restore generation 1
        RestoreResponse response = installationService.restoreBackup(session.getScanId(), "1");

        assertTrue(response.isSuccess());
        assertEquals("1", response.getBackupId());
        assertEquals(1, response.getRestoredBookmarkCount());

        // Active Bookmarks should now have 1 bookmark restored
        BookmarkDocument restoredDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, restoredDoc.getTotalBookmarkCount());
    }

    @Test
    @DisplayName("Restore backup generation 2 successfully into active Bookmarks")
    void testRestoreBackup_Generation2_Success() throws Exception {
        // Step 1: Install v1
        ScanSession session1 = createBrowserSession(null, CLEANED_BOOKMARKS_JSON_V1.getBytes(StandardCharsets.UTF_8));
        installationService.installCleanedBookmarks(session1.getScanId());

        // Step 2: Install v2 -> Bookmarks.old.2 now has original 1 bookmark
        ScanSession session2 = createBrowserSession(null, CLEANED_BOOKMARKS_JSON_V2.getBytes(StandardCharsets.UTF_8));
        installationService.installCleanedBookmarks(session2.getScanId());

        // Restore generation 2
        RestoreResponse response = installationService.restoreBackup(session2.getScanId(), "2");

        assertTrue(response.isSuccess());
        assertEquals("2", response.getBackupId());
        assertEquals(1, response.getRestoredBookmarkCount());

        // Active file has 1 bookmark
        BookmarkDocument restoredDoc = bookmarkReader.read(originalBookmarkFile);
        assertEquals(1, restoredDoc.getTotalBookmarkCount());
    }

    @Test
    @DisplayName("Restore schedules and enters WAITING_FOR_BROWSER_CLOSURE when browser is running")
    void testRestoreBackup_BrowserRunning_EntersWaitingState() throws Exception {
        ScanSession session = createBrowserSession(null, null);
        installationService.installCleanedBookmarks(session.getScanId());

        processDetector.setRunning(true);

        RestoreResponse response = installationService.restoreBackup(session.getScanId(), "1");

        assertTrue(response.isSuccess());
        assertEquals("WAITING_FOR_BROWSER_CLOSURE", response.getState());
        assertTrue(response.getMessage().contains("Close Brave completely"));
    }

    @Test
    @DisplayName("Restore throws BackupNotFoundException if requested backup does not exist")
    void testRestoreBackup_NotFound_ThrowsException() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        BackupNotFoundException ex = assertThrows(BackupNotFoundException.class, () ->
                installationService.restoreBackup(session.getScanId(), "2")
        );

        assertTrue(ex.getMessage().contains("Bookmarks.old.2"));
    }

    @Test
    @DisplayName("Restore throws BookmarkInstallationException if backup file is corrupted")
    void testRestoreBackup_CorruptedBackup_ThrowsException() throws Exception {
        ScanSession session = createBrowserSession(null, null);
        // Create corrupted backup file directly
        Path corruptedBackup = defaultProfileDir.resolve("Bookmarks.old.1");
        Files.writeString(corruptedBackup, "NOT VALID JSON >>>><<<<", StandardCharsets.UTF_8);

        BookmarkInstallationException ex = assertThrows(BookmarkInstallationException.class, () ->
                installationService.restoreBackup(session.getScanId(), "1")
        );

        assertTrue(ex.getMessage().contains("invalid or corrupted"));
    }

    @Test
    @DisplayName("Inspect available backups reports correct metadata and counts")
    void testInspectAvailableBackups() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        // Before any install: no backups
        BackupManagementResponse info1 = installationService.getBackupManagementInfo(session.getScanId());
        assertTrue(info1.isImportedFromBrowser());
        assertEquals("Brave", info1.getBrowserName());
        assertEquals(0, info1.getBackups().size());

        // After install: 1 backup
        installationService.installCleanedBookmarks(session.getScanId());

        BackupManagementResponse info2 = installationService.getBackupManagementInfo(session.getScanId());
        assertEquals(1, info2.getBackups().size());
        BackupInfoDto backup1 = info2.getBackups().get(0);
        assertEquals("1", backup1.getId());
        assertEquals("Bookmarks.old.1", backup1.getFilename());
        assertEquals(1, backup1.getBookmarkCount());
        assertTrue(backup1.isValid());
        assertTrue(backup1.getFileSize() > 0);
    }

    @Test
    @DisplayName("Concurrent replacement requests on the same profile are safely serialized without corruption")
    void testConcurrentReplacements_AreSerializedWithoutCorruption() throws Exception {
        ScanSession session = createBrowserSession(null, null);

        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Each thread attempts install
                    installationService.installCleanedBookmarks(session.getScanId());
                    successCount.incrementAndGet();
                } catch (BookmarkFileModifiedException e) {
                    // Subsequent threads will encounter modified file hash since previous thread updated it
                } catch (Exception e) {
                    // Other safely handled conditions
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS));
        executor.shutdown();

        // At least one succeeded, and final Bookmarks file must be intact and valid
        assertTrue(successCount.get() >= 1);
        BookmarkDocument finalDoc = bookmarkReader.read(originalBookmarkFile);
        assertNotNull(finalDoc);
        assertTrue(finalDoc.getTotalBookmarkCount() > 0);
    }
}
