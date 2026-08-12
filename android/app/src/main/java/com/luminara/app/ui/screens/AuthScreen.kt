package com.luminara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

/**
 * Sign in or create an account. Skippable on purpose: the demo lecture and
 * everything you process yourself work without one, and a student should be
 * able to try the product before being asked for an email.
 */
@Composable
fun AuthScreen(
    state: UiState,
    onRegister: (String, String) -> Unit,
    onLogin: (String, String) -> Unit,
    onSkip: () -> Unit,
    onDismissError: () -> Unit,
) {
    var registering by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val roleWord = if (state.role == "teacher") "teacher" else "student"
    val canSubmit = email.contains("@") && password.length >= 6 && !state.authBusy

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(24.dp, 64.dp, 24.dp, 32.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(Violet.copy(alpha = 0.16f), CircleShape)
                            .border(1.dp, Violet.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.School,
                            null,
                            tint = Violet,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (state.displayName.isNotBlank()) {
                            "Hello, ${state.displayName}"
                        } else {
                            "Luminara"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    if (registering) "Create your $roleWord account" else "Welcome back",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (registering) {
                        "An account lets you join classes and keep your lectures across devices."
                    } else {
                        "Sign in to reach your classes."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(26.dp))
            }

            state.authError?.let {
                item {
                    ErrorBanner(it, onRetry = onDismissError)
                    Spacer(Modifier.height(16.dp))
                }
            }

            item {
                Field(
                    value = email,
                    onValueChange = { email = it.trim() },
                    label = "Email",
                    placeholder = "you@university.edu",
                    keyboard = KeyboardType.Email,
                )
                Spacer(Modifier.height(14.dp))
                Field(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    placeholder = "at least 6 characters",
                    keyboard = KeyboardType.Password,
                    secret = true,
                )
                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (registering) onRegister(email, password) else onLogin(email, password)
                    },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    if (state.authBusy) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = androidx.compose.ui.graphics.Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            if (registering) "Create account" else "Sign in",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        if (registering) "Already have an account? " else "New here? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                    )
                    Text(
                        if (registering) "Sign in" else "Create one",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Violet,
                        modifier = Modifier.clickable {
                            registering = !registering
                            onDismissError()
                        },
                    )
                }

                Spacer(Modifier.height(30.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(InkCard.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
                        .clickable { onSkip() }
                        .padding(16.dp),
                ) {
                    Text(
                        "Continue without an account",
                        style = MaterialTheme.typography.titleSmall,
                        color = Teal,
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "The demo lecture, your own recordings and BOB all work as a guest. " +
                            "You need an account only to join or teach a class.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboard: KeyboardType,
    secret: Boolean = false,
) {
    Text(
        label.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextFaint,
        letterSpacing = 1.4.sp,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = { Text(placeholder, color = TextFaint, fontSize = 14.sp) },
        visualTransformation = if (secret) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet,
            unfocusedBorderColor = InkBorder,
            focusedContainerColor = InkCard.copy(alpha = 0.6f),
            unfocusedContainerColor = InkCard.copy(alpha = 0.6f),
        ),
    )
}
