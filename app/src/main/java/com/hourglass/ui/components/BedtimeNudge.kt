package com.hourglass.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.ui.theme.GoldSoft
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The only thing the app ever says unprompted.
 *
 * "Bank your time, respect your rest" has to cut both ways or it is just a slogan on
 * a productivity timer — so when a timer is still running after bedtime, the app says
 * so once, quietly, with the stop button right there. It never interrupts and it never
 * stops anything by itself; the choice stays with the user.
 */
@Composable
fun BedtimeNudge(
    visible: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) + expandVertically(tween(420)),
        exit = fadeOut(tween(220)) + shrinkVertically(tween(260)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colors.duskTop.copy(alpha = if (colors.isDark) 0.55f else 0.10f))
                .border(
                    width = 1.dp,
                    color = colors.duskTop.copy(alpha = 0.35f),
                    shape = MaterialTheme.shapes.medium
                )
                .padding(Spacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = if (colors.isDark) GoldSoft else colors.duskTop,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.past_bedtime_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.past_bedtime_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(Spacing.xs))
                TextButton(
                    onClick = onStop,
                    contentPadding = PaddingValues(
                        horizontal = 0.dp,
                        vertical = Spacing.xs
                    )
                ) {
                    Text(
                        text = stringResource(R.string.stop),
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.accent
                    )
                }
            }
        }
    }
}
