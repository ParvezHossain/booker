package com.parvez.booker.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.ui.components.BookCard
import com.parvez.booker.ui.components.FilterBadge
import com.parvez.booker.ui.components.StatCard
import com.parvez.booker.ui.viewmodel.BookFilterOption
import com.parvez.booker.ui.viewmodel.BookUiState

/**
 * Library dashboard view containing header, metric stats, search, filter chips, and book list cards.
 */
@Composable
fun BookListContent(
    uiState: BookUiState,
    onSearchQueryChange: (String) -> Unit,
    onFilterSelect: (BookFilterOption) -> Unit,
    onRefresh: () -> Unit,
    onOpenAddBook: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        // Header Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BOOKER",
                    color = Color(0xFFFFD36E),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Serif,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "Personal Library & Knowledge Hub",
                    color = Color(0xFFD9C7A3),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Serif
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFFFFD36E)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = onOpenAddBook,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFFD36E),
                        contentColor = Color(0xFF1A1028)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = onLogout,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(38.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFD36E)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFFD36E))
                ) {
                    Text(text = "Logout", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Metrics Dashboard Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                title = "Total Books",
                value = uiState.totalCount.toString(),
                accentColor = Color(0xFFFFD36E),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "Completed",
                value = uiState.completedCount.toString(),
                accentColor = Color(0xFF81C784),
                modifier = Modifier.weight(1f)
            )
            StatCard(
                title = "In Progress",
                value = uiState.inProgressCount.toString(),
                accentColor = Color(0xFFFF8A80),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by title, author, or ISBN...", color = Color(0xFF9E927A)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFFFFD36E)
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFD36E),
                unfocusedBorderColor = Color(0xFF8B5E1A),
                focusedTextColor = Color(0xFFFFD36E),
                unfocusedTextColor = Color(0xFFFFD36E),
                cursorColor = Color(0xFFFFD36E),
                focusedContainerColor = Color(0xFF1B1228),
                unfocusedContainerColor = Color(0xFF1B1228)
            )
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterBadge(
                label = "All (${uiState.totalCount})",
                isSelected = uiState.selectedFilter == BookFilterOption.ALL,
                onClick = { onFilterSelect(BookFilterOption.ALL) }
            )
            FilterBadge(
                label = "Completed (${uiState.completedCount})",
                isSelected = uiState.selectedFilter == BookFilterOption.COMPLETED,
                onClick = { onFilterSelect(BookFilterOption.COMPLETED) }
            )
            FilterBadge(
                label = "In Progress (${uiState.inProgressCount})",
                isSelected = uiState.selectedFilter == BookFilterOption.IN_PROGRESS,
                onClick = { onFilterSelect(BookFilterOption.IN_PROGRESS) }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Content List / Progress
        if (uiState.isLoadingBooks) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFFFFD36E))
            }
        } else if (uiState.filteredBooks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (uiState.searchQuery.isNotBlank()) "No books match your search." else "No books found in library.",
                        color = Color(0xFFD9C7A3),
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenAddBook,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD36E),
                            contentColor = Color(0xFF1A1028)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Add New Book", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(uiState.filteredBooks) { book ->
                    BookCard(book = book)
                }
            }
        }
    }
}