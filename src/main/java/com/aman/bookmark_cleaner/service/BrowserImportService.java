package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.dto.UploadResponse;
import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.BrowserNotFoundException;
import com.aman.bookmark_cleaner.exception.BrowserProfileNotFoundException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.exception.SecurityValidationException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BrowserInfo;
import com.aman.bookmark_cleaner.model.BrowserProfile;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Service coordinating the direct, safe, read-only import of bookmarks from an installed browser profile.
 */
@Service
public class BrowserImportService {

    private static final Logger log = LoggerFactory.getLogger(BrowserImportService.class);
    private static final Pattern SUSPICIOUS_ID_CHARS = Pattern.compile("(\\.\\.|/|\\\\|%2e|%2f|%5c|\\x00)", Pattern.CASE_INSENSITIVE);
    private static final long DEFAULT_MAX_FILE_SIZE = 50 * 1024 * 1024L; // 50MB

    private final BrowserDiscoveryService discoveryService;
    private final BookmarkReader bookmarkReader;
    private final ScanSessionManager sessionManager;
    private final BookmarkParserProperties parserProperties;

    @Autowired
    public BrowserImportService(BrowserDiscoveryService discoveryService,
                                BookmarkReader bookmarkReader,
                                ScanSessionManager sessionManager,
                                BookmarkParserProperties parserProperties) {
        this.discoveryService = discoveryService;
        this.bookmarkReader = bookmarkReader;
        this.sessionManager = sessionManager;
        this.parserProperties = parserProperties;
    }

    private long resolveEffectiveMaxFileSize() {
        if (parserProperties != null && parserProperties.getMaxFileSize() > 0) {
            return parserProperties.getMaxFileSize();
        }
        return DEFAULT_MAX_FILE_SIZE;
    }

