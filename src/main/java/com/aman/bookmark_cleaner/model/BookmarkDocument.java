package com.aman.bookmark_cleaner.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the root Chromium bookmark document.
 * Contains document metadata (checksum, version), browser bookmark roots,
 * and preserves any unknown document-level extension properties.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookmarkDocument {

    @JsonProperty("checksum")
    private String checksum;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("roots")
    private BookmarkRoots roots;

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public BookmarkDocument() {
    }

    public BookmarkDocument(BookmarkRoots roots) {
        this.roots = roots;
        this.version = 1;
    }

    public String getChecksum() {
        return checksum;
    }

    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public BookmarkRoots getRoots() {
        return roots;
    }

    public void setRoots(BookmarkRoots roots) {
        this.roots = roots;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String key, Object value) {
        this.additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    public Object getAdditionalProperty(String key) {
        return this.additionalProperties.get(key);
    }

    /**
     * Recursively traverses all roots and returns all URL bookmarks.
     */
    @JsonIgnore
    public List<BookmarkNode> getAllUrlBookmarks() {
        List<BookmarkNode> bookmarks = new ArrayList<>();
        if (roots != null) {
            for (BookmarkNode root : roots.getAllRoots().values()) {
                if (root != null) {
                    if (root.isUrl()) {
                        bookmarks.add(root);
                    }
                    bookmarks.addAll(root.getAllDescendantUrls());
                }
            }
        }
        return bookmarks;
    }

    /**
     * Recursively traverses all roots and returns all nodes (both folders and URLs).
     */
    @JsonIgnore
    public List<BookmarkNode> getAllNodes() {
        List<BookmarkNode> allNodes = new ArrayList<>();
        if (roots != null) {
            for (BookmarkNode root : roots.getAllRoots().values()) {
                if (root != null) {
                    root.traverse(allNodes::add);
                }
            }
        }
        return allNodes;
    }

    /**
     * Recursively traverses all roots and returns all folder nodes.
     */
    @JsonIgnore
    public List<BookmarkNode> getAllFolders() {
        List<BookmarkNode> folders = new ArrayList<>();
        if (roots != null) {
            for (BookmarkNode root : roots.getAllRoots().values()) {
                if (root != null) {
                    root.traverse(node -> {
                        if (node.isFolder()) {
                            folders.add(node);
                        }
                    });
                }
            }
        }
        return folders;
    }

    /**
     * Total number of URL bookmarks across all roots.
     */
    @JsonIgnore
    public int getTotalBookmarkCount() {
        return getAllUrlBookmarks().size();
    }

    /**
     * Total number of folder nodes across all roots.
     */
    @JsonIgnore
    public int getTotalFolderCount() {
        return getAllFolders().size();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookmarkDocument that = (BookmarkDocument) o;
        return Objects.equals(checksum, that.checksum) &&
                Objects.equals(version, that.version) &&
                Objects.equals(roots, that.roots) &&
                Objects.equals(additionalProperties, that.additionalProperties);
    }

    @Override
    public int hashCode() {
        return Objects.hash(checksum, version, roots, additionalProperties);
    }

    @Override
    public String toString() {
        return "BookmarkDocument{" +
                "version=" + version +
                ", checksum='" + checksum + '\'' +
                ", totalBookmarks=" + getTotalBookmarkCount() +
                ", totalFolders=" + getTotalFolderCount() +
                ", additionalProperties=" + additionalProperties.keySet() +
                '}';
    }
}
