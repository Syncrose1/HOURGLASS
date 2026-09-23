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

    private fun outputDir(): File {
        val out = System.getenv("WORLDSHOTS_DIR")
        assumeTrue("set WORLDSHOTS_DIR to render snapshots", !out.isNullOrBlank())
        return File(out!!).apply { mkdirs() }
    }

    @Test
    fun mine() {
        val dir = outputDir()
        listOf(3L, 11L, 21L).forEach { seed ->
            render(MineWorld(72, 96, seed, quota = 60), "mine-$seed", dir)
        }
    }

    @Test
    fun river() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed ->
            render(RiverWorld(72, 96, seed, richness = 150), "river-$seed", dir)
        }
    }

    @Test
    fun ants() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed ->
            render(AntWorld(72, 96, seed, AntWorld.quotaFor(5f)), "ants-$seed", dir)
        }
    }

    @Test
    fun island() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed ->
            render(IslandWorld(72, 96, seed, IslandWorld.quotaFor(5f)), "island-$seed", dir)
        }
    }

    @Test
    fun forest() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed ->
            render(ForestWorld(72, 96, seed, ForestWorld.quotaFor(5f)), "forest-$seed", dir)
        }
    }

    @Test
    fun battle() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed -> render(BattleWorld(72, 96, seed, BattleWorld.pointsFor(5f)), "battle-$seed", dir) }
    }

    @Test
    fun harbour() {
        val dir = outputDir()
        listOf(3L, 11L).forEach { seed -> render(HarbourWorld(72, 96, seed, HarbourWorld.quotaFor(5f)), "harbour-$seed", dir) }
    }

    @Test
    fun sky() {
        val dir = outputDir()
        val sky = DaySky(96, 14)
        val buffer = IntArray(96 * 14)
        listOf(0.1f, 0.5f, 0.75f, 0.92f, 1f).forEachIndexed { i, spent ->
            sky.render(buffer, spent, 0)
            writeArgb(buffer, 96, 14, File(dir, "sky-$i.png"))
        }
    }

    private fun writeArgb(argb: IntArray, width: Int, height: Int, file: File) {
        // One slot per pixel, so the ordinary writer can be reused.
        val slots = IntArray(argb.size) { it }
        val table = IntArray(height * argb.size)
        for (y in 0 until height) for (i in argb.indices) table[y * argb.size + i] = argb[i]
        write(slots, width, height, file, table, -1, argb.size)
    }

    /** Runs a five-minute timer and writes the world at checkpoints through it. */
    private fun render(world: World, name: String, dir: File) {
        val palette = Palettes.forKind(world.kind, AMBER)
        val shaded = Palettes.shadedByDepth(palette, world.height)
        val pacer = WorldPacer()
        val random = Random(name.hashCode().toLong())
        val ticks = 9000
        val checkpoints = mapOf(0 to "00", 2250 to "25", 4500 to "50", 9000 to "100", 12000 to "overtime")
        val buffer = IntArray(world.width * world.height)
        for (tick in 0..12000) {
            checkpoints[tick]?.let { label ->
                world.renderInto(buffer)
                write(buffer, world.width, world.height, File(dir, "$name-$label.png"), shaded, palette.sky, palette.colours.size)
            }
            world.step(pacer.effortFor((tick + 1f) / ticks, world.objectiveProgress), random)
        }
    }

    private fun write(
        cells: IntArray,
        width: Int,
        height: Int,
        file: File,
        shaded: IntArray,
        skySlot: Int,
        slots: Int
    ) {
        val scale = 6
        val w = width * scale
        val h = height * scale
        val pixels = IntArray(w * h)
        for (y in 0 until height) {
            val sky = lerpArgb(0xFF3B3E6E.toInt(), 0xFFE7C58F.toInt(), y / (height * 0.25f))
            for (x in 0 until width) {
                val slot = cells[y * width + x]
                val argb = if (slot == skySlot) sky else shaded[y * slots + slot]
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

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun ch(shift: Int) = (((a shr shift) and 0xFF) * (1 - f) + ((b shr shift) and 0xFF) * f).toInt()
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private companion object {
        const val AMBER = 0xFFD9A949.toInt()
    }
}
