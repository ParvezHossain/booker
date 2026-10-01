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
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.ReadingSummary
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
    summary: ReadingSummary? = null,
    onStatusToggle: ((Book) -> Unit)? = null,
    onUploadPdf: ((Book) -> Unit)? = null,
    onOpenPdf: ((Book, Document) -> Unit)? = null
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
                    .height(140.dp)
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

                        // Status Badge Pill (Manual Catalogue Metadata)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceAlt)
                                .then(
                                    if (onStatusToggle != null) Modifier.clickable {
                                        onStatusToggle(
                                            book
                                        )
                                    } else Modifier
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // PDF Document & Personal Reading Progress Section
                    val doc = summary?.document
                    val progress = summary?.progress

                    if (doc == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "No PDF uploaded",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            if (onUploadPdf != null) {
                                Text(
                                    text = "Upload PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    modifier = Modifier.clickable { onUploadPdf(book) }
                                )
                            }
                        }
                    } else {
                        Column {
                            val resumePage = progress?.resumePage ?: 1
                            val totalPages = doc.pageCount
                            val percentage = progress?.progressPercentage ?: 0.0

                            val progressText = if (progress == null || progress.currentPage == 0) {
                                "Not started (page 1 of $totalPages)"
                            } else {
                                "Page $resumePage of $totalPages • ${"%.1f".format(percentage)}%"
                            }

                            Text(
                                text = progressText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SuccessGreen
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (onOpenPdf != null) {
                                    Text(
                                        text = if (resumePage > 1) "Continue reading" else "Open PDF",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentGold,
                                        modifier = Modifier.clickable { onOpenPdf(book, doc) }
                                    )
                                }

                                if (onUploadPdf != null) {
                                    Text(
                                        text = "Replace PDF",
                                        fontSize = 11.sp,
                                        color = TextMuted,
                                        modifier = Modifier.clickable { onUploadPdf(book) }
                                    )
                                }
                            }
                        }
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