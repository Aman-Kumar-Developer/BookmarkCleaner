package com.aman.bookmark_cleaner.security;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.exception.SecurityValidationException;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import com.aman.bookmark_cleaner.service.BrowserDiscoveryService;
import com.aman.bookmark_cleaner.service.BrowserImportService;
import com.aman.bookmark_cleaner.service.BrowserPathResolver;
import com.aman.bookmark_cleaner.service.ScanSessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserSecurityTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private Map<BrowserType, Path> browserPaths;
    private BrowserImportService importService;
    private BookmarkParserProperties parserProperties;

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

        BrowserDiscoveryService discoveryService = new BrowserDiscoveryService(mockResolver, objectMapper);
        parserProperties = new BookmarkParserProperties();
        JsonBookmarkReader bookmarkReader = new JsonBookmarkReader(objectMapper, parserProperties);
        ScanSessionManager sessionManager = new ScanSessionManager();
        importService = new BrowserImportService(discoveryService, bookmarkReader, sessionManager, parserProperties);
    }

    @Test
    @DisplayName("Security: Reject arbitrary filesystem path injection in browser ID")
    void testRejectArbitraryPathInjectionInBrowserId() {
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("C:\\Windows\\System32", "Default"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("/etc/passwd", "Default"));
    }

    @Test
    @DisplayName("Security: Reject path traversal using ../ in profile ID")
    void testRejectPathTraversalDotDotSlash() {
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "../../../secret"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "Default/../../../etc/shadow"));
    }

    @Test
    @DisplayName("Security: Reject encoded path traversal (%2e%2e, %2f, %5c)")
    void testRejectEncodedPathTraversal() {
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "%2e%2e%2f%2e%2e%2fetc"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "%2e%2e%5csecret"));
    }

    @Test
    @DisplayName("Security: Reject malicious profile identifiers containing null bytes or control chars")
    void testRejectMaliciousProfileIdentifiers() {
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "Default\0secret"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "Default/evil"));
        assertThrows(SecurityValidationException.class, () ->
                importService.importBookmarks("chrome", "Default\\evil"));
    }

    @Test
    @DisplayName("Security: Reject unexpected file types (target is a directory)")
    void testRejectTargetIsDirectory() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        // Create Bookmarks as a directory instead of a regular file
        Path profileDir = chromeDir.resolve("Default");
        Files.createDirectories(profileDir.resolve("Bookmarks"));

        assertThrows(InvalidBookmarkException.class, () ->
                importService.importBookmarks("chrome", "Default"));
    }

    @Test
    @DisplayName("Security: Enforce maximum file size limit (reject oversized bookmark files)")
    void testRejectOversizedBookmarkFile() throws IOException {
        parserProperties.setMaxFileSize(100L); // 100 bytes limit
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        Path profileDir = chromeDir.resolve("Default");
        Files.createDirectories(profileDir);

        // Write 200 bytes of data
        byte[] largeData = new byte[200];
        Files.write(profileDir.resolve("Bookmarks"), largeData);

        BookmarkReaderException ex = assertThrows(BookmarkReaderException.class, () ->
                importService.importBookmarks("chrome", "Default"));
        assertTrue(ex.getMessage().contains("exceeds maximum permitted limit"));
    }

    @Test
    @DisplayName("Security: Sensitive filesystem paths are not returned in public error responses")
    void testSensitivePathsNotLeakedInExceptionMessages() {
        try {
            importService.importBookmarks("chrome", "NonexistentProfile");
        } catch (Exception ex) {
            String msg = ex.getMessage();
            assertFalse(msg.contains("C:\\"), "Error message should not leak Windows drive paths");
            assertFalse(msg.contains("/home/"), "Error message should not leak Linux home paths");
            assertFalse(msg.contains("/Users/"), "Error message should not leak Mac user paths");
            assertFalse(msg.contains("AppData"), "Error message should not leak AppData path");
        }
    }
}
