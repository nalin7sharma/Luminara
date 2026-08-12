package com.luminara.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.LANGUAGE_OPTIONS
import com.luminara.app.data.LectureSummaryDto
import com.luminara.app.data.LuminaraApi
import com.luminara.app.data.languageOption
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Rose
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.ui.theme.VioletSoft
import com.luminara.app.viewmodel.UiState
import java.util.Calendar

@Composable
fun HomeScreen(
    state: UiState,
    onStartLecture: () -> Unit,
    onOpenLecture: (String) -> Unit,
    onAskBob: (String) -> Unit,
    onLanguage: (String) -> Unit,
    onRefresh: () -> Unit,
    onBaseUrlChange: (String) -> Unit,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    val lectures = state.readyLectures

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item { Greeting(state, { showLanguages = true }, { showSettings = true }) }

            state.connectionError?.let { message ->
                item { ErrorBanner("Backend unreachable — $message", onRetry = onRefresh) }
            }

            item { DemoHeroCard(onStartLecture) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LiveLectureTile(Modifier.weight(1f))
                    AskBobTile(
                        enabled = lectures.isNotEmpty(),
                        lectureCount = lectures.size,
                        modifier = Modifier.weight(1f),
                    ) { lectures.firstOrNull()?.let { onAskBob(it.id) } }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("My lectures", Modifier.weight(1f))
                    if (lectures.isNotEmpty()) {
                        Text(
                            "${lectures.size} saved",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                        )
                    }
                }
            }

            if (lectures.isEmpty()) {
                item { EmptyLibrary() }
            } else {
                items(lectures) { lecture ->
                    LectureCard(lecture) { onOpenLecture(lecture.id) }
                }
            }
        }
    }

    if (showSettings) {
        BaseUrlDialog(
            current = LuminaraApi.baseUrl,
            onDismiss = { showSettings = false },
            onSave = {
                onBaseUrlChange(it)
                showSettings = false
            },
        )
    }

    if (showLanguages) {
        LanguageDialog(
            current = state.language,
            onDismiss = { showLanguages = false },
            onPick = {
                onLanguage(it)
                showLanguages = false
            },
        )
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun Greeting(state: UiState, onLanguage: () -> Unit, onSettings: () -> Unit) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }
    val option = languageOption(state.language)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Luminara",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, "Backend settings", tint = TextFaint)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip(option.nativeName, onLanguage)
            StatusChip(state)
        }
    }
}

@Composable
private fun LanguageChip(label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .background(Violet.copy(alpha = 0.14f), RoundedCornerShape(50))
            .border(1.dp, Violet.copy(alpha = 0.32f), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Language, null, tint = VioletSoft, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = VioletSoft)
    }
}

@Composable
private fun StatusChip(state: UiState) {
    val (label, color) = when {
        state.checkingConnection -> "Connecting…" to TextFaint
        state.connectionError != null -> "Offline" to Rose
        state.config?.bob?.configured == true -> "BOB connected" to Teal
        state.liveAi -> "AI connected" to Teal
        else -> "Local engines only" to Amber
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.26f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(7.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DemoHeroCard(onStart: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Violet.copy(alpha = 0.34f), Color(0xFF171E33))),
                shape,
            )
            .border(1.dp, Violet.copy(alpha = 0.38f), shape)
            .padding(22.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, null, tint = VioletSoft, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "TODAY'S LECTURE",
                style = MaterialTheme.typography.labelSmall,
                color = VioletSoft,
                letterSpacing = 1.6.sp,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Binary Search",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "CS 201 — Data Structures & Algorithms",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("70s", "lecture audio")
            Fact("1", "whiteboard")
            Fact("4", "languages")
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Violet),
        ) {
            Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("Start lecture", style = MaterialTheme.typography.labelLarge, fontSize = 16.sp)
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Column(
        Modifier
            .background(Color.Black.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 9.dp)
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextFaint)
    }
}

