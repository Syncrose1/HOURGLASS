package com.hourglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.components.FocusColumns
import com.hourglass.ui.components.HeroFigure
import com.hourglass.ui.components.HourglassMark
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.components.SectionCaption
import com.hourglass.ui.components.StatTile
import com.hourglass.ui.components.TaskBars
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.InsightsViewModel
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What the session log has been quietly collecting all along.
 *
 * The README promised habit tracking and the data was being written from the start;
 * this is the screen that finally reads it back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    onBack: () -> Unit,
    viewModel: InsightsViewModel = hiltViewModel()
) {
    val insights by viewModel.insights.collectAsStateWithLifecycle()
    val colors = HourglassTheme.colors
    val dayLabels = remember { weekdayInitials() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HourglassTopBar(
                title = stringResource(R.string.insights),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg)
        ) {
            if (insights.isEmpty) {
                EmptyInsights()
                return@Column
            }

            Spacer(Modifier.height(Spacing.sm))

            HeroFigure(
                value = TimeFormat.compact(insights.windowMillis),
                label = stringResource(R.string.banked_this_week).uppercase()
            )

            Spacer(Modifier.height(Spacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                StatTile(
                    label = stringResource(R.string.sessions),
                    value = insights.sessionCount.toString(),
                    support = stringResource(R.string.overran_count, insights.overrunCount),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.on_time),
                    value = stringResource(
                        R.string.percent,
                        (insights.completionRate * 100).roundToInt()
                    ),
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = stringResource(R.string.streak),
                    value = stringResource(R.string.streak_days, insights.streakDays),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            SectionCaption(
                text = stringResource(R.string.daily_focus),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(Spacing.lg))
            FocusColumns(
                days = insights.days,
                todayEpochDay = insights.days.lastOrNull()?.epochDay ?: 0L,
                dayLabel = { epochDay -> dayLabels[weekdayIndex(epochDay)] }
            )

            if (insights.tasks.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xxl))
                SectionCaption(
                    text = stringResource(R.string.where_it_went),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Spacing.lg))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.large)
                        .background(colors.surface)
                        .border(1.dp, colors.outline, MaterialTheme.shapes.large)
                        .padding(Spacing.lg)
                ) {
                    TaskBars(tasks = insights.tasks)
                }
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun EmptyInsights() {
    val colors = HourglassTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HourglassMark(size = 72.dp, animated = true)
        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = stringResource(R.string.no_sessions_yet),
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = stringResource(R.string.no_sessions_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Spacing.xl)
        )
    }
}

/**
 * Weekday initials in the device's locale, indexed Sunday-first to match
 * [Calendar.DAY_OF_WEEK].
 */
private fun weekdayInitials(): List<String> {
    val short = DateFormatSymbols.getInstance().shortWeekdays // index 1..7; 0 is unused
    return (1..7).map { index ->
        short.getOrNull(index)?.take(1)?.uppercase(Locale.getDefault()).orEmpty()
    }
}

/** Sunday = 0, matching the list [weekdayInitials] returns. */
private fun weekdayIndex(epochDay: Long): Int {
    // 1970-01-01 was a Thursday.
    val thursdayOffset = 4L
    return (((epochDay + thursdayOffset) % 7 + 7) % 7).toInt()
}
