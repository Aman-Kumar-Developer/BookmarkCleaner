package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.DuplicateBookmarkItem;
import com.aman.bookmark_cleaner.model.DuplicateGroup;
import com.aman.bookmark_cleaner.model.DuplicateType;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that detects exact and potential duplicate bookmarks within a Chromium bookmark tree.
 * Preserves the original document and assigns deterministic retention proposals.
 */
@Service
public class DuplicateDetectionService {

    /**
     * Scans the bookmark document and returns all detected duplicate groups.
     * The input document is never modified.
     */
    public List<DuplicateGroup> findDuplicates(BookmarkDocument document) {
        if (document == null || document.getRoots() == null) {
            return Collections.emptyList();
        }

        // 1. Collect all URL bookmarks in traversal order with their folder paths and identities
        List<DuplicateBookmarkItem> allBookmarks = new ArrayList<>();
        AtomicInteger generatedIdSeq = new AtomicInteger(1);

        for (Map.Entry<String, BookmarkNode> entry : document.getRoots().getAllRoots().entrySet()) {
            BookmarkNode root = entry.getValue();
            if (root != null) {
                String rootName = root.getName() != null && !root.getName().isBlank() ? root.getName() : entry.getKey();
                collectBookmarksRecursively(root, rootName, allBookmarks, generatedIdSeq);
            }
        }

        return findDuplicatesInList(allBookmarks);
    }

    /**
     * Identifies exact and potential duplicate groups from an ordered list of bookmark items.
     */
    public List<DuplicateGroup> findDuplicatesInList(List<DuplicateBookmarkItem> items) {
        if (items == null || items.size() < 2) {
            return Collections.emptyList();
        }

        List<DuplicateGroup> duplicateGroups = new ArrayList<>();
        AtomicInteger groupIndex = new AtomicInteger(1);

        // Map for exact duplicates: exactNormalizedUrl -> list of items
        Map<String, List<DuplicateBookmarkItem>> exactMap = new LinkedHashMap<>();
        for (DuplicateBookmarkItem item : items) {
            String exactKey = normalizeForExactDuplicate(item.getOriginalUrl());
            if (!exactKey.isEmpty()) {
                exactMap.computeIfAbsent(exactKey, k -> new ArrayList<>()).add(item);
            }
        }

        // Track items already assigned to an exact duplicate group
        Set<String> assignedItemIds = new HashSet<>();

        // Form exact duplicate groups
        for (Map.Entry<String, List<DuplicateBookmarkItem>> entry : exactMap.entrySet()) {
            List<DuplicateBookmarkItem> groupItems = entry.getValue();
            if (groupItems.size() > 1) {
                DuplicateBookmarkItem retained = groupItems.get(0);
                List<DuplicateBookmarkItem> removals = groupItems.subList(1, groupItems.size());
                String groupId = "dup-exact-" + groupIndex.getAndIncrement();

                duplicateGroups.add(new DuplicateGroup(groupId, DuplicateType.EXACT, entry.getKey(), retained, removals));
                for (DuplicateBookmarkItem item : groupItems) {
                    assignedItemIds.add(item.getBookmarkId());
                }
            }
        }

        // Map for potential duplicates: potentialNormalizedUrl -> list of items
        // Exclude items already assigned to an exact duplicate group
        Map<String, List<DuplicateBookmarkItem>> potentialMap = new LinkedHashMap<>();
        for (DuplicateBookmarkItem item : items) {
            if (!assignedItemIds.contains(item.getBookmarkId())) {
                String potentialKey = normalizeForPotentialDuplicate(item.getOriginalUrl());
                if (!potentialKey.isEmpty()) {
                    potentialMap.computeIfAbsent(potentialKey, k -> new ArrayList<>()).add(item);
                }
            }
        }

        // Form potential duplicate groups
        for (Map.Entry<String, List<DuplicateBookmarkItem>> entry : potentialMap.entrySet()) {
            List<DuplicateBookmarkItem> groupItems = entry.getValue();
            if (groupItems.size() > 1) {
                DuplicateBookmarkItem retained = groupItems.get(0);
                List<DuplicateBookmarkItem> removals = groupItems.subList(1, groupItems.size());
                String groupId = "dup-pot-" + groupIndex.getAndIncrement();

                duplicateGroups.add(new DuplicateGroup(groupId, DuplicateType.POTENTIAL, entry.getKey(), retained, removals));
            }
        }

        return duplicateGroups;
    }

