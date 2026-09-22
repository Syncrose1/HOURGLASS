package com.hourglass.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.BuildConfig
import com.hourglass.R
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.ui.components.HourglassMark
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.components.SectionCaption
import com.hourglass.ui.components.Stepper
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.SettingsViewModel
import java.util.Locale

/** Healthy sleep, below which the projection card flags the shortfall. */
private const val RESTFUL_SLEEP_MINUTES = 7 * 60

/** Full width of the sleep bar, so nine hours fills it. */
private const val SLEEP_BAR_MAX_MINUTES = 9 * 60f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val colors = HourglassTheme.colors
    var editing by remember { mutableStateOf<Editing?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HourglassTopBar(
                title = stringResource(R.string.settings),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SectionCaption(
                text = stringResource(R.string.rest),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Spacing.md)
            )

            TimeRow(
                label = stringResource(R.string.bedtime),
                value = settings.bedtime,
                expanded = editing == Editing.BEDTIME,
                onToggle = {
                    editing = if (editing == Editing.BEDTIME) null else Editing.BEDTIME
                },
                onChange = viewModel::setBedtime
            )

            Spacer(Modifier.height(Spacing.md))

            TimeRow(
                label = stringResource(R.string.wake_time),
                value = settings.wakeTime,
                expanded = editing == Editing.WAKE,
                onToggle = { editing = if (editing == Editing.WAKE) null else Editing.WAKE },
                onChange = viewModel::setWakeTime
            )

            Spacer(Modifier.height(Spacing.md))

            ToggleRow(
                title = stringResource(R.string.day_notice),
                body = stringResource(R.string.day_notice_body),
                checked = settings.dayNotice,
                onCheckedChange = viewModel::setDayNotice
            )

            Spacer(Modifier.height(Spacing.lg))

            SleepProjection(minutes = settings.sleepMinutes)

            Spacer(Modifier.height(Spacing.xl))

            SectionCaption(
                text = stringResource(R.string.about),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.large)
                    .background(colors.surface)
                    .border(1.dp, colors.outline, MaterialTheme.shapes.large)
                    .padding(Spacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                HourglassMark(size = 44.dp, animated = true)
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.about_hourglass),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = stringResource(R.string.about_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(Spacing.md))
                Text(
                    text = stringResource(R.string.version, BuildConfig.VERSION_NAME),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

private enum class Editing { BEDTIME, WAKE }

/** A labelled switch on the same card chrome as everything else on this screen. */
@Composable
private fun ToggleRow(
    title: String,
    body: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = HourglassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.outline, MaterialTheme.shapes.large)
            .padding(Spacing.xl),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        Spacer(Modifier.width(Spacing.md))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                uncheckedTrackColor = colors.surfaceMuted,
                uncheckedBorderColor = colors.outlineStrong
            )
        )
    }
}

/** A time, shown large, with an inline stepper editor that slides out when tapped. */
@Composable
private fun TimeRow(
    label: String,
    value: TimeOfDay,
    expanded: Boolean,
    onToggle: () -> Unit,
    onChange: (TimeOfDay) -> Unit
) {
    val colors = HourglassTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.outline, MaterialTheme.shapes.large)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(Spacing.xl),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textMuted
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = value.formatFriendly(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = stringResource(
                    if (expanded) R.string.collapse else R.string.expand
                ),
                tint = colors.textSecondary
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(200)) + expandVertically(tween(220)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(180))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.xl),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Stepper(
                    value = value.hour,
                    label = stringResource(R.string.hour),
                    range = 0..23,
                    accent = colors.accent,
                    onChange = { onChange(TimeOfDay(it, value.minute)) },
                    modifier = Modifier.weight(1f),
                    formatValue = { String.format(Locale.US, "%02d", it) }
                )
                Stepper(
                    value = value.minute,
                    label = stringResource(R.string.minutes),
                    range = 0..55,
                    step = 5,
                    accent = colors.accent,
                    onChange = { onChange(TimeOfDay(value.hour, it)) },
                    modifier = Modifier.weight(1f),
                    formatValue = { String.format(Locale.US, "%02d", it) }
                )
            }
        }
    }
}

/** How long the night is, and a nudge when it is shorter than it should be. */
@Composable
private fun SleepProjection(minutes: Int) {
    val colors = HourglassTheme.colors
    val short = minutes < RESTFUL_SLEEP_MINUTES
    val tint = if (short) colors.overtime else colors.accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.verticalGradient(
                    listOf(tint.copy(alpha = 0.14f), tint.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, tint.copy(alpha = 0.3f), MaterialTheme.shapes.large)
            .padding(Spacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.projected_sleep).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = tint
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = Bedtime.describeSleep(minutes),
            style = MaterialTheme.typography.displayMedium,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(Spacing.md))
        SleepBar(minutes = minutes, tint = tint)
        Spacer(Modifier.height(Spacing.md))
        Text(
            text = stringResource(
                if (short) R.string.sleep_short_hint else R.string.sleep_good_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** Nine hours across the full width, with a marker at the seven-hour line. */
@Composable
private fun SleepBar(minutes: Int, tint: Color) {
    val colors = HourglassTheme.colors
    val fraction = (minutes / SLEEP_BAR_MAX_MINUTES).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(colors.outline)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(8.dp)
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(tint.copy(alpha = 0.6f), tint)))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(RESTFUL_SLEEP_MINUTES / SLEEP_BAR_MAX_MINUTES)
                .height(8.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(8.dp)
                    .background(colors.surface)
            )
        }
    }
}
