package com.aman.bookmark_cleaner.parser;

import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonBookmarkReaderTest {

    private static final Path FIXTURES_DIR = Paths.get("src", "test", "resources", "fixtures");

    private BookmarkReader reader;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        reader = new JsonBookmarkReader(objectMapper);
    }

    @Test
    @DisplayName("Test 1: Basic bookmark parsing - reads URL bookmarks, names, URLs, IDs and roots")
    void testBasicBookmarkParsing() {
        Path path = FIXTURES_DIR.resolve("basic-bookmarks.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc, "Document must not be null");
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", doc.getChecksum());
        assertEquals(1, doc.getVersion());

        assertNotNull(doc.getRoots(), "Roots must not be null");
        BookmarkNode bar = doc.getRoots().getBookmarkBar();
        assertNotNull(bar, "Bookmarks bar root must be present");
        assertEquals("1", bar.getId());
        assertEquals("Bookmarks bar", bar.getName());
        assertTrue(bar.isFolder());
        assertFalse(bar.isUrl());

        List<BookmarkNode> children = bar.getChildren();
        assertNotNull(children);
        assertEquals(3, children.size());

        BookmarkNode b1 = children.get(0);
        assertEquals("101", b1.getId());
        assertEquals("GitHub", b1.getName());
        assertEquals("https://github.com", b1.getUrl());
        assertTrue(b1.isUrl());
        assertFalse(b1.isFolder());

        BookmarkNode b2 = children.get(1);
        assertEquals("102", b2.getId());
        assertEquals("Spring Framework", b2.getName());
        assertEquals("https://spring.io", b2.getUrl());

        BookmarkNode b3 = children.get(2);
        assertEquals("103", b3.getId());
        assertEquals("Brave Browser", b3.getName());
        assertEquals("https://brave.com", b3.getUrl());

        assertEquals(3, doc.getTotalBookmarkCount());
    }

    @Test
    @DisplayName("Test 2: Nested folder parsing - preserves multi-level folder hierarchy")
    void testNestedFolderParsing() {
        Path path = FIXTURES_DIR.resolve("nested-folders.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);
        BookmarkNode bar = doc.getRoots().getBookmarkBar();
        assertNotNull(bar);

        // Level 1: Dev Resources
        assertEquals(1, bar.getChildren().size());
        BookmarkNode devResources = bar.getChildren().get(0);
        assertEquals("10", devResources.getId());
        assertEquals("Dev Resources", devResources.getName());
        assertTrue(devResources.isFolder());
        assertEquals(2, devResources.getChildren().size());

        // Level 2: Documentation (folder) and Direct Link (url)
        BookmarkNode documentation = devResources.getChildren().get(0);
        assertEquals("11", documentation.getId());
        assertEquals("Documentation", documentation.getName());
        assertTrue(documentation.isFolder());

        BookmarkNode directLink = devResources.getChildren().get(1);
        assertEquals("15", directLink.getId());
        assertEquals("https://developer.mozilla.org", directLink.getUrl());
        assertTrue(directLink.isUrl());

        // Level 3: Java Docs (url) and Deep Dive (folder)
        assertEquals(2, documentation.getChildren().size());
        BookmarkNode javaDocs = documentation.getChildren().get(0);
        assertEquals("12", javaDocs.getId());
        assertEquals("Java Docs", javaDocs.getName());
        assertEquals("https://docs.oracle.com/en/java/", javaDocs.getUrl());

        BookmarkNode deepDive = documentation.getChildren().get(1);
        assertEquals("13", deepDive.getId());
        assertEquals("Deep Dive", deepDive.getName());
        assertTrue(deepDive.isFolder());

        // Level 4: JVM Specification (url)
        assertEquals(1, deepDive.getChildren().size());
        BookmarkNode jvmSpec = deepDive.getChildren().get(0);
        assertEquals("14", jvmSpec.getId());
        assertEquals("https://docs.oracle.com/javase/specs/jvms/se21/html/", jvmSpec.getUrl());

        // Document helpers check
        List<BookmarkNode> allUrls = doc.getAllUrlBookmarks();
        assertEquals(3, allUrls.size(), "Should find exactly 3 URL bookmarks in nested structure");
        assertTrue(allUrls.stream().anyMatch(u -> "Java Docs".equals(u.getName())));
        assertTrue(allUrls.stream().anyMatch(u -> "Direct Link in Dev Resources".equals(u.getName())));
        assertTrue(allUrls.stream().anyMatch(u -> "JVM Specification".equals(u.getName())));
    }

    @Test
    @DisplayName("Test 3: Multiple bookmark roots - handles standard and custom root folders")
    void testMultipleBookmarkRoots() {
        Path path = FIXTURES_DIR.resolve("multiple-roots.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);
        assertNotNull(doc.getRoots());

        Map<String, BookmarkNode> allRoots = doc.getRoots().getAllRoots();
        assertEquals(4, allRoots.size(), "Should have 4 roots: bookmark_bar, other, synced, custom_root");

        // Standard roots
        assertNotNull(doc.getRoots().getBookmarkBar());
        assertEquals("Bar Item", doc.getRoots().getBookmarkBar().getChildren().get(0).getName());

        assertNotNull(doc.getRoots().getOther());
        assertEquals("Other Item", doc.getRoots().getOther().getChildren().get(0).getName());

        assertNotNull(doc.getRoots().getSynced());
        assertEquals("Mobile Item", doc.getRoots().getSynced().getChildren().get(0).getName());

        // Custom additional root
        BookmarkNode customRoot = doc.getRoots().getRoot("custom_root");
        assertNotNull(customRoot, "Custom root must be accessible via getRoot");
        assertEquals("4", customRoot.getId());
        assertEquals("Custom Archive Root", customRoot.getName());
        assertEquals(1, customRoot.getChildren().size());
        assertEquals("Custom Root Item", customRoot.getChildren().get(0).getName());
        assertEquals("https://custom.example.com", customRoot.getChildren().get(0).getUrl());

        assertEquals(4, doc.getTotalBookmarkCount());
    }

    @Test
    @DisplayName("Test 4: Empty folders - correctly represents empty folder structures")
    void testEmptyFolders() {
        Path path = FIXTURES_DIR.resolve("empty-folders.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);
        BookmarkNode bar = doc.getRoots().getBookmarkBar();
        assertNotNull(bar);
        assertEquals(2, bar.getChildren().size());

        BookmarkNode emptyFolder1 = bar.getChildren().get(0);
        assertTrue(emptyFolder1.isFolder());
        assertFalse(emptyFolder1.isUrl());
        assertNotNull(emptyFolder1.getChildren());
        assertTrue(emptyFolder1.getChildren().isEmpty());
        assertFalse(emptyFolder1.hasChildren());
        assertNull(emptyFolder1.getUrl());

        BookmarkNode parentOfEmpty = bar.getChildren().get(1);
        assertTrue(parentOfEmpty.isFolder());
        assertEquals(1, parentOfEmpty.getChildren().size());

        BookmarkNode nestedEmpty = parentOfEmpty.getChildren().get(0);
        assertTrue(nestedEmpty.isFolder());
        assertTrue(nestedEmpty.getChildren().isEmpty());
        assertFalse(nestedEmpty.hasChildren());

        assertEquals(0, doc.getTotalBookmarkCount(), "Empty folders file should contain 0 URL bookmarks");
    }

    @Test
    @DisplayName("Test 5: Optional metadata - parses date fields, guid, and meta_info")
    void testOptionalMetadata() {
        Path path = FIXTURES_DIR.resolve("optional-metadata.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);
        BookmarkNode bar = doc.getRoots().getBookmarkBar();
        assertNotNull(bar);
        assertEquals(2, bar.getChildren().size());

        BookmarkNode rich = bar.getChildren().get(0);
        assertEquals("50", rich.getId());
        assertEquals("13318239012345678", rich.getDateAdded());
        assertEquals("13318239099999999", rich.getDateLastUsed());
        assertEquals("13318239088888888", rich.getDateModified());
        assertEquals("5c9b68e7-91f8-4e5a-96da-72a392bb1cf2", rich.getGuid());
        assertNotNull(rich.getMetaInfo());
        assertEquals("true", rich.getMetaInfo().get("last_visited_desktop"));
        assertEquals("xyz123", rich.getMetaInfo().get("power_bookmark_meta"));

        // Second item has missing optional properties
        BookmarkNode minimal = bar.getChildren().get(1);
        assertEquals("51", minimal.getId());
        assertNull(minimal.getDateAdded());
        assertNull(minimal.getDateLastUsed());
        assertNull(minimal.getDateModified());
        assertNull(minimal.getGuid());
        assertNull(minimal.getMetaInfo());
    }

    @Test
    @DisplayName("Test 6: Unknown metadata - preserves extension and browser-specific fields")
    void testUnknownMetadataPreservation() {
        Path path = FIXTURES_DIR.resolve("unknown-metadata.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);

        // Document-level extension fields
        assertEquals("chromium-cleaner-source", doc.getAdditionalProperty("custom_app_flag"));
        assertNotNull(doc.getAdditionalProperty("sync_metadata"));

        // Folder-level extension fields
        BookmarkNode bar = doc.getRoots().getBookmarkBar();
        assertNotNull(bar);
        assertEquals("#FF5733", bar.getAdditionalProperty("folder_theme_color"));

        // Node-level extension fields
        assertEquals(1, bar.getChildren().size());
        BookmarkNode node = bar.getChildren().get(0);
        assertEquals("reading-list", node.getAdditionalProperty("custom_tag"));
        assertEquals(Boolean.TRUE, node.getAdditionalProperty("is_pinned"));
        assertEquals(98.5, ((Number) node.getAdditionalProperty("custom_ranking_score")).doubleValue());
    }

    @Test
    @DisplayName("Test 7: Invalid JSON - throws InvalidBookmarkException with meaningful message")
    void testInvalidJsonProducesMeaningfulException() {
        Path path = FIXTURES_DIR.resolve("invalid-json.json");
        InvalidBookmarkException ex = assertThrows(InvalidBookmarkException.class, () -> reader.read(path));

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().toLowerCase().contains("invalid json") || ex.getMessage().toLowerCase().contains("syntax"),
                "Exception message should mention invalid JSON: " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 8: Unsupported structure - rejects documents missing required 'roots' object")
    void testUnsupportedStructureRejected() {
        Path path = FIXTURES_DIR.resolve("unsupported-structure.json");
        InvalidBookmarkException ex = assertThrows(InvalidBookmarkException.class, () -> reader.read(path));

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("roots"), "Exception message should indicate missing 'roots': " + ex.getMessage());
    }

    @Test
    @DisplayName("Test 9: Missing file - throws BookmarkFileNotFoundException for nonexistent path")
    void testMissingFileHandledCorrectly() {
        Path nonExistent = FIXTURES_DIR.resolve("does-not-exist.json");
        BookmarkFileNotFoundException ex = assertThrows(BookmarkFileNotFoundException.class, () -> reader.read(nonExistent));
        assertTrue(ex.getMessage().contains("not found"));

        assertThrows(BookmarkFileNotFoundException.class, () -> reader.read(null));
    }

    @Test
    @DisplayName("Test 10: Original file integrity - reading file does not alter file content or hash")
    void testOriginalFileIntegrity() throws IOException, NoSuchAlgorithmException {
        Path path = FIXTURES_DIR.resolve("basic-bookmarks.json");

        byte[] bytesBefore = Files.readAllBytes(path);
        String hashBefore = computeSha256(bytesBefore);

        // Execute parsing
        BookmarkDocument doc = reader.read(path);
        assertNotNull(doc);

        byte[] bytesAfter = Files.readAllBytes(path);
        String hashAfter = computeSha256(bytesAfter);

        assertEquals(bytesBefore.length, bytesAfter.length, "File size must remain identical");
        assertEquals(hashBefore, hashAfter, "File SHA-256 hash must be strictly identical before and after reading");
    }

    @Test
    @DisplayName("Test 11: Empty file - 0-byte file produces InvalidBookmarkException")
    void testEmptyFileProducesInvalidBookmarkException() {
        Path emptyFile = FIXTURES_DIR.resolve("empty-file.json");
        InvalidBookmarkException ex = assertThrows(InvalidBookmarkException.class, () -> reader.read(emptyFile));
        assertTrue(ex.getMessage().contains("empty"));
    }

    @Test
    @DisplayName("Test 12: Directory supplied instead of file produces InvalidBookmarkException")
    void testDirectoryPathProducesInvalidBookmarkException() {
        InvalidBookmarkException ex = assertThrows(InvalidBookmarkException.class, () -> reader.read(FIXTURES_DIR));
        assertTrue(ex.getMessage().contains("directory"));
    }

    @Test
    @DisplayName("Test 13: File size limit enforcement produces BookmarkReaderException")
    void testFileSizeLimitEnforcement() {
        // Create a reader configured with a tiny 50-byte maximum limit
        BookmarkReader limitedReader = new JsonBookmarkReader(objectMapper, 50L);
        Path path = FIXTURES_DIR.resolve("basic-bookmarks.json");

        BookmarkReaderException ex = assertThrows(BookmarkReaderException.class, () -> limitedReader.read(path));
        assertTrue(ex.getMessage().contains("exceeds maximum permitted limit"));
    }

    @Test
    @DisplayName("Test 14: Empty roots collection - parses cleanly without error")
    void testEmptyRootsCollectionParsedSuccessfully() {
        Path path = FIXTURES_DIR.resolve("empty-roots.json");
        BookmarkDocument doc = reader.read(path);

        assertNotNull(doc);
        assertNotNull(doc.getRoots());
        assertTrue(doc.getRoots().getAllRoots().isEmpty());
        assertEquals(0, doc.getTotalBookmarkCount());
    }

    private String computeSha256(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return HexFormat.of().formatHex(hash);
    }
}
