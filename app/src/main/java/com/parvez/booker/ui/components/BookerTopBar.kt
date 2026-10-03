package com.parvez.booker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parvez.booker.R
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.BgDark
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary

/**
 * Premium mobile header for Booker digital library featuring warm library study room background,
 * brand emblem, circular actions, library stats metadata, and a gold pill add button.
 */
@Composable
fun BookerTopBar(
    totalCount: Int,
    userEmail: String? = null,
    workspaceName: String? = null,
    isPublicTab: Boolean = false,
    isSuperAdmin: Boolean = false,
    onRefresh: () -> Unit,
    onChangePassword: (() -> Unit)? = null,
    onResetPassword: (() -> Unit)? = null,
    onLogout: () -> Unit,
    onOpenAddBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by remember { mutableStateOf(false) }

    val initialLetter = remember(userEmail) {
        userEmail?.trim()?.firstOrNull()?.uppercase() ?: "A"
    }

    val topSubtitle = when {
        isSuperAdmin -> "Super Admin Portal"
        isPublicTab -> "Global Library"
        else -> "Personal Library"
    }

    val libraryTitle = when {
        isPublicTab -> "Public Library"
        else -> "My Library"
    }

    val librarySubtitle = when {
        isPublicTab -> "$totalCount books in public library"
        else -> "$totalCount books in your workspace"
    }

    val addButtonText = when {
        isPublicTab && isSuperAdmin -> "Add Public Book"
        isPublicTab -> "Request Book"
        else -> "Add Book"
    }

    // Outer Header Box with warm library background image and subtle dark scrim overlay
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
            .background(BgDark)
    ) {
        // Background Library Study Image (Bookshelf, Lamp, and Desk)
        Image(
            painter = painterResource(id = R.drawable.library_header_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        // Gradient Scrim Overlay for high-contrast text readability
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xA0130C1E),
                            Color(0xC0130C1E),
                            Color(0xE6130C1E)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Row 1: Brand Area (Left) & Action Controls (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Brand Area
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    // Square "B" Booker Logo Mark
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x802D1B48))
                            .border(1.dp, Color(0x90E5B551), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "B",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp,
                            color = AccentGold
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "Booker",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 26.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = topSubtitle,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Action Controls: Circular Refresh & Avatar Buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Circular Refresh Button
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color(0x802A1B43))
                            .border(1.dp, Color(0x50A59BB8), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Circular User Avatar / Profile Button
                    Box {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(0x802A1B43))
                                .border(1.dp, Color(0x50A59BB8), CircleShape)
                                .clickable { isMenuExpanded = !isMenuExpanded },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initialLetter,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = TextPrimary
                            )
                        }

                        DropdownMenu(
                            expanded = isMenuExpanded,
                            onDismissRequest = { isMenuExpanded = false },
                            modifier = Modifier
                                .background(SurfaceDark)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        ) {
                            if (!userEmail.isNullOrBlank()) {
                                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                                    Text(
                                        text = userEmail,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = workspaceName ?: "Workspace Account",
                                        fontSize = 11.sp,
                                        color = TextMuted
                                    )
                                }
                                HorizontalDivider(color = HairlineBorder)
                            }

                            if (onChangePassword != null) {
                                DropdownMenuItem(
                                    text = { Text("Change Password", fontSize = 13.sp, color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = AccentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        onChangePassword()
                                    }
                                )
                            }

                            if (onResetPassword != null) {
                                DropdownMenuItem(
                                    text = { Text("Reset Password Token", fontSize = 13.sp, color = TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = null,
                                            tint = AccentGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        onResetPassword()
                                    }
                                )
                            }

                            HorizontalDivider(color = HairlineBorder)

                            DropdownMenuItem(
                                text = { Text("Sign Out", fontSize = 13.sp, color = Color(0xFFFF6B6B)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    isMenuExpanded = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Row 2: Library Information (Left) & Add Book Button (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Library Title & Subtitle with Outlined Book Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = libraryTitle,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = librarySubtitle,
                            fontSize = 11.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Prominent + Add Book Gold Pill Button
                Button(
                    onClick = onOpenAddBook,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = BgDark
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = BgDark,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = addButtonText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                        color = BgDark
                    )
                }
            }
        }
    }
}
