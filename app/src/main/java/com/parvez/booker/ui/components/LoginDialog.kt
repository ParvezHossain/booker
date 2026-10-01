package com.parvez.booker.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.parvez.booker.ui.theme.AccentGold
import com.parvez.booker.ui.theme.BgDark
import com.parvez.booker.ui.theme.HairlineBorder
import com.parvez.booker.ui.theme.StatusWarning
import com.parvez.booker.ui.theme.SuccessGreen
import com.parvez.booker.ui.theme.SurfaceAlt
import com.parvez.booker.ui.theme.SurfaceDark
import com.parvez.booker.ui.theme.TextMuted
import com.parvez.booker.ui.theme.TextPrimary
import com.parvez.booker.ui.viewmodel.AuthMode

/**
 * Modern, professional authentication modal supporting both Sign In and Sign Up
 * with workspace owner onboarding, real-time input guidance, and high-end visual hierarchy.
 */
@Composable
fun LoginDialog(
    authMode: AuthMode = AuthMode.SIGN_IN,
    isLoggingIn: Boolean = false,
    isSigningUp: Boolean = false,
    loginErrorMessage: String? = null,
    signupErrorMessage: String? = null,
    onTabSelected: (AuthMode) -> Unit = {},
    onLoginSubmit: (String, String) -> Unit,
    onSignupSubmit: (String, String, String) -> Unit = { _, _, _ -> },
    onForgotPasswordClick: (() -> Unit)? = null
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var workspaceName by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    val isSignup = authMode == AuthMode.SIGN_UP
    val clientPasswordsMatch = !isSignup || (password == confirmPassword)
    val isValidPasswordLength = !isSignup || (password.length in 12..64)

    Dialog(
        onDismissRequest = { /* Non-dismissible before authentication */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            AccentGold.copy(alpha = 0.6f),
                            HairlineBorder,
                            AccentGold.copy(alpha = 0.3f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = SurfaceDark
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Brand Emblem Header
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceAlt)
                        .border(
                            width = 1.dp,
                            brush = Brush.horizontalGradient(
                                listOf(AccentGold, HairlineBorder)
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "B",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        color = AccentGold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Booker SaaS",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif,
                    color = TextPrimary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (isSignup) "Register a new library workspace & owner account" else "Sign in to access your library workspace",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    color = TextMuted,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Segmented Switcher Tabs
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceAlt)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        .padding(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Sign In Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (!isSignup) AccentGold else Color.Transparent)
                                .clickable { onTabSelected(AuthMode.SIGN_IN) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Sign In",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isSignup) BgDark else TextMuted
                            )
                        }

                        // Create Workspace Tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSignup) AccentGold else Color.Transparent)
                                .clickable { onTabSelected(AuthMode.SIGN_UP) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Create Workspace",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSignup) BgDark else TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Input Fields
                if (isSignup) {
                    OutlinedTextField(
                        value = workspaceName,
                        onValueChange = { workspaceName = it },
                        label = { Text("Workspace Name", color = TextMuted) },
                        placeholder = { Text("e.g. My Digital Library", color = TextMuted.copy(alpha = 0.5f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentGold,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGold,
                            focusedContainerColor = SurfaceAlt.copy(alpha = 0.5f),
                            unfocusedContainerColor = SurfaceAlt.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Workspace Owner Email", color = TextMuted) },
                    placeholder = { Text("owner@example.com", color = TextMuted.copy(alpha = 0.5f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentGold,
                        focusedContainerColor = SurfaceAlt.copy(alpha = 0.5f),
                        unfocusedContainerColor = SurfaceAlt.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(if (isSignup) "Password (12–64 characters)" else "Password", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(
                                text = if (showPassword) "Hide" else "Show",
                                color = AccentGold,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentGold,
                        unfocusedBorderColor = HairlineBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = AccentGold,
                        focusedContainerColor = SurfaceAlt.copy(alpha = 0.5f),
                        unfocusedContainerColor = SurfaceAlt.copy(alpha = 0.3f)
                    )
                )

                // Forgot Password link nicely aligned directly below Password field
                if (!isSignup && onForgotPasswordClick != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = "Forgot password?",
                            color = AccentGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onForgotPasswordClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }

                if (isSignup) {
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Confirm Password", color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = if (confirmPassword.isNotEmpty() && !clientPasswordsMatch) Color(0xFFFF6B6B) else AccentGold,
                            unfocusedBorderColor = HairlineBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = AccentGold,
                            focusedContainerColor = SurfaceAlt.copy(alpha = 0.5f),
                            unfocusedContainerColor = SurfaceAlt.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Live Password Guidance Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ValidationChip(
                            label = "12–64 chars",
                            isValid = password.length in 12..64,
                            isAttempted = password.isNotEmpty()
                        )
                        ValidationChip(
                            label = "Passwords match",
                            isValid = password.isNotEmpty() && password == confirmPassword,
                            isAttempted = confirmPassword.isNotEmpty()
                        )
                    }
                }

                // Error Message Display Banner
                val displayedError = if (isSignup) {
                    when {
                        !isValidPasswordLength && password.isNotEmpty() -> "Password must be 12 to 64 characters long."
                        !clientPasswordsMatch && confirmPassword.isNotEmpty() -> "Passwords do not match."
                        else -> signupErrorMessage
                    }
                } else {
                    loginErrorMessage
                }

                if (!displayedError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x20FF5252))
                            .border(1.dp, Color(0x50FF5252), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayedError,
                            color = Color(0xFFFF6B6B),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                val isFormValid = if (isSignup) {
                    workspaceName.isNotBlank() && email.isNotBlank() && password.isNotBlank() &&
                            clientPasswordsMatch && isValidPasswordLength && !isSigningUp
                } else {
                    email.isNotBlank() && password.isNotBlank() && !isLoggingIn
                }

                // Main Submit Button displaying Label + Progress Indicator
                Button(
                    onClick = {
                        if (isSignup) {
                            onSignupSubmit(workspaceName, email, password)
                        } else {
                            onLoginSubmit(email, password)
                        }
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentGold,
                        contentColor = BgDark,
                        disabledContainerColor = SurfaceAlt,
                        disabledContentColor = TextMuted
                    )
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoggingIn || isSigningUp) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = BgDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isSignup) "Creating Workspace..." else "Signing In...",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BgDark
                            )
                        } else {
                            Text(
                                text = if (isSignup) "Create Workspace Account" else "Sign In to Workspace",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = BgDark
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer Prompt Link
                val promptPrefix = if (isSignup) "Already registered? " else "Need a workspace? "
                val promptAction = if (isSignup) "Sign In" else "Create Account"

                val annotatedPrompt = buildAnnotatedString {
                    append(promptPrefix)
                    withStyle(
                        style = SpanStyle(
                            color = AccentGold,
                            fontWeight = FontWeight.Bold
                        )
                    ) {
                        append(promptAction)
                    }
                }

                Text(
                    text = annotatedPrompt,
                    fontSize = 12.sp,
                    color = TextMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            onTabSelected(if (isSignup) AuthMode.SIGN_IN else AuthMode.SIGN_UP)
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Compact pill chip indicating live input validation status.
 */
@Composable
private fun ValidationChip(
    label: String,
    isValid: Boolean,
    isAttempted: Boolean
) {
    val chipBg = when {
        isValid -> SuccessGreen.copy(alpha = 0.15f)
        isAttempted -> StatusWarning.copy(alpha = 0.15f)
        else -> SurfaceAlt
    }

    val chipContent = when {
        isValid -> SuccessGreen
        isAttempted -> StatusWarning
        else -> TextMuted
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = chipBg,
        border = BorderStroke(
            1.dp,
            if (isValid) SuccessGreen.copy(alpha = 0.4f) else HairlineBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(chipContent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = chipContent
            )
        }
    }
}
