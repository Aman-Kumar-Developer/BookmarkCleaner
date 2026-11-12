# Bookmark Cleaner — Project Index

## 1) Purpose and current functionality
Bookmark Cleaner is a local Spring Boot application for analyzing Chromium-based browser bookmark exports, identifying broken links, redirects, and duplicates, and exporting a cleaned bookmark tree in Chromium-compatible JSON format. The app preserves bookmark hierarchy and metadata while letting a user review and accept or reject proposed actions before export.

The current implementation covers:
- upload and parsing of Chromium bookmark JSON files
- recursive traversal of bookmark folders and nested structure
- URL validation and redirect analysis
- duplicate detection and normalized URL comparisons
- comparison-item generation with priority and proposed actions
- decision toggling and export generation
- browser-import workflow and versioned backup management
- frontend dashboard with search, filtering, and progress polling

## 2) Technology stack and versions
- Java 21
- Spring Boot 4.1.0 (parent version from `pom.xml`)
- Maven wrapper (`mvnw` / `mvnw.cmd`)
- Spring Web MVC, Jackson, and Java 21 `HttpClient`
- Frontend: static HTML/CSS/JS served from `src/main/resources/static/`
- Test stack: Spring Boot test support + JUnit 5 + Mockito

## 3) Package and directory structure
- `src/main/java/com/aman/bookmark_cleaner/`
  - `config/` — application property holders for bookmark parsing, browser discovery, and validation
  - `controller/` — upload, scan, browser, and exception-handling endpoints
  - `dto/` — request/response payload models
  - `exception/` — custom domain exceptions
  - `model/` — session, bookmark, comparison, validation, and browser domain models
  - `parser/` — bookmark JSON parser implementations
  - `security/` — SSRF protection and validation logic
  - `service/` — orchestration, validation, comparison, export, and browser integration
  - `validator/` — bookmark URL validation logic
- `src/main/resources/` — static web UI assets and application config
- `src/test/java/` — unit and integration tests by package

## 4) Important classes and responsibilities
- `BookmarkCleanerApplication` — application entry point
- `BookmarkReader` / `JsonBookmarkReader` — parse and validate bookmark JSON into `BookmarkDocument`
- `BookmarkProcessingEngine` — orchestrates validation and analysis of bookmarks in controlled concurrency
- `BookmarkValidator` / `HttpBookmarkValidator` — checks URLs, redirects, and HTTP outcomes
- `DuplicateDetectionService` — identifies exact and potential duplicate bookmarks
- `ComparisonService` — converts analysis results into decision-ready comparison items
- `DecisionService` — manages user enable/disable decisions
- `BookmarkTreeReconstructionService` — rebuilds cleaned bookmark tree without mutating original document
- `BookmarkExportService` — serializes cleaned tree to JSON and manages export download
- `ScanOrchestrationService` — session lifecycle from upload to analysis to results
- `ScanSessionManager` — thread-safe management of active scan sessions
- `BrowserDiscoveryService` / `BrowserImportService` / `BrowserBookmarkInstallationService` — browser profile discovery and import/export flows
- `SsrfProtectionService` — blocks unsafe network destinations and redirect targets
- `GlobalExceptionHandler` — translates exceptions into consistent JSON error responses

## 5) Important interfaces and implementations
- `BookmarkReader` is implemented by `JsonBookmarkReader`
- `BrowserPathResolver` is implemented by `DefaultBrowserPathResolver`
- `BrowserProcessDetector` is implemented by `DefaultBrowserProcessDetector`
- The app also uses a set of concrete services for browser discovery, import, and installation rather than a repository layer, since session state is kept in memory

## 6) Application execution flow
1. A user uploads a Chromium bookmark JSON file via `/api/bookmarks/upload`.
2. `BookmarkUploadController` delegates to `ScanOrchestrationService.uploadBookmarkFile`.
3. `JsonBookmarkReader` parses the file into a `BookmarkDocument` and creates a `ScanSession`.
4. The client starts the analysis by calling `POST /api/scans` with the session `scanId`.
5. `ScanOrchestrationService.startScan` schedules the bookmark analysis in the background.
6. `BookmarkProcessingEngine` validates each bookmark URL and records outcomes.
7. `ComparisonService` builds comparison items with priority and proposed actions.
8. The client polls `/api/scans/{scanId}` and `/api/scans/{scanId}/results` to inspect status and decide what to keep or remove.
9. `DecisionService` updates per-item decisions.
10. `BookmarkExportService` generates a cleaned Chromium JSON export and makes it downloadable.

## 7) Bookmark processing workflow
- Parse JSON into `BookmarkDocument`, `BookmarkRoots`, and nested `BookmarkNode` trees.
- Keep the original parsed document immutable while reconstructing a separate cleaned tree.
- Process normalized URLs, detect redirects, and classify outcomes as `WORKING`, `REDIRECTED`, `BROKEN`, or `UNKNOWN`.
- Detect duplicates based on normalized domain/path comparisons and mark retain-vs-remove proposals.
- Rebuild a deep copy of the bookmark tree with accepted decisions only.
- Serialize the cleaned result with Jackson for browser import.

## 8) API endpoint inventory
Core endpoints from the controllers:
- `POST /api/bookmarks/upload` — upload and initialize a scan session
- `POST /api/scans` — start the analysis for a session
- `GET /api/scans/{scanId}` — fetch scan status and summary counts
- `GET /api/scans/{scanId}/results` — paginated comparison results with priority filtering and search
- `PATCH /api/scans/{scanId}/decisions/{itemId}` — toggle item decision
- `POST /api/scans/{scanId}/export` — generate export JSON
- `GET /api/scans/{scanId}/export` — download export JSON
- browser integration endpoints also exist for discovery, install, backup, and restore workflows

## 9) Frontend/backend communication flow
The frontend is a vanilla static dashboard served from `src/main/resources/static/`. It uses browser-side JavaScript to:
- select/upload a bookmark file
- call the upload and scan APIs
- poll status and summary data
- query filtered comparison results
- render decision toggles and priority badges
- trigger export generation and download

All user-supplied content is rendered via DOM methods rather than direct HTML injection, which is a key XSS mitigation detail in the app’s frontend implementation.

## 10) Important configuration details
- `application.properties` is under `src/main/resources/`
- Browser discovery and validation settings are driven by `config/` property classes
- The app is built to operate locally against a user’s browser profile rather than a remote service
- The project is designed around a safe local-first workflow and does not require a database

## 11) Existing testing structure
The test suite is organized under `src/test/java` by feature area:
- controller tests for upload and API behavior
- parser tests for bookmark JSON processing
- model tests for OS and domain-related logic
- security tests for SSRF and browser protection
- service tests for processing, export, and browser import logic

## 12) Known limitations and unverified areas
- The implementation is local-first and browser-profile specific; it assumes a valid local environment for browser discovery and installation workflows.
- Some advanced browser-automation paths depend on OS-specific filesystem behavior and local browser profile states.
- This index reflects the repository state as implemented and verified in the codebase; any behavior that is not directly represented by tests or source should be treated as unverified unless exercised by a targeted runtime check.
