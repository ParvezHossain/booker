package com.parvez.booker.ui.screen

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parvez.booker.ui.components.AddBookDialog
import com.parvez.booker.ui.components.BookerBookCard
import com.parvez.booker.ui.components.BookerNoticeBanner
import com.parvez.booker.ui.components.BookerSearchBarAndChips
import com.parvez.booker.ui.components.BookerStatsStrip
import com.parvez.booker.ui.components.BookerTopBar
import com.parvez.booker.ui.components.LoginDialog
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.BgDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.viewmodel.BookViewModel

/**
 * Modern personal library dashboard screen matching Booker design tokens.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookerLibraryScreen(
    viewModel: BookViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && uiState.isAuthenticated) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { _ -> }

        LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Observe app lifecycle to manage shared SSE connection when foregrounded/backgrounded
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
                        contentDescription = "Quick Add Book",
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
                    // Item 1: Top Bar
                    item {
                        BookerTopBar(
                            totalCount = uiState.totalCount,
                            onRefresh = { viewModel.refreshBooks() },
                            onLogout = { viewModel.logout() },
                            onOpenAddBook = { viewModel.setAddBookDialogVisible(true) }
                        )
                    }

                    // Item 2: Dismissible Notice Banner
                    if (uiState.newBookNotification != null) {
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            BookerNoticeBanner(
                                book = uiState.newBookNotification!!,
                                onDismiss = { viewModel.dismissNotification() }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // Item 3: Horizontal Stats Strip
                    item {
                        BookerStatsStrip(
                            totalCount = uiState.totalCount,
                            completedCount = uiState.completedCount,
                            inProgressCount = uiState.inProgressCount
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    // Item 4: Sticky Control Bar (Search Field & Horizontal Chips)
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

                    // Item 5: Sub-bar Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recently added",
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
                    if (uiState.isLoadingBooks) {
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
                                        text = if (uiState.searchQuery.isNotBlank()) "No books found matching '${uiState.searchQuery}'" else "No books found in library.",
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
                                            text = if (uiState.searchQuery.isNotBlank()) "Add '${uiState.searchQuery}' as New Book" else "+ Add New Book",
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
                            key = { book -> book.id ?: book.isbn ?: book.title ?: System.identityHashCode(book) }
                        ) { book ->
                            BookerBookCard(
                                book = book,
                                onStatusToggle = { viewModel.toggleBookCompletion(it) },
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
                    isLoggingIn = uiState.isLoggingIn,
                    errorMessage = uiState.loginErrorMessage,
                    onLoginSubmit = { username, password ->
                        viewModel.login(username, password)
                    }
                )
            }

            if (uiState.showAddBookDialog) {
                AddBookDialog(
                    initialQuery = uiState.searchQuery,
                    onDismiss = { viewModel.setAddBookDialogVisible(false) },
                    onSubmitBook = { request, onError ->
                        viewModel.createBook(
                            request = request,
                            onSuccess = {
                                Toast.makeText(context, "Book added successfully!", Toast.LENGTH_SHORT).show()
                            },
                            onError = onError
                        )
                    }
                )
            }
        }
    }
}