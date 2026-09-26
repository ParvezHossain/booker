package com.parvez.booker.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.parvez.booker.data.model.BookRequest

/**
 * Modal dialog containing form fields to submit a new book payload.
 */
@Composable
fun AddBookDialog(
    initialQuery: String? = null,
    onDismiss: () -> Unit,
    onSubmitBook: (BookRequest, (String) -> Unit) -> Unit
) {
    val isDigitsOnly = initialQuery?.trim()?.all { it.isDigit() } == true && (initialQuery.trim().length in 10..13)

    var isbn by remember { mutableStateOf(if (isDigitsOnly) initialQuery?.trim().orEmpty() else "") }
    var title by remember { mutableStateOf(if (!isDigitsOnly) initialQuery?.trim().orEmpty() else "") }
    var author by remember { mutableStateOf("") }
    var publishedDate by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var completed by remember { mutableStateOf(false) }

    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(16.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFFFFD36E),
                            Color(0xFF8B5E1A),
                            Color(0xFFFFD36E)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF20162F)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Add New Book",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif,
                        color = Color(0xFFFFD36E)
                    )

                    Text(
                        text = "✕",
                        fontSize = 18.sp,
                        color = Color(0xFFFFD36E),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onDismiss() }
                            .padding(8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = isbn,
                    onValueChange = { isbn = it },
                    label = { Text("ISBN (10 or 13 digits)", color = Color(0xFFD9C7A3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD36E),
                        unfocusedBorderColor = Color(0xFF8B5E1A),
                        focusedTextColor = Color(0xFFFFD36E),
                        unfocusedTextColor = Color(0xFFFFD36E),
                        cursorColor = Color(0xFFFFD36E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Book Title", color = Color(0xFFD9C7A3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD36E),
                        unfocusedBorderColor = Color(0xFF8B5E1A),
                        focusedTextColor = Color(0xFFFFD36E),
                        unfocusedTextColor = Color(0xFFFFD36E),
                        cursorColor = Color(0xFFFFD36E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author", color = Color(0xFFD9C7A3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD36E),
                        unfocusedBorderColor = Color(0xFF8B5E1A),
                        focusedTextColor = Color(0xFFFFD36E),
                        unfocusedTextColor = Color(0xFFFFD36E),
                        cursorColor = Color(0xFFFFD36E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = publishedDate,
                    onValueChange = { publishedDate = it },
                    label = { Text("Published Date (e.g. 1925)", color = Color(0xFFD9C7A3)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD36E),
                        unfocusedBorderColor = Color(0xFF8B5E1A),
                        focusedTextColor = Color(0xFFFFD36E),
                        unfocusedTextColor = Color(0xFFFFD36E),
                        cursorColor = Color(0xFFFFD36E)
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description", color = Color(0xFFD9C7A3)) },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFFD36E),
                        unfocusedBorderColor = Color(0xFF8B5E1A),
                        focusedTextColor = Color(0xFFFFD36E),
                        unfocusedTextColor = Color(0xFFFFD36E),
                        cursorColor = Color(0xFFFFD36E)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Completed Reading?",
                        color = Color(0xFFFFD36E),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Switch(
                        checked = completed,
                        onCheckedChange = { completed = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF1A1028),
                            checkedTrackColor = Color(0xFFFFD36E),
                            uncheckedThumbColor = Color(0xFF8B5E1A),
                            uncheckedTrackColor = Color(0xFF20162F)
                        )
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFFF6B6B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color(0xFFD9C7A3))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            isSubmitting = true
                            errorMessage = null
                            val request = BookRequest(
                                isbn = isbn.trim(),
                                title = title.trim(),
                                author = author.trim(),
                                publishedDate = publishedDate.trim(),
                                description = description.trim().ifBlank { null },
                                completed = completed
                            )
                            onSubmitBook(request) { error ->
                                isSubmitting = false
                                errorMessage = error
                            }
                        },
                        enabled = isbn.isNotBlank() && title.isNotBlank() && author.isNotBlank() && publishedDate.isNotBlank() && !isSubmitting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFFD36E),
                            contentColor = Color(0xFF1A1028),
                            disabledContainerColor = Color(0xFF8B5E1A).copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color(0xFF1A1028),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Save Book", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}