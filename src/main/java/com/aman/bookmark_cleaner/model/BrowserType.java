package com.aman.bookmark_cleaner.model;

import java.util.Locale;

/**
 * Supported Chromium-based browsers for automatic discovery and bookmark import.
 */
public enum BrowserType {

    CHROME("chrome", "Google Chrome"),
    EDGE("edge", "Microsoft Edge"),
    BRAVE("brave", "Brave");

    private final String id;
    private final String displayName;

    BrowserType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Resolves a {@link BrowserType} by its string identifier (case-insensitive).
     *
     * @param id the browser identifier (e.g. "chrome", "edge", "brave")
     * @return the matching {@link BrowserType}, or {@code null} if unknown
     */
    public static BrowserType fromId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (BrowserType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
