package net.adnan120hz.camera26

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private fun qualityOf(name: String): Quality = when (name) {
    "UHD" -> Quality.UHD
    "FHD" -> Quality.FHD
    "HD" -> Quality.HD
    else -> Quality.SD
}

private fun latestMediaUri(resolver: ContentResolver): Uri? {
    fun queryLatest(collection: Uri): Pair<Uri, Long>? {
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED)
        return try {
            resolver.query(
                collection, proj, null, null,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { c ->
                if (c.moveToFirst()) {
                    ContentUris.withAppendedId(collection, c.getLong(0)) to c.getLong(1)
                } else null
            }
        } catch (e: Throwable) {
            null
        }
    }
    val img = queryLatest(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
    val vid = queryLatest(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
    return when {
        img == null -> vid?.first
        vid == null -> img.first
        else -> if (img.second >= vid.second) img.first else vid.first
    }
}

/** Real display size in pixels — the target the preview resolution adapts to. */
private fun screenRealSizePx(context: Context): Pair<Int, Int> {
    return try {
        val wm = context.getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30 && wm != null) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            @Suppress("DEPRECATION")
            val dm = context.resources.displayMetrics
            dm.widthPixels to dm.heightPixels
        }
    } catch (e: Throwable) {
        1080 to 2400
    }
}

/** Copy a finished time-lapse MP4 from cache into MediaStore (Movies/Camera26). */
private fun saveVideoFileToMediaStore(context: Context, file: File): Uri? {
    return try {
        val resolver = context.contentResolver
        val name = "TL_" + java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
            .format(java.util.Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Camera26")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            file.inputStream().use { input -> input.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        uri
    } catch (e: Throwable) {
        null
    }
}

@Composable
fun CameraScreen() {
    // Screen-adaptive UI (core user complaint): every dp/sp dimension in the
    // camera UI scales with the physical screen width, so small and large
    // phones render proportionally instead of one fixed size for all.
    // Base design width = 393 dp, scale clamped to a sane range.
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val scale = (maxWidth.value / 393f).coerceIn(0.82f, 1.18f)
        val base = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(base.density * scale, base.fontScale)
        ) {
            CameraScreenContent()
        }
    }
}

