package com.hourglass.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.ui.components.BedtimeCard
import com.hourglass.ui.components.HourglassGlass
import com.hourglass.ui.components.QuicksandCard
import com.hourglass.ui.components.SandTimerCard
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.TimerCard

/*
 * Previews for the pieces that carry the app's look, so the design can be reviewed in
 * both themes without building and installing.
 */

private fun sample(
    id: Long = 1,
    name: String = "Flashcards",
    colour: String = "#FFE0A63C",
    duration: Long = 2 * 3_600_000L,
    remaining: Long = 83 * 60_000L,
    progress: Float = 0.31f,
    running: Boolean = false,
    paused: Boolean = false,
    overtime: Boolean = false,
    kind: TimerKind = TimerKind.TASK
) = TimerCard(
    ref = TimerRef(id, kind),
    name = name,
    colourHex = colour,
    durationMillis = duration,
    remainingMillis = remaining,
    progress = progress,
    isRunning = running,
    isPaused = paused,
    isOvertime = overtime,
    sessionsCompleted = 4
)

@Composable
private fun PreviewSurface(dark: Boolean, content: @Composable () -> Unit) {
    HourglassTheme(darkTheme = dark) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(HourglassTheme.colors.backdrop)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) { content() }
    }
}

@Preview(name = "Sand timers — light", widthDp = 380)
@Composable
private fun SandTimersLight() = PreviewSurface(dark = false) { SandTimerStates() }

@Preview(name = "Sand timers — dark", widthDp = 380)
@Composable
private fun SandTimersDark() = PreviewSurface(dark = true) { SandTimerStates() }

@Composable
private fun SandTimerStates() {
    SandTimerCard(sample(), {}, {}, {}, {}, {})
    SandTimerCard(
        sample(id = 2, name = "Question bank", colour = "#FF2F8F86", running = true, progress = 0.62f),
        {}, {}, {}, {}, {}
    )
    SandTimerCard(
        sample(
            id = 3,
            name = "Essay draft",
            colour = "#FFC2506E",
            remaining = -4 * 60_000L,
            progress = 1f,
            running = true,
            overtime = true
        ),
        {}, {}, {}, {}, {}
    )
}

@Preview(name = "Quicksand — light", widthDp = 380)
@Composable
private fun QuicksandLight() = PreviewSurface(dark = false) { QuicksandStates() }

@Preview(name = "Quicksand — dark", widthDp = 380)
@Composable
private fun QuicksandDark() = PreviewSurface(dark = true) { QuicksandStates() }

@Composable
private fun QuicksandStates() {
    QuicksandCard(
        sample(name = "Email", colour = "#FFE08A4B", duration = 15 * 60_000L,
            remaining = 9 * 60_000L, kind = TimerKind.QUICKSAND),
        {}, {}, {}, {}, {}
    )
    QuicksandCard(
        sample(id = 2, name = "Tidy desk", colour = "#FF52689E", duration = 10 * 60_000L,
            remaining = 3 * 60_000L, progress = 0.7f, paused = true, kind = TimerKind.QUICKSAND),
        {}, {}, {}, {}, {}
    )
}

@Preview(name = "Bedtime — light", widthDp = 380, heightDp = 340)
@Composable
private fun BedtimeLight() = PreviewSurface(dark = false) {
    BedtimeCard(bedtime = TimeOfDay(22, 30), sleepMinutes = 8 * 60)
}

@Preview(name = "Bedtime — dark", widthDp = 380, heightDp = 340)
@Composable
private fun BedtimeDark() = PreviewSurface(dark = true) {
    BedtimeCard(bedtime = TimeOfDay(22, 30), sleepMinutes = 6 * 60 + 30)
}

@Preview(name = "Hourglass sizes", widthDp = 380, heightDp = 220)
@Composable
private fun GlassSizes() = PreviewSurface(dark = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        listOf(0f, 0.35f, 0.75f, 1f).forEach { progress ->
            HourglassGlass(
                progress = progress,
                sandColor = HourglassTheme.colors.accent,
                running = true,
                overtime = false,
                modifier = Modifier.size(width = 60.dp, height = 80.dp)
            )
        }
    }
}
