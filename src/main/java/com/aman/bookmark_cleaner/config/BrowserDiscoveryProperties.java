package com.aman.bookmark_cleaner.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for automatic browser discovery.
 */
@Component
@ConfigurationProperties(prefix = "bookmark.browser-discovery")
public class BrowserDiscoveryProperties {

    /**
     * Whether browser discovery is enabled. Default is true.
     */
    private boolean enabled = true;

    /**
     * Custom override data directory paths by browser ID (e.g. chrome, edge, brave).
     */
    private Map<String, String> customPaths = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getCustomPaths() {
        return customPaths;
    }

    public void setCustomPaths(Map<String, String> customPaths) {
        this.customPaths = customPaths != null ? customPaths : new HashMap<>();
    }
}
