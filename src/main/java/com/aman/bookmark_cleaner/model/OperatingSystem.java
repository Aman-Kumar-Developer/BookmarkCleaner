package com.aman.bookmark_cleaner.model;

import java.util.Locale;

/**
 * Operating system classification for platform-specific path resolution.
 */
public enum OperatingSystem {

    WINDOWS,
    LINUX,
    MACOS,
    UNKNOWN;

    /**
     * Determines the current host operating system based on the {@code os.name} system property.
     */
    public static OperatingSystem getCurrent() {
        return fromOsName(System.getProperty("os.name"));
    }

    /**
     * Parses an OS name string into an {@link OperatingSystem} constant.
     *
     * @param osName the OS name string
     * @return the resolved {@link OperatingSystem}
     */
    public static OperatingSystem fromOsName(String osName) {
        if (osName == null || osName.isBlank()) {
            return UNKNOWN;
        }
        String normalized = osName.toLowerCase(Locale.ROOT);
        if (normalized.contains("mac") || normalized.contains("darwin")) {
            return MACOS;
        }
        if (normalized.contains("win")) {
            return WINDOWS;
        }
        if (normalized.contains("nux") || normalized.contains("nix") || normalized.contains("aix")) {
            return LINUX;
        }
        return UNKNOWN;
    }
}
