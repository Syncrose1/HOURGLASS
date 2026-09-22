package com.hourglass.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hourglass.R
import com.hourglass.core.Bedtime
import com.hourglass.core.TimeOfDay
import com.hourglass.ui.theme.Gold
import com.hourglass.ui.theme.GoldSoft
import com.hourglass.ui.theme.HourglassTheme
import com.hourglass.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * The hero of the home screen.
 *
 * It is the one surface in the app that is not sand-coloured: a dusk gradient that
 * deepens as bedtime approaches, so the card visibly changes mood over an evening
 * rather than just counting down in text.
 */
@Composable
fun BedtimeCard(
    bedtime: TimeOfDay,
    sleepMinutes: Int,
    modifier: Modifier = Modifier
) {
    val colors = HourglassTheme.colors

    // Re-read the clock on the minute boundary rather than on a free-running 30s
    // timer, so the displayed figure changes exactly when the wall clock does.
    var minutesUntil by remember { mutableIntStateOf(minutesUntilBedtimeNow(bedtime)) }
    LaunchedEffect(bedtime) {
        while (true) {
            minutesUntil = minutesUntilBedtimeNow(bedtime)
            delay(millisUntilNextMinute())
        }
    }

    val windingDown = Bedtime.isWindingDown(minutesUntil)
    val progress by animateFloatAsState(
        targetValue = Bedtime.windDownProgress(minutesUntil),
        animationSpec = tween(durationMillis = 700),
        label = "wind_down"
    )
    val warmth by animateFloatAsState(
        targetValue = if (windingDown) 1f else 0f,
        animationSpec = tween(durationMillis = 900),
        label = "dusk_warmth"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        colors.duskTop,
                        lerpColour(colors.duskTop, colors.duskBottom, 0.45f + 0.3f * warmth),
                        colors.duskBottom
                    ),
                    start = Offset.Zero,
                    end = Offset(GRADIENT_SPAN, GRADIENT_SPAN)
                )
            )
    ) {
        StarField(intensity = warmth)

        Column(modifier = Modifier.padding(Spacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.bedtime).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = GoldSoft
                )
                Spacer(Modifier.width(Spacing.sm))
                if (windingDown) {
                    Text(
                        text = stringResource(R.string.soon),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.duskTop,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(GoldSoft)
                            .padding(horizontal = Spacing.sm, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = bedtime.formatFriendly(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.92f)
                )
            }

            Spacer(Modifier.height(Spacing.xl))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = Bedtime.describe(minutesUntil),
                        style = MaterialTheme.typography.displaySmall,
                        color = Color.White
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = if (minutesUntil <= 0) {
                            stringResource(R.string.get_some_rest)
                        } else {
                            stringResource(R.string.until_bedtime)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }

                WindDownRing(progress = progress, lit = windingDown)
            }

            Spacer(Modifier.height(Spacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.lg)
            ) {
                DuskStat(
                    label = stringResource(R.string.projected_sleep),
                    value = Bedtime.describeSleep(sleepMinutes),
                    modifier = Modifier.weight(1f)
                )
                DuskStat(
                    label = stringResource(R.string.wake_time),
                    value = TimeOfDay
                        .ofMinuteOfDay(bedtime.minuteOfDay + sleepMinutes)
                        .formatFriendly(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/** A ring that fills through the last two hours before bed. */
@Composable
private fun WindDownRing(progress: Float, lit: Boolean) {
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(64.dp)) {
            val stroke = size.minDimension * 0.07f
            val inset = stroke / 2f
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke)
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(GoldSoft, Gold, GoldSoft)),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = Offset(inset, inset),
                size = Size(size.width - stroke, size.height - stroke)
            )
        }
        HourglassMark(
            size = 26.dp,
            tint = if (lit) GoldSoft else Color.White.copy(alpha = 0.55f),
            animated = lit
        )
    }
}

@Composable
private fun DuskStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.55f)
        )
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Start
        )
    }
}

/** A handful of slow stars that fade in as the evening draws on. */
@Composable
private fun StarField(intensity: Float) {
    if (intensity <= 0.01f) return
    val transition = rememberInfiniteTransition(label = "stars")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle"
    )
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        repeat(STAR_COUNT) { index ->
            val seed = index * 1.7f
            val x = size.width * (0.08f + 0.86f * fract(sin(seed * 12.9898f) * 43758.5453f))
            val y = size.height * (0.12f + 0.7f * fract(cos(seed * 78.233f) * 12345.6789f))
            val phase = fract(seed * 0.37f)
            val alpha = intensity * (0.25f + 0.45f * fract(twinkle + phase))
            drawCircle(
                color = Color.White,
                radius = size.minDimension * 0.006f * (0.7f + phase),
                center = Offset(x, y),
                alpha = alpha.coerceIn(0f, 0.8f)
            )
        }
    }
}

private fun fract(value: Float): Float = value - kotlin.math.floor(value)

private fun lerpColour(from: Color, to: Color, fraction: Float): Color = Color(
    red = from.red + (to.red - from.red) * fraction,
    green = from.green + (to.green - from.green) * fraction,
    blue = from.blue + (to.blue - from.blue) * fraction,
    alpha = from.alpha + (to.alpha - from.alpha) * fraction
)

private fun minutesUntilBedtimeNow(bedtime: TimeOfDay): Int {
    val now = Calendar.getInstance()
    val nowMinuteOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    return Bedtime.minutesUntil(nowMinuteOfDay, bedtime)
}

private fun millisUntilNextMinute(): Long {
    val now = Calendar.getInstance()
    val seconds = now.get(Calendar.SECOND)
    val millis = now.get(Calendar.MILLISECOND)
    return (60_000L - seconds * 1_000L - millis).coerceIn(1_000L, 60_000L)
}

private const val STAR_COUNT = 14
private const val GRADIENT_SPAN = 1200f
