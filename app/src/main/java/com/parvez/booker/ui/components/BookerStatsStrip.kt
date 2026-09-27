package com.parvez.booker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.SuccessGreen
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary

/**
 * Horizontal stats strip container divided into 3 equal cells with hairline dividers.
 */
@Composable
fun BookerStatsStrip(
    totalCount: Int,
    completedCount: Int,
    inProgressCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cell 1: Total
        StatCell(
            number = totalCount.toString(),
            label = "TOTAL",
            numberColor = TextPrimary,
            modifier = Modifier.weight(1f)
        )

        // Divider 1
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(HairlineBorder)
        )

        // Cell 2: Completed
        StatCell(
            number = completedCount.toString(),
            label = "COMPLETED",
            numberColor = SuccessGreen,
            modifier = Modifier.weight(1f)
        )

        // Divider 2
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(HairlineBorder)
        )

        // Cell 3: In Progress
        StatCell(
            number = inProgressCount.toString(),
            label = "IN PROGRESS",
            numberColor = AccentGold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCell(
    number: String,
    label: String,
    numberColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = number,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = numberColor
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMuted,
            letterSpacing = 1.sp
        )
    }
}