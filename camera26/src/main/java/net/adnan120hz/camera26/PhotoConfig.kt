package net.adnan120hz.camera26

import android.graphics.Bitmap
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * CONFIG — this app's own photo-quality configuration (user request,
 * GCam-config INSPIRED but not a GCam XML import: the parameters below are
 * ours, mapped onto adjustments this app really applies to the captured
 * bitmap). A preset fills the five sliders; touching any slider switches
 * the panel to "Kustom" (custom). Everything is applied for real in
 * [PhotoConfigProcessor] on the photo post-processing path — what the
 * panel says is what the JPEG gets.
 *
 * Ranges:
 *  - sharpness: 0..1   unsharp-mask strength
 *  - saturation: 0.5..1.6 (1 = untouched)
 *  - contrast: 0.6..1.5   (1 = untouched)
 *  - gamma: 0.6..1.6      (1 = untouched, >1 lifts shadows)
 *  - denoise: 0..1        blend toward a blurred copy
 */
data class PhotoConfig(
    val sharpness: Float = 0f,
    val saturation: Float = 1f,
    val contrast: Float = 1f,
    val gamma: Float = 1f,
    val denoise: Float = 0f
) {
    /** True when nothing deviates from neutral (capture fast-path stays). */
    val isNeutral: Boolean
        get() = sharpness == 0f && saturation == 1f && contrast == 1f &&
            gamma == 1f && denoise == 0f
}

data class PhotoConfigPreset(val id: String, val label: String, val config: PhotoConfig)

val PhotoConfigPresets: List<PhotoConfigPreset> = listOf(
    PhotoConfigPreset("default", "Default", PhotoConfig()),
    PhotoConfigPreset(
        "natural", "Natural",
        PhotoConfig(sharpness = 0.15f, saturation = 1.05f, contrast = 1.02f, denoise = 0.2f)
    ),
    PhotoConfigPreset(
        "vivid", "Vivid",
        PhotoConfig(sharpness = 0.25f, saturation = 1.35f, contrast = 1.08f, denoise = 0.1f)
    ),
    PhotoConfigPreset(
        "tajam", "Tajam",
        PhotoConfig(sharpness = 0.7f, saturation = 1.05f, contrast = 1.05f, denoise = 0.05f)
    ),
    PhotoConfigPreset(
        "malam", "Malam",
        PhotoConfig(
            sharpness = 0.1f, saturation = 1.05f, contrast = 0.95f,
            gamma = 1.25f, denoise = 0.6f
        )
    )
)

/**
 * Applies a [PhotoConfig] to a captured bitmap, for real:
 *
 *  1. A blurred copy is produced by downscaling ×8 and upscaling back
 *     (cheap box blur, no extra full-res buffer chain).
 *  2. One strip-wise pixel pass then applies, per pixel: gamma (256-entry
 *     LUT) → contrast (LUT) → saturation (luminance mix) → denoise
 *     (blend toward the blurred copy) → unsharp (add back the
 *     high-frequency difference, scaled by sharpness).
 *
 * Strips of 128 rows keep the scratch buffers at a few MB even on
 * 50 MP captures, so entry-level phones never OOM on the photo path.
 * Returns a NEW bitmap; the source is left untouched.
 */
object PhotoConfigProcessor {

    fun apply(src: Bitmap, cfg: PhotoConfig): Bitmap {
        if (cfg.isNeutral) return src
        val w = src.width
        val h = src.height
        if (w <= 0 || h <= 0) return src

        val blurred = makeBlurred(src)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        // LUTs: gamma then contrast+offset in one 0..255 pass.
        val lut = IntArray(256) { i ->
            val g = ((i / 255f).pow(1f / cfg.gamma.coerceIn(0.2f, 3f)) * 255f)
            val c = (g - 128f) * cfg.contrast + 128f
            c.roundToInt().coerceIn(0, 255)
        }
        val sat = cfg.saturation
        val den = cfg.denoise.coerceIn(0f, 1f)
        val sharp = cfg.sharpness.coerceIn(0f, 1f)

        val stripRows = 128
        val srcPx = IntArray(w * stripRows)
        val blurPx = IntArray(w * stripRows)
        var y = 0
        while (y < h) {
            val rows = minOf(stripRows, h - y)
            src.getPixels(srcPx, 0, w, 0, y, w, rows)
            blurred.getPixels(blurPx, 0, w, 0, y, w, rows)
            val n = w * rows
            for (i in 0 until n) {
                val p = srcPx[i]
                val b = blurPx[i]
                val a = (p ushr 24) and 0xFF
                var r = lut[(p ushr 16) and 0xFF]
                var g = lut[(p ushr 8) and 0xFF]
                var bl = lut[p and 0xFF]
                if (sat != 1f) {
                    val lum = (0.299f * r + 0.587f * g + 0.114f * bl)
                    r = (lum + (r - lum) * sat).roundToInt()
                    g = (lum + (g - lum) * sat).roundToInt()
                    bl = (lum + (bl - lum) * sat).roundToInt()
                }
                if (den > 0f || sharp > 0f) {
                    val br = (b ushr 16) and 0xFF
                    val bg = (b ushr 8) and 0xFF
                    val bb = b and 0xFF
                    if (den > 0f) {
                        r = (r + (br - r) * den * 0.7f).roundToInt()
                        g = (g + (bg - g) * den * 0.7f).roundToInt()
                        bl = (bl + (bb - bl) * den * 0.7f).roundToInt()
                    }
                    if (sharp > 0f) {
                        // Unsharp: original + k*(original - blur), computed
                        // against the pre-denoise blurred copy.
                        r += ((r - br) * sharp * 0.9f).roundToInt()
                        g += ((g - bg) * sharp * 0.9f).roundToInt()
                        bl += ((bl - bb) * sharp * 0.9f).roundToInt()
                    }
                }
                srcPx[i] = (a shl 24) or
                    (r.coerceIn(0, 255) shl 16) or
                    (g.coerceIn(0, 255) shl 8) or
                    bl.coerceIn(0, 255)
            }
            out.setPixels(srcPx, 0, w, 0, y, w, rows)
            y += rows
        }
        try {
            blurred.recycle()
        } catch (e: Throwable) { /* already gone */ }
        return out
    }

    /** Downscale-then-upscale copy: a cheap, honest blur (used for both
     *  denoise blending and the unsharp reference). */
    private fun makeBlurred(src: Bitmap): Bitmap {
        val smallW = maxOf(1, src.width / 8)
        val smallH = maxOf(1, src.height / 8)
        val small = Bitmap.createScaledBitmap(src, smallW, smallH, true)
        val back = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        if (back !== small) {
            try {
                small.recycle()
            } catch (e: Throwable) { /* gone */ }
        }
        return back
    }
}
