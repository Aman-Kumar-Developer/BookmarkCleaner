package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.ScanStatusResponse;
import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ScanStatus;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RealPipelineProgressVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScanSessionManager sessionManager;

    @MockitoBean
    private BookmarkValidator bookmarkValidator;

    private String generateBookmarksJson(int count) throws Exception {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        for (int i = 1; i <= count; i++) {
            bar.addChild(BookmarkNode.createUrl("id-" + i, "Bookmark " + i, "https://domain" + (i % 10) + ".example.com/page/" + i));
        }
        roots.setBookmarkBar(bar);
        BookmarkDocument doc = new BookmarkDocument(roots);

        ObjectMapper mapper = JsonMapper.builder().build();
        return mapper.writeValueAsString(doc);
    }

    @Test
    @DisplayName("Verify real end-to-end pipeline produces incremental progress updates from 0% to 100%")
    void testRealProcessingPipelineProducesIncrementalProgressUpdates() throws Exception {
        int totalBookmarks = 60;
        String jsonContent = generateBookmarksJson(totalBookmarks);

        // Simulate realistic network validation with slight latency (80ms per bookmark)
        when(bookmarkValidator.validate(anyString())).thenAnswer(invocation -> {
            String url = invocation.getArgument(0);
            Thread.sleep(80);
            if (url.contains("page/5") || url.contains("page/15")) {
                return ValidationResult.broken(url, 404, "Not Found", 80);
            }
            return ValidationResult.working(url, 200, 80);
        });

        // 1. Upload
        MockMultipartFile file = new MockMultipartFile(
                "file", "bookmarks-60.json", "application/json", jsonContent.getBytes(StandardCharsets.UTF_8)
        );

        String uploadResponseStr = mockMvc.perform(multipart("/api/bookmarks/upload").file(file))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        ObjectMapper mapper = JsonMapper.builder().build();
        UploadResponse uploadResponse = mapper.readValue(uploadResponseStr, UploadResponse.class);
        String scanId = uploadResponse.getScanId();
        assertEquals(totalBookmarks, uploadResponse.getTotalBookmarks());

        // 2. Start scan (asynchronous)
        String startResponseStr = mockMvc.perform(post("/api/scans")
                        .contentType("application/json")
                        .content("{\"scanId\":\"" + scanId + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        tools.jackson.databind.JsonNode startNode = mapper.readTree(startResponseStr);
        assertEquals("RUNNING", startNode.get("status").asText());
        assertEquals(totalBookmarks, startNode.get("totalBookmarks").asInt());

        // 3. Poll while scan progresses and record real progress snapshots
        List<tools.jackson.databind.JsonNode> snapshots = new ArrayList<>();
        snapshots.add(startNode);

        long deadline = System.currentTimeMillis() + 10000;
        tools.jackson.databind.JsonNode latestNode = startNode;

        while (!"COMPLETED".equals(latestNode.get("status").asText()) &&
               !"FAILED".equals(latestNode.get("status").asText()) &&
               System.currentTimeMillis() < deadline) {
            Thread.sleep(15);

            String pollResponseStr = mockMvc.perform(get("/api/scans/" + scanId))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            latestNode = mapper.readTree(pollResponseStr);
            snapshots.add(latestNode);
        }

        // 4. Verification
        assertEquals("COMPLETED", latestNode.get("status").asText(), "Scan must eventually reach COMPLETED");
        assertEquals(100, latestNode.get("progressPercentage").asInt(), "Final progress must be 100%");
        assertEquals(totalBookmarks, latestNode.get("completedBookmarks").asInt());
        assertEquals(0, latestNode.get("pendingBookmarks").asInt());

        // Check captured intermediate percentages
        List<Integer> percentages = snapshots.stream()
                .map(n -> n.get("progressPercentage").asInt())
                .distinct()
                .toList();

        List<Integer> completedCounts = snapshots.stream()
                .map(n -> n.get("completedBookmarks").asInt())
                .distinct()
                .toList();

        System.out.println("=== REAL-TIME PROGRESS EVIDENCE ===");
        System.out.println("Captured distinct percentages: " + percentages);
        System.out.println("Captured distinct completed counts: " + completedCounts);
        System.out.println("Total snapshots recorded: " + snapshots.size());
        for (int i = 0; i < Math.min(10, snapshots.size()); i++) {
            tools.jackson.databind.JsonNode s = snapshots.get(i);
            System.out.println(String.format("Snapshot [%d]: status=%s, completed=%d/%d, pending=%d, progress=%d%%",
                    i, s.get("status").asText(), s.get("completedBookmarks").asInt(), s.get("totalBookmarks").asInt(),
                    s.get("pendingBookmarks").asInt(), s.get("progressPercentage").asInt()));
        }
        tools.jackson.databind.JsonNode finalSnap = snapshots.get(snapshots.size() - 1);
        System.out.println(String.format("Final Snapshot: status=%s, completed=%d/%d, pending=%d, progress=%d%%",
                finalSnap.get("status").asText(), finalSnap.get("completedBookmarks").asInt(), finalSnap.get("totalBookmarks").asInt(),
                finalSnap.get("pendingBookmarks").asInt(), finalSnap.get("progressPercentage").asInt()));
        System.out.println("===================================");

        // Ensure we observed multiple distinct intermediate progress values
        assertTrue(percentages.size() >= 4,
                "Must capture at least 4 distinct progress percentages during execution, captured: " + percentages);
        assertTrue(completedCounts.size() >= 4,
                "Must capture at least 4 distinct completed counts during execution, captured: " + completedCounts);

        // Ensure first snapshot was 0%
        assertEquals(0, percentages.get(0), "Initial progress must be 0%");
        // Ensure last snapshot was 100%
        assertEquals(100, percentages.get(percentages.size() - 1), "Final progress must be 100%");
    }
}
