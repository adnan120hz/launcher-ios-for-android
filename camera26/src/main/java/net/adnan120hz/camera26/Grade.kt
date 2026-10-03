package net.adnan120hz.camera26

import android.graphics.ColorMatrix

/**
 * A real colour grade applied to captured photos (and, on API 31+, previewed
 * live via a RenderEffect colour filter). FILTER and STYLES share this
 * pipeline exactly like iOS: picking one clears the other. These are honest
 * colour-matrix approximations of the iOS looks — not Apple's LUTs.
 */
data class GradePreset(val id: String, val label: String, val matrix: FloatArray)

private fun buildMatrix(
    saturation: Float,
    contrast: Float,
    warmthDeg: Float,
    lift: Float = 0f
): FloatArray {
    val m = ColorMatrix()
    m.setSaturation(saturation)
    val off = (1f - contrast) * 128f + lift * 255f
    val cm = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, off,
            0f, contrast, 0f, 0f, off,
            0f, 0f, contrast, 0f, off,
            0f, 0f, 0f, 1f, 0f
        )
    )
    m.postConcat(cm)
    if (warmthDeg != 0f) {
        val w = ColorMatrix()
        // Rotating around the blue axis shifts the yellow<->blue balance.
        w.setRotate(2, warmthDeg)
        m.postConcat(w)
    }
    return m.array.copyOf()
}

/** iOS-style Filters (same names as the iOS Camera filter picker). */
val FilterPresets: List<GradePreset> = listOf(
    GradePreset("vivid", "Vivid", buildMatrix(1.35f, 1.05f, 0f)),
    GradePreset("vivid_warm", "Vivid Warm", buildMatrix(1.30f, 1.03f, 12f)),
    GradePreset("vivid_cool", "Vivid Cool", buildMatrix(1.30f, 1.03f, -12f)),
    GradePreset("dramatic", "Dramatic", buildMatrix(1.15f, 1.20f, 0f)),
    GradePreset("dramatic_warm", "Dramatic Warm", buildMatrix(1.10f, 1.18f, 10f)),
    GradePreset("dramatic_cool", "Dramatic Cool", buildMatrix(1.10f, 1.18f, -10f)),
    GradePreset("mono", "Mono", buildMatrix(0f, 1.05f, 0f)),
    GradePreset("silvertone", "Silvertone", buildMatrix(0.25f, 1.08f, -4f)),
    GradePreset("noir", "Noir", buildMatrix(0f, 1.30f, 0f))
)

/** iOS Photographic Styles (same names as the iOS style picker). */
val StylePresets: List<GradePreset> = listOf(
    GradePreset("standard", "Standard", buildMatrix(1.0f, 1.0f, 0f)),
    GradePreset("rich_contrast", "Rich Contrast", buildMatrix(1.05f, 1.22f, 0f)),
    GradePreset("vibrant", "Vibrant", buildMatrix(1.45f, 1.05f, 0f)),
    GradePreset("warm", "Warm", buildMatrix(1.05f, 1.0f, 16f)),
    GradePreset("cool", "Cool", buildMatrix(1.05f, 1.0f, -16f))
)
