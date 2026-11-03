# Bookmark Cleaner

> A local Spring Boot application that analyzes Chromium-based browser bookmarks (Brave, Chrome, Edge),
> identifies broken links, redirects, and duplicates, presents proposed changes in a prioritized comparison
> interface, and exports a cleaned Chromium-compatible bookmark JSON file.

---

## Current Status: Stage 4 Completed (Frontend Development: Bookmark Cleaner Dashboard)

Stage 1 (Foundation & Parsing), Stage 2 (Processing & Analysis Engine), Stage 3 (Business Logic, Decision Management, REST APIs & Bookmark Export), and Stage 4 (Frontend Development: Bookmark Cleaner Dashboard) are fully implemented and verified with **103 automated unit and integration tests**.

### Implemented Capabilities:

- **Stage 1: Recursive Chromium Bookmark Parser**
  - Robust, non-destructive parsing of Chromium bookmark JSON files (`BookmarkDocument`, `BookmarkRoots`, `BookmarkNode`).
  - Full preservation of nested hierarchies, child ordering, and metadata via Jackson `@JsonAnySetter`/`@JsonAnyGetter`.
  - Comprehensive validation of document structure, size limits, and error handling.

- **Stage 2: Processing and Analysis Engine**
  - **`BookmarkValidator` & `HttpBookmarkValidator`:** URL validation using Java 21 `HttpClient` with manual redirect inspection (301, 302, 303, 307, 308), HEAD preference with GET fallback, bounded retries, and rate-limit awareness.
  - **Outcome Classification:** Explicit structured outcomes (`WORKING`, `REDIRECTED`, `BROKEN`, `UNKNOWN`). Only confirmed 404/410 errors are categorized as broken; access restrictions (401/403), rate limits (429), timeouts, and DNS errors are safely classified as `UNKNOWN`.
  - **Redirect Classification:** Public Suffix List (PSL)-aware domain comparison (`PublicSuffixService` & `DomainComparisonService`). Distinguishes `SAME_WEBSITE` (HTTP $\rightarrow$ HTTPS, `www.` normalization, path changes) from `CROSS_DOMAIN` redirects.
  - **Duplicate Detection:** `DuplicateDetectionService` classifies exact and potential duplicates (HTTP/HTTPS, WWW variants) without flattening or mutating the bookmark tree. Designates deterministic retention (first in traversal order) and marks removal proposals.
  - **Controlled Parallel Processing:** `BookmarkProcessingEngine` executes concurrent validations bounded by global and per-host concurrency semaphores. Reuses validation results for repeated normalized URLs without duplicate network requests.
  - **SSRF Protection:** `SsrfProtectionService` defends against Server-Side Request Forgery by blocking loopback, private IPv4/IPv6 networks, link-local, carrier-grade NAT, and cloud metadata endpoints. Validates every redirect hop independently.

- **Stage 3: Business Logic, Decision Management, REST APIs & Bookmark Export**
  - **Analysis Result Transformation:** `ComparisonService` transforms Stage 2 analysis results into prioritized `ComparisonItem` models.
  - **Priority Classification:**
    - **`HIGH`:** Confirmed broken bookmarks (`BROKEN` $\rightarrow$ `REMOVE_BOOKMARK`) and cross-domain redirects (`CROSS_DOMAIN` $\rightarrow$ `UPDATE_URL`). Default: ON.
    - **`MEDIUM`:** Same-website redirects (`SAME_WEBSITE` $\rightarrow$ `UPDATE_URL`) and duplicate removal proposals (`REMOVE_DUPLICATE`). Default: ON.
    - **`LOW`:** Working bookmarks and inconclusive `UNKNOWN` results (`NO_CHANGE`). Default: OFF / Disabled.
  - **User Decision Management:** `DecisionService` manages toggle state (ON = apply proposed action, OFF = retain original bookmark). Enforces that non-actionable (`NO_CHANGE`) items cannot be toggled.
  - **Chromium Tree Reconstruction:** `BookmarkTreeReconstructionService` generates a separate deep copy of the Chromium bookmark tree, applying only the user's accepted decisions while preserving 100% of folder hierarchy, original Chromium node IDs, timestamp metadata, arbitrary vendor properties, and empty folders. The user's original parsed document remains completely immutable.
  - **JSON Export Generation & Download:** `BookmarkExportService` serializes the cleaned document with pretty-printed Jackson JSON formatting, ready for Chromium browser bookmark replacement.
  - **In-Memory Session Storage:** `ScanSessionManager` thread-safely manages active scan sessions without requiring a database.
  - **REST APIs:** Full suite of Spring Boot endpoints for uploading, scanning, querying paginated comparison results, toggling decisions, and downloading cleaned exports.
  - **Global Exception Handling:** `@RestControllerAdvice` translates domain exceptions into consistent `ErrorResponse` payloads (400, 404, 500).