@Composable
private fun CameraScreenContent() {
    val context = LocalContext.current
    val lifecycleOwner = remember { context as LifecycleOwner }
    val state = remember { CameraState(context) }
    val controller = remember { CameraController(context) }
    val scope = rememberCoroutineScope()
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val captureFlash = remember { Animatable(0f) }
    val focusScale = remember { Animatable(1f) }
    var exposureAcc by remember { mutableFloatStateOf(0f) }
    var firstModeEffect by remember { mutableStateOf(true) }
    val haptics = LocalHapticFeedback.current
    val screenSizePx = remember { screenRealSizePx(context) }
    val modePulse = remember { Animatable(0f) }
    var timelapseEncoder by remember { mutableStateOf<TimelapseEncoder?>(null) }
    var timelapseFile by remember { mutableStateOf<File?>(null) }

    // Software gyro EIS (ACTION): the GL pipeline owns the camera while it
    // is live; CameraX stays unbound in that window.
    var eisPipeline by remember { mutableStateOf<EisPipeline?>(null) }
    var eisSurface by remember { mutableStateOf<Surface?>(null) }
    var eisSurfaceSize by remember { mutableStateOf(0 to 0) }
    var eisFacing by remember { mutableStateOf(state.facingFront) }
    val eisWanted = state.mode == CamMode.VIDEO && state.actionOn && state.eisEnabled &&
        state.caps.gyroAvailable && !state.eisFailed
    // A shutter tap that lands while the EIS pipeline is still spinning up
    // (or whose encoder start failed) is parked here and resolved on the
    // EIS / plain path as soon as either is ready — the tap is never dropped.
    var pendingEisRecord by remember { mutableStateOf(false) }
    var pendingFallbackRecord by remember { mutableStateOf(false) }
    var lastZoomStopIdx by remember { mutableStateOf(-1) }

    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        state.micGranted = granted
        state.micAsked = true
    }
    fun requestMic() {
        state.micAsked = true
        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun refreshThumb() {
        scope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val latest = latestMediaUri(resolver) ?: return@launch
                state.thumbUri = latest
                state.thumbBitmap = try {
                    resolver.loadThumbnail(latest, Size(160, 160), null)
                } catch (e: Throwable) {
                    null
                }
            } catch (e: Throwable) { /* gallery empty */ }
        }
    }

    fun doCapture() {
        // iOS-style capture blink: a fast black flash, not a white one.
        scope.launch {
            captureFlash.snapTo(0.85f)
            captureFlash.animateTo(0f, tween(200))
        }
        // Portrait without an OEM BOKEH extension runs the real segmentation
        // pipeline; the ƒ slider controls its blur strength.
        val portraitStrength = if (
            state.mode == CamMode.PORTRAIT &&
            state.extensionMode != androidx.camera.extensions.ExtensionMode.BOKEH
        ) state.apertureStrength else null
        // FILTER / STYLES / PORTRAIT-lighting: a real colour grade (with the
        // user's intensity / warmth adjustments) baked into the photo.
        val gradeMatrix = if (
            state.mode == CamMode.PHOTO || state.mode == CamMode.PORTRAIT
        ) state.activeGradeMatrix() else null
        // CONFIG (the app's own quality settings) applies to PHOTO captures.
        val photoConfig = if (state.mode == CamMode.PHOTO) state.photoConfig() else null
        controller.takePhoto(
            squareCrop = state.aspect == PhotoAspect.SQUARE,
            gradeMatrix = gradeMatrix,
            portraitStrength = portraitStrength,
            photoConfig = photoConfig,
            onSaved = { refreshThumb() },
            onError = { msg -> state.toast = msg },
            onNotice = { msg -> state.toast = msg }
        )
    }

    fun startRecording() {
        if (!controller.videoReady) {
            state.toast = "Video tidak tersedia di mode ini"
            return
        }
        try {
            val sloMoWanted = state.mode == CamMode.SLO_MO && state.caps.sloMoFps >= 120
            controller.startRecording(state.micGranted) { uri, ok ->
                state.isRecording = false
                state.quickTake = false
                if (ok && uri != null) {
                    if (sloMoWanted) {
                        // 2.0.0 item 4: real slo-mo — measure what the HAL
                        // actually delivered, then retime to 30fps playback
                        // when it truly is high-speed. The toast reports
                        // the measured numbers either way (honest labels).
                        state.sloMoProcessing = true
                        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val res = SloMoRetime.retime(context, uri)
                            kotlinx.coroutines.withContext(
                                kotlinx.coroutines.Dispatchers.Main
                            ) {
                                state.sloMoProcessing = false
                                state.toast = when {
                                    res.retimedUri != null ->
                                        "SLO-MO jadi: ${res.measuredFps.roundToInt()} fps " +
                                            "diputar 30 fps (${SloMoRetime.factorText(res.measuredFps)} " +
                                            "lebih lambat, tanpa audio)"
                                    res.measuredFps > 0f ->
                                        "SLO-MO tidak tercapai — device merekam " +
                                            "${res.measuredFps.roundToInt()} fps; video asli disimpan"
                                    else ->
                                        "SLO-MO gagal diproses — video asli disimpan"
                                }
                                refreshThumb()
                            }
                        }
                    } else {
                        refreshThumb()
                    }
                } else if (!ok) {
                    state.toast = "Gagal merekam video"
                }
            }
            state.isRecording = true
            state.recordSeconds = 0
        } catch (e: Throwable) {
            state.toast = "Gagal memulai rekaman"
        }
    }

    fun stopRecording() {
        controller.stopRecording()
        state.isRecording = false
    }

    // --- Software gyro EIS (ACTION) ------------------------------------------
    // The pipeline's hard cap is 1920x1080: a 4K selection records 1080p and
    // the UI flags that (state.eisCapped). Encoder failure drops back to the
    // plain CameraX path so a recording is never lost to EIS.
    fun eisRecordSize(): Pair<Int, Int> = when (state.videoRes?.qualityName) {
        "UHD", "FHD" -> 1920 to 1080
        "HD" -> 1280 to 720
        else -> 854 to 480
    }

    fun startEisRecording() {
        val pipeline = eisPipeline
        if (pipeline == null || !state.eisActive) {
            state.toast = "EIS belum siap"
            return
        }
        val (w, h) = eisRecordSize()
        if (pipeline.startRecording(state.micGranted, w, h)) {
            state.isRecording = true
            state.recordSeconds = 0
        } else {
            state.eisFailed = true
            // The tap is not lost: it continues on the plain CameraX path
            // as soon as that is bound again (see the resolver effects).
            pendingFallbackRecord = true
            state.toast = "EIS gagal mulai di perangkat ini — memakai rekam biasa"
        }
    }

    fun stopEisRecording() {
        eisPipeline?.stopRecording()
        state.isRecording = false
    }

    // --- Panorama: guided sweep -> cylindrical stitch -----------------------
    val pano = remember { PanoEngine(context) }
    val panoRt = remember { PanoRuntime() }

    fun capturePanoFrame(yaw: Float) {
        if (panoRt.captureBusy) return
        panoRt.captureBusy = true
        val hfov = cameraHfovDeg(context, state.facingFront, state.zoomRatio)
        controller.captureBitmap { bmp ->
            panoRt.captureBusy = false
            if (bmp == null) return@captureBitmap
            if (!state.panoSweeping) {
                try { bmp.recycle() } catch (e: Throwable) { /* already gone */ }
                return@captureBitmap
            }
            val scaled = PanoStitcher.downscale(bmp, PANO_WORK_H)
            pano.addFrame(PanoFrame(scaled, yaw, hfov))
            state.panoFrameCount = pano.frameCount
        }
    }

    fun startPano() {
        if (state.panoSweeping || state.panoStitching ||
            state.isRecording || state.timelapseRunning
        ) {
            return
        }
        pano.discardFrames()
        // Motion source: the real gyroscope (restored 80dcd84 tracker);
        // devices without one fall back to timed captures, same guide.
        val gyroOk = state.caps.gyroAvailable
        pano.start(gyroOk)
        panoRt.reset(pano.yawDeg())
        state.panoSweeping = true
        state.panoProgress = 0f
        state.panoFrameCount = 0
        state.panoTooFast = false
        state.panoDirRight = true
        capturePanoFrame(panoRt.lastCaptureYaw)
        if (!gyroOk) {
            // Honest gate: no gyroscope -> timed captures, same guide.
            state.toast =
                "Gyroscope tidak ada — panorama memakai tangkapan interval waktu"
        }
    }

    fun finishPano() {
        if (!state.panoSweeping) return
        state.panoSweeping = false
        pano.stop()
        val frames = pano.takeFrames()
        state.panoFrameCount = 0
        state.panoProgress = 0f
        state.panoTooFast = false
        if (frames.size < 2) {
            frames.forEach { try { it.bmp.recycle() } catch (e: Throwable) { /* gone */ } }
            state.toast = "Panorama terlalu pendek — geser lebih jauh"
            return
        }
        state.panoStitching = true
        scope.launch(Dispatchers.Default) {
            val stitched = try {
                PanoStitcher.stitch(frames)
            } catch (e: Throwable) {
                null
            }
            if (stitched != null) {
                val uri = PanoSaver.saveJpeg(context, stitched)
                try { stitched.recycle() } catch (e: Throwable) { /* gone */ }
                frames.forEach { try { it.bmp.recycle() } catch (e: Throwable) { /* gone */ } }
                withContext(Dispatchers.Main) {
                    state.panoStitching = false
                    if (uri != null) {
                        refreshThumb()
                        state.toast = "Panorama tersimpan"
                    } else {
                        state.toast = "Panorama gagal disimpan"
                    }
                }
            } else {
                // Honest fallback: keep the middle (best-aimed) frame.
                val uri = PanoSaver.saveJpeg(context, frames[frames.size / 2].bmp)
                frames.forEach { try { it.bmp.recycle() } catch (e: Throwable) { /* gone */ } }
                withContext(Dispatchers.Main) {
                    state.panoStitching = false
                    if (uri != null) {
                        refreshThumb()
                        state.toast = "Jahitan panorama gagal — disimpan foto terbaik"
                    } else {
                        state.toast = "Panorama gagal disimpan"
                    }
                }
            }
        }
    }

    fun cancelPano() {
        if (!state.panoSweeping) return
        state.panoSweeping = false
        pano.stop()
        pano.discardFrames()
        state.panoProgress = 0f
        state.panoFrameCount = 0
        state.panoTooFast = false
        state.toast = "Panorama dibatalkan"
    }

    // --- Time-lapse: real interval capture -> MP4 ---------------------------
    suspend fun captureBitmapSuspend(): Bitmap? = suspendCancellableCoroutine { cont ->
        controller.captureBitmap { bmp -> if (cont.isActive) cont.resume(bmp) }
    }

    fun startTimelapse() {
        // Output size follows the chosen video resolution (the top-left pill).
        val longSide = when (state.videoRes?.qualityName) {
            "UHD", "FHD" -> 1920
            "HD" -> 1280
            "SD" -> 854
            else -> 1920
        }
        val (fw, fh) = when (state.aspect) {
            PhotoAspect.RATIO_16_9 -> longSide to (longSide * 9 / 16)
            PhotoAspect.RATIO_4_3 -> (longSide * 3 / 4) to (longSide * 9 / 16)
            PhotoAspect.SQUARE -> minOf(longSide, 1080) to minOf(longSide, 1080)
        }
        val file = File(context.cacheDir, "timelapse_${System.currentTimeMillis()}.mp4")
        val enc = TimelapseEncoder(file)
        if (!enc.prepare(fw, fh) || !enc.start()) {
            state.toast = "Time-Lapse tidak dapat dimulai di perangkat ini"
            return
        }
        timelapseEncoder = enc
        timelapseFile = file
        state.timelapseFrames = 0
        state.timelapseRunning = true
    }

    fun stopTimelapse() {
        state.timelapseRunning = false
        val enc = timelapseEncoder
        val file = timelapseFile
        timelapseEncoder = null
        timelapseFile = null
        scope.launch(Dispatchers.IO) {
            val ok = enc?.stop() ?: false
            if (ok && file != null) {
                val uri = saveVideoFileToMediaStore(context, file)
                try { file.delete() } catch (_: Throwable) { /* cache cleanup */ }
                if (uri != null) {
                    refreshThumb()
                    state.toast = "Time-Lapse tersimpan"
                } else {
                    state.toast = "Time-Lapse gagal disimpan"
                }
            } else {
                state.toast = "Time-Lapse gagal dibuat"
            }
        }
    }

    fun setExposureIndex(i: Int) {
        state.exposureIndex = i.coerceIn(state.exposureMin, state.exposureMax)
    }

    // --- Smooth zoom -------------------------------------------------------
    // Gestures never touch the camera directly: they only move the target.
    // The animator chases the target with an under-damped spring and applies
    // the eased value frame-by-frame, so pinch and dial zooms glide (and the
    // displayed ratio/label follows the same eased value).
    fun setZoomTarget(t: Float) {
        // Buttons / pinch / programmatic zooms: EXACTLY the 80dcd84 path
        // the user praised ("ga se smooth awal") — same clamp, and the
        // animator below only ever uses the original spring for them.
        // Resetting the dial flag here also means a stuck flag can never
        // leak dial behaviour into a button tap again (2.0.0 item 7).
        state.zoomDialDriven = false
        state.zoomTarget = if (state.facingFront) {
            t.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
        } else {
            t.coerceIn(
                state.dialMin.coerceAtLeast(0.1f),
                state.dialMax.coerceAtMost(40f)
            )
        }
    }

    /** Dial zooms only: the dial ceiling is the full 40x (item 3), and
     *  the animator chases with the dial's own tighter spring. */
    fun setZoomTargetFromDial(t: Float) {
        state.zoomDialDriven = true
        state.zoomTarget = if (state.facingFront) {
            t.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
        } else {
            t.coerceIn(state.dialMin.coerceAtLeast(0.1f), 40f)
        }
    }

    fun applyZoomAbsolute(v: Float) {
        if (state.facingFront) {
            controller.setZoomCropOverride(null)
            controller.applyZoomRatio(
                v.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
            )
            return
        }
        val session = state.sessionFor(v)
        if (session.cameraId != state.desiredSessionId) {
            // Crossing into another physical lens: ask for a rebind; the bind
            // effect re-applies the current value once the new session is live.
            // Clear any crop-zoom override so it cannot leak onto the
            // incoming session for a frame (audit fix, 2.0.0).
            controller.setZoomCropOverride(null)
            state.desiredSessionId = session.cameraId
            state.currentSessionRatio = session.ratio
            state.sessionBound = false
        } else if (state.sessionBound) {
            val digital = v / session.ratio
            if (session.cameraId == null && digital > state.sessionMaxZoom) {
                // 2.0.0 item 3: beyond CameraX's own zoom range, the
                // remainder is applied as a REAL Camera2 crop-region
                // request (centred crop of the active array, zoom-ratio
                // key pinned to 1.0 so the HAL does not double-zoom) —
                // preview, photo and video all read the same request.
                controller.setZoomCropOverride(digital)
            } else {
                controller.setZoomCropOverride(null)
                controller.applyZoomRatio(
                    digital.coerceIn(
                        state.sessionMinZoom.coerceAtLeast(0.05f),
                        state.sessionMaxZoom
                    )
                )
            }
        }
    }

    val zoomAnim = remember { Animatable(1f) }
    LaunchedEffect(state.zoomTarget) {
        // Dial input gets its own chase: higher stiffness + near-critical
        // damping tracks the finger tightly with no overshoot jerk —
        // smoother than the button spring, but never laggy. Buttons and
        // pinch keep the softer long-glide spring.
        val spec = if (state.zoomDialDriven) {
            spring<Float>(dampingRatio = 0.92f, stiffness = 420f)
        } else {
            spring<Float>(dampingRatio = 0.88f, stiffness = Spring.StiffnessLow)
        }
        zoomAnim.animateTo(state.zoomTarget, spec) {
            state.zoomRatio = value
            applyZoomAbsolute(value)
        }
    }

    fun selectMode(m: CamMode) {
        if (state.isRecording || state.timelapseRunning || state.panoSweeping || m == state.mode) return
        // Unavailable modes render dimmed in the carousel; selecting is a no-op.
        if (!state.modeAvailable(m)) return
        state.mode = m
        state.sheet = SheetKind.NONE
        state.countdown = null
        if ((m == CamMode.VIDEO || m == CamMode.SLO_MO) && !state.micGranted && !state.micAsked) {
            requestMic()
        }
    }

    fun swipeMode(dir: Int) {
        // fix8: ONE settle rule shared with the pill drag (see
        // nextAvailableModeIndex): a released gesture moves at most one
        // hop, always landing on the next AVAILABLE mode in that
        // direction — unavailable modes are never a landing spot and a
        // swipe can never overshoot by a mode.
        val modes = CamMode.values().toList()
        val idx = modes.indexOf(state.mode)
        if (idx < 0) return
        val target = nextAvailableModeIndex(modes, idx, dir) { state.modeAvailable(it) }
        if (target != idx) selectMode(modes[target])
    }

    val actions = CameraActions(
        onModeSelect = { m -> selectMode(m) },
        onOpenSheet = { kind -> state.sheet = kind },
        onCloseSheet = { state.sheet = SheetKind.NONE },
        onShutterTap = {
            when {
                state.timelapseRunning -> stopTimelapse()
                state.isRecording ->
                    if (eisPipeline?.recording == true) stopEisRecording() else stopRecording()
                state.panoSweeping -> finishPano()
                state.mode == CamMode.PANO -> startPano()
                state.mode == CamMode.TIME_LAPSE -> startTimelapse()
                state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO ->
                    when {
                        state.eisActive -> startEisRecording()
                        // Pipeline still spinning up: park the tap; the
                        // resolver effects start the recording the moment
                        // the pipeline (or the fallback) is ready.
                        eisWanted -> pendingEisRecord = true
                        else -> startRecording()
                    }
                else -> {
                    if (state.countdown != null) state.countdown = null
                    else if (state.timerSec > 0) state.countdown = state.timerSec
                    else doCapture()
                }
            }
        },
        onShutterHoldStart = {
            if (state.mode == CamMode.PHOTO && !state.isRecording && state.countdown == null) {
                state.quickTake = true
                startRecording()
            }
        },
        onShutterHoldEnd = {
            if (state.quickTake) {
                stopRecording()
                state.quickTake = false
            }
        },
        onFlip = {
            if (state.panoSweeping) cancelPano()
            if (state.timelapseRunning) stopTimelapse()
            if (state.isRecording) {
                if (eisPipeline?.recording == true) stopEisRecording() else stopRecording()
            }
            state.facingFront = !state.facingFront
            state.desiredSessionId = null
            state.currentSessionRatio = 1f
            state.sessionBound = false
            state.zoomTarget = 1f // animator glides the displayed zoom back to 1x
            state.videoTorch = false
            state.focusPoint = null
            state.sheet = SheetKind.NONE
            state.countdown = null
        },
        onZoomTo = { target -> setZoomTarget(target) },
        onZoomDial = { target -> setZoomTargetFromDial(target) },
        onDialShow = { state.dialVisible = true },
        onFlash = { f ->
            state.flash = f
            controller.setFlashMode(f)
            state.persistAll()
        },
        onToggleTorch = {
            state.videoTorch = !state.videoTorch
            controller.setTorch(state.videoTorch)
        },
        onTimer = { sec -> state.timerSec = sec },
        onAspect = { a ->
            state.aspect = a
            state.persistAll()
        },
        onExposure = { i -> setExposureIndex(i) },
        onVideoRes = { opt ->
            state.videoRes = opt
            state.persistAll()
        },
        onFps = { f ->
            state.fps = f
            state.persistAll()
        },
        onToggleGrid = {
            state.gridOn = !state.gridOn
            state.persistAll()
        },
        onToggleNight = {
            if (state.mode != CamMode.PHOTO) {
                state.toast = "Night Mode untuk mode Photo"
            } else {
                state.nightOn = !state.nightOn
            }
        },
        onThumbnailTap = {
            val uri = state.thumbUri
            if (uri == null) {
                state.toast = "Belum ada foto/video"
            } else {
                try {
                    val intent = Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, context.contentResolver.getType(uri) ?: "image/*")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                } catch (e: Throwable) {
                    state.toast = "Tidak ada aplikasi galeri"
                }
            }
        },
        onPanoCancel = { cancelPano() }
    )

    // ------------------------------------------------------------ init
    LaunchedEffect(Unit) {
        // First run: the developer introduction shows once (Settings can
        // re-open it later without resetting this flag).
        if (!state.onboardingDone) {
            state.onboardingVisible = true
        }
        // CameraManager capability reads run off the main thread.
        state.caps = withContext(Dispatchers.Default) { computeCaps(context) }
        state.micGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        try {
            val provider = controller.initProvider()
            val backInfo = try {
                CameraSelector.DEFAULT_BACK_CAMERA
                    .filter(provider.availableCameraInfos).firstOrNull()
            } catch (e: Throwable) {
                null
            }
            val qualities = try {
                backInfo?.let { Recorder.getVideoCapabilities(it).getSupportedQualities(DynamicRange.SDR) }
            } catch (e: Throwable) {
                null
            } ?: listOf(Quality.FHD, Quality.HD)
            val opts = mutableListOf<VideoResOption>()
            if (qualities.contains(Quality.UHD)) opts += VideoResOption("4K", "UHD")
            when {
                qualities.contains(Quality.FHD) -> opts += VideoResOption("HD", "FHD")
                qualities.contains(Quality.HD) -> opts += VideoResOption("HD", "HD")
                qualities.contains(Quality.SD) -> opts += VideoResOption("SD", "SD")
            }
            if (opts.isEmpty()) opts += VideoResOption("HD", "HD")
            state.videoResOptions = opts
            state.videoRes = opts.firstOrNull { it.qualityName == state.savedQualityName }
                ?: opts.first()
            if (state.fps !in state.caps.fpsOptions) {
                state.fps = state.caps.fpsOptions.firstOrNull() ?: 30
            }
            controller.initExtensions(provider)
            controller.extensionsManager?.let { mgr ->
                state.nightExtAvailable = try {
                    mgr.isExtensionAvailable(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        androidx.camera.extensions.ExtensionMode.NIGHT
                    )
                } catch (e: Throwable) {
                    false
                }
                state.bokehExtAvailable = try {
                    mgr.isExtensionAvailable(
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        androidx.camera.extensions.ExtensionMode.BOKEH
                    )
                } catch (e: Throwable) {
                    false
                }
            }
            state.capsReady = true
            refreshThumb()
        } catch (e: Throwable) {
            state.bindError = "Kamera tidak tersedia di perangkat ini"
        }
    }

    // Update check: GitHub releases vs the installed version, async and
    // 24h-cached; the result only ever surfaces inside Settings.
    LaunchedEffect(Unit) {
        val found = withContext(Dispatchers.IO) {
            val vn = try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            } catch (e: Throwable) {
                null
            } ?: "2.0.0"
            UpdateChecker.check(context, vn)
        }
        if (found != null) {
            state.updateTag = found.tag
            state.updateUrl = found.url
        }
    }

    // ------------------------------------------------------------ bind
    val extMode = state.extensionMode
    val bindKey = listOf(
        state.facingFront, state.desiredSessionId, extMode,
        state.aspect, state.videoRes?.qualityName, state.eisActive, state.eisFailed
    ).joinToString("|")
    LaunchedEffect(bindKey, previewView, state.capsReady) {
        val pv = previewView ?: return@LaunchedEffect
        if (!state.capsReady) return@LaunchedEffect
        if (state.eisActive) return@LaunchedEffect // EIS GL pipeline owns the camera
        if (controller.recordingActive) controller.stopRecording()
        state.isRecording = false
        val ok = controller.bind(
            lifecycleOwner,
            pv.surfaceProvider,
            state.facingFront,
            state.desiredSessionId,
            extMode,
            state.aspect,
            qualityOf(state.videoRes?.qualityName ?: "FHD"),
            screenSizePx.first,
            screenSizePx.second
        )
        state.sessionBound = ok
        if (ok) {
            state.bindError = null
            controller.readZoomState()?.let { (mn, mx) ->
                state.sessionMinZoom = mn
                state.sessionMaxZoom = mx
            }
            controller.readExposureState()?.let { (lo, hi, step) ->
                state.exposureMin = lo
                state.exposureMax = hi
                state.exposureStep = step
                state.exposureIndex = state.exposureIndex.coerceIn(lo, hi)
                controller.setExposure(state.exposureIndex)
            }
            controller.setFlashMode(state.flash)
            when (state.mode) {
                CamMode.VIDEO -> controller.setTargetFps(state.fps)
                CamMode.SLO_MO -> controller.setTargetFps(
                    state.caps.sloMoFps.coerceIn(60, 240)
                )
                else -> Unit
            }
            controller.setTorch(
                (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO) && state.videoTorch
            )
            applyZoomAbsolute(state.zoomRatio)
        } else {
            state.bindError = "Tidak dapat membuka kamera"
        }
    }

    // ------------------------------------------------------------ effects
    // Software gyro EIS lifecycle: while wanted, CameraX unbinds and the GL
    // pipeline owns the camera. Any pipeline failure latches eisFailed and
    // the normal CameraX path (hardware stabilization / plain recording)
    // takes over again — never a dead viewfinder.
    LaunchedEffect(eisWanted, state.facingFront, eisSurface, eisSurfaceSize) {
        if (eisWanted) {
            val surface = eisSurface ?: return@LaunchedEffect
            val (sw, sh) = eisSurfaceSize
            if (sw <= 0 || sh <= 0) return@LaunchedEffect
            if (state.eisActive && eisFacing == state.facingFront) {
                eisPipeline?.updatePreviewSize(sw, sh)
                return@LaunchedEffect
            }
            eisPipeline?.stop()
            eisPipeline = null
            if (controller.recordingActive) controller.stopRecording()
            controller.unbindAll()
            state.sessionBound = false
            // The GL surface only replaces the CameraX preview once real
            // frames have been presented (onFirstFrame below).
            state.eisPreviewLive = false
            val pipeline = EisPipeline(context)
            eisPipeline = pipeline
            eisFacing = state.facingFront
            pipeline.listener = object : EisPipeline.Listener {
                override fun onStarted() {
                    state.eisActive = true
                    state.bindError = null
                    pipeline.setZoom(state.zoomRatio)
                    pipeline.setTorch(state.videoTorch)
                }

                override fun onFirstFrame() {
                    state.eisPreviewLive = true
                }

                override fun onFailed(reason: String) {
                    state.eisActive = false
                    state.eisPreviewLive = false
                    state.eisFailed = true
                    state.toast =
                        "Stabilisasi software tidak berjalan di perangkat ini — rekam biasa"
                }

                override fun onRecordingSaved(uri: Uri) {
                    state.isRecording = false
                    refreshThumb()
                }

                override fun onRecordingFailed() {
                    state.isRecording = false
                    state.toast = "Rekaman EIS gagal disimpan"
                }
            }
            pipeline.start(surface, sw, sh, state.facingFront)
        } else {
            if (eisPipeline != null) {
                eisPipeline?.stop()
                eisPipeline = null
            }
            state.eisActive = false
            state.eisPreviewLive = false
            // A parked shutter tap belongs to the run that just ended —
            // unless this exit IS the failure fallback, whose resolver
            // effects still need it to start the plain recording.
            if (!state.eisFailed) {
                pendingEisRecord = false
                pendingFallbackRecord = false
            }
        }
    }

    // fix8: turning ACTION or the EIS toggle off re-arms the pipeline.
    // The failure latch holds only while the user keeps ACTION on (so a
    // dead pipeline never loops start/fail), but one transient failure no
    // longer disables EIS for the rest of the session.
    LaunchedEffect(state.actionOn, state.eisEnabled) {
        if (!state.actionOn || !state.eisEnabled) state.eisFailed = false
    }

    // Parked-tap resolvers (see pendingEisRecord / pendingFallbackRecord):
    // start the recording as soon as whichever path survived is ready.
    LaunchedEffect(state.eisActive, state.eisFailed) {
        if (pendingEisRecord && state.eisActive) {
            pendingEisRecord = false
            startEisRecording()
        } else if (pendingEisRecord && state.eisFailed) {
            pendingEisRecord = false
            pendingFallbackRecord = true
        }
    }

    LaunchedEffect(state.sessionBound, state.eisFailed) {
        if (pendingFallbackRecord && state.eisFailed &&
            state.sessionBound && !state.isRecording
        ) {
            pendingFallbackRecord = false
            startRecording()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            eisPipeline?.stop()
            pano.stop()
        }
    }

    // Panorama sweep pump: poll the yaw, drive the guide (progress,
    // direction, too-fast warning) and trigger a capture every
    // PANO_STEP_DEG of pan travel; finishing is automatic at the target
    // sweep or the frame cap.
    LaunchedEffect(state.panoSweeping) {
        if (!state.panoSweeping) return@LaunchedEffect
        var lastPollNs = SystemClock.elapsedRealtimeNanos()
        while (state.panoSweeping) {
            delay(40)
            val yaw = pano.yawDeg()
            val nowNs = SystemClock.elapsedRealtimeNanos()
            val dt = ((nowNs - lastPollNs) / 1e9f).coerceIn(0.01f, 0.2f)
            lastPollNs = nowNs
            val vel = (yaw - panoRt.lastYaw) / dt
            panoRt.yawVelEma = panoRt.yawVelEma * 0.8f + vel * 0.2f
            state.panoTooFast = abs(panoRt.yawVelEma) > PANO_TOO_FAST_DEG_S
            if (!panoRt.dirLocked && abs(yaw - panoRt.startYaw) > 3f) {
                panoRt.dirLocked = true
                panoRt.dirSign = if (yaw >= panoRt.startYaw) 1f else -1f
                state.panoDirRight = panoRt.dirSign > 0f
            }
            if (yaw < panoRt.minYaw) panoRt.minYaw = yaw
            if (yaw > panoRt.maxYaw) panoRt.maxYaw = yaw
            state.panoProgress =
                ((panoRt.maxYaw - panoRt.minYaw) / PANO_TARGET_DEG).coerceIn(0f, 1f)
            if (!panoRt.captureBusy &&
                abs(yaw - panoRt.lastCaptureYaw) >= PANO_STEP_DEG
            ) {
                panoRt.lastCaptureYaw = yaw
                capturePanoFrame(yaw)
            }
            panoRt.lastYaw = yaw
            if (state.panoProgress >= 1f || pano.frameCount >= PANO_MAX_FRAMES) {
                finishPano()
            }
        }
    }

    LaunchedEffect(state.zoomRatio, state.eisActive) {
        if (state.eisActive) eisPipeline?.setZoom(state.zoomRatio)
    }

    LaunchedEffect(state.videoTorch, state.eisActive) {
        if (state.eisActive) eisPipeline?.setTorch(state.videoTorch)
    }

    LaunchedEffect(state.exposureIndex) { controller.setExposure(state.exposureIndex) }

    LaunchedEffect(state.mode, state.fps, state.caps.sloMoFps) {
        when (state.mode) {
            CamMode.VIDEO -> controller.setTargetFps(state.fps)
            CamMode.SLO_MO -> if (state.caps.sloMoFps >= 120) {
                // Record at the highest frame rate this device exposes.
                controller.setTargetFps(state.caps.sloMoFps.coerceIn(60, 240))
            }
            else -> Unit
        }
    }

    // Action mode: real video stabilization, only ever shown when the
    // device reports it as supported.
    LaunchedEffect(state.mode, state.actionOn) {
        controller.setVideoStabilization(
            state.actionOn &&
                (state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO)
        )
    }

    // Time-lapse frame pump: one still per interval into the encoder.
    LaunchedEffect(state.timelapseRunning) {
        if (!state.timelapseRunning) return@LaunchedEffect
        while (state.timelapseRunning) {
            val enc = timelapseEncoder
            if (enc != null) {
                val bmp = captureBitmapSuspend()
                if (bmp != null && state.timelapseRunning) {
                    enc.addFrame(bmp)
                    state.timelapseFrames += 1
                }
            }
            delay(state.timelapseIntervalMs)
        }
    }

    // Subtle haptic tick whenever the eased zoom crosses a quick stop.
    LaunchedEffect(state.zoomRatio) {
        val stops = state.quickStops()
        var nearest = -1
        var best = Float.MAX_VALUE
        stops.forEachIndexed { i, s ->
            val d = abs(s - state.zoomRatio)
            if (d < best) {
                best = d
                nearest = i
            }
        }
        if (nearest >= 0 && best < 0.04f && nearest != lastZoomStopIdx) {
            lastZoomStopIdx = nearest
            if (state.dialVisible) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    LaunchedEffect(state.toast) {
        val t = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, t, Toast.LENGTH_SHORT).show()
        state.toast = null
    }

    // Status banner auto-dismiss (~1.5 s, like iOS).
    LaunchedEffect(state.bannerNonce) {
        if (state.bannerText != null) {
            delay(1500)
            state.bannerText = null
        }
    }

    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            state.recordSeconds = 0
            while (true) {
                delay(1000)
                state.recordSeconds += 1
            }
        }
    }

    LaunchedEffect(state.countdown) {
        val c = state.countdown ?: return@LaunchedEffect
        if (c <= 0) {
            state.countdown = null
            doCapture()
        } else {
            delay(1000)
            if (state.countdown == c) state.countdown = c - 1
        }
    }

    LaunchedEffect(state.zoomRatio, state.dialVisible) {
        if (state.dialVisible) {
            delay(1300)
            state.dialVisible = false
        }
    }

    LaunchedEffect(state.focusNonce) {
        if (state.focusPoint != null) {
            focusScale.snapTo(1.3f)
            focusScale.animateTo(
                1f,
                spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy)
            )
            if (!state.focusLocked) {
                delay(2800)
                state.focusPoint = null
            }
        }
    }

    LaunchedEffect(state.mode) {
        if (firstModeEffect) {
            firstModeEffect = false
            return@LaunchedEffect
        }
        // iOS-like mode crossfade: a brief dim pulse over the viewfinder.
        scope.launch {
            modePulse.snapTo(0.30f)
            modePulse.animateTo(0f, tween(150))
        }
    }

    fun handleFocus(off: Offset, lock: Boolean) {
        state.focusPoint = off
        state.focusLocked = lock
        state.focusNonce += 1
        exposureAcc = 0f
        if (state.eisActive) {
            // Real AF/AE metering regions in the EIS pipeline's Camera2
            // session. Normalise by the GL surface's own pixel size (the
            // tap offsets are in layout px of the preview area, which the
            // surface fills) — the physical screen size skewed the region.
            eisPipeline?.tapToFocus(
                off.x / eisSurfaceSize.first.coerceAtLeast(1),
                off.y / eisSurfaceSize.second.coerceAtLeast(1)
            )
            return
        }
        val pv = previewView ?: return
        try {
            controller.startFocus(pv.meteringPointFactory.createPoint(off.x, off.y), lock)
        } catch (e: Throwable) { /* metering unsupported */ }
    }

    // ------------------------------------------------------------ UI
    val activeGradeMatrix = state.activeGradeMatrix()
    val screenCfg = LocalConfiguration.current
    val letterboxedMode = state.mode == CamMode.PHOTO || state.mode == CamMode.PORTRAIT ||
        state.mode == CamMode.TIME_LAPSE
    // Preview region: everything above the solid-black control strip
    // (strip content height + this device's navigation-bar inset). The
    // shutter and mode pill live on that strip, never over the preview.
    // The strip height is state-dependent (the Portrait wheel grows it):
    // using the bare constant here let the preview creep up and swallow
    // the top black band in exactly those states.
    val screenDensity = LocalDensity.current
    val navBottomDp = with(screenDensity) {
        WindowInsets.navigationBars.getBottom(screenDensity).toDp().value
    }
    val previewRegionH = (
        screenCfg.screenHeightDp.toFloat() - bottomStripHeightDp(state) - navBottomDp
        ).coerceAtLeast(1f)
    val previewArea = previewAreaDp(
        aspect = state.aspect,
        filled = state.previewFilled,
        letterboxedMode = letterboxedMode,
        screenW = screenCfg.screenWidthDp.toFloat(),
        screenH = previewRegionH
    )
    val isFullBleed = previewArea.first >= screenCfg.screenWidthDp.toFloat() - 0.5f &&
        previewArea.second >= previewRegionH - 0.5f
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Preview region (above the black control strip). COMPATIBLE
        // (TextureView) so the live FILTER/STYLES grade can be previewed
        // via a RenderEffect colour filter on API 31+; FILL_CENTER crops
        // to the preview area's aspect — never stretched. A small capture
        // aspect (4:3 / 1:1) letterboxes the preview at the region's bottom
        // edge with real black above it, merging into the strip below;
        // the ⤢ button flips it to fill the region. 16:9 fills the region.
        Box(Modifier.fillMaxWidth().height(previewRegionH.dp)) {
        Box(
            if (activeGradeMatrix != null && Build.VERSION.SDK_INT >= 31) {
                Modifier.graphicsLayer {
                    renderEffect = android.graphics.RenderEffect.createColorFilterEffect(
                        android.graphics.ColorMatrixColorFilter(
                            android.graphics.ColorMatrix(activeGradeMatrix)
                        )
                    ).asComposeRenderEffect()
                }
            } else {
                Modifier
            }.then(
                if (isFullBleed) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(previewArea.first.dp, previewArea.second.dp)
                }
            )
        ) {
            if (eisWanted || state.eisActive) {
                // ACTION gyro-EIS: the GL pipeline's own preview surface;
                // the warped (stabilized) output shows before recording.
                // fix8 handover: this surface sits UNDER the CameraX
                // preview until the pipeline has actually presented frames
                // (state.eisPreviewLive). The viewfinder is never handed
                // to a pipeline that has not produced an image — and on
                // failure the CameraX preview simply never left.
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(h: SurfaceHolder) {
                                    eisSurface = h.surface
                                    val f = h.surfaceFrame
                                    eisSurfaceSize = f.width() to f.height()
                                }

                                override fun surfaceChanged(
                                    h: SurfaceHolder,
                                    format: Int,
                                    w: Int,
                                    ht: Int
                                ) {
                                    eisSurface = h.surface
                                    eisSurfaceSize = w to ht
                                }

                                override fun surfaceDestroyed(h: SurfaceHolder) {
                                    eisSurface = null
                                    eisSurfaceSize = 0 to 0
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (!state.eisPreviewLive) {
                AndroidView(
                    factory = { ctx ->
                        PreviewView(ctx).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { pv -> if (previewView !== pv) previewView = pv }
                )
            }
        }
        }

        // Mode-change pulse: a brief dim, like the iOS viewfinder crossfade.
        if (modePulse.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = modePulse.value))
            )
        }

        // Gesture layer (below all controls).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { off ->
                            if (state.sheet != SheetKind.NONE) {
                                state.sheet = SheetKind.NONE
                            } else {
                                handleFocus(off, lock = false)
                            }
                        },
                        onLongPress = { off -> handleFocus(off, lock = true) }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (!state.isRecording && state.sheet == SheetKind.NONE) {
                            state.dialVisible = true
                            setZoomTarget(state.zoomTarget * zoomChange)
                        }
                    }
                }
                .pointerInput(previewRegionH) {
                    var total = 0f
                    var fromStrip = false
                    val stripTopPx = with(screenDensity) { previewRegionH.dp.toPx() }
                    detectHorizontalDragGestures(
                        // Swipes that START on the bottom control strip
                        // belong to the carousel pill / shutter zone; the
                        // full-screen swipe must not also fire there (the
                        // two settle paths fighting was one source of the
                        // "won't stay on PHOTO" behaviour).
                        onDragStart = { pos ->
                            total = 0f
                            fromStrip = pos.y >= stripTopPx
                        },
                        onHorizontalDrag = { _, drag -> total += drag },
                        onDragEnd = {
                            if (!fromStrip && abs(total) > 70f && state.sheet == SheetKind.NONE) {
                                swipeMode(if (total < 0) 1 else -1)
                            }
                        },
                        onDragCancel = { }
                    )
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, drag ->
                        if (state.focusPoint != null && state.exposureSupported) {
                            exposureAcc += -drag
                            val steps = (exposureAcc / 26f).toInt()
                            if (steps != 0) {
                                exposureAcc -= steps * 26f
                                setExposureIndex(state.exposureIndex + steps)
                            }
                        }
                    }
                }
        )

        // Grid overlay (preview region only — never over the black strip).
        if (state.gridOn) {
            Canvas(Modifier.fillMaxWidth().height(previewRegionH.dp)) {
                val w = size.width
                val h = size.height
                val col = Color.White.copy(alpha = 0.22f)
                drawLine(col, Offset(w / 3, 0f), Offset(w / 3, h), 1f)
                drawLine(col, Offset(2 * w / 3, 0f), Offset(2 * w / 3, h), 1f)
                drawLine(col, Offset(0f, h / 3), Offset(w, h / 3), 1f)
                drawLine(col, Offset(0f, 2 * h / 3), Offset(w, 2 * h / 3), 1f)
            }
        }

        // Focus box.
        state.focusPoint?.let { fp ->
            val density = LocalDensity.current
            val halfPx = with(density) { 46.dp.toPx() }
            Box(
                Modifier
                    .offset {
                        IntOffset((fp.x - halfPx).toInt(), (fp.y - halfPx).toInt())
                    }
                    .size(92.dp)
                    .scale(focusScale.value)
            ) {
                FocusReticle(state, Modifier.fillMaxSize())
            }
        }

        // Recording indicator.
        if (state.isRecording) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "%d:%02d".format(state.recordSeconds / 60, state.recordSeconds % 60),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Time-lapse indicator.
        if (state.timelapseRunning) {
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF3B30))
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    "TIME-LAPSE · ${state.timelapseFrames} frame",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Controls (single iOS 26 UI).
        Controls26(state, actions)

        // In-app Settings & Info (credits / license / developer).
        if (state.settingsOpen) {
            CameraSettingsScreen(state, onBack = { state.settingsOpen = false })
        }

        // First-run developer introduction (once; re-openable in Settings).
        if (state.onboardingVisible) {
            OnboardingScreen(
                onStart = {
                    state.onboardingDone = true
                    state.persistAll()
                    state.onboardingVisible = false
                }
            )
        }

        // Countdown.
        state.countdown?.let { c ->
            if (c > 0) {
                Text(
                    c.toString(),
                    color = IosYellow,
                    fontSize = 110.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }

        // Capture blink (black, like iOS).
        if (captureFlash.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = captureFlash.value))
            )
        }

        // Bind error banner.
        state.bindError?.let { msg ->
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 190.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFFB3261E).copy(alpha = 0.92f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(msg, color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

/**
 * Size (dp) of the live preview area inside the preview region (the screen
 * above the black control strip). Full region unless a small aspect
 * (4:3 / 1:1) letterboxes the preview in a photo-family mode and the user
 * has not expanded it with the ⤢ button — then the area follows the
 * aspect's long:short ratio and sits at the region's bottom edge, with the
 * black background showing above it, exactly like iOS.
 */
internal fun previewAreaDp(
    aspect: PhotoAspect,
    filled: Boolean,
    letterboxedMode: Boolean,
    screenW: Float,
    screenH: Float
): Pair<Float, Float> {
    if (!letterboxedMode || filled) return screenW to screenH
    val ratio = when (aspect) {
        PhotoAspect.RATIO_4_3 -> 4f / 3f
        PhotoAspect.SQUARE -> 1f
        PhotoAspect.RATIO_16_9 -> return screenW to screenH
    }
    var w = screenW
    var h = screenW * ratio
    if (h > screenH - TOP_BAND_MIN_DP) {
        // Guarantee the top black band in EVERY letterboxed state: when
        // the width-driven height would eat it (e.g. PORTRAIT, whose
        // aperture wheel grows the bottom strip — the reported "hitam di
        // atas hilang" bug), fit by height minus the band instead and let
        // the preview shrink, exactly like iOS.
        h = (screenH - TOP_BAND_MIN_DP).coerceAtLeast(1f)
        w = h / ratio
    }
    return w to h
}

/** Minimum top black band kept in letterboxed modes (the top pill floats in it). */
internal const val TOP_BAND_MIN_DP = 76f
