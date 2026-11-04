package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.BrowserDiscoveryResponse;
import com.aman.bookmark_cleaner.dto.BrowserInfoDto;
import com.aman.bookmark_cleaner.dto.BrowserProfileDto;
import com.aman.bookmark_cleaner.model.BrowserInfo;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    private ObjectMapper objectMapper;
    private Map<BrowserType, Path> browserPaths;
    private BrowserPathResolver mockResolver;
    private BrowserDiscoveryService discoveryService;

    private static final String SAMPLE_BOOKMARKS = """
            {
              "roots": {
                "bookmark_bar": {
                  "children": [
                    {
                      "name": "Example",
                      "type": "url",
                      "url": "https://example.com"
                    }
                  ],
                  "name": "Bookmarks Bar",
                  "type": "folder"
                }
              },
              "version": 1
            }
            """;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        browserPaths = new HashMap<>();
        mockResolver = new BrowserPathResolver() {
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
    }

    private void createProfileWithBookmarks(Path dataDir, String profileDirName, String bookmarksJson) throws IOException {
        Path profileDir = dataDir.resolve(profileDirName);
        Files.createDirectories(profileDir);
        Files.writeString(profileDir.resolve("Bookmarks"), bookmarksJson);
    }

    private void createLocalState(Path dataDir, String localStateJson) throws IOException {
        Files.createDirectories(dataDir);
        Files.writeString(dataDir.resolve("Local State"), localStateJson);
    }

    @Test
    @DisplayName("Case 1: Chrome installed and detected")
    void testChromeInstalledAndDetected() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertTrue(info.isAvailable());
        assertEquals(1, info.getProfiles().size());
        assertEquals("Default", info.getProfiles().get(0).getDisplayName());
    }

    @Test
    @DisplayName("Case 2: Edge installed and detected")
    void testEdgeInstalledAndDetected() throws IOException {
        Path edgeDir = tempDir.resolve("Edge");
        browserPaths.put(BrowserType.EDGE, edgeDir);
        createProfileWithBookmarks(edgeDir, "Default", SAMPLE_BOOKMARKS);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.EDGE);
        assertTrue(info.isAvailable());
        assertEquals(1, info.getProfiles().size());
    }

    @Test
    @DisplayName("Case 3: Brave installed and detected")
    void testBraveInstalledAndDetected() throws IOException {
        Path braveDir = tempDir.resolve("Brave");
        browserPaths.put(BrowserType.BRAVE, braveDir);
        createProfileWithBookmarks(braveDir, "Default", SAMPLE_BOOKMARKS);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.BRAVE);
        assertTrue(info.isAvailable());
        assertEquals(1, info.getProfiles().size());
    }

    @Test
    @DisplayName("Case 4: No supported browsers installed")
    void testNoSupportedBrowsersInstalled() {
        // Leave browserPaths empty (resolves to null)
        BrowserDiscoveryResponse response = discoveryService.discoverBrowsers();
        assertNotNull(response);
        assertEquals(3, response.getBrowsers().size());
        for (BrowserInfoDto dto : response.getBrowsers()) {
            assertFalse(dto.isAvailable());
            assertTrue(dto.getProfiles().isEmpty());
        }
    }

    @Test
    @DisplayName("Case 5: Only one supported browser installed")
    void testOnlyOneSupportedBrowserInstalled() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);

        BrowserDiscoveryResponse response = discoveryService.discoverBrowsers();
        for (BrowserInfoDto b : response.getBrowsers()) {
            if ("chrome".equals(b.getId())) {
                assertTrue(b.isAvailable());
                assertEquals(1, b.getProfiles().size());
            } else {
                assertFalse(b.isAvailable());
                assertTrue(b.getProfiles().isEmpty());
            }
        }
    }

    @Test
    @DisplayName("Case 6: Multiple browsers installed simultaneously")
    void testMultipleBrowsersInstalledSimultaneously() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        Path edgeDir = tempDir.resolve("Edge");
        Path braveDir = tempDir.resolve("Brave");

        browserPaths.put(BrowserType.CHROME, chromeDir);
        browserPaths.put(BrowserType.EDGE, edgeDir);
        browserPaths.put(BrowserType.BRAVE, braveDir);

        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);
        createProfileWithBookmarks(edgeDir, "Default", SAMPLE_BOOKMARKS);
        createProfileWithBookmarks(braveDir, "Default", SAMPLE_BOOKMARKS);

        BrowserDiscoveryResponse response = discoveryService.discoverBrowsers();
        for (BrowserInfoDto b : response.getBrowsers()) {
            assertTrue(b.isAvailable());
            assertEquals(1, b.getProfiles().size());
        }
    }

    @Test
    @DisplayName("Case 7: Browser directory exists but Local State is missing")
    void testLocalStateMissing() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);
        // Do not create Local State

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertTrue(info.isAvailable());
        assertEquals(1, info.getProfiles().size());
        assertEquals("Default", info.getProfiles().get(0).getDisplayName());
    }

    @Test
    @DisplayName("Case 8: Local State contains malformed JSON")
    void testLocalStateMalformedJson() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);
        createLocalState(chromeDir, "{ invalid-json: missing brackets ");

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertTrue(info.isAvailable());
        assertEquals(1, info.getProfiles().size());
        assertEquals("Default", info.getProfiles().get(0).getDisplayName());
    }

    @Test
    @DisplayName("Case 9: Browser contains one profile")
    void testBrowserContainsOneProfile() throws IOException {
        Path edgeDir = tempDir.resolve("Edge");
        browserPaths.put(BrowserType.EDGE, edgeDir);
        createProfileWithBookmarks(edgeDir, "Default", SAMPLE_BOOKMARKS);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.EDGE);
        assertEquals(1, info.getProfiles().size());
    }

    @Test
    @DisplayName("Case 10: Browser contains multiple profiles")
    void testBrowserContainsMultipleProfiles() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);
        createProfileWithBookmarks(chromeDir, "Profile 1", SAMPLE_BOOKMARKS);
        createProfileWithBookmarks(chromeDir, "Profile 2", SAMPLE_BOOKMARKS);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertEquals(3, info.getProfiles().size());
    }

    @Test
    @DisplayName("Case 11: Profile names are correctly mapped from metadata")
    void testProfileNamesMappedFromMetadata() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);
        createProfileWithBookmarks(chromeDir, "Profile 1", SAMPLE_BOOKMARKS);

        String localState = """
                {
                  "profile": {
                    "info_cache": {
                      "Default": { "name": "Personal" },
                      "Profile 1": { "name": "Work Account" }
                    }
                  }
                }
                """;
        createLocalState(chromeDir, localState);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertEquals(2, info.getProfiles().size());

        boolean foundPersonal = info.getProfiles().stream().anyMatch(p -> "Personal".equals(p.getDisplayName()));
        boolean foundWork = info.getProfiles().stream().anyMatch(p -> "Work Account".equals(p.getDisplayName()));

        assertTrue(foundPersonal, "Should have mapped Default to 'Personal'");
        assertTrue(foundWork, "Should have mapped Profile 1 to 'Work Account'");
    }

    @Test
    @DisplayName("Case 12: Profile directory exists but Bookmarks file is missing")
    void testProfileExistsWithoutBookmarksFile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        // Create Default directory without Bookmarks file
        Files.createDirectories(chromeDir.resolve("Default"));

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertTrue(info.isAvailable());
        assertTrue(info.getProfiles().isEmpty(), "Profile with missing Bookmarks should not be displayed");
    }

    @Test
    @DisplayName("Case 13: Profile exists but bookmark file is unreadable")
    void testProfileExistsWithUnreadableBookmarkFile() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        Path bookmarkFile = chromeDir.resolve("Default").resolve("Bookmarks");
        Files.createDirectories(chromeDir.resolve("Default"));
        Files.writeString(bookmarkFile, SAMPLE_BOOKMARKS);

        // Make bookmark file not readable if OS supports it
        try {
            bookmarkFile.toFile().setReadable(false);
            if (!Files.isReadable(bookmarkFile)) {
                BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
                assertTrue(info.getProfiles().isEmpty(), "Unreadable bookmark file should be skipped");
            }
        } finally {
            bookmarkFile.toFile().setReadable(true);
        }
    }

    @Test
    @DisplayName("Case 14: Profile metadata references an unavailable directory")
    void testProfileMetadataReferencesUnavailableDirectory() throws IOException {
        Path chromeDir = tempDir.resolve("Chrome");
        browserPaths.put(BrowserType.CHROME, chromeDir);
        createProfileWithBookmarks(chromeDir, "Default", SAMPLE_BOOKMARKS);

        // Local State lists Profile 99 which does not exist on disk
        String localState = """
                {
                  "profile": {
                    "info_cache": {
                      "Default": { "name": "Personal" },
                      "Profile 99": { "name": "Nonexistent Profile" }
                    }
                  }
                }
                """;
        createLocalState(chromeDir, localState);

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.CHROME);
        assertEquals(1, info.getProfiles().size());
        assertEquals("Personal", info.getProfiles().get(0).getDisplayName());
    }

    @Test
    @DisplayName("Case 15: Browser directory is inaccessible")
    void testBrowserDirectoryInaccessible() throws IOException {
        Path inaccessibleDir = tempDir.resolve("InaccessibleDir");
        Files.createDirectories(inaccessibleDir);
        browserPaths.put(BrowserType.EDGE, inaccessibleDir);

        try {
            inaccessibleDir.toFile().setReadable(false);
            if (!Files.isReadable(inaccessibleDir)) {
                BrowserInfo info = discoveryService.inspectBrowser(BrowserType.EDGE);
                assertFalse(info.isAvailable());
                assertTrue(info.getProfiles().isEmpty());
            }
        } finally {
            inaccessibleDir.toFile().setReadable(true);
        }
    }

    @Test
    @DisplayName("Case 16: Empty browser profile directory")
    void testEmptyBrowserProfileDirectory() throws IOException {
        Path braveDir = tempDir.resolve("Brave");
        browserPaths.put(BrowserType.BRAVE, braveDir);
        Files.createDirectories(braveDir.resolve("Default"));
        Files.createDirectories(braveDir.resolve("Profile 1"));

        BrowserInfo info = discoveryService.inspectBrowser(BrowserType.BRAVE);
        assertTrue(info.isAvailable());
        assertTrue(info.getProfiles().isEmpty());
    }

    @Test
    @DisplayName("Case 17: Unknown operating system handling")
    void testUnknownOperatingSystemHandling() {
        BrowserPathResolver unknownResolver = new BrowserPathResolver() {
            @Override
            public Path resolveDataDirectory(BrowserType browserType) {
                return null;
            }

            @Override
            public OperatingSystem getOperatingSystem() {
                return OperatingSystem.UNKNOWN;
            }
        };

        BrowserDiscoveryService service = new BrowserDiscoveryService(unknownResolver, objectMapper);
        BrowserDiscoveryResponse response = service.discoverBrowsers();

        assertNotNull(response);
        assertEquals(3, response.getBrowsers().size());
        for (BrowserInfoDto b : response.getBrowsers()) {
            assertFalse(b.isAvailable());
            assertTrue(b.getProfiles().isEmpty());
        }
    }

    @Test
    @DisplayName("Case 18: Correct platform-specific path resolution")
    void testPlatformSpecificPathResolution() {
        DefaultBrowserPathResolver winResolver = new DefaultBrowserPathResolver(
                null,
                OperatingSystem.WINDOWS,
                Map.of("LOCALAPPDATA", "C:\\Users\\Test\\AppData\\Local"),
                "C:\\Users\\Test"
        );
        assertTrue(winResolver.resolveDataDirectory(BrowserType.CHROME).toString().contains("Google"));

        DefaultBrowserPathResolver linuxResolver = new DefaultBrowserPathResolver(
                null,
                OperatingSystem.LINUX,
                Map.of("XDG_CONFIG_HOME", "/home/test/.config"),
                "/home/test"
        );
        assertTrue(linuxResolver.resolveDataDirectory(BrowserType.CHROME).toString().contains("google-chrome"));

        DefaultBrowserPathResolver macResolver = new DefaultBrowserPathResolver(
                null,
                OperatingSystem.MACOS,
                Map.of(),
                "/Users/test"
        );
        assertTrue(macResolver.resolveDataDirectory(BrowserType.CHROME).toString().contains("Application Support"));
    }
}
