# Bookmark Cleaner --- Project Context and AI Development Specification

**Document purpose:** This file is the single source of truth for AI
coding agents working on Bookmark Cleaner. Read it completely before
making changes. Follow the requirements, architecture, constraints,
implementation sequence, and acceptance criteria below.

**Project status:** Greenfield or existing-project continuation. Inspect
the repository before changing anything. Preserve working code and adapt
the implementation to the agreed design rather than blindly recreating
files.

------------------------------------------------------------------------

## 1. Project overview

### 1.1 Product summary

Bookmark Cleaner is a local Java/Spring Boot application that analyzes a
user's Chromium-based browser bookmarks, identifies broken links,
redirects, and duplicates, presents proposed changes in a prioritized
comparison interface, and exports a cleaned Chromium-compatible bookmark
JSON file.

The initial browser targets are:

-   Brave
-   Google Chrome
-   Microsoft Edge

These browsers use Chromium's bookmark JSON format. The initial release
does not include Firefox support.

### 1.2 Problem statement

Users can accumulate hundreds or thousands of bookmarks over several
years. Some become unavailable, some redirect to new URLs, and some are
duplicated. Manually checking every bookmark is tedious and showing
every unchanged bookmark as an equally important result creates an
overwhelming interface.

The application must automate analysis and focus the user's attention on
bookmarks that may need action. It must retain the original bookmark
data, explain proposed changes, allow the user to accept or reject each
proposed change, and produce a separate cleaned file.

### 1.3 Product principles

1.  **User control:** Never silently discard or alter the user's
    original bookmarks.
2.  **Safe by default:** Preserve original data and generate a separate
    output file.
3.  **Prioritized review:** Put actionable changes ahead of unchanged
    bookmarks.
4.  **Transparent comparison:** Show the original state and proposed
    state.
5.  **Reliable classification:** Distinguish confirmed failures from
    temporary or inconclusive network outcomes.
6.  **Maintainable engineering:** Use clear separation of concerns
    without unnecessary enterprise complexity.
7.  **Local-first operation:** Process files locally in the application;
    no account, cloud service, or database is required.

------------------------------------------------------------------------

## 2. Scope

### 2.1 In scope

-   Select or upload a Chromium bookmark JSON file.
-   Parse the Chromium bookmark tree recursively.
-   Preserve folders, nested folders, bookmark names, URLs, ordering,
    and relevant metadata.
-   Validate bookmark URLs over HTTP/HTTPS.
-   Identify working links, redirects, confirmed broken links, and
    inconclusive outcomes.
-   Compare original and proposed bookmark states.
-   Detect exact duplicates and conservatively identify potential
    duplicates.
-   Assign priorities.
-   Display results in one comparison table/list.
-   Filter and search results.
-   Provide per-item ON/OFF decision controls.
-   Reconstruct the final bookmark tree using the user's decisions.
-   Export a separate Chromium-compatible JSON file.
-   Provide concise instructions for importing the exported file into
    Brave, Chrome, or Edge.
-   Unit tests, integration tests, and end-to-end workflow verification.

### 2.2 Explicitly out of scope for the initial release

Do not add these unless the project owner explicitly requests them:

-   Firefox bookmark database parsing.
-   Database persistence, user accounts, authentication, or cloud
    synchronization.
-   Microservices or distributed infrastructure.
-   React, Angular, Vue, or another frontend framework.
-   Automatic replacement or overwriting of the browser's live bookmark
    file.
-   Browser extensions.
-   AI/LLM-based URL classification.
-   Automatic deletion of uncertain or merely suspected duplicates.
-   Unrequested analytics, telemetry, or external data collection.

------------------------------------------------------------------------

## 3. Technology stack

Use the following stack unless the existing repository already has a
compatible, explicitly approved equivalent.

  -----------------------------------------------------------------------
  Area                    Technology              Purpose
  ----------------------- ----------------------- -----------------------
  Language                Java 21                 Backend and domain
                                                  logic

  Backend framework       Spring Boot             Application lifecycle
                                                  and REST API

  Build                   Maven                   Dependencies, build,
                                                  and tests

  JSON                    Jackson                 Chromium JSON parsing
                                                  and serialization

  HTTP                    Java 21 `HttpClient`    URL checks and redirect
                                                  inspection

  Frontend structure      HTML5                   UI

  Frontend styling        CSS3                    Professional responsive
                                                  design

  Frontend behavior       Vanilla JavaScript      API requests and
                                                  interactions

  Unit testing            JUnit 5                 Domain and component
                                                  tests

  Spring testing          Spring Boot Test /      API and integration
                          MockMvc                 tests
  -----------------------------------------------------------------------

### 3.1 Dependencies and implementation guidance

-   Use a compatible Spring Boot release for Java 21.
-   Use Jackson through Spring Boot's managed dependencies where
    possible.
-   Use Java's built-in HTTP client unless a concrete requirement
    justifies another dependency.
-   Do not add Lombok merely for convenience. Prefer straightforward,
    readable Java.
-   Do not add Spring Data JPA, database drivers, Spring Security,
    Thymeleaf, or frontend build tooling.
