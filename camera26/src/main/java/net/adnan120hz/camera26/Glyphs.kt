package net.adnan120hz.camera26

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

val IosYellow = Color(0xFFFFD60A)

// ---------------------------------------------------------------------------
// Liquid Glass material (iOS 26). Translucent light-tinted gray with a
// vertical sheen: the live preview faintly shows through, a bright rim and a
// soft top highlight sell real glass.
// ---------------------------------------------------------------------------
val GlassPanelBrush = Brush.verticalGradient(
    listOf(Color(0xDB636366), Color(0xC9404043))
)
val GlassPillBrush = Brush.verticalGradient(
    listOf(Color(0xC957575B), Color(0xB03C3C40))
)
val GlassRim = Color.White.copy(alpha = 0.32f)
val GlassSheen = Color.White.copy(alpha = 0.20f)
val GlassButton = Color(0xF238383B)

@Composable
fun FlashGlyph(
    color: Color,
    modifier: Modifier = Modifier,
    off: Boolean = false,
    auto: Boolean = false
) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                moveTo(w * 0.58f, h * 0.02f)
                lineTo(w * 0.20f, h * 0.57f)
                lineTo(w * 0.42f, h * 0.57f)
                lineTo(w * 0.32f, h * 0.98f)
                lineTo(w * 0.82f, h * 0.40f)
                lineTo(w * 0.56f, h * 0.40f)
                close()
            }
            drawPath(p, color)
            if (off) {
                drawLine(
                    color, Offset(w * 0.12f, h * 0.92f), Offset(w * 0.88f, h * 0.08f),
                    strokeWidth = w * 0.09f
                )
            }
        }
        if (auto) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ApertureGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text(
            "ƒ",
            color = color,
            fontSize = 30.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Light
        )
    }
}

@Composable
fun ExposureGlyph(color: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Text("±", color = color, fontSize = 26.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
fun TimerGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2, h / 2)
        val r = w * 0.34f
        drawCircle(color, r, c, style = Stroke(width = w * 0.055f))
        drawLine(color, Offset(w / 2, h * 0.06f), Offset(w / 2, h * 0.18f), strokeWidth = w * 0.07f)
        drawLine(color, c, Offset(w / 2, h * 0.30f), strokeWidth = w * 0.06f)
        drawLine(color, c, Offset(w * 0.68f, h * 0.60f), strokeWidth = w * 0.06f)
    }
}

@Composable
fun StylesGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.055f
        val s = w * 0.34f
        val offsets = listOf(0.10f, 0.28f, 0.46f)
        for (o in offsets) {
            drawRoundRect(
                color,
                topLeft = Offset(w * o, h * (0.62f - o)),
                size = Size(s, s),
                cornerRadius = CornerRadius(w * 0.05f, w * 0.05f),
                style = Stroke(width = sw)
            )
        }
    }
}

@Composable
fun FilterGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val r = w * 0.20f
        val sw = w * 0.05f
        drawCircle(color, r, Offset(w * 0.50f, h * 0.34f), style = Stroke(sw))
        drawCircle(color, r, Offset(w * 0.36f, h * 0.60f), style = Stroke(sw))
        drawCircle(color, r, Offset(w * 0.64f, h * 0.60f), style = Stroke(sw))
    }
}

@Composable
fun NightGlyph(color: Color, paper: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(color, w * 0.30f, Offset(w * 0.44f, h * 0.52f))
        drawCircle(paper, w * 0.26f, Offset(w * 0.60f, h * 0.40f))
        // tiny stars
        drawCircle(color, w * 0.035f, Offset(w * 0.72f, h * 0.62f))
        drawCircle(color, w * 0.028f, Offset(w * 0.80f, h * 0.50f))
        drawCircle(color, w * 0.024f, Offset(w * 0.66f, h * 0.76f))
    }
}

