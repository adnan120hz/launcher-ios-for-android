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
import android.provider.MediaStore
import android.util.Size
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
        // FILTER / STYLES: a real colour grade baked into the captured photo.
        val grade = if (state.mode == CamMode.PHOTO) state.activeGrade() else null
        controller.takePhoto(
            squareCrop = state.aspect == PhotoAspect.SQUARE,
            gradeMatrix = grade?.matrix,
            portraitStrength = portraitStrength,
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
            controller.startRecording(state.micGranted) { uri, ok ->
                state.isRecording = false
                state.quickTake = false
                if (ok && uri != null) refreshThumb()
                else if (!ok) state.toast = "Gagal merekam video"
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

    // --- Time-lapse: real interval capture -> MP4 ---------------------------
    suspend fun captureBitmapSuspend(): Bitmap? = suspendCancellableCoroutine { cont ->
        controller.captureBitmap { bmp -> if (cont.isActive) cont.resume(bmp) }
    }

    fun startTimelapse() {
        val (fw, fh) = when (state.aspect) {
            PhotoAspect.RATIO_16_9 -> 1920 to 1080
            PhotoAspect.RATIO_4_3 -> 1440 to 1080
            PhotoAspect.SQUARE -> 1080 to 1080
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
        state.zoomTarget = if (state.facingFront) {
            t.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
        } else {
            t.coerceIn(
                state.dialMin.coerceAtLeast(0.1f),
                state.dialMax.coerceAtMost(100f)
            )
        }
    }

    fun applyZoomAbsolute(v: Float) {
        if (state.facingFront) {
            controller.applyZoomRatio(
                v.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
            )
            return
        }
        val session = state.sessionFor(v)
        if (session.cameraId != state.desiredSessionId) {
            // Crossing into another physical lens: ask for a rebind; the bind
            // effect re-applies the current value once the new session is live.
            state.desiredSessionId = session.cameraId
            state.currentSessionRatio = session.ratio
            state.sessionBound = false
        } else if (state.sessionBound) {
            val digital = (v / session.ratio)
                .coerceIn(state.sessionMinZoom.coerceAtLeast(0.05f), state.sessionMaxZoom)
            controller.applyZoomRatio(digital)
        }
    }

    val zoomAnim = remember { Animatable(1f) }
    LaunchedEffect(state.zoomTarget) {
        zoomAnim.animateTo(
            state.zoomTarget,
            spring(dampingRatio = 0.88f, stiffness = Spring.StiffnessLow)
        ) {
            state.zoomRatio = value
            applyZoomAbsolute(value)
        }
    }

    fun selectMode(m: CamMode) {
        if (state.isRecording || state.timelapseRunning || m == state.mode) return
        // Unavailable modes are never rendered, so this is only a guard.
        if (!state.modeAvailable(m)) return
        state.mode = m
        state.sheet = SheetKind.NONE
        state.countdown = null
        if ((m == CamMode.VIDEO || m == CamMode.SLO_MO) && !state.micGranted && !state.micAsked) {
            requestMic()
        }
    }

    fun swipeMode(dir: Int) {
        val modes = CamMode.values().filter { state.modeAvailable(it) }
        val idx = modes.indexOf(state.mode)
        if (idx < 0) return
        val next = modes.getOrNull(idx + dir) ?: return
        selectMode(next)
    }

    val actions = CameraActions(
        onModeSelect = { m -> selectMode(m) },
        onOpenSheet = { kind -> state.sheet = kind },
        onCloseSheet = { state.sheet = SheetKind.NONE },
        onShutterTap = {
            when {
                state.timelapseRunning -> stopTimelapse()
                state.isRecording -> stopRecording()
                state.mode == CamMode.TIME_LAPSE -> startTimelapse()
                state.mode == CamMode.VIDEO || state.mode == CamMode.SLO_MO -> startRecording()
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
            if (state.timelapseRunning) stopTimelapse()
            if (state.isRecording) stopRecording()
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
        onUiStyle = { style ->
            state.uiStyle = style
            state.sheet = SheetKind.NONE
            state.trayOpen = false
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
        onToggleTray = { state.trayOpen = !state.trayOpen }
    )

    // ------------------------------------------------------------ init
    LaunchedEffect(Unit) {
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

    // ------------------------------------------------------------ bind
    val extMode = state.extensionMode
    val bindKey = listOf(
        state.facingFront, state.desiredSessionId, extMode,
        state.aspect, state.videoRes?.qualityName
    ).joinToString("|")
    LaunchedEffect(bindKey, previewView, state.capsReady) {
        val pv = previewView ?: return@LaunchedEffect
        if (!state.capsReady) return@LaunchedEffect
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
        state.modeStripVisible = true
        // iOS-like mode crossfade: a brief dim pulse over the viewfinder.
        scope.launch {
            modePulse.snapTo(0.30f)
            modePulse.animateTo(0f, tween(260))
        }
        delay(1500)
        state.modeStripVisible = false
    }

    fun handleFocus(off: Offset, lock: Boolean) {
        val pv = previewView ?: return
        state.focusPoint = off
        state.focusLocked = lock
        state.focusNonce += 1
        exposureAcc = 0f
        try {
            controller.startFocus(pv.meteringPointFactory.createPoint(off.x, off.y), lock)
        } catch (e: Throwable) { /* metering unsupported */ }
    }

    // ------------------------------------------------------------ UI
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    // PERFORMANCE (SurfaceView) = less viewfinder latency & heat.
                    implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { pv -> if (previewView !== pv) previewView = pv }
        )

        // Gesture layer (below all controls).
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { off ->
                            if (state.sheet != SheetKind.NONE) {
                                state.sheet = SheetKind.NONE
                            } else if (state.trayOpen) {
                                state.trayOpen = false
                            } else {
                                handleFocus(off, lock = false)
                            }
                        },
                        onLongPress = { off -> handleFocus(off, lock = true) }
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (!state.isRecording && state.sheet == SheetKind.NONE && !state.trayOpen) {
                            state.dialVisible = true
                            setZoomTarget(state.zoomTarget * zoomChange)
                        }
                    }
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, drag -> total += drag },
                        onDragEnd = {
                            // Never hijack iOS 18 tray scrolling into a mode swipe.
                            if (abs(total) > 70f && state.sheet == SheetKind.NONE && !state.trayOpen) {
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

        // Grid overlay.
        if (state.gridOn) {
            Canvas(Modifier.fillMaxSize()) {
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
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width
                    val sw = 1.6.dp.toPx()
                    drawRect(IosYellow, style = Stroke(sw))
                    val t = w * 0.09f
                    val c = w / 2
                    drawLine(IosYellow, Offset(c, 0f), Offset(c, t), sw)
                    drawLine(IosYellow, Offset(c, w - t), Offset(c, w), sw)
                    drawLine(IosYellow, Offset(0f, c), Offset(t, c), sw)
                    drawLine(IosYellow, Offset(w - t, c), Offset(w, c), sw)
                }
                SunGlyph(
                    IosYellow,
                    Modifier.size(17.dp).align(Alignment.CenterEnd).offset(x = 24.dp)
                )
                if (state.focusLocked) {
                    Text(
                        "AE/AF LOCK",
                        color = Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = (-20).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(IosYellow)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
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

        // Controls.
        if (state.uiStyle == UiStyle.IOS26) {
            Controls26(state, actions)
        } else {
            Controls18(state, actions)
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

        // Capture flash.
        if (captureFlash.value > 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = captureFlash.value))
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