-   Keep dependencies minimal and explain any additional dependency
    before introducing it.
-   Serve the frontend from Spring Boot's `src/main/resources/static`
    directory.

------------------------------------------------------------------------

## 4. Architecture

Use a **modular monolith**. Keep responsibilities separate, but do not
split the application into services or create abstractions without a
reason.

### 4.1 Suggested package structure

``` text
bookmark-cleaner/
├── pom.xml
├── README.md
└── src/
    ├── main/
    │   ├── java/com/bookmarkcleaner/
    │   │   ├── BookmarkCleanerApplication.java
    │   │   ├── controller/
    │   │   ├── model/
    │   │   ├── dto/
    │   │   ├── parser/
    │   │   ├── validator/
    │   │   ├── service/
    │   │   ├── generator/
    │   │   ├── exception/
    │   │   └── config/
    │   └── resources/
    │       ├── application.properties
    │       └── static/
    │           ├── index.html
    │           ├── css/style.css
    │           └── js/app.js
    └── test/
        └── java/com/bookmarkcleaner/
```

Adapt package names if the repository already has an established base
package. Do not create empty packages or speculative classes.

### 4.2 Package responsibilities

**`model`** - Internal domain models for the Chromium bookmark document
and recursive nodes. - Bookmark node model must represent both folders
and URL bookmarks. - Preserve relevant original fields and metadata
needed to write a compatible output file. - Keep original and proposed
state distinguishable.

**`parser`** - Read and parse the selected Chromium bookmark JSON
file. - Expose a `BookmarkReader` abstraction and a Jackson-backed
implementation where useful. - Validate required structure and return
clear errors for invalid or unsupported files. - Do not perform HTTP
validation here.

**`validator`** - Expose a `BookmarkValidator` abstraction and an HTTP
implementation. - Check URLs and return structured validation
outcomes. - Do not mutate the bookmark tree or decide whether the user
accepts a change.

**`service`** - Coordinate the workflow: parse, flatten/traverse for
analysis, validate, classify, build comparison results, apply decisions,
and prepare export. - Keep controllers thin. - Do not place all
application logic in one giant service class.

**`generator`** - Expose a `BookmarkWriter` abstraction and a JSON
implementation where useful. - Reconstruct the Chromium bookmark
document and serialize it. - Preserve metadata, hierarchy, ordering, and
required structural fields.

**`controller`** - REST endpoints for file intake, scan
execution/status/results, user decisions, and export. - Validate request
input and delegate to services. - Do not contain business logic.

**`dto`** - API request and response types. - Do not expose internal
domain objects directly when a DTO is more appropriate. - Include stable
identifiers so decisions can be mapped back to original bookmark nodes.

**`exception`** - Application-specific exceptions and consistent API
error responses.

**`config`** - Only real configuration, such as HTTP client and
validation concurrency settings.

### 4.3 Interfaces and coupling

Use interfaces where they define a meaningful boundary, particularly:

-   `BookmarkReader` → JSON implementation.
-   `BookmarkValidator` → HTTP implementation.
-   `BookmarkWriter` → JSON implementation.

Consumers should depend on these abstractions where practical. Do not
create an interface for every class merely to increase the number of
interfaces. Do not build speculative plugin systems.

------------------------------------------------------------------------

## 5. Chromium bookmark data model and preservation

### 5.1 Recursive tree

A bookmark document is a tree, not a flat list. A folder can contain
folders and URL bookmarks recursively.

The internal model should represent:

-   Root/document metadata.
-   Top-level browser bookmark roots (for example, bookmark bar, other
    bookmarks, and synced/mobile roots when present).
-   Folder nodes.
-   URL bookmark nodes.
-   Child ordering.
-   Names, URLs, IDs, dates, and other relevant metadata found in the
    source file.

A simplified conceptual node may contain:

``` java
class BookmarkNode {
    String id;
    String name;
    String type; // folder or url
    String url;  // present for URL nodes
    List<BookmarkNode> children;
}
```

This is illustrative only. The actual implementation must preserve the
full relevant Chromium structure rather than reducing the source
document to these few fields.

### 5.2 Preservation rules

-   Parse recursively.
-   Preserve folder nesting and child order.
-   Preserve bookmark names and original URLs.
-   Preserve relevant metadata and root structure.
-   Do not create a flat export.
-   Do not regenerate IDs or timestamps unnecessarily.
-   Preserve unknown fields when feasible so that fields not used by the
    application are not lost during a read/write cycle.
-   Ensure the generated file is valid JSON and compatible with Chromium
    bookmark import.
-   The original input file must remain unchanged.

### 5.3 Stable item identity

Every analyzed bookmark must be associated with a stable internal
identifier for the duration of a scan. Prefer the Chromium bookmark node
ID when present and valid. If unavailable or unsuitable, generate a
scan-local identifier and retain its mapping to the original node.

Do not use a URL alone as the identity because duplicate bookmarks can
have the same URL in different folders.

------------------------------------------------------------------------

## 6. End-to-end application workflow

