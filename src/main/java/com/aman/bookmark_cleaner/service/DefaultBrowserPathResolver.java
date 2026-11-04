package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BrowserDiscoveryProperties;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.OperatingSystem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Default OS-aware implementation of {@link BrowserPathResolver}.
 * Resolves browser data directories for Windows, Linux, and macOS using standard conventions.
 */
@Component
public class DefaultBrowserPathResolver implements BrowserPathResolver {

    private final BrowserDiscoveryProperties properties;
    private final OperatingSystem operatingSystem;
    private final Map<String, String> environment;
    private final String userHome;

    @Autowired
    public DefaultBrowserPathResolver(BrowserDiscoveryProperties properties) {
        this(properties, OperatingSystem.getCurrent(), System.getenv(), System.getProperty("user.home"));
    }

    public DefaultBrowserPathResolver(BrowserDiscoveryProperties properties,
                                      OperatingSystem operatingSystem,
                                      Map<String, String> environment,
                                      String userHome) {
        this.properties = properties != null ? properties : new BrowserDiscoveryProperties();
        this.operatingSystem = operatingSystem != null ? operatingSystem : OperatingSystem.UNKNOWN;
        this.environment = environment != null ? environment : System.getenv();
        this.userHome = (userHome != null && !userHome.isBlank()) ? userHome : System.getProperty("user.home", ".");
    }

    @Override
    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    @Override
    public Path resolveDataDirectory(BrowserType browserType) {
        if (browserType == null) {
            return null;
        }

        // Check custom property override first
        if (properties.getCustomPaths() != null && properties.getCustomPaths().containsKey(browserType.getId())) {
            String customPath = properties.getCustomPaths().get(browserType.getId());
            if (customPath != null && !customPath.isBlank()) {
                return Paths.get(customPath).normalize();
            }
        }

        return switch (operatingSystem) {
            case WINDOWS -> resolveWindowsPath(browserType);
            case LINUX -> resolveLinuxPath(browserType);
            case MACOS -> resolveMacPath(browserType);
            case UNKNOWN -> null;
        };
    }

    private Path resolveWindowsPath(BrowserType browserType) {
        String localAppData = environment.get("LOCALAPPDATA");
        Path basePath;
        if (localAppData != null && !localAppData.isBlank()) {
            basePath = Paths.get(localAppData);
        } else {
            String userProfile = environment.get("USERPROFILE");
            if (userProfile != null && !userProfile.isBlank()) {
                basePath = Paths.get(userProfile, "AppData", "Local");
            } else {
                basePath = Paths.get(userHome, "AppData", "Local");
            }
        }

        return switch (browserType) {
            case CHROME -> basePath.resolve("Google").resolve("Chrome").resolve("User Data").normalize();
            case EDGE -> basePath.resolve("Microsoft").resolve("Edge").resolve("User Data").normalize();
            case BRAVE -> basePath.resolve("BraveSoftware").resolve("Brave-Browser").resolve("User Data").normalize();
        };
    }

    private Path resolveLinuxPath(BrowserType browserType) {
        String xdgConfigHome = environment.get("XDG_CONFIG_HOME");
        Path basePath;
        if (xdgConfigHome != null && !xdgConfigHome.isBlank()) {
            basePath = Paths.get(xdgConfigHome);
        } else {
            basePath = Paths.get(userHome, ".config");
        }

        return switch (browserType) {
            case CHROME -> basePath.resolve("google-chrome").normalize();
            case EDGE -> basePath.resolve("microsoft-edge").normalize();
            case BRAVE -> basePath.resolve("BraveSoftware").resolve("Brave-Browser").normalize();
        };
    }

    private Path resolveMacPath(BrowserType browserType) {
        Path basePath = Paths.get(userHome, "Library", "Application Support");

        return switch (browserType) {
            case CHROME -> basePath.resolve("Google").resolve("Chrome").normalize();
            case EDGE -> basePath.resolve("Microsoft Edge").normalize();
            case BRAVE -> basePath.resolve("BraveSoftware").resolve("Brave-Browser").normalize();
        };
    }
}
