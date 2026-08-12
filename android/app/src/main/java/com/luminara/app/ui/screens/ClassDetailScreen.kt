package com.luminara.app.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.ClassLectureDto
import com.luminara.app.ui.components.EmptyState
import com.luminara.app.ui.components.ErrorBanner
import com.luminara.app.ui.components.GlassCard
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.components.SectionLabel
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.viewmodel.UiState

@Composable
fun ClassDetailScreen(
    state: UiState,
    onOpenLecture: (String) -> Unit,
    onUpload: (String) -> Unit,
    onBack: () -> Unit,
) {
    val detail = state.classDetail
    val schoolClass = detail?.schoolClass

    LuminaraBackground {
        if (detail == null || schoolClass == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                if (state.classError != null) {
                    ErrorBanner(state.classError, onRetry = onBack, modifier = Modifier.padding(20.dp))
                } else {
                    CircularProgressIndicator(color = Violet)
                }
            }
            return@LuminaraBackground
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(20.dp, 52.dp, 20.dp, 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                ScreenTopBar(
                    schoolClass.name,
                    schoolClass.subject.ifBlank {
                        if (schoolClass.isTeacher) "Your class" else schoolClass.teacherName
                    },
                    onBack,
                )
            }

            if (schoolClass.isTeacher) {
                item { JoinCodeCard(schoolClass.joinCode) }
                item {
                    Button(
                        onClick = { onUpload(schoolClass.id) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet),
                    ) {
                        Icon(Icons.Filled.CloudUpload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Upload a lecture", style = MaterialTheme.typography.labelLarge)
                    }
                }
                item {
                    Text(
                        "${schoolClass.studentCount} student" +
                            (if (schoolClass.studentCount != 1) "s" else "") + " joined",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 12.5.sp,
                    )
                }
            }

            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel(if (schoolClass.isTeacher) "Lectures" else "Published lectures")
            }

            if (detail.lectures.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Filled.LibraryBooks,
                        "No lectures yet",
                        if (schoolClass.isTeacher) {
                            "Upload one — it runs through the same pipeline as everything else, " +
                                "then you can review and publish it."
                        } else {
                            "Your teacher has not published a lecture in this class yet."
                        },
                    )
                }
            }

            items(detail.lectures) { lecture ->
                ClassLectureCard(lecture, schoolClass.isTeacher) { onOpenLecture(lecture.id) }
            }
        }
    }
}

@Composable
private fun ClassLectureCard(
    lecture: ClassLectureDto,
    isTeacher: Boolean,
    onClick: () -> Unit,
) {
    val (statusLabel, statusColor) = when {
        lecture.status == "failed" -> "Failed" to MaterialTheme.colorScheme.error
        lecture.status != "ready" -> "Processing" to Violet
        lecture.published -> "Published" to Teal
        else -> "Draft — not visible to students" to Amber
    }

    GlassCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    lecture.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 15.5.sp,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                    )
                }
                if (lecture.durationSec > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${lecture.durationSec.toInt()}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 12.sp,
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                null,
                tint = TextFaint,
                modifier = Modifier.size(17.dp),
            )
        }
        if (isTeacher && !lecture.published && lecture.status == "ready") {
            Spacer(Modifier.height(10.dp))
            Text(
                "Open it to review and publish.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}
