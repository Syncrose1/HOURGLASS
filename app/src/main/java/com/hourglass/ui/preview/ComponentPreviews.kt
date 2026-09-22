package com.hourglass.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hourglass.core.DayTotal
import com.hourglass.core.Desert
import com.hourglass.core.SessionSummary
import com.hourglass.core.TileDetail
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.core.TimerSand
import com.hourglass.ui.components.BedtimeTile
import com.hourglass.ui.components.DuneCanvas
import com.hourglass.ui.components.FocusColumns
import com.hourglass.ui.components.HourglassGlass
import com.hourglass.ui.components.TimerTile
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HourglassTheme.colors.backdrop)
                .padding(Spacing.md)
        ) { content() }
    }
}

/** The wall at three densities, which is the thing most worth eyeballing. */
@Composable
private fun Wall(count: Int, detail: TileDetail) {
    val cards = List(count) { index ->
        sample(
            id = index + 1L,
            name = listOf("Flashcards", "Question bank", "Essay", "Reading", "Email")[index % 5],
            sand = TimerSand.entries[index % TimerSand.entries.size],
            progress = (index % 5) / 5f,
            running = index == 1
        )
    }
    val columns = when {
        count <= 4 -> 2
        count <= 9 -> 3
        else -> 4
    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        cards.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                row.forEach { card ->
                    TimerTile(
                        card = card,
                        detail = detail,
                        onOpen = {},
                        onEdit = {},
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}

@Preview(name = "Wall, four tiles — light", widthDp = 380, heightDp = 620)
@Composable
private fun WallFourLight() = PreviewSurface(dark = false) { Wall(4, TileDetail.FULL) }

@Preview(name = "Wall, four tiles — dark", widthDp = 380, heightDp = 620)
@Composable
private fun WallFourDark() = PreviewSurface(dark = true) { Wall(4, TileDetail.FULL) }

@Preview(name = "Wall, nine tiles — light", widthDp = 380, heightDp = 620)
@Composable
private fun WallNineLight() = PreviewSurface(dark = false) { Wall(9, TileDetail.COMPACT) }

@Preview(name = "Wall, sixteen tiles — dark", widthDp = 380, heightDp = 620)
@Composable
private fun WallManyDark() = PreviewSurface(dark = true) { Wall(16, TileDetail.MINIMAL) }

@Preview(name = "Bedtime tile — light", widthDp = 200, heightDp = 200)
@Composable
private fun BedtimeTileLight() = PreviewSurface(dark = false) {
    BedtimeTile(
        bedtime = TimeOfDay(22, 30),
        minutesUntil = 139,
        detail = TileDetail.FULL,
        onClick = {},
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(name = "Bedtime tile, winding down — dark", widthDp = 200, heightDp = 200)
@Composable
private fun BedtimeTileDark() = PreviewSurface(dark = true) {
    BedtimeTile(
        bedtime = TimeOfDay(22, 30),
        minutesUntil = 42,
        detail = TileDetail.FULL,
        onClick = {},
        modifier = Modifier.fillMaxSize()
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

private fun sampleDesert() = Desert.from(
    (0..22).map { index ->
        summary(
            day = 20_000L - index,
            name = "Task ${index % 4}",
            sand = TimerSand.entries[index % TimerSand.entries.size],
            minutes = 25L + (index % 5) * 20L
        )
    }
)

@Preview(name = "Desert, day", widthDp = 380, heightDp = 300)
@Composable
private fun DesertDay() = PreviewSurface(dark = false) {
    DuneCanvas(desert = sampleDesert(), nightMode = false, modifier = Modifier.fillMaxSize())
}

@Preview(name = "Desert, night", widthDp = 380, heightDp = 300)
@Composable
private fun DesertNight() = PreviewSurface(dark = true) {
    DuneCanvas(desert = sampleDesert(), nightMode = true, modifier = Modifier.fillMaxSize())
}

@Preview(name = "Desert, empty", widthDp = 380, heightDp = 300)
@Composable
private fun DesertEmpty() = PreviewSurface(dark = false) {
    DuneCanvas(
        desert = Desert.from(emptyList()),
        nightMode = false,
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(name = "Week columns — light", widthDp = 380, heightDp = 220)
@Composable
private fun ColumnsLight() = PreviewSurface(dark = false) {
    val today = 20_000L
    FocusColumns(
        days = listOf(45L, 0L, 120L, 75L, 0L, 30L, 95L).mapIndexed { index, minutes ->
            DayTotal(today - (6 - index), minutes * 60_000L)
        },
        todayEpochDay = today,
        dayLabel = { epochDay -> "MTWTFSS"[(((epochDay % 7) + 7) % 7).toInt()].toString() }
    )
}

@Preview(name = "All eight sands — light", widthDp = 380, heightDp = 140)
@Composable
private fun SandsLight() = PreviewSurface(dark = false) { Sands() }

@Preview(name = "All eight sands — dark", widthDp = 380, heightDp = 140)
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
