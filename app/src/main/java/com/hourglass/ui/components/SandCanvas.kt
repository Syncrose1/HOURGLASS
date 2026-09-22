package com.hourglass.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.hourglass.core.Direction
import com.hourglass.core.SandGrid
import kotlin.math.abs
import kotlin.random.Random

/**
 * Renders a [SandGrid] and drives its clock.
 *
 * The grid is painted into a bitmap the size of the grid itself and then scaled up with
 * filtering off, so a grain is a crisp square rather than a blurred dot. One draw call
 * per frame regardless of how much sand is on screen — thousands of individual rects
 * would not hold a frame rate.
 */
@Composable
fun SandCanvas(
    grid: SandGrid,
    tints: List<Color>,
    wallColor: Color,
    modifier: Modifier = Modifier,
    running: Boolean = true,
    gravity: Direction = Direction.S,
    interactive: Boolean = true,
    /** Called before each step, for pouring or metering a drain. */
    onBeforeStep: (SandGrid) -> Unit = {}
) {
    val currentGravity by rememberUpdatedState(gravity)
    val currentBeforeStep by rememberUpdatedState(onBeforeStep)

    // Bumped per simulated step; read inside the draw lambda so the canvas repaints.
    var frame by remember { mutableIntStateOf(0) }

    val bitmap = remember(grid.width, grid.height) {
        Bitmap.createBitmap(grid.width, grid.height, Bitmap.Config.ARGB_8888)
    }
    val image: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val pixels = remember(grid.width, grid.height) { IntArray(grid.width * grid.height) }
    val random = remember { Random(System.nanoTime()) }

    val argb = remember(tints, wallColor) {
        // Index 0 is empty, negative values are walls; everything else is a tint.
        IntArray(tints.size + 1).also { table ->
            table[0] = Color.Transparent.toArgb()
            tints.forEachIndexed { index, colour -> table[index + 1] = colour.toArgb() }
        }
    }
    val wallArgb = remember(wallColor) { wallColor.toArgb() }

    LaunchedEffect(grid, running) {
        if (!running) return@LaunchedEffect
        var last = 0L
        while (true) {
            withFrameMillis { now ->
                // Sand looks like sand at about 30 steps a second; at display rate the
                // grains stream rather than tumble.
                if (now - last >= STEP_INTERVAL_MILLIS) {
                    last = now
                    currentBeforeStep(grid)
                    grid.step(currentGravity, random)
                    frame++
                }
            }
        }
    }

    Canvas(
        modifier = modifier.then(
            if (!interactive) Modifier else Modifier.pointerInput(grid) {
                detectDragGestures { change, _ ->
                    val cell = toCell(change.position, size.width, size.height, grid)
                    if (cell != null) {
                        grid.disturb(cell.first, cell.second, DISTURB_RADIUS, random)
                        frame++
                    }
                }
            }
        )
    ) {
        @Suppress("UNUSED_EXPRESSION")
        frame // read so a step invalidates the drawing

        val buffer = grid.buffer()
        for (index in buffer.indices) {
            val value = buffer[index]
            pixels[index] = when {
                value == SandGrid.WALL -> wallArgb
                value <= 0 -> argb[0]
                else -> argb.getOrElse(value) { argb[0] }
            }
        }
        bitmap.setPixels(pixels, 0, grid.width, 0, 0, grid.width, grid.height)

        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            filterQuality = FilterQuality.None
        )
    }
}

private fun toCell(position: Offset, width: Int, height: Int, grid: SandGrid): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val x = (position.x / width * grid.width).toInt()
    val y = (position.y / height * grid.height).toInt()
    return if (grid.inBounds(x, y)) x to y else null
}

/**
 * The direction sand should fall, taken from the accelerometer.
 *
 * Heavily smoothed and snapped to a compass point: the grid can only move a grain to
 * one of eight neighbours, and an unfiltered reading would have the pile twitching
 * between them on every hand tremor.
 */
@Composable
fun rememberTiltDirection(enabled: Boolean = true): State<Direction> {
    val context = LocalContext.current
    val direction = remember { mutableStateOf(Direction.S) }

    DisposableEffect(context, enabled) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (accelerometer == null) return@DisposableEffect onDispose { }

        var smoothedX = 0f
        var smoothedY = SensorManager.GRAVITY_EARTH

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Device axes: +x is right, +y is up. The grid's y grows downward, so
                // the screen-space gravity vector flips x and keeps y.
                smoothedX += SMOOTHING * (-event.values[0] - smoothedX)
                smoothedY += SMOOTHING * (event.values[1] - smoothedY)

                if (abs(smoothedX) < TILT_DEADZONE && abs(smoothedY) < TILT_DEADZONE) return
                val next = Direction.nearest(smoothedX, smoothedY)
                if (next != direction.value) direction.value = next
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensors.unregisterListener(listener) }
    }

    return direction
}

private const val STEP_INTERVAL_MILLIS = 33L
private const val DISTURB_RADIUS = 3
private const val SMOOTHING = 0.08f
private const val TILT_DEADZONE = 1.5f
