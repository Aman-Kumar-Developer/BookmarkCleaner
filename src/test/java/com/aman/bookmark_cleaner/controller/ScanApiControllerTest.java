package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.service.ScanSessionManager;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ScanApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScanSessionManager sessionManager;

    @MockitoBean
    private BookmarkValidator bookmarkValidator;

    private static final String SAMPLE_BOOKMARKS_JSON = """
            {
              "checksum": "test-checksum",
              "roots": {
                "bookmark_bar": {
                  "id": "1",
                  "name": "Bookmarks bar",
                  "type": "folder",
                  "children": [
                    {
                      "id": "2",
                      "name": "Working Link",
                      "type": "url",
                      "url": "https://example.com/working",
                      "date_added": "13300000000000000"
                    },
                    {
                      "id": "3",
                      "name": "Broken Link",
                      "type": "url",
                      "url": "https://example.com/broken",
                      "date_added": "13300000000000001"
                    }
                  ]
                },
                "other": {
                  "id": "4",
                  "name": "Other bookmarks",
                  "type": "folder",
                  "children": []
                }
              },
              "version": 1
            }
            """;

    @Test
    @DisplayName("POST /api/bookmarks/upload uploads valid file and returns scanId")
    void testUploadBookmarkFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "bookmarks.json", "application/json", SAMPLE_BOOKMARKS_JSON.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/bookmarks/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").isNotEmpty())
                .andExpect(jsonPath("$.filename").value("bookmarks.json"))
                .andExpect(jsonPath("$.totalBookmarks").value(2))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/bookmarks/upload with empty file returns 400 Bad Request")
    void testUploadEmptyFileReturnsBadRequest() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.json", "application/json", new byte[0]
        );

        mockMvc.perform(multipart("/api/bookmarks/upload").file(emptyFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("empty")));
    }

    @Test
    @DisplayName("Full workflow: upload -> start scan -> get status -> query results -> toggle decision -> export")
    void testCompleteScanLifecycleWorkflow() throws Exception {
        // 1. Mock BookmarkValidator
        when(bookmarkValidator.validate(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            if (url.contains("broken")) {
                return ValidationResult.broken(url, 404, "HTTP 404 Not Found", 15);
            }
            return ValidationResult.working(url, 200, 10);
        });

        // 2. Upload file
        MockMultipartFile file = new MockMultipartFile(
                "file", "bookmarks.json", "application/json", SAMPLE_BOOKMARKS_JSON.getBytes(StandardCharsets.UTF_8)
        );

        String uploadResponse = mockMvc.perform(multipart("/api/bookmarks/upload").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Extract scanId
        String scanId = uploadResponse.split("\"scanId\":\"")[1].split("\"")[0];

        // 3. Start scan
        mockMvc.perform(post("/api/scans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scanId\":\"" + scanId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalBookmarks").value(2))
                .andExpect(jsonPath("$.brokenCount").value(1))
                .andExpect(jsonPath("$.workingCount").value(1));

        // 4. Get scan status
        mockMvc.perform(get("/api/scans/" + scanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.totalBookmarks").value(2))
                .andExpect(jsonPath("$.brokenCount").value(1))
                .andExpect(jsonPath("$.workingCount").value(1));

        // 5. Query paginated results (all)
        mockMvc.perform(get("/api/scans/" + scanId + "/results"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.summary.highCount").value(1))
                .andExpect(jsonPath("$.summary.lowCount").value(1))
                .andExpect(jsonPath("$.items", hasSize(2)));

        // 6. Query filtered by HIGH priority
        mockMvc.perform(get("/api/scans/" + scanId + "/results?priority=HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].priority").value("HIGH"))
                .andExpect(jsonPath("$.items[0].proposedAction").value("REMOVE_BOOKMARK"))
                .andExpect(jsonPath("$.items[0].enabled").value(true));

        // 7. Toggle decision on the broken item (turn off deletion)
        String resultsJson = mockMvc.perform(get("/api/scans/" + scanId + "/results?priority=HIGH"))
                .andReturn().getResponse().getContentAsString();
        String brokenItemId = resultsJson.split("\"itemId\":\"")[1].split("\"")[0];

        mockMvc.perform(patch("/api/scans/" + scanId + "/decisions/" + brokenItemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(brokenItemId))
                .andExpect(jsonPath("$.enabled").value(false));

        // Verify the decision was updated
        mockMvc.perform(get("/api/scans/" + scanId + "/results?priority=HIGH"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].enabled").value(false));

        // 8. Generate export
        mockMvc.perform(post("/api/scans/" + scanId + "/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scanId").value(scanId))
                .andExpect(jsonPath("$.exportFilename").value("bookmarks-cleaned.json"))
                .andExpect(jsonPath("$.changesAppliedCount").value(0)) // Because we set enabled=false for the broken item
                .andExpect(jsonPath("$.totalBookmarksExported").value(2))
                .andExpect(jsonPath("$.downloadUrl").value("/api/scans/" + scanId + "/export"));

        // 9. Download export
        mockMvc.perform(get("/api/scans/" + scanId + "/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"bookmarks-cleaned.json\""))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.roots.bookmark_bar.children", hasSize(2)));
    }

    @Test
    @DisplayName("PATCH /api/scans/{scanId}/decisions/{itemId} rejects toggle on disabled item with 400 Bad Request")
    void testToggleDisabledItemReturnsBadRequest() throws Exception {
        BookmarkNode node = BookmarkNode.createUrl("100", "Working", "https://example.com/ok");
        BookmarkNode bar = BookmarkNode.createFolder("0", "Bar");
        bar.addChild(node);
        BookmarkRoots roots = new BookmarkRoots(bar, null, null);
        BookmarkDocument doc = new BookmarkDocument(roots);

        ScanSession session = new ScanSession("scan-disabled-test", "bookmarks.json", doc);
        session.setStatus(ScanStatus.COMPLETED);

        // Disabled comparison item (NO_CHANGE)
        ComparisonItem noChangeItem = new ComparisonItem(
                "item-nochange", "100", "Working", "https://example.com/ok", "Bar",
                null, ValidationOutcome.WORKING, RedirectType.NONE,
                null, Priority.LOW, ProposedAction.NO_CHANGE,
                "HTTP 200 OK", false, false
        );
        session.setComparisonItems(List.of(noChangeItem));
        sessionManager.registerSession(session);

        mockMvc.perform(patch("/api/scans/scan-disabled-test/decisions/item-nochange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value(containsString("disabled")));
    }

    @Test
    @DisplayName("GET /api/scans/{scanId} with non-existent ID returns 404 Not Found")
    void testGetNonExistentScanReturns404() throws Exception {
        mockMvc.perform(get("/api/scans/non-existent-scan-id"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value(containsString("not found")));
    }
}
