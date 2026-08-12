package com.luminara.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire types for the P1 features: lecture script and in-lecture search. */

@Serializable
data class ScriptRelationDto(
    val kind: String = "",      // concept | point | board
    val label: String = "",
    val detail: String = "",
)

@Serializable
data class ScriptEntryDto(
    val timecode: String = "",
    val start: Double = 0.0,
    val end: Double = 0.0,
    val text: String = "",
    val speaker: String = "Teacher",
    val related: List<ScriptRelationDto> = emptyList(),
    @SerialName("has_board_moment") val hasBoardMoment: Boolean = false,
)

@Serializable
data class BoardOnlyDto(
    val label: String = "",
    val detail: String = "",
    @SerialName("source_ref") val sourceRef: String = "Whiteboard",
)

@Serializable
data class ScriptDto(
    val language: String = "en",
    val title: String = "",
    @SerialName("duration_sec") val durationSec: Double = 0.0,
    @SerialName("entry_count") val entryCount: Int = 0,
    val entries: List<ScriptEntryDto> = emptyList(),
    @SerialName("board_only") val boardOnly: List<BoardOnlyDto> = emptyList(),
    @SerialName("served_language") val servedLanguage: String = "en",
)

@Serializable
data class SearchHitDto(
    val type: String = "speech",     // speech | whiteboard | diagram | formula | note
    val ref: String = "",
    val start: Double = 0.0,
    val title: String = "",
    val text: String = "",
    val score: Double = 0.0,
    val relationships: List<String> = emptyList(),
    val formula: FormulaDto? = null,
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class SearchResponseDto(
    val query: String = "",
    val terms: List<String> = emptyList(),
    val count: Int = 0,
    val results: List<SearchHitDto> = emptyList(),
)
