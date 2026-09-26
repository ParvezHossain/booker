package com.parvez.booker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Filter chip component for filtering books in the library.
 */
@Composable
fun FilterBadge(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) Color(0xFFFFD36E) else Color(0xFF20162F),
        border = BorderStroke(
            1.dp,
            if (isSelected) Color(0xFFFFD36E) else Color(0xFF8B5E1A)
        )
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) Color(0xFF1A1028) else Color(0xFFFFD36E),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}