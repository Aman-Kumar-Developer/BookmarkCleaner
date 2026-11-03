package com.aman.bookmark_cleaner.parser;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Jackson-backed implementation of {@link BookmarkReader} for Chromium bookmark JSON files.
 * Handles reading from a {@link Path}, validating Chromium document structure,
 * and preserving nested folders and metadata.
 */
@Component
public class JsonBookmarkReader implements BookmarkReader {

    public static final long DEFAULT_MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024L; // 50 MB

    private final ObjectMapper objectMapper;
    private final BookmarkParserProperties properties;
    private final long maxFileSizeBytes;

    @Autowired
    public JsonBookmarkReader(ObjectMapper objectMapper, BookmarkParserProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.maxFileSizeBytes = (properties != null && properties.getMaxFileSize() > 0)
                ? properties.getMaxFileSize()
                : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    public JsonBookmarkReader(ObjectMapper objectMapper) {
        this(objectMapper, (BookmarkParserProperties) null);
    }

    public JsonBookmarkReader(ObjectMapper objectMapper, long maxFileSizeBytes) {
        this.objectMapper = objectMapper;
        this.properties = null;
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    private long resolveEffectiveMaxFileSize() {
        if (properties != null && properties.getMaxFileSize() > 0) {
            return properties.getMaxFileSize();
        }
        return maxFileSizeBytes > 0 ? maxFileSizeBytes : DEFAULT_MAX_FILE_SIZE_BYTES;
    }

    @Override
    public BookmarkDocument read(Path path) {
        if (path == null) {
            throw new BookmarkFileNotFoundException("Bookmark file path must not be null");
        }

        if (!Files.exists(path)) {
            throw new BookmarkFileNotFoundException("Bookmark file not found: " + path.toAbsolutePath());
        }

        if (Files.isDirectory(path)) {
            throw new InvalidBookmarkException("Supplied path is a directory, not a file: " + path.toAbsolutePath());
        }

        if (!Files.isReadable(path)) {
            throw new BookmarkReaderException("Bookmark file cannot be read (insufficient permissions): " + path.toAbsolutePath());
        }

        long fileSize;
        try {
            fileSize = Files.size(path);
        } catch (IOException e) {
            throw new BookmarkReaderException("Failed to read file size: " + path.toAbsolutePath(), e);
        }

        if (fileSize == 0) {
            throw new InvalidBookmarkException("Bookmark file is empty (0 bytes): " + path.toAbsolutePath());
        }

        long effectiveLimit = resolveEffectiveMaxFileSize();
        if (fileSize > effectiveLimit) {
            throw new BookmarkReaderException("Bookmark file size (" + fileSize + " bytes) exceeds maximum permitted limit (" + effectiveLimit + " bytes): " + path.toAbsolutePath());
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            BookmarkDocument document = objectMapper.readValue(inputStream, BookmarkDocument.class);
            validateDocumentStructure(document, path);
            return document;
        } catch (JacksonException e) {
            throw new InvalidBookmarkException("Invalid JSON syntax or incompatible data in bookmark file: " + path.toAbsolutePath() + ". Details: " + e.getOriginalMessage(), e);
        } catch (IOException e) {
            throw new BookmarkReaderException("I/O error while reading bookmark file: " + path.toAbsolutePath(), e);
        }
    }

    private void validateDocumentStructure(BookmarkDocument document, Path path) {
        if (document == null) {
            throw new InvalidBookmarkException("Bookmark file did not yield a valid document object: " + path.toAbsolutePath());
        }

        if (document.getRoots() == null) {
            throw new InvalidBookmarkException("Unsupported bookmark document: missing required 'roots' property in " + path.toAbsolutePath());
        }

        Map<String, BookmarkNode> allRoots = document.getRoots().getAllRoots();
        for (Map.Entry<String, BookmarkNode> entry : allRoots.entrySet()) {
            validateNode(entry.getValue(), path, "root '" + entry.getKey() + "'");
        }
    }

    private void validateNode(BookmarkNode node, Path path, String context) {
        if (node == null) {
            throw new InvalidBookmarkException("Malformed bookmark tree: found null node in " + context + " in " + path.toAbsolutePath());
        }

        if (node.isUrl() && node.getUrl() == null) {
            throw new InvalidBookmarkException("Malformed bookmark node: URL bookmark has missing 'url' property in " + context + " in " + path.toAbsolutePath());
        }

        if (node.getChildren() != null) {
            for (BookmarkNode child : node.getChildren()) {
                validateNode(child, path, "folder '" + (node.getName() != null ? node.getName() : node.getId()) + "'");
            }
        }
    }
}
