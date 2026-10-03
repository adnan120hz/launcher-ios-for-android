package net.adnan120hz.camera26

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.video.Quality
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

@Composable
fun CameraScreen() {
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
        scope.launch {
            captureFlash.snapTo(0.8f)
            captureFlash.animateTo(0f, tween(260))
        }
        controller.takePhoto(
            squareCrop = state.aspect == PhotoAspect.SQUARE,
            onSaved = { refreshThumb() },
            onError = { msg -> state.toast = msg }
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

    fun setExposureIndex(i: Int) {
        state.exposureIndex = i.coerceIn(state.exposureMin, state.exposureMax)
    }

    fun setZoom(target: Float) {
        if (state.facingFront) {
            val d = target.coerceIn(state.sessionMinZoom, state.sessionMaxZoom)
            controller.applyZoomRatio(d)
            state.zoomRatio = d
            return
        }
        val t = target.coerceIn(0.2f, 100f)
        val session = state.sessionFor(t)
        state.zoomRatio = t
        if (session.cameraId != state.desiredSessionId) {
            state.desiredSessionId = session.cameraId
            state.currentSessionRatio = session.ratio
        } else {
            val digital = (t / session.ratio)
                .coerceIn(state.sessionMinZoom.coerceAtLeast(0.05f), state.sessionMaxZoom)
            controller.applyZoomRatio(digital)
            state.zoomRatio = session.ratio * digital
        }
    }

    fun selectMode(m: CamMode) {
        if (state.isRecording || m == state.mode) return
        if (!state.modeAvailable(m)) {
            state.toast = "Mode ${m.label} — Segera (belum didukung perangkat ini)"
            return
        }
        state.mode = m
        state.sheet = SheetKind.NONE
        state.countdown = null
        if (m == CamMode.VIDEO && !state.micGranted && !state.micAsked) requestMic()
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
                state.isRecording -> stopRecording()
                state.mode == CamMode.VIDEO -> startRecording()
                state.modeAvailable(state.mode) -> {
                    if (state.countdown != null) state.countdown = null
                    else if (state.timerSec > 0) state.countdown = state.timerSec
                    else doCapture()
                }
                else -> state.toast = "Mode ${state.mode.label} — Segera"
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
            if (state.isRecording) stopRecording()
            state.facingFront = !state.facingFront
            state.desiredSessionId = null
            state.currentSessionRatio = 1f
            state.zoomRatio = 1f
            state.videoTorch = false
            state.focusPoint = null
            state.sheet = SheetKind.NONE
            state.countdown = null
        },
        onZoomTo = { target -> setZoom(target) },
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
        onSegera = { name -> state.toast = "$name — Segera hadir" },
        onToggleTray = { state.trayOpen = !state.trayOpen }
    )

    // ------------------------------------------------------------ init
    LaunchedEffect(Unit) {
        state.caps = computeCaps(context)
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
                backInfo?.let { Recorder.getVideoCapabilities(it).getSupportedQualities() }
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
            qualityOf(state.videoRes?.qualityName ?: "FHD")
        )
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
            if (state.mode == CamMode.VIDEO) controller.setTargetFps(state.fps)
            controller.setTorch(state.mode == CamMode.VIDEO && state.videoTorch)
            setZoom(state.zoomRatio)
        } else {
            state.bindError = "Tidak dapat membuka kamera"
        }
    }

    // ------------------------------------------------------------ effects
    LaunchedEffect(state.exposureIndex) { controller.setExposure(state.exposureIndex) }

    LaunchedEffect(state.mode, state.fps) {
        if (state.mode == CamMode.VIDEO) controller.setTargetFps(state.fps)
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
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
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
                        if (!state.isRecording && state.sheet == SheetKind.NONE) {
                            state.dialVisible = true
                            setZoom(state.zoomRatio * zoomChange)
                        }
                    }
                }
                .pointerInput(Unit) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onHorizontalDrag = { _, drag -> total += drag },
                        onDragEnd = {
                            if (abs(total) > 70f && state.sheet == SheetKind.NONE) {
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
