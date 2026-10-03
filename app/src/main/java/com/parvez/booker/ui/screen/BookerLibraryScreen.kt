package com.parvez.booker.ui.screen

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.PublicLibraryBookRequest
import com.parvez.booker.ui.components.AcceptPublicRequestDialog
import com.parvez.booker.ui.components.AddBookDialog
import com.parvez.booker.ui.components.BookerBookCard
import com.parvez.booker.ui.components.BookerNoticeBanner
import com.parvez.booker.ui.components.BookerSearchBarAndChips
import com.parvez.booker.ui.components.BookerStatsStrip
import com.parvez.booker.ui.components.BookerTopBar
import com.parvez.booker.ui.components.ChangePasswordDialog
import com.parvez.booker.ui.components.ForgotPasswordDialog
import com.parvez.booker.ui.components.LoginDialog
import com.parvez.booker.ui.components.WorkspaceBanner
import com.parvez.booker.ui.reader.PdfReaderScreen
import com.parvez.booker.ui.reader.ReaderViewModel
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.BgDark
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.StatusWarning
import com.parvez.booker.ui.theme.SuccessGreen
import com.parvez.booker.ui.theme.SurfaceAlt
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary
import com.parvez.booker.ui.viewmodel.BookViewModel
import com.parvez.booker.ui.viewmodel.LibraryTab