1.  User opens the local application.
2.  User selects a Chromium bookmark JSON file.
3.  Backend validates the file and parses its recursive structure.
4.  Backend identifies URL bookmark nodes while retaining their original
    tree positions and metadata.
5.  Backend validates URLs, subject to safe concurrency and request
    limits.
6.  Backend detects redirects, failures, and duplicates.
7.  Backend creates a comparison result for each relevant bookmark and
    assigns a priority.
8.  Frontend displays the comparison list, ordered by priority by
    default.
9.  User filters/searches and changes individual ON/OFF decisions as
    desired.
10. User requests export.
11. Backend reconstructs the original tree, applying only the accepted
    proposed changes.
12. Backend returns a downloadable JSON file.
13. User imports the file into their browser or manually replaces their
    browser bookmark file if they choose.

The application must not automatically overwrite the browser's active
bookmark file.

------------------------------------------------------------------------

## 7. URL validation requirements

### 7.1 Outcomes

Use explicit structured outcomes. At minimum, distinguish:

-   `WORKING`: URL responded successfully and did not require a
    meaningful destination change.
-   `REDIRECTED`: URL redirected to a final destination.
-   `BROKEN`: Strong evidence that the URL or page is unavailable.
-   `UNKNOWN`: The application cannot confidently determine the outcome.

Do not treat every non-2xx response as a permanently broken bookmark.

### 7.2 HTTP status and network handling

Consider these examples:

  -----------------------------------------------------------------------
  Result                              Suggested handling
  ----------------------------------- -----------------------------------
  2xx                                 Working

  3xx                                 Follow/inspect redirect and record
                                      chain/final URL

  404 / 410                           Candidate confirmed broken page

  401 / 403                           Restricted or forbidden; generally
                                      UNKNOWN, not automatically broken

  429                                 Rate limited; UNKNOWN and
                                      optionally retry later

  5xx                                 Server-side failure; UNKNOWN unless
                                      a carefully bounded retry
                                      establishes a result

  DNS failure / connection refused    Failure evidence; classify
                                      carefully and avoid overclaiming
                                      permanent deletion

  Timeout                             UNKNOWN

  TLS/certificate error               UNKNOWN or validation error, not
                                      automatically a deleted page
  -----------------------------------------------------------------------

The implementation may refine these rules, but must preserve the
principle: temporary, access-controlled, or inconclusive outcomes must
not be presented as confirmed broken links.

### 7.3 HTTP behavior

-   Set connection and request timeouts.
-   Use a reasonable, configurable user-agent.
-   Follow and record redirects in a controlled way.
-   Limit redirect hops to prevent loops.
-   Detect redirect loops.
-   Handle HTTPS/TLS failures safely.
-   Avoid downloading large response bodies. Prefer lightweight requests
    where reliable.
-   Some sites reject `HEAD`; implement a controlled fallback to `GET`
    when needed, without downloading unnecessary content.
-   Avoid repeatedly requesting the same normalized URL during one scan.
-   Do not retry aggressively.
-   Respect rate limiting and use bounded retries/backoff where
    appropriate.
-   Do not execute page scripts or download page assets.

### 7.4 Concurrency

Parallel URL validation is required for practical performance with
hundreds or thousands of bookmarks.

-   Use a bounded executor or equivalent controlled concurrency
    mechanism.
-   Make maximum concurrency configurable.
-   Do not create one unbounded thread per bookmark.
-   Use per-request timeouts.
-   Avoid overwhelming any one host; consider per-host limits or a small
    delay/limiter.
-   Keep results associated with their original bookmark IDs.
-   Report progress accurately.
-   Handle cancellation or application errors without corrupting
    original data.
-   Avoid duplicate network requests for identical normalized URLs
    within a scan.
-   Test concurrency and failure handling.

------------------------------------------------------------------------

## 8. Redirect and same-website detection

A redirected bookmark is not necessarily a bad bookmark. The application
must compare the original URL with the final destination.

### 8.1 Same-website rules

Apply these rules:

1.  Compare normalized hostnames.
2.  Treat HTTP to HTTPS as the same website.
3.  Treat `www.example.com` and `example.com` as the same website.
4.  Treat a changed path on the same domain as a redirect within the
    same website.
5.  Treat a different domain as a potentially significant change
    requiring higher attention.

Additional implementation safeguards:

-   Normalize hostname case and internationalized hostnames
    consistently.
-   Normalize default ports.
-   Ignore URL fragments for host/site comparison where appropriate.
-   Do not assume unrelated subdomains are equivalent without a
    deliberate rule.
-   Do not assume two domains are the same organization merely because
    their names look similar.
-   Be careful with public suffixes: `example.co.uk` and `another.co.uk`
    are different registrable domains.
-   Record the full original URL, final URL, and redirect chain where
    available.

### 8.2 Priority for redirects

-   Same-site redirect: **Medium priority**.
-   Redirect to a different domain/website: **High priority**, because
    the destination may be materially different and deserves user
    attention.
-   No redirect/no meaningful change: **Low priority**.

A cross-domain redirect is not automatically malicious or broken. It is
a high-priority review item, not an automatic deletion.

------------------------------------------------------------------------

## 9. Duplicate detection