- **Stage 4: Frontend Development (Bookmark Cleaner Dashboard)**
  - **Vanilla Web Stack:** Pure HTML5, modern CSS3, and vanilla ES6+ JavaScript served via Spring Boot's static resource mechanism (`src/main/resources/static/`). Zero external frontend frameworks, Node.js tooling, or CDN dependencies.
  - **Desktop-First UI Design:** Professional productivity-tool interface with custom CSS variables, refined typography, subtle borders, and consistent status indicators.
  - **Interactive Drag-and-Drop Dropzone:** Accessible file picker supporting drag-and-drop, `.json` file selection, file metadata display, and client-side validation.
  - **Real-Time Scan Progress:** Animated progress bar, status badges, processed bookmark counter, and non-overlapping polling (1.5s interval).
  - **Results Comparison Dashboard:**
    - **Summary Stat Cards:** Live counts for Total, High Priority, Medium Priority, Low Priority, Broken Links, Redirects, Duplicates, and Changes Enabled.
    - **Priority Filtering & Debounced Search:** Instant server-side priority filtering (`ALL`, `HIGH`, `MEDIUM`, `LOW`) and 300ms debounced search on bookmark names, paths, and URLs.
    - **Comparison Table:** Full-width readable table displaying priority badges, folder hierarchies, original URLs, proposed changes, action badges, and accessible decision switches.
  - **Synchronized Decision Switches:** Individual accessible toggles synchronized with `PATCH /api/scans/{scanId}/decisions/{itemId}`. Locks during in-flight requests, rolls back on error, and prevents toggling disabled/non-actionable items.
  - **Export & Download Workflow:** One-click cleaned JSON generation, live instructions for Chrome, Edge, and Brave importing, and browser file download trigger.
  - **Strict Security & XSS Defense:** All untrusted bookmark names, folder paths, and URLs are rendered safely via `textContent` and programmatic DOM creation. Links are strictly validated to allow only `http:` and `https:` schemes.
  - **Responsive Design:** Fluid layouts for Desktop, Tablet (adapted stat cards and toolbar), and Mobile (controlled horizontal table scroll and stacked layouts).

---

## REST API Specification

| Method | Endpoint | Description | Request Body / Params | Response |
| :--- | :--- | :--- | :--- | :--- |
| `GET` | `/` or `/index.html` | Serve dashboard frontend application | None | `text/html` (200) |
| `POST` | `/api/bookmarks/upload` | Upload Chromium bookmark JSON file | Multipart `file` | `UploadResponse` (200) / `ErrorResponse` (400) |
| `POST` | `/api/scans` | Start analysis for uploaded session | JSON `{"scanId": "..."}` or `?scanId=...` | `ScanStatusResponse` (200) |
| `GET` | `/api/scans/{scanId}` | Get scan status and summary | Path `scanId` | `ScanStatusResponse` (200) / (404) |
| `GET` | `/api/scans/{scanId}/results` | Query paginated comparison results | Query: `priority` (`HIGH`, `MEDIUM`, `LOW`, `ALL`), `search`, `page`, `size` | `ComparisonResultsPageResponse` (200) / (404) |
| `PATCH` | `/api/scans/{scanId}/decisions/{itemId}` | Toggle user decision (ON/OFF) | JSON `{"enabled": true\|false}` | `DecisionUpdateResponse` (200) / (400, 404) |
| `POST` | `/api/scans/{scanId}/export` | Generate cleaned JSON export | Path `scanId` | `ExportResponse` (200) / (404) |
| `GET` | `/api/scans/{scanId}/export` | Download `bookmarks-cleaned.json` | Path `scanId` | `application/json` file attachment (200) / (404) |

---

## Technology Stack

