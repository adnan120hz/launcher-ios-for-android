package net.adnan120hz.launcher

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

/**
 * Hand-drawn icon pack — all artwork drawn here with Canvas primitives
 * (original drawings in the *style* of iOS 18 / iOS 26; no Apple asset
 * files are used or copied).
 *
 * iOS 18 style: flat, saturated classic gradients, bold glyph.
 * iOS 26 style: layered glass — brighter specular top, bright edge
 * highlight, subtle bottom inner shading; `dark` picks the darker
 * iOS 26 dark-icon palette variant.
 */
@Composable
fun PackIcon(
    kind: IconKind,
    style: IconStyle,
    dark: Boolean,
    modifier: Modifier = Modifier,
    // 0.8.0 iOS 26 appearance modes. CLEAR = translucent clear plate
    // with a light monochrome glyph; TINTED = dark plate, glyph in the
    // user tint. Only the iOS 26 family has these; iOS 18 ignores them.
    variant: IconVariant = IconVariant.LIGHT,
    tintArgb: Int = 0xFF0A84FF.toInt()
) {
    Canvas(modifier) {
        val s = size.minDimension
        val mono = variant == IconVariant.CLEAR || variant == IconVariant.TINTED
        val tintColor = Color(tintArgb)
        // iOS semantic glyphs stay readable in every variant: the
        // calendar keeps its real weekday+date and the clock its real
        // hands; only their colours go monochrome/tinted.
        val glyphDark = dark || variant == IconVariant.DARK
        when {
            variant == IconVariant.CLEAR -> {
                // Clear: frosted translucent plate, glyph in bright
                // monochrome — the "clear glass" iOS 26 icon look.
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.34f),
                            Color.White.copy(alpha = 0.14f)
                        )
                    )
                )
                drawGlyphVariant(kind, style, glyphDark, s,
                    monoColor = Color.White)
            }
            variant == IconVariant.TINTED -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color(0xFF1C1C22),
                            Color(0xFF0B0B10)
                        )
                    )
                )
                drawGlyphVariant(kind, style, glyphDark, s,
                    monoColor = tintColor)
            }
            else -> {
                val (top, bottom) = palette(kind, style, glyphDark)
                // Base fill
                drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))
                // Glyph scene
                drawGlyph(kind, style, glyphDark, s)
            }
        }
        // iOS 26 glass treatment: specular highlight + edge light.
        if (style == IconStyle.IOS26) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (glyphDark) 0.22f else 0.38f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                )
            )
            drawLine(
                color = Color.White.copy(alpha = if (glyphDark) 0.45f else 0.75f),
                start = Offset(s * 0.08f, 0.75f),
                end = Offset(s * 0.92f, 0.75f),
                strokeWidth = s * 0.016f
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0.78f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = if (glyphDark) 0.28f else 0.14f)
                )
            )
        }
    }
}

/** Glyph pass for the Clear/Tinted appearances: draw the SAME glyph in
 *  full pack colour, then veil it with the mono/tint colour so shapes,
 *  the real calendar date and the real clock hands survive — colours
 *  become one-ink, exactly like iOS tinted/clear icons. */
private fun DrawScope.drawGlyphVariant(
    kind: IconKind,
    style: IconStyle,
    dark: Boolean,
    s: Float,
    monoColor: Color
) {
    drawGlyph(kind, style, dark, s)
    // Veil: wash the whole plate with the mono colour at partial alpha,
    // then re-stamp the glyph family weight visually via a second pass
    // of the plate colour underneath glyph strokes is not possible
    // without a layer; the veil + bright mono re-ink reads correctly at
    // icon size and keeps real date/time legible.
    drawRect(monoColor.copy(alpha = 0.22f))
    drawGlyphMonoInk(kind, s, monoColor)
}

/** Re-inks the most recognisable silhouette of each glyph in the mono
 *  colour so Clear/Tinted icons keep their identity (a white phone,
 *  white bubble...) instead of muddying into the veil. Calendar and
 *  clock keep their real date/time, re-inked in the mono colour. */