Duplicate detection is part of the same comparison/results interface. Do
not create a separate mandatory workflow.

### 9.1 Required behavior

-   Detect exact duplicate URLs.
-   Detect potential duplicates using conservative URL normalization.
-   Retain each bookmark's identity, name, folder, and original
    location.
-   Do not silently delete any duplicate.
-   Create a proposed action for the duplicate candidate.
-   Allow the user to accept or reject the proposed removal using the
    same ON/OFF decision mechanism.
-   Preserve the original bookmark if the user turns the action OFF.

### 9.2 Duplicate safety

-   Exact duplicate URL matches can be identified confidently, but
    removal is still a user-approved action.
-   Potential duplicates must be clearly distinguished from exact
    duplicates.
-   Do not treat different paths as duplicates merely because they share
    a domain.
-   Do not automatically merge folders or bookmark names.
-   Do not remove both items in a duplicate group.
-   Select a deterministic item to retain for the proposed action, but
    make the retained and proposed-to-remove items clear in the UI.
-   Avoid false positives from query parameters or URL variants that may
    change page meaning.
-   Do not normalize away meaningful path, query, or case differences
    without a well-defined rule.

### 9.3 Duplicate priority and default

-   Duplicate-removal proposals: **Medium priority**.
-   Proposed duplicate removal is ON by default.
-   User can turn it OFF to retain the original bookmark.
-   If duplicate confidence is uncertain, explain that it is a potential
    duplicate and do not make an aggressive automatic removal proposal.

------------------------------------------------------------------------

## 10. Priority categorization and comparison interface

This is a core product requirement. The user may have hundreds or
thousands of bookmarks. Do not force the user to inspect every unchanged
bookmark.

### 10.1 Three priority categories

Use these exact labels:

  -----------------------------------------------------------------------
  Priority                Meaning                 Examples
  ----------------------- ----------------------- -----------------------
  **High**                Requires the most       Confirmed broken links;
                          attention               redirects to a
                                                  different
                                                  domain/website

  **Medium**              A proposed cleanup or   Same-website redirects;
                          same-site URL change    exact or potential
                                                  duplicate-removal
                                                  proposals

  **Low**                 No change is needed     Working bookmarks with
                                                  no meaningful URL or
                                                  destination change
  -----------------------------------------------------------------------

Low-priority items may be shown in the same table when the user selects
the Low filter or All, but must not distract from actionable results.

### 10.2 Default ordering and filters

-   Default view: all results, ordered High → Medium → Low.
-   Within a priority, use a stable and understandable ordering.
-   Provide filters for All, High, Medium, and Low.
-   Display counts for each category.
-   Provide search by bookmark name and URL.
-   Consider folder/path filtering if practical.
-   Support pagination or virtualization so thousands of rows do not
    make the browser interface unusable.
-   Preserve user decisions when filters, search, sorting, or pages
    change.

### 10.3 Comparison columns

The comparison table should communicate:

-   Bookmark name.
-   Folder/path.
-   Priority.
-   Original value/URL.
-   Proposed/current value/URL.
-   Proposed action or change description.
-   Apply-change toggle.

Examples:

  ----------------------------------------------------------------------------------------------
  Bookmark       Priority       Before                    Proposed result         Apply
  -------------- -------------- ------------------------- ----------------------- --------------
  Old tutorial   High           `https://site.test/old`   Remove bookmark         ON

  Java docs      Medium         `http://docs.test/a`      `https://docs.test/a`   ON

  Duplicate link Medium         Same URL as retained item Remove this duplicate   ON

  GitHub         Low            `https://github.com`      No change               Disabled
  ----------------------------------------------------------------------------------------------

Use truncated URL display with a way to inspect/copy the full value.
Make before/after differences easy to understand.

### 10.4 Toggle semantics

The toggle represents whether the proposed change should be applied
during export.

-   **ON:** Apply the proposed action.
-   **OFF:** Retain the original bookmark state.
-   **Disabled:** No proposed change exists.

Default states:

-   High-priority proposed changes: ON by default.
-   Medium-priority proposed changes: ON by default.
-   Low-priority unchanged bookmarks: disabled; no interaction possible.

For a broken bookmark: - ON = remove the bookmark from the generated
output. - OFF = retain the original bookmark.

For a same-site redirect: - ON = replace the original URL with the final
destination. - OFF = retain the original URL.

For a cross-domain redirect: - ON = apply the proposed replacement only
if the validation logic has a safe, explicit replacement proposal.
Because this is high priority, show a clear warning and original/final
destination comparison. - OFF = retain the original URL.

For a duplicate: - ON = remove the specifically identified duplicate
item, retaining the designated original. - OFF = retain both original
bookmark entries.

For unchanged items: - No toggle interaction. Render a visually disabled
control/state (for example, a disabled switch or lock/ban indicator) and
show a disabled cursor on hover. Do not rely on cursor styling alone;
the control must actually be disabled.

### 10.5 Backend is authoritative

The frontend toggle is not the source of truth.

-   Send user decisions to the backend or submit them as part of a
    validated export request.
-   Backend must validate item IDs and allowed actions.
-   Export must use the backend's original parsed model plus the
    accepted decisions.
