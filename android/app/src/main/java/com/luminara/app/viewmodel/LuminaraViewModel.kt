package com.luminara.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminara.app.data.ApiResult
import com.luminara.app.data.AuthResponseDto
import com.luminara.app.data.BoardCamera
import com.luminara.app.data.ClassDetailDto
import com.luminara.app.data.ClassDto
import com.luminara.app.data.ConfigDto
import com.luminara.app.data.UserDto
import com.luminara.app.data.LectureDto
import com.luminara.app.data.LectureSummaryDto
import com.luminara.app.data.LiveChunkDto
import com.luminara.app.data.LiveRecorder
import com.luminara.app.data.LuminaraApi
import com.luminara.app.data.Prefs
import com.luminara.app.data.SavedPack
import com.luminara.app.data.ScriptDto
import com.luminara.app.data.SearchResponseDto
import com.luminara.app.data.SourceDto
import com.luminara.app.data.StatusDto
import com.luminara.app.data.StudyPackSaver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

/** One processed chunk of a live lecture, as the student sees it. */
data class LiveLine(
    val timecode: String,
    val original: String,
    val translated: String = "",
    val error: String = "",
)

/** One board reading, pinned to the moment of the class it was taken. */
data class BoardMoment(
    val captureId: Int,
    val timecode: String,
    val headline: String,
    val useful: Boolean,
    val auto: Boolean,
    val engine: String = "",
    val formula: String = "",
    val error: String = "",
)