    /**
     * Performs safe, conservative normalization for exact duplicate detection.
     * - Lowercase scheme
     * - Lowercase host
     * - Strips default ports (80 for http, 443 for https)
     * - Strips URL fragment
     * - Strictly preserves paths, trailing slashes, and query parameters
     */
    public String normalizeForExactDuplicate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }

        String trimmed = rawUrl.trim();
        URI uri = safeParseUri(trimmed);
        if (uri == null || uri.getScheme() == null || uri.getHost() == null) {
            return trimmed;
        }

        String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }

        int port = uri.getPort();
        boolean isDefaultPort = (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443) || port == -1);

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (!isDefaultPort) {
            sb.append(":").append(port);
        }

        String path = uri.getRawPath();
        if (path != null && !path.isEmpty()) {
            sb.append(path);
        } else {
            sb.append("/");
        }

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            sb.append("?").append(query);
        }

        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.isBlank()) {
            sb.append("#").append(fragment);
        }

        return sb.toString();
    }

    /**
     * Performs conservative normalization for potential duplicate detection.
     * - Normalizes HTTP to HTTPS
     * - Normalizes WWW prefix
     * - Lowercase scheme and host
     * - Strips default ports and fragments
     * - Strictly preserves paths and query parameters
     */
    public String normalizeForPotentialDuplicate(String rawUrl) {
        String exactNormalized = normalizeForExactDuplicate(rawUrl);
        if (exactNormalized.isEmpty()) {
            return "";
        }

        URI uri = safeParseUri(exactNormalized);
        if (uri == null || uri.getHost() == null) {
            return exactNormalized;
        }

        String host = uri.getHost();
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }

        // Normalize scheme to https
        StringBuilder sb = new StringBuilder();
        sb.append("https://").append(host);

        int port = uri.getPort();
        if (port != -1 && port != 80 && port != 443) {
            sb.append(":").append(port);
        }

        String path = uri.getRawPath();
        if (path != null && !path.isEmpty()) {
            sb.append(path);
        } else {
            sb.append("/");
        }

        String query = uri.getRawQuery();
        if (query != null && !query.isEmpty()) {
            sb.append("?").append(query);
        }

        String fragment = uri.getRawFragment();
        if (fragment != null && !fragment.isBlank()) {
            sb.append("#").append(fragment);
        }

        return sb.toString();
    }

    private void collectBookmarksRecursively(BookmarkNode node, String currentPath, List<DuplicateBookmarkItem> results, AtomicInteger generatedIdSeq) {
        if (node == null) {
            return;
        }

        if (node.isUrl() && node.getUrl() != null && !node.getUrl().isBlank()) {
            String id = (node.getId() != null && !node.getId().isBlank()) ? node.getId() : "gen-" + generatedIdSeq.getAndIncrement();
            String name = (node.getName() != null) ? node.getName() : "";
            results.add(new DuplicateBookmarkItem(id, name, node.getUrl(), currentPath));
        }

        if (node.getChildren() != null) {
            for (BookmarkNode child : node.getChildren()) {
                if (child != null) {
                    String childPath = currentPath;
                    if (child.isFolder()) {
                        String folderName = child.getName() != null && !child.getName().isBlank() ? child.getName() : child.getId();
                        childPath = currentPath.isEmpty() ? folderName : currentPath + " / " + folderName;
                    }
                    collectBookmarksRecursively(child, childPath, results, generatedIdSeq);
                }
            }
        }
    }

    private URI safeParseUri(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null;
        }
        try {
            return URI.create(rawUrl);
        } catch (Exception e) {
            try {
                return new URI(rawUrl);
            } catch (Exception ex) {
                try {
                    String ascii = toAsciiUriString(rawUrl);
                    return URI.create(ascii);
                } catch (Exception ex2) {
                    return null;
                }
            }
        }
    }

    private String toAsciiUriString(String rawUrl) {
        int schemeIdx = rawUrl.indexOf("://");
        if (schemeIdx == -1) {
            return rawUrl;
        }
        String scheme = rawUrl.substring(0, schemeIdx + 3);
        String rest = rawUrl.substring(schemeIdx + 3);
        int slashIdx = rest.indexOf('/');
        String hostPart = (slashIdx != -1) ? rest.substring(0, slashIdx) : rest;
        String pathPart = (slashIdx != -1) ? rest.substring(slashIdx) : "";

        int portIdx = hostPart.lastIndexOf(':');
        String host = (portIdx != -1 && !hostPart.contains("]")) ? hostPart.substring(0, portIdx) : hostPart;
        String port = (portIdx != -1 && !hostPart.contains("]")) ? hostPart.substring(portIdx) : "";

        try {
            String asciiHost = java.net.IDN.toASCII(host, java.net.IDN.ALLOW_UNASSIGNED);
            return scheme + asciiHost + port + pathPart;
        } catch (Exception e) {
            return rawUrl;
        }
    }
}