-   Never trust client-supplied original bookmark JSON as the canonical
    source.
-   Turning a toggle OFF must restore/retain the original value; it must
    not attempt to reverse a destructive mutation.
-   Keep original and proposed values separate and immutable during the
    review session.

------------------------------------------------------------------------

## 11. File selection, scan lifecycle, and session handling

### 11.1 File selection

-   Allow the user to select a bookmark JSON file through a standard
    file picker.
-   Validate file extension only as a preliminary hint; validate the
    actual JSON structure and content.
-   Provide clear feedback for invalid JSON, unsupported structure,
    empty files, and files with no URL bookmarks.
-   Do not assume a browser-specific file path.
-   Do not silently access or modify browser profile files.
-   Set a reasonable configurable upload/file-size limit and report it
    clearly.

### 11.2 Scan lifecycle

The application should support understandable states such as:

-   Ready for file selection.
-   File loaded.
-   Scanning.
-   Scan completed.
-   Scan failed.
-   Exporting.
-   Export ready.

During scanning, display: - Total bookmark count. - Processed count. -
Remaining count. - Progress indicator. - Useful status text.

Avoid freezing the UI while scanning. Use asynchronous/background
processing and progress/status polling or an equivalent reliable
approach.

### 11.3 Session data

No database is required. Keep scan state in memory for the active
application session, with clear lifecycle management.

-   Do not retain uploaded files unnecessarily.
-   Do not leave temporary files indefinitely.
-   Clean up session data when appropriate.
-   If the application restarts, it is acceptable for the user to start
    a new scan.
-   Do not claim that a scan can be resumed after restart unless that
    functionality is actually implemented.

------------------------------------------------------------------------

## 12. API design

Define a consistent REST API. The following is a suggested contract;
adapt endpoint names if the existing code has a sound convention, but
preserve the capabilities and semantics.

Possible endpoints:

  ------------------------------------------------------------------------------------------
  Method                  Endpoint                                   Purpose
  ----------------------- ------------------------------------------ -----------------------
  `POST`                  `/api/bookmarks/upload`                    Upload/select and parse
                                                                     the bookmark file

  `POST`                  `/api/scans`                               Start validation for
                                                                     the loaded file

  `GET`                   `/api/scans/{scanId}`                      Retrieve scan status
                                                                     and progress

  `GET`                   `/api/scans/{scanId}/results`              Retrieve comparison
                                                                     results, with
                                                                     filtering/pagination

  `PATCH`                 `/api/scans/{scanId}/decisions/{itemId}`   Update one
                                                                     proposed-change
                                                                     decision

  `POST`                  `/api/scans/{scanId}/export`               Generate the cleaned
                                                                     JSON file

  `GET`                   `/api/scans/{scanId}/export`               Download generated
                                                                     output, if using a
                                                                     separate
                                                                     generation/download
                                                                     step
  ------------------------------------------------------------------------------------------

### 12.1 API requirements

-   Use DTOs for request and response payloads.
-   Return stable IDs for bookmarks and proposed changes.
-   Include priority, original state, proposed state, action
    description, toggle state, and whether the control is enabled.
-   Include counts by priority and scan progress.
-   Return consistent error response shapes.
-   Validate all IDs and user decisions.
-   Do not return internal stack traces to the frontend.
-   Handle missing/expired scan IDs clearly.
-   Support pagination or equivalent scalable result retrieval.
-   Document the actual API in the README.

The exact endpoint contract must be documented before frontend
integration. Do not let frontend and backend agents independently invent
incompatible payloads.

------------------------------------------------------------------------

## 13. Export and browser implementation

### 13.1 Export behavior

-   Generate a new JSON file, for example `bookmarks-cleaned.json`.
-   Never overwrite the uploaded source file.
-   Reconstruct the original Chromium tree.
-   Apply only ON decisions.
-   Retain original nodes for OFF decisions.
-   Retain unchanged nodes.
-   Remove only bookmarks whose removal action is ON.
-   Preserve folders and their order.
-   Handle folders that become empty according to a documented,
    conservative rule; do not remove unrelated empty folders without a
    requirement.
-   Ensure valid JSON and Chromium-compatible structure.
-   Use a sensible download filename.

### 13.2 User implementation

The application provides a downloadable file. The user chooses how to
apply it:

1.  Import the generated JSON through the browser's bookmark manager, or
2.  Manually replace the browser's bookmark file, if the user
    understands the risks and has closed the browser/backed up the
    original.

The application must not perform automatic replacement.

Provide brief browser-specific import guidance for Brave, Chrome, and
Edge. Browser UI wording can vary by version; avoid hardcoding brittle
instructions as if they never change.

------------------------------------------------------------------------

## 14. Frontend design specification

The coding agent has creative freedom over the visual design and layout,
provided the requirements below are met.

### 14.1 Design direction

Build a professional, polished desktop-first dashboard. It should look
like a credible finished utility, not a bare demo, but must not be
overly decorative.

Suggested direction:

