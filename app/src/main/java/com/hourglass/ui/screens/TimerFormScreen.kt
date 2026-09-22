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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import com.hourglass.ui.world.WorldView
import com.hourglass.ui.world.WorldRegistry
import com.hourglass.core.world.WorldKind
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.core.TimerKind
import com.hourglass.core.TimerRef
import com.hourglass.core.TimerSand
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.components.Stepper
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.HourglassViewModel

/**
 * One form for creating and for editing.
 *
 * The glass at the top is the real card art in the chosen sand, so the choice is made
 * against the thing being made rather than against a row of abstract swatches.
 * Durations and names are constrained rather than free text — there is no way to
 * enter something the timer cannot honour.
 */
private val PREVIEW_HEIGHT = 240.dp

/** A preview's demo timer, start to finish. */
private const val PREVIEW_CYCLE_MILLIS = 24_000

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TimerFormScreen(
    onDone: () -> Unit,
    editing: TimerRef? = null,
    viewModel: HourglassViewModel = hiltViewModel()
) {
    val colors = HourglassTheme.colors

    var name by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(1) }
    var minutes by remember { mutableIntStateOf(0) }
    var sand by remember { mutableStateOf(TimerSand.DEFAULT) }
    var world by remember { mutableStateOf(WorldKind.DEFAULT) }
    var kind by remember { mutableStateOf(editing?.kind ?: TimerKind.TASK) }
    var showNameError by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(editing == null) }

    LaunchedEffect(editing) {
        val ref = editing ?: return@LaunchedEffect
        val definition = viewModel.definition(ref)
        if (definition != null) {
            name = definition.name
            hours = (definition.durationMillis / 3_600_000L).toInt()
            minutes = ((definition.durationMillis % 3_600_000L) / 60_000L).toInt()
            sand = definition.sand
            world = definition.world
            kind = ref.kind
        }
        loaded = true
    }

    val colour = colors.sand(sand)
    val durationMillis = (hours * 3_600L + minutes * 60L) * 1_000L
    val canSubmit = loaded && name.isNotBlank() && durationMillis > 0
    val onColour = if (colour.luminance() > 0.55f) colors.textPrimary else Color.White

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            HourglassTopBar(
                title = stringResource(
                    if (editing == null) R.string.new_timer else R.string.edit_timer
                ),
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

            // The chosen world, running on a demo clock, in the chosen colour. You pick a
            // world by watching one rather than by reading its name.
            WorldPreview(
                kind = world,
                accent = colour,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(PREVIEW_HEIGHT)
                    .clip(MaterialTheme.shapes.large)
            )

            Spacer(Modifier.height(Spacing.sm))

            Text(
                text = TimeFormat.compact(durationMillis),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimary
            )

            Spacer(Modifier.height(Spacing.xl))

            // Which family a timer belongs to is part of its identity, so it is fixed
            // once created — changing it would orphan the timer's session history.
            if (editing == null) {
                KindSelector(selected = kind, onSelect = { kind = it }, accent = colour)
                Spacer(Modifier.height(Spacing.md))
            }

            WorldSelector(selected = world, onSelect = { world = it }, accent = colour)
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                Stepper(
                    value = hours,
                    label = stringResource(R.string.hours),
                    range = 0..23,
                    accent = colour,
                    onChange = { hours = it },
                    modifier = Modifier.weight(1f)
                )
                Stepper(
                    value = minutes,
                    label = stringResource(R.string.minutes),
                    range = 0..55,
                    step = 5,
                    accent = colour,
                    onChange = { minutes = it },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            FieldLabel(stringResource(R.string.colour))
            Spacer(Modifier.height(Spacing.md))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.md)
            ) {
                colors.allSands().forEach { (candidate, swatch) ->
                    SandSwatch(
                        swatch = swatch,
                        selected = candidate == sand,
                        onSelect = { sand = candidate }
                    )
                }
            }

            Spacer(Modifier.height(Spacing.xxl))

            Button(
                onClick = {
                    if (!canSubmit) {
                        showNameError = name.isBlank()
                        return@Button
                    }
                    if (editing == null) {
                        viewModel.create(kind, name, hours, minutes, sand, world)
                    } else {
                        viewModel.update(editing, name, hours, minutes, sand, world)
                    }
                    onDone()
                },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colour,
                    contentColor = onColour,
                    disabledContainerColor = colors.outline,
                    disabledContentColor = colors.textMuted
                )
            ) {
                Text(
                    text = stringResource(
                        when {
                            editing != null -> R.string.save_changes
                            kind == TimerKind.QUICKSAND -> R.string.create_quicksand
                            else -> R.string.create_sand_timer
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            if (editing != null) {
                Spacer(Modifier.height(Spacing.md))
                TextButton(
                    onClick = {
                        viewModel.archive(editing)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.remove_timer, name),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.textMuted
                    )
                }
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

/** Two mutually exclusive pills; the selected one carries the chosen sand. */
@Composable
private fun KindSelector(
    selected: TimerKind,
    onSelect: (TimerKind) -> Unit,
    accent: Color
) {
    val colors = HourglassTheme.colors
    val onAccent = if (accent.luminance() > 0.55f) colors.textPrimary else Color.White
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
                    color = if (isSelected) onAccent else colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun SandSwatch(swatch: Color, selected: Boolean, onSelect: () -> Unit) {
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
            .background(swatch)
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = if (selected) colors.textPrimary.copy(alpha = 0.35f) else Color.Transparent,
                shape = CircleShape
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                // Picked from the swatch's own luminance so the tick always clears it.
                tint = if (swatch.luminance() > 0.55f) colors.textPrimary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Which world the timer builds. Same shape as the kind pills, so the form stays one voice. */
@Composable
private fun WorldSelector(
    selected: WorldKind,
    onSelect: (WorldKind) -> Unit,
    accent: Color
) {
    val colors = HourglassTheme.colors
    val onAccent = if (accent.luminance() > 0.55f) colors.textPrimary else Color.White
    // Rows of three: there are more worlds than fit on one line.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceMuted)
            .padding(Spacing.xs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs)
    ) {
        WorldKind.entries.chunked(WORLDS_PER_ROW).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
            ) {
                row.forEach { option ->
                    WorldPill(
                        option = option,
                        isSelected = option == selected,
                        accent = accent,
                        onAccent = onAccent,
                        onSelect = onSelect,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Keep a short last row's pills the same width as the rest.
                repeat(WORLDS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private const val WORLDS_PER_ROW = 3

@Composable
private fun WorldPill(
    option: WorldKind,
    isSelected: Boolean,
    accent: Color,
    onAccent: Color,
    onSelect: (WorldKind) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val background by animateColorAsState(
        targetValue = if (isSelected) accent else Color.Transparent,
        animationSpec = tween(durationMillis = 220),
        label = "world_background"
    )
    Box(
        modifier = modifier
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
                when (option) {
                    WorldKind.MINE -> R.string.world_mine
                    WorldKind.RIVER -> R.string.world_river
                    WorldKind.ANTS -> R.string.world_ants
                    WorldKind.ISLAND -> R.string.world_island
                    WorldKind.FOREST -> R.string.world_forest
                }
            ),
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) onAccent else colors.textSecondary
        )
    }
}

/**
 * A throwaway world of the chosen kind, played through a short demo timer on repeat.
 * Not registered anywhere: it is a sample, not the world the timer will get.
 */
@Composable
private fun WorldPreview(kind: WorldKind, accent: Color, modifier: Modifier = Modifier) {
    var cycle by remember { mutableIntStateOf(0) }
    val session = remember(kind, cycle) { WorldRegistry.sample(kind) }
    val started = remember(session) { System.currentTimeMillis() }
    var progress by remember(session) { mutableStateOf(0f) }

    LaunchedEffect(session) {
        while (true) {
            withFrameMillis { }
            progress = (System.currentTimeMillis() - started).toFloat() / PREVIEW_CYCLE_MILLIS
            if (progress >= 1.1f) {
                cycle++
                return@LaunchedEffect
            }
        }
    }

    WorldView(
        session = session,
        mineral = accent,
        running = true,
        timerProgress = progress,
        modifier = modifier
    )
}
