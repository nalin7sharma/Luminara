package com.luminara.app.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.Pill
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

private data class PickedFile(val name: String, val bytes: ByteArray)

/**
 * Teacher upload. Chooses a class and a title, picks the audio and/or the board
 * photo, and hands them to the existing `/api/lectures/upload` + `/process` —
 * the same route a personal upload takes.
 */
@Composable
fun UploadLectureScreen(
    state: UiState,
    initialClassId: String?,
    onUpload: (title: String, classId: String?, audio: Pair<String, ByteArray>?, image: Pair<String, ByteArray>?) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var classId by remember { mutableStateOf(initialClassId ?: state.classes.firstOrNull()?.id) }
    var audio by remember { mutableStateOf<PickedFile?>(null) }
    var image by remember { mutableStateOf<PickedFile?>(null) }

    fun read(uri: Uri?): PickedFile? {
        if (uri == null) return null
        return runCatching {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
            } ?: uri.lastPathSegment ?: "file"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return null
            PickedFile(name, bytes)
        }.getOrNull()
    }

    // OpenDocument rather than GetContent so both audio and video are offered:
    // a lecture recording is usually a video, and the backend extracts its audio.
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { audio = read(it) }
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { image = read(it) }

    val canUpload = title.isNotBlank() && (audio != null || image != null) && !state.uploading

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize().imePadding(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { ScreenTopBar("Upload a lecture", "Runs the standard Luminara pipeline", onBack) }

            if (state.classes.isNotEmpty()) {
                item {
                    SectionLabel("Class")
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.classes.filter { it.isTeacher }.forEach { schoolClass ->
                            Pill(
                                text = schoolClass.name,
                                selected = classId == schoolClass.id,
                                onClick = { classId = schoolClass.id },
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel("Lecture title")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    placeholder = { Text("Binary Search", color = TextFaint) },
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

            item {
                SectionLabel("Lecture material")
                Spacer(Modifier.height(10.dp))
                FilePickRow(
                    icon = { Icon(Icons.Filled.GraphicEq, null, tint = Teal, modifier = Modifier.size(19.dp)) },
                    title = "Lecture recording",
                    hint = "Video or audio — the audio track is extracted for transcription",
                    picked = audio?.name,
                ) { pickMedia.launch(arrayOf("video/*", "audio/*")) }
                Spacer(Modifier.height(10.dp))
                FilePickRow(
                    icon = { Icon(Icons.Filled.Image, null, tint = Violet, modifier = Modifier.size(19.dp)) },
                    title = "Board photo",
                    hint = "Optional for a video — a frame is used if you skip it",
                    picked = image?.name,
                ) { pickImage.launch(arrayOf("image/*")) }
            }

            item {
                Button(
                    onClick = {
                        onUpload(
                            title.trim(),
                            classId,
                            audio?.let { it.name to it.bytes },
                            image?.let { it.name to it.bytes },
                        )
                    },
                    enabled = canUpload,
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(15.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    if (state.uploading) {
                        CircularProgressIndicator(
                            Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("Uploading…", style = MaterialTheme.typography.labelLarge)
                    } else {
                        Text(
                            "Upload and process",
                            style = MaterialTheme.typography.labelLarge,
                            fontSize = 16.sp,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Processing takes roughly a minute. Nothing is visible to students until " +
                        "you review it and press Publish.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
private fun FilePickRow(
    icon: @Composable () -> Unit,
    title: String,
    hint: String,
    picked: String?,
    onPick: () -> Unit,
) {
    GlassCard(accent = if (picked != null) Teal else null, onClick = onPick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon()
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    picked ?: hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (picked != null) Teal else TextSecondary,
                    fontSize = 12.5.sp,
                )
            }
            if (picked != null) {
                Icon(
                    Icons.Filled.CheckCircle,
                    null,
                    tint = Teal,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text("Choose", style = MaterialTheme.typography.labelSmall, color = Violet)
            }
        }
    }
}