- **Language:** Java 21 (bytecode target `--release 21`)
- **Backend Framework:** Spring Boot 4.x
- **Frontend:** Vanilla HTML5, CSS3, ES6+ JavaScript (Fetch API, zero dependencies)
- **Build Tool:** Maven (wrapper provided)
- **JSON Processing:** Jackson (`tools.jackson.core` / `tools.jackson.databind`)
- **HTTP Client:** Built-in Java 21 `java.net.http.HttpClient`
- **Testing:** JUnit 5, AssertJ, Spring Boot Test / MockMvc, JDK lightweight `HttpServer` (zero external network dependencies)

---

## Architecture & Package Structure

```text
src/main/
├── java/com/aman/bookmark_cleaner/
│   ├── BookmarkCleanerApplication.java          # Spring Boot main application
│   ├── config/
│   │   ├── BookmarkParserProperties.java        # Bookmark file size limits (50MB)
│   │   └── BookmarkValidationProperties.java    # HTTP timeouts, concurrency & SSRF settings
│   ├── controller/
│   │   ├── BookmarkUploadController.java        # POST /api/bookmarks/upload
│   │   ├── ScanController.java                  # REST endpoints for scans, results, decisions, export
│   │   └── GlobalExceptionHandler.java          # REST exception advice returning ErrorResponse
│   ├── dto/
│   │   ├── ComparisonItemDto.java               # DTO for paginated comparison items
│   │   ├── ComparisonResultsPageResponse.java   # Paginated results response with summary
│   │   ├── DecisionUpdateRequest.java           # Toggle decision request { "enabled": true/false }
│   │   ├── DecisionUpdateResponse.java          # Decision update result
│   │   ├── ErrorResponse.java                   # Standard error payload (timestamp, status, error, message, path)
│   │   ├── ExportResponse.java                  # Export generation metadata and download URL
│   │   ├── ScanStatusResponse.java              # Scan status and breakdown counts
│   │   ├── StartScanRequest.java                # Scan trigger request
│   │   ├── SummaryCountsDto.java                # Overall priority and outcome summary counts
│   │   └── UploadResponse.java                  # File upload response with scanId and metadata
│   ├── exception/
│   │   ├── BookmarkException.java               # Base application exception
│   │   ├── BookmarkReaderException.java         # I/O & file constraint errors
│   │   ├── BookmarkFileNotFoundException.java  # Missing file errors
│   │   ├── InvalidBookmarkException.java        # Malformed JSON or structure errors
│   │   ├── ProcessingException.java             # Analysis engine execution errors
│   │   ├── SecurityValidationException.java     # SSRF security violations
│   │   ├── ScanNotFoundException.java           # 404 when scan ID does not exist
│   │   ├── ComparisonItemNotFoundException.java # 404 when item ID does not exist
│   │   ├── InvalidDecisionException.java        # 400 when attempting to toggle disabled item
│   │   └── ExportException.java                 # 500 when export generation fails
│   ├── model/
│   │   ├── BookmarkDocument.java                # Top-level document model
│   │   ├── BookmarkRoots.java                   # Roots container (bookmark_bar, other, synced, custom)
│   │   ├── BookmarkNode.java                    # Recursive bookmark and folder tree node
│   │   ├── ValidationOutcome.java               # WORKING, REDIRECTED, BROKEN, UNKNOWN
│   │   ├── ValidationResult.java                # URL validation result
│   │   ├── RedirectType.java                    # NONE, SAME_WEBSITE, CROSS_DOMAIN
│   │   ├── RedirectStep.java                    # Individual redirect hop
│   │   ├── RedirectChain.java                   # Complete redirect trace
│   │   ├── DuplicateType.java                   # EXACT, POTENTIAL
│   │   ├── DuplicateBookmarkItem.java           # Bookmark reference in duplicate group
│   │   ├── DuplicateGroup.java                  # Group with retained item & removal candidates
│   │   ├── BookmarkAnalysisItem.java            # Individual bookmark analysis container
│   │   ├── BookmarkAnalysisReport.java          # Aggregated scan report
│   │   ├── Priority.java                        # HIGH, MEDIUM, LOW
│   │   ├── ProposedAction.java                  # REMOVE_BOOKMARK, UPDATE_URL, REMOVE_DUPLICATE, NO_CHANGE
│   │   ├── ComparisonItem.java                  # Comparison item linking original data, proposal & toggle
│   │   ├── ScanSession.java                     # In-memory session state model
│   │   └── ScanStatus.java                      # PENDING, RUNNING, COMPLETED, FAILED
│   ├── parser/
│   │   ├── BookmarkReader.java                  # Reader abstraction (Path-based)
│   │   └── JsonBookmarkReader.java              # Jackson 3 Chromium JSON reader
│   ├── security/
│   │   └── SsrfProtectionService.java           # SSRF protection and IP range validation
│   ├── service/
│   │   ├── PublicSuffixService.java             # Public Suffix List (eTLD+1) calculation
│   │   ├── DomainComparisonService.java         # Same-website vs cross-domain classification
│   │   ├── DuplicateDetectionService.java       # Exact and potential duplicate grouping
│   │   ├── BookmarkProcessingEngine.java        # Parallel analysis orchestration & caching
│   │   ├── ComparisonService.java               # Analysis results to comparison items transformation & priority sorting
│   │   ├── ScanSessionManager.java              # Thread-safe in-memory session manager
│   │   ├── DecisionService.java                 # User decision validation and toggle management
│   │   ├── BookmarkTreeReconstructionService.java # Non-destructive Chromium tree deep copy & decision applicator
│   │   ├── BookmarkExportService.java           # Jackson JSON export generation and download byte provider
│   │   └── ScanOrchestrationService.java        # High-level workflow orchestration (upload, scan, filter, page)
│   └── validator/
│       ├── BookmarkValidator.java               # URL validation interface abstraction
│       └── HttpBookmarkValidator.java           # Java 21 HttpClient validator implementation
└── resources/
    ├── application.properties                   # Configurable limits, timeouts & concurrency
    └── static/
        ├── index.html                           # Single-page dashboard application markup
        ├── css/
        │   ├── styles.css                       # CSS custom properties, reset, typography & layout
        │   ├── components.css                   # Buttons, dropzone, table, switches, cards, toasts
        │   └── responsive.css                   # Media queries for tablet and mobile screens
        └── js/
            ├── state.js                         # Reactive in-memory application state store
            ├── api.js                           # Centralized REST API fetch client
            ├── upload.js                        # Drag-and-drop & file selection controller
            ├── scan.js                          # Scan initiation and status polling controller
            ├── decisions.js                     # Toggle decision update & rollback controller
            ├── results.js                       # Results table rendering, filters, search & pagination
            ├── export.js                        # Export generation & file download controller
            └── app.js                           # Main application coordinator & toast notifications
```