@Composable
fun LiveGlyph(color: Color, modifier: Modifier = Modifier, auto: Boolean = false) {
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val c = Offset(w / 2, h / 2)
            drawCircle(
                color, w * 0.40f, c,
                style = Stroke(w * 0.05f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(w * 0.09f, w * 0.07f)))
            )
            drawCircle(color, w * 0.22f, c, style = Stroke(w * 0.055f))
            drawCircle(color, w * 0.06f, c)
        }
        if (auto) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 3.dp, y = 3.dp)
                    .size(11.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                Text("A", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AspectGlyph(color: Color, text: String, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val w = size.width; val h = size.height
            val sw = w * 0.055f; val l = w * 0.16f
            drawLine(color, Offset(w * 0.16f, h * 0.18f), Offset(w * 0.16f + l, h * 0.18f), sw)
            drawLine(color, Offset(w * 0.16f, h * 0.18f), Offset(w * 0.16f, h * 0.18f + l), sw)
            drawLine(color, Offset(w * 0.84f, h * 0.18f), Offset(w * 0.84f - l, h * 0.18f), sw)
            drawLine(color, Offset(w * 0.84f, h * 0.18f), Offset(w * 0.84f, h * 0.18f + l), sw)
            drawLine(color, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.16f + l, h * 0.82f), sw)
            drawLine(color, Offset(w * 0.16f, h * 0.82f), Offset(w * 0.16f, h * 0.82f - l), sw)
            drawLine(color, Offset(w * 0.84f, h * 0.82f), Offset(w * 0.84f - l, h * 0.82f), sw)
            drawLine(color, Offset(w * 0.84f, h * 0.82f), Offset(w * 0.84f, h * 0.82f - l), sw)
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SixDotsGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val r = w * 0.075f
        for (row in 0..2) for (col in 0..1) {
            drawCircle(
                color, r,
                Offset(w * (0.34f + 0.32f * col), h * (0.26f + 0.24f * row))
            )
        }
    }
}

@Composable
fun FlipGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2, h / 2)
        val r = w * 0.30f
        val sw = w * 0.07f
        drawArc(color, 300f, 130f, false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(sw))
        drawArc(color, 120f, 130f, false, topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = Stroke(sw))
        // arrowheads
        val a1 = Math.toRadians(300.0)
        val p1 = Offset(c.x + r * cos(a1).toFloat(), c.y + r * sin(a1).toFloat())
        drawLine(color, p1, Offset(p1.x + w * 0.02f, p1.y - w * 0.16f), sw)
        drawLine(color, p1, Offset(p1.x + w * 0.16f, p1.y + w * 0.06f), sw)
        val a2 = Math.toRadians(120.0)
        val p2 = Offset(c.x + r * cos(a2).toFloat(), c.y + r * sin(a2).toFloat())
        drawLine(color, p2, Offset(p2.x - w * 0.02f, p2.y + w * 0.16f), sw)
        drawLine(color, p2, Offset(p2.x - w * 0.16f, p2.y - w * 0.06f), sw)
    }
}

@Composable
fun RunnerGlyph(color: Color, modifier: Modifier = Modifier, off: Boolean = false) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.06f
        // head
        drawCircle(color, w * 0.075f, Offset(w * 0.58f, h * 0.16f))
        // torso + limbs (simple running stick figure)
        drawLine(color, Offset(w * 0.54f, h * 0.26f), Offset(w * 0.46f, h * 0.52f), sw)
        drawLine(color, Offset(w * 0.52f, h * 0.34f), Offset(w * 0.72f, h * 0.44f), sw)
        drawLine(color, Offset(w * 0.52f, h * 0.34f), Offset(w * 0.36f, h * 0.50f), sw)
        drawLine(color, Offset(w * 0.46f, h * 0.52f), Offset(w * 0.66f, h * 0.66f), sw)
        drawLine(color, Offset(w * 0.66f, h * 0.66f), Offset(w * 0.64f, h * 0.86f), sw)
        drawLine(color, Offset(w * 0.46f, h * 0.52f), Offset(w * 0.30f, h * 0.72f), sw)
        drawLine(color, Offset(w * 0.30f, h * 0.72f), Offset(w * 0.16f, h * 0.70f), sw)
        // speed lines
        drawLine(color, Offset(w * 0.06f, h * 0.36f), Offset(w * 0.24f, h * 0.36f), sw)
        drawLine(color, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.26f, h * 0.50f), sw)
        if (off) {
            drawLine(color, Offset(w * 0.10f, h * 0.92f), Offset(w * 0.90f, h * 0.08f), w * 0.055f)
        }
    }
}

@Composable
fun SunGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2, h / 2)
        val r = w * 0.20f
        drawCircle(color, r, c, style = Stroke(w * 0.06f))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = c.x + (r + w * 0.08f) * cos(a).toFloat()
            val y1 = c.y + (r + w * 0.08f) * sin(a).toFloat()
            val x2 = c.x + (r + w * 0.20f) * cos(a).toFloat()
            val y2 = c.y + (r + w * 0.20f) * sin(a).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), w * 0.05f)
        }
    }
}

