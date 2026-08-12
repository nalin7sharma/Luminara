package com.luminara.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.FormulaDto
import com.luminara.app.data.NoteSectionDto
import com.luminara.app.data.ObservationDto
import com.luminara.app.data.SourceDto
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.Ink
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet

/**
 * Rendering for the parts of a lecture that appear on more than one tab.
 * Kept here so the Lecture Detail tabs stay about layout, not about markup.
 */

@Composable
fun NoteSectionCard(
    section: NoteSectionDto,
    formulas: List<FormulaDto>,
    modifier: Modifier = Modifier,
    onSource: ((SourceDto) -> Unit)? = null,
) {
    GlassCard(modifier) {
        Text(
            section.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        when (section.type) {
            "text" -> MarkdownText(section.body, color = TextSecondary)

            "concepts" -> section.items.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(16.dp))
                Text(item.heading, style = MaterialTheme.typography.titleSmall, color = Violet)
                Spacer(Modifier.height(4.dp))
                MarkdownText(item.body, color = TextSecondary)
                if (item.sources.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    SourceChipRow(item.sources, onClick = onSource)
                }
            }

            "bullets" -> section.items.forEach { item ->
                Row(Modifier.padding(vertical = 5.dp)) {
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(5.dp)
                            .background(Violet, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        MarkdownText(item.body, color = TextSecondary)
                        if (item.sources.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            SourceChipRow(item.sources, onClick = onSource)
                        }
                    }
                }
            }

            "formulas" -> {
                val list = if (formulas.isNotEmpty()) formulas else section.items.map {
                    FormulaDto(latex = it.latex, plain = it.plain, meaning = it.body)
                }
                list.forEachIndexed { index, formula ->
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    FormulaCard(formula)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Preserved exactly as written on the board — never translated, " +
                        "never turned into a sentence.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }

            "terms" -> section.items.forEach { item ->
                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Text(
                        item.heading,
                        style = MaterialTheme.typography.titleSmall,
                        color = Teal,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(112.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            "links" -> section.items.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Amber.copy(alpha = 0.07f), RoundedCornerShape(14.dp))
                        .border(1.dp, Amber.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Text(
                        item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                    )
                    if (item.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            item.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                    if (item.sources.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        SourceChipRow(item.sources, onClick = onSource)
                    }
                }
            }

            else -> MarkdownText(section.body, color = TextSecondary)
        }
    }
}

@Composable
fun ObservationCard(observation: ObservationDto, modifier: Modifier = Modifier) {
    GlassCard(modifier, accent = Violet) {
        Text(
            observation.kind.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Violet,
            letterSpacing = 1.3.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            observation.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            observation.description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
        )
        if (observation.relationships.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "STRUCTURE READ FROM THE DIAGRAM",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(8.dp))
            observation.relationships.forEach { relation ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier
                            .padding(top = 7.dp)
                            .size(5.dp)
                            .background(Teal, RoundedCornerShape(50))
                    )
                    Spacer(Modifier.width(11.dp))
                    Text(
                        relation,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                    )
                }
            }
        }
        if (observation.extractedText.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                observation.extractedText,
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.5.sp,
            )
        }
    }
}

/** The verbatim OCR block, shown on the Visuals tab. */
@Composable
fun BoardTextCard(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Ink.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .border(1.dp, Amber.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = Amber,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            lineHeight = 21.sp,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
        Spacer(Modifier.height(12.dp))
        SourceChip(SourceDto(type = "whiteboard", ref = "Whiteboard"))
    }
}

/** Small stat used in the Overview header. */
@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Ink.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.Center,
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