-   Light theme by default.
-   Neutral background, white or near-white surfaces.
-   Dark readable typography.
-   Restrained accent colors.
-   High priority uses a clear but not overwhelming danger color.
-   Medium uses a restrained warning/amber treatment.
-   Low uses a calm neutral/success treatment.
-   Consistent spacing, alignment, typography, borders, and subtle
    shadows.
-   Clear hierarchy and professional icons.
-   Responsive layout for smaller screens.
-   Accessible contrast and keyboard focus states.

The agent may choose exact palette, typography, component arrangement,
and responsive behavior.

Avoid: - Excessive gradients. - Neon colors. - Excessive animation. -
Giant decorative cards. - Cluttered tables. - Tiny low-contrast text. -
Unnecessary charts or widgets. - Decorative features that reduce the
space available for bookmark review.

### 14.2 Main interface sections

1.  **Dashboard/header**
    -   Product name and concise description.
    -   Current scan/file context.
    -   Main action to select a bookmark file.
2.  **Overview**
    -   Total bookmarks.
    -   High, Medium, and Low counts.
    -   Scanning progress/status when relevant.
3.  **Results**
    -   Comparison table/list.
    -   Default High → Medium → Low order.
    -   Priority filters and search.
    -   Before/after information.
    -   Toggle controls.
    -   Pagination or virtualization for large collections.
4.  **Export summary**
    -   Number of changes to be applied.
    -   Number of bookmarks retained.
    -   Number of bookmarks proposed for removal.
    -   Export/download action.
5.  **Help/instructions**
    -   Concise browser import instructions.
    -   Explanation of priority labels and toggle behavior.

### 14.3 Interaction quality

-   Do not lose decisions when filtering or changing pages.
-   Confirm or clearly communicate destructive-looking actions such as
    removing a bookmark from the generated output.
-   Show loading, success, warning, empty, and error states.
-   Disable controls when actions are unavailable.
-   Avoid fake controls or buttons that do nothing.
-   Ensure the frontend works with the actual API, not hardcoded sample
    data.
-   Any sample/demo data must be clearly separated from production scan
    results.

------------------------------------------------------------------------

## 15. Security, privacy, and safety

Although this is a local utility, uploaded files and URL requests
require care.

-   Treat uploaded bookmark JSON as untrusted input.
-   Validate file structure and size.
-   Do not execute or interpret bookmark content as code.
-   Do not render bookmark names or URLs as unsanitized HTML.
-   Prevent path traversal and arbitrary server-side file access.
-   Store temporary files only where necessary and clean them up.
-   Do not log full bookmark contents or sensitive URLs unnecessarily.
-   Do not send bookmark data to external AI services or analytics
    services.
-   Validate URL schemes. HTTP and HTTPS are the expected schemes.
-   Reject or safely handle `file:`, `javascript:`, `data:`, localhost,
    private-network, and other non-public targets as appropriate.
-   Guard against SSRF: the URL validator must not become a tool for
    accessing localhost, internal services, cloud metadata endpoints, or
    private network resources.
-   Validate resolved IP addresses and redirect destinations, not just
    the initial URL.
-   Re-check every redirect hop against the network safety policy.
-   Do not follow redirects into prohibited local/private destinations.
-   Apply request timeouts and response-size limits.
-   Do not expose stack traces, filesystem paths, or internal
    configuration to the browser.

If a safe validation policy prevents checking a URL, return an explicit
UNKNOWN/blocked result rather than bypassing the restriction.

------------------------------------------------------------------------

## 16. Error handling and reliability

Handle at least:

-   Invalid JSON.
-   Valid JSON with unsupported bookmark structure.
-   Empty bookmark file.
-   No URL bookmarks.
-   Missing or invalid bookmark fields.
-   Duplicate or missing node IDs.
-   Invalid URL syntax.
-   Unsupported URL schemes.
-   DNS and connection errors.
-   Timeouts.
-   TLS errors.
-   HTTP 4xx and 5xx responses.
-   Rate limiting.
-   Redirect loops and excessive redirect hops.
-   Unsafe redirect targets.
-   Cancellation or interrupted scan.
-   Export/serialization errors.
-   Expired or missing scan session.
-   Invalid decision ID or action.
-   Browser download/API errors.

Error messages should be understandable to users. Technical detail can
be logged safely for debugging, but do not expose internal stack traces
in the UI.

------------------------------------------------------------------------

## 17. Testing requirements

Tests are part of implementation, not an optional final activity.

### 17.1 Parser tests

Test: - Valid Chromium bookmark JSON. - Nested folders. - Multiple
bookmark roots. - Empty folders. - Bookmarks with metadata. - Missing
optional fields. - Invalid JSON. - Unsupported structure. - Preservation
of child ordering and metadata.

### 17.2 Validator tests

Use a local mock HTTP server or controlled test doubles. Do not make
normal unit tests depend on real public websites.

Test: - 200 response. - 3xx redirect. - Same-host path redirect. -
HTTP-to-HTTPS destination comparison. - `www` versus non-`www`. -
Cross-domain redirect. - 404 and 410. - 403 and 401. - 429. - 5xx. -
Timeout. - DNS/connection failure. - Redirect loop. - HEAD rejection and
GET fallback. - Unsafe/private destination rejection. - Retry limits and
concurrency limits.

