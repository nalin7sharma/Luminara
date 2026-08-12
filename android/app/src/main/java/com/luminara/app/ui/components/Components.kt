package com.luminara.app.ui.components

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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.FormulaDto
import com.luminara.app.data.SourceDto
import com.luminara.app.data.StageDto
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.Ink
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Rose
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet

/** The app's ground: deep navy with two soft light sources. */
@Composable
fun LuminaraBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .background(
                Brush.radialGradient(
                    colors = listOf(Violet.copy(alpha = 0.18f), Color.Transparent),
                    radius = 900f,
                )
            )
    ) { content() }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.75f), shape)
            .border(1.dp, accent?.copy(alpha = 0.35f) ?: InkBorder, shape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(18.dp),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = TextFaint,
        letterSpacing = 1.4.sp,
        modifier = modifier,
    )
}

// ---------------------------------------------------------------------------
// evidence
// ---------------------------------------------------------------------------

private fun sourceIcon(type: String): ImageVector = when (type.lowercase()) {
    "speech" -> Icons.Filled.RecordVoiceOver
    "whiteboard", "board", "slide" -> Icons.Filled.Image
    "diagram", "graph", "chart" -> Icons.Filled.Schema
    "formula" -> Icons.Filled.Functions
    else -> Icons.Filled.AutoAwesome
}

private fun sourceColor(type: String): Color = when (type.lowercase()) {
    "speech" -> Teal
    "whiteboard", "board", "slide" -> Amber
    "diagram", "graph", "chart" -> Violet
    "formula" -> Rose
    else -> TextSecondary
}

/** "Where did this come from?" — the evidence chip used across every screen.
 *  Give it an `onClick` and it becomes navigation into that evidence. */