/**
 * Personal & Public library dashboard screen integrated with backend workspace accounts, quotas,
 * global public library, admin review portal for public requests, and password management.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookerLibraryScreen(
    viewModel: BookViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var targetBookForUpload by remember { mutableStateOf<Book?>(null) }
    var activePdfReaderParams by remember { mutableStateOf<Pair<Book, Document>?>(null) }
    var acceptingRequest by remember { mutableStateOf<PublicLibraryBookRequest?>(null) }

    val pdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && targetBookForUpload?.id != null) {
            viewModel.uploadPdfForBook(context, targetBookForUpload!!.id!!, uri)
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.isAuthenticated) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    viewModel.onAppForegrounded()
                }
                Lifecycle.Event.ON_STOP -> {
                    viewModel.onAppBackgrounded()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val filteredBooks by remember(uiState) {
        derivedStateOf { uiState.filteredBooks }
    }

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BgDark,
        floatingActionButton = {
            if (uiState.isAuthenticated) {
                FloatingActionButton(
                    onClick = { viewModel.setAddBookDialogVisible(true) },
                    containerColor = AccentGold,
                    contentColor = BgDark,
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(bottom = navBarPadding)
                        .size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = if (uiState.isSuperAdmin && uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY) "Add Public Book" else "Quick Add / Request Book",
                        tint = BgDark,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BgDark)
                .padding(innerPadding)
        ) {
            if (uiState.isAuthenticated) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = navBarPadding + 80.dp
                    )
                ) {
                    // Item 1: Redesigned Header Bar
                    item {
                        val isPublicTab = uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY
                        val currentBookCount = if (isPublicTab) uiState.publicBooks.size else uiState.books.size

                        BookerTopBar(
                            totalCount = currentBookCount,
                            userEmail = viewModel.sessionEmail,
                            workspaceName = uiState.workspace?.name,
                            isPublicTab = isPublicTab,
                            isSuperAdmin = uiState.isSuperAdmin,
                            onRefresh = { viewModel.refreshBooks() },
                            onChangePassword = { viewModel.setChangePasswordDialogVisible(true) },
                            onResetPassword = { viewModel.setForgotPasswordDialogVisible(true) },
                            onLogout = { viewModel.logout() },
                            onOpenAddBook = { viewModel.setAddBookDialogVisible(true) }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // Item 2: Workspace Details & Quota Usage Banner (hidden for Super Admin)
                    if (uiState.workspace != null && uiState.selectedLibraryTab == LibraryTab.PRIVATE_WORKSPACE && !uiState.isSuperAdmin) {
                        item {
                            WorkspaceBanner(workspace = uiState.workspace)
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                    }

                    // Item 3: Library Tab Switcher (Private Workspace vs. Public Library)
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceAlt)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (!uiState.isSuperAdmin) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (uiState.selectedLibraryTab == LibraryTab.PRIVATE_WORKSPACE) AccentGold else Color.Transparent)
                                            .clickable { viewModel.selectLibraryTab(LibraryTab.PRIVATE_WORKSPACE) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "My Workspace Books",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (uiState.selectedLibraryTab == LibraryTab.PRIVATE_WORKSPACE) BgDark else TextMuted
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY || uiState.isSuperAdmin) AccentGold else Color.Transparent)
                                        .clickable { viewModel.selectLibraryTab(LibraryTab.PUBLIC_LIBRARY) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (uiState.isSuperAdmin) "Global Public Library (Admin Portal)" else "Global Public Library",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY || uiState.isSuperAdmin) BgDark else TextMuted
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Item 4: Admin Review Section for Super Admin
                    if (uiState.isSuperAdmin && uiState.adminPublicBookRequests.isNotEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Admin Review: All Public Book Requests",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = AccentGold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                uiState.adminPublicBookRequests.forEach { req ->
                                    PublicRequestRow(
                                        request = req,
                                        isAdmin = true,
                                        onAcceptClick = if (req.status.equals("PENDING", ignoreCase = true) && req.id != null) {
                                            { acceptingRequest = req }
                                        } else null,
                                        onRejectClick = if (req.status.equals("PENDING", ignoreCase = true) && req.id != null) {
                                            { viewModel.rejectPublicBookRequest(req.id) }
                                        } else null
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else if (!uiState.isSuperAdmin && uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY && uiState.publicBookRequests.isNotEmpty()) {
                        // Public Library Requests History Section for Regular User
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceDark)
                                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Your Public Book Requests",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    color = AccentGold
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                uiState.publicBookRequests.take(5).forEach { req ->
                                    PublicRequestRow(request = req)
                                    Spacer(modifier = Modifier.height(6.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Item 5: Dismissible Notice Banner
                    if (uiState.newBookNotification != null && uiState.selectedLibraryTab == LibraryTab.PRIVATE_WORKSPACE) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            BookerNoticeBanner(
                                book = uiState.newBookNotification!!,
                                onDismiss = { viewModel.dismissNotification() }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Item 6: Horizontal Stats Strip
                    item {
                        BookerStatsStrip(
                            totalCount = uiState.totalCount,
                            completedCount = uiState.completedCount,
                            inProgressCount = uiState.inProgressCount
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Item 7: Sticky Control Bar (Search Field & Horizontal Chips)
                    stickyHeader {
                        BookerSearchBarAndChips(
                            searchQuery = uiState.searchQuery,
                            searchType = uiState.searchType,
                            selectedFilter = uiState.selectedFilter,
                            totalCount = uiState.totalCount,
                            completedCount = uiState.completedCount,
                            inProgressCount = uiState.inProgressCount,
                            onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                            onSearchTypeChange = { viewModel.updateSearchType(it) },
                            onFilterSelect = { viewModel.updateFilter(it) },
                            onApiSearchSubmit = { viewModel.performApiSearch() }
                        )
                    }

                    // Item 8: Sub-bar Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY || uiState.isSuperAdmin) "Global Public Library Collection" else "Workspace Collection",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                            Text(
                                text = "${filteredBooks.size} books",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextMuted
                            )
                        }
                    }

                    // Items: Book Cards List
                    val isCurrentlyLoading = if (uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY || uiState.isSuperAdmin) uiState.isLoadingPublicBooks else uiState.isLoadingBooks

                    if (isCurrentlyLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = AccentGold)
                            }
                        }
                    } else if (filteredBooks.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 40.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = if (uiState.searchQuery.isNotBlank()) "No books found matching '${uiState.searchQuery}'" else "No books found in collection.",
                                        color = TextMuted,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Serif
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Button(
                                        onClick = { viewModel.setAddBookDialogVisible(true) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = AccentGold,
                                            contentColor = BgDark
                                        ),
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = if (uiState.isSuperAdmin) "+ Add Public Book" else "+ Request Book for Public Library",
                                            fontWeight = FontWeight.Bold,
                                            color = BgDark
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        items(
                            items = filteredBooks,
                            key = { book -> book.id ?: book.title ?: System.identityHashCode(book) }
                        ) { book ->
                            val summary = book.id?.let { uiState.activeSummaries[it] }
                            val isPublicTab = uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY
                            val allowPdfUpload = if (isPublicTab) uiState.isSuperAdmin else true

                            BookerBookCard(
                                book = book,
                                summary = summary,
                                onStatusToggle = null,
                                onUploadPdf = if (allowPdfUpload) { selectedBook ->
                                    targetBookForUpload = selectedBook
                                    pdfLauncher.launch(arrayOf("application/pdf"))
                                } else null,
                                onOpenPdf = { selectedBook, doc ->
                                    activePdfReaderParams = selectedBook to doc
                                },
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Booker",
                            color = AccentGold,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please log in to access your digital library.",
                            color = TextMuted,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            if (uiState.showLoginDialog) {
                LoginDialog(
                    authMode = uiState.authMode,
                    isLoggingIn = uiState.isLoggingIn,
                    isSigningUp = uiState.isSigningUp,
                    loginErrorMessage = uiState.loginErrorMessage,
                    signupErrorMessage = uiState.signupErrorMessage,
                    onTabSelected = { viewModel.setAuthMode(it) },
                    onLoginSubmit = { email, password ->
                        viewModel.login(email, password)
                    },
                    onSignupSubmit = { workspaceName, email, password ->
                        viewModel.signup(workspaceName, email, password)
                    },
                    onForgotPasswordClick = {
                        viewModel.setForgotPasswordDialogVisible(true)
                    }
                )
            }

            if (uiState.showPasswordChangeDialog) {
                ChangePasswordDialog(
                    isSubmitting = uiState.isPasswordActionInProgress,
                    feedbackMessage = uiState.passwordActionFeedback,
                    onDismiss = { viewModel.setChangePasswordDialogVisible(false) },
                    onSubmitChange = { currentPass, newPass ->
                        viewModel.changePassword(currentPass, newPass)
                    }
                )
            }

            if (uiState.showForgotPasswordDialog) {
                ForgotPasswordDialog(
                    isSubmitting = uiState.isPasswordActionInProgress,
                    feedbackMessage = uiState.passwordActionFeedback,
                    onDismiss = { viewModel.setForgotPasswordDialogVisible(false) },
                    onRequestForgot = { email ->
                        viewModel.forgotPassword(email)
                    },
                    onSubmitReset = { token, newPass ->
                        viewModel.resetPassword(token, newPass)
                    }
                )
            }

            if (acceptingRequest != null) {
                AcceptPublicRequestDialog(
                    request = acceptingRequest!!,
                    onDismiss = { acceptingRequest = null },
                    onSubmitAccept = { uri, publishedDate, description, completed, onError ->
                        viewModel.acceptPublicBookRequest(
                            requestId = acceptingRequest!!.id!!,
                            context = context,
                            uri = uri,
                            publishedDate = publishedDate,
                            description = description,
                            completed = completed,
                            onSuccess = {
                                Toast.makeText(context, "Public book request accepted & PDF published!", Toast.LENGTH_SHORT).show()
                                acceptingRequest = null
                            },
                            onError = onError
                        )
                    }
                )
            }

            if (uiState.showAddBookDialog) {
                val isPublic = uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY && !uiState.isSuperAdmin
                AddBookDialog(
                    initialQuery = uiState.searchQuery,
                    isPublicRequest = isPublic,
                    onDismiss = { viewModel.setAddBookDialogVisible(false) },
                    onSubmitBook = { request, onError ->
                        if (isPublic) {
                            viewModel.submitPublicBookRequest(
                                title = request.title,
                                authorName = request.author,
                                onSuccess = {
                                    Toast.makeText(context, "Public book request submitted successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = onError
                            )
                        } else {
                            viewModel.createBook(
                                request = request,
                                onSuccess = {
                                    Toast.makeText(context, "Book added successfully!", Toast.LENGTH_SHORT).show()
                                },
                                onError = onError
                            )
                        }
                    }
                )
            }

            if (activePdfReaderParams != null) {
                val (readingBook, doc) = activePdfReaderParams!!
                val isPublic = uiState.selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY || uiState.isSuperAdmin
                val readerViewModel: ReaderViewModel = viewModel()
                PdfReaderScreen(
                    bookId = readingBook.id ?: 0L,
                    documentId = doc.documentId,
                    bookTitle = readingBook.title ?: "Reading PDF",
                    isPublic = isPublic,
                    viewModel = readerViewModel,
                    onBackClicked = {
                        readerViewModel.closeReader {
                            activePdfReaderParams = null
                            viewModel.refreshBooks()
                        }
                    }
                )
            }
        }
    }
}

/**
 * Compact row presenting status of a user's submitted public library book request or Super Admin review row.
 */
@Composable
private fun PublicRequestRow(
    request: PublicLibraryBookRequest,
    isAdmin: Boolean = false,
    onAcceptClick: (() -> Unit)? = null,
    onRejectClick: (() -> Unit)? = null
) {
    val statusColor = when (request.status.uppercase()) {
        "ACCEPTED" -> SuccessGreen
        "REJECTED" -> Color(0xFFFF6B6B)
        else -> StatusWarning
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.title ?: "Untitled",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "By ${request.authorName ?: "Unknown"}${if (isAdmin && !request.requesterEmail.isNullOrBlank()) " · Requested by ${request.requesterEmail}" else ""}",
                fontSize = 11.sp,
                color = TextMuted
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
            ) {
                Text(
                    text = request.status.uppercase(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            if (isAdmin && request.status.equals("PENDING", ignoreCase = true)) {
                if (onAcceptClick != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    TextButton(
                        onClick = onAcceptClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Accept",
                            color = AccentGold,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (onRejectClick != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = onRejectClick,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "Reject",
                            color = Color(0xFFFF6B6B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
