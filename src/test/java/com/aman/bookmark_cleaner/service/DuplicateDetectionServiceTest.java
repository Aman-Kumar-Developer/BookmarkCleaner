package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.DuplicateBookmarkItem;
import com.aman.bookmark_cleaner.model.DuplicateGroup;
import com.aman.bookmark_cleaner.model.DuplicateType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DuplicateDetectionServiceTest {

    private DuplicateDetectionService duplicateService;

    @BeforeEach
    void setUp() {
        duplicateService = new DuplicateDetectionService();
    }

    @Test
    @DisplayName("Exact duplicate URLs detected and grouped, retaining first occurrence")
    void testExactDuplicateUrls() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "GitHub Main", "https://github.com", "Bookmarks bar"),
                new DuplicateBookmarkItem("2", "GitHub Copy", "https://github.com", "Bookmarks bar / Tools")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        DuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.EXACT, group.getDuplicateType());
        assertEquals("1", group.getRetainedBookmark().getBookmarkId());
        assertEquals(1, group.getRemovalCandidates().size());
        assertEquals("2", group.getRemovalCandidates().get(0).getBookmarkId());
    }

    @Test
    @DisplayName("Three or more copies of the same URL grouped together, retaining only the first")
    void testMultipleCopiesOfSameUrl() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("10", "First", "https://example.com/doc", "Folder A"),
                new DuplicateBookmarkItem("20", "Second", "https://example.com/doc", "Folder B"),
                new DuplicateBookmarkItem("30", "Third", "https://example.com/doc", "Folder C")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        DuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.EXACT, group.getDuplicateType());
        assertEquals(3, group.getAllItems().size());
        assertEquals("10", group.getRetainedBookmark().getBookmarkId());
        assertEquals(2, group.getRemovalCandidates().size());
        assertEquals("20", group.getRemovalCandidates().get(0).getBookmarkId());
        assertEquals("30", group.getRemovalCandidates().get(1).getBookmarkId());
    }

    @Test
    @DisplayName("Same URL with different bookmark names still detected as exact duplicate")
    void testSameUrlDifferentNames() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Spring", "https://spring.io", "Folder 1"),
                new DuplicateBookmarkItem("2", "Spring Boot Framework", "https://spring.io", "Folder 2")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        assertEquals(DuplicateType.EXACT, groups.get(0).getDuplicateType());
    }

    @Test
    @DisplayName("HTTP vs HTTPS variants detected as POTENTIAL duplicate, not exact duplicate")
    void testHttpVsHttpsVariants() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Insecure Site", "http://example.com/guide", "Folder A"),
                new DuplicateBookmarkItem("2", "Secure Site", "https://example.com/guide", "Folder B")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        DuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.POTENTIAL, group.getDuplicateType());
        assertEquals("1", group.getRetainedBookmark().getBookmarkId());
        assertEquals("2", group.getRemovalCandidates().get(0).getBookmarkId());
    }

    @Test
    @DisplayName("WWW vs non-WWW variants detected as POTENTIAL duplicate")
    void testWwwVsNonWwwVariants() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "WWW Site", "https://www.example.com/news", "Folder 1"),
                new DuplicateBookmarkItem("2", "Bare Site", "https://example.com/news", "Folder 2")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        DuplicateGroup group = groups.get(0);
        assertEquals(DuplicateType.POTENTIAL, group.getDuplicateType());
    }

    @Test
    @DisplayName("Different paths on same domain are NOT duplicates")
    void testDifferentPathsAreNotDuplicates() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Page 1", "https://example.com/page1", "Folder"),
                new DuplicateBookmarkItem("2", "Page 2", "https://example.com/page2", "Folder")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertTrue(groups.isEmpty(), "Different paths on the same domain must not be classified as duplicates");
    }

    @Test
    @DisplayName("Different query parameters are NOT duplicates")
    void testDifferentQueryParamsAreNotDuplicates() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Item 100", "https://example.com/item?id=100", "Shop"),
                new DuplicateBookmarkItem("2", "Item 200", "https://example.com/item?id=200", "Shop")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertTrue(groups.isEmpty(), "Meaningful query parameters must not be normalized away");
    }

    @Test
    @DisplayName("Identical URL fragments are matched as exact duplicates")
    void testUrlFragmentsNormalizedForExactMatch() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Doc Overview 1", "https://example.com/doc#overview", "Docs"),
                new DuplicateBookmarkItem("2", "Doc Overview 2", "https://example.com/doc#overview", "Docs")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertEquals(1, groups.size());
        assertEquals(DuplicateType.EXACT, groups.get(0).getDuplicateType());
    }

    @Test
    @DisplayName("Different URL fragments (e.g. SPA hash routes) are NOT duplicates")
    void testDifferentUrlFragmentsAreNotDuplicates() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "App Dashboard", "https://example.com/app/#/dashboard", "App"),
                new DuplicateBookmarkItem("2", "App Settings", "https://example.com/app/#/settings", "App")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertTrue(groups.isEmpty(), "Distinct fragments or SPA hash routes must be preserved as distinct bookmarks");
    }

    @Test
    @DisplayName("Unique bookmarks produce no duplicate groups")
    void testUniqueBookmarks() {
        List<DuplicateBookmarkItem> items = List.of(
                new DuplicateBookmarkItem("1", "Google", "https://google.com", "Search"),
                new DuplicateBookmarkItem("2", "Bing", "https://bing.com", "Search"),
                new DuplicateBookmarkItem("3", "DuckDuckGo", "https://duckduckgo.com", "Search")
        );

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertTrue(groups.isEmpty());
    }

    @Test
    @DisplayName("Bookmark tree document structure is not modified during duplicate detection")
    void testTreeRemainsUnmodified() {
        BookmarkRoots roots = new BookmarkRoots();
        BookmarkNode bar = BookmarkNode.createFolder("1", "Bookmarks bar");

        BookmarkNode b1 = BookmarkNode.createUrl("101", "Site A", "https://example.com");
        BookmarkNode b2 = BookmarkNode.createUrl("102", "Site A Copy", "https://example.com");

        bar.addChild(b1);
        bar.addChild(b2);
        roots.setBookmarkBar(bar);

        BookmarkDocument doc = new BookmarkDocument(roots);

        List<DuplicateGroup> groups = duplicateService.findDuplicates(doc);

        assertEquals(1, groups.size());
        assertEquals(2, bar.getChildren().size(), "Original document children count must remain intact");
        assertSame(b1, bar.getChildren().get(0));
        assertSame(b2, bar.getChildren().get(1));
        assertEquals("https://example.com", b1.getUrl());
        assertEquals("https://example.com", b2.getUrl());
    }

    @Test
    @DisplayName("Bookmarks with missing or blank URLs are skipped safely")
    void testMissingOrBlankUrlsHandledSafely() {
        List<DuplicateBookmarkItem> items = new ArrayList<>();
        items.add(new DuplicateBookmarkItem("1", "Blank 1", "", "Folder"));
        items.add(new DuplicateBookmarkItem("2", "Blank 2", null, "Folder"));
        items.add(new DuplicateBookmarkItem("3", "Valid", "https://example.com", "Folder"));

        List<DuplicateGroup> groups = duplicateService.findDuplicatesInList(items);

        assertTrue(groups.isEmpty());
    }
}
