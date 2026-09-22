package com.hourglass.core.world

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.CRC32
import java.util.zip.DeflaterOutputStream
import kotlin.random.Random

/**
 * Renders worlds to PNG at points through a timer, for looking at without a device.
 *
 * Opt-in: set WORLDSHOTS_DIR and run
 * `./gradlew testDebugUnitTest --tests '*WorldSnapshots*'`. Skipped otherwise, so CI
 * never pays for it.
 */
class WorldSnapshots {

    @Test
    fun mine() {
        val out = System.getenv("WORLDSHOTS_DIR")
        assumeTrue("set WORLDSHOTS_DIR to render snapshots", !out.isNullOrBlank())
        val dir = File(out!!).apply { mkdirs() }

        listOf(3L, 11L, 21L).forEach { seed ->
            val world = MineWorld(72, 96, seed, richness = 60)
            val pacer = WorldPacer()
            val random = Random(seed)
            val ticks = 9000 // a five-minute timer at thirty steps a second
            val checkpoints = mapOf(0 to "00", 2250 to "25", 4500 to "50", 9000 to "100")
            val buffer = IntArray(world.width * world.height)

            for (tick in 0..ticks + 3000) {
                checkpoints[tick]?.let { label ->
                    world.renderInto(buffer)
                    write(buffer, world.width, world.height, File(dir, "mine-$seed-$label.png"))
                }
                if (tick == ticks + 3000) {
                    world.renderInto(buffer)
                    write(buffer, world.width, world.height, File(dir, "mine-$seed-overtime.png"))
                }
                val progress = (tick + 1f) / ticks
                world.step(pacer.effortFor(progress, world.objectiveProgress), random)
            }
        }
    }

    private fun write(cells: IntArray, width: Int, height: Int, file: File) {
        val scale = 6
        val w = width * scale
        val h = height * scale
        val pixels = IntArray(w * h)
        for (y in 0 until height) {
            val sky = lerpArgb(0xFF3B3E6E.toInt(), 0xFFE7C58F.toInt(), y / (height * 0.25f))
            for (x in 0 until width) {
                val slot = cells[y * width + x]
                val argb = if (slot == Mat.SKY) sky else shade(PALETTE[slot], y, height, slot)
                for (dy in 0 until scale) for (dx in 0 until scale) {
                    pixels[(y * scale + dy) * w + x * scale + dx] = argb
                }
            }
        }
        file.writeBytes(png(pixels, w, h))
    }

    /**
     * The smallest honest PNG: an RGB image, one filter byte per row, zlib'd.
     * `android.jar` has no image codecs, but it has zlib and CRC32, which is all a
     * PNG is.
     */
    private fun png(pixels: IntArray, w: Int, h: Int): ByteArray {
        val raw = ByteArrayOutputStream()
        DeflaterOutputStream(raw).use { z ->
            for (y in 0 until h) {
                z.write(0)
                for (x in 0 until w) {
                    val p = pixels[y * w + x]
                    z.write((p shr 16) and 0xFF)
                    z.write((p shr 8) and 0xFF)
                    z.write(p and 0xFF)
                }
            }
        }
        val out = ByteArrayOutputStream()
        val data = DataOutputStream(out)
        data.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, body: ByteArray) {
            data.writeInt(body.size)
            val typed = type.toByteArray() + body
            data.write(typed)
            data.writeInt(CRC32().apply { update(typed) }.value.toInt())
        }
        val header = ByteArrayOutputStream()
        DataOutputStream(header).apply {
            writeInt(w)
            writeInt(h)
            writeByte(8) // bit depth
            writeByte(2) // truecolour
            writeByte(0)
            writeByte(0)
            writeByte(0)
        }
        chunk("IHDR", header.toByteArray())
        chunk("IDAT", raw.toByteArray())
        chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun shade(argb: Int, y: Int, height: Int, slot: Int): Int {
        if (slot == Mat.MINER || slot == Mat.MINER_LOADED || Mat.isMineral(slot)) return argb
        return lerpArgb(argb, 0xFF000000.toInt(), DEPTH_DARKENING * y / height)
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - f) + ((b shr shift) and 0xFF) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        const val DEPTH_DARKENING = 0.4f

        /** Mirrors the app's mine palette, with amber seams. */
        val PALETTE = IntArray(Mat.COUNT).also {
            it[Mat.AIR] = 0xFF1B130D.toInt()
            it[Mat.SAND] = 0xFFD8B77C.toInt()
            it[Mat.SAND_DARK] = 0xFFC7A366.toInt()
            it[Mat.SANDSTONE] = 0xFFAE7849.toInt()
            it[Mat.SANDSTONE_DARK] = 0xFF98683E.toInt()
            it[Mat.ROCK] = 0xFF5C4F47.toInt()
            it[Mat.ROCK_DARK] = 0xFF4B403A.toInt()
            it[Mat.MINERAL] = 0xFFD9A949.toInt()
            it[Mat.MINERAL_BRIGHT] = 0xFFF0D596.toInt()
            it[Mat.CART] = 0xFF6E4B2E.toInt()
            it[Mat.MINER] = 0xFFF6EBD6.toInt()
            it[Mat.MINER_LOADED] = 0xFFE9C37A.toInt()
            it[Mat.STOCK] = 0xFFE0B458.toInt()
            it[Mat.SAND_PACKED] = 0xFFB89A68.toInt()
            it[Mat.PLATFORM] = 0xFF7A5634.toInt()
        }
    }
}