@Composable
fun GridGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.05f
        drawRect(color, topLeft = Offset(w * 0.14f, h * 0.14f), size = Size(w * 0.72f, h * 0.72f), style = Stroke(sw))
        drawLine(color, Offset(w * 0.38f, h * 0.14f), Offset(w * 0.38f, h * 0.86f), sw)
        drawLine(color, Offset(w * 0.62f, h * 0.14f), Offset(w * 0.62f, h * 0.86f), sw)
        drawLine(color, Offset(w * 0.14f, h * 0.38f), Offset(w * 0.86f, h * 0.38f), sw)
        drawLine(color, Offset(w * 0.14f, h * 0.62f), Offset(w * 0.86f, h * 0.62f), sw)
    }
}

/** Expand (⤢) glyph: two diagonal arrows pointing out to opposite corners. */
@Composable
fun ExpandGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.075f
        // arrow to top-right
        drawLine(color, Offset(w * 0.38f, h * 0.62f), Offset(w * 0.80f, h * 0.20f), sw)
        drawLine(color, Offset(w * 0.56f, h * 0.18f), Offset(w * 0.82f, h * 0.18f), sw)
        drawLine(color, Offset(w * 0.82f, h * 0.18f), Offset(w * 0.82f, h * 0.44f), sw)
        // arrow to bottom-left
        drawLine(color, Offset(w * 0.62f, h * 0.38f), Offset(w * 0.20f, h * 0.80f), sw)
        drawLine(color, Offset(w * 0.44f, h * 0.82f), Offset(w * 0.18f, h * 0.82f), sw)
        drawLine(color, Offset(w * 0.18f, h * 0.82f), Offset(w * 0.18f, h * 0.56f), sw)
    }
}

/** Settings gear glyph. */
@Composable
fun GearGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val c = Offset(w / 2, h / 2)
        val sw = w * 0.055f
        drawCircle(color, w * 0.16f, c, style = Stroke(sw))
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x1 = c.x + w * 0.24f * cos(a).toFloat()
            val y1 = c.y + w * 0.24f * sin(a).toFloat()
            val x2 = c.x + w * 0.36f * cos(a).toFloat()
            val y2 = c.y + w * 0.36f * sin(a).toFloat()
            drawLine(color, Offset(x1, y1), Offset(x2, y2), sw)
        }
        drawCircle(color, w * 0.30f, c, style = Stroke(sw))
    }
}

/** Camera body glyph (permission screen / placeholders). */
@Composable
fun CameraBodyGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.06f, h * 0.26f),
            size = Size(w * 0.88f, h * 0.52f),
            cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
        )
        // top bump
        drawRoundRect(
            color,
            topLeft = Offset(w * 0.38f, h * 0.18f),
            size = Size(w * 0.24f, h * 0.12f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
        )
        // lens cutout circles (paper = black)
        drawCircle(Color.Black, w * 0.16f, Offset(w / 2, h * 0.52f))
        drawCircle(color, w * 0.10f, Offset(w / 2, h * 0.52f))
        drawCircle(Color.Black, w * 0.055f, Offset(w / 2, h * 0.52f))
    }
}

/** ✕ glyph for the iOS adjustment bars. */
@Composable
fun CloseGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.09f
        val p = w * 0.26f
        drawLine(color, Offset(p, p), Offset(w - p, h - p), sw)
        drawLine(color, Offset(w - p, p), Offset(p, h - p), sw)
    }
}

/** Isometric cube — the centre glyph of the Portrait lighting wheel. */
@Composable
fun CubeGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val sw = w * 0.055f
        val top = Offset(w * 0.5f, h * 0.08f)
        val upperLeft = Offset(w * 0.12f, h * 0.29f)
        val upperRight = Offset(w * 0.88f, h * 0.29f)
        val centre = Offset(w * 0.5f, h * 0.5f)
        val lowerLeft = Offset(w * 0.12f, h * 0.71f)
        val lowerRight = Offset(w * 0.88f, h * 0.71f)
        val bottom = Offset(w * 0.5f, h * 0.92f)
        drawLine(color, top, upperLeft, sw)
        drawLine(color, top, upperRight, sw)
        drawLine(color, upperLeft, centre, sw)
        drawLine(color, upperRight, centre, sw)
        drawLine(color, upperLeft, lowerLeft, sw)
        drawLine(color, upperRight, lowerRight, sw)
        drawLine(color, centre, bottom, sw)
        drawLine(color, lowerLeft, bottom, sw)
        drawLine(color, lowerRight, bottom, sw)
    }
}
