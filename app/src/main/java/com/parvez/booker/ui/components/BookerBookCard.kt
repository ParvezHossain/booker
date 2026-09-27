package com.parvez.booker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.data.model.Book
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.StatusWarning
import com.parvez.booker.ui.theme.SuccessGreen
import com.parvez.booker.ui.theme.SurfaceAlt
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary
import com.parvez.booker.ui.util.coverColors
import com.parvez.booker.ui.util.initials

/**
 * Modern book item card featuring vertical colored spine, 44x60dp cover swatch with initials,
 * serif title, status pill badge, author, 2-line ellipsized description, and "See more..." popup trigger.
 */
@Composable
fun BookerBookCard(
    book: Book,
    modifier: Modifier = Modifier,
    onStatusToggle: ((Book) -> Unit)? = null
) {
    var showDetailsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable { showDetailsDialog = true }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 6dp Vertical Colored Spine Strip
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(118.dp)
                    .background(if (book.completed) SuccessGreen else AccentGold)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 44x60dp Cover Swatch with Gradient & Initials
                val (color1, color2) = book.coverColors
                Box(
                    modifier = Modifier
                        .size(width = 44.dp, height = 60.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Brush.verticalGradient(listOf(color1, color2)))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = book.initials,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Content Details
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = book.title ?: "Untitled",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(6.dp))

                        // Status Badge Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceAlt)
                                .then(
                                    if (onStatusToggle != null) Modifier.clickable { onStatusToggle(book) } else Modifier
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = if (book.completed) "Completed" else "In progress",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (book.completed) SuccessGreen else StatusWarning
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = book.author ?: "Unknown author",
                        fontSize = 12.sp,
                        color = TextMuted
                    )

                    if (!book.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = book.description,
                            fontSize = 12.sp,
                            color = TextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "See details...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            modifier = Modifier.clickable { showDetailsDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showDetailsDialog) {
        BookDetailsDialog(
            book = book,
            onDismiss = { showDetailsDialog = false },
            onStatusToggle = onStatusToggle
        )
    }
}