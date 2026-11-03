package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.exception.ScanNotFoundException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.ScanSession;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory manager for active scan sessions.
 */
@Service
public class ScanSessionManager {

    private final Map<String, ScanSession> sessions = new ConcurrentHashMap<>();

    /**
     * Creates and registers a new {@link ScanSession} for the uploaded document.
     */
    public ScanSession createSession(String originalFilename, BookmarkDocument document) {
        String scanId = UUID.randomUUID().toString();
        ScanSession session = new ScanSession(scanId, originalFilename, document);
        sessions.put(scanId, session);
        return session;
    }

    /**
     * Registers an existing session.
     */
    public void registerSession(ScanSession session) {
        if (session != null && session.getScanId() != null) {
            sessions.put(session.getScanId(), session);
        }
    }

    /**
     * Retrieves an active session by its scan ID.
     *
     * @throws ScanNotFoundException if the session does not exist
     */
    public ScanSession getSession(String scanId) {
        if (scanId == null) {
            throw new ScanNotFoundException("null");
        }
        ScanSession session = sessions.get(scanId);
        if (session == null) {
            throw new ScanNotFoundException(scanId);
        }
        return session;
    }

    /**
     * Checks if a scan session exists.
     */
    public boolean hasSession(String scanId) {
        return scanId != null && sessions.containsKey(scanId);
    }

    /**
     * Removes a session.
     */
    public boolean removeSession(String scanId) {
        return scanId != null && sessions.remove(scanId) != null;
    }

    /**
     * Total number of currently active in-memory sessions.
     */
    public int getActiveSessionCount() {
        return sessions.size();
    }
}
