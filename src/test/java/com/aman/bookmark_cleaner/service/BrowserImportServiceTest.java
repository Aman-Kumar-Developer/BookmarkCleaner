package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.BrowserNotFoundException;
import com.aman.bookmark_cleaner.exception.BrowserProfileNotFoundException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.exception.SecurityValidationException;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserImportServiceTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private Map<BrowserType, Path> browserPaths;
    private BrowserDiscoveryService discoveryService;
    private JsonBookmarkReader bookmarkReader;
    private ScanSessionManager sessionManager;
    private BookmarkParserProperties parserProperties;
    private BrowserImportService importService;

    private static final String VALID_BOOKMARKS_JSON = """
            {
              "checksum": "abc-123",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks Bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "Working Example",
                      "type": "url",
                      "url": "https://example.com/working"
                    },
                    {
                      "id": "3",
                      "name": "Another Site",
                      "type": "url",
                      "url": "https://example.org"
                    }
                  ]
                },
                "other": {
                  "id": "4",
                  "name": "Other Bookmarks",
                  "type": "folder",
                  "children": []
                }
              },
              "version": 1
            }
            """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        browserPaths = new HashMap<>();
        BrowserPathResolver mockResolver = new BrowserPathResolver() {
            @Override
            public Path resolveDataDirectory(BrowserType browserType) {
                return browserPaths.get(browserType);
            }

            @Override
            public OperatingSystem getOperatingSystem() {
                return OperatingSystem.WINDOWS;
            }
        };

        discoveryService = new BrowserDiscoveryService(mockResolver, objectMapper);
        parserProperties = new BookmarkParserProperties();
        bookmarkReader = new JsonBookmarkReader(objectMapper, parserProperties);
        sessionManager = new ScanSessionManager();
        importService = new BrowserImportService(discoveryService, bookmarkReader, sessionManager, parserProperties);
    }

    private Path createProfileWithBookmarks(Path dataDir, String profileDirName, String bookmarksJson) throws IOException {
        Path profileDir = dataDir.resolve(profileDirName);
        Files.createDirectories(profileDir);
        Path bookmarkFile = profileDir.resolve("Bookmarks");
        Files.writeString(bookmarkFile, bookmarksJson);
        return bookmarkFile;
    }

    private void createLocalState(Path dataDir, String localStateJson) throws IOException {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("Local State"), localStateJson);
    }

    @Test
    @DisplayName("Case 1: Import from Chrome Default profile")
    void testImportFromChromeDefaultProfile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        UploadResponse response = importService.importBookmarks("chrome", "Default");

        assertNotNull(response);
        assertNotNull(response.getScanId());
        assertTrue(response.getFilename().contains("Google Chrome"));
        assertEquals(2, response.getTotalBookmarks());
        assertEquals("PENDING", response.getStatus());

        ScanSession session = sessionManager.getSession(response.getScanId());
        assertNotNull(session);
        assertEquals(2, session.getTotalBookmarks());
    }

    @Test
    @DisplayName("Case 2: Import from Chrome Profile 1")
    void testImportFromChromeProfile1() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Profile 1", VALID_BOOKMARKS_JSON);
        createLocalState(chromeDir, """
                {
                  "profile": {
                    "info_cache": {
                      "Profile 1": { "name": "Work" }
                    }
                  }
                }
                """);

        UploadResponse response = importService.importBookmarks("chrome", "chrome-profile-1");

        assertNotNull(response);
        assertTrue(response.getFilename().contains("Work"));
        assertEquals(2, response.getTotalBookmarks());
    }

    @Test
    @DisplayName("Case 3: Import from Edge profile")
    void testImportFromEdgeProfile() throws IOException {
        Path edgeDir = tempDir.resolve("Edge");
        browserPaths.put(BrowserType.EDGE, edgeDir);
        createProfileWithBookmarks(edgeDir, "Default", VALID_BOOKMARKS_JSON);

        UploadResponse response = importService.importBookmarks("edge", "edge-default");

        assertNotNull(response);
        assertTrue(response.getFilename().contains("Microsoft Edge"));
        assertEquals(2, response.getTotalBookmarks());
    }

    @Test
    @DisplayName("Case 4: Import from Brave profile")
    void testImportFromBraveProfile() throws IOException {
        Path braveDir = tempDir.resolve("Brave");
        browserPaths.put(BrowserType.BRAVE, braveDir);
        createProfileWithBookmarks(braveDir, "Default", VALID_BOOKMARKS_JSON);

        UploadResponse response = importService.importBookmarks("brave", "brave-default");

        assertNotNull(response);
        assertTrue(response.getFilename().contains("Brave"));
        assertEquals(2, response.getTotalBookmarks());
    }

    @Test
    @DisplayName("Case 5: Import a valid Chromium bookmark file")
    void testImportValidChromiumBookmarkFile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        UploadResponse response = importService.importBookmarks("chrome", "Default");
        assertEquals(2, response.getTotalBookmarks());
        ScanSession session = sessionManager.getSession(response.getScanId());
        assertNotNull(session.getOriginalDocument().getRoots().getBookmarkBar());
    }

    @Test
    @DisplayName("Case 6: Reject malformed bookmark JSON")
    void testRejectMalformedBookmarkJson() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", "{ not valid json: missing everything ");

        assertThrows(InvalidBookmarkException.class, () ->
                importService.importBookmarks("chrome", "Default"));
    }

    @Test
    @DisplayName("Case 7: Reject a missing bookmark file")
    void testRejectMissingBookmarkFile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        Files.createDirectories(chromeDir.resolve("Default"));
        // Profile directory exists, but Bookmarks file is missing

        assertThrows(BookmarkFileNotFoundException.class, () ->
                importService.importBookmarks("chrome", "Default"));
    }

    @Test
    @DisplayName("Case 8: Reject an unreadable bookmark file")
    void testRejectUnreadableBookmarkFile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        Path bookmarkFile = createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        try {
            bookmarkFile.toFile().setReadable(false);
            if (!Files.isReadable(bookmarkFile)) {
                assertThrows(BookmarkReaderException.class, () ->
                        importService.importBookmarks("chrome", "Default"));
            }
        } finally {
            bookmarkFile.toFile().setReadable(true);
        }
    }

    @Test
    @DisplayName("Case 9: Reject a file exceeding the configured size limit")
    void testRejectOversizedBookmarkFile() throws IOException {
        // Set small size limit: 50 bytes
        parserProperties.setMaxFileSize(50L);
        BrowserImportService limitedService = new BrowserImportService(
                discoveryService,
                new JsonBookmarkReader(objectMapper, parserProperties),
                sessionManager,
                parserProperties
        );

        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        BookmarkReaderException ex = assertThrows(BookmarkReaderException.class, () ->
                limitedService.importBookmarks("chrome", "Default"));
        assertTrue(ex.getMessage().contains("exceeds maximum permitted limit"));
    }

    @Test
    @DisplayName("Case 10: Reject an unknown browser ID")
    void testRejectUnknownBrowserId() {
        assertThrows(BrowserNotFoundException.class, () ->
                importService.importBookmarks("safari", "Default"));
        assertThrows(BrowserNotFoundException.class, () ->
                importService.importBookmarks("firefox", "Default"));
        assertThrows(BrowserNotFoundException.class, () ->
                importService.importBookmarks("", "Default"));
    }

    @Test
    @DisplayName("Case 11: Reject an unknown profile ID")
    void testRejectUnknownProfileId() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        assertThrows(BrowserProfileNotFoundException.class, () ->
                importService.importBookmarks("chrome", "NonexistentProfile"));
        assertThrows(BrowserProfileNotFoundException.class, () ->
                importService.importBookmarks("chrome", ""));
    }

    @Test
    @DisplayName("Case 12: Reject a profile ID belonging to a different browser")
    void testRejectProfileBelongingToDifferentBrowser() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        Path edgeDir = tempDir.resolve("Edge");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        browserPaths.put(BrowserType.EDGE, edgeDir);

        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);
        createProfileWithBookmarks(edgeDir, "Profile 1", VALID_BOOKMARKS_JSON);

        // Try to request edge's profile under chrome
        assertThrows(BrowserProfileNotFoundException.class, () ->
                importService.importBookmarks("chrome", "edge-profile-1"));
    }

    @Test
    @DisplayName("Case 13: Reject path traversal attempts")
    void testRejectPathTraversalAttempts() {
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "../../etc/passwd"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "..\\..\\windows\\system32"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "%2e%2e/secret"));
    }

    @Test
    @DisplayName("Case 14: Verify that the selected bookmark file remains unchanged after import")
    void testBookmarkFileRemainsUnchangedAfterImport() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        Path bookmarkFile = createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        byte[] originalBytes = Files.readAllBytes(bookmarkFile);
        long originalModifiedTime = Files.getLastModifiedTime(bookmarkFile).toMillis();

        importService.importBookmarks("chrome", "Default");

        byte[] postImportBytes = Files.readAllBytes(bookmarkFile);
        long postImportModifiedTime = Files.getLastModifiedTime(bookmarkFile).toMillis();

        assertEquals(new String(originalBytes), new String(postImportBytes), "Original bookmark file content must not be modified");
        assertEquals(originalModifiedTime, postImportModifiedTime, "Original bookmark file timestamp must not change");
    }

    @Test
    @DisplayName("Case 15: Verify that Local State remains unchanged")
    void testLocalStateRemainsUnchanged() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        String localState = """
                {
                  "profile": {
                    "info_cache": {
                      "Default": { "name": "Personal" }
                    }
                  }
                }
                """;
        createLocalState(chromeDir, localState);
        Path localStatePath = chromeDir.resolve("Local State");
        byte[] originalLocalStateBytes = Files.readAllBytes(localStatePath);

        importService.importBookmarks("chrome", "Default");

        byte[] postImportBytes = Files.readAllBytes(localStatePath);
        assertEquals(new String(originalLocalStateBytes), new String(postImportBytes), "Local State must not be modified");
    }

    @Test
    @DisplayName("Case 16: Verify that no files are created inside the browser profile")
    void testNoFilesCreatedInsideBrowserProfile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);
        Path profileDir = chromeDir.resolve("Default");

        long initialCount;
        try (Stream<Path> stream = Files.list(profileDir)) {
            initialCount = stream.count();
        }

        importService.importBookmarks("chrome", "Default");

        long postImportCount;
        try (Stream<Path> stream = Files.list(profileDir)) {
            postImportCount = stream.count();
        }

        assertEquals(initialCount, postImportCount, "No temporary or new files must be written into the browser profile");
    }

    @Test
    @DisplayName("Case 17: Verify that imported bookmarks enter the existing parsing and scan pipeline")
    void testImportedBookmarksEnterScanPipeline() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        UploadResponse response = importService.importBookmarks("chrome", "Default");
        ScanSession session = sessionManager.getSession(response.getScanId());

        assertNotNull(session);
        assertEquals(ScanStatus.PENDING, session.getStatus());
        assertEquals(2, session.getTotalBookmarks());
        assertNotNull(session.getOriginalDocument());
    }

    @Test
    @DisplayName("Case 18: Verify that existing manual upload continues to work")
    void testExistingManualUploadContinuesToWork() {
        ScanOrchestrationService orchestrationService = new ScanOrchestrationService(
                bookmarkReader,
                null,
                null,
                sessionManager
        );

        MockMultipartFile file = new MockMultipartFile(
                "file", "bookmarks.json", "application/json", VALID_BOOKMARKS_JSON.getBytes()
        );

        UploadResponse response = orchestrationService.uploadBookmarkFile(file);
        assertNotNull(response);
        assertNotNull(response.getScanId());
        assertEquals(2, response.getTotalBookmarks());
    }
}
