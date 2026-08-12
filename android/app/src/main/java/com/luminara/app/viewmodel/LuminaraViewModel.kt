package com.luminara.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminara.app.data.ApiResult
import com.luminara.app.data.ConfigDto
import com.luminara.app.data.LectureDto
import com.luminara.app.data.LectureSummaryDto
import com.luminara.app.data.LuminaraApi
import com.luminara.app.data.Prefs
import com.luminara.app.data.SavedPack
import com.luminara.app.data.ScriptDto
import com.luminara.app.data.SearchResponseDto
import com.luminara.app.data.SourceDto
import com.luminara.app.data.StatusDto
import com.luminara.app.data.StudyPackSaver
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatTurn(
    val question: String,
    val answer: String = "",
    val sources: List<SourceDto> = emptyList(),
    val followUps: List<String> = emptyList(),
    val engine: String = "",
    val intent: String = "qa",
    val grounded: Boolean = true,
    val pending: Boolean = false,
    val failed: Boolean = false,
)

data class UiState(
    val config: ConfigDto? = null,
    val connectionError: String? = null,
    val checkingConnection: Boolean = true,
    val language: String = "en",
    val lectures: List<LectureSummaryDto> = emptyList(),
    val activeId: String? = null,
    val status: StatusDto? = null,
    val lecture: LectureDto? = null,
    val loadingLecture: Boolean = false,
    val translating: Boolean = false,
    val error: String? = null,
    val chat: List<ChatTurn> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val bobThinking: Boolean = false,
    val onboarded: Boolean = true,

    // --- P1: script, search, study pack ---
    val script: ScriptDto? = null,
    val scriptLoading: Boolean = false,
    val searchQuery: String = "",
    val search: SearchResponseDto? = null,
    val searching: Boolean = false,
    val downloading: Boolean = false,
    val savedPack: SavedPack? = null,
    val downloadError: String? = null,
) {
    val languages get() = config?.languages.orEmpty()
    val liveAi get() = config?.liveAi == true

    /** Best cached demo lecture: fully processed by a live engine, newest first. */
    val readyDemo: LectureSummaryDto?
        get() = lectures.firstOrNull {
            it.sourceType == "demo" && it.status == "ready" && it.engine.isNotBlank() &&
                !it.engine.startsWith("local") && !it.engine.startsWith("none")
        }

    val readyLectures get() = lectures.filter { it.status == "ready" }
}

/** Hosts tried automatically when the configured backend does not answer. */
private val BACKEND_CANDIDATES = listOf(
    "http://10.0.2.2:8000",   // Android emulator -> host machine
    "http://127.0.0.1:8000",  // physical device with `adb reverse tcp:8000 tcp:8000`
)

class LuminaraViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        UiState(language = prefs.language, onboarded = prefs.onboarded)
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        LuminaraApi.baseUrl = prefs.baseUrl
        refresh()
    }

    /** Called once from the welcome flow; the choice persists across restarts. */
    fun completeOnboarding(languageCode: String) {
        prefs.language = languageCode
        prefs.onboarded = true
        _state.update { it.copy(language = languageCode, onboarded = true) }
    }

    // -- connection / config ----------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(checkingConnection = true) }
            var result = LuminaraApi.config()

            // The right host depends on how the app is being run: 10.0.2.2 from an
            // emulator, 127.0.0.1 when `adb reverse` forwards over USB. Rather than
            // making the student care, probe the known candidates once.
            if (result is ApiResult.Err) {
                for (candidate in BACKEND_CANDIDATES) {
                    if (candidate == LuminaraApi.baseUrl) continue
                    val previous = LuminaraApi.baseUrl
                    LuminaraApi.baseUrl = candidate
                    val probe = LuminaraApi.config()
                    if (probe is ApiResult.Ok) {
                        prefs.baseUrl = candidate
                        result = probe
                        break
                    }
                    LuminaraApi.baseUrl = previous
                }
            }

            when (val res = result) {
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        config = res.value,
                        connectionError = null,
                        checkingConnection = false,
                    )
                }
                is ApiResult.Err -> _state.update {
                    it.copy(connectionError = res.message, checkingConnection = false)
                }
            }
            loadLectures()
        }
    }

    fun setBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        LuminaraApi.baseUrl = clean
        prefs.baseUrl = clean
        refresh()
    }

    fun loadLectures() {
        viewModelScope.launch {
            LuminaraApi.lectures().valueOrNull?.let { list ->
                _state.update { it.copy(lectures = list.lectures) }
            }
        }
    }

    fun setLanguage(code: String) {
        prefs.language = code
        _state.update { it.copy(language = code) }
        val lecture = _state.value.lecture ?: return
        if (lecture.status == "ready" && !lecture.availableLanguages.contains(code)) {
            translateActive(code)
        } else {
            loadLecture(lecture.id)
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    // -- lecture lifecycle -------------------------------------------------

    /**
     * @param fresh true = run the pipeline again from the raw audio and image;
     *              false = reuse an already-processed demo lecture if present.
     */
    fun startDemo(fresh: Boolean, onProcessing: (String) -> Unit, onReady: (String) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            when (val created = LuminaraApi.createDemo(_state.value.language, reuse = !fresh)) {
                is ApiResult.Err -> _state.update { it.copy(error = created.message) }
                is ApiResult.Ok -> {
                    val id = created.value.lectureId
                    _state.update { it.copy(activeId = id, lecture = null, chat = emptyList()) }
                    if (created.value.cached) {
                        loadLecture(id)
                        onReady(id)
                    } else {
                        when (val started = LuminaraApi.process(id)) {
                            is ApiResult.Err -> _state.update { it.copy(error = started.message) }
                            is ApiResult.Ok -> {
                                _state.update { it.copy(status = started.value) }
                                onProcessing(id)
                                pollUntilDone(id)
                            }
                        }
                    }
                }
            }
        }
    }

    fun reprocess(lectureId: String, onProcessing: (String) -> Unit) {
        viewModelScope.launch {
            when (val started = LuminaraApi.process(lectureId)) {
                is ApiResult.Err -> _state.update { it.copy(error = started.message) }
                is ApiResult.Ok -> {
                    _state.update {
                        it.copy(activeId = lectureId, status = started.value, lecture = null)
                    }
                    onProcessing(lectureId)
                    pollUntilDone(lectureId)
                }
            }
        }
    }

    private fun pollUntilDone(lectureId: String) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            var misses = 0
            while (true) {
                when (val res = LuminaraApi.status(lectureId)) {
                    is ApiResult.Ok -> {
                        misses = 0
                        val status = res.value
                        _state.update { it.copy(status = status) }
                        if (status.status == "ready") {
                            loadLecture(lectureId)
                            loadLectures()
                            return@launch
                        }
                        if (status.status == "failed") {
                            _state.update {
                                it.copy(error = status.error.ifBlank { "Processing failed" })
                            }
                            return@launch
                        }
                    }
                    is ApiResult.Err -> {
                        misses++
                        if (misses >= 5) {
                            _state.update { it.copy(error = res.message) }
                            return@launch
                        }
                    }
                }
                delay(800)
            }
        }
    }

    fun loadLecture(lectureId: String) {
        val switching = _state.value.activeId != lectureId
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loadingLecture = true,
                    activeId = lectureId,
                    // never show one lecture's script or search results against another
                    script = if (switching) null else it.script,
                    search = if (switching) null else it.search,
                    searchQuery = if (switching) "" else it.searchQuery,
                    savedPack = null,
                    downloadError = null,
                )
            }
            when (val res = LuminaraApi.lecture(lectureId, _state.value.language)) {
                is ApiResult.Ok -> {
                    _state.update { it.copy(lecture = res.value, loadingLecture = false) }
                    loadSuggestions(lectureId)
                    loadChat(lectureId)
                    loadScript(lectureId)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(loadingLecture = false, error = res.message)
                }
            }
        }
    }

    private fun translateActive(code: String) {
        val id = _state.value.lecture?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(translating = true) }
            val res = LuminaraApi.translate(id, code)
            _state.update { it.copy(translating = false) }
            if (res is ApiResult.Err) {
                _state.update { it.copy(error = res.message) }
            }
            loadLecture(id)
        }
    }

    // -- BOB ----------------------------------------------------------------

    private fun loadSuggestions(lectureId: String) {
        viewModelScope.launch {
            LuminaraApi.suggestions(lectureId, _state.value.language).valueOrNull?.let { s ->
                _state.update { it.copy(suggestions = s.suggestions) }
            }
        }
    }

    private fun loadChat(lectureId: String) {
        viewModelScope.launch {
            LuminaraApi.chatHistory(lectureId).valueOrNull?.let { history ->
                _state.update {
                    it.copy(
                        chat = history.messages.map { m ->
                            ChatTurn(
                                question = m.question,
                                answer = m.answer,
                                sources = m.sources,
                                engine = m.engine,
                                intent = m.intent,
                            )
                        },
                    )
                }
            }
        }
    }

    fun ask(question: String) {
        val id = _state.value.activeId ?: return
        if (question.isBlank() || _state.value.bobThinking) return

        _state.update {
            it.copy(
                chat = it.chat + ChatTurn(question = question, pending = true),
                bobThinking = true,
            )
        }

        viewModelScope.launch {
            val res = LuminaraApi.ask(id, question, _state.value.language)
            _state.update { st ->
                val updated = st.chat.toMutableList()
                val index = updated.indexOfLast { it.pending }
                if (index >= 0) {
                    updated[index] = when (res) {
                        is ApiResult.Ok -> ChatTurn(
                            question = question,
                            answer = res.value.answer,
                            sources = res.value.sources,
                            followUps = res.value.followUps,
                            engine = res.value.engine,
                            intent = res.value.intent,
                            grounded = res.value.grounded,
                        )
                        is ApiResult.Err -> ChatTurn(
                            question = question,
                            answer = res.message,
                            failed = true,
                        )
                    }
                }
                st.copy(chat = updated, bobThinking = false)
            }
        }
    }

    // -- P1: script -------------------------------------------------------

    fun loadScript(lectureId: String? = null) {
        val id = lectureId ?: _state.value.activeId ?: return
        viewModelScope.launch {
            _state.update { it.copy(scriptLoading = true) }
            when (val res = LuminaraApi.script(id, _state.value.language)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(script = res.value, scriptLoading = false)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(scriptLoading = false, error = res.message)
                }
            }
        }
    }

    // -- P1: search -------------------------------------------------------

    private var searchJob: Job? = null

    fun setSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        val id = _state.value.activeId ?: return
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(search = null, searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(220)   // let the student finish typing before hitting the backend
            _state.update { it.copy(searching = true) }
            when (val res = LuminaraApi.search(id, query, _state.value.language)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(search = res.value, searching = false)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(searching = false, error = res.message)
                }
            }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.update { it.copy(searchQuery = "", search = null, searching = false) }
    }

    // -- P1: study pack ---------------------------------------------------

    fun downloadStudyPack() {
        val lecture = _state.value.lecture ?: return
        if (_state.value.downloading) return

        viewModelScope.launch {
            _state.update { it.copy(downloading = true, downloadError = null, savedPack = null) }
            val context = getApplication<Application>()
            val language = _state.value.language
            val temp = File(context.cacheDir, "studypack-${lecture.id}.tmp")

            when (val res = LuminaraApi.downloadStudyPack(lecture.id, language, temp)) {
                is ApiResult.Err -> _state.update {
                    it.copy(downloading = false, downloadError = res.message)
                }
                is ApiResult.Ok -> {
                    val isPdf = res.value.contains("pdf", ignoreCase = true)
                    // \p{M} keeps Unicode combining marks — without it Devanagari
                    // matras are stripped and a Hindi title becomes unreadable.
                    val base = lecture.knowledge.title.ifBlank { lecture.title }
                        .replace(Regex("[^\\p{L}\\p{N}\\p{M} ]+"), "")
                        .trim()
                        .take(48)
                        .ifBlank { "Lecture" }
                    val name = "Luminara - $base ($language).${if (isPdf) "pdf" else "html"}"
                    val saved = runCatching {
                        StudyPackSaver.save(context, temp, name, isPdf)
                    }.getOrNull()
                    temp.delete()
                    _state.update {
                        it.copy(
                            downloading = false,
                            savedPack = saved,
                            downloadError = if (saved == null) {
                                "Could not save the study pack to this device."
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    fun dismissSavedPack() = _state.update { it.copy(savedPack = null, downloadError = null) }

    fun retryLast() {
        val last = _state.value.chat.lastOrNull { it.failed } ?: return
        _state.update { it.copy(chat = it.chat.filterNot { turn -> turn.failed }) }
        ask(last.question)
    }

    fun clearChat() {
        val id = _state.value.activeId ?: return
        viewModelScope.launch {
            LuminaraApi.clearChat(id)
            _state.update { it.copy(chat = emptyList()) }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
