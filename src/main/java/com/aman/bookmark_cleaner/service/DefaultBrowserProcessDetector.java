package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BrowserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * Standard implementation of {@link BrowserProcessDetector} leveraging Java 9+ {@link ProcessHandle}
 * to inspect operating system processes cross-platform without native subprocess invocations.
 */
@Component
public class DefaultBrowserProcessDetector implements BrowserProcessDetector {

    private static final Logger log = LoggerFactory.getLogger(DefaultBrowserProcessDetector.class);

    private static final Set<String> CHROME_EXES = Set.of(
            "chrome.exe", "chrome", "google-chrome", "google-chrome-stable", "google chrome"
    );

    private static final Set<String> EDGE_EXES = Set.of(
            "msedge.exe", "msedge", "microsoft-edge", "microsoft-edge-stable", "microsoft-edge-dev", "microsoft edge"
    );

    private static final Set<String> BRAVE_EXES = Set.of(
            "brave.exe", "brave", "brave-browser", "brave-browser-stable", "brave browser"
    );

    @Override
    public boolean isBrowserRunning(BrowserType browserType) {
        if (browserType == null) {
            return false;
        }

        Set<String> targetExes = switch (browserType) {
            case CHROME -> CHROME_EXES;
            case EDGE -> EDGE_EXES;
            case BRAVE -> BRAVE_EXES;
        };

        try {
            return ProcessHandle.allProcesses().anyMatch(process -> matchesProcess(process, targetExes));
        } catch (Exception e) {
            log.warn("Process inspection encountered an error: {}", e.getMessage());
            return false;
        }
    }

    private boolean matchesProcess(ProcessHandle handle, Set<String> targetExes) {
        try {
            ProcessHandle.Info info = handle.info();
            if (info.command().isPresent()) {
                String cmd = info.command().get();
                if (matchesCommandString(cmd, targetExes)) {
                    return true;
                }
            }
            if (info.commandLine().isPresent()) {
                String line = info.commandLine().get();
                if (matchesCommandLine(line, targetExes)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Some system processes can't be inspected due to OS security
        }
        return false;
    }

    private boolean matchesCommandString(String cmd, Set<String> targetExes) {
        if (cmd == null || cmd.isBlank()) {
            return false;
        }
        String normalized = cmd.trim().toLowerCase(Locale.ROOT);
        try {
            String filename = Path.of(normalized).getFileName().toString();
            if (targetExes.contains(filename)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        for (String exe : targetExes) {
            if (normalized.endsWith(exe) || normalized.contains("/" + exe) || normalized.contains("\\" + exe)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesCommandLine(String line, Set<String> targetExes) {
        if (line == null || line.isBlank()) {
            return false;
        }
        String normalized = line.trim().toLowerCase(Locale.ROOT);
        for (String exe : targetExes) {
            if (normalized.contains(exe)) {
                return true;
            }
        }
        return false;
    }
}
