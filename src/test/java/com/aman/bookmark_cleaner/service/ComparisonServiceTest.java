package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkAnalysisItem;
import com.aman.bookmark_cleaner.model.BookmarkAnalysisReport;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.DuplicateType;
import com.aman.bookmark_cleaner.model.Priority;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.RedirectChain;
import com.aman.bookmark_cleaner.model.RedirectStep;
import com.aman.bookmark_cleaner.model.RedirectType;
import com.aman.bookmark_cleaner.model.ValidationOutcome;
import com.aman.bookmark_cleaner.model.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonServiceTest {

    private ComparisonService comparisonService;

    @BeforeEach
    void setUp() {
        comparisonService = new ComparisonService();
    }

    @Test
    @DisplayName("Confirmed broken bookmark becomes HIGH priority, REMOVE_BOOKMARK, default ON")
    void testBrokenBookmarkClassification() {
        ValidationResult brokenResult = ValidationResult.broken("https://broken.example.com", 404, "HTTP 404 Not Found", 10);
        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b1", "Broken Site", "https://broken.example.com", "Bookmarks bar", brokenResult, null, null, true);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.HIGH, comp.getPriority());
        assertEquals(ProposedAction.REMOVE_BOOKMARK, comp.getProposedAction());
        assertTrue(comp.isApplicable());
        assertTrue(comp.isEnabled(), "HIGH priority broken bookmark must default to ON");
        assertNull(comp.getProposedUrl(), "Proposed URL should be null for removal");
    }

    @Test
    @DisplayName("Cross-domain redirect becomes HIGH priority, UPDATE_URL, default ON")
    void testCrossDomainRedirectClassification() {
        RedirectStep step = new RedirectStep(301, "https://old.example.com", "https://newsite.com", "https://newsite.com");
        RedirectChain chain = new RedirectChain("https://old.example.com", "https://newsite.com", List.of(step), RedirectType.CROSS_DOMAIN);
        ValidationResult redirectResult = ValidationResult.redirected("https://old.example.com", "https://newsite.com", 200, chain, 20);

        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b2", "Old Site", "https://old.example.com", "Folder", redirectResult, null, null, true);
        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.HIGH, comp.getPriority());
        assertEquals(ProposedAction.UPDATE_URL, comp.getProposedAction());
        assertTrue(comp.isApplicable());
        assertTrue(comp.isEnabled(), "Cross-domain redirect must default to ON");
        assertEquals("https://newsite.com", comp.getProposedUrl());
    }

    @Test
    @DisplayName("Same-website redirect becomes MEDIUM priority, UPDATE_URL, default ON")
    void testSameWebsiteRedirectClassification() {
        RedirectStep step = new RedirectStep(301, "http://example.com/docs", "https://example.com/docs", "https://example.com/docs");
        RedirectChain chain = new RedirectChain("http://example.com/docs", "https://example.com/docs", List.of(step), RedirectType.SAME_WEBSITE);
        ValidationResult redirectResult = ValidationResult.redirected("http://example.com/docs", "https://example.com/docs", 200, chain, 15);

        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b3", "Docs", "http://example.com/docs", "Folder", redirectResult, null, null, true);
        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.MEDIUM, comp.getPriority());
        assertEquals(ProposedAction.UPDATE_URL, comp.getProposedAction());
        assertTrue(comp.isApplicable());
        assertTrue(comp.isEnabled(), "Same-website redirect must default to ON");
        assertEquals("https://example.com/docs", comp.getProposedUrl());
    }

    @Test
    @DisplayName("Exact duplicate removal proposal becomes MEDIUM priority, REMOVE_DUPLICATE, default ON")
    void testExactDuplicateRemovalCandidate() {
        ValidationResult working = ValidationResult.working("https://github.com", 200, 10);
        // designatedRetained = false means this is a removal candidate
        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b4", "GitHub Copy", "https://github.com", "Folder / Sub", working, "dup-1", DuplicateType.EXACT, false);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.MEDIUM, comp.getPriority());
        assertEquals(ProposedAction.REMOVE_DUPLICATE, comp.getProposedAction());
        assertTrue(comp.isApplicable());
        assertTrue(comp.isEnabled(), "Duplicate removal must default to ON");
        assertNull(comp.getProposedUrl());
    }

    @Test
    @DisplayName("Potential duplicate removal proposal becomes MEDIUM priority, REMOVE_DUPLICATE, default ON")
    void testPotentialDuplicateRemovalCandidate() {
        ValidationResult working = ValidationResult.working("http://github.com", 200, 10);
        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b5", "GitHub Variant", "http://github.com", "Folder", working, "dup-2", DuplicateType.POTENTIAL, false);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.MEDIUM, comp.getPriority());
        assertEquals(ProposedAction.REMOVE_DUPLICATE, comp.getProposedAction());
        assertTrue(comp.isApplicable());
        assertTrue(comp.isEnabled());
    }

    @Test
    @DisplayName("Unchanged working bookmark becomes LOW priority, NO_CHANGE, disabled")
    void testWorkingBookmarkClassification() {
        ValidationResult working = ValidationResult.working("https://example.com", 200, 5);
        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b6", "Example", "https://example.com", "Bookmarks bar", working, null, null, true);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.LOW, comp.getPriority());
        assertEquals(ProposedAction.NO_CHANGE, comp.getProposedAction());
        assertFalse(comp.isApplicable(), "Working bookmark has no actionable proposal");
        assertFalse(comp.isEnabled(), "Working bookmark must be disabled");
    }

    @Test
    @DisplayName("UNKNOWN validation outcome is NOT marked as broken; defaults to LOW, NO_CHANGE, disabled")
    void testUnknownOutcomeClassification() {
        ValidationResult unknown = ValidationResult.unknown("https://restricted.example.com", 403, "HTTP 403 Forbidden", 10);
        BookmarkAnalysisItem item = new BookmarkAnalysisItem("b7", "Restricted", "https://restricted.example.com", "Folder", unknown, null, null, true);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(item), Collections.emptyList(), 1);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(1, results.size());
        ComparisonItem comp = results.get(0);
        assertEquals(Priority.LOW, comp.getPriority());
        assertEquals(ProposedAction.NO_CHANGE, comp.getProposedAction());
        assertFalse(comp.isApplicable());
        assertFalse(comp.isEnabled());
        assertNotEquals(ProposedAction.REMOVE_BOOKMARK, comp.getProposedAction(), "UNKNOWN result must never propose deletion");
    }

    @Test
    @DisplayName("Comparison results are sorted with HIGH before MEDIUM before LOW")
    void testPrioritySortingOrder() {
        ValidationResult working = ValidationResult.working("https://low.example.com", 200, 5);
        ValidationResult broken = ValidationResult.broken("https://high.example.com", 404, "404", 5);
        RedirectStep step = new RedirectStep(301, "http://med.example.com", "https://med.example.com", "https://med.example.com");
        RedirectChain chain = new RedirectChain("http://med.example.com", "https://med.example.com", List.of(step), RedirectType.SAME_WEBSITE);
        ValidationResult redirect = ValidationResult.redirected("http://med.example.com", "https://med.example.com", 200, chain, 5);

        BookmarkAnalysisItem lowItem = new BookmarkAnalysisItem("1", "Low", "https://low.example.com", "F", working, null, null, true);
        BookmarkAnalysisItem highItem = new BookmarkAnalysisItem("2", "High", "https://high.example.com", "F", broken, null, null, true);
        BookmarkAnalysisItem medItem = new BookmarkAnalysisItem("3", "Medium", "http://med.example.com", "F", redirect, null, null, true);

        BookmarkAnalysisReport report = new BookmarkAnalysisReport(List.of(lowItem, highItem, medItem), Collections.emptyList(), 3);
        List<ComparisonItem> results = comparisonService.buildComparisonItems(report);

        assertEquals(3, results.size());
        assertEquals(Priority.HIGH, results.get(0).getPriority());
        assertEquals(Priority.MEDIUM, results.get(1).getPriority());
        assertEquals(Priority.LOW, results.get(2).getPriority());
    }
}
