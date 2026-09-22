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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.TileLayout
import com.hourglass.core.TimeOfDay
import com.hourglass.core.TimerRef
import com.hourglass.ui.components.ActionTile
import com.hourglass.ui.components.BedtimeTile
import com.hourglass.ui.components.TimerTile
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.HomeState
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.viewmodel.SettingsViewModel
import com.hourglass.viewmodel.TimerCard

/**
 * Everything, on one screen, always.
 *
 * There is no scrolling here and no scrolling anywhere it can be avoided: however many
 * timers exist, they tile into the space available and the tiles get smaller. That is
 * the point rather than a limitation — a wall that visibly gets denser is an argument
 * for having fewer timers, which a scrolling list never makes.
 *
 * Tapping a timer hands the whole screen over to it. Everything else — settings, the
 * desert, adding and editing — lives here, and is therefore only reachable when
 * nothing is running.
 */
@Composable
fun HomeScreen(
    onAddTimer: () -> Unit,
    onEditTimer: (TimerRef) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesert: () -> Unit,
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

    Box(modifier = Modifier.fillMaxSize()) {
        TileWall(
            home = home,
            bedtimeMinutes = home.minutesUntilBedtime,
            bedtime = settings.bedtime,
            onOpenTimer = { card ->
                haptics.tick()
                viewModel.focus(card.ref)
            },
            onEditTimer = onEditTimer,
            onAddTimer = onAddTimer,
            onOpenSettings = onOpenSettings,
            onOpenDesert = onOpenDesert
        )

        AnimatedVisibility(
            visible = focused != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            // Held separately so the card keeps rendering through the exit animation.
            val card = focused ?: home.lastFocusedCard
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
private fun TileWall(
    home: HomeState,
    bedtimeMinutes: Int,
    bedtime: TimeOfDay,
    onOpenTimer: (TimerCard) -> Unit,
    onEditTimer: (TimerRef) -> Unit,
    onAddTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDesert: () -> Unit
) {
    val timers = home.sandTimers + home.quicksand
    // Bedtime leads, then the timers, then the things you only reach when idle.
    val tileCount = timers.size + UTILITY_TILES

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(Spacing.md)
    ) {
        val aspect = if (maxHeight.value > 0f) maxWidth.value / maxHeight.value else 0.6f
        val shape = TileLayout.shapeFor(tileCount, aspect)
        val tileWidth = maxWidth.value / shape.columns
        val detail = TileLayout.detailFor(tileWidth)

        val cells: List<Tile> = buildList {
            add(Tile.Bedtime)
            timers.forEach { add(Tile.Timer(it)) }
            add(Tile.Desert)
            add(Tile.Settings)
            add(Tile.Add)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            for (row in 0 until shape.rows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    for (column in 0 until shape.columns) {
                        val index = row * shape.columns + column
                        if (index < cells.size) {
                            val cell = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                            when (val tile = cells[index]) {
                                Tile.Bedtime -> BedtimeTile(
                                    bedtime = bedtime,
                                    minutesUntil = bedtimeMinutes,
                                    detail = detail,
                                    onClick = onOpenSettings,
                                    modifier = cell
                                )

                                is Tile.Timer -> TimerTile(
                                    card = tile.card,
                                    detail = detail,
                                    onOpen = { onOpenTimer(tile.card) },
                                    onEdit = { onEditTimer(tile.card.ref) },
                                    modifier = cell
                                )

                                Tile.Desert -> ActionTile(
                                    icon = Icons.Rounded.Terrain,
                                    label = stringResource(R.string.desert),
                                    detail = detail,
                                    onClick = onOpenDesert,
                                    modifier = cell
                                )

                                Tile.Settings -> ActionTile(
                                    icon = Icons.Rounded.Settings,
                                    label = stringResource(R.string.settings),
                                    detail = detail,
                                    onClick = onOpenSettings,
                                    modifier = cell
                                )

                                Tile.Add -> ActionTile(
                                    icon = Icons.Rounded.Add,
                                    label = stringResource(R.string.new_timer),
                                    detail = detail,
                                    onClick = onAddTimer,
                                    modifier = cell
                                )
                            }
                        } else {
                            // Keeps the last row's tiles the same size as every other
                            // row's rather than stretching them across the gap.
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        }
                    }
                }
            }
        }
    }
}

/** What can occupy a cell of the wall. */
private sealed interface Tile {
    data object Bedtime : Tile
    data class Timer(val card: TimerCard) : Tile
    data object Desert : Tile
    data object Settings : Tile
    data object Add : Tile
}

/** Bedtime, desert, settings, add. */
private const val UTILITY_TILES = 4

/** Every tap answers with the same short tick. */
private fun HapticFeedback.tick() = performHapticFeedback(HapticFeedbackType.TextHandleMove)
