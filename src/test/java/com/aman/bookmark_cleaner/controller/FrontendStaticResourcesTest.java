package com.aman.bookmark_cleaner.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FrontendStaticResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET / forwards to index.html welcome page")
    void testRootEndpointServesIndexHtml() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    @DisplayName("GET /index.html serves index.html dashboard directly including browser discovery and manual upload")
    void testIndexHtmlDirectAccess() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Bookmark Cleaner")))
                .andExpect(content().string(containsString("browser-discovery-section")))
                .andExpect(content().string(containsString("browser-discovery-loading")))
                .andExpect(content().string(containsString("browser-discovery-error")))
                .andExpect(content().string(containsString("btn-retry-discovery")))
                .andExpect(content().string(containsString("browser-cards-grid")))
                .andExpect(content().string(containsString("import-divider")))
                .andExpect(content().string(containsString("upload-dropzone")))
                .andExpect(content().string(containsString("comparison-table")))
                .andExpect(content().string(containsString("btn-generate-export")));
    }

    @Test
    @DisplayName("GET /css/styles.css serves global stylesheet and component styles")
    void testCssStylesheetsServed() throws Exception {
        mockMvc.perform(get("/css/styles.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("--color-primary: #2563eb")));

        mockMvc.perform(get("/css/components.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".comparison-table")))
                .andExpect(content().string(containsString(".browser-discovery-section")))
                .andExpect(content().string(containsString(".browser-card")))
                .andExpect(content().string(containsString(".browser-status-pill")))
                .andExpect(content().string(containsString(".btn-import-profile")))
                .andExpect(content().string(containsString(".import-divider")));

        mockMvc.perform(get("/css/responsive.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@media (max-width: 768px)")));
    }

    @Test
    @DisplayName("GET /js/* serves all frontend modules including browser discovery")
    void testJavaScriptModulesServed() throws Exception {
        String[] modules = {
                "/js/state.js",
                "/js/api.js",
                "/js/upload.js",
                "/js/scan.js",
                "/js/results.js",
                "/js/decisions.js",
                "/js/export.js",
                "/js/app.js"
        };

        for (String module : modules) {
            mockMvc.perform(get(module))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("BookmarkCleaner")));
        }

        mockMvc.perform(get("/js/api.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("discoverBrowsers")))
                .andExpect(content().string(containsString("importBrowserProfile")));

        mockMvc.perform(get("/js/upload.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("initBrowserDiscovery")))
                .andExpect(content().string(containsString("loadDiscoveredBrowsers")))
                .andExpect(content().string(containsString("createBrowserCard")))
                .andExpect(content().string(containsString("btn-import-profile")));
    }

    @Test
    @DisplayName("Verify UX improvements: Decision controls and expandable details assets")
    void testDecisionControlsAndExpandableRowsStaticAssets() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("col-expand")))
                .andExpect(content().string(containsString("col-decision")))
                .andExpect(content().string(containsString("col-change")));

        mockMvc.perform(get("/css/components.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".btn-row-expand")))
                .andExpect(content().string(containsString(".expand-chevron")))
                .andExpect(content().string(containsString(".compact-change-wrapper")))
                .andExpect(content().string(containsString(".decision-control-wrapper")))
                .andExpect(content().string(containsString(".decision-label")))
                .andExpect(content().string(containsString(".details-card")))
                .andExpect(content().string(containsString(".details-grid")));

        mockMvc.perform(get("/js/results.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("createTableRowGroup")))
                .andExpect(content().string(containsString("getCompactSummary")))
                .andExpect(content().string(containsString("formatShortUrl")))
                .andExpect(content().string(containsString("btn-row-expand")))
                .andExpect(content().string(containsString("details-card")));

        mockMvc.perform(get("/js/decisions.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("updateDecisionUi")))
                .andExpect(content().string(containsString("toggleDecision")))
                .andExpect(content().string(containsString("isDecisionPending")));
    }
}
