package com.luminara.app.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luminara.app.data.LANGUAGE_OPTIONS
import com.luminara.app.data.LanguageOption
import com.luminara.app.ui.components.LuminaraBackground
import com.luminara.app.ui.theme.Amber
import com.luminara.app.ui.theme.InkBorder
import com.luminara.app.ui.theme.InkCard
import com.luminara.app.ui.theme.Teal
import com.luminara.app.ui.theme.TextFaint
import com.luminara.app.ui.theme.TextSecondary
import com.luminara.app.ui.theme.Violet
import com.luminara.app.ui.theme.VioletSoft

/**
 * First launch only. One decision, asked once: which language do you learn in?
 * Everything downstream — notes, explanations, BOB — follows from it.
 */
@Composable
fun OnboardingScreen(
    initialLanguage: String,
    initialName: String = "",
    initialRole: String = "student",
    onContinue: (name: String, role: String, language: String) -> Unit,
) {
    var selected by remember { mutableStateOf(initialLanguage) }
    var name by remember { mutableStateOf(initialName) }
    var role by remember { mutableStateOf(initialRole) }

    LuminaraBackground {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp, 72.dp, 24.dp, 32.dp),
        ) {
            item { Wordmark() }

            item {
                Spacer(Modifier.height(28.dp))
                Text(
                    "The AI that attends\nclass with you",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 42.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (role == "teacher") {
                        "Luminara listens to your class, reads your board, and hands every " +
                            "student the whole lecture in the language they think in."
                    } else {
                        "Luminara understands what your professor says, writes and draws — " +
                            "then gives you the whole lecture in the language you think in."
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary,
                )
            }

            item {
                Spacer(Modifier.height(30.dp))
                Text(
                    "HOW WILL YOU USE LUMINARA?",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RoleTile(
                        title = "Student",
                        blurb = "Follow classes, revise, ask BOB",
                        selected = role == "student",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { role = "student" }
                    RoleTile(
                        title = "Teacher",
                        blurb = "Create classes, upload lectures",
                        selected = role == "teacher",
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) { role = "teacher" }
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "YOUR NAME",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            if (role == "teacher") "Dr Rao" else "Aisha",
                            color = TextFaint,
                        )
                    },
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
                Spacer(Modifier.height(24.dp))
                Text(
                    if (role == "teacher") "MY CLASS LANGUAGE" else "I LEARN BEST IN",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(14.dp))
            }

            items(LANGUAGE_OPTIONS.chunked(2).size) { rowIndex ->
                val row = LANGUAGE_OPTIONS.chunked(2)[rowIndex]
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)   // both tiles in a row match height
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { option ->
                        LanguageTile(
                            option = option,
                            selected = selected == option.code,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) { selected = option.code }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        null,
                        tint = Teal,
                        modifier = Modifier.size(15.dp).padding(top = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (role == "teacher") {
                            "Formulas and technical terms stay in their original notation, so a " +
                                "translated lecture never mangles your mathematics."
                        } else {
                            "Formulas and technical terms stay in their original notation — " +
                                "you get the explanation in your language, not a mistranslated equation."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextFaint,
                        fontSize = 13.sp,
                    )
                }
            }

            item {
                Spacer(Modifier.height(28.dp))
                Button(
                    onClick = { onContinue(name, role, selected) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet),
                ) {
                    Text(
                        if (role == "teacher") "Start teaching" else "Start learning",
                        style = MaterialTheme.typography.labelLarge,
                        fontSize = 16.sp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        null,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "You can change this at any time.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextFaint,
                    fontSize = 12.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Wordmark() {
    val transition = rememberInfiniteTransition(label = "glow")
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "scale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(46.dp)
                    .scale(scale)
                    .background(
                        Brush.radialGradient(listOf(Violet.copy(alpha = 0.55f), Color.Transparent)),
                        CircleShape,
                    )
            )
            Box(
                Modifier
                    .size(16.dp)
                    .background(Teal, CircleShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            "Luminara",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun RoleTile(
    title: String,
    blurb: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .background(
                if (selected) Violet.copy(alpha = 0.18f) else InkCard.copy(alpha = 0.7f),
                shape,
            )
            .border(1.dp, if (selected) Violet else InkBorder, shape)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) VioletSoft else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(
                    Modifier.size(20.dp).background(Violet, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            blurb,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun LanguageTile(
    option: LanguageOption,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .background(
                if (selected) Violet.copy(alpha = 0.18f) else InkCard.copy(alpha = 0.7f),
                shape,
            )
            .border(
                1.dp,
                if (selected) Violet else InkBorder,
                shape,
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                option.nativeName,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) VioletSoft else MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Box(
                    Modifier.size(20.dp).background(Violet, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Check,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            option.sample,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            fontSize = 13.sp,
        )
        if (!option.verified) {
            Spacer(Modifier.height(8.dp))
            Text(
                "preview",
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
                fontSize = 10.5.sp,
            )
        }
    }
}
