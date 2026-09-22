package com.hourglass.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing

/**
 * A bounded numeric stepper.
 *
 * Durations and bedtimes are entered with these rather than free text: the old screens
 * accepted any keystrokes at all and then quietly fell back to a default when the
 * result would not parse.
 */
@Composable
fun Stepper(
    value: Int,
    label: String,
    range: IntRange,
    accent: Color,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    step: Int = 1,
    formatValue: (Int) -> String = { it.toString() }
) {
    val colors = HourglassTheme.colors
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(colors.surface)
            .border(1.dp, colors.outline, MaterialTheme.shapes.small)
            .padding(vertical = Spacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textMuted
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(
                symbol = MINUS,
                description = stringResource(R.string.decrease, label),
                enabled = value - step >= range.first,
                accent = accent,
                onClick = { onChange((value - step).coerceIn(range)) }
            )
            Text(
                text = formatValue(value),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(56.dp)
            )
            StepButton(
                symbol = PLUS,
                description = stringResource(R.string.increase, label),
                enabled = value + step <= range.last,
                accent = accent,
                onClick = { onChange((value + step).coerceIn(range)) }
            )
        }
    }
}

@Composable
private fun StepButton(
    symbol: String,
    description: String,
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val colors = HourglassTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(38.dp)
            .semantics { contentDescription = description },
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = accent.copy(alpha = 0.14f),
            contentColor = accent,
            disabledContainerColor = colors.outline.copy(alpha = 0.4f),
            disabledContentColor = colors.textMuted
        )
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium)
    }
}

private const val MINUS = "−"
private const val PLUS = "+"
