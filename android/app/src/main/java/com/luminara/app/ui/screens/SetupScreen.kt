package com.luminara.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luminara.app.data.LuminaraApi
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.Pill
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

@Composable
fun SetupScreen(
    state: UiState,
    onLanguage: (String) -> Unit,
    onProcess: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val cached = state.readyDemo

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item { ScreenTopBar("Lecture setup", "Binary Search · CS 201", onBack) }

            item {
                SectionLabel("I want to learn in")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    val languages = state.languages.ifEmpty { defaultLanguages() }
                    languages.take(4).forEach { lang ->
                        Pill(
                            text = lang.name,
                            selected = state.language == lang.code,
                            onClick = { onLanguage(lang.code) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Notes, explanations and BOB's answers arrive in this language. " +
                        "Formulas and technical terms stay in their original notation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 13.sp,
                )
            }

            item {
                SectionLabel("Lecture inputs")
                Spacer(Modifier.height(10.dp))
                GlassCard {
                    AsyncImage(
                        model = "${LuminaraApi.baseUrl}/api/demo/image",
                        contentDescription = "Classroom whiteboard",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, InkBorder, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(16.dp))
                    InputRow(
                        icon = { Icon(Icons.Filled.GraphicEq, null, tint = Teal, modifier = Modifier.size(19.dp)) },
                        title = "Teacher audio · 70 seconds",
                        body = "Transcribed on the spot by Whisper running on the backend.",
                    )
                    Spacer(Modifier.height(14.dp))
                    InputRow(
                        icon = { Icon(Icons.Filled.Image, null, tint = Violet, modifier = Modifier.size(19.dp)) },
                        title = "Whiteboard photograph",
                        body = "Read for text, diagrams and formulas — independently of the speech.",
                    )
                }
            }

            item {
                Button(
                    onClick = { onProcess(true) },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    Icon(Icons.Filled.PlayArrow, null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Process this lecture",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 16.sp,
                    )
                }
                if (cached != null) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { onProcess(false) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Text(
                            "Open the last processed result",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Processing runs the real pipeline: speech recognition, board OCR, " +
                        "diagram interpretation, fusion, notes and translation. It takes " +
                        "roughly 30–60 seconds.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

@Composable
private fun InputRow(
    icon: @Composable () -> Unit,
    title: String,
    body: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        icon()
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, fontSize = 13.sp)
        }
    }
}

private fun defaultLanguages() = listOf(
    com.luminara.app.data.LanguageDto("en", "English"),
    com.luminara.app.data.LanguageDto("hi", "Hindi"),
)
