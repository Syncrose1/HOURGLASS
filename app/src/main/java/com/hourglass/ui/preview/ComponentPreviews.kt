package com.hourglass.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hourglass.core.DayTotal
import com.hourglass.core.Insights
import com.hourglass.core.SessionSummary
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.core.TimerSand
import com.hourglass.ui.components.BedtimeCard
import com.hourglass.ui.components.BedtimeNudge
import com.hourglass.ui.components.FocusColumns
import com.hourglass.ui.components.HourglassGlass
import com.hourglass.ui.components.QuicksandCard
import com.hourglass.ui.components.SandTimerCard
import com.hourglass.ui.components.TaskBars
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.TimerCard

/*
 * Previews for the pieces that carry the app's look, so the design can be reviewed in
 * both themes without building and installing. Every one is rendered light and dark:
 * the dark palette is its own set of steps, not a flip, so it has to be looked at.
 */

private fun sample(
    id: Long = 1,
    name: String = "Flashcards",
    sand: TimerSand = TimerSand.AMBER,
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
    sand = sand,
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
    SandTimerCard(sample(), {}, {}, {}, {}, {}, {})
    SandTimerCard(
        sample(id = 2, name = "Question bank", sand = TimerSand.AQUA, running = true, progress = 0.62f),
        {}, {}, {}, {}, {}, {}
    )
    SandTimerCard(
        sample(
            id = 3,
            name = "Essay draft",
            sand = TimerSand.ROSE,
            remaining = -4 * 60_000L,
            progress = 1f,
            running = true,
            overtime = true
        ),
        {}, {}, {}, {}, {}, {}
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
        sample(
            name = "Email", sand = TimerSand.TERRACOTTA, duration = 15 * 60_000L,
            remaining = 9 * 60_000L, kind = TimerKind.QUICKSAND
        ),
        {}, {}, {}, {}, {}, {}
    )
    QuicksandCard(
        sample(
            id = 2, name = "Tidy desk", sand = TimerSand.INDIGO, duration = 10 * 60_000L,
            remaining = 3 * 60_000L, progress = 0.7f, paused = true, kind = TimerKind.QUICKSAND
        ),
        {}, {}, {}, {}, {}, {}
    )
}

@Preview(name = "Bedtime — light", widthDp = 380, heightDp = 380)
@Composable
private fun BedtimeLight() = PreviewSurface(dark = false) {
    BedtimeCard(bedtime = TimeOfDay(22, 30), sleepMinutes = 8 * 60)
}

@Preview(name = "Bedtime — dark", widthDp = 380, heightDp = 420)
@Composable
private fun BedtimeDark() = PreviewSurface(dark = true) {
    BedtimeCard(bedtime = TimeOfDay(22, 30), sleepMinutes = 6 * 60 + 30)
    BedtimeNudge(visible = true, onStop = {})
}

@Preview(name = "Insights charts — light", widthDp = 380, heightDp = 460)
@Composable
private fun ChartsLight() = PreviewSurface(dark = false) { Charts() }

@Preview(name = "Insights charts — dark", widthDp = 380, heightDp = 460)
@Composable
private fun ChartsDark() = PreviewSurface(dark = true) { Charts() }

@Composable
private fun Charts() {
    val today = 20_000L
    val minutes = listOf(45L, 0L, 120L, 75L, 0L, 30L, 95L)
    FocusColumns(
        days = minutes.mapIndexed { index, m ->
            DayTotal(today - (6 - index), m * 60_000L)
        },
        todayEpochDay = today,
        dayLabel = { epochDay -> "MTWTFSS"[((epochDay % 7) + 7).toInt() % 7].toString() }
    )
    TaskBars(
        tasks = Insights.from(
            listOf(
                summary(today, "Flashcards", TimerSand.AMBER, 120),
                summary(today, "Question bank", TimerSand.AQUA, 75),
                summary(today - 1, "Essay draft", TimerSand.ROSE, 40),
                summary(today - 2, "Reading", TimerSand.OLIVE, 25)
            ),
            today = today
        ).tasks
    )
}

private fun summary(day: Long, name: String, sand: TimerSand, minutes: Long) = SessionSummary(
    epochDay = day,
    taskName = name,
    sand = sand,
    elapsedMillis = minutes * 60_000L,
    overtimeMillis = 0L,
    completed = true
)

@Preview(name = "All eight sands — light", widthDp = 380, heightDp = 200)
@Composable
private fun SandsLight() = PreviewSurface(dark = false) { Sands() }

@Preview(name = "All eight sands — dark", widthDp = 380, heightDp = 200)
@Composable
private fun SandsDark() = PreviewSurface(dark = true) { Sands() }

@Composable
private fun Sands() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        HourglassTheme.colors.allSands().forEach { (_, colour) ->
            HourglassGlass(
                progress = 0.4f,
                sandColor = colour,
                running = false,
                overtime = false,
                modifier = Modifier.size(width = 38.dp, height = 50.dp)
            )
        }
    }
}