data class LiveState(
    val lectureId: String,
    val language: String,
    val chunkSeconds: Int = 9,
    val recording: Boolean = false,
    val paused: Boolean = false,
    val finishing: Boolean = false,
    val elapsedSec: Int = 0,
    val chunksSent: Int = 0,
    val chunksFailed: Int = 0,
    val behindSec: Double = 0.0,
    val level: Float = 0f,
    val lines: List<LiveLine> = emptyList(),
    // --- board -----------------------------------------------------------
    val cameraOn: Boolean = false,
    val cameraError: String = "",
    val capturing: Boolean = false,
    val autoCapture: Boolean = false,
    val autoCaptureSeconds: Int = 12,
    val boards: List<BoardMoment> = emptyList(),
    /** Set briefly after a capture so the screen can confirm it visually. */
    val lastCapture: BoardMoment? = null,
    // --- live BOB --------------------------------------------------------
    val asking: Boolean = false,
    val liveAnswer: String = "",
    val liveAnswerEngine: String = "",
    val liveQuestion: String = "",
    val error: String? = null,
) {
    /** Honest figure: the chunk still being spoken, plus what processing measured. */
    val behindLabel: String
        get() = if (behindSec <= 0) "~${chunkSeconds}s behind" else "~${behindSec.toInt()}s behind"
}

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

    // --- P2: live lecture ---
    val live: LiveState? = null,

    // --- P3: classroom ---
    val user: UserDto? = null,
    val role: String = "student",
    val displayName: String = "",
    val classes: List<ClassDto> = emptyList(),
    val classDetail: ClassDetailDto? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val classBusy: Boolean = false,
    val classError: String? = null,
    val uploading: Boolean = false,
) {
    val isTeacher get() = user?.isTeacher == true || (user == null && role == "teacher")
    val signedIn get() = user != null
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

/**
 * Development-only fallbacks. A release build points at the deployed HTTPS
 * backend and must never silently wander onto a localhost address, so this list
 * is empty outside debug builds.
 */
private val BACKEND_CANDIDATES: List<String> =
    if (LuminaraApi.isDebugBuild) {
        listOf(
            "http://10.0.2.2:8000",   // Android emulator -> host machine
            "http://127.0.0.1:8000",  // physical device with `adb reverse tcp:8000 tcp:8000`
        )
    } else {
        emptyList()
    }

class LuminaraViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    private val _state = MutableStateFlow(
        UiState(
            language = prefs.language,
            onboarded = prefs.onboarded,
            role = prefs.role,
            displayName = prefs.name,
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    init {
        LuminaraApi.baseUrl = prefs.baseUrl
        LuminaraApi.token = prefs.token
        refresh()
        if (prefs.token != null) restoreSession()
    }

    /** Called once from the welcome flow; the choices persist across restarts. */
    fun completeOnboarding(name: String, role: String, languageCode: String) {
        prefs.name = name.trim()
        prefs.role = role
        prefs.language = languageCode
        prefs.onboarded = true
        _state.update {
            it.copy(
                displayName = name.trim(),
                role = role,
                language = languageCode,
                onboarded = true,
            )
        }
    }

    // -- P3: accounts ------------------------------------------------------

    private fun restoreSession() {
        viewModelScope.launch {
            when (val res = LuminaraApi.me()) {
                is ApiResult.Ok -> {
                    adoptUser(res.value.user)
                    loadClasses()
                }
                is ApiResult.Err -> {
                    // An expired or rejected token must not strand the app: drop
                    // it and carry on as a guest, where the demo still works.
                    if (res.code == 401) signOut()
                }
            }
        }
    }

    private fun adoptUser(user: UserDto) {
        prefs.role = user.role
        prefs.name = user.name
        if (user.language.isNotBlank()) prefs.language = user.language
        _state.update {
            it.copy(
                user = user,
                role = user.role,
                displayName = user.name,
                language = user.language.ifBlank { it.language },
                authError = null,
            )
        }
    }

    fun register(email: String, password: String, onDone: () -> Unit) {
        val current = _state.value
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            val res = LuminaraApi.register(
                name = current.displayName.ifBlank { email.substringBefore("@") },
                email = email,
                password = password,
                role = current.role,
                language = current.language,
            )
            finishAuth(res, onDone)
        }
    }

    fun login(email: String, password: String, onDone: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(authBusy = true, authError = null) }
            finishAuth(LuminaraApi.login(email, password), onDone)
        }
    }

    private fun finishAuth(res: ApiResult<AuthResponseDto>, onDone: () -> Unit) {
        when (res) {
            is ApiResult.Err -> _state.update {
                it.copy(authBusy = false, authError = res.message)
            }
            is ApiResult.Ok -> {
                prefs.token = res.value.token
                LuminaraApi.token = res.value.token
                adoptUser(res.value.user)
                _state.update { it.copy(authBusy = false) }
                loadClasses()
                loadLectures()
                onDone()
            }
        }
    }

    fun signOut() {
        prefs.clearAccount()
        LuminaraApi.token = null
        _state.update {
            it.copy(user = null, classes = emptyList(), classDetail = null, authError = null)
        }
        loadLectures()
    }

    fun dismissAuthError() = _state.update { it.copy(authError = null) }

    // -- P3: classes -------------------------------------------------------

    fun loadClasses() {
        if (_state.value.user == null) return
        viewModelScope.launch {
            LuminaraApi.classes().valueOrNull?.let { list ->
                _state.update { it.copy(classes = list.classes) }
            }
        }
    }

    fun createClass(name: String, subject: String, onDone: (ClassDto) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(classBusy = true, classError = null) }
            when (val res = LuminaraApi.createClass(name, subject)) {
                is ApiResult.Err -> _state.update {
                    it.copy(classBusy = false, classError = res.message)
                }
                is ApiResult.Ok -> {
                    _state.update { it.copy(classBusy = false) }
                    loadClasses()
                    onDone(res.value.schoolClass)
                }
            }
        }
    }

    fun joinClass(code: String, onDone: (ClassDto) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(classBusy = true, classError = null) }
            when (val res = LuminaraApi.joinClass(code)) {
                is ApiResult.Err -> _state.update {
                    it.copy(classBusy = false, classError = res.message)
                }
                is ApiResult.Ok -> {
                    _state.update { it.copy(classBusy = false) }
                    loadClasses()
                    loadLectures()
                    onDone(res.value.schoolClass)
                }
            }
        }
    }

    fun openClass(classId: String) {
        viewModelScope.launch {
            _state.update { it.copy(classBusy = true, classDetail = null, classError = null) }
            when (val res = LuminaraApi.classDetail(classId)) {
                is ApiResult.Ok -> _state.update {
                    it.copy(classBusy = false, classDetail = res.value)
                }
                is ApiResult.Err -> _state.update {
                    it.copy(classBusy = false, classError = res.message)
                }
            }
        }
    }

    fun dismissClassError() = _state.update { it.copy(classError = null) }

    /** Publish or unpublish a class lecture, from the existing Lecture Detail. */
    fun setPublished(lectureId: String, published: Boolean) {
        viewModelScope.launch {
            when (val res = LuminaraApi.publish(lectureId, published)) {
                is ApiResult.Err -> _state.update { it.copy(error = res.message) }
                is ApiResult.Ok -> {
                    _state.update { st ->
                        st.copy(
                            lecture = st.lecture?.takeIf { it.id == lectureId }
                                ?.copy(published = res.value.published) ?: st.lecture
                        )
                    }
                    _state.value.classDetail?.schoolClass?.id?.let { openClass(it) }
                    loadLectures()
                }
            }
        }
    }

    /** Teacher upload — straight into the existing pipeline, no second system. */
    fun uploadLecture(
        title: String,
        classId: String?,
        audio: Pair<String, ByteArray>?,
        image: Pair<String, ByteArray>?,
        onProcessing: (String) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        if (audio == null && image == null) {
            onFailed("Choose an audio file or a board photo first.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(uploading = true) }
            when (
                val res = LuminaraApi.uploadLecture(
                    title, _state.value.language, classId, audio, image
                )
            ) {
                is ApiResult.Err -> {
                    _state.update { it.copy(uploading = false) }
                    onFailed(res.message)
                }
                is ApiResult.Ok -> {
                    val id = res.value.lectureId
                    _state.update { it.copy(uploading = false, activeId = id, lecture = null) }
                    when (val started = LuminaraApi.process(id)) {
                        is ApiResult.Err -> onFailed(started.message)
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

    // -- P2: live lecture -------------------------------------------------

    private var recorder: LiveRecorder? = null
    private var uploadJob: Job? = null
    private var readJob: Job? = null
    private var tickJob: Job? = null
    private var autoCaptureJob: Job? = null

    /** Set synchronously: a state flag would let two callers both pass the guard
     *  before the first one's update lands, and start two recorders. */
    @Volatile
    private var liveStarting = false

    /**
     * Open a live session and start capturing. The read loop and the upload
     * loop are separate: audio keeps being captured while a chunk is in flight,
     * so nothing is lost to a slow network.
     */
    fun startLive(onFailed: (String) -> Unit) {
        if (liveStarting || _state.value.live?.recording == true) return
        liveStarting = true

        viewModelScope.launch {
            when (val started = LuminaraApi.liveStart(_state.value.language, "Live lecture")) {
                is ApiResult.Err -> {
                    liveStarting = false
                    onFailed(started.message)
                }
                is ApiResult.Ok -> {
                    val session = started.value
                    val rec = LiveRecorder(chunkSeconds = session.chunkSeconds)
                    if (!rec.start()) {
                        liveStarting = false
                        onFailed("This device would not start the microphone.")
                        return@launch
                    }
                    recorder = rec
                    _state.update {
                        it.copy(
                            live = LiveState(
                                lectureId = session.lectureId,
                                language = session.language,
                                chunkSeconds = session.chunkSeconds,
                                recording = true,
                            )
                        )
                    }

                    readJob = launch(Dispatchers.IO) { rec.readLoop() }

                    uploadJob = launch(Dispatchers.IO) {
                        var index = 0
                        for (chunk in rec.chunks) {
                            val result = LuminaraApi.liveChunk(session.lectureId, index++, chunk)
                            _state.update { st ->
                                val live = st.live ?: return@update st
                                st.copy(live = applyChunk(live, result))
                            }
                        }
                    }

                    tickJob = launch {
                        while (isActive) {
                            delay(1000)
                            _state.update { st ->
                                val live = st.live ?: return@update st
                                if (!live.recording) return@update st
                                st.copy(
                                    live = live.copy(
                                        elapsedSec = if (live.paused) {
                                            live.elapsedSec
                                        } else {
                                            live.elapsedSec + 1
                                        },
                                        level = recorder?.level ?: 0f,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun applyChunk(live: LiveState, result: ApiResult<LiveChunkDto>): LiveState =
        when (result) {
            is ApiResult.Err -> live.copy(
                chunksFailed = live.chunksFailed + 1,
                error = result.message,
            )
            is ApiResult.Ok -> {
                val chunk = result.value
                if (!chunk.ok || chunk.transcript.isBlank()) {
                    // Silence is normal in a classroom and is not an error; a
                    // decode failure is. Either way we never invent a line.
                    live.copy(
                        chunksFailed = live.chunksFailed + 1,
                        error = chunk.error.ifBlank { null },
                    )
                } else {
                    live.copy(
                        chunksSent = live.chunksSent + 1,
                        behindSec = chunk.behindMs / 1000.0,
                        error = null,
                        lines = live.lines + LiveLine(
                            timecode = chunk.timecode,
                            original = chunk.transcript,
                            translated = chunk.translation,
                            error = chunk.error,
                        ),
                    )
                }
            }
        }

    // ---- board capture ---------------------------------------------------

    /**
     * Read the board once.
     *
     * Runs as its own request while the recorder keeps filling chunks, so the
     * class is never paused to look at the board. A failure is surfaced on the
     * capture, not on the lecture: losing the camera must not end the class.
     */
    fun captureBoard(camera: BoardCamera, auto: Boolean = false) {
        val live = _state.value.live ?: return
        if (live.capturing || !live.recording) return
        // A manual capture always wins; the sampler simply skips this round.
        _state.update { it.copy(live = it.live?.copy(capturing = true)) }

        viewModelScope.launch {
            val jpeg = camera.captureJpeg()
            if (jpeg == null) {
                _state.update {
                    it.copy(
                        live = it.live?.copy(
                            capturing = false,
                            cameraError = camera.lastError.ifBlank { "the camera did not respond" },
                        )
                    )
                }
                return@launch
            }
            when (val res = LuminaraApi.liveBoard(live.lectureId, jpeg, auto)) {
                is ApiResult.Err -> _state.update {
                    it.copy(live = it.live?.copy(capturing = false, cameraError = res.message))
                }
                is ApiResult.Ok -> {
                    val dto = res.value
                    val moment = BoardMoment(
                        captureId = dto.captureId,
                        timecode = dto.timecode,
                        headline = dto.headline,
                        useful = dto.useful,
                        auto = dto.auto,
                        engine = dto.engine,
                        formula = dto.formulas.firstOrNull()
                            ?.let { f -> f.plain.ifBlank { f.latex } }
                            .orEmpty(),
                        error = dto.error,
                    )
                    _state.update { st ->
                        val cur = st.live ?: return@update st
                        st.copy(
                            live = cur.copy(
                                capturing = false,
                                cameraError = "",
                                // An automatic frame that found nothing is not
                                // worth a line on the timeline; a tap always is.
                                boards = if (moment.useful || !auto) cur.boards + moment
                                else cur.boards,
                                lastCapture = moment,
                            )
                        )
                    }
                }
            }
        }
    }

    fun onCameraReady(error: String?) {
        _state.update {
            it.copy(
                live = it.live?.copy(
                    cameraOn = error == null,
                    cameraError = error.orEmpty(),
                )
            )
        }
    }

    fun stopCamera() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        _state.update { it.copy(live = it.live?.copy(cameraOn = false, autoCapture = false)) }
    }

    /** Sample a frame every few seconds. Manual capture always takes priority. */
    fun toggleAutoCapture(camera: BoardCamera) {
        val live = _state.value.live ?: return
        val on = !live.autoCapture
        _state.update { it.copy(live = it.live?.copy(autoCapture = on)) }
        autoCaptureJob?.cancel()
        if (!on) {
            autoCaptureJob = null
            return
        }
        autoCaptureJob = viewModelScope.launch {
            while (isActive) {
                delay(live.autoCaptureSeconds * 1000L)
                val cur = _state.value.live ?: return@launch
                if (!cur.recording || cur.paused || cur.capturing || !cur.cameraOn) continue
                captureBoard(camera, auto = true)
            }
        }
    }

    fun dismissCaptureToast() {
        _state.update { it.copy(live = it.live?.copy(lastCapture = null)) }
    }

    // ---- live BOB --------------------------------------------------------

    /** Ask about the class so far. Answers only from what has been captured. */
    fun askLive(question: String) {
        val live = _state.value.live ?: return
        if (question.isBlank() || live.asking) return
        _state.update {
            it.copy(
                live = it.live?.copy(asking = true, liveQuestion = question, liveAnswer = "")
            )
        }
        viewModelScope.launch {
            when (val res = LuminaraApi.liveAsk(live.lectureId, question, live.language)) {
                is ApiResult.Err -> _state.update {
                    it.copy(
                        live = it.live?.copy(
                            asking = false,
                            liveAnswer = "",
                            liveAnswerEngine = "",
                            error = res.message,
                        )
                    )
                }
                is ApiResult.Ok -> _state.update {
                    it.copy(
                        live = it.live?.copy(
                            asking = false,
                            liveAnswer = res.value.answer,
                            liveAnswerEngine = res.value.engine,
                        )
                    )
                }
            }
        }
    }

    fun clearLiveAnswer() {
        _state.update {
            it.copy(live = it.live?.copy(liveAnswer = "", liveQuestion = "", liveAnswerEngine = ""))
        }
    }

    fun togglePauseLive() {
        val live = _state.value.live ?: return
        val paused = !live.paused
        recorder?.paused = paused
        _state.update { it.copy(live = it.live?.copy(paused = paused)) }
        viewModelScope.launch { LuminaraApi.livePause(live.lectureId) }
    }

    /** Stop recording, let the last chunk finish uploading, then finalise. */
    fun endLive(onReady: (String) -> Unit, onFailed: (String) -> Unit) {
        val live = _state.value.live ?: return
        viewModelScope.launch {
            liveStarting = false
            _state.update {
                it.copy(live = it.live?.copy(finishing = true, recording = false, paused = false))
            }
            tickJob?.cancel()
            autoCaptureJob?.cancel()
            autoCaptureJob = null
            recorder?.paused = false
            recorder?.stop()          // read loop sends the tail, then closes the channel
            recorder = null
            readJob?.join()
            uploadJob?.join()         // never finalise while a chunk is still in flight

            when (val finished = LuminaraApi.liveFinish(live.lectureId)) {
                is ApiResult.Err -> {
                    // Clear `finishing`, or the screen sits on "Building your
                    // lecture" forever with the failure invisible behind it.
                    _state.update {
                        it.copy(live = it.live?.copy(finishing = false, error = finished.message))
                    }
                    onFailed(finished.message)
                }
                is ApiResult.Ok -> {
                    if (finished.value.status == "failed") {
                        val message = finished.value.error.ifBlank {
                            "No speech was recognised during this lecture."
                        }
                        _state.update {
                            it.copy(live = it.live?.copy(finishing = false, error = message))
                        }
                        onFailed(message)
                        return@launch
                    }
                    _state.update { it.copy(live = null, status = null, lecture = null) }
                    pollUntilDone(live.lectureId)
                    onReady(live.lectureId)
                }
            }
        }
    }

    /** Leaving the screen without finishing must not leave the mic running. */
    fun abandonLive() {
        val abandoned = _state.value.live
        liveStarting = false
        tickJob?.cancel()
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        recorder?.stop()
        recorder = null
        readJob?.cancel()
        uploadJob?.cancel()
        _state.update { it.copy(live = null) }
        // Tell the backend, or every re-entry to the screen leaves a session
        // sitting in `live` forever. Fire-and-forget: the student has gone.
        abandoned?.lectureId?.let { id ->
            viewModelScope.launch { LuminaraApi.liveDiscard(id) }
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
