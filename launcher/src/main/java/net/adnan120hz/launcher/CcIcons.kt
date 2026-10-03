package net.adnan120hz.launcher

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate

/**
 * Hand-drawn vector glyphs for the Control Center (0.7.0 redesign).
 * Everything here is original path art in the iOS glyph language —
 * no Apple asset files and no emoji text glyphs. Drawn with Canvas
 * primitives so they stay crisp at any density.
 */
enum class CcGlyph {
    PLANE, WIFI, BLUETOOTH, FLASHLIGHT, SUN, SPEAKER, ROTATION_LOCK,
    MOON, CAMERA, CALCULATOR, TIMER, LOCK, GEAR, MUSIC_NOTE,
    PLAY, PAUSE, NEXT, PREV, BATTERY
}

@Composable
fun CcGlyphIcon(
    glyph: CcGlyph,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val s = size.minDimension
        val c = center
        when (glyph) {
            CcGlyph.PLANE -> {
                // Airplane silhouette pointing up-right (iOS orientation).
                val p = Path().apply {
                    moveTo(c.x + s * 0.02f, c.y - s * 0.36f)
                    cubicTo(c.x + s * 0.10f, c.y - s * 0.36f, c.x + s * 0.13f, c.y - s * 0.26f, c.x + s * 0.13f, c.y - s * 0.10f)
                    lineTo(c.x + s * 0.36f, c.y + s * 0.10f)
                    lineTo(c.x + s * 0.36f, c.y + s * 0.20f)
                    lineTo(c.x + s * 0.13f, c.y + s * 0.13f)
                    lineTo(c.x + s * 0.13f, c.y + s * 0.26f)
                    lineTo(c.x + s * 0.20f, c.y + s * 0.34f)
                    lineTo(c.x + s * 0.20f, c.y + s * 0.42f)
                    lineTo(c.x + s * 0.02f, c.y + s * 0.37f)
                    lineTo(c.x - s * 0.16f, c.y + s * 0.42f)
                    lineTo(c.x - s * 0.16f, c.y + s * 0.34f)
                    lineTo(c.x - s * 0.09f, c.y + s * 0.26f)
                    lineTo(c.x - s * 0.09f, c.y + s * 0.13f)
                    lineTo(c.x - s * 0.32f, c.y + s * 0.20f)
                    lineTo(c.x - s * 0.32f, c.y + s * 0.10f)
                    lineTo(c.x - s * 0.09f, c.y - s * 0.10f)
                    lineTo(c.x - s * 0.09f, c.y - s * 0.26f)
                    cubicTo(c.x - s * 0.09f, c.y - s * 0.36f, c.x - s * 0.06f, c.y - s * 0.36f, c.x + s * 0.02f, c.y - s * 0.36f)
                    close()
                }
                drawPath(p, color)
            }
            CcGlyph.WIFI -> {
                // Three arcs + dot, iOS radiowave language.
                listOf(0.30f to 110f, 0.21f to 110f, 0.12f to 110f).forEach { (r, sweep) ->
                    drawArc(
                        color = color,
                        startAngle = -90f - sweep / 2f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - s * r, c.y - s * r + s * 0.08f),
                        size = Size(s * r * 2f, s * r * 2f),
                        style = Stroke(width = s * 0.055f, cap = StrokeCap.Round)
                    )
                }
                drawCircle(color, radius = s * 0.045f, center = Offset(c.x, c.y + s * 0.16f))
            }
            CcGlyph.BLUETOOTH -> {
                // Bluetooth rune: two crossing triangles on a stem.
                val p = Path().apply {
                    moveTo(c.x - s * 0.05f, c.y - s * 0.34f)
                    lineTo(c.x + s * 0.22f, c.y - s * 0.10f)
                    lineTo(c.x - s * 0.14f, c.y + s * 0.14f)
                    lineTo(c.x + s * 0.22f, c.y + s * 0.34f)
                    lineTo(c.x - s * 0.05f, c.y + s * 0.10f)
                    lineTo(c.x - s * 0.05f, c.y - s * 0.34f)
                    moveTo(c.x - s * 0.05f, c.y - s * 0.10f)
                    lineTo(c.x - s * 0.22f, c.y - s * 0.24f)
                    moveTo(c.x - s * 0.05f, c.y + s * 0.14f)
                    lineTo(c.x - s * 0.22f, c.y + s * 0.28f)
                }
                drawPath(p, color, style = Stroke(width = s * 0.07f, cap = StrokeCap.Round))
            }
            CcGlyph.FLASHLIGHT -> {
                // Torch: trapezoid head, straight body, button dot.
                val head = Path().apply {
                    moveTo(c.x - s * 0.10f, c.y - s * 0.36f)
                    lineTo(c.x + s * 0.10f, c.y - s * 0.36f)
                    lineTo(c.x + s * 0.075f, c.y - s * 0.10f)
                    lineTo(c.x - s * 0.075f, c.y - s * 0.10f)
                    close()
                }
                drawPath(head, color)
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.075f, c.y - s * 0.10f),
                    size = Size(s * 0.15f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
                drawCircle(
                    color.copy(alpha = 0.35f),
                    radius = s * 0.035f,
                    center = Offset(c.x, c.y + s * 0.10f)
                )
            }
            CcGlyph.SUN -> {
                drawCircle(color, radius = s * 0.16f, center = c)
                for (i in 0 until 8) {
                    rotate(degrees = i * 45f, pivot = c) {
                        drawLine(
                            color,
                            c + Offset(0f, -s * 0.235f),
                            c + Offset(0f, -s * 0.33f),
                            strokeWidth = s * 0.05f,
                            cap = StrokeCap.Round
                        )
                    }
                }
            }
            CcGlyph.SPEAKER -> {
                val body = Path().apply {
                    moveTo(c.x - s * 0.30f, c.y - s * 0.09f)
                    lineTo(c.x - s * 0.14f, c.y - s * 0.09f)
                    lineTo(c.x + s * 0.05f, c.y - s * 0.26f)
                    lineTo(c.x + s * 0.05f, c.y + s * 0.26f)
                    lineTo(c.x - s * 0.14f, c.y + s * 0.09f)
                    lineTo(c.x - s * 0.30f, c.y + s * 0.09f)
                    close()
                }
                drawPath(body, color)
                listOf(0.16f, 0.26f).forEach { r ->
                    drawArc(
                        color = color,
                        startAngle = -55f,
                        sweepAngle = 110f,
                        useCenter = false,
                        topLeft = Offset(c.x + s * 0.02f - s * r, c.y - s * r),
                        size = Size(s * r * 2f, s * r * 2f),
                        style = Stroke(width = s * 0.05f, cap = StrokeCap.Round)
                    )
                }
            }
            CcGlyph.ROTATION_LOCK -> {
                drawArc(
                    color = color,
                    startAngle = -200f,
                    sweepAngle = 300f,
                    useCenter = false,
                    topLeft = Offset(c.x - s * 0.26f, c.y - s * 0.26f),
                    size = Size(s * 0.52f, s * 0.52f),
                    style = Stroke(width = s * 0.06f, cap = StrokeCap.Round)
                )
                // Arrow head at the open end of the ring.
                val arrow = Path().apply {
                    moveTo(c.x + s * 0.24f, c.y - s * 0.20f)
                    lineTo(c.x + s * 0.37f, c.y - s * 0.10f)
                    lineTo(c.x + s * 0.20f, c.y - s * 0.03f)
                    close()
                }
                drawPath(arrow, color)
                // Padlock body + shackle in the middle.
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.11f, c.y - s * 0.02f),
                    size = Size(s * 0.22f, s * 0.18f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(c.x - s * 0.07f, c.y - s * 0.115f),
                    size = Size(s * 0.14f, s * 0.14f),
                    style = Stroke(width = s * 0.045f)
                )
            }
            CcGlyph.MOON -> {
                val p = Path().apply {
                    moveTo(c.x + s * 0.26f, c.y - s * 0.28f)
                    cubicTo(c.x - s * 0.10f, c.y - s * 0.22f, c.x - s * 0.26f, c.y + s * 0.02f, c.x - s * 0.26f, c.y + s * 0.24f)
                    cubicTo(c.x - s * 0.26f, c.y + s * 0.30f, c.x - s * 0.05f, c.y + s * 0.34f, c.x + s * 0.05f, c.y + s * 0.30f)
                    cubicTo(c.x - s * 0.10f, c.y + s * 0.16f, c.x - s * 0.10f, c.y - s * 0.02f, c.x + s * 0.02f, c.y - s * 0.12f)
                    cubicTo(c.x + s * 0.10f, c.y - s * 0.20f, c.x + s * 0.18f, c.y - s * 0.26f, c.x + s * 0.26f, c.y - s * 0.28f)
                    close()
                }
                drawPath(p, color)
            }
            CcGlyph.CAMERA -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.14f, c.y - s * 0.36f),
                    size = Size(s * 0.28f, s * 0.10f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.34f, c.y - s * 0.27f),
                    size = Size(s * 0.68f, s * 0.52f),
                    cornerRadius = CornerRadius(s * 0.09f)
                )
                drawCircle(color.copy(alpha = 0.0f), radius = 0f, center = c)
                drawCircle(
                    Color.Black.copy(alpha = 0.55f),
                    radius = s * 0.155f,
                    center = c + Offset(0f, s * 0.01f)
                )
                drawCircle(
                    color,
                    radius = s * 0.155f,
                    center = c + Offset(0f, s * 0.01f),
                    style = Stroke(width = s * 0.028f)
                )
                drawCircle(
                    Color.White,
                    radius = s * 0.045f,
                    center = c + Offset(-s * 0.05f, -s * 0.04f)
                )
            }
            CcGlyph.CALCULATOR -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.26f, c.y - s * 0.36f),
                    size = Size(s * 0.52f, s * 0.72f),
                    cornerRadius = CornerRadius(s * 0.07f),
                    style = Stroke(width = s * 0.05f)
                )
                drawLine(
                    color,
                    Offset(c.x - s * 0.17f, c.y - s * 0.16f),
                    Offset(c.x + s * 0.17f, c.y - s * 0.16f),
                    strokeWidth = s * 0.05f,
                    cap = StrokeCap.Round
                )
                for (row in 0 until 3) {
                    for (col in 0 until 3) {
                        drawCircle(
                            color,
                            radius = s * 0.038f,
                            center = Offset(
                                c.x + (col - 1) * s * 0.15f,
                                c.y + s * 0.02f + row * s * 0.13f
                            )
                        )
                    }
                }
            }
            CcGlyph.TIMER -> {
                drawCircle(
                    color, radius = s * 0.30f, center = c,
                    style = Stroke(width = s * 0.055f)
                )
                drawLine(
                    color, c, c + Offset(0f, -s * 0.19f),
                    strokeWidth = s * 0.05f, cap = StrokeCap.Round
                )
                drawLine(
                    color, c, c + Offset(s * 0.13f, s * 0.05f),
                    strokeWidth = s * 0.05f, cap = StrokeCap.Round
                )
                drawLine(
                    color,
                    Offset(c.x - s * 0.10f, c.y - s * 0.40f),
                    Offset(c.x + s * 0.10f, c.y - s * 0.40f),
                    strokeWidth = s * 0.06f, cap = StrokeCap.Round
                )
            }
            CcGlyph.LOCK -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.24f, c.y - s * 0.06f),
                    size = Size(s * 0.48f, s * 0.38f),
                    cornerRadius = CornerRadius(s * 0.06f)
                )
                drawArc(
                    color = color,
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(c.x - s * 0.15f, c.y - s * 0.30f),
                    size = Size(s * 0.30f, s * 0.30f),
                    style = Stroke(width = s * 0.07f)
                )
                drawCircle(
                    Color.Black.copy(alpha = 0.6f),
                    radius = s * 0.05f,
                    center = c + Offset(0f, s * 0.13f)
                )
            }
            CcGlyph.GEAR -> {
                for (i in 0 until 8) {
                    rotate(degrees = i * 45f, pivot = c) {
                        drawRoundRect(
                            color,
                            topLeft = Offset(
                                c.x - s * 0.055f, c.y - s * 0.40f
                            ),
                            size = Size(s * 0.11f, s * 0.17f),
                            cornerRadius = CornerRadius(s * 0.03f)
                        )
                    }
                }
                drawCircle(color, radius = s * 0.28f, center = c)
                drawCircle(
                    Color.Black.copy(alpha = 0.45f),
                    radius = s * 0.115f,
                    center = c
                )
            }
            CcGlyph.MUSIC_NOTE -> {
                val beam = Path().apply {
                    moveTo(c.x - s * 0.14f, c.y - s * 0.30f)
                    lineTo(c.x + s * 0.26f, c.y - s * 0.36f)
                    lineTo(c.x + s * 0.26f, c.y - s * 0.20f)
                    lineTo(c.x - s * 0.14f, c.y - s * 0.26f)
                    close()
                }
                drawPath(beam, color)
                drawRect(
                    color,
                    topLeft = Offset(c.x - s * 0.165f, c.y - s * 0.28f),
                    size = Size(s * 0.035f, s * 0.42f)
                )
                drawRect(
                    color,
                    topLeft = Offset(c.x + s * 0.235f, c.y - s * 0.34f),
                    size = Size(s * 0.035f, s * 0.42f)
                )
                drawOval(
                    color,
                    topLeft = Offset(c.x - s * 0.30f, c.y + s * 0.08f),
                    size = Size(s * 0.16f, s * 0.12f)
                )
                drawOval(
                    color,
                    topLeft = Offset(c.x + s * 0.10f, c.y + s * 0.02f),
                    size = Size(s * 0.16f, s * 0.12f)
                )
            }
            CcGlyph.PLAY -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.16f, c.y - s * 0.24f)
                    lineTo(c.x + s * 0.22f, c.y)
                    lineTo(c.x - s * 0.16f, c.y + s * 0.24f)
                    close()
                }
                drawPath(p, color)
            }
            CcGlyph.PAUSE -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.20f, c.y - s * 0.22f),
                    size = Size(s * 0.13f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.04f)
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x + s * 0.07f, c.y - s * 0.22f),
                    size = Size(s * 0.13f, s * 0.44f),
                    cornerRadius = CornerRadius(s * 0.04f)
                )
            }
            CcGlyph.NEXT -> {
                val p = Path().apply {
                    moveTo(c.x - s * 0.26f, c.y - s * 0.20f)
                    lineTo(c.x + s * 0.06f, c.y)
                    lineTo(c.x - s * 0.26f, c.y + s * 0.20f)
                    close()
                }
                drawPath(p, color)
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x + s * 0.14f, c.y - s * 0.20f),
                    size = Size(s * 0.09f, s * 0.40f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
            }
            CcGlyph.PREV -> {
                val p = Path().apply {
                    moveTo(c.x + s * 0.26f, c.y - s * 0.20f)
                    lineTo(c.x - s * 0.06f, c.y)
                    lineTo(c.x + s * 0.26f, c.y + s * 0.20f)
                    close()
                }
                drawPath(p, color)
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.23f, c.y - s * 0.20f),
                    size = Size(s * 0.09f, s * 0.40f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
            }
            CcGlyph.BATTERY -> {
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.34f, c.y - s * 0.16f),
                    size = Size(s * 0.60f, s * 0.32f),
                    cornerRadius = CornerRadius(s * 0.08f),
                    style = Stroke(width = s * 0.045f)
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x - s * 0.29f, c.y - s * 0.11f),
                    size = Size(s * 0.34f, s * 0.22f),
                    cornerRadius = CornerRadius(s * 0.05f)
                )
                drawRoundRect(
                    color,
                    topLeft = Offset(c.x + s * 0.29f, c.y - s * 0.07f),
                    size = Size(s * 0.07f, s * 0.14f),
                    cornerRadius = CornerRadius(s * 0.03f)
                )
            }
        }
    }
}
