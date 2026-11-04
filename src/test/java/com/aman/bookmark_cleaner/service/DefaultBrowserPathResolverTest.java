package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BrowserDiscoveryProperties;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBrowserPathResolverTest {

    @Test
    @DisplayName("Windows path resolution using LOCALAPPDATA environment variable")
    void testWindowsPathResolution() {
        Map<String, String> env = Map.of("LOCALAPPDATA", "C:\\Users\\TestUser\\AppData\\Local");
        DefaultBrowserPathResolver resolver = new DefaultBrowserPathResolver(
                new BrowserDiscoveryProperties(),
                OperatingSystem.WINDOWS,
                env,
                "C:\\Users\\TestUser"
        );

        Path chromePath = resolver.resolveDataDirectory(BrowserType.CHROME);
        assertNotNull(chromePath);
        assertTrue(chromePath.toString().contains("Google"));
        assertTrue(chromePath.toString().contains("Chrome"));
        assertTrue(chromePath.toString().contains("User Data"));

        Path edgePath = resolver.resolveDataDirectory(BrowserType.EDGE);
        assertNotNull(edgePath);
        assertTrue(edgePath.toString().contains("Microsoft"));
        assertTrue(edgePath.toString().contains("Edge"));

        Path bravePath = resolver.resolveDataDirectory(BrowserType.BRAVE);
        assertNotNull(bravePath);
        assertTrue(bravePath.toString().contains("BraveSoftware"));
        assertTrue(bravePath.toString().contains("Brave-Browser"));
    }

    @Test
    @DisplayName("Linux path resolution using XDG_CONFIG_HOME or fallback ~/.config")
    void testLinuxPathResolution() {
        Map<String, String> env = Map.of("XDG_CONFIG_HOME", "/home/testuser/.config");
        DefaultBrowserPathResolver resolver = new DefaultBrowserPathResolver(
                new BrowserDiscoveryProperties(),
                OperatingSystem.LINUX,
                env,
                "/home/testuser"
        );

        Path chromePath = resolver.resolveDataDirectory(BrowserType.CHROME);
        assertNotNull(chromePath);
        assertTrue(chromePath.toString().endsWith("google-chrome"));

        Path edgePath = resolver.resolveDataDirectory(BrowserType.EDGE);
        assertNotNull(edgePath);
        assertTrue(edgePath.toString().endsWith("microsoft-edge"));

        Path bravePath = resolver.resolveDataDirectory(BrowserType.BRAVE);
        assertNotNull(bravePath);
        assertTrue(bravePath.toString().contains("BraveSoftware"));
    }

    @Test
    @DisplayName("macOS path resolution using ~/Library/Application Support")
    void testMacPathResolution() {
        DefaultBrowserPathResolver resolver = new DefaultBrowserPathResolver(
                new BrowserDiscoveryProperties(),
                OperatingSystem.MACOS,
                Collections.emptyMap(),
                "/Users/testuser"
        );

        Path chromePath = resolver.resolveDataDirectory(BrowserType.CHROME);
        assertNotNull(chromePath);
        assertTrue(chromePath.toString().contains("Application Support"));
        assertTrue(chromePath.toString().contains("Google"));

        Path edgePath = resolver.resolveDataDirectory(BrowserType.EDGE);
        assertNotNull(edgePath);
        assertTrue(edgePath.toString().contains("Microsoft Edge"));

        Path bravePath = resolver.resolveDataDirectory(BrowserType.BRAVE);
        assertNotNull(bravePath);
        assertTrue(bravePath.toString().contains("BraveSoftware"));
    }

    @Test
    @DisplayName("Unknown operating system returns null path")
    void testUnknownOsHandling() {
        DefaultBrowserPathResolver resolver = new DefaultBrowserPathResolver(
                new BrowserDiscoveryProperties(),
                OperatingSystem.UNKNOWN,
                Collections.emptyMap(),
                "/tmp"
        );

        assertNull(resolver.resolveDataDirectory(BrowserType.CHROME));
        assertNull(resolver.resolveDataDirectory(BrowserType.EDGE));
        assertNull(resolver.resolveDataDirectory(BrowserType.BRAVE));
        assertNull(resolver.resolveDataDirectory(null));
    }

    @Test
    @DisplayName("Custom property path overrides platform default")
    void testCustomPropertyOverride() {
        BrowserDiscoveryProperties props = new BrowserDiscoveryProperties();
        Map<String, String> custom = new HashMap<>();
        custom.put("chrome", "/custom/path/to/chrome");
        props.setCustomPaths(custom);

        DefaultBrowserPathResolver resolver = new DefaultBrowserPathResolver(
                props,
                OperatingSystem.LINUX,
                Collections.emptyMap(),
                "/home/user"
        );

        Path chromePath = resolver.resolveDataDirectory(BrowserType.CHROME);
        assertNotNull(chromePath);
        assertEquals(Path.of("/custom/path/to/chrome").normalize(), chromePath);
    }
}
