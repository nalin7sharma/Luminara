package com.luminara.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.luminara.app.data.BoardCamera
import com.luminara.app.data.languageOption
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.Ink
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Rose
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextPrimary
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.ui.theme.VioletSoft
import com.luminara.app.viewmodel.BoardMoment
import com.luminara.app.viewmodel.LiveLine
import com.luminara.app.viewmodel.UiState
import kotlinx.coroutines.delay

/**
 * Live Class.
 *
 * The screen has one job: show what the professor just said, what Luminara made
 * of it in the student's language, and what has been read off the board — while
 * being honest that a chunk of audio cannot be understood before it has been
 * spoken. Audio is the lecture; the camera is optional evidence added to it,
 * and losing the camera never interrupts the class.
 */

/** One entry in the class timeline: something heard, or something seen. */
private sealed interface Moment {
    val order: Int

    data class Speech(val line: LiveLine, override val order: Int) : Moment
    data class Board(val board: BoardMoment, override val order: Int) : Moment
}

@Composable
fun LiveScreen(
    state: UiState,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    onCaptureBoard: (BoardCamera) -> Unit,
    onCameraReady: (String?) -> Unit,
    onCameraStopped: () -> Unit,
    onToggleAutoCapture: (BoardCamera) -> Unit,
    onAsk: (String) -> Unit,
    onDismissAnswer: () -> Unit,
    onDismissCapture: () -> Unit,
) {
    val context = LocalContext.current
    val live = state.live
    var permissionDenied by remember { mutableStateOf(false) }

    val granted = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    var hasPermission by remember { mutableStateOf(granted) }

    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        hasPermission = allowed
        permissionDenied = !allowed
        // Starting here as well as in the effect below would open two recorders.
    }

    // The camera is a separate, optional permission asked for only when the
    // student actually wants the board.
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var wantsCamera by remember { mutableStateOf(false) }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { allowed ->
        hasCamera = allowed
        wantsCamera = allowed
        if (!allowed) onCameraReady("camera permission was declined")
    }

    val camera = remember { BoardCamera(context) }
    DisposableEffect(Unit) { onDispose { camera.stop() } }

    // The single place a session starts: on arrival with permission, or the
    // moment permission is granted.
    LaunchedEffect(hasPermission) {
        if (hasPermission && state.live == null) onStart()
    }

    // Leaving without pressing End must not leave the microphone open.
    val leave by rememberUpdatedState(onLeave)
    DisposableEffect(Unit) { onDispose { leave() } }

    LuminaraBackground {
        Column(Modifier.fillMaxSize().padding(16.dp, 48.dp, 16.dp, 16.dp)) {
            LiveHeader(state, onBack)

            when {
                !hasPermission -> PermissionPrompt(permissionDenied) {
                    requestPermission.launch(Manifest.permission.RECORD_AUDIO)
                }

                live == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Rose)
                }

                live.finishing -> FinishingState(live.lines.size, live.boards.count { it.useful })

                // Recording stopped and the session could not become a lecture.
                !live.recording && live.error != null -> FailedState(live.error, onBack)

                else -> {
                    LatencyStrip(state)
                    Spacer(Modifier.height(10.dp))

                    BoardPanel(
                        state = state,
                        camera = camera,
                        showPreview = wantsCamera && hasCamera,
                        onEnable = {
                            if (hasCamera) wantsCamera = true
                            else requestCamera.launch(Manifest.permission.CAMERA)
                        },
                        onDisable = {
                            wantsCamera = false
                            camera.stop()
                            onCameraStopped()
                        },
                        onCameraReady = onCameraReady,
                        onCapture = { onCaptureBoard(camera) },
                        onToggleAuto = { onToggleAutoCapture(camera) },
                    )

                    Spacer(Modifier.height(10.dp))
                    live.error?.let {
                        ErrorBanner(it)
                        Spacer(Modifier.height(10.dp))
                    }

                    Box(Modifier.weight(1f)) {
                        ClassTimeline(live.lines, live.boards, state.language)
                        CaptureToast(live.lastCapture, onDismissCapture)
                    }

                    LiveAsk(
                        asking = live.asking,
                        question = live.liveQuestion,
                        answer = live.liveAnswer,
                        engine = live.liveAnswerEngine,
                        onAsk = onAsk,
                        onDismiss = onDismissAnswer,
                    )

                    Controls(paused = live.paused, onTogglePause = onTogglePause, onEnd = onEnd)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// header + latency
// ---------------------------------------------------------------------------

@Composable
private fun LiveHeader(state: UiState, onBack: () -> Unit) {
    val live = state.live
    val pulse by rememberInfiniteTransition("live").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val recording = live?.recording == true && live.paused != true

    Row(
        Modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .background(
                    if (recording) Rose.copy(alpha = 0.14f) else InkCard,
                    RoundedCornerShape(999.dp),
                )
                .border(
                    1.dp,
                    if (recording) Rose.copy(alpha = 0.5f) else InkBorder,
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        if (recording) Rose.copy(alpha = pulse) else TextFaint,
                        CircleShape,
                    )
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (live?.paused == true) "PAUSED" else "LIVE",
                color = if (recording) Rose else TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.width(12.dp))
        Text(
            formatElapsed(live?.elapsedSec ?: 0),
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.weight(1f))
        Text(
            languageOption(state.language).nativeName,
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        IconButton(onClick = onBack) {
            Icon(Icons.Filled.Close, "Leave", tint = TextFaint)
        }
    }
}

@Composable
private fun LatencyStrip(state: UiState) {
    val live = state.live ?: return
    val boards = live.boards.count { it.useful }
    Row(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (live.paused) Icons.Filled.MicOff else Icons.Filled.Mic,
            null,
            tint = if (live.paused) TextFaint else Teal,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            live.behindLabel,
            color = Amber,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "near real time",
            color = TextFaint,
            fontSize = 12.sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            buildString {
                append("${live.lines.size} lines")
                if (boards > 0) append(" · $boards board")
            },
            color = TextSecondary,
            fontSize = 12.sp,
        )
    }
}

// ---------------------------------------------------------------------------
// board
// ---------------------------------------------------------------------------

@Composable
private fun BoardPanel(
    state: UiState,
    camera: BoardCamera,
    showPreview: Boolean,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onCameraReady: (String?) -> Unit,
    onCapture: () -> Unit,
    onToggleAuto: () -> Unit,
) {
    val live = state.live ?: return

    if (!showPreview) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(InkCard.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onEnable)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CameraAlt, null, tint = Violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Read the board",
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    if (live.cameraError.isNotBlank()) live.cameraError
                    else "Point the camera at the board and capture what the professor writes",
                    color = if (live.cameraError.isNotBlank()) Amber else TextSecondary,
                    fontSize = 12.sp,
                )
            }
            Text("Turn on", color = Violet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // COMPATIBLE renders through a TextureView. The default SurfaceView
            // draws in its own window layer, which ignores the Compose clip and
            // spills the preview over the header and the transcript.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    LaunchedEffect(previewView) {
        onCameraReady(camera.start(lifecycleOwner, previewView))
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
            .padding(10.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Ink, RoundedCornerShape(12.dp))
        ) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            if (live.capturing) {
                Box(
                    Modifier.fillMaxSize().background(Ink.copy(alpha = 0.55f)),
                    Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Violet, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Reading the board…", color = TextPrimary, fontSize = 12.sp)
                        Text(
                            "the class keeps recording",
                            color = TextFaint,
                            fontSize = 11.sp,
                        )
                    }
                }
            }
            IconButton(
                onClick = onDisable,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Ink.copy(alpha = 0.6f), CircleShape)
                    .size(28.dp),
            ) {
                Icon(
                    Icons.Filled.Close,
                    "Turn the camera off",
                    tint = TextPrimary,
                    modifier = Modifier.size(15.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = onCapture,
            enabled = !live.capturing && live.cameraOn,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Violet,
                contentColor = Color.White,
                disabledContainerColor = Violet.copy(alpha = 0.35f),
                disabledContentColor = Color.White.copy(alpha = 0.6f),
            ),
        ) {
            Icon(Icons.Filled.PhotoCamera, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text("Capture Board", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(
                        if (live.autoCapture) Teal else Color.Transparent,
                        RoundedCornerShape(5.dp),
                    )
                    .border(
                        1.dp,
                        if (live.autoCapture) Teal else InkBorder,
                        RoundedCornerShape(5.dp),
                    )
                    .clickable(onClick = onToggleAuto)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                "Also check the board every ${live.autoCaptureSeconds}s",
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.clickable(onClick = onToggleAuto),
            )
        }
        if (live.cameraError.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(live.cameraError, color = Amber, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CaptureToast(capture: BoardMoment?, onDismiss: () -> Unit) {
    LaunchedEffect(capture) {
        if (capture != null) {
            delay(4200)
            onDismiss()
        }
    }
    AnimatedVisibility(
        visible = capture != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier.fillMaxSize(),
    ) {
        val moment = capture ?: return@AnimatedVisibility
        val good = moment.useful
        Box(Modifier.fillMaxSize(), Alignment.BottomCenter) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(
                        if (good) Teal.copy(alpha = 0.16f) else InkCard,
                        RoundedCornerShape(14.dp),
                    )
                    .border(
                        1.dp,
                        if (good) Teal.copy(alpha = 0.55f) else InkBorder,
                        RoundedCornerShape(14.dp),
                    )
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.PhotoCamera,
                    null,
                    tint = if (good) Teal else TextFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (good) "Captured from the board" else "Nothing readable on the board",
                        color = if (good) Teal else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${moment.timecode} — ${moment.headline}",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// timeline
// ---------------------------------------------------------------------------

@Composable
private fun ClassTimeline(
    lines: List<LiveLine>,
    boards: List<BoardMoment>,
    language: String,
) {
    // Ordered by arrival, not by timecode: a board capture belongs where the
    // student took it, and speech arrives a chunk behind by design.
    val moments: List<Moment> = remember(lines.size, boards.size) {
        val speech = lines.mapIndexed { i, line -> Moment.Speech(line, i) as Moment }
        val board = boards.mapIndexed { i, b -> Moment.Board(b, lines.size + i) as Moment }
        (speech + board).sortedBy { it.order }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(moments.size) {
        if (moments.isNotEmpty()) listState.animateScrollToItem(moments.size - 1)
    }

    if (moments.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Listening to the lecture…",
                    color = TextSecondary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The first lines appear once the first chunk has been spoken.",
                    color = TextFaint,
                    fontSize = 12.sp,
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(moments) { moment ->
            when (moment) {
                is Moment.Speech -> SpeechCard(moment.line, language)
                is Moment.Board -> BoardCard(moment.board)
            }
        }
    }
}

@Composable
private fun SpeechCard(line: LiveLine, language: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkCard, RoundedCornerShape(16.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Text(
            line.timecode,
            color = TextFaint,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            line.original,
            color = TextPrimary,
            fontSize = 17.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.Medium,
        )
        if (line.translated.isNotBlank() && language != "en") {
            Spacer(Modifier.height(10.dp))
            Row {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(if (line.translated.length > 90) 62.dp else 30.dp)
                        .background(Violet, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    line.translated,
                    color = VioletSoft,
                    fontSize = 17.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun BoardCard(board: BoardMoment) {
    val tint = if (board.useful) Amber else TextFaint
    Column(
        Modifier
            .fillMaxWidth()
            .background(Amber.copy(alpha = if (board.useful) 0.10f else 0.04f), RoundedCornerShape(16.dp))
            .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoCamera, null, tint = tint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                board.timecode,
                color = tint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(7.dp))
            Text(
                if (board.auto) "BOARD · AUTO" else "BOARD",
                color = tint.copy(alpha = 0.8f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            board.headline,
            color = TextPrimary,
            fontSize = 16.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = if (board.formula.isNotBlank()) FontFamily.Monospace else FontFamily.Default,
        )
        if (board.engine.isNotBlank() && board.useful) {
            Spacer(Modifier.height(6.dp))
            Text(board.engine, color = TextFaint, fontSize = 10.sp)
        }
    }
}

// ---------------------------------------------------------------------------
// live BOB
// ---------------------------------------------------------------------------

@Composable
private fun LiveAsk(
    asking: Boolean,
    question: String,
    answer: String,
    engine: String,
    onAsk: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var open by remember { mutableStateOf(false) }

    if (answer.isNotBlank() || asking) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(Violet.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .border(1.dp, Violet.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "BOB · during class",
                    color = VioletSoft,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                if (!asking) {
                    Text(
                        "Dismiss",
                        color = TextFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { onDismiss(); open = false },
                    )
                }
            }
            if (question.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(question, color = TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(8.dp))
            if (asking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(color = Violet, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Answering from the class so far…",
                        color = TextSecondary,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Text(answer, color = TextPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                if (engine.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(engine, color = TextFaint, fontSize = 10.sp)
                }
            }
        }
    }

    if (open) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 10.dp).imePadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask about the class so far…", color = TextFaint) },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Violet,
                    unfocusedBorderColor = InkBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Violet,
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAsk(draft.trim())
                        draft = ""
                        open = false
                    }
                },
                modifier = Modifier.size(46.dp).background(Violet, RoundedCornerShape(13.dp)),
            ) {
                Icon(Icons.Filled.Send, "Ask", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    } else if (answer.isBlank() && !asking) {
        Text(
            "Ask BOB about the class so far",
            color = VioletSoft,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp)
                .background(InkCard.copy(alpha = 0.5f), RoundedCornerShape(13.dp))
                .border(1.dp, InkBorder, RoundedCornerShape(13.dp))
                .clickable { open = true }
                .padding(vertical = 12.dp, horizontal = 14.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// controls and states
// ---------------------------------------------------------------------------

@Composable
private fun Controls(paused: Boolean, onTogglePause: () -> Unit, onEnd: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick = onTogglePause,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
        ) {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                null,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (paused) "Resume" else "Pause", fontSize = 15.sp)
        }
        Button(
            onClick = onEnd,
            modifier = Modifier.weight(1.25f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Rose, contentColor = Color.White),
        ) {
            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("End class", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FinishingState(lineCount: Int, boardCount: Int) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(18.dp))
            Text(
                "Building your lecture",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append("$lineCount transcribed lines")
                    if (boardCount > 0) append(" and $boardCount board capture(s)")
                    append(" → notes, script, formulas and BOB")
                },
                color = TextSecondary,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun FailedState(message: String, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Text(
                "Nothing to save",
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                message,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Luminara will not create a lecture from audio it could not hear.",
                color = TextFaint,
                fontSize = 12.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = Color.White,
                ),
            ) {
                Text("Back", fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(Icons.Filled.Mic, null, tint = Violet, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Luminara needs the microphone",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Live Class records the lecture on this device and sends short chunks " +
                    "to your Luminara backend for transcription and translation.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            if (denied) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Permission was declined. Enable the microphone in Settings to use Live Class.",
                    color = Amber,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = Color.White,
                ),
            ) {
                Text("Allow microphone", fontSize = 15.sp)
            }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