### 17.3 Duplicate tests

Test: - Exact duplicates. - Same URL in different folders. - Same URL
with different bookmark names. - HTTP versus HTTPS variants. - `www`
versus non-`www`. - Different paths that must not be treated as
duplicates. - Query-string variants that must not be aggressively
merged. - Potential duplicates are labeled distinctly. - OFF retains all
original entries. - ON removes only the designated duplicate.

### 17.4 Priority and decision tests

Test: - Confirmed broken → High. - Cross-domain redirect → High. -
Same-site redirect → Medium. - Duplicate proposal → Medium. - Unchanged
working bookmark → Low. - High and Medium default ON. - Low unchanged
item has disabled control. - OFF retains original. - ON applies proposed
action. - User decisions survive filtering/pagination. - Invalid or
tampered decisions are rejected.

### 17.5 Generator tests

Test: - Correct Chromium JSON output. - Original hierarchy preserved. -
Metadata preserved. - Accepted URL replacements applied. - Accepted
removals applied. - Rejected proposals retain original bookmarks. -
Unchanged bookmarks retained. - Empty folders handled according to the
documented rule. - Original input file is not modified. - Output can be
parsed again.

### 17.6 Integration/end-to-end tests

Verify the complete flow:

1.  Upload a representative bookmark file.
2.  Parse it.
3.  Run validation using controlled test endpoints.
4.  Retrieve categorized comparison results.
5.  Change several decisions.
6.  Export the cleaned JSON.
7.  Parse the export and verify the expected tree and values.

Finally, manually verify that a representative exported file can be
imported into a Chromium browser. Do not claim this manual check has
passed unless it has actually been performed.

------------------------------------------------------------------------

## 18. Five-stage AI agent implementation plan

Implement the project in five controlled stages. Each stage must leave
the repository in a buildable, understandable state.

### Stage 1 --- Project foundation and bookmark parsing

**Tasks** - Inspect repository and existing code. - Confirm
Java/Maven/Spring Boot setup. - Configure minimal dependencies. - Create
or refine package structure. - Implement Chromium bookmark document/node
models. - Implement `BookmarkReader` and Jackson-backed reader. -
Preserve nested structure and relevant metadata. - Add parser unit
tests. - Add initial application configuration and README notes.

**Acceptance criteria** - `mvn test` passes. - Application starts. -
Valid fixture files parse recursively. - Invalid/unsupported files
produce useful errors. - Round-trip model handling does not
unnecessarily discard metadata.

### Stage 2 --- Validation engine, redirects, duplicates, and concurrency

**Tasks** - Implement `BookmarkValidator` and HTTP implementation. -
Implement URL normalization and same-website comparison. - Implement
redirect chain/final URL tracking. - Implement robust outcome
classification. - Implement bounded parallel validation and progress
reporting. - Implement exact and conservative potential duplicate
detection. - Add validator, normalization, duplicate, and concurrency
tests.

**Acceptance criteria** - Validation is not dependent on public websites
in unit tests. - Timeouts and rate limits are handled. - Redirect loops
and unsafe destinations are blocked. - Same-site and cross-domain
redirects are distinguished correctly. - Duplicate detection does not
aggressively merge different pages. - Concurrency is bounded.

### Stage 3 --- Business logic, comparison results, decisions, and APIs

**Tasks** - Implement scan/session workflow. - Flatten/traverse
bookmarks for analysis while retaining original tree references. - Build
comparison result models and DTOs. - Assign High/Medium/Low
priorities. - Implement default decisions and disabled Low controls. -
Implement backend decision storage and validation. - Implement
reconstruction and JSON writer. - Implement REST endpoints and
consistent error responses. - Add API, decision, and generator tests. -
Document API contracts.

**Acceptance criteria** - All relevant bookmarks have stable
identities. - Comparison results contain original and proposed states. -
Results are ordered High → Medium → Low by default. - High and Medium
changes default ON. - Unchanged Low items are disabled. - OFF retains
original state. - ON applies the proposal. - Export reconstructs the
tree correctly and leaves input untouched. - API contract is stable and
documented.

### Stage 4 --- Frontend implementation

**Tasks** - Design and implement the professional dashboard. - Implement
file selection and upload. - Implement scan progress and status. -
Implement category summary/counts. - Implement comparison table. -
Implement priority filters and search. - Implement before/after
display. - Implement ON/OFF and disabled toggle states. - Implement
pagination/virtualization where needed. - Implement export summary and
download. - Add browser import guidance. - Connect all controls to real
backend endpoints.

**Acceptance criteria** - No hardcoded fake results in the real
workflow. - UI decisions persist across filtering and pagination. - All
controls work. - Large result sets remain usable. - Error, loading,
empty, and success states are present. - Responsive layout and
accessibility basics are addressed.

### Stage 5 --- Integration, verification, fixes, and documentation

