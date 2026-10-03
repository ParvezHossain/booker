package com.parvez.booker.ui.reader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Composable screen destination displaying PDF document pages with saved-page restoration,
 * previous/next page navigation controls, zoom controls, and offline/unsynced indicators.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    bookId: Long,
    documentId: String,
    bookTitle: String,
    isPublic: Boolean = false,
    viewModel: ReaderViewModel,
    onBackClicked: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookId, documentId, isPublic) {
        viewModel.loadDocument(bookId, documentId, bookTitle, isPublic)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.closeReader()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.bookTitle,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.isOffline) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = "Offline",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                            }
                            if (uiState.isUnsynced) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall
                                ) {
                                    Text(
                                        text = "Unsynced",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(
                                            horizontal = 4.dp,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClicked) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to library"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            if (!uiState.isLoading && uiState.errorMessage == null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Page Button
                        IconButton(
                            onClick = { viewModel.previousPage() },
                            enabled = uiState.currentPage > 1
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Previous Page"
                            )
                        }

                        // Page Counter Text
                        Text(
                            text = "Page ${uiState.currentPage} of ${uiState.totalPages}",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp
                            )
                        )

                        // Next Page Button
                        IconButton(
                            onClick = { viewModel.nextPage() },
                            enabled = uiState.currentPage < uiState.totalPages
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Next Page"
                            )
                        }

                        // Zoom Controls
                        Row {
                            IconButton(
                                onClick = { viewModel.setZoomScale(uiState.zoomScale - 0.25f) },
                                enabled = uiState.zoomScale > 0.5f
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Remove,
                                    contentDescription = "Zoom Out"
                                )
                            }
                            IconButton(
                                onClick = { viewModel.setZoomScale(uiState.zoomScale + 0.25f) },
                                enabled = uiState.zoomScale < 3.0f
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Zoom In"
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            when {
                uiState.isLoading -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.padding(top = 16.dp))
                        Text(
                            text = "Opening PDF document...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                uiState.errorMessage != null -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Text(
                            text = uiState.errorMessage ?: "Failed to render PDF",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.padding(top = 16.dp))
                        Button(onClick = {
                            viewModel.loadDocument(
                                bookId,
                                documentId,
                                bookTitle
                            )
                        }) {
                            Text("Retry Opening")
                        }
                    }
                }

                uiState.currentBitmap != null -> {
                    Image(
                        bitmap = uiState.currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${uiState.currentPage} of ${uiState.totalPages}",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
