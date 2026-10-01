package com.parvez.booker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.data.model.Workspace
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.StatusWarning
import com.parvez.booker.ui.theme.SuccessGreen
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary

/**
 * Display banner presenting current active workspace name, subscription plan, quota usage bar, and capacity.
 */
@Composable
fun WorkspaceBanner(
    workspace: Workspace?,
    modifier: Modifier = Modifier
) {
    if (workspace == null) return

    val bookLimit = workspace.bookLimit
    val booksUsed = workspace.booksUsed
    val remaining = workspace.remainingCapacity
    val progress = if (bookLimit > 0) (booksUsed.toFloat() / bookLimit.toFloat()).coerceIn(0f, 1f) else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workspace.name ?: "My Workspace",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Plan: ${workspace.plan ?: "FREE"}",
                    fontSize = 12.sp,
                    color = AccentGold,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "$booksUsed / $bookLimit books ($remaining left)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (remaining <= 5) StatusWarning else TextMuted
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (remaining <= 5) StatusWarning else SuccessGreen,
            trackColor = HairlineBorder
        )
    }
}