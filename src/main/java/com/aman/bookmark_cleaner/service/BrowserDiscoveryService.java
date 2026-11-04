package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.BrowserDiscoveryResponse;
import com.aman.bookmark_cleaner.dto.BrowserInfoDto;
import com.aman.bookmark_cleaner.dto.BrowserProfileDto;
import com.aman.bookmark_cleaner.model.BrowserInfo;
import com.aman.bookmark_cleaner.model.BrowserProfile;
import com.aman.bookmark_cleaner.model.BrowserType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service discovering installed Chromium browsers and their readable bookmark profiles.
 */
@Service
public class BrowserDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(BrowserDiscoveryService.class);
    private static final Pattern PROFILE_DIR_PATTERN = Pattern.compile("^Profile\\s*\\d+$", Pattern.CASE_INSENSITIVE);

    private final BrowserPathResolver pathResolver;
    private final ObjectMapper objectMapper;

    @Autowired
    public BrowserDiscoveryService(BrowserPathResolver pathResolver, ObjectMapper objectMapper) {
        this.pathResolver = pathResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Discovers all supported browsers and returns a structured {@link BrowserDiscoveryResponse} DTO.
     */
    public BrowserDiscoveryResponse discoverBrowsers() {
        List<BrowserInfoDto> browserDtos = new ArrayList<>();

        for (BrowserType type : BrowserType.values()) {
            BrowserInfo info = inspectBrowser(type);
            List<BrowserProfileDto> profileDtos = new ArrayList<>();

            for (BrowserProfile profile : info.getProfiles()) {
                profileDtos.add(new BrowserProfileDto(
                        profile.getId(),
                        profile.getDisplayName(),
                        profile.isBookmarkAvailable(),
                        profile.isBookmarkAvailable()
                ));
            }

            browserDtos.add(new BrowserInfoDto(
                    type.getId(),
                    type.getDisplayName(),
                    info.isAvailable(),
                    profileDtos
            ));
        }

        return new BrowserDiscoveryResponse(browserDtos);
    }

    /**
     * Inspects a specific browser, returning its {@link BrowserInfo} including discovered profiles.
     */
    public BrowserInfo inspectBrowser(BrowserType browserType) {
        if (browserType == null) {
            return new BrowserInfo(null, false, null, Collections.emptyList());
        }

        Path dataDir;
        try {
            dataDir = pathResolver.resolveDataDirectory(browserType);
        } catch (Exception e) {
            log.debug("Failed to resolve data directory for browser {}: {}", browserType, e.getMessage());
            return new BrowserInfo(browserType, false, null, Collections.emptyList());
        }

        if (dataDir == null) {
            return new BrowserInfo(browserType, false, null, Collections.emptyList());
        }

        boolean directoryExistsAndAccessible;
        try {
            directoryExistsAndAccessible = Files.exists(dataDir) && Files.isDirectory(dataDir) && Files.isReadable(dataDir);
        } catch (SecurityException e) {
            log.debug("Access denied when inspecting data directory for browser {}: {}", browserType, e.getMessage());
            return new BrowserInfo(browserType, false, dataDir, Collections.emptyList());
        } catch (Exception e) {
            log.debug("Error checking data directory for browser {}: {}", browserType, e.getMessage());
            return new BrowserInfo(browserType, false, dataDir, Collections.emptyList());
        }

        if (!directoryExistsAndAccessible) {
            return new BrowserInfo(browserType, false, dataDir, Collections.emptyList());
        }

        // Parse Local State for user-friendly profile metadata
        Map<String, String> profileNames = parseLocalStateProfiles(dataDir);
        List<String> profilesOrder = parseLocalStateProfilesOrder(dataDir);

        // Discover profiles that actually have valid Bookmarks files
        List<BrowserProfile> validProfiles = discoverValidProfiles(browserType, dataDir, profileNames, profilesOrder);

        return new BrowserInfo(browserType, true, dataDir, validProfiles);
    }

    /**
     * Discovers profiles within a browser's user data directory that contain a valid, readable Bookmarks file.
     */
    private List<BrowserProfile> discoverValidProfiles(BrowserType browserType,
                                                       Path dataDir,
                                                       Map<String, String> profileNames,
                                                       List<String> profilesOrder) {
        Set<String> candidateDirNames = new LinkedHashSet<>();

        // 1. Add profiles from Local State ordering
        if (profilesOrder != null) {
            candidateDirNames.addAll(profilesOrder);
        }

        // 2. Add profiles from Local State profile map
        candidateDirNames.addAll(profileNames.keySet());

        // 3. Always include Default candidate
        candidateDirNames.add("Default");

        // 4. Scan disk for candidate directories
        try (Stream<Path> stream = Files.list(dataDir)) {
            stream.filter(Files::isDirectory).forEach(p -> {
                String name = p.getFileName().toString();
                if (name.equalsIgnoreCase("Default") || PROFILE_DIR_PATTERN.matcher(name).matches()) {
                    candidateDirNames.add(name);
                } else if (Files.isRegularFile(p.resolve("Bookmarks"))) {
                    candidateDirNames.add(name);
                }
            });
        } catch (Exception e) {
            log.debug("Failed to list subdirectories in {}: {}", dataDir, e.getMessage());
        }

        List<BrowserProfile> result = new ArrayList<>();

        for (String dirName : candidateDirNames) {
            if (dirName == null || dirName.isBlank()) continue;

            Path profileDir = dataDir.resolve(dirName).normalize();

            // Security check: ensure resolved profile directory remains strictly inside dataDir
            if (!profileDir.startsWith(dataDir)) {
                log.warn("Path traversal or external path detected for candidate profile: {}", dirName);
                continue;
            }

            try {
                if (!Files.isDirectory(profileDir)) {
                    continue;
                }

                Path bookmarkFile = profileDir.resolve("Bookmarks").normalize();
                if (!bookmarkFile.startsWith(profileDir)) {
                    continue;
                }

                if (!Files.exists(bookmarkFile) || !Files.isRegularFile(bookmarkFile) || !Files.isReadable(bookmarkFile)) {
                    continue;
                }

                long size = Files.size(bookmarkFile);
                if (size <= 0) {
                    continue;
                }

                // Determine user-friendly display name
                String displayName = profileNames.get(dirName);
                if (displayName == null || displayName.isBlank()) {
                    // Check case-insensitive match in profileNames
                    for (Map.Entry<String, String> entry : profileNames.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(dirName) && entry.getValue() != null && !entry.getValue().isBlank()) {
                            displayName = entry.getValue();
                            break;
                        }
                    }
                }
                if (displayName == null || displayName.isBlank()) {
                    displayName = dirName;
                }

                String profileId = generateProfileId(browserType, dirName);
                result.add(new BrowserProfile(profileId, browserType, dirName, displayName, bookmarkFile, true, size));

            } catch (Exception e) {
                log.debug("Skipping unreadable profile directory {}: {}", profileDir, e.getMessage());
            }
        }

        return result;
    }

    /**
     * Parses user-friendly profile names from the browser's {@code Local State} file.
     */
    private Map<String, String> parseLocalStateProfiles(Path dataDir) {
        Map<String, String> names = new LinkedHashMap<>();
        Path localStateFile = dataDir.resolve("Local State");

        if (!Files.exists(localStateFile) || !Files.isRegularFile(localStateFile) || !Files.isReadable(localStateFile)) {
            return names;
        }

        try (InputStream is = Files.newInputStream(localStateFile)) {
            JsonNode root = objectMapper.readTree(is);
            if (root == null || !root.isObject()) {
                return names;
            }

            JsonNode profileNode = root.path("profile");
            if (!profileNode.isObject()) {
                return names;
            }

            // Inspect info_cache (standard Chromium) or profiles_attributes
            JsonNode cacheNode = profileNode.path("info_cache");
            if (!cacheNode.isObject()) {
                cacheNode = profileNode.path("profiles_attributes");
            }

            if (cacheNode.isObject()) {
                cacheNode.properties().forEach(entry -> {
                    String dirName = entry.getKey();
                    JsonNode node = entry.getValue();
                    if (node != null && node.isObject()) {
                        String name = node.path("name").asText(null);
                        if (name == null || name.isBlank()) {
                            name = node.path("user_name").asText(null);
                        }
                        if (name != null && !name.isBlank()) {
                            names.put(dirName, name.trim());
                        }
                    }
                });
            }
        } catch (Exception e) {
            log.debug("Non-fatal: could not parse Local State at {}: {}", localStateFile, e.getMessage());
        }

        return names;
    }

    /**
     * Parses the preferred profile display order from {@code Local State} if available.
     */
    private List<String> parseLocalStateProfilesOrder(Path dataDir) {
        List<String> order = new ArrayList<>();
        Path localStateFile = dataDir.resolve("Local State");

        if (!Files.exists(localStateFile) || !Files.isRegularFile(localStateFile) || !Files.isReadable(localStateFile)) {
            return order;
        }

        try (InputStream is = Files.newInputStream(localStateFile)) {
            JsonNode root = objectMapper.readTree(is);
            if (root != null && root.isObject()) {
                JsonNode orderNode = root.path("profile").path("profiles_order");
                if (orderNode.isArray()) {
                    for (JsonNode n : orderNode) {
                        if (n.isTextual() && !n.asText().isBlank()) {
                            order.add(n.asText().trim());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Non-fatal: could not parse profiles_order from Local State: {}", e.getMessage());
        }

        return order;
    }

    /**
     * Generates a URL-safe, sanitized profile ID.
     */
    public static String generateProfileId(BrowserType browserType, String directoryName) {
        String cleanDir = directoryName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "-");
        return browserType.getId() + "-" + cleanDir;
    }
}
