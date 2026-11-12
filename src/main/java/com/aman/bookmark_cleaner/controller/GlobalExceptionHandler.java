package com.aman.bookmark_cleaner.controller;

import com.aman.bookmark_cleaner.dto.ErrorResponse;
import com.aman.bookmark_cleaner.exception.BackupNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkFileModifiedException;
import com.aman.bookmark_cleaner.exception.BookmarkFileNotFoundException;
import com.aman.bookmark_cleaner.exception.BookmarkInstallationException;
import com.aman.bookmark_cleaner.exception.BookmarkReaderException;
import com.aman.bookmark_cleaner.exception.BrowserNotFoundException;
import com.aman.bookmark_cleaner.exception.BrowserProfileNotFoundException;
import com.aman.bookmark_cleaner.exception.BrowserRunningException;
import com.aman.bookmark_cleaner.exception.ComparisonItemNotFoundException;
import com.aman.bookmark_cleaner.exception.ExportException;
import com.aman.bookmark_cleaner.exception.InvalidBookmarkException;
import com.aman.bookmark_cleaner.exception.InvalidDecisionException;
import com.aman.bookmark_cleaner.exception.ScanNotFoundException;
import com.aman.bookmark_cleaner.exception.SecurityValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Central exception handler providing consistent, user-friendly JSON error responses.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ScanNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleScanNotFound(ScanNotFoundException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Scan Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(ComparisonItemNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleComparisonItemNotFound(ComparisonItemNotFoundException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Comparison Item Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler({BrowserNotFoundException.class, BrowserProfileNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleBrowserOrProfileNotFound(Exception ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler({BookmarkFileNotFoundException.class, BackupNotFoundException.class})
    public ResponseEntity<ErrorResponse> handleFileNotFound(Exception ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.NOT_FOUND.value(), "File Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    @ExceptionHandler(BrowserRunningException.class)
    public ResponseEntity<ErrorResponse> handleBrowserRunning(BrowserRunningException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), "Browser Is Running", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BookmarkFileModifiedException.class)
    public ResponseEntity<ErrorResponse> handleBookmarkFileModified(BookmarkFileModifiedException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.CONFLICT.value(), "Bookmark File Modified", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(BookmarkInstallationException.class)
    public ResponseEntity<ErrorResponse> handleInstallationException(BookmarkInstallationException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Installation Failed", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler({InvalidBookmarkException.class, InvalidDecisionException.class, IllegalArgumentException.class, SecurityValidationException.class, BookmarkReaderException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Bad Request", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(ExportException.class)
    public ResponseEntity<ErrorResponse> handleExportException(ExportException ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Export Failed", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception ex, HttpServletRequest request) {
        ErrorResponse error = new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal Server Error", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