    /**
     * Imports bookmarks directly from a resolved browser profile into an active scan session.
     *
     * @param browserId the browser identifier (e.g. "chrome", "edge", "brave")
     * @param profileId the profile identifier or directory name (e.g. "chrome-default", "Default", "Personal")
     * @return the {@link UploadResponse} matching the manual upload workflow
     */
    public UploadResponse importBookmarks(String browserId, String profileId) {
        // 1. Validate browser identifier
        if (browserId == null || browserId.isBlank()) {
            throw new BrowserNotFoundException("Browser ID must not be empty");
        }
        if (SUSPICIOUS_ID_CHARS.matcher(browserId).find()) {
            throw new SecurityValidationException("Invalid browser identifier: path traversal detected");
        }

        BrowserType browserType = BrowserType.fromId(browserId);
        if (browserType == null) {
            throw new BrowserNotFoundException("Unsupported browser: " + browserId);
        }

        // 2. Validate profile identifier
        if (profileId == null || profileId.isBlank()) {
            throw new BrowserProfileNotFoundException("Profile ID must not be empty");
        }
        if (SUSPICIOUS_ID_CHARS.matcher(profileId).find()) {
            throw new SecurityValidationException("Invalid profile identifier: path traversal detected");
        }

        // 3. Inspect browser and discover valid profiles
        BrowserInfo browserInfo = discoveryService.inspectBrowser(browserType);
        if (!browserInfo.isAvailable()) {
            throw new BrowserNotFoundException("Browser is not installed or data directory is inaccessible: " + browserType.getDisplayName());
        }

        BrowserProfile profile = findMatchingProfile(browserInfo, browserType, profileId);
        if (profile == null) {
            checkPotentialProfileDirectory(browserInfo.getDataDirectory(), browserType, profileId);
            throw new BrowserProfileNotFoundException("Profile '" + profileId + "' not found for browser: " + browserType.getDisplayName());
        }

        // 4. Resolve and validate bookmark path security
        Path bookmarkPath = profile.getBookmarkPath();
        if (bookmarkPath == null || !Files.exists(bookmarkPath)) {
            throw new BookmarkFileNotFoundException("Bookmark file not found for profile: " + profile.getDisplayName());
        }

        if (Files.isDirectory(bookmarkPath)) {
            throw new InvalidBookmarkException("Bookmark target is a directory, not a file: " + profile.getDisplayName());
        }

        if (!Files.isReadable(bookmarkPath)) {
            throw new BookmarkReaderException("Bookmark file cannot be read (insufficient permissions): " + profile.getDisplayName());
        }

        // Path containment validation
        validatePathSecurity(browserInfo.getDataDirectory(), profile.getDirectoryName(), bookmarkPath);

        // File size validation
        long fileSize;
        try {
            fileSize = Files.size(bookmarkPath);
        } catch (IOException e) {
            throw new BookmarkReaderException("Failed to determine bookmark file size: " + profile.getDisplayName(), e);
        }

        if (fileSize == 0) {
            throw new InvalidBookmarkException("Bookmark file is empty (0 bytes) for profile: " + profile.getDisplayName());
        }

        long maxFileSize = resolveEffectiveMaxFileSize();
        if (fileSize > maxFileSize) {
            throw new BookmarkReaderException("Bookmark file size (" + fileSize + " bytes) exceeds maximum permitted limit (" + maxFileSize + " bytes)");
        }

        // 5. Safely read a bounded snapshot into an application-owned temporary file
        Path tempSnapshot = null;
        BookmarkDocument document;
        String sessionName = browserType.getDisplayName() + " (" + profile.getDisplayName() + ")";
        String originalHash;

        try {
            tempSnapshot = Files.createTempFile("bookmark-browser-import-", ".json");
            try (InputStream is = Files.newInputStream(bookmarkPath, StandardOpenOption.READ)) {
                Files.copy(is, tempSnapshot, StandardCopyOption.REPLACE_EXISTING);
            }

            // Compute SHA-256 fingerprint of the imported file snapshot
            originalHash = computeSha256(tempSnapshot);

            // Feed the snapshot into the existing BookmarkReader
            document = bookmarkReader.read(tempSnapshot);
        } catch (BookmarkReaderException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to read browser bookmark snapshot for {}: {}", sessionName, e.getMessage());
            throw new BookmarkReaderException("Failed to read browser bookmark file: " + e.getMessage(), e);
        } finally {
            if (tempSnapshot != null) {
                try {
                    Files.deleteIfExists(tempSnapshot);
                } catch (Exception ignored) {
                }
            }
        }

        // 6. Create active scan session in the existing ScanSessionManager and attach profile origin
        ScanSession session = sessionManager.createSession(sessionName, document);
        session.setImportedFromBrowser(true);
        session.setBrowserType(browserType);
        session.setProfileId(profile.getId());
        session.setProfileDirectoryName(profile.getDirectoryName());
        session.setProfileDisplayName(profile.getDisplayName());
        session.setOriginalBookmarkPath(bookmarkPath);
        session.setOriginalFileHash(originalHash);

        log.info("Successfully imported {} bookmarks from {} for scanId: {} (hash: {})",
                session.getTotalBookmarks(), sessionName, session.getScanId(), originalHash);

        return new UploadResponse(
                session.getScanId(),
                sessionName,
                session.getTotalBookmarks(),
                session.getStatus().name(),
                true,
                browserType.getId(),
                browserType.getDisplayName(),
                profile.getId(),
                profile.getDisplayName()
        );
    }

    /**
     * Resolves a requested profile identifier against discovered profiles.
     */
    private BrowserProfile findMatchingProfile(BrowserInfo browserInfo, BrowserType browserType, String profileId) {
        String normalizedId = profileId.trim();

        for (BrowserProfile p : browserInfo.getProfiles()) {
            if (p.getId().equalsIgnoreCase(normalizedId)) {
                return p;
            }
            if (p.getDirectoryName().equalsIgnoreCase(normalizedId)) {
                return p;
            }
            if (p.getDisplayName().equalsIgnoreCase(normalizedId)) {
                return p;
            }
            // Match browserId-dirName format
            String fullId = browserType.getId() + "-" + p.getDirectoryName().toLowerCase();
            if (fullId.equalsIgnoreCase(normalizedId)) {
                return p;
            }
        }
        return null;
    }

