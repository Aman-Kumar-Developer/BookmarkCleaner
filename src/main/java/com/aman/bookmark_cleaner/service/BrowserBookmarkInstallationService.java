package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.dto.BackupInfoDto;
import com.aman.bookmark_cleaner.dto.BackupManagementResponse;
import com.aman.bookmark_cleaner.dto.InstallResponse;
import com.aman.bookmark_cleaner.dto.InstallationOperationDto;
import com.aman.bookmark_cleaner.dto.RestoreResponse;
import com.aman.bookmark_cleaner.exception.BackupNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkFileModifiedException;
import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkInstallationException;
import com.aman.bookmark_cleaner.exception.BrowserNotFoundException;
import com.aman.bookmark_cleaner.exception.BrowserRunningException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.exception.SecurityValidationException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BrowserInfo;
import com.aman.bookmark_cleaner.model.BrowserType;
import com.aman.bookmark_cleaner.model.InstallationOperation;
import com.aman.bookmark_cleaner.model.InstallationState;
import com.aman.bookmark_cleaner.model.ScanSession;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Service managing direct browser bookmark replacement and versioned backup restoration.
 * Supports asynchronous decoupled installation, waiting for complete browser closure independently
 * of the browser tab, maintaining a strict 2-generation versioned backup rotation, and crash recovery.
 */
@Service
public class BrowserBookmarkInstallationService {

    private static final Logger log = LoggerFactory.getLogger(BrowserBookmarkInstallationService.class);

    public static final String BOOKMARK_FILENAME = "Bookmarks";
    public static final String BACKUP_1_FILENAME = "Bookmarks.old.1";
    public static final String BACKUP_2_FILENAME = "Bookmarks.old.2";
    public static final String TMP_PREFIX = "Bookmarks.tmp.";
    public static final String OPERATION_METADATA_FILE = ".bookmark-cleaner-operation.json";

