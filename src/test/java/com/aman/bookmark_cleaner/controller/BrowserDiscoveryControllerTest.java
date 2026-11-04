package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.service.BrowserPathResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BrowserDiscoveryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookmarkParserProperties parserProperties;

    @MockitoBean
    private BrowserPathResolver pathResolver;

    @TempDir
    Path tempDir;

    private Path chromeDir;
    private Path edgeDir;
    private Path braveDir;

    private static final String VALID_BOOKMARKS_JSON = """
            {
              "checksum": "test-checksum",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks Bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "Example Link",
                      "type": "url",
                      "url": "https://example.com"
                    }
                  ]
                },
                "other": {
                  "id": "3",
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
        chromeDir = tempDir.resolve("Chrome");
        edgeDir = tempDir.resolve("Edge");
        braveDir = tempDir.resolve("Brave");

        when(pathResolver.resolveDataDirectory(BrowserType.CHROME)).thenReturn(chromeDir);
        when(pathResolver.resolveDataDirectory(BrowserType.EDGE)).thenReturn(edgeDir);
        when(pathResolver.resolveDataDirectory(BrowserType.BRAVE)).thenReturn(braveDir);
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
    @DisplayName("GET /api/browsers returns expected structure with discovered browsers and profiles")
    void testGetBrowsersSuccessfulDiscovery() throws Exception {
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);
        createProfileWithBookmarks(chromeDir, "Profile 1", VALID_BOOKMARKS_JSON);
        createLocalState(chromeDir, """
                {
                  "profile": {
                    "info_cache": {
                      "Default": { "name": "Personal" },
                      "Profile 1": { "name": "Work" }
                    }
                  }
                }
                """);

        mockMvc.perform(get("/api/browsers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.browsers", hasSize(3)))
                .andExpect(jsonPath("$.browsers[0].id", equalTo("chrome")))
                .andExpect(jsonPath("$.browsers[0].available", equalTo(true)))
                .andExpect(jsonPath("$.browsers[0].profiles", hasSize(2)))
                .andExpect(jsonPath("$.browsers[0].profiles[0].id", equalTo("chrome-default")))
                .andExpect(jsonPath("$.browsers[0].profiles[0].name", equalTo("Personal")))
                .andExpect(jsonPath("$.browsers[0].profiles[0].available", equalTo(true)))
                .andExpect(jsonPath("$.browsers[0].profiles[0].bookmarkAvailable", equalTo(true)))
                .andExpect(jsonPath("$.browsers[0].profiles[1].id", equalTo("chrome-profile-1")))
                .andExpect(jsonPath("$.browsers[0].profiles[1].name", equalTo("Work")));
    }

    @Test
    @DisplayName("GET /api/browsers handles empty discovery result when no browsers are installed")
    void testGetBrowsersEmptyDiscovery() throws Exception {
        // Point to non-existent directories
        when(pathResolver.resolveDataDirectory(BrowserType.CHROME)).thenReturn(tempDir.resolve("nonexistent_chrome"));
        when(pathResolver.resolveDataDirectory(BrowserType.EDGE)).thenReturn(tempDir.resolve("nonexistent_edge"));
        when(pathResolver.resolveDataDirectory(BrowserType.BRAVE)).thenReturn(tempDir.resolve("nonexistent_brave"));

        mockMvc.perform(get("/api/browsers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.browsers", hasSize(3)))
                .andExpect(jsonPath("$.browsers[0].available", equalTo(false)))
                .andExpect(jsonPath("$.browsers[0].profiles", empty()));
    }

    @Test
    @DisplayName("GET /api/browsers gracefully handles malformed profile metadata")
    void testGetBrowsersMalformedProfileMetadata() throws Exception {
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);
        createLocalState(chromeDir, "{ malformed: json, missing closing brace");

        mockMvc.perform(get("/api/browsers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.browsers[0].available", equalTo(true)))
                .andExpect(jsonPath("$.browsers[0].profiles", hasSize(1)))
                .andExpect(jsonPath("$.browsers[0].profiles[0].name", equalTo("Default")));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import succeeds and returns UploadResponse")
    void testImportBrowserProfileSuccess() throws Exception {
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        mockMvc.perform(post("/api/browsers/chrome/profiles/chrome-default/import"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId", notNullValue()))
                .andExpect(jsonPath("$.filename", equalTo("Google Chrome (Default)")))
                .andExpect(jsonPath("$.totalBookmarks", equalTo(1)))
                .andExpect(jsonPath("$.status", equalTo("PENDING")));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects invalid browser ID with 404")
    void testImportInvalidBrowserId() throws Exception {
        mockMvc.perform(post("/api/browsers/safari/profiles/default/import"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects invalid profile ID with 404")
    void testImportInvalidProfileId() throws Exception {
        createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

        mockMvc.perform(post("/api/browsers/chrome/profiles/unknown-profile/import"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)))
                .andExpect(jsonPath("$.message", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects missing bookmark file with 404")
    void testImportMissingBookmarkFile() throws Exception {
        // Directory exists, but no Bookmarks file
        Files.createDirectories(chromeDir.resolve("Default"));

        mockMvc.perform(post("/api/browsers/chrome/profiles/default/import"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", equalTo(404)));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects invalid JSON bookmark file with 400")
    void testImportInvalidJson() throws Exception {
        createProfileWithBookmarks(chromeDir, "Default", "{ not json ");

        mockMvc.perform(post("/api/browsers/chrome/profiles/default/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.error", equalTo("Bad Request")));
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects oversized bookmark file with 400")
    void testImportOversizedFile() throws Exception {
        long originalLimit = parserProperties.getMaxFileSize();
        try {
            parserProperties.setMaxFileSize(20L); // Set 20 bytes limit
            createProfileWithBookmarks(chromeDir, "Default", VALID_BOOKMARKS_JSON);

            mockMvc.perform(post("/api/browsers/chrome/profiles/default/import"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status", equalTo(400)));
        } finally {
            parserProperties.setMaxFileSize(originalLimit);
        }
    }

    @Test
    @DisplayName("POST /api/browsers/{browserId}/profiles/{profileId}/import rejects path traversal in profile ID with 400")
    void testImportPathTraversalRejected() throws Exception {
        mockMvc.perform(post("/api/browsers/chrome/profiles/..%2f..%2fetc/import"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", equalTo(400)))
                .andExpect(jsonPath("$.error", equalTo("Bad Request")));
    }
}
