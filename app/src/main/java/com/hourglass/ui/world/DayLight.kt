package com.hourglass.ui.world

import androidx.compose.runtime.compositionLocalOf

/**
 * How far through the waking day it is, `0f..1f`, for everything that draws a sky.
 *
 * Provided once at the top of the app from the bedtime clock, so the bar and every
 * world under it show the same hour: a mine at breakfast is under a morning sky, and
 * at bedtime every world is under the same stars. The default is midday, for previews.
 */
val LocalDaySpent = compositionLocalOf { 0.4f }
