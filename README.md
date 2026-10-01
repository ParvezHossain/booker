# Booker Android Application

Modern Android reading application built with Kotlin, Jetpack Compose, Coroutines/Flow, Retrofit, OkHttp, and platform `PdfRenderer`. Integrates with the Booker Spring Boot backend API to manage library catalogues, workspace accounts, JWT session lifecycles, live Server-Sent Events (SSE), streamed PDF uploads/replacements, resumable private offline caching, and debounced CAS reading progress synchronization.

---

## 1. SDK & Environment Requirements

- **Kotlin Version**: `2.0.21`
- **Gradle Version**: `8.13`
- **Android Gradle Plugin (AGP)**: `8.9.1`
- **Minimum SDK**: `API 24` (Android 7.0)
- **Compile / Target SDK**: `API 37`
- **Java Compatibility**: `Java 11 / 17`

---

## 2. Server Base URL & Network Configuration

### Debug Environments
- **Android Emulator**: `http://10.0.2.2:8080/api/` (Default)
- **Physical Device**: `http://192.168.0.122:8080/api/` (Configurable via `RetrofitClient.baseUrl`)

### Release Configuration
Release builds enforce HTTPS via `app/src/main/res/xml/network_security_config.xml`. Cleartext HTTP is strictly restricted to local emulator and development origins (`10.0.2.2`, `192.168.0.122`, `localhost`).

---

## 3. Architecture & Key Features

### A. Authentication & Session Lifecycle (`SessionCoordinator`)
- **Signup**: `POST /api/auth/signup` registers a workspace and owner account. Auto-logs in on success, preserving account-created state if immediate login fails.
- **Login**: `POST /api/auth/login` returns access and refresh JWT tokens and initializes `SessionState`.
- **Single-Flight Token Refresh**: When concurrent requests receive `401 Unauthorized`, `SessionCoordinator` uses a `Mutex` to serialize a single call to `POST /api/auth/refresh`. Concurrent requests await and reuse the rotated `accessToken`.
- **Token Rotation**: Rotates both access and refresh tokens atomically upon refresh. Transient 503/network errors are rethrown for retry, while 400/401 token invalidation triggers clean local logout.
- **Logout**: `POST /api/auth/logout` invalidates the refresh token on the server, clears local credentials, and increments `sessionGeneration` to block late async callbacks.

### B. Catalogue, Workspace & Live SSE (`BookSseManager`)
- **Workspace**: `GET /api/workspace` fetches book limit and books used metrics (`remainingCapacity = book_limit - books_used`).
- **Catalogue**: `GET /api/books` and `GET /api/books/isbn/{isbn}` fetch catalogue items. `POST /api/books` handles creation with quota (`403`) and duplicate ISBN (`409`) error routing.
- **Live SSE Events**: `GET /api/books/events` with `Last-Event-ID` cursor streaming. Handles `ready` `{}` heartbeat events, `book.created` announcements, deduplication, and resync on bad cursor (`400`) or 401 token expiration.

### C. Durable Local Reading Store & Account Isolation (`LocalReadingStore`)
- **Multi-Tenant Key Hierarchy**: All progress and caches are strictly scoped by:
  `${envUrl.hashCode()}_${normalizedEmail.hashCode()}_${workspaceId}_${bookId}_${documentId}`
- **Durability**: Uses `SharedPreferences` with synchronous `commit()` for atomic disk persistence, backed by `ConcurrentHashMap` for unit testing.
- **Account Isolation**: Offline library and progress state are locked to verified account sessions. Switch account or logout clears active session access. Sensitive passwords and raw PDF bytes are never stored in the progress store.
- **Backup Exclusion**: Excludes `booker_local_reading_store.xml`, `booker_sse_cursors.xml`, and `pdf_cache` from Android auto-backup via `backup_rules.xml`.

### D. PDF Upload & Replacement (`DocumentRepository`)
- **Selection**: Uses Compose Storage Access Framework (`ACTION_OPEN_DOCUMENT`). Queries `OpenableColumns.DISPLAY_NAME` and `SIZE` via `ContentResolver` without inferring filesystem paths.
- **App-Private Snapshot**: Stages content stream into `cacheDir/upload_snapshots/<uploadId>.pdf` to ensure byte reproducibility across network retries.
- **Streamed Bounded Transfer**: `UploadStreamRequestBody` streams file in 16 KB bounded chunks with `Long` counters.
- **Idempotency**: Generates UUID `Idempotency-Key` headers for `POST /api/books/{bookId}/document`. Retries reuse the same key; new file selections generate a new key.
- **Active Verification**: Re-fetches `GET /api/books/{bookId}/document` after 201 Created to verify active document status.
- **Semantics Notice**: Replaces document, resets personal reading progress, and consumes workspace storage quota.

