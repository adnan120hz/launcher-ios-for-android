package net.adnan120hz.camera26

import android.graphics.Color
import android.graphics.ColorMatrix
import androidx.compose.ui.graphics.Color as ComposeColor

/**
 * A real colour grade applied to captured photos (and, on API 31+, previewed
 * live via a RenderEffect colour filter). FILTER and STYLES share this
 * pipeline exactly like iOS: picking one clears the other. The user can
 * adjust the grade's intensity and warmth (tone) on top of any preset —
 * the adjustments are computed into the same matrix, so what you see is
 * literally what gets baked into the photo. These are honest colour-matrix
 * approximations of the iOS looks — not Apple's LUTs.
 */
data class GradePreset(
    val id: String,
    val label: String,
    val saturation: Float,
    val contrast: Float,
    val warmthDeg: Float,
    /** Black-point lift (0..~0.08): gentle brightness raise, used by Portrait lighting. */
    val lift: Float = 0f
) {
    /** Matrix for this preset scaled by [intensity] (0..1) and shifted by [warmthAdjust] degrees. */
    fun matrix(intensity: Float = 1f, warmthAdjust: Float = 0f): FloatArray {
        val s = 1f + (saturation - 1f) * intensity.coerceIn(0f, 1f)
        val c = 1f + (contrast - 1f) * intensity.coerceIn(0f, 1f)
        val w = warmthDeg * intensity.coerceIn(0f, 1f) + warmthAdjust
        return buildMatrix(s, c, w, lift * intensity.coerceIn(0f, 1f))
    }
}

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

/** Apply a 4x5 colour [matrix] to an ARGB colour (used to render honest swatches). */
fun applyColorMatrix(matrix: FloatArray, argb: Int): Int {
    val r = Color.red(argb).toFloat()
    val g = Color.green(argb).toFloat()
    val b = Color.blue(argb).toFloat()
    val a = Color.alpha(argb).toFloat()
    fun clamp(v: Float): Int = v.coerceIn(0f, 255f).toInt()
    val nr = clamp(matrix[0] * r + matrix[1] * g + matrix[2] * b + matrix[3] * a + matrix[4])
    val ng = clamp(matrix[5] * r + matrix[6] * g + matrix[7] * b + matrix[8] * a + matrix[9])
    val nb = clamp(matrix[10] * r + matrix[11] * g + matrix[12] * b + matrix[13] * a + matrix[14])
    return Color.argb(255, nr, ng, nb)
}

/**
 * Two representative tones (skin / sky) graded by this preset at [intensity] —
 * the picker swatches are rendered from the real matrix, not stock colours.
 */
fun gradeSwatchColors(preset: GradePreset, intensity: Float): Pair<ComposeColor, ComposeColor> {
    val m = preset.matrix(intensity, 0f)
    val skin = ComposeColor(applyColorMatrix(m, Color.rgb(198, 136, 99)))
    val sky = ComposeColor(applyColorMatrix(m, Color.rgb(120, 165, 205)))
    return skin to sky
}

/** iOS-style Filters (same names as the iOS Camera filter picker). */
val FilterPresets: List<GradePreset> = listOf(
    GradePreset("vivid", "Vivid", 1.35f, 1.05f, 0f),
    GradePreset("vivid_warm", "Vivid Warm", 1.30f, 1.03f, 12f),
    GradePreset("vivid_cool", "Vivid Cool", 1.30f, 1.03f, -12f),
    GradePreset("dramatic", "Dramatic", 1.15f, 1.20f, 0f),
    GradePreset("dramatic_warm", "Dramatic Warm", 1.10f, 1.18f, 10f),
    GradePreset("dramatic_cool", "Dramatic Cool", 1.10f, 1.18f, -10f),
    GradePreset("mono", "Mono", 0f, 1.05f, 0f),
    GradePreset("silvertone", "Silvertone", 0.25f, 1.08f, -4f),
    GradePreset("noir", "Noir", 0f, 1.30f, 0f)
)

/** One Portrait lighting effect. [real] = it truly grades the capture on this platform. */
data class PortraitLight(val preset: GradePreset, val real: Boolean)

/**
 * Portrait lighting effects (iOS names). Natural / Studio / Contour are real
 * colour-grade looks baked into the captured portrait via the shared grade
 * pipeline. Stage Light and Stage Light Mono need true relighting / subject
 * relighting we cannot do honestly yet, so they render dimmed & unusable.
 */
val PortraitLights: List<PortraitLight> = listOf(
    PortraitLight(GradePreset("natural", "Natural Light", 1.0f, 1.0f, 0f), real = true),
    PortraitLight(GradePreset("studio", "Studio Light", 1.03f, 1.07f, 4f, lift = 0.05f), real = true),
    PortraitLight(GradePreset("contour", "Contour Light", 1.0f, 1.18f, 0f), real = true),
    PortraitLight(GradePreset("stage", "Stage Light", 1.0f, 1.0f, 0f), real = false),
    PortraitLight(GradePreset("stage_mono", "Stage Light Mono", 0f, 1.1f, 0f), real = false)
)

/** iOS Photographic Styles (same names as the iOS style picker). */
val StylePresets: List<GradePreset> = listOf(
    GradePreset("standard", "Standard", 1.0f, 1.0f, 0f),
    GradePreset("rich_contrast", "Rich Contrast", 1.05f, 1.22f, 0f),
    GradePreset("vibrant", "Vibrant", 1.45f, 1.05f, 0f),
    GradePreset("warm", "Warm", 1.05f, 1.0f, 16f),
    GradePreset("cool", "Cool", 1.05f, 1.0f, -16f)
)
