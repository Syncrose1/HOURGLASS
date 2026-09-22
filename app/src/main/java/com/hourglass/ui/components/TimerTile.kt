package com.hourglass.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hourglass.core.TileDetail
import com.hourglass.R
import com.hourglass.core.TimeFormat
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import com.hourglass.viewmodel.TimerCard

/**
 * One cell of the wall.
 *
 * A tile has no controls at all. Tapping it opens the timer full-screen and starts it;
 * that is the only thing it does, because a running timer should not be something you
 * can fiddle with from a grid. Editing is a long press, which is deliberately not a
 * gesture you make by accident.
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

    val lift by animateFloatAsState(
        targetValue = if (card.isRunning) 1.03f else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "tile_lift"
    )

    TileShell(
        modifier = modifier.scale(lift),
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
        }
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            HourglassGlass(
                progress = card.progress,
                sandColor = sand,
                running = card.isRunning,
                overtime = card.isOvertime,
                showFrame = detail != TileDetail.GLYPH,
                modifier = Modifier
                    .fillMaxWidth(if (detail == TileDetail.FULL) 0.46f else 0.56f)
                    .weight(1f, fill = false)
                    .height(if (detail == TileDetail.FULL) 76.dp else 52.dp)
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
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = Spacing.xs)
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

/** A utility tile: settings, the desert, adding a timer. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ActionTile(
    icon: ImageVector,
    label: String,
    detail: TileDetail,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val colors = HourglassTheme.colors
    val colour = tint ?: colors.textSecondary

    TileShell(
        modifier = modifier,
        accent = colour,
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
                tint = colour,
                modifier = Modifier.size(if (detail == TileDetail.FULL) 26.dp else 20.dp)
            )
            if (detail.showsName) {
                Spacer(Modifier.height(Spacing.sm))
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
                if (background != null) {
                    Modifier.background(background)
                } else {
                    Modifier.background(if (active) colors.surface else colors.surfaceMuted)
                }
            )
            .border(
                width = if (active) 1.5.dp else 1.dp,
                color = if (active) accent.copy(alpha = 0.85f) else colors.outline,
                shape = shape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics { contentDescription = description }
            .padding(Spacing.xs),
        content = content
    )
}
