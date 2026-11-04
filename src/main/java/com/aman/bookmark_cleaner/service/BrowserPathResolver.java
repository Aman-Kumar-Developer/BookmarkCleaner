package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;

import java.nio.file.Path;

/**
 * Strategy interface for resolving platform-specific browser user data directories.
 */
public interface BrowserPathResolver {

    /**
     * Resolves the user data directory for a specific browser type on the current platform.
     *
     * @param browserType the target browser type
     * @return the resolved {@link Path}, or {@code null} if unsupported or not determinable
     */
    Path resolveDataDirectory(BrowserType browserType);

    /**
     * Returns the active operating system used for resolution.
     */
    OperatingSystem getOperatingSystem();
}
