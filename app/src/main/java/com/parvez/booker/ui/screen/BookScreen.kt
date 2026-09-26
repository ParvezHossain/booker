package com.parvez.booker.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.parvez.booker.ui.components.AddBookDialog
import com.parvez.booker.ui.components.LoginDialog
import com.parvez.booker.ui.viewmodel.BookViewModel

/**
 * Top-level screen binding [BookViewModel] UI state with Compose components.
 */
@Composable
fun BookScreen(
    viewModel: BookViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF090A12),
            Color(0xFF1A1028),
            Color(0xFF2B1A3F)
        )
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (uiState.isAuthenticated) {
                FloatingActionButton(
                    onClick = { viewModel.setAddBookDialogVisible(true) },
                    containerColor = Color(0xFFFFD36E),
                    contentColor = Color(0xFF1A1028),
                    shape = CircleShape,
                    modifier = Modifier
                        .padding(16.dp)
                        .shadow(12.dp, CircleShape, ambientColor = Color(0xFFFFC857))
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Book",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush)
                .padding(innerPadding)
        ) {
            if (uiState.isAuthenticated) {
                BookListContent(
                    uiState = uiState,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onFilterSelect = { viewModel.updateFilter(it) },
                    onRefresh = { viewModel.refreshBooks() },
                    onOpenAddBook = { viewModel.setAddBookDialogVisible(true) },
                    onLogout = { viewModel.logout() }
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "BOOKER",
                            color = Color(0xFFFFD36E),
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Serif
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please log in to access your digital library.",
                            color = Color(0xFFD9C7A3),
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif
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