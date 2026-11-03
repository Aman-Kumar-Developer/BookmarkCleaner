package com.aman.bookmark_cleaner;

import com.aman.bookmark_cleaner.config.BookmarkParserProperties;
import com.aman.bookmark_cleaner.config.BookmarkValidationProperties;
import com.aman.bookmark_cleaner.controller.BookmarkUploadController;
import com.aman.bookmark_cleaner.controller.GlobalExceptionHandler;
import com.aman.bookmark_cleaner.controller.ScanController;
import com.aman.bookmark_cleaner.parser.BookmarkReader;
import com.aman.bookmark_cleaner.parser.JsonBookmarkReader;
import com.aman.bookmark_cleaner.service.BookmarkExportService;
import com.aman.bookmark_cleaner.service.BookmarkProcessingEngine;
import com.aman.bookmark_cleaner.service.BookmarkTreeReconstructionService;
import com.aman.bookmark_cleaner.service.ComparisonService;
import com.aman.bookmark_cleaner.service.DecisionService;
import com.aman.bookmark_cleaner.service.DomainComparisonService;
import com.aman.bookmark_cleaner.service.DuplicateDetectionService;
import com.aman.bookmark_cleaner.service.ScanOrchestrationService;
import com.aman.bookmark_cleaner.service.ScanSessionManager;
import com.aman.bookmark_cleaner.validator.BookmarkValidator;
import com.aman.bookmark_cleaner.validator.HttpBookmarkValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class BookmarkCleanerApplicationTests {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private BookmarkReader bookmarkReader;

	@Autowired
	private BookmarkParserProperties parserProperties;

	@Autowired
	private BookmarkValidator bookmarkValidator;

	@Autowired
	private DuplicateDetectionService duplicateDetectionService;

	@Autowired
	private DomainComparisonService domainComparisonService;

	@Autowired
	private BookmarkProcessingEngine processingEngine;

	@Autowired
	private BookmarkValidationProperties validationProperties;

	@Autowired
	private ComparisonService comparisonService;

	@Autowired
	private ScanSessionManager scanSessionManager;

	@Autowired
	private DecisionService decisionService;

	@Autowired
	private BookmarkTreeReconstructionService reconstructionService;

	@Autowired
	private BookmarkExportService exportService;

	@Autowired
	private ScanOrchestrationService orchestrationService;

	@Autowired
	private BookmarkUploadController bookmarkUploadController;

	@Autowired
	private ScanController scanController;

	@Autowired
	private GlobalExceptionHandler globalExceptionHandler;

	@Test
	void contextLoads() {
		assertNotNull(objectMapper, "ObjectMapper bean should be auto-configured");
		assertNotNull(bookmarkReader, "BookmarkReader bean should be loaded in application context");
		assertInstanceOf(JsonBookmarkReader.class, bookmarkReader);
		assertNotNull(parserProperties, "BookmarkParserProperties bean should be loaded");
		assertEquals(52428800L, parserProperties.getMaxFileSize(), "Default max file size should be 50MB");

		// Stage 2 Bean Verifications
		assertNotNull(bookmarkValidator, "BookmarkValidator bean should be loaded");
		assertInstanceOf(HttpBookmarkValidator.class, bookmarkValidator);
		assertNotNull(duplicateDetectionService, "DuplicateDetectionService bean should be loaded");
		assertNotNull(domainComparisonService, "DomainComparisonService bean should be loaded");
		assertNotNull(processingEngine, "BookmarkProcessingEngine bean should be loaded");
		assertNotNull(validationProperties, "BookmarkValidationProperties bean should be loaded");
		assertEquals(20, validationProperties.getMaxConcurrentRequests());
		assertEquals(4, validationProperties.getMaxConcurrentRequestsPerHost());

		// Stage 3 Bean Verifications
		assertNotNull(comparisonService, "ComparisonService bean should be loaded");
		assertNotNull(scanSessionManager, "ScanSessionManager bean should be loaded");
		assertNotNull(decisionService, "DecisionService bean should be loaded");
		assertNotNull(reconstructionService, "BookmarkTreeReconstructionService bean should be loaded");
		assertNotNull(exportService, "BookmarkExportService bean should be loaded");
		assertNotNull(orchestrationService, "ScanOrchestrationService bean should be loaded");
		assertNotNull(bookmarkUploadController, "BookmarkUploadController bean should be loaded");
		assertNotNull(scanController, "ScanController bean should be loaded");
		assertNotNull(globalExceptionHandler, "GlobalExceptionHandler bean should be loaded");
	}

}