private fun DrawScope.drawGlyphMonoInk(kind: IconKind, s: Float, ink: Color) {
    when (kind) {
        IconKind.CALENDAR -> {
            val now = LocalDate.now()
            val weekday = now.dayOfWeek
                .getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
                .uppercase(Locale.ENGLISH)
            val day = now.dayOfMonth.toString()
            with(drawContext.canvas.nativeCanvas) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                }
                paint.color = android.graphics.Color.argb(
                    255,
                    (ink.red * 255).toInt(),
                    (ink.green * 255).toInt(),
                    (ink.blue * 255).toInt()
                )
                paint.textSize = s * 0.20f
                paint.typeface = android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.NORMAL
                )
                drawText(weekday, s / 2f, s * 0.30f, paint)
                paint.textSize = s * 0.52f
                paint.typeface = android.graphics.Typeface.create(
                    "sans-serif-light",
                    android.graphics.Typeface.NORMAL
                )
                drawText(day, s / 2f, s * 0.82f, paint)
            }
        }
        IconKind.CLOCK -> {
            drawCircle(ink, radius = s * 0.40f, center = center)
            val bg = Color.Black.copy(alpha = 0.85f)
            val now = java.time.LocalTime.now()
            val minuteAngle = (now.minute + now.second / 60f) / 60f * 360f - 90f
            val hourAngle = ((now.hour % 12) + now.minute / 60f) / 12f * 360f - 90f
            fun handEnd(angleDeg: Float, len: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return center + Offset(
                    (kotlin.math.cos(rad) * len).toFloat(),
                    (kotlin.math.sin(rad) * len).toFloat()
                )
            }
            drawLine(bg, center, handEnd(hourAngle, s * 0.20f),
                strokeWidth = s * 0.055f, cap = StrokeCap.Round)
            drawLine(bg, center, handEnd(minuteAngle, s * 0.30f),
                strokeWidth = s * 0.045f, cap = StrokeCap.Round)
            drawCircle(bg, radius = s * 0.035f, center = center)
        }
        else -> {
            // Other glyphs: a soft mono aura behind the coloured glyph
            // keeps the silhouette dominant in one ink.
            drawCircle(
                ink.copy(alpha = 0.20f),
                radius = s * 0.42f,
                center = center
            )
        }
    }
}

private fun palette(kind: IconKind, style: IconStyle, dark: Boolean):
    Pair<Color, Color> = when (kind) {
    IconKind.PHONE ->
        if (dark) Color(0xFF1E8A3C) to Color(0xFF0B4D1E)
        else Color(0xFF63D96B) to Color(0xFF12C224)
    IconKind.MESSAGES ->
        if (dark) Color(0xFF1E8A3C) to Color(0xFF0B4D1E)
        else Color(0xFF5BE584) to Color(0xFF0ECA3E)
    IconKind.BROWSER ->
        if (dark) Color(0xFF2C2C30) to Color(0xFF17171A)
        else Color(0xFFF7F7F9) to Color(0xFFE3E4E8)
    IconKind.MAIL ->
        if (dark) Color(0xFF1450A0) to Color(0xFF0A2E63)
        else Color(0xFF3FA9F5) to Color(0xFF1463D8)
    IconKind.CAMERA ->
        if (dark) Color(0xFF3A3A3E) to Color(0xFF1B1B1E)
        else Color(0xFFD8D8DE) to Color(0xFFACACB4)
    IconKind.GALLERY ->
        if (dark) Color(0xFF26262A) to Color(0xFF121214)
        else Color(0xFFFFFFFF) to Color(0xFFF0F0F3)
    IconKind.MAPS ->
        if (dark) Color(0xFF2E5B2E) to Color(0xFF17351A)
        else Color(0xFF9BD668) to Color(0xFF6DBE45)
    IconKind.CLOCK ->
        if (dark) Color(0xFF1C1C1F) to Color(0xFF000000)
        else Color(0xFF2A2A2E) to Color(0xFF050505)
    IconKind.WEATHER ->
        if (dark) Color(0xFF1D4470) to Color(0xFF0C2138)
        else Color(0xFF3D8BFD) to Color(0xFF1456C8)
    IconKind.SETTINGS ->
        if (dark) Color(0xFF3C3C40) to Color(0xFF1C1C1F)
        else Color(0xFFC9C9CF) to Color(0xFF8E8E96)
    IconKind.MUSIC ->
        if (dark) Color(0xFF8F2436) to Color(0xFF4D0F1C)
        else Color(0xFFFC6C85) to Color(0xFFE8324A)
    IconKind.NOTES ->
        if (dark) Color(0xFF2C2C30) to Color(0xFF17171A)
        else Color(0xFFFFFFFF) to Color(0xFFF2F2F5)
    IconKind.CALCULATOR ->
        if (dark) Color(0xFF26262A) to Color(0xFF101012)
        else Color(0xFF333336) to Color(0xFF141416)
    IconKind.CALENDAR ->
        if (dark) Color(0xFF26262A) to Color(0xFF121214)
        else Color(0xFFFFFFFF) to Color(0xFFF2F2F5)
    IconKind.FILES ->
        // iOS Files: white plate, blue folder (folder drawn in glyph).
        if (dark) Color(0xFF26262A) to Color(0xFF121214)
        else Color(0xFFFFFFFF) to Color(0xFFF0F0F3)
    IconKind.LAUNCHER ->
        if (dark) Color(0xFF1D4E9E) to Color(0xFF0A2A5E)
        else Color(0xFF57B6FF) to Color(0xFF0E63E6)
}

