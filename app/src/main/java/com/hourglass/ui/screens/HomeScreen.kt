package com.hourglass.ui.screens

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.components.BedtimeCard
import com.hourglass.ui.components.HourglassMark
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.components.QuicksandCard
import com.hourglass.ui.components.SandTimerCard
import com.hourglass.ui.components.SectionCaption
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.HourglassViewModel
import com.hourglass.viewmodel.SettingsViewModel

/**
 * The day at a glance: what time is left before bed, what has been banked so far, and
 * every timer waiting to be started.
 *
 * The scaffold is transparent — the sand backdrop is painted once, by [com.hourglass.MainActivity],
 * so it stays continuous while navigating.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onAddTimer: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HourglassViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val home by viewModel.homeState.collectAsStateWithLifecycle()
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val colors = HourglassTheme.colors

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HourglassTopBar(
                title = stringResource(R.string.app_name),
                showMark = true,
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = colors.textSecondary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddTimer,
                containerColor = colors.accent,
                contentColor = colors.onAccent,
                shape = MaterialTheme.shapes.medium,
                icon = { Icon(imageVector = Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.new_timer)) }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
                // Clear the extended FAB so the last card is never trapped beneath it.
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item(key = "bedtime") {
                BedtimeCard(
                    bedtime = settings.bedtime,
                    sleepMinutes = settings.sleepMinutes
                )
            }

            if (home.focusedTodayMillis > 0) {
                item(key = "focus_summary") {
                    FocusSummary(millis = home.focusedTodayMillis)
                }
            }

            if (home.isEmpty) {
                item(key = "empty") { EmptyState() }
            }

            if (home.sandTimers.isNotEmpty()) {
                item(key = "sand_caption") {
                    SectionCaption(
                        text = stringResource(R.string.today_sand_timers),
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                items(home.sandTimers, key = { "sand_${it.ref.id}" }) { card ->
                    SandTimerCard(
                        card = card,
                        onStart = { viewModel.start(card.ref) },
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStop = viewModel::stop,
                        onRemove = { viewModel.archive(card.ref) },
                        modifier = Modifier.animateItemPlacement(tween(durationMillis = 280))
                    )
                }
            }

            if (home.quicksand.isNotEmpty()) {
                item(key = "quicksand_caption") {
                    SectionCaption(
                        text = stringResource(R.string.quicksand),
                        modifier = Modifier.padding(top = Spacing.sm)
                    )
                }
                items(home.quicksand, key = { "quick_${it.ref.id}" }) { card ->
                    QuicksandCard(
                        card = card,
                        onStart = { viewModel.start(card.ref) },
                        onPause = viewModel::pause,
                        onResume = viewModel::resume,
                        onStop = viewModel::stop,
                        onRemove = { viewModel.archive(card.ref) },
                        modifier = Modifier.animateItemPlacement(tween(durationMillis = 280))
                    )
                }
            }
        }
    }
}

/** A single line of habit feedback: what the session log adds up to today. */
@Composable
private fun FocusSummary(millis: Long) {
    val colors = HourglassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HourglassMark(size = 18.dp, tint = colors.accent)
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = stringResource(R.string.focused_today, TimeFormat.compact(millis)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun EmptyState() {
    val colors = HourglassTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HourglassMark(size = 88.dp, animated = true)
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
            modifier = Modifier.padding(horizontal = Spacing.xl)
        )
    }
}
