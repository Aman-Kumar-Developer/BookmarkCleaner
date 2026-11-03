package com.aman.bookmark_cleaner.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the browser bookmark roots container in a Chromium bookmark document.
 * Contains standard roots (bookmark_bar, other, synced) and any browser-specific or
 * extension root folders.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookmarkRoots {

    public static final String ROOT_BOOKMARK_BAR = "bookmark_bar";
    public static final String ROOT_OTHER = "other";
    public static final String ROOT_SYNCED = "synced";

    @JsonProperty("bookmark_bar")
    private BookmarkNode bookmarkBar;

    @JsonProperty("other")
    private BookmarkNode other;

    @JsonProperty("synced")
    private BookmarkNode synced;

    private final Map<String, BookmarkNode> additionalRoots = new LinkedHashMap<>();

    public BookmarkRoots() {
    }

    public BookmarkRoots(BookmarkNode bookmarkBar, BookmarkNode other, BookmarkNode synced) {
        this.bookmarkBar = bookmarkBar;
        this.other = other;
        this.synced = synced;
    }

    public BookmarkNode getBookmarkBar() {
        return bookmarkBar;
    }

    public void setBookmarkBar(BookmarkNode bookmarkBar) {
        this.bookmarkBar = bookmarkBar;
    }

    public BookmarkNode getOther() {
        return other;
    }

    public void setOther(BookmarkNode other) {
        this.other = other;
    }

    public BookmarkNode getSynced() {
        return synced;
    }

    public void setSynced(BookmarkNode synced) {
        this.synced = synced;
    }

    @JsonAnySetter
    public void setAdditionalRoot(String name, BookmarkNode node) {
        this.additionalRoots.put(name, node);
    }

    @JsonAnyGetter
    public Map<String, BookmarkNode> getAdditionalRoots() {
        return additionalRoots;
    }

    /**
     * Retrieves a root by name, checking standard roots first and then additional roots.
     */
    public BookmarkNode getRoot(String name) {
        if (ROOT_BOOKMARK_BAR.equals(name)) {
            return bookmarkBar;
        }
        if (ROOT_OTHER.equals(name)) {
            return other;
        }
        if (ROOT_SYNCED.equals(name)) {
            return synced;
        }
        return additionalRoots.get(name);
    }

    /**
     * Sets a root by name, routing standard roots to their respective fields
     * and custom roots to additional roots.
     */
    public void setRoot(String name, BookmarkNode node) {
        if (ROOT_BOOKMARK_BAR.equals(name)) {
            this.bookmarkBar = node;
        } else if (ROOT_OTHER.equals(name)) {
            this.other = node;
        } else if (ROOT_SYNCED.equals(name)) {
            this.synced = node;
        } else {
            this.additionalRoots.put(name, node);
        }
    }

    /**
     * Returns an unmodifiable map containing all roots (standard + additional) present in this document.
     */
    public Map<String, BookmarkNode> getAllRoots() {
        Map<String, BookmarkNode> all = new LinkedHashMap<>();
        if (bookmarkBar != null) {
            all.put(ROOT_BOOKMARK_BAR, bookmarkBar);
        }
        if (other != null) {
            all.put(ROOT_OTHER, other);
        }
        if (synced != null) {
            all.put(ROOT_SYNCED, synced);
        }
        all.putAll(additionalRoots);
        return Collections.unmodifiableMap(all);
    }

    @JsonIgnore
    public boolean isEmpty() {
        return bookmarkBar == null && other == null && synced == null && additionalRoots.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BookmarkRoots that = (BookmarkRoots) o;
        return Objects.equals(bookmarkBar, that.bookmarkBar) &&
                Objects.equals(other, that.other) &&
                Objects.equals(synced, that.synced) &&
                Objects.equals(additionalRoots, that.additionalRoots);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bookmarkBar, other, synced, additionalRoots);
    }

    @Override
    public String toString() {
        return "BookmarkRoots{" +
                "hasBookmarkBar=" + (bookmarkBar != null) +
                ", hasOther=" + (other != null) +
                ", hasSynced=" + (synced != null) +
                ", additionalRoots=" + additionalRoots.keySet() +
                '}';
    }
}
