package com.luminara.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.luminara.app.data.LuminaraApi
import com.luminara.app.data.ScriptEntryDto
import com.luminara.app.data.SearchHitDto
import com.luminara.app.data.SourceDto
import com.luminara.app.data.languageOption
import com.luminara.app.ui.components.BoardTextCard
import com.luminara.app.ui.components.EmptyState
import com.luminara.app.ui.components.EngineBadge
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.FormulaCard
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.MarkdownText
import com.luminara.app.ui.components.NoteSectionCard
import com.luminara.app.ui.components.ObservationCard
import com.luminara.app.ui.components.Pill
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.components.SourceChip
import com.luminara.app.ui.components.StatTile
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.Ink
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

enum class DetailTab(val key: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    SCRIPT("script", "Script"),
    NOTES("notes", "Notes"),
    VISUALS("visuals", "Visuals"),
    FORMULAS("formulas", "Formulas"),
    BOB("bob", "Ask BOB"),
    SOURCES("sources", "Sources");

    companion object {
        fun from(key: String?) = entries.firstOrNull { it.key == key } ?: OVERVIEW
    }
}

/**
 * One lecture, one screen. Everything the student needs to review a class lives
 * behind these tabs, and every piece of evidence can be opened from any other —
 * a source chip on a note jumps to the moment in the script it came from.
 */
