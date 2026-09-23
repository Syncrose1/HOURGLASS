package com.hourglass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.TileLayout
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerRef
import com.hourglass.core.Treemap
import com.hourglass.ui.components.ActionTile
import com.hourglass.ui.components.BedtimeBar
import com.hourglass.ui.components.SandGlass
import com.hourglass.ui.components.SandDetail
import com.hourglass.ui.components.TimerTile
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.HomeState
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.viewmodel.SettingsViewModel
import com.hourglass.viewmodel.TimerCard

/**
 * Everything, on one screen, always.
 *
 * Three bands and no scrolling anywhere: the day as a bar across the top, the timers
 * carving up everything under it, and the things you only touch when idle along the
 * bottom.
 *
 * The timers are a squarified treemap weighted by how long each one is allocated, so
 * the wall is a picture of how the day is committed — an afternoon's work is visibly
 * a bigger piece than a ten-minute errand — and adding a timer redivides the space
 * instead of extending it downward.
 *
 * Tapping a timer hands it the whole screen. Everything else lives here, and is
 * therefore only reachable when nothing is running.
 */
@Composable
fun HomeScreen(
    onAddTimer: () -> Unit,
    onEditTimer: (TimerRef) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesert: () -> Unit,
    onOpenSouls: () -> Unit,
    viewModel: HourglassViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val home by viewModel.homeState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        viewModel.completions.collect {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val focused: TimerCard? = home.focusedCard

    // Kept so the focus view still has something to draw while it fades out, after
    // the timer has already been dropped from the state it reads.
    var lastFocused by remember { mutableStateOf<TimerCard?>(null) }
    LaunchedEffect(focused) { if (focused != null) lastFocused = focused }

    Box(modifier = Modifier.fillMaxSize()) {
        Wall(
            home = home,
            bedtime = settings.bedtime,
            onOpenTimer = { card ->
                haptics.tick()
                viewModel.focus(card.ref)
            },
            onEditTimer = onEditTimer,
            onAddTimer = onAddTimer,
            onOpenSettings = onOpenSettings,
            onOpenDesert = onOpenDesert,
            onOpenSouls = onOpenSouls
        )

        AnimatedVisibility(visible = focused != null, enter = fadeIn(), exit = fadeOut()) {
            val card = focused ?: lastFocused
            if (card != null) {
                FocusScreen(
                    card = card,
                    onPause = {
                        haptics.tick()
                        viewModel.pauseAndClose()
                    },
                    onFinish = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun Wall(
    home: HomeState,
    bedtime: TimeOfDay,
    onOpenTimer: (TimerCard) -> Unit,
    onEditTimer: (TimerRef) -> Unit,
    onAddTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesert: () -> Unit,
    onOpenSouls: () -> Unit
) {
    val timers = home.sandTimers + home.quicksand

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        BedtimeBar(
            bedtime = bedtime,
            minutesUntil = home.minutesUntilBedtime,
            onClick = onOpenSettings
        )

        Box(modifier = Modifier.weight(1f)) {
            if (timers.isEmpty()) {
                EmptyWall()
            } else {
                TimerTreemap(
                    timers = timers,
                    onOpenTimer = onOpenTimer,
                    onEditTimer = onEditTimer
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(ACTION_ROW_HEIGHT),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            ActionTile(
                icon = Icons.Rounded.Terrain,
                label = stringResource(R.string.desert),
                onClick = onOpenDesert,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ActionTile(
                icon = Icons.Rounded.Groups,
                label = stringResource(R.string.souls),
                onClick = onOpenSouls,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ActionTile(
                icon = Icons.Rounded.Settings,
                label = stringResource(R.string.settings),
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            ActionTile(
                icon = Icons.Rounded.Add,
                label = stringResource(R.string.new_timer),
                onClick = onAddTimer,
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

/**
 * Cells are weighted by allocated duration, not by time remaining: a running timer
 * that shrank as it counted down would make the whole wall crawl.
 */
@Composable
private fun TimerTreemap(
    timers: List<TimerCard>,
    onOpenTimer: (TimerCard) -> Unit,
    onEditTimer: (TimerRef) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val width = maxWidth
        val height = maxHeight
        val cells = remember(timers, width, height) {
            Treemap.squarify(
                items = timers,
                width = width.value,
                height = height.value,
                minWeight = MIN_CELL_WEIGHT
            ) { it.durationMillis / 60_000f }
        }

        cells.forEach { cell ->
            val shortEdge = min(cell.width.dp, cell.height.dp)
            TimerTile(
                card = cell.item,
                detail = TileLayout.detailFor(shortEdge.value),
                onOpen = { onOpenTimer(cell.item) },
                onEdit = { onEditTimer(cell.item.ref) },
                modifier = Modifier
                    .offset(x = cell.x.dp, y = cell.y.dp)
                    .size(width = cell.width.dp, height = cell.height.dp)
                    .padding(GUTTER)
            )
        }
    }
}

@Composable
private fun EmptyWall() {
    val colors = HourglassTheme.colors
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Big enough to simulate properly, so even the empty state is real sand.
        SandGlass(
            progress = EMPTY_STATE_PROGRESS,
            sand = colors.accent,
            running = true,
            overtime = false,
            detail = SandDetail.SMALL,
            modifier = Modifier.size(width = 108.dp, height = 144.dp)
        )
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = stringResource(R.string.your_day_awaits),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.empty_state_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xxl)
        )
    }
}

/** Enough sand left in the top that the empty state's glass is visibly running. */
private const val EMPTY_STATE_PROGRESS = 0.25f

private val ACTION_ROW_HEIGHT = 64.dp

/** Half the gap between cells; each side contributes one. */
private val GUTTER = 3.dp

/** A five-minute floor, so the shortest quicksand still gets a cell worth tapping. */
private const val MIN_CELL_WEIGHT = 5f

/** Every tap answers with the same short tick. */
private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
