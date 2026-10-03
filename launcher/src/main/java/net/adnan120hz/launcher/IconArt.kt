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
    modifier: Modifier = Modifier
) {
    Canvas(modifier) {
        val s = size.minDimension
        val (top, bottom) = palette(kind, style, dark)
        // Base fill
        drawRect(brush = Brush.verticalGradient(listOf(top, bottom)))
        // Glyph scene
        drawGlyph(kind, style, dark, s)
        // iOS 26 glass treatment: specular highlight + edge light.
        if (style == IconStyle.IOS26) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = if (dark) 0.22f else 0.38f),
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                )
            )
            drawLine(
                color = Color.White.copy(alpha = if (dark) 0.45f else 0.75f),
                start = Offset(s * 0.08f, 0.75f),
                end = Offset(s * 0.92f, 0.75f),
                strokeWidth = s * 0.016f
            )
            drawRect(
                brush = Brush.verticalGradient(
                    0.78f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = if (dark) 0.28f else 0.14f)
                )
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
        if (dark) Color(0xFF1C3D66) to Color(0xFF0B1E38)
        else Color(0xFF5EB2FF) to Color(0xFF1E7BE0)
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
            // Handset as a thick-stroked arc with round caps.
            val path = Path().apply {
                moveTo(s * 0.22f, s * 0.26f)
                cubicTo(
                    s * 0.30f, s * 0.62f,
                    s * 0.70f, s * 0.62f,
                    s * 0.78f, s * 0.26f
                )
            }
            drawPath(
                path, white,
                style = Stroke(width = s * 0.17f, cap = StrokeCap.Round)
            )
        }
        IconKind.MESSAGES -> {
            drawRoundRect(
                white,
                topLeft = Offset(s * 0.15f, s * 0.20f),
                size = Size(s * 0.70f, s * 0.47f),
                cornerRadius = CornerRadius(s * 0.16f)
            )
            val tail = Path().apply {
                moveTo(s * 0.28f, s * 0.62f)
                lineTo(s * 0.27f, s * 0.84f)
                lineTo(s * 0.47f, s * 0.65f)
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
            val blue = if (dark) Color(0xFF1450A0) else Color(0xFF1D6FE0)
            val flap = Path().apply {
                moveTo(s * 0.16f, s * 0.30f)
                lineTo(s * 0.50f, s * 0.54f)
                lineTo(s * 0.84f, s * 0.30f)
            }
            drawPath(
                flap, blue,
                style = Stroke(width = s * 0.038f, cap = StrokeCap.Round)
            )
        }
        IconKind.CAMERA -> {
            // Bump + body + lens assembly.
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
        }
        IconKind.GALLERY -> {
            // Photos-style colour pinwheel: 8 translucent petals.
            val petalColors = listOf(
                Color(0xFFF5A623), Color(0xFFF8E71C), Color(0xFF7ED321),
                Color(0xFF50E3C2), Color(0xFF4A90E2), Color(0xFF9013FE),
                Color(0xFFFF5E9C), Color(0xFFFC3D39)
            )
            petalColors.forEachIndexed { i, color ->
                rotate(degrees = i * 45f, pivot = center) {
                    drawOval(
                        color.copy(alpha = 0.72f),
                        topLeft = Offset(
                            center.x - s * 0.085f,
                            center.y - s * 0.36f
                        ),
                        size = Size(s * 0.17f, s * 0.34f)
                    )
                }
            }
        }
        IconKind.MAPS -> {
            // Stylised map: blue river band, two roads, red pin.
            val river = Path().apply {
                moveTo(0f, s * 0.30f)
                lineTo(s * 0.55f, 0f)
                lineTo(s * 0.85f, 0f)
                lineTo(s * 0.30f, s * 0.30f)
                close()
            }
            drawPath(river, Color(0xFF7FC8F8))
            drawLine(
                Color(0xFFFFCC00), Offset(0f, s * 1.02f),
                Offset(s, s * 0.48f), strokeWidth = s * 0.14f
            )
            drawLine(
                white, Offset(s * 0.12f, 0f),
                Offset(s * 0.88f, s), strokeWidth = s * 0.07f
            )
            drawLine(
                Color(0xFFFFCC00), Offset(s * 0.12f, 0f),
                Offset(s * 0.88f, s), strokeWidth = s * 0.028f
            )
            val pinX = s * 0.68f
            val pinY = s * 0.34f
            val pinTail = Path().apply {
                moveTo(pinX - s * 0.07f, pinY + s * 0.05f)
                lineTo(pinX, pinY + s * 0.22f)
                lineTo(pinX + s * 0.07f, pinY + s * 0.05f)
                close()
            }
            drawPath(pinTail, Color(0xFFFF3B30))
            drawCircle(
                Color(0xFFFF3B30), radius = s * 0.115f,
                center = Offset(pinX, pinY)
            )
            drawCircle(
                white, radius = s * 0.045f,
                center = Offset(pinX, pinY)
            )
        }
        IconKind.CLOCK -> {
            drawCircle(white, radius = s * 0.40f, center = center)
            val ink = Color(0xFF111114)
            drawLine(
                ink, center, center + Offset(0f, -s * 0.235f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round
            )
            drawLine(
                ink, center, center + Offset(s * 0.16f, s * 0.06f),
                strokeWidth = s * 0.05f, cap = StrokeCap.Round
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
            for (i in 0 until 8) {
                rotate(degrees = i * 45f, pivot = center) {
                    drawRoundRect(
                        gear,
                        topLeft = Offset(
                            center.x - s * 0.055f,
                            center.y - s * 0.42f
                        ),
                        size = Size(s * 0.11f, s * 0.17f),
                        cornerRadius = CornerRadius(s * 0.03f)
                    )
                }
            }
            drawCircle(gear, radius = s * 0.30f, center = center)
            drawCircle(
                if (dark) Color(0xFF2C2C30) else Color(0xFF9A9AA1),
                radius = s * 0.135f, center = center
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
                }
                paint.color = android.graphics.Color.rgb(
                    (red.red * 255).toInt(),
                    (red.green * 255).toInt(),
                    (red.blue * 255).toInt()
                )
                paint.textSize = s * 0.20f
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
    }
}
