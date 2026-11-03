package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class BookmarkProcessingIntegrationTest {

    private static final Path FIXTURES_DIR = Paths.get("src", "test", "resources", "fixtures");

    @Test
    @DisplayName("End-to-end integration: read fixture, detect duplicates and analyze bookmarks")
    void testStage1AndStage2Integration() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        BookmarkReader reader = new JsonBookmarkReader(objectMapper);

        // Read Stage 1 basic bookmarks fixture
        Path path = FIXTURES_DIR.resolve("basic-bookmarks.json");
        BookmarkDocument document = reader.read(path);
        assertNotNull(document);
        assertEquals(3, document.getTotalBookmarkCount());

        BookmarkValidator mockValidator = url -> {
            if (url.contains("github")) {
                return ValidationResult.working(url, 200, 10);
            } else if (url.contains("spring")) {
                return ValidationResult.working(url, 200, 15);
            } else {
                return ValidationResult.broken(url, 404, "HTTP 404 Not Found", 20);
            }
        };

        BookmarkValidationProperties props = new BookmarkValidationProperties();
        try (BookmarkProcessingEngine engine = new BookmarkProcessingEngine(mockValidator, new DuplicateDetectionService(), props)) {
            BookmarkAnalysisReport report = engine.analyze(document);

            assertEquals(3, report.getTotalBookmarksAnalyzed());
            assertEquals(2, report.getCount(ValidationOutcome.WORKING));
            assertEquals(1, report.getCount(ValidationOutcome.BROKEN));
            assertEquals(3, report.getUniqueUrlsCount());
            assertTrue(report.getDuplicateGroups().isEmpty());
        }
    }
}
