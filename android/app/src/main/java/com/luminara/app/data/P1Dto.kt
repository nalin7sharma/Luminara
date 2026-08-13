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

/** The one board event a script line is the primary moment for. */
@Serializable
data class BoardMomentDto(
    val id: String = "",
    val kind: String = "board",      // formula | diagram | window
    val label: String = "",
    val detail: String = "",
    @SerialName("also_at") val alsoAt: List<String> = emptyList(),
)

/** The same board event, cited again on a different line. */
@Serializable
data class BoardReferenceDto(
    val id: String = "",
    val label: String = "",
    @SerialName("primary_timecode") val primaryTimecode: String = "",
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
    @SerialName("board_moment") val boardMoment: BoardMomentDto? = null,
    @SerialName("board_references") val boardReferences: List<BoardReferenceDto> = emptyList(),
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
    @SerialName("board_moments") val boardMoments: List<BoardMomentSummaryDto> = emptyList(),
    @SerialName("board_only") val boardOnly: List<BoardOnlyDto> = emptyList(),
    @SerialName("served_language") val servedLanguage: String = "en",
)

@Serializable
data class BoardMomentSummaryDto(
    val id: String = "",
    val kind: String = "board",
    val label: String = "",
    val detail: String = "",
    @SerialName("primary_timecode") val primaryTimecode: String = "",
    @SerialName("also_at") val alsoAt: List<String> = emptyList(),
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

// --- Live Lecture -------------------------------------------------------

@Serializable
data class LiveStartDto(
    @SerialName("lecture_id") val lectureId: String = "",
    @SerialName("chunk_seconds") val chunkSeconds: Int = 9,
    val language: String = "en",
    @SerialName("sample_rate") val sampleRate: Int = 16000,
)

@Serializable
data class LiveChunkDto(
    val ok: Boolean = false,
    @SerialName("chunk_index") val chunkIndex: Int = 0,
    val timecode: String = "",
    val start: Double = 0.0,
    @SerialName("chunk_seconds") val chunkSeconds: Double = 0.0,
    val transcript: String = "",
    val translation: String = "",
    val words: Int = 0,
    @SerialName("asr_ms") val asrMs: Long = 0,
    @SerialName("translate_ms") val translateMs: Long = 0,
    @SerialName("total_ms") val totalMs: Long = 0,
    @SerialName("behind_ms") val behindMs: Long = 0,
    val engines: Map<String, String> = emptyMap(),
    val error: String = "",
)

@Serializable
data class LiveFinishDto(
    @SerialName("lecture_id") val lectureId: String = "",
    val status: String = "processing",
    val segments: Int = 0,
    val error: String = "",
)

/** What the vision pass found in one frame of the board. */
@Serializable
data class LiveBoardDto(
    val ok: Boolean = false,
    @SerialName("capture_id") val captureId: Int = 0,
    val timecode: String = "",
    @SerialName("at_seconds") val atSeconds: Double = 0.0,
    val headline: String = "",
    val useful: Boolean = false,
    val auto: Boolean = false,
    @SerialName("board_text") val boardText: String = "",
    @SerialName("text_lines") val textLines: Int = 0,
    val formulas: List<BoardFormulaDto> = emptyList(),
    val summary: String = "",
    val engine: String = "",
    val ms: Long = 0,
    val error: String = "",
)

@Serializable
data class BoardFormulaDto(
    val latex: String = "",
    val plain: String = "",
    val meaning: String = "",
)

@Serializable
data class SearchResponseDto(
    val query: String = "",
    val terms: List<String> = emptyList(),
    val count: Int = 0,
    val results: List<SearchHitDto> = emptyList(),
)