    private final ScanSessionManager sessionManager;
    private final BrowserDiscoveryService discoveryService;
    private final BookmarkExportService exportService;
    private final BookmarkReader bookmarkReader;
    private final BrowserProcessDetector browserProcessDetector;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, ReentrantLock> profileLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, InstallationOperation> activeOperations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> scanIdToOperationId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ScheduledFuture<?>> monitoringTasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService monitorExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "browser-closure-monitor");
        t.setDaemon(true);
        return t;
    });

    @Autowired
    public BrowserBookmarkInstallationService(ScanSessionManager sessionManager,
                                              BrowserDiscoveryService discoveryService,
                                              BookmarkExportService exportService,
                                              BookmarkReader bookmarkReader,
                                              BrowserProcessDetector browserProcessDetector,
                                              ObjectMapper objectMapper) {
        this.sessionManager = sessionManager;
        this.discoveryService = discoveryService;
        this.exportService = exportService;
        this.bookmarkReader = bookmarkReader;
        this.browserProcessDetector = browserProcessDetector;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        log.info("BrowserBookmarkInstallationService initialized with decoupled async browser closure monitor.");
    }

    @PreDestroy
    public void shutdown() {
        monitorExecutor.shutdownNow();
    }

    /**
     * Inspects active bookmarks, available versioned backups (1 and 2), running state,
     * and any pending installation operation for the session.
     */
    public BackupManagementResponse getBackupManagementInfo(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);

        if (!session.isImportedFromBrowser()) {
            return new BackupManagementResponse(
                    scanId, false, null, null, null, null, false, false, List.of()
            );
        }

        BrowserType browserType = session.getBrowserType();
        Path originalPath = session.getOriginalBookmarkPath();
        String profileDirName = session.getProfileDirectoryName();

        validateSessionProfileSecurity(session, browserType, profileDirName, originalPath);

        boolean browserRunning = browserProcessDetector.isBrowserRunning(browserType);
        boolean bookmarkFileExists = Files.exists(originalPath);

        Path profileDir = originalPath.getParent();
        List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);

        BackupInfoDto backup1 = null;
        BackupInfoDto backup2 = null;
        for (BackupInfoDto b : backups) {
            if ("1".equals(b.getId())) backup1 = b;
            if ("2".equals(b.getId())) backup2 = b;
        }

        // Active bookmark metadata
        int activeBookmarkCount = 0;
        long activeFileSize = 0;
        long activeLastModified = 0;
        if (bookmarkFileExists) {
            try {
                activeFileSize = Files.size(originalPath);
                activeLastModified = Files.getLastModifiedTime(originalPath).toMillis();
                BookmarkDocument doc = bookmarkReader.read(originalPath);
                activeBookmarkCount = doc.getTotalBookmarkCount();
            } catch (Exception ignored) {
            }
        }

        InstallationOperation op = getOperationForScan(scanId);
        InstallationOperationDto opDto = op != null ? toDto(op, browserRunning, backups) : null;

        return new BackupManagementResponse(
                scanId,
                true,
                browserType.getId(),
                browserType.getDisplayName(),
                session.getProfileId(),
                session.getProfileDisplayName(),
                bookmarkFileExists,
                browserRunning,
                activeBookmarkCount,
                activeFileSize,
                activeLastModified,
                backup1,
                backup2,
                backups,
                opDto
        );
    }

    /**
     * Initiates or schedules direct bookmark replacement into the browser profile.
     * If the target browser is currently running, stages the operation and enters
     * WAITING_FOR_BROWSER_CLOSURE so the backend completes installation automatically
     * once the browser process terminates, without requiring the browser tab to remain open.
     */
    public InstallResponse installCleanedBookmarks(String scanId) {
        ScanSession session = sessionManager.getSession(scanId);

        if (!session.isImportedFromBrowser()) {
            throw new InvalidBookmarkException(
                    "Session was not imported from a browser profile. Direct browser replacement is only supported for direct browser profile imports."
            );
        }

        BrowserType browserType = session.getBrowserType();
        Path bookmarkPath = session.getOriginalBookmarkPath();
        String profileDirName = session.getProfileDirectoryName();

        validateSessionProfileSecurity(session, browserType, profileDirName, bookmarkPath);

        Path profileDir = bookmarkPath.getParent();
        ReentrantLock lock = getLockForProfile(profileDir);
        lock.lock();

        try {
            // Check if active bookmark file still exists
            if (!Files.exists(bookmarkPath)) {
                throw new BookmarkFileNotFoundException(
                        "Original bookmark file not found at expected location: " + bookmarkPath.toAbsolutePath()
                );
            }

            // Verify original bookmark fingerprint has not been modified since import
            String currentHash;
            try {
                currentHash = BrowserImportService.computeSha256(bookmarkPath);
            } catch (IOException e) {
                throw new BookmarkInstallationException("Failed to verify original bookmark integrity: " + e.getMessage(), e);
            }

            String expectedHash = session.getOriginalFileHash();
            if (expectedHash != null && !expectedHash.equalsIgnoreCase(currentHash)) {
                throw new BookmarkFileModifiedException(
                        "The browser bookmark file has been modified since it was imported. To prevent overwriting newer bookmarks, please re-import your bookmarks and re-run the analysis."
                );
            }

            // Retrieve or generate export content
            byte[] exportBytes = session.getExportJsonBytes();
            if (exportBytes == null || exportBytes.length == 0) {
                exportService.generateExport(scanId);
                exportBytes = session.getExportJsonBytes();
            }

            if (exportBytes == null || exportBytes.length == 0) {
                throw new BookmarkInstallationException("Cleaned bookmark export data is empty or unavailable.");
            }

            // Stage cleaned bookmarks in a temporary file in the same directory
            String operationId = UUID.randomUUID().toString();
            Path tempInstallFile = profileDir.resolve(TMP_PREFIX + operationId);
            try {
                Files.write(tempInstallFile, exportBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE, StandardOpenOption.SYNC);
            } catch (IOException e) {
                throw new BookmarkInstallationException("Failed to stage cleaned bookmarks to temporary file: " + e.getMessage(), e);
            }

            // Pre-installation validation of staged file
            BookmarkDocument stagedDoc;
            try {
                stagedDoc = bookmarkReader.read(tempInstallFile);
            } catch (Exception e) {
                safeDelete(tempInstallFile);
                throw new BookmarkInstallationException("Staged cleaned bookmarks failed validation check: " + e.getMessage(), e);
            }

            // Create and register operation model
            InstallationOperation operation = new InstallationOperation(
                    operationId,
                    scanId,
                    "INSTALL",
                    null,
                    browserType,
                    browserType.getDisplayName(),
                    session.getProfileId(),
                    session.getProfileDisplayName(),
                    profileDirName,
                    bookmarkPath,
                    tempInstallFile,
                    currentHash,
                    stagedDoc.getTotalBookmarkCount()
            );

            activeOperations.put(operationId, operation);
            scanIdToOperationId.put(scanId, operationId);

            boolean isBrowserRunning = browserProcessDetector.isBrowserRunning(browserType);

            if (isBrowserRunning) {
                // Stage 2: Register in WAITING_FOR_BROWSER_CLOSURE and monitor asynchronously
                operation.setState(InstallationState.WAITING_FOR_BROWSER_CLOSURE);
                operation.setMessage("Close " + browserType.getDisplayName() + " completely. Bookmark Cleaner will finish installing your cleaned bookmarks automatically.");
                persistOperationState(profileDir, operation);

                scheduleBrowserClosureMonitoring(operation);

                List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);
                return new InstallResponse(
                        true,
                        operationId,
                        InstallationState.WAITING_FOR_BROWSER_CLOSURE.name(),
                        scanId,
                        browserType.getDisplayName(),
                        session.getProfileDisplayName(),
                        stagedDoc.getTotalBookmarkCount(),
                        BACKUP_1_FILENAME,
                        Files.exists(profileDir.resolve(BACKUP_1_FILENAME)),
                        operation.getMessage(),
                        backups
                );
            } else {
                // Browser is already closed: execute immediately
                executeInstallationTransaction(operation);

                List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);
                return new InstallResponse(
                        operation.getState() == InstallationState.COMPLETED,
                        operationId,
                        operation.getState().name(),
                        scanId,
                        browserType.getDisplayName(),
                        session.getProfileDisplayName(),
                        operation.getInstalledBookmarkCount(),
                        operation.getBackupCreated(),
                        operation.isBackupRotated(),
                        operation.getMessage(),
                        backups
                );
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Retrieves the status of the current or most recent installation operation for a scan.
     */
    public InstallationOperationDto getInstallationStatus(String scanId) {
        InstallationOperation op = getOperationForScan(scanId);
        if (op == null) {
            ScanSession session = sessionManager.getSession(scanId);
            Path originalPath = session.getOriginalBookmarkPath();
            if (originalPath != null) {
                op = loadOperationState(originalPath.getParent());
                if (op != null && scanId.equals(op.getScanId())) {
                    activeOperations.put(op.getOperationId(), op);
                    scanIdToOperationId.put(scanId, op.getOperationId());
                } else {
                    op = null;
                }
            }
        }

        if (op == null) {
            return null;
        }

        boolean browserRunning = browserProcessDetector.isBrowserRunning(op.getBrowserType());
        Path profileDir = op.getOriginalBookmarkPath() != null ? op.getOriginalBookmarkPath().getParent() : null;
        List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);

        return toDto(op, browserRunning, backups);
    }

    /**
     * Cancels a pending installation operation that is waiting for browser closure.
     */
    public InstallationOperationDto cancelInstallation(String scanId) {
        InstallationOperation op = getOperationForScan(scanId);
        if (op == null) {
            throw new IllegalArgumentException("No active installation operation found for scan: " + scanId);
        }

        if (op.getState() != InstallationState.WAITING_FOR_BROWSER_CLOSURE) {
            throw new IllegalStateException("Cannot cancel operation in state: " + op.getState());
        }

        // Cancel monitoring
        ScheduledFuture<?> task = monitoringTasks.remove(op.getOperationId());
        if (task != null) {
            task.cancel(true);
        }

        // Clean up staged temp file
        safeDelete(op.getStagedTempPath());

        op.setState(InstallationState.CANCELLED);
        op.setMessage("Operation was cancelled by user.");

        if (op.getOriginalBookmarkPath() != null) {
            persistOperationState(op.getOriginalBookmarkPath().getParent(), op);
        }

        boolean browserRunning = browserProcessDetector.isBrowserRunning(op.getBrowserType());
        Path profileDir = op.getOriginalBookmarkPath() != null ? op.getOriginalBookmarkPath().getParent() : null;
        List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);

        return toDto(op, browserRunning, backups);
    }

    /**
     * Schedules periodic monitoring of the target browser process until it completely exits.
     */
    public void scheduleBrowserClosureMonitoring(InstallationOperation operation) {
        String opId = operation.getOperationId();

        ScheduledFuture<?> future = monitorExecutor.scheduleWithFixedDelay(() -> {
            try {
                if (operation.getState() != InstallationState.WAITING_FOR_BROWSER_CLOSURE) {
                    ScheduledFuture<?> self = monitoringTasks.remove(opId);
                    if (self != null) self.cancel(false);
                    return;
                }

                boolean isRunning = browserProcessDetector.isBrowserRunning(operation.getBrowserType());
                if (!isRunning) {
                    log.info("Detected target browser closure for operation {}. Initiating installation transaction.", opId);
                    ScheduledFuture<?> self = monitoringTasks.remove(opId);
                    if (self != null) self.cancel(false);

                    if ("RESTORE".equalsIgnoreCase(operation.getOperationType())) {
                        executeRestoreTransaction(operation);
                    } else {
                        executeInstallationTransaction(operation);
                    }
                }
            } catch (Throwable t) {
                log.error("Error during browser closure monitoring for operation {}: {}", opId, t.getMessage(), t);
            }
        }, 1, 1, TimeUnit.SECONDS);

        monitoringTasks.put(opId, future);
    }

    /**
     * Executes the replacement transaction after browser closure has been verified.
     */
    public synchronized void executeInstallationTransaction(InstallationOperation operation) {
        Path bookmarkPath = operation.getOriginalBookmarkPath();
        Path profileDir = bookmarkPath.getParent();
        Path stagedFile = operation.getStagedTempPath();

        ReentrantLock lock = getLockForProfile(profileDir);
        lock.lock();

        Path stageBackup1 = null;
        Path stageBackup2 = null;

        try {
            operation.setState(InstallationState.INSTALLING);
            persistOperationState(profileDir, operation);

            // 1. Re-verify browser process is not running
            if (browserProcessDetector.isBrowserRunning(operation.getBrowserType())) {
                operation.setState(InstallationState.WAITING_FOR_BROWSER_CLOSURE);
                scheduleBrowserClosureMonitoring(operation);
                return;
            }

            // 2. Re-check file fingerprint
            String currentHash = BrowserImportService.computeSha256(bookmarkPath);
            if (operation.getExpectedOriginalHash() != null && !operation.getExpectedOriginalHash().equalsIgnoreCase(currentHash)) {
                operation.setState(InstallationState.FAILED);
                operation.setErrorMessage("The browser bookmark file was modified prior to closure. Replacement aborted to prevent data loss.");
                persistOperationState(profileDir, operation);
                safeDelete(stagedFile);
                return;
            }

            // 3. 2-Generation Backup Rotation
            Path backup1 = profileDir.resolve(BACKUP_1_FILENAME);
            Path backup2 = profileDir.resolve(BACKUP_2_FILENAME);
            boolean backupRotated = false;

            if (Files.exists(backup1)) {
                stageBackup2 = profileDir.resolve(BACKUP_2_FILENAME + ".staging." + UUID.randomUUID());
                Files.copy(backup1, stageBackup2, StandardCopyOption.REPLACE_EXISTING);
                backupRotated = true;
            }

            stageBackup1 = profileDir.resolve(BACKUP_1_FILENAME + ".staging." + UUID.randomUUID());
            Files.copy(bookmarkPath, stageBackup1, StandardCopyOption.REPLACE_EXISTING);

            if (stageBackup2 != null && Files.exists(stageBackup2)) {
                safeAtomicMoveOrReplace(stageBackup2, backup2);
                stageBackup2 = null;
            }
            safeAtomicMoveOrReplace(stageBackup1, backup1);
            stageBackup1 = null;

            // 4. Atomic move staged file to Bookmarks
            safeAtomicMoveOrReplace(stagedFile, bookmarkPath);

            // 5. Verification
            operation.setState(InstallationState.VERIFYING);
            BookmarkDocument installedDoc = bookmarkReader.read(bookmarkPath);

            operation.setState(InstallationState.COMPLETED);
            operation.setInstalledBookmarkCount(installedDoc.getTotalBookmarkCount());
            operation.setBackupCreated(BACKUP_1_FILENAME);
            operation.setBackupRotated(backupRotated);
            operation.setMessage("Successfully installed " + installedDoc.getTotalBookmarkCount()
                    + " cleaned bookmarks into " + operation.getBrowserName() + " (" + operation.getProfileDisplayName() + "). "
                    + (backupRotated ? "Backups rotated (Bookmarks.old.1 and Bookmarks.old.2 preserved)." : "Backup created at Bookmarks.old.1."));

            persistOperationState(profileDir, operation);

            // Update session hash if session is in memory
            try {
                ScanSession session = sessionManager.getSession(operation.getScanId());
                if (session != null) {
                    session.setOriginalFileHash(BrowserImportService.computeSha256(bookmarkPath));
                }
            } catch (Exception ignored) {
            }

            log.info("Installation transaction successfully completed for operation {}: {} bookmarks installed.",
                    operation.getOperationId(), installedDoc.getTotalBookmarkCount());

        } catch (Exception e) {
            log.error("Installation transaction failed for operation {}: {}", operation.getOperationId(), e.getMessage(), e);
            // Attempt rollback from backup1 if available
            Path backup1 = profileDir.resolve(BACKUP_1_FILENAME);
            try {
                if (Files.exists(backup1)) {
                    Files.copy(backup1, bookmarkPath, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception rollbackEx) {
                log.error("Critical: Rollback from backup1 failed: {}", rollbackEx.getMessage());
            }

            operation.setState(InstallationState.FAILED);
            operation.setErrorMessage("Installation failed: " + e.getMessage() + ". Original bookmarks restored.");
            persistOperationState(profileDir, operation);

        } finally {
            safeDelete(stagedFile);
            safeDelete(stageBackup1);
            safeDelete(stageBackup2);
            lock.unlock();
        }
    }

    /**
     * Restores a previously created versioned backup into active Bookmarks with deterministic rotation.
     * If the browser is running, schedules the restore to execute automatically on browser closure.
     */
    public RestoreResponse restoreBackup(String scanId, String backupId) {
        ScanSession session = sessionManager.getSession(scanId);

        if (!session.isImportedFromBrowser()) {
            throw new InvalidBookmarkException(
                    "Session was not imported from a browser profile. Direct browser restore is only supported for direct browser profile imports."
            );
        }

        BrowserType browserType = session.getBrowserType();
        Path bookmarkPath = session.getOriginalBookmarkPath();
        String profileDirName = session.getProfileDirectoryName();

        validateSessionProfileSecurity(session, browserType, profileDirName, bookmarkPath);

        String normalizedBackupId = normalizeBackupId(backupId);
        String targetBackupFilename = "Bookmarks.old." + normalizedBackupId;

        Path profileDir = bookmarkPath.getParent();
        Path targetBackupFile = profileDir.resolve(targetBackupFilename);

        ReentrantLock lock = getLockForProfile(profileDir);
        lock.lock();

        try {
            if (!Files.exists(targetBackupFile)) {
                throw new BackupNotFoundException(
                        "Requested backup file '" + targetBackupFilename + "' not found in profile directory."
                );
            }

            // Validate backup file content
            BookmarkDocument backupDoc;
            try {
                backupDoc = bookmarkReader.read(targetBackupFile);
            } catch (Exception e) {
                throw new BookmarkInstallationException(
                        "Backup file '" + targetBackupFilename + "' is invalid or corrupted: " + e.getMessage(), e
                );
            }

            // Stage restore file
            String operationId = UUID.randomUUID().toString();
            Path tempRestoreFile = profileDir.resolve(TMP_PREFIX + operationId);
            try {
                Files.copy(targetBackupFile, tempRestoreFile, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new BookmarkInstallationException("Failed to stage backup file: " + e.getMessage(), e);
            }

            InstallationOperation operation = new InstallationOperation(
                    operationId,
                    scanId,
                    "RESTORE",
                    normalizedBackupId,
                    browserType,
                    browserType.getDisplayName(),
                    session.getProfileId(),
                    session.getProfileDisplayName(),
                    profileDirName,
                    bookmarkPath,
                    tempRestoreFile,
                    null,
                    backupDoc.getTotalBookmarkCount()
            );

            activeOperations.put(operationId, operation);
            scanIdToOperationId.put(scanId, operationId);

            boolean isBrowserRunning = browserProcessDetector.isBrowserRunning(browserType);

            if (isBrowserRunning) {
                operation.setState(InstallationState.WAITING_FOR_BROWSER_CLOSURE);
                operation.setMessage("Close " + browserType.getDisplayName() + " completely. Bookmark Cleaner will restore your backup automatically.");
                persistOperationState(profileDir, operation);

                scheduleBrowserClosureMonitoring(operation);

                List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);
                return new RestoreResponse(
                        true,
                        operationId,
                        InstallationState.WAITING_FOR_BROWSER_CLOSURE.name(),
                        scanId,
                        normalizedBackupId,
                        browserType.getDisplayName(),
                        session.getProfileDisplayName(),
                        backupDoc.getTotalBookmarkCount(),
                        operation.getMessage(),
                        backups
                );
            } else {
                executeRestoreTransaction(operation);

                List<BackupInfoDto> backups = inspectAvailableBackups(profileDir);
                return new RestoreResponse(
                        operation.getState() == InstallationState.COMPLETED,
                        operationId,
                        operation.getState().name(),
                        scanId,
                        normalizedBackupId,
                        browserType.getDisplayName(),
                        session.getProfileDisplayName(),
                        operation.getInstalledBookmarkCount(),
                        operation.getMessage(),
                        backups
                );
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Executes the restore transaction with deterministic, lossless backup rotation.
     *
     * Semantics:
     * - Restoring Backup 1: Active Bookmarks becomes new Backup 1. Backup 2 remains untouched.
     *   (This allows symmetric undo: restoring Backup 1 again swaps back to the previous active bookmarks!)
     * - Restoring Backup 2: Active Bookmarks becomes new Backup 1. Old Backup 1 becomes Backup 2.
     *   (Lossless: both prior states remain safely accessible!)
     */
    public synchronized void executeRestoreTransaction(InstallationOperation operation) {
        Path bookmarkPath = operation.getOriginalBookmarkPath();
        Path profileDir = bookmarkPath.getParent();
        Path stagedRestoreFile = operation.getStagedTempPath();
        String backupId = operation.getBackupId();

        ReentrantLock lock = getLockForProfile(profileDir);
        lock.lock();

        Path backup1 = profileDir.resolve(BACKUP_1_FILENAME);
        Path backup2 = profileDir.resolve(BACKUP_2_FILENAME);

        try {
            operation.setState(InstallationState.INSTALLING);
            persistOperationState(profileDir, operation);

            // Re-verify browser closed
            if (browserProcessDetector.isBrowserRunning(operation.getBrowserType())) {
                operation.setState(InstallationState.WAITING_FOR_BROWSER_CLOSURE);
                scheduleBrowserClosureMonitoring(operation);
                return;
            }

            // Preserve active bookmarks before restore
            if (Files.exists(bookmarkPath)) {
                if ("2".equals(backupId)) {
                    // Restoring Version 2:
                    // Rotate backup1 to backup2, then active bookmarks to backup1
                    if (Files.exists(backup1)) {
                        Files.copy(backup1, backup2, StandardCopyOption.REPLACE_EXISTING);
                    }
                    Files.copy(bookmarkPath, backup1, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // Restoring Version 1:
                    // Active bookmarks becomes backup1, backup2 remains unchanged
                    Files.copy(bookmarkPath, backup1, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // Atomically replace Bookmarks with staged restore file
            safeAtomicMoveOrReplace(stagedRestoreFile, bookmarkPath);

            operation.setState(InstallationState.VERIFYING);
            BookmarkDocument installedDoc = bookmarkReader.read(bookmarkPath);

            operation.setState(InstallationState.COMPLETED);
            operation.setInstalledBookmarkCount(installedDoc.getTotalBookmarkCount());
            operation.setMessage("Successfully restored " + installedDoc.getTotalBookmarkCount()
                    + " bookmarks from Previous version " + backupId + " into " + operation.getBrowserName() + ".");

            persistOperationState(profileDir, operation);

            try {
                ScanSession session = sessionManager.getSession(operation.getScanId());
                if (session != null) {
                    session.setOriginalFileHash(BrowserImportService.computeSha256(bookmarkPath));
                }
            } catch (Exception ignored) {
            }

            log.info("Restore transaction successfully completed for operation {}: restored {} bookmarks from version {}.",
                    operation.getOperationId(), installedDoc.getTotalBookmarkCount(), backupId);

        } catch (Exception e) {
            log.error("Restore transaction failed for operation {}: {}", operation.getOperationId(), e.getMessage(), e);
            operation.setState(InstallationState.FAILED);
            operation.setErrorMessage("Restoration failed: " + e.getMessage());
            persistOperationState(profileDir, operation);
        } finally {
            safeDelete(stagedRestoreFile);
            lock.unlock();
        }
    }

    /**
     * Inspects a profile directory for existing versioned backups (Bookmarks.old.1 and Bookmarks.old.2).
     */
    public List<BackupInfoDto> inspectAvailableBackups(Path profileDir) {
        List<BackupInfoDto> backups = new ArrayList<>();
        if (profileDir == null || !Files.isDirectory(profileDir)) {
            return backups;
        }

        checkAndAddBackup(profileDir, "1", BACKUP_1_FILENAME, "Previous version 1 (Most Recent)", backups);
        checkAndAddBackup(profileDir, "2", BACKUP_2_FILENAME, "Previous version 2 (Older Backup)", backups);

        return backups;
    }

    private void checkAndAddBackup(Path profileDir, String id, String filename, String displayName, List<BackupInfoDto> list) {
        Path backupFile = profileDir.resolve(filename);
        if (Files.exists(backupFile) && !Files.isDirectory(backupFile)) {
            long size = 0;
            long lastModified = 0;
            try {
                size = Files.size(backupFile);
                FileTime ft = Files.getLastModifiedTime(backupFile);
                lastModified = ft.toMillis();
            } catch (Exception ignored) {
            }

            int count = -1;
            boolean valid = false;
            try {
                BookmarkDocument doc = bookmarkReader.read(backupFile);
                count = doc.getTotalBookmarkCount();
                valid = true;
            } catch (Exception ignored) {
            }

            list.add(new BackupInfoDto(id, filename, displayName, size, count, valid, lastModified));
        }
    }

    InstallationOperation getOperationForScan(String scanId) {
        String opId = scanIdToOperationId.get(scanId);
        if (opId != null) {
            return activeOperations.get(opId);
        }
        return null;
    }

    private InstallationOperationDto toDto(InstallationOperation op, boolean browserRunning, List<BackupInfoDto> backups) {
        return new InstallationOperationDto(
                op.getOperationId(),
                op.getScanId(),
                op.getOperationType(),
                op.getBackupId(),
                op.getState().name(),
                op.getBrowserName(),
                op.getProfileDisplayName(),
                op.getTargetBookmarkCount(),
                op.getInstalledBookmarkCount(),
                op.getBackupCreated(),
                op.isBackupRotated(),
                op.getMessage(),
                op.getErrorMessage(),
                browserRunning,
                op.getState() == InstallationState.WAITING_FOR_BROWSER_CLOSURE,
                backups,
                op.getUpdatedAt()
        );
    }

    private void persistOperationState(Path profileDir, InstallationOperation operation) {
        if (profileDir == null || !Files.isDirectory(profileDir)) return;
        Path opFile = profileDir.resolve(OPERATION_METADATA_FILE);
        try {
            objectMapper.writeValue(opFile.toFile(), operation);
        } catch (Exception e) {
            log.warn("Could not persist operation metadata: {}", e.getMessage());
        }
    }

    private InstallationOperation loadOperationState(Path profileDir) {
        if (profileDir == null || !Files.isDirectory(profileDir)) return null;
        Path opFile = profileDir.resolve(OPERATION_METADATA_FILE);
        if (Files.exists(opFile)) {
            try {
                return objectMapper.readValue(opFile.toFile(), InstallationOperation.class);
            } catch (Exception e) {
                log.warn("Could not load operation metadata: {}", e.getMessage());
            }
        }
        return null;
    }

    private String normalizeBackupId(String backupId) {
        if (backupId == null || backupId.isBlank()) {
            throw new IllegalArgumentException("Backup ID must not be empty");
        }
        String trimmed = backupId.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("1") || trimmed.endsWith(".1")) {
            return "1";
        }
        if (trimmed.equals("2") || trimmed.endsWith(".2")) {
            return "2";
        }
        throw new IllegalArgumentException("Invalid backup identifier: " + backupId + ". Must be '1' or '2'.");
    }

    private void validateSessionProfileSecurity(ScanSession session, BrowserType browserType, String profileDirName, Path bookmarkPath) {
        if (browserType == null || profileDirName == null || bookmarkPath == null) {
            throw new SecurityValidationException("Missing profile provenance details for secure browser replacement");
        }

        BrowserInfo browserInfo = discoveryService.inspectBrowser(browserType);
        if (!browserInfo.isAvailable() || browserInfo.getDataDirectory() == null) {
            throw new BrowserNotFoundException("Browser data directory inaccessible: " + browserType.getDisplayName());
        }

        Path dataDir = browserInfo.getDataDirectory().normalize().toAbsolutePath();
        Path expectedProfileDir = dataDir.resolve(profileDirName).normalize().toAbsolutePath();
        Path normalizedBookmark = bookmarkPath.normalize().toAbsolutePath();

        if (!normalizedBookmark.startsWith(expectedProfileDir) || !normalizedBookmark.startsWith(dataDir)) {
            throw new SecurityValidationException("Security validation failed: destination escapes verified browser profile directory");
        }

        try {
            Path realBookmark = normalizedBookmark.toRealPath();
            Path realDataDir = dataDir.toRealPath();
            if (!realBookmark.startsWith(realDataDir)) {
                throw new SecurityValidationException("Security validation failed: symbolic link escapes browser data directory");
            }
        } catch (IOException ignored) {
        }
    }

    private void safeAtomicMoveOrReplace(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void safeDelete(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (Exception ignored) {
            }
        }
    }

    private ReentrantLock getLockForProfile(Path profileDir) {
        String key = profileDir.toAbsolutePath().normalize().toString();
        return profileLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}