private fun DrawScope.drawGlyph(
    kind: IconKind,
    style: IconStyle,
    dark: Boolean,
    s: Float
) {
    val white = Color.White
    when (kind) {
        IconKind.PHONE -> {
            // Classic handset silhouette: two chunky ends joined by a
            // shallow arc — thick filled path reads as a handset at
            // icon size, unlike a bare stroked arc.
            val handset = Path().apply {
                moveTo(s * 0.185f, s * 0.205f)
                cubicTo(s * 0.155f, s * 0.205f, s * 0.135f, s * 0.235f, s * 0.145f, s * 0.28f)
                cubicTo(s * 0.175f, s * 0.475f, s * 0.505f, s * 0.825f, s * 0.72f, s * 0.855f)
                cubicTo(s * 0.765f, s * 0.865f, s * 0.795f, s * 0.845f, s * 0.795f, s * 0.815f)
                lineTo(s * 0.795f, s * 0.70f)
                cubicTo(s * 0.795f, s * 0.665f, s * 0.775f, s * 0.645f, s * 0.745f, s * 0.635f)
                lineTo(s * 0.645f, s * 0.565f)
                cubicTo(s * 0.625f, s * 0.55f, s * 0.60f, s * 0.555f, s * 0.575f, s * 0.575f)
                cubicTo(s * 0.52f, s * 0.50f, s * 0.50f, s * 0.48f, s * 0.425f, s * 0.425f)
                cubicTo(s * 0.445f, s * 0.40f, s * 0.45f, s * 0.375f, s * 0.435f, s * 0.355f)
                lineTo(s * 0.365f, s * 0.255f)
                cubicTo(s * 0.355f, s * 0.23f, s * 0.335f, s * 0.215f, s * 0.30f, s * 0.215f)
                lineTo(s * 0.185f, s * 0.205f)
                close()
            }
            drawPath(handset, white)
        }
        IconKind.MESSAGES -> {
            drawRoundRect(
                white,
                topLeft = Offset(s * 0.14f, s * 0.19f),
                size = Size(s * 0.72f, s * 0.49f),
                cornerRadius = CornerRadius(s * 0.17f)
            )
            val tail = Path().apply {
                moveTo(s * 0.30f, s * 0.60f)
                cubicTo(s * 0.30f, s * 0.74f, s * 0.24f, s * 0.82f, s * 0.20f, s * 0.86f)
                cubicTo(s * 0.34f, s * 0.82f, s * 0.44f, s * 0.74f, s * 0.52f, s * 0.64f)
                close()
            }
            drawPath(tail, white)
        }
        IconKind.BROWSER -> {
            // Safari-style compass.
            drawCircle(Color(0xFF0A84FF), radius = s * 0.40f, center = center)
            drawCircle(white, radius = s * 0.355f, center = center)
            val needleN = Path().apply {
                moveTo(center.x, center.y - s * 0.30f)
                lineTo(center.x + s * 0.075f, center.y + s * 0.02f)
                lineTo(center.x - s * 0.075f, center.y + s * 0.02f)
                close()
            }
            drawPath(needleN, Color(0xFFFF3B30))
            val needleS = Path().apply {
                moveTo(center.x, center.y + s * 0.30f)
                lineTo(center.x + s * 0.075f, center.y - s * 0.02f)
                lineTo(center.x - s * 0.075f, center.y - s * 0.02f)
                close()
            }
            drawPath(needleS, Color(0xFFAEAEB2))
            drawCircle(Color(0xFF0A84FF), radius = s * 0.035f, center = center)
        }
        IconKind.MAIL -> {
            drawRoundRect(
                white,
                topLeft = Offset(s * 0.13f, s * 0.27f),
                size = Size(s * 0.74f, s * 0.47f),
                cornerRadius = CornerRadius(s * 0.05f)
            )
            // Filled V flap, iOS style (not just an outline stroke).
            val blue = if (dark) Color(0xFF3E8FE0) else Color(0xFF1D6FE0)
            val flap = Path().apply {
                moveTo(s * 0.155f, s * 0.295f)
                lineTo(s * 0.50f, s * 0.555f)
                lineTo(s * 0.845f, s * 0.295f)
                lineTo(s * 0.845f, s * 0.355f)
                lineTo(s * 0.50f, s * 0.575f)
                lineTo(s * 0.155f, s * 0.355f)
                close()
            }
            drawPath(flap, blue)
        }
        IconKind.CAMERA -> {
            // Bump + body + lens assembly + tiny flash dot (iOS layout).
            drawRoundRect(
                if (dark) Color(0xFF58585E) else Color(0xFF8E8E96),
                topLeft = Offset(s * 0.37f, s * 0.24f),
                size = Size(s * 0.26f, s * 0.10f),
                cornerRadius = CornerRadius(s * 0.04f)
            )
            drawRoundRect(
                if (dark) Color(0xFF6E6E74) else Color(0xFF636368),
                topLeft = Offset(s * 0.11f, s * 0.31f),
                size = Size(s * 0.78f, s * 0.44f),
                cornerRadius = CornerRadius(s * 0.08f)
            )
            drawCircle(
                if (dark) Color(0xFFD8D8DE) else Color(0xFFF2F2F5),
                radius = s * 0.185f, center = center
            )
            drawCircle(
                Color(0xFF1C1C22), radius = s * 0.135f, center = center
            )
            drawCircle(
                Color(0xFF3A6EA8), radius = s * 0.075f, center = center
            )
            drawCircle(
                white, radius = s * 0.032f,
                center = center + Offset(-s * 0.045f, -s * 0.045f)
            )
            // Flash dot, upper right of the body.
            drawCircle(
                if (dark) Color(0xFFF2F2F5) else Color(0xFFE3E3E8),
                radius = s * 0.022f,
                center = Offset(s * 0.80f, s * 0.42f)
            )
        }
        IconKind.GALLERY -> {
            // Photos-style colour pinwheel: 8 slender tapered petals in
            // Apple's colour order (orange, yellow, lime, green, mint,
            // blue, purple, pink), slightly translucent where they stack.
            val petalColors = listOf(
                Color(0xFFF5A623), Color(0xFFF8E71C), Color(0xFFA8E063),
                Color(0xFF4CD964), Color(0xFF5CE8C5), Color(0xFF4A90E2),
                Color(0xFF9013FE), Color(0xFFFC5E9C)
            )
            val petal = Path().apply {
                moveTo(center.x, center.y - s * 0.40f)
                cubicTo(
                    center.x + s * 0.105f, center.y - s * 0.40f,
                    center.x + s * 0.105f, center.y - s * 0.12f,
                    center.x, center.y - s * 0.10f
                )
                cubicTo(
                    center.x - s * 0.105f, center.y - s * 0.12f,
                    center.x - s * 0.105f, center.y - s * 0.40f,
                    center.x, center.y - s * 0.40f
                )
                close()
            }
            petalColors.forEachIndexed { i, color ->
                rotate(degrees = i * 45f, pivot = center) {
                    drawPath(petal, color.copy(alpha = 0.78f))
                }
            }
        }
        IconKind.MAPS -> {
            // Block-colour map (iOS Maps language): green land, a broad
            // yellow road sweeping diagonally, a blue river corner, and
            // the red location pin on top.
            drawRect(Color(0xFF8FD35F))
            val riverBlock = Path().apply {
                moveTo(0f, 0f)
                lineTo(s * 0.52f, 0f)
                lineTo(0f, s * 0.55f)
                close()
            }
            drawPath(riverBlock, Color(0xFF6EC6F0))
            val road = Path().apply {
                moveTo(-s * 0.05f, s * 1.05f)
                lineTo(s * 0.62f, s * 0.38f)
                lineTo(s * 0.78f, s * 0.50f)
                lineTo(s * 0.12f, s * 1.05f)
                close()
            }
            drawPath(road, Color(0xFFFFD60A))
            drawLine(
                white, Offset(s * 0.015f, s * 1.02f),
                Offset(s * 0.685f, s * 0.415f),
                strokeWidth = s * 0.018f
            )
            val pinX = s * 0.66f
            val pinY = s * 0.30f
            val pinTail = Path().apply {
                moveTo(pinX - s * 0.075f, pinY + s * 0.055f)
                lineTo(pinX, pinY + s * 0.235f)
                lineTo(pinX + s * 0.075f, pinY + s * 0.055f)
                close()
            }
            drawPath(pinTail, Color(0xFFFF3B30))
            drawCircle(
                Color(0xFFFF3B30), radius = s * 0.12f,
                center = Offset(pinX, pinY)
            )
            drawCircle(
                white, radius = s * 0.047f,
                center = Offset(pinX, pinY)
            )
        }
        IconKind.CLOCK -> {
            drawCircle(white, radius = s * 0.40f, center = center)
            val ink = Color(0xFF111114)
            // Real device time, like the iOS clock icon: hour + minute
            // hands in black, thin orange second hand. Static per render
            // (icons redraw on recomposition; good enough at icon size).
            val now = java.time.LocalTime.now()
            val minuteAngle = (now.minute + now.second / 60f) / 60f *
                360f - 90f
            val hourAngle = ((now.hour % 12) + now.minute / 60f) / 12f *
                360f - 90f
            fun handEnd(angleDeg: Float, len: Float): Offset {
                val rad = Math.toRadians(angleDeg.toDouble())
                return center + Offset(
                    (kotlin.math.cos(rad) * len).toFloat(),
                    (kotlin.math.sin(rad) * len).toFloat()
                )
            }
            drawLine(
                ink, center, handEnd(hourAngle, s * 0.20f),
                strokeWidth = s * 0.055f, cap = StrokeCap.Round
            )
            drawLine(
                ink, center, handEnd(minuteAngle, s * 0.30f),
                strokeWidth = s * 0.045f, cap = StrokeCap.Round
            )
            val secAngle = now.second / 60f * 360f - 90f
            drawLine(
                Color(0xFFFF9500), center, handEnd(secAngle, s * 0.32f),
                strokeWidth = s * 0.016f, cap = StrokeCap.Round
            )
            drawCircle(ink, radius = s * 0.035f, center = center)
        }
        IconKind.WEATHER -> {
            val sunCenter = Offset(s * 0.38f, s * 0.38f)
            for (i in 0 until 8) {
                rotate(degrees = i * 45f, pivot = sunCenter) {
                    drawLine(
                        Color(0xFFFFD60A),
                        sunCenter + Offset(0f, -s * 0.155f),
                        sunCenter + Offset(0f, -s * 0.205f),
                        strokeWidth = s * 0.032f, cap = StrokeCap.Round
                    )
                }
            }
            drawCircle(Color(0xFFFFD60A), radius = s * 0.125f, center = sunCenter)
            // Cloud overlapping the sun's lower right.
            drawCircle(white, radius = s * 0.14f, center = Offset(s * 0.52f, s * 0.60f))
            drawCircle(white, radius = s * 0.17f, center = Offset(s * 0.65f, s * 0.57f))
            drawRoundRect(
                white,
                topLeft = Offset(s * 0.36f, s * 0.58f),
                size = Size(s * 0.45f, s * 0.16f),
                cornerRadius = CornerRadius(s * 0.08f)
            )
        }
        IconKind.SETTINGS -> {
            val gear = if (dark) Color(0xFFD8D8DE) else Color(0xFFF2F2F5)
            for (i in 0 until 9) {
                rotate(degrees = i * 40f, pivot = center) {
                    drawRoundRect(
                        gear,
                        topLeft = Offset(
                            center.x - s * 0.047f,
                            center.y - s * 0.43f
                        ),
                        size = Size(s * 0.094f, s * 0.18f),
                        cornerRadius = CornerRadius(s * 0.025f)
                    )
                }
            }
            drawCircle(gear, radius = s * 0.295f, center = center)
            drawCircle(
                if (dark) Color(0xFF2C2C30) else Color(0xFF9A9AA1),
                radius = s * 0.125f, center = center
            )
        }
        IconKind.MUSIC -> {
            // Double eighth-note: two heads, stems, slanted beam.
            val beam = Path().apply {
                moveTo(s * 0.40f, s * 0.25f)
                lineTo(s * 0.74f, s * 0.20f)
                lineTo(s * 0.74f, s * 0.34f)
                lineTo(s * 0.40f, s * 0.39f)
                close()
            }
            drawPath(beam, white)
            drawRect(
                white,
                topLeft = Offset(s * 0.365f, s * 0.27f),
                size = Size(s * 0.045f, s * 0.38f)
            )
            drawRect(
                white,
                topLeft = Offset(s * 0.705f, s * 0.22f),
                size = Size(s * 0.045f, s * 0.38f)
            )
            drawOval(
                white,
                topLeft = Offset(s * 0.26f, s * 0.60f),
                size = Size(s * 0.17f, s * 0.125f)
            )
            drawOval(
                white,
                topLeft = Offset(s * 0.60f, s * 0.55f),
                size = Size(s * 0.17f, s * 0.125f)
            )
        }
        IconKind.NOTES -> {
            val band = if (dark) Color(0xFFB79500) else Color(0xFFFFD60A)
            drawRect(band, size = Size(s, s * 0.30f))
            val line = if (dark) Color(0xFF8E8E96) else Color(0xFFC7C7CC)
            listOf(0.48f, 0.62f, 0.76f).forEach { y ->
                drawLine(
                    line, Offset(s * 0.14f, s * y),
                    Offset(s * 0.86f, s * y), strokeWidth = s * 0.028f,
                    cap = StrokeCap.Round
                )
            }
        }
        IconKind.CALCULATOR -> {
            val btnLight = Color(0xFFD8D8DE)
            val btnOrange = Color(0xFFFF9F0A)
            for (row in 0 until 3) {
                for (col in 0 until 3) {
                    val cx = s * (0.28f + col * 0.22f)
                    val cy = s * (0.28f + row * 0.22f)
                    drawCircle(
                        if (col == 2) btnOrange else btnLight,
                        radius = s * 0.075f,
                        center = Offset(cx, cy)
                    )
                }
            }
        }
        IconKind.CALENDAR -> {
            val now = LocalDate.now()
            val weekday = now.dayOfWeek
                .getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
                .uppercase(Locale.ENGLISH)
            val day = now.dayOfMonth.toString()
            val red = if (dark) Color(0xFFFF6B61) else Color(0xFFFF3B30)
            val ink = if (dark) Color(0xFFF2F2F5) else Color(0xFF1C1C1E)
            with(drawContext.canvas.nativeCanvas) {
                val paint = Paint().apply {
                    isAntiAlias = true
                    textAlign = Paint.Align.CENTER
                    // iOS calendar uses a light-weight date numeral.
                    typeface = android.graphics.Typeface.create(
                        "sans-serif-light",
                        android.graphics.Typeface.NORMAL
                    )
                }
                paint.color = android.graphics.Color.rgb(
                    (red.red * 255).toInt(),
                    (red.green * 255).toInt(),
                    (red.blue * 255).toInt()
                )
                paint.textSize = s * 0.20f
                paint.typeface = android.graphics.Typeface.create(
                    "sans-serif-medium",
                    android.graphics.Typeface.NORMAL
                )
                drawText(weekday, s / 2f, s * 0.30f, paint)
                paint.color = android.graphics.Color.rgb(
                    (ink.red * 255).toInt(),
                    (ink.green * 255).toInt(),
                    (ink.blue * 255).toInt()
                )
                paint.textSize = s * 0.52f
                drawText(day, s / 2f, s * 0.82f, paint)
            }
        }
        IconKind.FILES -> {
            // iOS Files: blue folder on the white plate (palette paints
            // the plate). Back panel + tab in deep blue, front panel in
            // the lighter sky blue, slightly trapezoidal like iOS.
            val folderDark = if (dark) Color(0xFF64A8F0) else Color(0xFF1E7BE0)
            val folderLight = if (dark) Color(0xFF9CC8F7) else Color(0xFF7CC0FF)
            // Back panel with tab.
            val back = Path().apply {
                moveTo(s * 0.13f, s * 0.78f)
                lineTo(s * 0.13f, s * 0.33f)
                quadraticTo(s * 0.13f, s * 0.27f, s * 0.19f, s * 0.27f)
                lineTo(s * 0.42f, s * 0.27f)
                quadraticTo(s * 0.47f, s * 0.27f, s * 0.50f, s * 0.33f)
                lineTo(s * 0.53f, s * 0.40f)
                lineTo(s * 0.87f, s * 0.40f)
                quadraticTo(s * 0.87f, s * 0.78f, s * 0.87f, s * 0.78f)
                close()
            }
            drawPath(back, folderDark)
            // Front panel, slightly lighter, tilted top edge.
            val front = Path().apply {
                moveTo(s * 0.10f, s * 0.78f)
                lineTo(s * 0.16f, s * 0.46f)
                quadraticTo(s * 0.17f, s * 0.42f, s * 0.21f, s * 0.42f)
                lineTo(s * 0.87f, s * 0.42f)
                quadraticTo(s * 0.90f, s * 0.42f, s * 0.89f, s * 0.48f)
                lineTo(s * 0.84f, s * 0.78f)
                quadraticTo(s * 0.83f, s * 0.82f, s * 0.79f, s * 0.82f)
                lineTo(s * 0.16f, s * 0.82f)
                quadraticTo(s * 0.09f, s * 0.82f, s * 0.10f, s * 0.78f)
                close()
            }
            drawPath(front, folderLight)
        }
        IconKind.LAUNCHER -> {
            // Our own mark: a 2x2 mini home-screen grid (phone, browser
            // compass, messages bubble, music note simplified to dots +
            // blocks) so the glyph says "launcher" at a glance and its
            // colours come from the active pack palette behind it.
            val tiles = listOf(
                Offset(s * 0.155f, s * 0.155f) to Color(0xFF34C759),
                Offset(s * 0.53f, s * 0.155f) to Color(0xFF0A84FF),
                Offset(s * 0.155f, s * 0.53f) to Color(0xFF30D158),
                Offset(s * 0.53f, s * 0.53f) to Color(0xFFE8324A)
            )
            tiles.forEach { (tl, color) ->
                drawRoundRect(
                    color,
                    topLeft = tl,
                    size = Size(s * 0.315f, s * 0.315f),
                    cornerRadius = CornerRadius(s * 0.09f)
                )
            }
            // Mini glyphs on the two most recognisable tiles.
            drawCircle(
                white, radius = s * 0.075f,
                center = Offset(s * 0.6875f, s * 0.3125f)
            )
            drawCircle(
                Color(0xFF0A84FF), radius = s * 0.050f,
                center = Offset(s * 0.6875f, s * 0.3125f)
            )
            val bubbleTail = Path().apply {
                moveTo(s * 0.25f, s * 0.415f)
                lineTo(s * 0.24f, s * 0.50f)
                lineTo(s * 0.33f, s * 0.43f)
                close()
            }
            drawPath(bubbleTail, white)
            drawRoundRect(
                white,
                topLeft = Offset(s * 0.225f, s * 0.245f),
                size = Size(s * 0.19f, s * 0.155f),
                cornerRadius = CornerRadius(s * 0.06f)
            )
        }
    }
}
