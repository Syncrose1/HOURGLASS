package com.hourglass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.core.TileDetail
import com.hourglass.core.TimeFormat
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.ui.world.WorldRegistry
import com.hourglass.ui.world.WorldView
import com.hourglass.viewmodel.TimerCard

/**
 * One cell of the treemap.
 *
 * A tile has no controls. Tapping it opens the timer full-screen and starts it; that
 * is the only thing it does, because a running timer should not be something you can
 * fiddle with from a wall. Editing is a long press, which is not a gesture you make
 * by accident.
 *
 * Cells come in whatever shape the carve-up gives them, so everything sizes off the
 * short edge and the contents thin out as the cell shrinks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TimerTile(
    card: TimerCard,
    detail: TileDetail,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors
    val sand = colors.sand(card.sand)
    val active = !card.isIdle

    val warmth by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 480),
        label = "tile_warmth"
    )

    TileShell(
        modifier = modifier,
        accent = sand,
        active = active,
        onClick = onOpen,
        onLongClick = onEdit,
        description = buildString {
            append(card.name)
            append(", ")
            append(TimeFormat.compact(card.remainingMillis))
            if (card.isOvertime) append(", ${stringResource(R.string.overtime)}")
            if (card.isPaused) append(", ${stringResource(R.string.paused)}")
        },
        // Each tile carries a wash of its own sand, so the wall reads as a set of
        // different materials rather than a grid of identical grey boxes.
        background = Brush.verticalGradient(
            listOf(
                lerp(colors.surfaceMuted, sand, 0.10f + 0.14f * warmth),
                lerp(colors.surfaceMuted, sand, 0.04f + 0.07f * warmth)
            )
        )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // The timer's own world. Idle, it is the untouched ground this session
            // will dig; running, it is being dug.
            val session = remember(card.ref, card.sessionStartedAt, card.durationMillis, card.world) {
                WorldRegistry.obtain(card.ref, card.sessionStartedAt, card.durationMillis, card.world)
            }
            WorldView(
                session = session,
                mineral = sand,
                running = card.isRunning,
                timerProgress = card.timerProgress,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
            )

            if (detail.showsTime) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = TimeFormat.signedClock(card.remainingMillis),
                    style = if (detail == TileDetail.FULL) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    color = if (card.isOvertime) colors.overtime else colors.textPrimary,
                    maxLines = 1
                )
            }

            if (detail.showsName) {
                Text(
                    text = card.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.sm)
                )
            }
        }

        if (card.isPaused) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.sm)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.textMuted)
            )
        }
    }
}

/** A utility control: the desert, settings, adding a timer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors

    TileShell(
        modifier = modifier,
        accent = colors.textSecondary,
        active = false,
        onClick = onClick,
        onLongClick = null,
        description = label
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shared tile chrome, so every cell on the wall sits at the same depth. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TileShell(
    accent: Color,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    description: String,
    modifier: Modifier = Modifier,
    background: Brush? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = HourglassTheme.colors
    val shape = MaterialTheme.shapes.medium

    Box(
        modifier = modifier
            .clip(shape)
            .then(
                if (background != null) Modifier.background(background)
                else Modifier.background(colors.surfaceMuted)
            )
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) accent.copy(alpha = 0.9f) else colors.outline,
                shape = shape
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = description }
            .padding(Spacing.xs),
        content = content
    )
}

internal fun lerp(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)
