package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BrowserType;

/**
 * Interface responsible for checking whether a specific browser application process is currently running.
 */
public interface BrowserProcessDetector {

    /**
     * Determines whether any process belonging to the specified browser is active.
     *
     * @param browserType the browser type to check
     * @return true if the browser is running, false otherwise
     */
    boolean isBrowserRunning(BrowserType browserType);
}
