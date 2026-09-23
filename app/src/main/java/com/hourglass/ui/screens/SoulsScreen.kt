package com.hourglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hourglass.R
import com.hourglass.core.world.Deed
import com.hourglass.core.world.Souls
import com.hourglass.data.souls.SoulLedger
import com.hourglass.ui.components.HourglassTopBar
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The ten souls, and everything they have done.
 *
 * Every world's crew is cast from these ten, so this is where the work of every mine,
 * river and castle ends up: under the names of the people who did it. One screen, no
 * scrolling — tap a soul for their whole record.
 */
@Composable
fun SoulsScreen(onBack: () -> Unit) {
    val deeds by SoulLedger.deeds.collectAsStateWithLifecycle()
    val bySoul = remember(deeds) { deeds.groupBy { it.soul } }
    var open by remember { mutableStateOf<Int?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
    ) {
        HourglassTopBar(title = stringResource(R.string.souls), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            (0 until Souls.count).chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    pair.forEach { soul ->
                        SoulCard(
                            soul = soul,
                            deeds = bySoul[soul].orEmpty(),
                            onClick = { open = soul },
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }
                }
            }
        }
    }

    open?.let { soul ->
        SoulRecord(soul = soul, deeds = bySoul[soul].orEmpty(), onDismiss = { open = null })
    }
}

@Composable
private fun SoulCard(soul: Int, deeds: List<Deed>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = HourglassTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surfaceMuted)
            .clickable(onClick = onClick)
            .padding(Spacing.md)
    ) {
        Text(
            text = Souls.name(soul),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary
        )
        Text(
            text = Souls.temperament(soul).nature,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Spacing.xs))
        if (deeds.isEmpty()) {
            Text(
                text = stringResource(R.string.soul_idle),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textMuted
            )
        } else {
            // Their biggest deeds, whatever the trade.
            deeds.sortedByDescending { it.count }.take(CARD_LINES).forEach { deed ->
                Text(
                    text = "${deed.count} ${deed.deed}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** Everything one soul has done, trade by trade. */
@Composable
private fun SoulRecord(soul: Int, deeds: List<Deed>, onDismiss: () -> Unit) {
    val colors = HourglassTheme.colors
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .background(colors.surface)
                .padding(Spacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = Souls.name(soul),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary
            )
            Text(
                text = Souls.temperament(soul).nature,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMuted
            )
            if (deeds.isEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = stringResource(R.string.soul_idle_long, Souls.name(soul)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
            deeds.groupBy { it.role }.toSortedMap().forEach { (role, list) ->
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "${Souls.name(soul)} the $role",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                list.sortedByDescending { it.count }.forEach { deed ->
                    Text(
                        text = "${deed.count} ${deed.deed}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }
    }
}

private const val CARD_LINES = 3
