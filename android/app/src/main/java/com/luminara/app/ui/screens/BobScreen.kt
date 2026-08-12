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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.SourceDto
import com.luminara.app.ui.components.EngineBadge
import com.luminara.app.ui.components.MarkdownText
import com.luminara.app.ui.components.SourceChipRow
import com.luminara.app.ui.components.ThinkingDots
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Rose
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.ChatTurn
import com.luminara.app.viewmodel.UiState

/**
 * The BOB conversation. Lives inside the Lecture Detail as a tab, so it carries
 * no chrome of its own — the lecture it is grounded in is already on screen.
 */
@Composable
fun BobChat(
    state: UiState,
    onAsk: (String) -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    onSource: ((SourceDto) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.chat.size) {
        if (state.chat.isNotEmpty()) listState.animateScrollToItem(state.chat.size)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.chat.isEmpty()) {
                item { BobIntro(state, onAsk) }
            } else {
                item {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            Modifier.clickable { onClear() }.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                null,
                                tint = TextFaint,
                                modifier = Modifier.size(15.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Clear",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextFaint,
                            )
                        }
                    }
                }
            }
            items(state.chat.size) { index -> ChatBubble(state.chat[index], onRetry, onSource) }
            if (state.bobThinking) {
                item { ThinkingDots(modifier = Modifier.padding(vertical = 6.dp)) }
            }
        }

        Composer(
            value = draft,
            enabled = state.lecture?.status == "ready" && !state.bobThinking,
            onValueChange = { draft = it },
            onSend = {
                if (draft.isNotBlank()) {
                    onAsk(draft.trim())
                    draft = ""
                }
            },
        )
    }
}

@Composable
private fun BobIntro(state: UiState, onAsk: (String) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .background(Violet.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .border(1.dp, Violet.copy(alpha = 0.4f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.AutoAwesome, null, tint = Violet, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    "BOB attended this lecture",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Answers come from the speech, the board and the diagram — with sources.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
        Spacer(Modifier.height(22.dp))
        Text(
            "TRY ASKING",
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            letterSpacing = 1.4.sp,
        )
        Spacer(Modifier.height(10.dp))
        state.suggestions.forEach { suggestion ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(InkCard.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
                    .border(1.dp, InkBorder, RoundedCornerShape(14.dp))
                    .clickable { onAsk(suggestion) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    null,
                    tint = Violet.copy(alpha = 0.7f),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    turn: ChatTurn,
    onRetry: () -> Unit,
    onSource: ((SourceDto) -> Unit)?,
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                Modifier
                    .fillMaxWidth(0.85f)
                    .background(
                        Violet.copy(alpha = 0.20f),
                        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    )
                    .border(
                        1.dp,
                        Violet.copy(alpha = 0.35f),
                        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
                    )
                    .padding(14.dp)
            ) {
                Text(
                    turn.question,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (turn.pending) return@Column

        Spacer(Modifier.height(10.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .background(
                    if (turn.failed) Rose.copy(alpha = 0.08f) else InkCard.copy(alpha = 0.75f),
                    RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                )
                .border(
                    1.dp,
                    if (turn.failed) Rose.copy(alpha = 0.3f) else InkBorder,
                    RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp),
                )
                .padding(16.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        null,
                        tint = if (turn.failed) Rose else Violet,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "BOB",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (turn.failed) Rose else Violet,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (!turn.grounded && !turn.failed) {
                        Text(
                            "beyond the lecture",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                MarkdownText(turn.answer, color = MaterialTheme.colorScheme.onSurface)

                if (turn.sources.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "SOURCES · tap to open",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        letterSpacing = 1.3.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    SourceChipRow(turn.sources, onClick = onSource)
                    turn.sources.firstOrNull { it.quote.isNotBlank() }?.let { source ->
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "“${source.quote}”",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextFaint,
                            fontSize = 12.5.sp,
                        )
                    }
                }

                if (turn.failed) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.clickable { onRetry() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            null,
                            tint = Rose,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text("Retry", color = Rose, style = MaterialTheme.typography.labelLarge)
                    }
                } else if (turn.engine.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    EngineBadge(turn.engine)
                }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = {
                Text(
                    if (enabled) "Ask about this lecture…" else "Process a lecture first",
                    color = TextFaint,
                )
            },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(16.dp),
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = InkBorder,
                focusedContainerColor = InkCard.copy(alpha = 0.6f),
                unfocusedContainerColor = InkCard.copy(alpha = 0.6f),
                disabledContainerColor = InkCard.copy(alpha = 0.3f),
            ),
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier
                .size(50.dp)
                .background(
                    if (enabled && value.isNotBlank()) Violet else InkCard,
                    RoundedCornerShape(16.dp),
                )
                .clickable(enabled = enabled && value.isNotBlank()) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                "Send",
                tint = if (enabled && value.isNotBlank()) Color.White else TextFaint,
                modifier = Modifier.size(19.dp),
            )
        }
    }
}
