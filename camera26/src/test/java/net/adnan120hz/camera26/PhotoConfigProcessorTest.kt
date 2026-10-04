package net.adnan120hz.camera26

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Measured proof that CONFIG ("mode pro") is real processing, not panel
 * theatre (fix11 item 3): the pure pixel core is run over synthetic
 * patches and the effect is asserted numerically —
 *  - neutral config leaves every value byte-identical (fast path truth),
 *  - Vivid measurably raises saturation on a colour patch,
 *  - Tajam measurably raises edge energy on a checkerboard,
 *  - Malam's gamma measurably lifts a shadow pixel.
 */
class PhotoConfigProcessorTest {

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun red(p: Int) = (p ushr 16) and 0xFF

    /** Saturation proxy: channel spread relative to mean luminance. */
    private fun saturationOf(p: Int): Float {
        val r = (p ushr 16) and 0xFF
        val g = (p ushr 8) and 0xFF
        val b = p and 0xFF
        val lum = 0.299f * r + 0.587f * g + 0.114f * b
        if (lum <= 0f) return 0f
        val spread = (maxOf(r, g, b) - minOf(r, g, b)).toFloat()
        return spread / lum
    }

    @Test
    fun neutralIsIdentity() {
        val n = 64
        val src = IntArray(n) { argb((it * 37) % 256, (it * 91) % 256, (it * 53) % 256) }
        val before = src.copyOf()
        val blur = IntArray(n) { argb(10, 20, 30) }
        PhotoConfigProcessor.processPixels(src, blur, n, PhotoConfig())
        assertArrayEquals(before, src)
    }

    @Test
    fun vividRaisesSaturation() {
        val n = 16
        val patch = argb(120, 90, 70)
        val src = IntArray(n) { patch }
        val blur = IntArray(n) { patch }
        val base = saturationOf(patch)
        PhotoConfigProcessor.processPixels(
            src, blur, n,
            PhotoConfigPresets.first { it.id == "vivid" }.config
        )
        val after = saturationOf(src[0])
        assertTrue(
            "Vivid saturation $after must exceed neutral $base by >25%",
            after > base * 1.25f
        )
    }

    @Test
    fun tajamRaisesEdgeEnergy() {
        val w = 8
        val h = 8
        val n = w * h
        fun checkerboard(): IntArray = IntArray(n) { i ->
            val x = i % w
            val y = i / w
            val v = if ((x + y) % 2 == 0) 100 else 160
            argb(v, v, v)
        }
        // Blur reference for a checkerboard is its mean (130).
        val blur = IntArray(n) { argb(130, 130, 130) }

        fun edgeEnergy(px: IntArray): Double {
            var e = 0.0
            for (y in 0 until h) {
                for (x in 0 until w - 1) {
                    e += kotlin.math.abs(red(px[y * w + x + 1]) - red(px[y * w + x])).toDouble()
                }
            }
            return e
        }

        val plain = checkerboard()
        val baseEnergy = edgeEnergy(plain)
        val sharpened = checkerboard()
        PhotoConfigProcessor.processPixels(
            sharpened, blur, n,
            PhotoConfigPresets.first { it.id == "tajam" }.config
        )
        val afterEnergy = edgeEnergy(sharpened)
        assertTrue(
            "Tajam edge energy $afterEnergy must exceed neutral $baseEnergy by >20%",
            afterEnergy > baseEnergy * 1.2
        )
    }

    @Test
    fun malamLiftsShadows() {
        val n = 4
        val shadow = argb(48, 40, 32)
        val src = IntArray(n) { shadow }
        val blur = IntArray(n) { shadow }
        PhotoConfigProcessor.processPixels(
            src, blur, n,
            PhotoConfigPresets.first { it.id == "malam" }.config
        )
        assertTrue(
            "Malam gamma must lift a 48 shadow measurably (got ${red(src[0])})",
            red(src[0]) > 60
        )
    }
}
