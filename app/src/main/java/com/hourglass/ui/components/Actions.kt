package com.hourglass.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * The filled action on a card, tinted with that timer's own sand colour.
 * Label colour is picked from the fill's luminance so a pale sand never ends up
 * with white text on it.
 */
@Composable
fun PrimaryAction(
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val onAccent = if (accent.luminance() > 0.55f) HourglassTheme.colors.textPrimary
    else Color.White

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 38.dp else 46.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(
            containerColor = accent,
            contentColor = onAccent
        ),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(if (compact) 15.dp else 18.dp)
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelMedium
            else MaterialTheme.typography.labelLarge
        )
    }
}

/** The quieter companion action — Stop, and anything else that undoes rather than does. */
@Composable
fun SecondaryAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = HourglassTheme.colors
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = if (compact) 38.dp else 46.dp),
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.textSecondary),
        border = BorderStroke(1.dp, colors.outlineStrong)
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelMedium
                else MaterialTheme.typography.labelLarge
            )
        }
    }
}
