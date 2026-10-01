package com.parvez.booker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.data.model.Book

/**
 * Card component presenting book title, author, description, ISBN, publication date, and completion status.
 */
@Composable
fun BookCard(
    book: Book,
    onStatusToggle: ((Book) -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color(0xFFFFC857),
                spotColor = Color(0xFFFFC857)
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFFFFD36E),
                        Color(0xFF8B5E1A),
                        Color(0xFFFFD36E)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF20162F)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = book.title ?: "Untitled",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFFFFD36E),
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Surface(
                    onClick = { onStatusToggle?.invoke(book) },
                    enabled = onStatusToggle != null,
                    color = if (book.completed) Color(0xFF1B5E20) else Color(0xFF4A1212),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (book.completed) Color(0xFF81C784) else Color(0xFFFF8A80)
                    )
                ) {
                    Text(
                        text = if (book.completed) "Completed ✓" else "Not Completed ✗",
                        color = if (book.completed) Color(0xFF81C784) else Color(0xFFFF8A80),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "By ${book.author ?: "Unknown Author"}",
                color = Color(0xFFE9D8A6),
                fontSize = 15.sp,
                fontFamily = FontFamily.Serif
            )

            if (!book.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = book.description,
                    color = Color(0xFFC7B896),
                    fontSize = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Book ID: #${book.id ?: "N/A"}",
                color = Color(0xFFBFAE87),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Published: ${book.publishedDate ?: "N/A"}",
                color = Color(0xFFBFAE87),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.then(
                    if (onStatusToggle != null) Modifier.clickable { onStatusToggle(book) } else Modifier
                )
            ) {
                Text(
                    text = "Status: ",
                    color = Color(0xFFBFAE87),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (book.completed) "Completed (Tap to change)" else "Not Completed (Tap to change)",
                    color = if (book.completed) Color(0xFF81C784) else Color(0xFFFF8A80),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}