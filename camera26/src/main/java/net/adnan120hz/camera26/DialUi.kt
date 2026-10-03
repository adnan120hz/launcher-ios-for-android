package net.adnan120hz.camera26

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.roundToInt
import kotlin.math.sin

private fun tOf(ratio: Float, min: Float, max: Float): Float =
    (ln(ratio) - ln(min)) / (ln(max) - ln(min))

/** iOS 26 curved zoom arc: ticks scroll under a fixed yellow needle. */
@Composable
fun ArcDial(state: CameraState, modifier: Modifier = Modifier) {
    val min = state.dialMin
    val max = state.dialMax
    val tNow = tOf(state.zoomRatio, min, max)
    val sweep = 112f
    BoxWithConstraints(modifier.fillMaxWidth().height(118.dp)) {
        val w = maxWidth
        val h = maxHeight
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height * 1.98f
            val r = size.height * 1.62f
            for (i in 0..72) {
                val tt = i / 72f
                val angDeg = -90f + (tt - tNow) * sweep
                if (angDeg < -170f || angDeg > -10f) continue
                val rad = Math.toRadians(angDeg.toDouble())
                val major = i % 6 == 0
                val len = if (major) size.height * 0.17f else size.height * 0.09f
                drawLine(
                    Color.White.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(cx + r * cos(rad).toFloat(), cy + r * sin(rad).toFloat()),
                    Offset(cx + (r - len) * cos(rad).toFloat(), cy + (r - len) * sin(rad).toFloat()),
                    strokeWidth = if (major) 2.4f else 1.3f
                )
            }
            // fixed yellow needle at top center
            val nx = cx
            val ny = cy - r
            val tri = Path().apply {
                moveTo(nx, ny + 9f)
                lineTo(nx - 7f, ny - 7f)
                lineTo(nx + 7f, ny - 7f)
                close()
            }
            drawPath(tri, IosYellow)
        }
        // stop labels (ratio + equivalent MM) riding the arc
        state.quickStops().forEach { stop ->
            val tt = tOf(stop, min, max)
            val angDeg = -90f + (tt - tNow) * sweep
            if (angDeg in -158f..-22f) {
                val rad = Math.toRadians(angDeg.toDouble())
                val rDp = h * 1.62f - 42.dp
                val x = w / 2 + rDp * cos(rad).toFloat()
                val y = h * 1.98f + rDp * sin(rad).toFloat()
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(x - 16.dp, y - 8.dp)
                        .graphicsLayer { rotationZ = angDeg + 90f }
                ) {
                    Text(
                        formatRatioLabel(stop),
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (state.caps.baseEqMm > 0f) {
                        Text(
                            "${(stop * state.caps.baseEqMm).roundToInt()}MM",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 7.sp
                        )
                    }
                }
            }
        }
        Text(
            formatZoomValue(state.zoomRatio),
            color = IosYellow,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp)
        )
    }
}

/** iOS 18 horizontal zoom strip: ticks slide under a fixed center needle. */
@Composable
fun StripDial(state: CameraState, modifier: Modifier = Modifier) {
    val min = state.dialMin
    val max = state.dialMax
    val tNow = tOf(state.zoomRatio, min, max)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        val w = maxWidth
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            for (i in 0..72) {
                val tt = i / 72f
                val x = cx + (tt - tNow) * size.width * 1.5f
                if (x < 0f || x > size.width) continue
                val major = i % 6 == 0
                drawLine(
                    Color.White.copy(alpha = if (major) 0.9f else 0.45f),
                    Offset(x, size.height * 0.52f),
                    Offset(x, size.height * (if (major) 0.86f else 0.72f)),
                    strokeWidth = if (major) 2.4f else 1.3f
                )
            }
            val tri = Path().apply {
                moveTo(cx, 12f)
                lineTo(cx - 7f, 0f)
                lineTo(cx + 7f, 0f)
                close()
            }
            drawPath(tri, IosYellow)
        }
        state.quickStops().forEach { stop ->
            val tt = tOf(stop, min, max)
            val x = w / 2 + (tt - tNow) * w * 1.5f
            if (x > 8.dp && x < w - 24.dp) {
                Text(
                    formatRatioLabel(stop),
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    modifier = Modifier.offset(x - 8.dp, 2.dp)
                )
            }
        }
        Text(
            formatZoomValue(state.zoomRatio),
            color = IosYellow,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}