@Composable
fun SourceChip(
    source: SourceDto,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val color = sourceColor(source.type)
    val label = buildString {
        append(source.type.replaceFirstChar { it.uppercase() })
        if (source.ref.isNotBlank() && !source.ref.equals(source.type, ignoreCase = true)) {
            append(" · ")
            append(source.ref)
        }
    }
    Row(
        modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
            .border(1.dp, color.copy(alpha = 0.30f), RoundedCornerShape(50))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(sourceIcon(source.type), null, tint = color, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
fun SourceChipRow(
    sources: List<SourceDto>,
    modifier: Modifier = Modifier,
    onClick: ((SourceDto) -> Unit)? = null,
) {
    if (sources.isEmpty()) return
    LazyRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sources) { source ->
            SourceChip(source, onClick = onClick?.let { handler -> { handler(source) } })
        }
    }
}

@Composable
fun EngineBadge(engine: String, modifier: Modifier = Modifier) {
    if (engine.isBlank()) return
    val live = !engine.startsWith("offline") && !engine.startsWith("local") && engine != "none"
    val color = if (live) Teal else Amber
    Row(
        modifier
            .background(color.copy(alpha = 0.10f), RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(engine, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

/**
 * A formula is shown exactly as it was written on the board — never reflowed,
 * never translated, never turned into a sentence.
 */
@Composable
fun FormulaCard(formula: FormulaDto, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.85f), shape)
            .border(1.dp, Rose.copy(alpha = 0.30f), shape)
            .padding(16.dp)
    ) {
        Text(
            formula.plain.ifBlank { formula.latex },
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 30.sp,
            ),
            color = Rose,
        )
        if (formula.meaning.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                formula.meaning,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
        Spacer(Modifier.height(12.dp))
        SourceChip(SourceDto(type = "formula", ref = formula.sourceRef))
    }
}

// ---------------------------------------------------------------------------
// pipeline stages
// ---------------------------------------------------------------------------

@Composable
fun StageRow(stage: StageDto, modifier: Modifier = Modifier) {
    val (icon, color) = when (stage.status) {
        "done" -> Icons.Filled.CheckCircle to Teal
        "running" -> Icons.Filled.AutoAwesome to Violet
        "failed" -> Icons.Filled.ErrorOutline to Rose
        "skipped" -> Icons.Filled.RemoveCircleOutline to TextFaint
        else -> Icons.Filled.CheckCircle to TextFaint.copy(alpha = 0.35f)
    }
    Row(modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            if (stage.status == "running") {
                CircularProgressIndicator(
                    Modifier.size(18.dp),
                    color = Violet,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(icon, null, tint = color, modifier = Modifier.size(19.dp))
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stage.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (stage.status == "pending") TextFaint else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (stage.elapsedMs > 0 && stage.status != "pending") {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        formatElapsed(stage.elapsedMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                    )
                }
            }
            if (stage.detail.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    stage.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
            if (stage.engine.isNotBlank() && stage.status != "pending") {
                Spacer(Modifier.height(6.dp))
                EngineBadge(stage.engine)
            }
        }
    }
}

fun formatElapsed(ms: Long): String =
    if (ms < 1000) "${ms}ms" else String.format("%.1fs", ms / 1000.0)

// ---------------------------------------------------------------------------
// states
// ---------------------------------------------------------------------------

@Composable
fun ErrorBanner(message: String, onRetry: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .background(Rose.copy(alpha = 0.10f), shape)
            .border(1.dp, Rose.copy(alpha = 0.30f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.ErrorOutline, null, tint = Rose, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, null, tint = Rose, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry", color = Rose, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = TextFaint, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(14.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = TextFaint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ThinkingDots(label: String = "BOB is reading the lecture", modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "thinking")
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = index * 180),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$index",
            )
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .size(7.dp)
                    .alpha(alpha)
                    .background(Violet, CircleShape)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
    }
}

// ---------------------------------------------------------------------------
// lightweight markdown
// ---------------------------------------------------------------------------

/** Inline **bold**, *italic* and `code`. */
fun inlineMarkdown(raw: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < raw.length) {
        when {
            raw.startsWith("**", i) -> {
                val end = raw.indexOf("**", i + 2)
                if (end == -1) { append(raw.substring(i)); i = raw.length }
                else {
                    withStyleSafe(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(raw.substring(i + 2, end))
                    }
                    i = end + 2
                }
            }
            raw[i] == '`' -> {
                val end = raw.indexOf('`', i + 1)
                if (end == -1) { append(raw.substring(i)); i = raw.length }
                else {
                    withStyleSafe(
                        SpanStyle(fontFamily = FontFamily.Monospace, color = Amber)
                    ) { append(raw.substring(i + 1, end)) }
                    i = end + 1
                }
            }
            raw[i] == '*' -> {
                val end = raw.indexOf('*', i + 1)
                if (end == -1) { append(raw.substring(i)); i = raw.length }
                else {
                    withStyleSafe(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(raw.substring(i + 1, end))
                    }
                    i = end + 1
                }
            }
            else -> {
                val next = raw.indexOfAny(charArrayOf('*', '`'), i)
                if (next == -1) { append(raw.substring(i)); i = raw.length }
                else { append(raw.substring(i, next)); i = next }
            }
        }
    }
}

private inline fun androidx.compose.ui.text.AnnotatedString.Builder.withStyleSafe(
    style: SpanStyle,
    block: () -> Unit,
) {
    val marker = pushStyle(style)
    block()
    pop(marker)
}

/** Renders the small subset of markdown BOB and the notes actually emit. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Column(modifier) {
        text.split("\n").forEach { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() -> Spacer(Modifier.height(8.dp))

                trimmed.startsWith("### ") || trimmed.startsWith("## ") -> Text(
                    inlineMarkdown(trimmed.substringAfter("# ").trim()),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> Row(
                    Modifier.padding(vertical = 3.dp)
                ) {
                    Text("•", style = style, color = Violet)
                    Spacer(Modifier.width(10.dp))
                    Text(inlineMarkdown(trimmed.drop(2)), style = style, color = color)
                }

                trimmed.length > 2 && trimmed[0].isDigit() && trimmed[1] == '.' -> Row(
                    Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        trimmed.takeWhile { it != '.' } + ".",
                        style = style.copy(fontWeight = FontWeight.Bold),
                        color = Violet,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        inlineMarkdown(trimmed.substringAfter(".").trim()),
                        style = style,
                        color = color,
                    )
                }

                else -> Text(
                    inlineMarkdown(trimmed),
                    style = style,
                    color = color,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        modifier = modifier.clickable { onClick() },
        shape = shape,
        color = if (selected) Violet else InkCard,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, InkBorder),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else TextSecondary,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
        )
    }
}
