package com.luminara.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.components.StageRow
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

@Composable
fun ProcessingScreen(
    state: UiState,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val status = state.status

    LaunchedEffect(status?.status, state.lecture?.id) {
        if (status?.status == "ready" && state.lecture != null) onDone()
    }

    val progress by animateFloatAsState(
        targetValue = status?.progress ?: 0f,
        label = "progress",
    )

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ScreenTopBar(
                    "Understanding the lecture",
                    status?.current ?: "Starting the pipeline…",
                    onBack,
                )
            }

            item {
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(InkCard, RoundedCornerShape(50))
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(Violet, RoundedCornerShape(50))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${(progress * 100).toInt()}% · every step below is real work, " +
                            "timed as it happens",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 13.sp,
                    )
                }
            }

            state.error?.let { item { ErrorBanner(it, onRetry = onBack) } }

            item {
                SectionLabel("Pipeline")
                Spacer(Modifier.height(6.dp))
            }

            item {
                GlassCard {
                    val stages = status?.stages.orEmpty()
                    if (stages.isEmpty()) {
                        Text(
                            "Waiting for the backend to report the first stage…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                        )
                    } else {
                        stages.forEach { StageRow(it) }
                    }
                }
            }

            item {
                Text(
                    "Speech and board are analysed as two independent evidence streams, " +
                        "then fused into one lecture. That is why Luminara can later tell " +
                        "you which parts of an answer came from what the professor said and " +
                        "which came from what they wrote.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
