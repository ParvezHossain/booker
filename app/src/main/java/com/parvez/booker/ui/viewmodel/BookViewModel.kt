package com.parvez.booker.ui.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.PublicLibraryBookRequest
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.network.BookSseEvent
import com.parvez.booker.data.network.RetrofitClient
import com.parvez.booker.data.repository.BookRepository
import com.parvez.booker.data.repository.DocumentRepository
import com.parvez.booker.data.repository.PdfDownloadState
import com.parvez.booker.data.repository.UploadState
import com.parvez.booker.ui.notification.SystemNotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AndroidViewModel managing UI state, JWT session lifecycle, workspace accounts, public library,
 * password management, live SSE notifications, and book operations.
 */
class BookViewModel(
    application: Application,
    private val repository: BookRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = BookRepository(application)
    )

    private val gson = Gson()
    private val systemNotificationHelper = SystemNotificationHelper(application)

    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    init {
        observeSessionState()
        observeSseEvents()
    }

    private fun observeSessionState() {
        viewModelScope.launch {
            repository.sessionCoordinator.sessionState.collect { session ->
                if (session.isAuthenticated) {
                    _uiState.update {
                        it.copy(
                            workspace = session.workspace,
                            isAuthenticated = true,
                            showLoginDialog = false,
                            isLoggingIn = false,
                            isSigningUp = false
                        )
                    }
                    refreshPublicBooks()
                } else if (_uiState.value.isAuthenticated) {
                    _uiState.update {
                        BookUiState(
                            books = emptyList(),
                            publicBooks = emptyList(),
                            workspace = null,
                            isAuthenticated = false,
                            showLoginDialog = true,
                            authMode = AuthMode.SIGN_IN
                        )
                    }
                }
            }
        }
    }

    private fun observeSseEvents() {
        viewModelScope.launch {
            repository.sseEvents.collect { event ->
                when (event) {
                    is BookSseEvent.BookCreated -> {
                        val newBook = event.book
                        if (!event.isOwnCreation) {
                            systemNotificationHelper.showNewBookNotification(newBook)
                        }

                        _uiState.update { current ->
                            val alreadyExists = current.books.any {
                                it.id != null && it.id == newBook.id
                            }
                            val updatedList = if (alreadyExists) {
                                current.books.map {
                                    if (it.id != null && it.id == newBook.id) newBook else it
                                }
                            } else {
                                current.books + newBook
                            }
                            current.copy(
                                books = updatedList,
                                newBookNotification = newBook,
                                userFeedbackMessage = "New book added: ${newBook.title ?: "Untitled"}"
                            )
                        }
                        refreshWorkspace()
                    }
                    is BookSseEvent.PublicRequestReviewed -> {
                        _uiState.update {
                            it.copy(
                                userFeedbackMessage = "Public book request updated: ${event.status}"
                            )
                        }
                        refreshPublicBooks()
                        refreshPublicBookRequests()
                    }
                    is BookSseEvent.ResyncRequired -> {
                        refreshWorkspaceAndBooks()
                    }
                    is BookSseEvent.AuthError -> {
                        logout()
                    }
                }
            }
        }
    }

    fun onAppForegrounded() {
        if (_uiState.value.isAuthenticated) {
            repository.resumeSse()
            refreshWorkspaceAndBooks()
            refreshPublicBooks()
        }
    }

    fun onAppBackgrounded() {
        repository.pauseSse()
    }

    fun dismissNotification() {
        _uiState.update { it.copy(newBookNotification = null) }
    }

    private fun extractApiErrorMessage(httpException: HttpException): String? {
        return try {
            val errorJson = httpException.response()?.errorBody()?.string()
            if (!errorJson.isNullOrBlank()) {
                val apiError = gson.fromJson(errorJson, ApiError::class.java)
                apiError?.message
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun formatErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> {
                val serverMsg = extractApiErrorMessage(throwable)
                if (!serverMsg.isNullOrBlank()) {
                    return serverMsg
                }
                when (throwable.code()) {
                    400 -> "Invalid request payload. Please verify form values."
                    401 -> "Invalid credentials. Please verify your email and password."
                    403 -> "Workspace book limit reached or super admin permission required."
                    404 -> "Requested book or record was not found."
                    409 -> "Resource conflict (duplicate email or author/title pair)."
                    413 -> "Selected file is too large (exceeds server multipart limit)."
                    415 -> "Use a valid, unencrypted PDF without document scripts or attachments, within page limits."
                    500, 503 -> "Server error occurred. Please try again later."
                    else -> "HTTP ${throwable.code()}: ${throwable.message()}"
                }
            }
            is ConnectException, is UnknownHostException -> {
                "Cannot connect to Booker API (${RetrofitClient.baseUrl}). Check connection."
            }
            is SocketTimeoutException -> {
                "Server request timed out. Please try again."
            }
            else -> throwable.localizedMessage ?: "An unexpected error occurred."
        }
    }

    // --- Tab Selection & Public Library ---

    fun selectLibraryTab(tab: LibraryTab) {
        _uiState.update { it.copy(selectedLibraryTab = tab, searchQuery = "") }
        if (tab == LibraryTab.PUBLIC_LIBRARY && _uiState.value.publicBooks.isEmpty()) {
            refreshPublicBooks()
            refreshPublicBookRequests()
        }
    }

    fun refreshPublicBookRequests() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBookRequests = true) }
            try {
                val requests = repository.getPublicBookRequests()
                _uiState.update {
                    it.copy(
                        publicBookRequests = requests,
                        isLoadingBookRequests = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingBookRequests = false
                    )
                }
            }
        }
    }

    fun submitPublicBookRequest(title: String, authorName: String, onSuccess: (PublicLibraryBookRequest) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val req = repository.submitPublicBookRequest(title, authorName)
                _uiState.update { current ->
                    current.copy(
                        publicBookRequests = current.publicBookRequests + req,
                        showAddBookDialog = false,
                        userFeedbackMessage = "Public book request for '$title' submitted successfully!"
                    )
                }
                refreshPublicBookRequests()
                withContext(Dispatchers.Main) {
                    onSuccess(req)
                }
            } catch (e: Exception) {
                val errorMsg = formatErrorMessage(e)
                withContext(Dispatchers.Main) {
                    onError(errorMsg)
                }
            }
        }
    }

    fun refreshPublicBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingPublicBooks = true) }
            try {
                val publicBooks = repository.getPublicBooks()
                _uiState.update {
                    it.copy(
                        publicBooks = publicBooks,
                        isLoadingPublicBooks = false
                    )
                }
                refreshSummariesForPublicBooks(publicBooks)
                if (_uiState.value.isSuperAdmin) {
                    refreshAdminPublicBookRequests()
                } else {
                    refreshPublicBookRequests()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingPublicBooks = false,
                        userFeedbackMessage = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    fun refreshAdminPublicBookRequests(status: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBookRequests = true) }
            try {
                val adminRequests = repository.getAdminPublicBookRequests(status)
                _uiState.update {
                    it.copy(
                        adminPublicBookRequests = adminRequests,
                        isLoadingBookRequests = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBookRequests = false) }
            }
        }
    }

    fun rejectPublicBookRequest(requestId: String) {
        viewModelScope.launch {
            try {
                repository.rejectPublicBookRequest(requestId)
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = "Public book request rejected."
                    )
                }
                refreshAdminPublicBookRequests()
            } catch (e: Exception) {
                _uiState.update { it.copy(userFeedbackMessage = formatErrorMessage(e)) }
            }
        }
    }

    fun acceptPublicBookRequest(
        requestId: String,
        context: Context,
        uri: Uri,
        publishedDate: String,
        description: String?,
        completed: Boolean = false,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.acceptPublicBookRequest(
                    requestId = requestId,
                    context = context,
                    uri = uri,
                    publishedDate = publishedDate,
                    description = description,
                    completed = completed
                )
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = "Public book request accepted & PDF published successfully!"
                    )
                }
                refreshAdminPublicBookRequests()
                refreshPublicBooks()
                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                val errorMsg = formatErrorMessage(e)
                withContext(Dispatchers.Main) {
                    onError(errorMsg)
                }
            }
        }
    }

    private fun refreshSummariesForPublicBooks(books: List<Book>) {
        val ids = books.mapNotNull { it.id }.filter { it > 0L }
        if (ids.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val summariesMap = repository.fetchPublicReadingSummaries(ids)
            if (summariesMap.isNotEmpty()) {
                _uiState.update {
                    it.copy(publicReadingSummaries = it.publicReadingSummaries + summariesMap)
                }
            }
        }
    }

    // --- Authentication & Password Management ---

    fun setAuthMode(mode: AuthMode) {
        _uiState.update {
            it.copy(
                authMode = mode,
                loginErrorMessage = null,
                signupErrorMessage = null
            )
        }
    }

    fun signup(workspaceNameInput: String, emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSigningUp = true, signupErrorMessage = null) }
            val cleanEmail = emailInput.trim().lowercase()
            try {
                val request = SignupRequest(
                    workspaceName = workspaceNameInput.trim(),
                    email = cleanEmail,
                    password = passwordInput
                )
                repository.signup(request)

                try {
                    val workspace = repository.login(cleanEmail, passwordInput)
                    val books = repository.getBooks()

                    _uiState.update {
                        it.copy(
                            workspace = workspace,
                            books = books,
                            isAuthenticated = true,
                            showLoginDialog = false,
                            isSigningUp = false,
                            userFeedbackMessage = "Workspace created and signed in successfully!"
                        )
                    }
                    refreshPublicBooks()
                } catch (loginEx: Exception) {
                    _uiState.update {
                        it.copy(
                            isSigningUp = false,
                            authMode = AuthMode.SIGN_IN,
                            signupErrorMessage = null,
                            userFeedbackMessage = "Workspace created! Please sign in with your email & password."
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        signupErrorMessage = formatErrorMessage(e),
                        isSigningUp = false
                    )
                }
            }
        }
    }

    fun login(emailInput: String, passwordInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginErrorMessage = null) }
            val genBefore = repository.sessionCoordinator.currentGeneration
            try {
                val cleanEmail = emailInput.trim().lowercase()
                val workspace = repository.login(cleanEmail, passwordInput)
                val books = try { repository.getBooks() } catch (e: Exception) { emptyList() }

                val isSuperAdmin = workspace.id == "super_admin"

                if (repository.sessionCoordinator.currentGeneration == genBefore + 1L) {
                    _uiState.update {
                        it.copy(
                            workspace = workspace,
                            books = books,
                            isAuthenticated = true,
                            showLoginDialog = false,
                            isLoggingIn = false,
                            loginErrorMessage = null,
                            selectedLibraryTab = if (isSuperAdmin) LibraryTab.PUBLIC_LIBRARY else it.selectedLibraryTab
                        )
                    }
                    refreshPublicBooks()
                }
            } catch (e: Exception) {
                if (repository.sessionCoordinator.currentGeneration == genBefore) {
                    _uiState.update {
                        it.copy(
                            loginErrorMessage = formatErrorMessage(e),
                            isLoggingIn = false
                        )
                    }
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _uiState.update {
                BookUiState(
                    books = emptyList(),
                    publicBooks = emptyList(),
                    workspace = null,
                    isAuthenticated = false,
                    showLoginDialog = true,
                    authMode = AuthMode.SIGN_IN
                )
            }
        }
    }

    // --- Password Management Actions (Steps 36–38) ---

    fun setChangePasswordDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showPasswordChangeDialog = visible,
                passwordActionFeedback = null
            )
        }
    }

    fun setForgotPasswordDialogVisible(visible: Boolean) {
        _uiState.update {
            it.copy(
                showForgotPasswordDialog = visible,
                passwordActionFeedback = null
            )
        }
    }

    fun changePassword(currentPass: String, newPass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPasswordActionInProgress = true, passwordActionFeedback = null) }
            try {
                repository.changePassword(currentPass, newPass)
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        showPasswordChangeDialog = false,
                        userFeedbackMessage = "Password changed successfully! Please sign in again with your new password."
                    )
                }
                logout()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        passwordActionFeedback = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    fun forgotPassword(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPasswordActionInProgress = true, passwordActionFeedback = null) }
            try {
                val res = repository.forgotPassword(email)
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        passwordActionFeedback = res.message ?: "Password reset instructions sent to your email."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        passwordActionFeedback = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    fun resetPassword(token: String, newPass: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isPasswordActionInProgress = true, passwordActionFeedback = null) }
            try {
                repository.resetPassword(token, newPass)
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        showForgotPasswordDialog = false,
                        userFeedbackMessage = "Password reset successfully! Please sign in with your new password."
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isPasswordActionInProgress = false,
                        passwordActionFeedback = formatErrorMessage(e)
                    )
                }
            }
        }
    }

    // --- Catalogue & Search ---

    fun refreshWorkspaceAndBooks() {
        val currentGen = repository.sessionCoordinator.currentGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBooks = true, userFeedbackMessage = null) }
            try {
                val workspace = try { repository.getWorkspace() } catch (e: Exception) { null }
                val books = try { repository.getBooks() } catch (e: Exception) { emptyList() }
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    _uiState.update {
                        it.copy(
                            workspace = workspace ?: it.workspace,
                            books = books,
                            isLoadingBooks = false,
                            searchQuery = ""
                        )
                    }
                    refreshSummariesForBooks(books)
                }
            } catch (e: Exception) {
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    _uiState.update {
                        it.copy(
                            isLoadingBooks = false,
                            userFeedbackMessage = formatErrorMessage(e)
                        )
                    }
                }
            }
        }
    }

    fun refreshSummariesForBooks(books: List<Book>) {
        val ids = books.mapNotNull { it.id }.filter { it > 0L }
        if (ids.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val summariesMap = repository.fetchReadingSummaries(ids)
            if (summariesMap.isNotEmpty()) {
                _uiState.update {
                    it.copy(readingSummaries = it.readingSummaries + summariesMap)
                }
            }
        }
    }

    private fun refreshWorkspace() {
        val currentGen = repository.sessionCoordinator.currentGeneration
        viewModelScope.launch {
            try {
                val workspace = repository.getWorkspace()
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    _uiState.update { it.copy(workspace = workspace) }
                }
            } catch (e: Exception) {
                // Ignore silent workspace refresh failures
            }
        }
    }

    fun refreshBooks() {
        if (_uiState.value.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY) {
            refreshPublicBooks()
        } else {
            refreshWorkspaceAndBooks()
        }
    }

    fun performApiSearch() {
        val query = _uiState.value.searchQuery.trim()
        val searchType = _uiState.value.searchType
        val isPublic = _uiState.value.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY
        val currentGen = repository.sessionCoordinator.currentGeneration

        if (query.isBlank()) {
            if (isPublic) refreshPublicBooks() else refreshWorkspaceAndBooks()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBooks = true, userFeedbackMessage = null) }
            try {
                val results = when (searchType) {
                    SearchType.BOOK_ID -> {
                        val numericId = query.toLongOrNull()
                        if (numericId != null && numericId > 0) {
                            try {
                                val book = if (isPublic) repository.getPublicBookById(numericId) else repository.getBookById(numericId)
                                listOf(book)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } else emptyList()
                    }
                    SearchType.TITLE_AUTHOR, SearchType.LOCAL -> {
                        if (isPublic) {
                            val byTitle = try { repository.getPublicBooks(title = query, author = null) } catch (e: Exception) { emptyList() }
                            val byAuthor = try { repository.getPublicBooks(title = null, author = query) } catch (e: Exception) { emptyList() }
                            (byTitle + byAuthor).distinctBy { it.id ?: it.title }
                        } else {
                            val byTitle = try { repository.getBooks(title = query, author = null) } catch (e: Exception) { emptyList() }
                            val byAuthor = try { repository.getBooks(title = null, author = query) } catch (e: Exception) { emptyList() }
                            (byTitle + byAuthor).distinctBy { it.id ?: it.title }
                        }
                    }
                }
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    val msg = if (results.isEmpty()) "No books found for '$query'." else null
                    if (isPublic) {
                        _uiState.update { it.copy(publicBooks = results, isLoadingBooks = false, userFeedbackMessage = msg) }
                    } else {
                        _uiState.update { it.copy(books = results, isLoadingBooks = false, userFeedbackMessage = msg) }
                    }
                }
            } catch (e: Exception) {
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    _uiState.update { it.copy(isLoadingBooks = false, userFeedbackMessage = formatErrorMessage(e)) }
                }
            }
        }
    }

    fun createBook(request: BookRequest, onSuccess: (Book) -> Unit, onError: (String) -> Unit) {
        val currentGen = repository.sessionCoordinator.currentGeneration
        viewModelScope.launch {
            try {
                val newBook = repository.createBook(request)
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    _uiState.update { current ->
                        val updatedList = if (current.books.any { it.id == newBook.id }) {
                            current.books
                        } else {
                            current.books + newBook
                        }
                        current.copy(
                            books = updatedList,
                            showAddBookDialog = false,
                            userFeedbackMessage = "Book '${newBook.title}' created successfully!"
                        )
                    }
                    refreshWorkspace()
                    withContext(Dispatchers.Main) {
                        onSuccess(newBook)
                    }
                }
            } catch (e: Exception) {
                if (repository.sessionCoordinator.currentGeneration == currentGen) {
                    val errorMsg = formatErrorMessage(e)
                    withContext(Dispatchers.Main) {
                        onError(errorMsg)
                    }
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateSearchType(type: SearchType) {
        _uiState.update { it.copy(searchType = type) }
    }

    fun updateFilter(filter: BookFilterOption) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setAddBookDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddBookDialog = visible) }
    }

    // --- PDF Upload & Cache Actions ---

    fun uploadPdfForBook(context: Context, bookId: Long, uri: Uri) {
        val currentState = _uiState.value.uploadState
        if (currentState is UploadState.Transferring ||
            currentState is UploadState.Staging ||
            currentState is UploadState.Validating) {
            return
        }

        val uploadId = DocumentRepository.generateIdempotencyKey()
        _uiState.update {
            it.copy(
                uploadBookId = bookId,
                uploadState = UploadState.Staging,
                userFeedbackMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val docRepo = repository.documentRepository
            var snapshotFile: File? = null
            try {
                val metadata = docRepo.queryUriMetadata(context, uri)
                if (metadata.fileSize != null && metadata.fileSize == 0L) {
                    throw IllegalArgumentException("Selected PDF is empty (0 bytes).")
                }

                snapshotFile = docRepo.stagePrivateSnapshot(context, uri, uploadId)
                val totalLength = snapshotFile.length()

                _uiState.update {
                    it.copy(uploadState = UploadState.Transferring(0L, totalLength, 0f))
                }

                val uploadedDoc = docRepo.uploadDocument(
                    bookId = bookId,
                    idempotencyKey = uploadId,
                    snapshotFile = snapshotFile,
                    fileName = metadata.fileName,
                    onProgress = { bytesSent, totalBytes ->
                        val percent = if (totalBytes > 0) (bytesSent.toFloat() / totalBytes.toFloat()) * 100f else 0f
                        if (bytesSent >= totalBytes) {
                            _uiState.update {
                                it.copy(uploadState = UploadState.Validating)
                            }
                        } else {
                            _uiState.update {
                                it.copy(uploadState = UploadState.Transferring(bytesSent, totalBytes, percent))
                            }
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        uploadState = UploadState.Success(uploadedDoc),
                        userFeedbackMessage = "PDF '${uploadedDoc.fileName}' uploaded successfully! Reading progress reset for new document version."
                    )
                }

                refreshWorkspace()
            } catch (e: Exception) {
                val errorMsg = formatErrorMessage(e)
                val code = (e as? HttpException)?.code()
                _uiState.update {
                    it.copy(uploadState = UploadState.Error(errorMsg, code))
                }
            } finally {
                docRepo.cleanupSnapshot(context, uploadId)
            }
        }
    }

    fun clearUploadState() {
        _uiState.update {
            it.copy(
                uploadBookId = null,
                uploadState = UploadState.Idle
            )
        }
    }

    fun downloadPdfForBook(bookId: Long, document: Document) {
        val email = repository.sessionCoordinator.sessionState.value.email
        val workspaceId = repository.sessionCoordinator.sessionState.value.workspace?.id
        if (email.isNullOrBlank() || workspaceId.isNullOrBlank()) return

        val envUrl = RetrofitClient.baseUrl

        _uiState.update {
            it.copy(
                downloadBookId = bookId,
                downloadState = PdfDownloadState.Downloading(0L, document.fileSize, 0f),
                userFeedbackMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            val pdfRepo = repository.pdfCacheRepository
            try {
                val completeFile = pdfRepo.downloadPdfDocument(
                    envUrl = envUrl,
                    email = email,
                    workspaceId = workspaceId,
                    bookId = bookId,
                    document = document,
                    onProgress = { downloaded, total, percent ->
                        _uiState.update {
                            it.copy(
                                downloadState = PdfDownloadState.Downloading(downloaded, total, percent)
                            )
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        downloadState = PdfDownloadState.Completed(completeFile),
                        userFeedbackMessage = "PDF '${document.fileName}' downloaded and cached for offline reading."
                    )
                }
            } catch (e: Exception) {
                val msg = formatErrorMessage(e)
                val code = (e as? HttpException)?.code()
                _uiState.update {
                    it.copy(
                        downloadState = PdfDownloadState.Error(msg, code)
                    )
                }
            }
        }
    }

    fun clearDownloadState() {
        _uiState.update {
            it.copy(
                downloadBookId = null,
                downloadState = PdfDownloadState.Idle
            )
        }
    }

    fun fetchDriveConnection() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val conn = repository.driveImportRepository.getDriveConnection()
                _uiState.update { it.copy(isDriveConnected = conn.connected) }
            } catch (e: Exception) {
                // Ignore silent drive connection check failure
            }
        }
    }

    fun disconnectDrive() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.driveImportRepository.disconnectDrive()
                _uiState.update {
                    it.copy(
                        isDriveConnected = false,
                        userFeedbackMessage = "Google Drive disconnected successfully."
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userFeedbackMessage = formatErrorMessage(e)) }
            }
        }
    }

    fun importDriveFile(bookId: Long, fileId: String) {
        val idempotencyKey = DocumentRepository.generateIdempotencyKey()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val status = repository.driveImportRepository.initiateDriveImport(bookId, idempotencyKey, fileId)
                _uiState.update { it.copy(activeDriveImportStatus = status) }

                if (status.status == "PENDING" || status.status == "RUNNING") {
                    pollDriveImportStatus(bookId, status.importId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(userFeedbackMessage = formatErrorMessage(e)) }
            }
        }
    }

    fun pollDriveImportStatus(bookId: Long, importId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val finalStatus = repository.driveImportRepository.pollImportStatus(
                bookId = bookId,
                importId = importId,
                onUpdate = { status ->
                    _uiState.update { it.copy(activeDriveImportStatus = status) }
                }
            )

            if (finalStatus.status == "COMPLETED") {
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = "Google Drive import completed successfully!"
                    )
                }
                refreshWorkspaceAndBooks()
            } else if (finalStatus.status == "FAILED") {
                _uiState.update {
                    it.copy(
                        userFeedbackMessage = finalStatus.message ?: "Google Drive import failed."
                    )
                }
            }
        }
    }
}