---

## Configuration

In `src/main/resources/application.properties`:

```properties
spring.application.name=bookmark-cleaner

# Maximum permitted bookmark file size in bytes (default: 50MB)
bookmark.reader.max-file-size=52428800

# Validation Engine Timeouts
bookmark.validation.connect-timeout=5s
bookmark.validation.request-timeout=10s

# Redirects & Retries
bookmark.validation.max-redirects=10
bookmark.validation.max-retries=2
bookmark.validation.retry-backoff=500ms

# Parallel Concurrency Limits
bookmark.validation.max-concurrent-requests=20
bookmark.validation.max-concurrent-requests-per-host=4

# Custom User-Agent header
bookmark.validation.user-agent=BookmarkCleaner/1.0 (+https://github.com/aman-a25/BookmarkCleaner)

# SSRF Security
bookmark.validation.ssrf-protection-enabled=true
bookmark.validation.allow-loopback-for-testing=false
```

---

## Build and Execution Commands

### Prerequisites
- JDK 21+ installed and available on PATH.
- Git.

### Run Automated Tests (103 tests)
```bash
./mvnw clean test
# Or on Windows:
.\mvnw.cmd clean test
```

### Build Executable JAR
```bash
./mvnw clean package
# Or on Windows:
.\mvnw.cmd clean package
```

### Run Application & Open Dashboard
```bash
./mvnw spring-boot:run
# Or run the packaged JAR:
java -jar target/bookmark-cleaner-0.0.1-SNAPSHOT.jar
```
Open your browser and navigate to:
```text
http://localhost:8080/
```

---

## Upcoming Stages

- **Stage 5:** End-to-end integration, browser import verification, and comprehensive code audit.
