package com.parvez.booker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.BgDark
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary
import com.parvez.booker.ui.viewmodel.BookFilterOption
import com.parvez.booker.ui.viewmodel.SearchType

/**
 * Control bar containing search text input and scrollable filter chips.
 */
@Composable
fun BookerSearchBarAndChips(
    searchQuery: String,
    searchType: SearchType,
    selectedFilter: BookFilterOption,
    totalCount: Int,
    completedCount: Int,
    inProgressCount: Int,
    onSearchQueryChange: (String) -> Unit,
    onSearchTypeChange: (SearchType) -> Unit,
    onFilterSelect: (BookFilterOption) -> Unit,
    onApiSearchSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(BgDark)
            .padding(vertical = 10.dp)
    ) {
        // Search Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = {
                val hint = when (searchType) {
                    SearchType.LOCAL -> "Search title, author, ISBN…"
                    SearchType.TITLE_AUTHOR -> "API: e.g. Joshua Bloch or Effective Java"
                    SearchType.ISBN -> "API: e.g. 9780134685991"
                }
                Text(hint, color = TextMuted, fontSize = 13.sp)
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchType != SearchType.LOCAL && searchQuery.isNotBlank()) {
                    Button(
                        onClick = onApiSearchSubmit,
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentGold,
                            contentColor = BgDark
                        ),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .height(34.dp)
                    ) {
                        Text("Search", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp)),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = Color.Transparent,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = AccentGold,
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontally Scrollable Chip Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookerChip(
                label = "All · $totalCount",
                isSelected = selectedFilter == BookFilterOption.ALL && searchType == SearchType.LOCAL,
                onClick = {
                    onSearchTypeChange(SearchType.LOCAL)
                    onFilterSelect(BookFilterOption.ALL)
                }
            )

            BookerChip(
                label = "Completed · $completedCount",
                isSelected = selectedFilter == BookFilterOption.COMPLETED && searchType == SearchType.LOCAL,
                onClick = {
                    onSearchTypeChange(SearchType.LOCAL)
                    onFilterSelect(BookFilterOption.COMPLETED)
                }
            )

            BookerChip(
                label = "In progress · $inProgressCount",
                isSelected = selectedFilter == BookFilterOption.IN_PROGRESS && searchType == SearchType.LOCAL,
                onClick = {
                    onSearchTypeChange(SearchType.LOCAL)
                    onFilterSelect(BookFilterOption.IN_PROGRESS)
                }
            )

            BookerChip(
                label = "By title (API)",
                isSelected = searchType == SearchType.TITLE_AUTHOR,
                onClick = { onSearchTypeChange(SearchType.TITLE_AUTHOR) }
            )

            BookerChip(
                label = "By ISBN (API)",
                isSelected = searchType == SearchType.ISBN,
                onClick = { onSearchTypeChange(SearchType.ISBN) }
            )
        }
    }
}

@Composable
private fun BookerChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) AccentGold else SurfaceDark,
        border = BorderStroke(
            1.dp,
            if (isSelected) AccentGold else HairlineBorder
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) BgDark else TextMuted,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}