@Composable
private fun LiveLectureTile(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "dot",
    )
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .height(146.dp)
            .background(InkCard.copy(alpha = 0.5f), shape)
            .border(1.dp, InkBorder, shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).alpha(pulse).background(Rose, CircleShape))
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.GraphicEq,
                null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Live Lecture",
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Follow class in near real time",
            style = MaterialTheme.typography.bodyMedium,
            color = TextFaint,
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            "COMING NEXT",
            style = MaterialTheme.typography.labelSmall,
            color = Amber,
            fontSize = 10.sp,
            letterSpacing = 1.2.sp,
        )
    }
}

@Composable
private fun AskBobTile(
    enabled: Boolean,
    lectureCount: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .height(146.dp)
            .background(
                if (enabled) Violet.copy(alpha = 0.16f) else InkCard.copy(alpha = 0.5f),
                shape,
            )
            .border(1.dp, if (enabled) Violet.copy(alpha = 0.34f) else InkBorder, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(16.dp)
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            null,
            tint = if (enabled) VioletSoft else TextFaint,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.weight(1f))
        Text(
            "Ask BOB",
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else TextSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (enabled) {
                "$lectureCount ${if (lectureCount == 1) "lecture" else "lectures"} ready to ask about"
            } else {
                "Process a lecture first"
            },
            maxLines = 2,
            style = MaterialTheme.typography.bodyMedium,
            color = TextFaint,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun EmptyLibrary() {
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.4f), shape)
            .border(1.dp, InkBorder, shape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Book, null, tint = TextFaint, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(12.dp))
        Text(
            "No lectures yet",
            style = MaterialTheme.typography.titleSmall,
            color = TextSecondary,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            "Process the demo lecture above and it will be saved here for you to revisit.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextFaint,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun LectureCard(lecture: LectureSummaryDto, onClick: () -> Unit) {
    val live = lecture.engine.isNotBlank() &&
        !lecture.engine.startsWith("local") && !lecture.engine.startsWith("none")
    val dot = if (live) Teal else Amber
    val shape = RoundedCornerShape(20.dp)

    Row(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.68f), shape)
            .border(1.dp, InkBorder, shape)
            .clickable { onClick() }
            .padding(17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(9.dp).background(dot, CircleShape),
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                lecture.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                buildString {
                    append(languageOption(lecture.language).nativeName)
                    if (lecture.conceptCount > 0) append(" · ${lecture.conceptCount} concepts")
                    if (lecture.formulaCount > 0) append(" · ${lecture.formulaCount} formulas")
                    if (lecture.durationSec > 0) append(" · ${lecture.durationSec.toInt()}s")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.5.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Filled.ArrowForward,
            null,
            tint = TextFaint,
            modifier = Modifier.size(17.dp),
        )
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun LanguageDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text("Study language", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    "Notes, explanations and BOB's answers arrive in this language. " +
                        "Formulas keep their original notation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(14.dp))
                LANGUAGE_OPTIONS.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                if (current == option.code) Violet.copy(alpha = 0.16f)
                                else Color.Transparent,
                                RoundedCornerShape(12.dp),
                            )
                            .clickable { onPick(option.code) }
                            .padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            option.nativeName,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (current == option.code) VioletSoft
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        if (!option.verified) {
                            Text(
                                "preview",
                                style = MaterialTheme.typography.labelSmall,
                                color = Amber,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = TextSecondary) }
        },
    )
}

@Composable
private fun BaseUrlDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkCard,
        title = { Text("Backend address", color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column {
                Text(
                    "10.0.2.2 is this machine as seen from the emulator. On a physical " +
                        "device use your computer's LAN IP.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(value) }) { Text("Save") } },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        },
    )
}

/** Shared top bar used by the inner screens. */
@Composable
fun ScreenTopBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .background(InkCard, CircleShape)
                .border(1.dp, InkBorder, CircleShape),
        ) {
            Icon(Icons.Filled.Close, "Back", tint = TextSecondary, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}