**Tasks** - Run all unit and integration tests. - Fix integration
mismatches. - Test the complete workflow with representative fixtures. -
Verify output JSON structure and metadata. - Verify original input
remains unchanged. - Perform a manual browser import check if the
environment permits. - Review security controls, especially SSRF and
file handling. - Remove dead code, placeholders, TODOs, unused
dependencies, and sample data from production paths. - Complete README
with build/run instructions, usage, limitations, and import guidance.

**Acceptance criteria** - Clean Maven build and test suite. -
Application starts using documented commands. - End-to-end workflow
works. - No known critical data-loss or unsafe-network issue. - Export
is valid and browser-compatible. - README is sufficient for another
developer to run the application.

------------------------------------------------------------------------

## 19. Strict instructions for every AI coding agent

These rules apply to every implementation stage.

### 19.1 Before coding

1.  Read this entire `context.md`.
2.  Inspect the current repository, including source files, tests, build
    configuration, and README.
3.  Identify existing functionality and preserve it.
4.  Identify which stage is being requested.
5.  Check whether required work from previous stages is complete.
6.  Report conflicts, missing prerequisites, or ambiguous requirements
    before making risky changes.
7.  Do not assume a greenfield project if code already exists.

### 19.2 While coding

1.  Follow the agreed technology stack.
2.  Respect package responsibilities and API contracts.
3.  Do not introduce unrequested features.
4.  Do not replace the architecture with a different one.
5.  Do not add unnecessary dependencies, interfaces, layers, or design
    patterns.
6.  Keep methods and classes focused and understandable.
7.  Use clear names and meaningful comments where needed.
8.  Handle exceptions explicitly.
9.  Never silently discard bookmark data.
10. Never mutate the original bookmark file.
11. Keep original and proposed bookmark states separate.
12. Keep user decisions on the backend as the authoritative state.
13. Do not mark uncertain network outcomes as confirmed broken.
14. Do not automatically remove uncertain duplicates.
15. Do not bypass SSRF or file-access protections to make a test pass.
16. Do not hardcode sample data into the real application workflow.
17. Do not leave placeholder implementations, empty methods, or TODOs in
    functionality claimed as complete.
18. Do not claim tests passed unless they were executed and passed.

### 19.3 After each stage

Provide a concise implementation report containing:

-   Files created.
-   Files modified.
-   Major design decisions.
-   Functionality completed.
-   Tests executed and their actual results.
-   Known limitations or unresolved issues.
-   Exact command to run the application or tests.
-   Whether the stage acceptance criteria are satisfied.

Do not start a later stage if the current stage has unresolved failures
that would undermine it. If a failure is environmental, explain it
precisely rather than pretending it passed.

### 19.4 Change control

If an implementation detail appears to conflict with this specification:

-   Do not silently change the requirement.
-   Explain the conflict.
-   Recommend the smallest safe adjustment.
-   Wait for project-owner approval when the change affects product
    behavior, architecture, data preservation, or scope.

------------------------------------------------------------------------

## 20. Definition of Done

The project is complete only when all of the following are true:

-   [ ] Application builds and starts successfully.
-   [ ] Chromium bookmark JSON can be selected and parsed.
-   [ ] Nested folders, roots, ordering, and relevant metadata are
    preserved.
-   [ ] Hundreds or thousands of bookmarks can be processed without
    unbounded resource usage.
-   [ ] URL validation runs with bounded concurrency and safe timeouts.
-   [ ] Working, redirected, broken, and unknown outcomes are
    distinguished.
-   [ ] Same-site and cross-domain redirects are classified correctly.
-   [ ] Exact and potential duplicates are detected conservatively.
-   [ ] Comparison results show original and proposed states.
-   [ ] High, Medium, and Low priorities are assigned correctly.
-   [ ] Default result ordering is High → Medium → Low.
-   [ ] Priority filters and search work.
-   [ ] High and Medium proposed changes default ON.
-   [ ] Unchanged Low items have disabled toggles.
-   [ ] OFF retains the original bookmark.
-   [ ] ON applies the proposed change.
-   [ ] Duplicate removal affects only the designated duplicate.
-   [ ] Decisions survive filtering and pagination.
-   [ ] Export reconstructs the Chromium tree correctly.
-   [ ] Original input file remains untouched.
-   [ ] Exported JSON is valid and can be imported into a Chromium
    browser.
-   [ ] Frontend and backend are fully integrated.
-   [ ] Unit and integration tests pass.
-   [ ] Security protections for file handling and URL validation are
    implemented.
-   [ ] README explains setup, usage, limitations, and browser import.

------------------------------------------------------------------------

## 21. Final product definition

Bookmark Cleaner is not merely a URL checker.

It is a local bookmark maintenance utility that:

1.  Reads a user's existing Chromium bookmark collection.
2.  Analyzes links, redirects, and duplicates.
3.  Prioritizes proposed changes so users do not need to inspect every
    bookmark.
4.  Shows a clear before/after comparison.
5.  Lets users accept or reject each proposed change.
6.  Produces a separate, clean, browser-compatible bookmark JSON file.

**The most important invariant is that the user's original bookmarks
remain safe and recoverable throughout the process.**

Build the smallest reliable application that fulfills this
specification. Prioritize correctness, data preservation, usability, and
maintainability over unnecessary complexity.