### E. Resumable Private PDF Cache (`PdfCacheRepository`)
- **Storage**: Private directory `noBackupFilesDir/pdf_cache/` (or `cacheDir/pdf_cache/`).
- **HTTP Range Resumption**: Requests `Range: bytes=<partialLength>-` for incomplete `.part` files via `@Streaming GET /api/books/{bookId}/document/content`. Handles `206 Partial Content` (appends bytes), `200 OK` (truncates/restarts), and `416 Range Not Satisfiable`.
- **SHA-256 Integrity Verification**: Calculates SHA-256 checksum and compares length before atomic `.part` -> `.pdf` promotion.
- **Open Document Protection**: Tracks open document IDs in reader to defer eviction during active reading sessions.

### F. Compose PDF Reader & Saved-Page Restoration (`PdfRendererAdapter`, `ReaderViewModel`)
- **Native Renderer**: Platform `android.graphics.pdf.PdfRenderer` backed by seekable `ParcelFileDescriptor`. Converts 1-based API pages to 0-based renderer indices.
- **Off-Main-Thread Bitmap Rendering**: Renders bitmaps off main thread synchronized with a `Mutex`. Caps bitmap dimensions to 2048px during zoom to prevent OOM.
- **Restoration Priority**:
  1. Saved local resume page from `LocalReadingStore`.
  2. Server `acknowledgedProgress.resumePage`.
  3. Default page 1 for first-open.
- Does NOT reset to page 1 on network failure.

### G. Debounced Progress Outbox & CAS Conflict Handling (`ProgressSyncManager`)
- **Debounced Sync**: Debounces page navigation events around 1200ms, coalescing rapid page flips.
- **Ordered Syncing**: If furthest page reached offline is 93 and current resume page is 20:
  1. First syncs `currentPage = 93` (`SYNC_MAX`).
  2. Next syncs `currentPage = 20` (`SYNC_RESUME`) with returned revision counter.
- **409 CAS Conflict Resolution**:
  - **Stale Revision (`ReadingProgress` body)**: Emits `StaleRevisionConflict`. User can choose **Server Choice** (accepts server position) or **Local Choice** (overwrites with new operation ID and returned server revision).
  - **Replaced Document (`ApiError` body)**: Emits `ReplacedDocumentConflict`, requiring document reopen.

### H. Batched Reading Summaries (`ReadingSummaryRepository`)
- `GET /api/books/reading-summaries?bookIds=1,2,3...` batches book IDs, deduplicates, and chunks into requests of size <= 100 without N+1 per-card queries.
- Displays PDF progress, page counter, percentage (formatted e.g. `64.6%`), max page reached, and "Continue reading" / "Upload PDF" / "Replace PDF" actions on library cards.

### I. Google Drive Integration Status
- **Prompt 11 Browser Flow (Operational)**: Explains external browser / Custom Tab handoff for Google OAuth consent andsame-origin browser Google Picker.
- **Native SAF Drive Provider (Operational)**: Normal Android document picker (`ACTION_OPEN_DOCUMENT`) streams selected Google Drive URIs via ordinary multipart upload (`sourceType=UPLOAD`).
- **Prompt 12 Native PKCE Handoff Protocol (Proposed Extension)**: Designed and documented in `drive_handoff_design.artifact.md` with Flyway `V9` schema migration and OpenAPI contracts pending backend deployment authorization.

---

## 4. Summary of Verification & Unit Test Coverage

All 11 test suites pass with **0 errors**:

