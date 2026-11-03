package com.aman.bookmark_cleaner.parser;

import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.model.BookmarkDocument;

import java.nio.file.Path;

/**
 * Abstraction for reading and parsing a Chromium bookmark document.
 */
public interface BookmarkReader {

    /**
     * Reads a Chromium bookmark document from the specified file path.
     *
     * @param path the path to the bookmark file
     * @return the parsed {@link BookmarkDocument}
     * @throws BookmarkFileNotFoundException if the file does not exist
     * @throws InvalidBookmarkException     if the file is empty, invalid JSON, or structurally unsupported
     * @throws BookmarkReaderException      if an I/O error or size constraint violation occurs
     */
    BookmarkDocument read(Path path);
}
