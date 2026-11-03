package com.aman.bookmark_cleaner.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Represents an individual Chromium bookmark node (either a URL bookmark or a folder).
 * Preserves standard Chromium properties as well as arbitrary unknown/extension metadata.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookmarkNode {

    public static final String TYPE_FOLDER = "folder";
    public static final String TYPE_URL = "url";

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("url")
    private String url;

    @JsonProperty("date_added")
    private String dateAdded;

    @JsonProperty("date_last_used")
    private String dateLastUsed;

    @JsonProperty("date_modified")
    private String dateModified;

    @JsonProperty("guid")
    private String guid;

    @JsonProperty("meta_info")
    private Map<String, Object> metaInfo;

    @JsonProperty("children")
    private List<BookmarkNode> children;

    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    public BookmarkNode() {
    }

    public BookmarkNode(String id, String name, String type) {
        this.id = id;
        this.name = name;
        this.type = type;
        if (TYPE_FOLDER.equalsIgnoreCase(type)) {
            this.children = new ArrayList<>();
        }
    }

    public static BookmarkNode createFolder(String id, String name) {
        BookmarkNode node = new BookmarkNode(id, name, TYPE_FOLDER);
        node.children = new ArrayList<>();
        return node;
    }

    public static BookmarkNode createUrl(String id, String name, String url) {
        BookmarkNode node = new BookmarkNode(id, name, TYPE_URL);
        node.url = url;
        return node;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(String dateAdded) {
        this.dateAdded = dateAdded;
    }

    public String getDateLastUsed() {
        return dateLastUsed;
    }

    public void setDateLastUsed(String dateLastUsed) {
        this.dateLastUsed = dateLastUsed;
    }

    public String getDateModified() {
        return dateModified;
    }

    public void setDateModified(String dateModified) {
        this.dateModified = dateModified;
    }

    public String getGuid() {
        return guid;
    }

    public void setGuid(String guid) {
        this.guid = guid;
    }

    public Map<String, Object> getMetaInfo() {
        return metaInfo;
    }

    public void setMetaInfo(Map<String, Object> metaInfo) {
        this.metaInfo = metaInfo;
    }

    public List<BookmarkNode> getChildren() {
        return children;
    }

    public void setChildren(List<BookmarkNode> children) {
        this.children = children;
    }

    public void addChild(BookmarkNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
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

    @JsonIgnore
    public boolean isFolder() {
        return TYPE_FOLDER.equalsIgnoreCase(type) || (children != null && !TYPE_URL.equalsIgnoreCase(type));
    }

    @JsonIgnore
    public boolean isUrl() {
        return TYPE_URL.equalsIgnoreCase(type) || (url != null && !TYPE_FOLDER.equalsIgnoreCase(type));
    }

    @JsonIgnore
    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    /**
     * Traverses this node and its descendants recursively in depth-first order.
     */
    public void traverse(Consumer<BookmarkNode> visitor) {
        if (visitor == null) {
            return;
        }
        visitor.accept(this);
        if (children != null) {
            for (BookmarkNode child : children) {
                if (child != null) {
                    child.traverse(visitor);
                }
            }
        }
    }

    /**
     * Recursively collects all descendant nodes satisfying the given predicate.
     */
    public List<BookmarkNode> findNodes(Predicate<BookmarkNode> predicate) {
        List<BookmarkNode> results = new ArrayList<>();
        traverse(node -> {
            if (predicate == null || predicate.test(node)) {
                results.add(node);
            }
        });
        return results;
    }

    /**
     * Recursively collects all descendant URL bookmark nodes under this node.
     */
    @JsonIgnore
    public List<BookmarkNode> getAllDescendantUrls() {
        List<BookmarkNode> urls = new ArrayList<>();
        if (children != null) {
            for (BookmarkNode child : children) {
                if (child != null) {
                    if (child.isUrl()) {
                        urls.add(child);
                    }
                    if (child.isFolder()) {
                        urls.addAll(child.getAllDescendantUrls());
                    }
                }
            }
        }
        return urls;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookmarkNode that = (BookmarkNode) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(url, that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, type, url);
    }

    @Override
    public String toString() {
        return "BookmarkNode{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", url='" + url + '\'' +
                ", childrenCount=" + (children != null ? children.size() : "none") +
                ", additionalPropertiesCount=" + additionalProperties.size() +
                '}';
    }
}
