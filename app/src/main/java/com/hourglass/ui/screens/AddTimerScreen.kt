package com.hourglass.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.core.TimerKind
import com.hourglass.ui.components.HourglassGlass
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.components.Stepper
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.ui.theme.TimerPalette
import com.hourglass.ui.util.toHex
import com.hourglass.viewmodel.HourglassViewModel

/**
 * Creating a timer, with a live preview of the thing being made. The old form asked for
 * a name, two free-text numbers and a colour with no indication of what any of it would
 * look like; here the glass at the top is the actual card art, in the chosen colour.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddTimerScreen(
    onDone: () -> Unit,
    viewModel: HourglassViewModel = hiltViewModel()
) {
    val colors = HourglassTheme.colors

    var name by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(0) }
    var colour by remember { mutableStateOf(TimerPalette.first()) }
    var kind by remember { mutableStateOf(TimerKind.TASK) }
    var showNameError by remember { mutableStateOf(false) }

    val durationMillis = (hours * 3_600L + minutes * 60L) * 1_000L
    val canCreate = name.isNotBlank() && durationMillis > 0

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HourglassTopBar(
                title = stringResource(R.string.new_timer),
                onBack = onDone
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(Spacing.sm))

            HourglassGlass(
                progress = 0.32f,
                sandColor = colour,
                running = true,
                overtime = false,
                modifier = Modifier.size(width = 96.dp, height = 128.dp)
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = TimeFormat.compact(durationMillis),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(Spacing.xl))

            KindSelector(selected = kind, onSelect = { kind = it }, accent = colour)

            Spacer(Modifier.height(Spacing.xl))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    showNameError = false
                },
                label = {
                    Text(
                        stringResource(
                            if (kind == TimerKind.QUICKSAND) R.string.quick_task_name
                            else R.string.task_name
                        )
                    )
                },
                singleLine = true,
                isError = showNameError,
                supportingText = if (showNameError) {
                    { Text(stringResource(R.string.please_enter_a_name)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colour,
                    unfocusedBorderColor = colors.outline,
                    focusedLabelColor = colour,
                    cursorColor = colour
                )
            )

            Spacer(Modifier.height(Spacing.xl))

            FieldLabel(stringResource(R.string.duration))
            Spacer(Modifier.height(Spacing.md))
            DurationPicker(
                hours = hours,
                minutes = minutes,
                accent = colour,
                onHoursChange = { hours = it },
                onMinutesChange = { minutes = it }
            )

            Spacer(Modifier.height(Spacing.xl))

            FieldLabel(stringResource(R.string.colour))
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                TimerPalette.forEach { swatch ->
                    ColourSwatch(
                        colour = swatch,
                        selected = swatch == colour,
                        onSelect = { colour = swatch }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xxl))

            Button(
                onClick = {
                    if (!canCreate) {
                        showNameError = name.isBlank()
                        return@Button
                    }
                    viewModel.create(kind, name, hours, minutes, colour.toHex())
                    onDone()
                },
                enabled = canCreate,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colour,
                    contentColor = Color.White,
                    disabledContainerColor = colors.outline,
                    disabledContentColor = colors.textMuted
                )
            ) {
                Text(
                    text = stringResource(
                        if (kind == TimerKind.QUICKSAND) R.string.create_quicksand
                        else R.string.create_sand_timer
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Spacer(Modifier.height(Spacing.xxl))
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = HourglassTheme.colors.textMuted,
        modifier = Modifier.fillMaxWidth()
    )
}

/** Two mutually exclusive pills; the selected one carries the chosen sand colour. */
@Composable
private fun KindSelector(
    selected: TimerKind,
    onSelect: (TimerKind) -> Unit,
    accent: Color
) {
    val colors = HourglassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(colors.surfaceMuted)
            .padding(Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        TimerKind.entries.forEach { option ->
            val isSelected = option == selected
            val background by animateColorAsState(
                targetValue = if (isSelected) accent else Color.Transparent,
                animationSpec = tween(durationMillis = 220),
                label = "kind_background"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(CircleShape)
                    .background(background)
                    .selectable(
                        selected = isSelected,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .padding(vertical = Spacing.md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (option == TimerKind.QUICKSAND) R.string.quicksand
                        else R.string.sand_timer
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else colors.textSecondary
                )
            }
        }
    }
}

/** Steppers rather than free-text fields: fewer ways to enter an invalid duration. */
@Composable
private fun DurationPicker(
    hours: Int,
    minutes: Int,
    accent: Color,
    onHoursChange: (Int) -> Unit,
    onMinutesChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md)
    ) {
        Stepper(
            value = hours,
            label = stringResource(R.string.hours),
            range = 0..23,
            accent = accent,
            onChange = onHoursChange,
            modifier = Modifier.weight(1f)
        )
        Stepper(
            value = minutes,
            label = stringResource(R.string.minutes),
            range = 0..55,
            step = 5,
            accent = accent,
            onChange = onMinutesChange,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ColourSwatch(colour: Color, selected: Boolean, onSelect: () -> Unit) {
    val colors = HourglassTheme.colors
    val diameter by animateDpAsState(
        targetValue = if (selected) 46.dp else 40.dp,
        animationSpec = tween(durationMillis = 200),
        label = "swatch_size"
    )
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(CircleShape)
            .background(colour)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) colors.textPrimary.copy(alpha = 0.35f) else Color.Transparent,
                shape = CircleShape
            )
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onSelect
            ),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
