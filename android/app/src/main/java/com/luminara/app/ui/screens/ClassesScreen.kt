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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.ClassDto
import com.luminara.app.ui.components.EmptyState
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

@Composable
fun ClassesScreen(
    state: UiState,
    onOpenClass: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onJoin: (String) -> Unit,
    onDismissError: () -> Unit,
    onBack: () -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var showJoin by remember { mutableStateOf(false) }
    val teacher = state.isTeacher

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenTopBar(
                    "My classes",
                    if (teacher) "Classes you teach" else "Classes you have joined",
                    onBack,
                )
            }

            state.classError?.let { item { ErrorBanner(it, onRetry = onDismissError) } }

            item {
                Button(
                    onClick = { if (teacher) showCreate = true else showJoin = true },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    Icon(Icons.Filled.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (teacher) "Create a class" else "Join a class with a code",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            if (state.classes.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.Groups,
                        if (teacher) "No classes yet" else "You have not joined a class",
                        if (teacher) {
                            "Create one and share its join code with your students."
                        } else {
                            "Ask your teacher for the six-character join code."
                        },
                    )
                }
            }

            items(state.classes) { schoolClass ->
                ClassCard(schoolClass) { onOpenClass(schoolClass.id) }
            }
        }
    }

    if (showCreate) {
        TextPromptDialog(
            title = "New class",
            body = "Students join it with a code Luminara generates.",
            firstLabel = "Class name",
            firstPlaceholder = "CS 201",
            secondLabel = "Subject",
            secondPlaceholder = "Data Structures & Algorithms",
            confirm = "Create",
            busy = state.classBusy,
            onDismiss = { showCreate = false },
            onConfirm = { name, subject ->
                onCreate(name, subject)
                showCreate = false
            },
        )
    }

    if (showJoin) {
        TextPromptDialog(
            title = "Join a class",
            body = "Enter the six-character code your teacher gave you.",
            firstLabel = "Join code",
            firstPlaceholder = "ABC123",
            secondLabel = null,
            secondPlaceholder = "",
            confirm = "Join",
            busy = state.classBusy,
            onDismiss = { showJoin = false },
            onConfirm = { code, _ ->
                onJoin(code)
                showJoin = false
            },
        )
    }
}

@Composable
private fun ClassCard(schoolClass: ClassDto, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(Violet.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Groups, null, tint = Violet, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    schoolClass.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (schoolClass.subject.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        schoolClass.subject,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 12.5.sp,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        if (schoolClass.isTeacher) {
                            append("${schoolClass.studentCount} student")
                            if (schoolClass.studentCount != 1) append("s")
                            append(" · ${schoolClass.lectureCount} lecture")
                            if (schoolClass.lectureCount != 1) append("s")
                        } else {
                            append(schoolClass.teacherName.ifBlank { "Your teacher" })
                            append(" · ${schoolClass.lectureCount} lecture")
                            if (schoolClass.lectureCount != 1) append("s")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.sp,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = TextFaint,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

/** Shows the join code big enough to read from the back of a classroom. */
@Composable
fun JoinCodeCard(code: String) {
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(Teal.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .border(1.dp, Teal.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .clickable { clipboard.setText(AnnotatedString(code)) }
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "JOIN CODE",
            style = MaterialTheme.typography.labelSmall,
            color = Teal,
            letterSpacing = 1.6.sp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            code,
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            letterSpacing = 6.sp,
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.ContentCopy,
                null,
                tint = TextFaint,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "tap to copy",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
            )
        }
    }
}

@Composable
fun TextPromptDialog(
    title: String,
    body: String,
    firstLabel: String,
    firstPlaceholder: String,
    secondLabel: String?,
    secondPlaceholder: String,
    confirm: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit,
) {
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.imePadding()) {
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = first,
                    onValueChange = { first = it },
                    singleLine = true,
                    label = { Text(firstLabel, color = TextFaint) },
                    placeholder = { Text(firstPlaceholder, color = TextFaint) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = InkBorder,
                    ),
                )
                if (secondLabel != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = second,
                        onValueChange = { second = it },
                        singleLine = true,
                        label = { Text(secondLabel, color = TextFaint) },
                        placeholder = { Text(secondPlaceholder, color = TextFaint) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Violet,
                            unfocusedBorderColor = InkBorder,
                        ),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(first.trim(), second.trim()) },
                enabled = first.isNotBlank() && !busy,
            ) { Text(confirm, color = if (first.isNotBlank()) Violet else TextFaint) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}