@Composable
fun LectureDetailScreen(
    state: UiState,
    initialTab: DetailTab,
    onBack: () -> Unit,
    onLanguage: (String) -> Unit,
    onAsk: (String) -> Unit,
    onRetryAsk: () -> Unit,
    onClearChat: () -> Unit,
    onDownload: () -> Unit,
    onOpenPack: () -> Unit,
    onSharePack: () -> Unit,
    onDismissPack: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onClearSearch: () -> Unit,
    onReprocess: (String) -> Unit,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var highlight by remember { mutableStateOf<String?>(null) }
    val lecture = state.lecture

    fun openSource(source: SourceDto) {
        when (source.type.lowercase()) {
            "speech" -> {
                highlight = source.ref
                tab = DetailTab.SCRIPT
            }
            "formula" -> tab = DetailTab.FORMULAS
            "diagram", "graph", "chart", "whiteboard", "board", "slide" -> tab = DetailTab.VISUALS
            else -> tab = DetailTab.NOTES
        }
    }

    LuminaraBackground {
        if (lecture == null) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Violet)
                Spacer(Modifier.height(16.dp))
                Text("Loading lecture…", color = TextSecondary)
            }
            return@LuminaraBackground
        }

        Column(Modifier.fillMaxSize()) {
            DetailHeader(
                title = lecture.knowledge.title.ifBlank { lecture.title },
                subtitle = lecture.course.ifBlank { "Lecture" },
                onBack = onBack,
            )

            TabStrip(selected = tab, onSelect = { tab = it; })

            when (tab) {
                DetailTab.OVERVIEW -> OverviewTab(
                    state, overviewScroll, onLanguage, onDownload, onOpenPack, onSharePack,
                    onDismissPack, onReprocess, onAskBob = { tab = DetailTab.BOB },
                )
                DetailTab.SCRIPT -> ScriptTab(state, highlight) { highlight = null }
                DetailTab.NOTES -> NotesTab(state, ::openSource)
                DetailTab.VISUALS -> VisualsTab(state)
                DetailTab.FORMULAS -> FormulasTab(state)
                DetailTab.BOB -> BobChat(
                    state = state,
                    onAsk = onAsk,
                    onRetry = onRetryAsk,
                    onClear = onClearChat,
                    onSource = ::openSource,
                )
                DetailTab.SOURCES -> SourcesTab(
                    state, onSearchQuery, onClearSearch,
                ) { hit -> openSource(SourceDto(type = hit.type, ref = hit.ref)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
private fun DetailHeader(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(16.dp, 48.dp, 16.dp, 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(38.dp)
                .background(InkCard, CircleShape)
                .border(1.dp, InkBorder, CircleShape),
        ) {
            Icon(
                Icons.Filled.ArrowBack,
                "Back",
                tint = TextSecondary,
                modifier = Modifier.size(17.dp),
            )
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 17.sp,
                maxLines = 2,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                fontSize = 12.5.sp,
            )
        }
    }
}

@Composable
private fun TabStrip(selected: DetailTab, onSelect: (DetailTab) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(bottom = 8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DetailTab.entries) { entry ->
            val active = entry == selected
            Text(
                entry.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (active) Color.White else TextSecondary,
                modifier = Modifier
                    .background(
                        if (active) Violet else InkCard.copy(alpha = 0.6f),
                        RoundedCornerShape(50),
                    )
                    .border(
                        1.dp,
                        if (active) Color.Transparent else InkBorder,
                        RoundedCornerShape(50),
                    )
                    .clickable { onSelect(entry) }
                    .padding(horizontal = 15.dp, vertical = 9.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Overview
// ---------------------------------------------------------------------------

@Composable
private fun OverviewTab(
    state: UiState,
    onLanguage: (String) -> Unit,
    onDownload: () -> Unit,
    onOpenPack: () -> Unit,
    onSharePack: () -> Unit,
    onDismissPack: () -> Unit,
    onReprocess: (String) -> Unit,
    onAskBob: () -> Unit,
) {
    val lecture = state.lecture ?: return
    val knowledge = lecture.knowledge

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                if (lecture.durationSec > 0) {
                    StatTile("${lecture.durationSec.toInt()}s", "lecture")
                }
                StatTile("${knowledge.keyConcepts.size}", "concepts")
                StatTile("${lecture.formulas.size}", "formulas")
                StatTile("${lecture.transcript.size}", "segments")
            }
        }

        item {
            SectionLabel("Study language")
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.languages.take(4).forEach { language ->
                    Pill(
                        text = languageOption(language.code).nativeName,
                        selected = state.language == language.code,
                        onClick = { onLanguage(language.code) },
                    )
                }
            }
            if (state.translating) {
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(14.dp),
                        color = Teal,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Translating — formulas are kept untouched",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            } else if (state.language != "en" && lecture.servedLanguage != state.language) {
                Spacer(Modifier.height(9.dp))
                Text(
                    "Showing English — translation was not available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Amber,
                    fontSize = 13.sp,
                )
            }
        }

        if (knowledge.summary.isNotBlank()) {
            item {
                GlassCard {
                    Text(
                        "Summary",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(10.dp))
                    MarkdownText(knowledge.summary, color = TextSecondary)
                }
            }
        }

        if (knowledge.simpleExplanation.isNotBlank()) {
            item {
                GlassCard(accent = Teal) {
                    Text(
                        "In simple words",
                        style = MaterialTheme.typography.titleMedium,
                        color = Teal,
                    )
                    Spacer(Modifier.height(10.dp))
                    MarkdownText(knowledge.simpleExplanation, color = TextSecondary)
                }
            }
        }

        item { StudyPackCard(state, onDownload, onOpenPack, onSharePack, onDismissPack) }

        item {
            Button(
                onClick = onAskBob,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
            ) {
                Icon(Icons.Filled.AutoAwesome, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Ask BOB about this lecture", style = MaterialTheme.typography.labelLarge)
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                lecture.engines["asr"]?.let { EngineBadge(it) }
                lecture.engines["vision"]?.let { EngineBadge(it) }
                lecture.engines["reasoning"]?.let { EngineBadge(it) }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.clickable { onReprocess(lecture.id) }.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    null,
                    tint = TextFaint,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "Process this lecture again",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                )
            }
        }
    }
}

@Composable
private fun StudyPackCard(
    state: UiState,
    onDownload: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    GlassCard(accent = Amber) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Download, null, tint = Amber, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Study pack",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Summary, notes, script, formulas and board — as a PDF",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }

        val saved = state.savedPack
        Spacer(Modifier.height(14.dp))

        if (saved != null) {
            Text(
                "Saved to ${saved.location}",
                style = MaterialTheme.typography.bodyMedium,
                color = Teal,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                saved.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                fontSize = 12.sp,
            )
            if (!saved.isPdf) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Saved as HTML — no local browser was available to render a PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Amber,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button(
                    onClick = onOpen,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber),
                ) { Text("Open", color = Ink, style = MaterialTheme.typography.labelLarge) }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = InkCard),
                ) { Text("Share", color = TextSecondary) }
                IconButton(onClick = onDismiss, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        "Dismiss",
                        tint = TextFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        } else {
            Button(
                onClick = onDownload,
                enabled = !state.downloading && state.lecture?.status == "ready",
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Amber),
            ) {
                if (state.downloading) {
                    CircularProgressIndicator(
                        Modifier.size(16.dp),
                        color = Ink,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Preparing…", color = Ink, style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(
                        "Download study pack",
                        color = Ink,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            state.downloadError?.let {
                Spacer(Modifier.height(10.dp))
                ErrorBanner(it, onRetry = onDownload)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Script
// ---------------------------------------------------------------------------

@Composable
private fun ScriptTab(state: UiState, highlight: String?, onHighlightConsumed: () -> Unit) {
    val script = state.script
    var filter by remember { mutableStateOf("") }
    var openEntry by remember { mutableStateOf<String?>(highlight) }
    val listState = rememberLazyListState()

    LaunchedEffect(highlight, script?.entryCount) {
        val target = highlight ?: return@LaunchedEffect
        val entries = script?.entries ?: return@LaunchedEffect
        val index = entries.indexOfFirst { it.timecode == target }
        if (index >= 0) {
            openEntry = target
            listState.animateScrollToItem(index + 1)   // +1 for the search field row
        }
        onHighlightConsumed()
    }

    if (state.scriptLoading && script == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Violet)
        }
        return
    }
    if (script == null || script.entries.isEmpty()) {
        EmptyState(
            Icons.Filled.Search,
            "No script yet",
            "The script is built from the lecture's own transcript once it has been processed.",
        )
        return
    }

    val entries = remember(filter, script) {
        if (filter.isBlank()) script.entries
        else script.entries.filter { it.text.contains(filter, ignoreCase = true) }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            SearchField(
                value = filter,
                placeholder = "Search the script…",
                onValueChange = { filter = it },
                onClear = { filter = "" },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (filter.isBlank()) {
                    "${script.entryCount} moments · ${script.durationSec.toInt()}s"
                } else {
                    "${entries.size} of ${script.entryCount} moments"
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
            )
            Spacer(Modifier.height(6.dp))
        }

        items(entries) { entry ->
            ScriptLine(
                entry = entry,
                expanded = openEntry == entry.timecode,
                onClick = { openEntry = if (openEntry == entry.timecode) null else entry.timecode },
            )
        }

        if (script.boardOnly.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                SectionLabel("Written on the board, never said aloud")
                Spacer(Modifier.height(10.dp))
                GlassCard(accent = Amber) {
                    script.boardOnly.forEachIndexed { index, board ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        Text(
                            board.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = Amber,
                            fontFamily = FontFamily.Monospace,
                        )
                        if (board.detail.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                board.detail,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScriptLine(entry: ScriptEntryDto, expanded: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                if (expanded) Violet.copy(alpha = 0.12f) else Color.Transparent,
                shape,
            )
            .border(
                1.dp,
                if (expanded) Violet.copy(alpha = 0.35f) else Color.Transparent,
                shape,
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                entry.timecode,
                style = MaterialTheme.typography.labelSmall,
                color = Teal,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(InkCard, RoundedCornerShape(6.dp))
                    .border(1.dp, InkBorder, RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.sp,
                )
                if (entry.hasBoardMoment) {
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).background(Amber, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "board activity at this moment",
                            style = MaterialTheme.typography.labelSmall,
                            color = Amber,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(expanded && entry.related.isNotEmpty()) {
            Column(Modifier.padding(start = 56.dp, top = 10.dp)) {
                entry.related.forEach { relation ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                        Text(
                            when (relation.kind) {
                                "concept" -> "Concept"
                                "point" -> "Key point"
                                else -> "Board"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (relation.kind == "board") Amber else Violet,
                            modifier = Modifier.width(66.dp),
                        )
                        Text(
                            listOf(relation.label, relation.detail)
                                .filter { it.isNotBlank() }
                                .joinToString(" — "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Notes / Visuals / Formulas
// ---------------------------------------------------------------------------

@Composable
private fun NotesTab(state: UiState, onSource: (SourceDto) -> Unit) {
    val lecture = state.lecture ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (lecture.notes.sections.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.Search,
                    "No notes yet",
                    "Notes appear once the lecture has been processed.",
                )
            }
        }
        items(lecture.notes.sections) { section ->
            NoteSectionCard(section, lecture.formulas, onSource = onSource)
        }
    }
}

@Composable
private fun VisualsTab(state: UiState) {
    val lecture = state.lecture ?: return
    val diagrams = lecture.observations.filter { it.kind != "board_text" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (lecture.imageUrl != null) {
            item {
                GlassCard {
                    AsyncImage(
                        model = LuminaraApi.mediaUrl(lecture.imageUrl),
                        contentDescription = "Classroom whiteboard",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, InkBorder, RoundedCornerShape(12.dp)),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "The classroom image the vision model analysed — read independently " +
                            "of what the professor said.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 12.5.sp,
                    )
                }
            }
        }

        if (lecture.boardText.isNotBlank()) {
            item {
                SectionLabel("Text extracted from the board (OCR)")
                Spacer(Modifier.height(9.dp))
                BoardTextCard(lecture.boardText)
            }
        }

        if (diagrams.isEmpty() && lecture.imageUrl == null) {
            item {
                EmptyState(
                    Icons.Filled.ImageSearch,
                    "No board captured",
                    "This lecture was processed from audio only.",
                )
            }
        }

        items(diagrams) { observation -> ObservationCard(observation) }
    }
}

@Composable
private fun FormulasTab(state: UiState) {
    val lecture = state.lecture ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (lecture.formulas.isEmpty()) {
            item {
                EmptyState(
                    Icons.Filled.Search,
                    "No formulas captured",
                    "Nothing formula-shaped was found on this lecture's board.",
                )
            }
        }
        items(lecture.formulas) { formula -> FormulaCard(formula) }
        if (lecture.formulas.isNotEmpty()) {
            item {
                Text(
                    "Formulas are never sent to the translator. They are reproduced exactly " +
                        "as they were written on the board.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Sources (search)
// ---------------------------------------------------------------------------

@Composable
private fun SourcesTab(
    state: UiState,
    onQuery: (String) -> Unit,
    onClear: () -> Unit,
    onOpen: (SearchHitDto) -> Unit,
) {
    val results = state.search

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 36.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SearchField(
                value = state.searchQuery,
                placeholder = "Search this lecture…",
                onValueChange = onQuery,
                onClear = onClear,
            )
            Spacer(Modifier.height(8.dp))
            if (state.searchQuery.isBlank()) {
                Text(
                    "Ask where something came from — \"time complexity\", " +
                        "\"where was the formula written\", \"binary search tree\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 13.sp,
                )
            } else if (state.searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        Modifier.size(13.dp),
                        color = Violet,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        "Searching the lecture…",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                    )
                }
            } else if (results != null) {
                Text(
                    "${results.count} ${if (results.count == 1) "match" else "matches"}" +
                        if (results.terms.isNotEmpty()) {
                            " for ${results.terms.joinToString(", ")}"
                        } else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                )
            }
            Spacer(Modifier.height(4.dp))
        }

        if (results != null && results.results.isEmpty() && !state.searching) {
            item {
                EmptyState(
                    Icons.Filled.Search,
                    "Nothing found",
                    "This lecture does not mention that. Try asking BOB instead — it can " +
                        "reason beyond an exact word match.",
                )
            }
        }

        items(results?.results ?: emptyList()) { hit -> SearchHitCard(hit) { onOpen(hit) } }
    }
}

@Composable
private fun SearchHitCard(hit: SearchHitDto, onClick: () -> Unit) {
    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceChip(SourceDto(type = hit.type, ref = hit.ref))
            Spacer(Modifier.weight(1f))
            Text(
                "open",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
            )
        }
        if (hit.title.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                hit.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = if (hit.type == "formula") FontFamily.Monospace else null,
            )
        }
        if (hit.text.isNotBlank()) {
            Spacer(Modifier.height(5.dp))
            Text(
                hit.text,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.5.sp,
            )
        }
        if (hit.relationships.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            hit.relationships.forEach {
                Text(
                    "• $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextFaint, fontSize = 14.sp) },
        leadingIcon = {
            Icon(Icons.Filled.Search, null, tint = TextFaint, modifier = Modifier.size(18.dp))
        },
        trailingIcon = {
            if (value.isNotBlank()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        "Clear",
                        tint = TextFaint,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        },
        singleLine = true,
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
