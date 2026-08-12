package com.luminara.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.luminara.app.data.languageOption
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.viewmodel.LiveLine
import com.luminara.app.viewmodel.UiState
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.Ink
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Rose
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet

/**
 * Live Lecture. Deliberately plain: the student is in a classroom, following a
 * professor, and the screen has one job — show what was just said and what it
 * means in their language, without pretending the delay isn't there.
 */
@Composable
fun LiveScreen(
    state: UiState,
    onStart: () -> Unit,
    onTogglePause: () -> Unit,
    onEnd: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
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

                live.finishing -> FinishingState(live.lines.size)

                // Recording stopped and the session could not become a lecture.
                !live.recording && live.error != null ->
                    FailedState(live.error, onBack)

                else -> {
                    LatencyStrip(state)
                    Spacer(Modifier.height(12.dp))
                    live.error?.let {
                        ErrorBanner(it)
                        Spacer(Modifier.height(10.dp))
                    }
                    TranscriptStream(live.lines, state.language, Modifier.weight(1f))
                    Controls(paused = live.paused, onTogglePause = onTogglePause, onEnd = onEnd)
                }
            }
        }
    }
}

@Composable
private fun LiveHeader(state: UiState, onBack: () -> Unit) {
    val live = state.live
    val recording = live?.recording == true && live.paused.not()
    val transition = rememberInfiniteTransition(label = "rec")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot",
    )

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .background(Rose.copy(alpha = 0.12f), RoundedCornerShape(50))
                .border(1.dp, Rose.copy(alpha = 0.35f), RoundedCornerShape(50))
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
            Spacer(Modifier.width(8.dp))
            Text(
                if (live?.paused == true) "PAUSED" else "LIVE",
                style = MaterialTheme.typography.labelSmall,
                color = if (live?.paused == true) TextFaint else Rose,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            formatElapsed(live?.elapsedSec ?: 0),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.weight(1f))
        Text(
            languageOption(state.language).nativeName,
            style = MaterialTheme.typography.labelSmall,
            color = Violet,
            modifier = Modifier
                .background(Violet.copy(alpha = 0.14f), RoundedCornerShape(50))
                .padding(horizontal = 11.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LatencyStrip(state: UiState) {
    val live = state.live ?: return
    Spacer(Modifier.height(14.dp))
    Row(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .border(1.dp, InkBorder, RoundedCornerShape(14.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                live.behindLabel,
                style = MaterialTheme.typography.titleSmall,
                color = Amber,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Near real time — audio is processed in ${live.chunkSeconds}s chunks, " +
                    "so you are always about one chunk behind the room.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "${live.chunksSent}",
                style = MaterialTheme.typography.titleMedium,
                color = Teal,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "chunks",
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                fontSize = 10.sp,
            )
        }
    }
    if (live.chunksFailed > 0) {
        Spacer(Modifier.height(6.dp))
        Text(
            "${live.chunksFailed} chunk(s) had no recognisable speech",
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
        )
    }
}

@Composable
private fun TranscriptStream(lines: List<LiveLine>, language: String, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    if (lines.isEmpty()) {
        Box(modifier.fillMaxWidth(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Mic, null, tint = TextFaint, modifier = Modifier.size(30.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Listening to the lecture…",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    "The first lines appear once the first chunk has been spoken.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(lines) { line -> LiveLineCard(line, language) }
    }
}

@Composable
private fun LiveLineCard(line: LiveLine, language: String) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(InkCard.copy(alpha = 0.6f), shape)
            .border(1.dp, InkBorder, shape)
            .padding(14.dp)
    ) {
        Text(
            line.timecode,
            style = MaterialTheme.typography.labelSmall,
            color = Teal,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            line.original,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
        )
        if (line.translated.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Row {
                Box(Modifier.width(2.dp).fillMaxHeight().background(Violet))
                Spacer(Modifier.width(10.dp))
                Text(
                    line.translated,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Violet.copy(alpha = 0.92f),
                    fontSize = 15.sp,
                )
            }
        } else if (language != "en") {
            Spacer(Modifier.height(8.dp))
            Text(
                line.error.ifBlank { "translation unavailable for this line" },
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
            )
        }
    }
}

@Composable
private fun Controls(paused: Boolean, onTogglePause: () -> Unit, onEnd: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        OutlinedButton(
            onClick = onTogglePause,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(
                if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (paused) "Resume" else "Pause",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Button(
            onClick = onEnd,
            modifier = Modifier.weight(1f).height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Rose),
        ) {
            Icon(Icons.Filled.Stop, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("End lecture", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun FinishingState(lineCount: Int) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(18.dp))
            Text(
                "Building your lecture",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "$lineCount transcribed passages → notes, script and BOB grounding.",
                style = MaterialTheme.typography.bodyMedium,
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
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Icon(Icons.Filled.MicOff, null, tint = Amber, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                "Nothing to save",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Luminara will not create a lecture from audio it could not hear.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextFaint,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = onBack,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.height(48.dp),
            ) { Text("Back") }
        }
    }
}

@Composable
private fun PermissionPrompt(denied: Boolean, onRequest: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 20.dp),
        ) {
            Icon(
                if (denied) Icons.Filled.MicOff else Icons.Filled.Mic,
                null,
                tint = if (denied) Rose else Violet,
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Luminara needs the microphone",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (denied) {
                    "Permission was declined. Live Lecture cannot listen to the class " +
                        "without it — you can still open the demo lecture or your saved ones."
                } else {
                    "Live Lecture records the class on this device and sends short chunks " +
                        "to your own backend for transcription."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onRequest,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet),
                modifier = Modifier.height(48.dp),
            ) { Text(if (denied) "Try again" else "Allow microphone") }
        }
    }
}

private fun formatElapsed(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