    /**
     * Verifies that the bookmark file strictly resides inside the expected browser data directory
     * and profile directory, preventing traversal or external symbolic link escapes.
     */
    private void validatePathSecurity(Path dataDir, String profileDirName, Path bookmarkPath) {
        if (dataDir == null || profileDirName == null || bookmarkPath == null) {
            throw new SecurityValidationException("Invalid path arguments for security verification");
        }

        Path normalizedDataDir = dataDir.normalize().toAbsolutePath();
        Path expectedProfileDir = normalizedDataDir.resolve(profileDirName).normalize().toAbsolutePath();
        Path normalizedBookmark = bookmarkPath.normalize().toAbsolutePath();

        if (!normalizedBookmark.startsWith(expectedProfileDir) || !normalizedBookmark.startsWith(normalizedDataDir)) {
            throw new SecurityValidationException("Security validation failed: bookmark file escapes permitted profile directory");
        }

        // Check for symbolic link traversal if supported
        try {
            Path realBookmark = normalizedBookmark.toRealPath();
            Path realDataDir = normalizedDataDir.toRealPath();
            if (!realBookmark.startsWith(realDataDir)) {
                throw new SecurityValidationException("Security validation failed: symbolic link escapes browser data directory");
            }
        } catch (IOException e) {
            // If toRealPath fails due to file or path peculiarities, ensure normalized path check is upheld
            log.debug("Real path check completed with fallback to normalized path: {}", e.getMessage());
        }
    }

    /**
     * Inspects candidate profile directories on disk to provide precise error diagnosis when a profile
     * was not included in active discovery results (e.g. missing Bookmarks, directory-typed target, or unreadable).
     */
    private void checkPotentialProfileDirectory(Path dataDir, BrowserType browserType, String profileId) {
        if (dataDir == null || !Files.exists(dataDir)) return;

        String candidateDirName = profileId;
        String prefix = browserType.getId() + "-";
        if (profileId.toLowerCase().startsWith(prefix)) {
            candidateDirName = profileId.substring(prefix.length());
        }

        Path profileDir = dataDir.resolve(candidateDirName).normalize();
        if (!profileDir.startsWith(dataDir) || !Files.isDirectory(profileDir)) {
            try (Stream<Path> stream = Files.list(dataDir)) {
                String target = candidateDirName;
                profileDir = stream.filter(Files::isDirectory)
                        .filter(p -> p.getFileName().toString().equalsIgnoreCase(target))
                        .findFirst()
                        .orElse(null);
            } catch (Exception ignored) {
            }
        }

        if (profileDir != null && Files.isDirectory(profileDir)) {
            Path bookmarkFile = profileDir.resolve("Bookmarks");
            if (!Files.exists(bookmarkFile)) {
                throw new BookmarkFileNotFoundException("Bookmark file not found for profile: " + profileId);
            }
            if (Files.isDirectory(bookmarkFile)) {
                throw new InvalidBookmarkException("Bookmark target is a directory, not a file for profile: " + profileId);
            }
            if (!Files.isReadable(bookmarkFile)) {
                throw new BookmarkReaderException("Bookmark file cannot be read (insufficient permissions) for profile: " + profileId);
            }
        }
    }

    /**
     * Calculates the SHA-256 hexadecimal hash string for a file.
     *
     * @param path the path to the file
     * @return the lowercase 64-character hex string
     * @throws IOException if reading the file fails
     */
    public static String computeSha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(path, StandardOpenOption.READ)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hashBytes = digest.digest();
            StringBuilder sb = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("Failed to calculate SHA-256 digest: " + e.getMessage(), e);
        }
    }
}
