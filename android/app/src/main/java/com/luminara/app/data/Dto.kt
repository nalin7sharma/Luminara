package com.luminara.app.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire types. Every field carries a default so a backend running in a degraded
 * mode (no vision, no translation) still deserialises into a usable screen
 * rather than throwing.
 */

@Serializable
data class LanguageDto(val code: String = "en", val name: String = "English")

@Serializable
data class BobStatusDto(
    val configured: Boolean = false,
    val protocol: String? = null,
    val base: String? = null,
    val model: String? = null,
)

@Serializable
data class ConfigDto(
    val languages: List<LanguageDto> = emptyList(),
    @SerialName("demo_available") val demoAvailable: Boolean = false,
    @SerialName("live_ai") val liveAi: Boolean = false,
    val bob: BobStatusDto = BobStatusDto(),
)

@Serializable
data class SourceDto(
    val type: String = "speech",
    val ref: String = "",
    val quote: String = "",
)

@Serializable
data class StageDto(
    val key: String = "",
    val label: String = "",
    val status: String = "pending",
    val detail: String = "",
    val engine: String = "",
    @SerialName("elapsed_ms") val elapsedMs: Long = 0,
    val ordinal: Int = 0,
)

@Serializable
data class StatusDto(
    @SerialName("lecture_id") val lectureId: String = "",
    val status: String = "created",
    val title: String = "",
    val error: String = "",
    val stages: List<StageDto> = emptyList(),
    val progress: Float = 0f,
    val current: String? = null,
)

@Serializable
data class ConceptDto(
    val name: String = "",
    val explanation: String = "",
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class PointDto(
    val text: String = "",
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class TermDto(
    val term: String = "",
    val definition: String = "",
    @SerialName("keep_untranslated") val keepUntranslated: Boolean = true,
)

@Serializable
data class FormulaDto(
    val latex: String = "",
    val plain: String = "",
    val meaning: String = "",
    @SerialName("source_ref") val sourceRef: String = "Whiteboard",
)

@Serializable
data class VisualExplanationDto(
    val title: String = "",
    val explanation: String = "",
    @SerialName("source_ref") val sourceRef: String = "Whiteboard",
)

@Serializable
data class ObservationDto(
    val kind: String = "diagram",
    val title: String = "",
    val description: String = "",
    @SerialName("extracted_text") val extractedText: String = "",
    val relationships: List<String> = emptyList(),
    @SerialName("source_ref") val sourceRef: String = "Whiteboard",
)

@Serializable
data class ModalityLinkDto(
    val claim: String = "",
    @SerialName("speech_ref") val speechRef: String = "",
    @SerialName("visual_ref") val visualRef: String = "",
    @SerialName("why_it_matters") val whyItMatters: String = "",
)

@Serializable
data class TranscriptSegmentDto(
    val start: Double = 0.0,
    val end: Double = 0.0,
    val text: String = "",
    val timecode: String = "",
)

@Serializable
data class KnowledgeDto(
    val title: String = "",
    val topic: String = "",
    val summary: String = "",
    @SerialName("key_concepts") val keyConcepts: List<ConceptDto> = emptyList(),
    @SerialName("important_points") val importantPoints: List<PointDto> = emptyList(),
    @SerialName("technical_terms") val technicalTerms: List<TermDto> = emptyList(),
    val formulas: List<FormulaDto> = emptyList(),
    @SerialName("visual_explanations") val visualExplanations: List<VisualExplanationDto> = emptyList(),
    @SerialName("simple_explanation") val simpleExplanation: String = "",
    @SerialName("modality_links") val modalityLinks: List<ModalityLinkDto> = emptyList(),
    @SerialName("quiz_seeds") val quizSeeds: List<String> = emptyList(),
    @SerialName("board_text") val boardText: String = "",
    @SerialName("visual_observations") val visualObservations: List<ObservationDto> = emptyList(),
    val engines: Map<String, String> = emptyMap(),
    val language: String = "en",
)

@Serializable
data class NoteItemDto(
    val heading: String = "",
    val body: String = "",
    val note: String = "",
    val latex: String = "",
    val plain: String = "",
    val preserved: Boolean = false,
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class NoteSectionDto(
    val key: String = "",
    val title: String = "",
    val type: String = "text",
    val body: String = "",
    val items: List<NoteItemDto> = emptyList(),
    val sources: List<SourceDto> = emptyList(),
)

@Serializable
data class NotesDto(
    val language: String = "en",
    val sections: List<NoteSectionDto> = emptyList(),
)

@Serializable
data class LectureSummaryDto(
    val id: String = "",
    val title: String = "",
    val topic: String = "",
    val course: String = "",
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String = "",
    val published: Boolean = false,
    val status: String = "created",
    val engine: String = "",
    val language: String = "en",
    @SerialName("source_type") val sourceType: String = "demo",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("duration_sec") val durationSec: Double = 0.0,
    @SerialName("concept_count") val conceptCount: Int = 0,
    @SerialName("formula_count") val formulaCount: Int = 0,
    @SerialName("has_visuals") val hasVisuals: Boolean = false,
    val error: String = "",
)

@Serializable
data class LectureDto(
    val id: String = "",
    val title: String = "",
    val course: String = "",
    val status: String = "created",
    val engine: String = "",
    val language: String = "en",
    @SerialName("source_type") val sourceType: String = "demo",
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_name") val className: String = "",
    val published: Boolean = false,
    @SerialName("owner_id") val ownerId: String? = null,
    @SerialName("processed_at") val processedAt: String? = null,
    @SerialName("duration_sec") val durationSec: Double = 0.0,
    val error: String = "",
    @SerialName("requested_language") val requestedLanguage: String = "en",
    @SerialName("served_language") val servedLanguage: String = "en",
    @SerialName("translation_available") val translationAvailable: Boolean = true,
    @SerialName("available_languages") val availableLanguages: List<String> = emptyList(),
    val knowledge: KnowledgeDto = KnowledgeDto(),
    val notes: NotesDto = NotesDto(),
    val transcript: List<TranscriptSegmentDto> = emptyList(),
    val formulas: List<FormulaDto> = emptyList(),
    val observations: List<ObservationDto> = emptyList(),
    @SerialName("board_text") val boardText: String = "",
    val stages: List<StageDto> = emptyList(),
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("audio_url") val audioUrl: String? = null,
    val engines: Map<String, String> = emptyMap(),
)

@Serializable
data class CreateLectureResponse(
    @SerialName("lecture_id") val lectureId: String = "",
    val status: String = "created",
    val cached: Boolean = false,
)

@Serializable
data class AskResponseDto(
    val id: Int = 0,
    val answer: String = "",
    val sources: List<SourceDto> = emptyList(),
    val grounded: Boolean = true,
    @SerialName("follow_ups") val followUps: List<String> = emptyList(),
    val intent: String = "qa",
    val engine: String = "",
    val error: String = "",
)

@Serializable
data class SuggestionsDto(val suggestions: List<String> = emptyList())

@Serializable
data class LectureListDto(val lectures: List<LectureSummaryDto> = emptyList())

@Serializable
data class TranslateResponseDto(
    val language: String = "en",
    val cached: Boolean = false,
    val engine: String = "",
)

@Serializable
data class ChatHistoryDto(val messages: List<ChatMessageDto> = emptyList())

@Serializable
data class ChatMessageDto(
    val id: Int = 0,
    val question: String = "",
    val answer: String = "",
    val intent: String = "qa",
    val language: String = "en",
    val engine: String = "",
    val sources: List<SourceDto> = emptyList(),
)
