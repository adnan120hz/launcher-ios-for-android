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
 * Ranges (widened in fix11 — the panel is a real "pro mode", not a
 * subtle nudge):
 *  - sharpness: 0..1   unsharp-mask strength (1 = full-strength mask)
 *  - saturation: 0.0..2.0 (1 = untouched, 0 = grayscale)
 *  - contrast: 0.4..1.8   (1 = untouched)
 *  - gamma: 0.5..2.0      (1 = untouched, >1 lifts shadows)
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
        PhotoConfig(sharpness = 0.25f, saturation = 1.10f, contrast = 1.05f, denoise = 0.25f)
    ),
    PhotoConfigPreset(
        "vivid", "Vivid",
        PhotoConfig(sharpness = 0.40f, saturation = 1.60f, contrast = 1.20f, denoise = 0.10f)
    ),
    PhotoConfigPreset(
        "tajam", "Tajam",
        PhotoConfig(sharpness = 1.0f, saturation = 1.08f, contrast = 1.10f, denoise = 0.05f)
    ),
    PhotoConfigPreset(
        "malam", "Malam",
        PhotoConfig(
            sharpness = 0.15f, saturation = 1.10f, contrast = 0.90f,
            gamma = 1.55f, denoise = 0.75f
        )
    )
)

/**
 * Applies a [PhotoConfig] to a captured bitmap, for real:
 *
 *  1. A blurred reference is produced by downscaling ×8 (kept at that
 *     small size — fix11: the old code upscaled it back to a second
 *     FULL-RES bitmap, ~200 MB on a 50 MP capture, an OOM risk on entry
 *     phones; strips now sample the small copy directly).
 *  2. One strip-wise pixel pass then applies, per pixel: gamma (256-entry
 *     LUT) → contrast (LUT) → saturation (luminance mix) → denoise
 *     (blend toward the blurred copy) → unsharp (add back the
 *     high-frequency difference, scaled by sharpness).
 *
 * The per-pixel math lives in [processPixels] — pure, Android-free, and
 * unit-tested (PhotoConfigProcessorTest) so the "is it really applied?"
 * question has a measured answer. The pipeline is identical on every
 * performance tier: CONFIG is never silently disabled on entry devices.
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

        val small = makeBlurredSmall(src)
        val smallW = small.width
        val smallH = small.height
        val smallPx = IntArray(smallW * smallH)
        small.getPixels(smallPx, 0, smallW, 0, 0, smallW, smallH)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val stripRows = 128
        val srcPx = IntArray(w * stripRows)
        val blurPx = IntArray(w * stripRows)
        var y = 0
        while (y < h) {
            val rows = minOf(stripRows, h - y)
            src.getPixels(srcPx, 0, w, 0, y, w, rows)
            // Expand the ×8 blur reference into this strip by sampling —
            // no full-res blurred bitmap is ever materialised.
            for (row in 0 until rows) {
                val sRow = ((y + row) * smallH / h) * smallW
                val base = row * w
                for (x in 0 until w) {
                    blurPx[base + x] = smallPx[sRow + (x * smallW / w)]
                }
            }
            processPixels(srcPx, blurPx, w * rows, cfg)
            out.setPixels(srcPx, 0, w, 0, y, w, rows)
            y += rows
        }
        try {
            small.recycle()
        } catch (e: Throwable) { /* already gone */ }
        return out
    }

    /**
     * Pure per-pixel core (no Android types): mutates [srcPx] in place
     * using [blurPx] as the blur reference, applying gamma LUT → contrast
     * → saturation → denoise blend → unsharp. A neutral config returns
     * without touching a single value (the capture fast path stays
     * byte-identical — proven by unit test).
     */
    internal fun processPixels(srcPx: IntArray, blurPx: IntArray, n: Int, cfg: PhotoConfig) {
        if (cfg.isNeutral) return
        // LUTs: gamma then contrast+offset in one 0..255 pass.
        val lut = IntArray(256) { i ->
            val g = ((i / 255f).pow(1f / cfg.gamma.coerceIn(0.2f, 3f)) * 255f)
            val c = (g - 128f) * cfg.contrast + 128f
            c.roundToInt().coerceIn(0, 255)
        }
        val sat = cfg.saturation
        val den = cfg.denoise.coerceIn(0f, 1f)
        val sharp = cfg.sharpness.coerceIn(0f, 1f)
        val count = minOf(n, srcPx.size, blurPx.size)
        for (i in 0 until count) {
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
                    // fix11: 0.85 max blend (was 0.7) — denoise at full
                    // slider now visibly smooths instead of whispering.
                    r = (r + (br - r) * den * 0.85f).roundToInt()
                    g = (g + (bg - g) * den * 0.85f).roundToInt()
                    bl = (bl + (bb - bl) * den * 0.85f).roundToInt()
                }
                if (sharp > 0f) {
                    // Unsharp: original + k*(original - blur) against the
                    // pre-denoise blurred copy. fix11: gain 1.5 (was 0.9)
                    // so "Tajam" at full strength reads as real sharpening.
                    r += ((r - br) * sharp * 1.5f).roundToInt()
                    g += ((g - bg) * sharp * 1.5f).roundToInt()
                    bl += ((bl - bb) * sharp * 1.5f).roundToInt()
                }
            }
            srcPx[i] = (a shl 24) or
                (r.coerceIn(0, 255) shl 16) or
                (g.coerceIn(0, 255) shl 8) or
                bl.coerceIn(0, 255)
        }
    }

    /** ×8 downscale: a cheap, honest blur reference (used for both the
     *  denoise blend and the unsharp difference). Kept small on purpose —
     *  strips sample it directly instead of upscaling to full res. */
    private fun makeBlurredSmall(src: Bitmap): Bitmap {
        val smallW = maxOf(1, src.width / 8)
        val smallH = maxOf(1, src.height / 8)
        return Bitmap.createScaledBitmap(src, smallW, smallH, true)
    }
}
