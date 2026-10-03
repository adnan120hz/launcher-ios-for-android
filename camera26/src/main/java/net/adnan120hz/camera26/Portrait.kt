package net.adnan120hz.camera26

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Portrait fallback when the OEM provides no CameraX BOKEH extension:
 * ML Kit selfie segmentation separates the subject, the background gets a
 * real blur whose strength comes from the APERTURE (ƒ) slider. Processing is
 * capped at 1600 px on the long edge to stay fast on mid-range devices.
 */
object PortraitFallback {
    val available: Boolean by lazy {
        try {
            Segmentation.getClient(
                SelfieSegmenterOptions.Builder()
                    .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                    .build()
            )
            true
        } catch (_: Throwable) {
            false
        }
    }
}

object PortraitProcessor {

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * done receives the composited portrait, or null when segmentation failed
     * or no subject was detected (caller then saves the original photo).
     */
    fun process(src: Bitmap, strength01: Float, done: (Bitmap?) -> Unit) {
        executor.execute {
            try {
                val maxDim = 1600
                val scale = minOf(1f, maxDim.toFloat() / max(src.width, src.height))
                val work = if (scale < 1f) {
                    Bitmap.createScaledBitmap(
                        src,
                        (src.width * scale).toInt().coerceAtLeast(1),
                        (src.height * scale).toInt().coerceAtLeast(1),
                        true
                    )
                } else src
                val segmenter = Segmentation.getClient(
                    SelfieSegmenterOptions.Builder()
                        .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
                        .build()
                )
                segmenter.process(InputImage.fromBitmap(work, 0))
                    .addOnSuccessListener { mask ->
                        executor.execute {
                            val out = try {
                                composite(work, mask, strength01)
                            } catch (_: Throwable) {
                                null
                            }
                            done(out)
                        }
                    }
                    .addOnFailureListener { done(null) }
            } catch (_: Throwable) {
                done(null)
            }
        }
    }

    private fun composite(src: Bitmap, mask: SegmentationMask, strength01: Float): Bitmap? {
        val w = src.width
        val h = src.height
        val mw = mask.width
        val mh = mask.height
        if (mw <= 0 || mh <= 0) return null
        val total = mw * mh
        val buf = mask.buffer
        buf.rewind()
        // Selfie segmentation masks are float-per-pixel; some builds ship
        // byte-per-pixel. Detect from the buffer size instead of assuming.
        val floatMask = buf.remaining() >= total * 4
        val conf = FloatArray(total)
        var fgCount = 0
        for (i in 0 until total) {
            val v = if (floatMask) {
                try { buf.getFloat() } catch (_: Throwable) { 0f }
            } else {
                try { (buf.get().toInt() and 0xFF) / 255f } catch (_: Throwable) { 0f }
            }
            val c = v.coerceIn(0f, 1f)
            conf[i] = c
            if (c > 0.5f) fgCount++
        }
        // No person in frame -> nothing meaningful to blur behind.
        if (fgCount < total * 0.004) return null

        val bg = blurred(src, strength01)
        val fgPx = IntArray(w * h)
        src.getPixels(fgPx, 0, w, 0, 0, w, h)
        val bgPx = IntArray(w * h)
        bg.getPixels(bgPx, 0, w, 0, 0, w, h)
        val out = IntArray(w * h)
        for (y in 0 until h) {
            val my = (y * mh / h).coerceIn(0, mh - 1)
            for (x in 0 until w) {
                val mx = (x * mw / w).coerceIn(0, mw - 1)
                val a = conf[my * mw + mx]
                val idx = y * w + x
                val f = fgPx[idx]
                val b = bgPx[idx]
                val r = (((f shr 16) and 0xFF) * a + ((b shr 16) and 0xFF) * (1f - a)).toInt()
                val g = (((f shr 8) and 0xFF) * a + ((b shr 8) and 0xFF) * (1f - a)).toInt()
                val bl = ((f and 0xFF) * a + (b and 0xFF) * (1f - a)).toInt()
                out[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or bl
            }
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(out, 0, w, 0, 0, w, h)
        return bmp
    }

    /** Cheap gaussian approximation: repeated downscale -> upscale passes. */
    private fun blurred(src: Bitmap, strength01: Float): Bitmap {
        var cur = src
        val divisor = 5f + strength01 * 11f // 5 (mild) .. 16 (strong)
        repeat(2) {
            val sw = max(1, (cur.width / divisor).toInt())
            val sh = max(1, (cur.height / divisor).toInt())
            val small = Bitmap.createScaledBitmap(cur, sw, sh, true)
            cur = Bitmap.createScaledBitmap(small, src.width, src.height, true)
        }
        return cur
    }
}