| Test Suite File | Tested Behaviors & Coverage |
| :--- | :--- |
| `SessionCoordinatorTests.kt` | Signup 201 -> Login 200 sequence, 401 invalid credentials error, 10 simultaneous 401s triggering exactly 1 refresh call, token rotation, consumed refresh token logout, logout session generation block. |
| `ContractTests.kt` | JSON field mapping, Long values, explicit `version = 0` serialization, URL path resolution, multipart upload headers, binary 206 streaming, 204 empty response, 409 `ReadingProgress` vs `ApiError` shapes, batched summaries. |
| `SseCatalogueTests.kt` | Omit blank search filters, exact title/author filters, local creation notification deduplication flags, 400 bad cursor resync trigger, 401 auth error reconnect stop, account switch stream cancellation. |
| `LocalReadingStoreTests.kt` | Atomic recovery & process recreation, two accounts in one workspace isolation, two environments with same bookId isolation, latest local intent surviving old server response, max 93 then resume 20 offline, simulated disk write failure. |
| `DocumentUploadTests.kt` | 201 Upload success & active document fetch, exact retry reusing same `Idempotency-Key` and staged file, new key for new file, 413 / 415 / 403 server errors, streaming buffer counters, filename sanitization. |
| `PdfCacheRepositoryTests.kt` | Complete @Streaming download, 206 Range content append, 200 Range ignored truncation, corrupt checksum deletion, 409 replacement error, offline reopening with account isolation. |
| `PdfReaderTests.kt` | 144-page fixture restoration at page 93 (index 92), first-open page 1, backward reading navigation (page 20 with max page 93 preserved), page index bounds clamping, corrupt file descriptor cleanup. |
| `ProgressSyncTests.kt` | 93/144 = 64.58% calculation & version 0 initial save, exact uncertain retry with same operationId, max 93 then resume 20 ordered sync, stale 409 revision conflict user choices, replaced document 409 conflict, backward navigation completion retention. |
| `ReadingSummaryTests.kt` | Mixed missing/present document summaries, >100 book IDs chunked into <=100 requests, summary network error isolation, remaining book capacity calculation (`book_limit - books_used`). |
| `DriveImportTests.kt` | Drive connection status (connected false/true), 204 disconnect, 202 import initiation, repeat idempotency key replay, polling `PENDING` -> `RUNNING` -> `COMPLETED` / `FAILED`. |
| `EndToEndIntegrationTests.kt` | Complete auth lifecycle matrix, quota 403 & duplicate ISBN 409 error routing, Range download & SHA-256 integrity check, saved-page restoration & backward completion, multi-device conflict choice, cross-account & workspace isolation. |

---

## 5. Summary of Changed / Created Files

### Data & Network
- `app/src/main/java/com/parvez/booker/data/model/` (DTOs: `ApiError`, `Tokens`, `Document`, `ReadingProgress`, `ProgressUpdate`, `ReadingSummary`, `DriveConnect`, `DriveConnection`, `DriveImportRequest`, `DrivePicker`, `ImportStatus`, `Workspace`)
- `app/src/main/java/com/parvez/booker/data/network/BookApi.kt`
- `app/src/main/java/com/parvez/booker/data/network/RetrofitClient.kt`
- `app/src/main/java/com/parvez/booker/data/network/SessionCoordinator.kt`
- `app/src/main/java/com/parvez/booker/data/network/BookSseManager.kt`
- `app/src/main/java/com/parvez/booker/data/network/UploadStreamRequestBody.kt`

### Repositories & Stores
- `app/src/main/java/com/parvez/booker/data/repository/BookRepository.kt`
- `app/src/main/java/com/parvez/booker/data/repository/LocalReadingStore.kt`
- `app/src/main/java/com/parvez/booker/data/repository/DocumentRepository.kt`
- `app/src/main/java/com/parvez/booker/data/repository/PdfCacheRepository.kt`
- `app/src/main/java/com/parvez/booker/data/repository/ProgressSyncManager.kt`
- `app/src/main/java/com/parvez/booker/data/repository/DriveImportRepository.kt`
- `app/src/main/java/com/parvez/booker/data/repository/CursorStorage.kt`

### UI & ViewModel
- `app/src/main/java/com/parvez/booker/ui/viewmodel/BookViewModel.kt`
- `app/src/main/java/com/parvez/booker/ui/viewmodel/BookUiState.kt`
- `app/src/main/java/com/parvez/booker/ui/reader/PdfRendererAdapter.kt`
- `app/src/main/java/com/parvez/booker/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/com/parvez/booker/ui/reader/PdfReaderScreen.kt`
- `app/src/main/java/com/parvez/booker/ui/components/BookerBookCard.kt`
- `app/src/main/java/com/parvez/booker/ui/components/LoginDialog.kt`
- `app/src/main/java/com/parvez/booker/ui/components/WorkspaceBanner.kt`

### Security & Resources
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/AndroidManifest.xml`

---

## 6. Known Limitations & Acceptance Notes

1. **Background Sync**: Uses Coroutine outbox sync and deferrable background flushing. Does not use an always-on background service; process death relies on durable `LocalReadingStore` persistence until reauthentication.
2. **Offline Instant Revocation**: A cached PDF remains readable offline for the authenticated account until the device reconnects or the user logs out/clears cache.
3. **SSE Boundary**: Server-Sent Events notify `book.created` events only. Document uploads, imports, and progress updates trigger REST refreshes rather than SSE push.
4. **Drive Integration**: Web OAuth browser flow and native SAF `DocumentsProvider` selection are fully operational. Seamless native PKCE handoff protocol is fully designed and documented for future backend implementation.
