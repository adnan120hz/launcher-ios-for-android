package net.adnan120hz.camera26

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.MeteringPoint
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min
import kotlinx.coroutines.suspendCancellableCoroutine

suspend fun <T> ListenableFuture<T>.awaitValue(executor: Executor): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, executor)
    }

class CameraController(private val context: Context) {
    private val mainExecutor = ContextCompat.getMainExecutor(context)

    var provider: ProcessCameraProvider? = null
        private set
    var extensionsManager: ExtensionsManager? = null
        private set
    var camera: Camera? = null
        private set

    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null

    /** Photo post-processing (portrait segmentation, grading) off the main thread. */
    private val processingExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    // Camera2 interop options applied together so neither overwrites the other.
    private var fpsWanted: Int? = null
    private var stabWanted: Boolean? = null

    val recordingActive: Boolean get() = recording != null
    val videoReady: Boolean get() = videoCapture != null

    suspend fun initProvider(): ProcessCameraProvider {
        provider?.let { return it }
        val p = ProcessCameraProvider.getInstance(context).awaitValue(mainExecutor)
        provider = p
        return p
    }

    suspend fun initExtensions(p: ProcessCameraProvider) {
        try {
            extensionsManager =
                ExtensionsManager.getInstanceAsync(context, p).awaitValue(mainExecutor)
        } catch (e: Throwable) {
            extensionsManager = null
        }
    }

    private fun baseSelector(front: Boolean, sessionCameraId: String?): CameraSelector {
        val b = CameraSelector.Builder()
            .requireLensFacing(
                if (front) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            )
        if (sessionCameraId != null) {
            b.addCameraFilter { infos ->
                infos.filter { info ->
                    try {
                        Camera2CameraInfo.from(info).cameraId == sessionCameraId
                    } catch (e: Throwable) {
                        false
                    }
                }.toMutableList()
            }
        }
        return b.build()
    }

    /**
     * Bind Preview + ImageCapture (+ VideoCapture unless an extension mode is
     * active). Returns true on success; falls back to the default camera
     * before giving up so the viewfinder never stays dead silently.
     */
    fun bind(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        front: Boolean,
        sessionCameraId: String?,
        extensionMode: Int,
        aspect: PhotoAspect,
        videoQuality: Quality,
        screenW: Int,
        screenH: Int
    ): Boolean {
        val p = provider ?: return false

        val base = baseSelector(front, sessionCameraId)
        val selector = if (extensionMode != ExtensionMode.NONE && extensionsManager != null) {
            try {
                extensionsManager!!.getExtensionEnabledCameraSelector(base, extensionMode)
            } catch (e: Throwable) {
                base
            }
        } else base

        if (bindOnce(p, lifecycleOwner, surfaceProvider, selector, extensionMode, aspect, videoQuality, screenW, screenH)) {
            return true
        }
        // Fallback 1: default camera, no extension, no physical-lens filter.
        if (extensionMode != ExtensionMode.NONE || sessionCameraId != null) {
            val plain = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            if (bindOnce(p, lifecycleOwner, surfaceProvider, plain, ExtensionMode.NONE, aspect, videoQuality, screenW, screenH)) {
                return true
            }
        }
        // Fallback 2 (last resort): preview + photo only, so the viewfinder
        // never stays dead on an exotic device. Video reports "not ready".
        val plain = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        return bindOnce(
            p, lifecycleOwner, surfaceProvider, plain,
            ExtensionMode.NONE, aspect, videoQuality, screenW, screenH, withVideo = false
        )
    }

    private fun bindOnce(
        p: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        selector: CameraSelector,
        extensionMode: Int,
        aspect: PhotoAspect,
        videoQuality: Quality,
        screenW: Int,
        screenH: Int,
        withVideo: Boolean = true
    ): Boolean {
        return try {
            p.unbindAll()
            // Screen-matched preview (the user's core complaint): rank every
            // supported preview size by how close its aspect ratio is to the
            // DISPLAY's aspect ratio, then by closeness to the display's pixel
            // count, so the viewfinder fills this phone's screen with the
            // correct crop — never stretched, never a fixed one-size buffer.
            // Slight penalty above 2560 on the long edge keeps weak GPUs cool.
            val screenLong = maxOf(screenW, screenH).coerceAtLeast(1)
            val screenShort = minOf(screenW, screenH).coerceAtLeast(1)
            val screenAspect = screenLong.toDouble() / screenShort.toDouble()
            val screenArea = screenW.toDouble() * screenH.toDouble()
            val previewSelector = ResolutionSelector.Builder()
                .setResolutionFilter { sizes, _ ->
                    sizes.sortedBy { s ->
                        val long = maxOf(s.width, s.height).coerceAtLeast(1)
                        val short = minOf(s.width, s.height).coerceAtLeast(1)
                        val aspectScore = kotlin.math.abs(kotlin.math.ln((long.toDouble() / short.toDouble()) / screenAspect))
                        val area = s.width.toDouble() * s.height.toDouble()
                        val areaScore = kotlin.math.abs(kotlin.math.ln(area / screenArea))
                        val heatPenalty = if (long > 2560) 0.75 else 0.0
                        aspectScore * 2.0 + areaScore + heatPenalty
                    }
                }
                .build()
            val preview = Preview.Builder()
                .setResolutionSelector(previewSelector)
                .build()
            preview.setSurfaceProvider(surfaceProvider)

            val ratio = if (aspect == PhotoAspect.RATIO_16_9) AspectRatio.RATIO_16_9 else AspectRatio.RATIO_4_3
            val resSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(
                    AspectRatioStrategy(ratio, AspectRatioStrategy.FALLBACK_RULE_AUTO)
                )
                .build()
            val ic = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(resSelector)
                .build()

            camera = if (extensionMode != ExtensionMode.NONE || !withVideo) {
                // CameraX Extensions support Preview + ImageCapture only; the
                // photo-only fallback also skips VideoCapture.
                videoCapture = null
                p.bindToLifecycle(lifecycleOwner, selector, preview, ic)
            } else {
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            videoQuality,
                            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
                        )
                    )
                    .build()
                val vc = VideoCapture.withOutput(recorder)
                videoCapture = vc
                p.bindToLifecycle(lifecycleOwner, selector, preview, ic, vc)
            }
            imageCapture = ic
            true
        } catch (e: Throwable) {
            false
        }
    }

    // ------------------------------------------------------------- controls

    fun setFlashMode(f: FlashSetting) {
        imageCapture?.flashMode = when (f) {
            FlashSetting.OFF -> ImageCapture.FLASH_MODE_OFF
            FlashSetting.AUTO -> ImageCapture.FLASH_MODE_AUTO
            FlashSetting.ON -> ImageCapture.FLASH_MODE_ON
        }
    }

    fun setTorch(on: Boolean) {
        try {
            camera?.cameraControl?.enableTorch(on)
        } catch (e: Throwable) { /* no torch on this camera */ }
    }

    fun setExposure(index: Int) {
        try {
            camera?.cameraControl?.setExposureCompensationIndex(index)
        } catch (e: Throwable) { /* unsupported */ }
    }

    /** Returns (minIndex, maxIndex, step) or null when exposure comp is unsupported. */
    fun readExposureState(): Triple<Int, Int, Float>? {
        val es = camera?.cameraInfo?.exposureState ?: return null
        if (!es.isExposureCompensationSupported) return null
        return Triple(
            es.exposureCompensationRange.lower,
            es.exposureCompensationRange.upper,
            es.exposureCompensationStep.toFloat()
        )
    }

    /** Returns (minZoomRatio, maxZoomRatio) of the bound camera. */
    fun readZoomState(): Pair<Float, Float>? {
        val zs = camera?.cameraInfo?.zoomState?.value ?: return null
        return zs.minZoomRatio to zs.maxZoomRatio
    }

    fun applyZoomRatio(ratio: Float) {
        try {
            camera?.cameraControl?.setZoomRatio(ratio)
        } catch (e: Throwable) { /* unsupported */ }
    }

    fun setTargetFps(fps: Int) {
        fpsWanted = fps
        applyCamera2Options()
    }

    /** Action mode: real video stabilization via Camera2 interop. */
    fun setVideoStabilization(on: Boolean) {
        stabWanted = on
        applyCamera2Options()
    }

    private fun applyCamera2Options() {
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            val builder = CaptureRequestOptions.Builder()
            fpsWanted?.let { fps ->
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(fps, fps)
                )
            }
            stabWanted?.let { on ->
                builder.setCaptureRequestOption(
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    if (on) 1 else 0 // 1 = CONTROL_VIDEO_STABILIZATION_MODE_ON
                )
            }
            control.setCaptureRequestOptions(builder.build())
        } catch (e: Throwable) { /* device clamps silently */ }
    }

    fun startFocus(point: MeteringPoint, lock: Boolean) {
        val cam = camera ?: return
        try {
            val builder = FocusMeteringAction.Builder(
                point,
                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
            )
            if (lock) {
                builder.setAutoCancelDuration(365, TimeUnit.DAYS)
            } else {
                builder.setAutoCancelDuration(4, TimeUnit.SECONDS)
            }
            cam.cameraControl.startFocusAndMetering(builder.build())
        } catch (e: Throwable) { /* metering unsupported */ }
    }

    fun cancelFocus() {
        try {
            camera?.cameraControl?.cancelFocusAndMetering()
        } catch (e: Throwable) { /* ignore */ }
    }

    // ------------------------------------------------------------- capture

    fun takePhoto(
        squareCrop: Boolean,
        gradeMatrix: FloatArray? = null,
        portraitStrength: Float? = null,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit,
        onNotice: (String) -> Unit = {}
    ) {
        val ic = imageCapture ?: run { onError("Kamera belum siap"); return }
        ic.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val needsBitmap = squareCrop || gradeMatrix != null || portraitStrength != null
                if (!needsBitmap) {
                    // Fast path: save the captured JPEG untouched.
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                        image.close()
                        val uri = saveJpeg(bytes)
                        if (uri != null) onSaved(uri) else onError("Gagal menyimpan foto")
                    } catch (e: Throwable) {
                        try { image.close() } catch (_: Throwable) { }
                        onError("Gagal memproses foto")
                    }
                    return
                }
                val bmp = try {
                    imageProxyToBitmap(image)
                } catch (e: Throwable) {
                    null
                }
                try { image.close() } catch (_: Throwable) { }
                if (bmp == null) {
                    onError("Gagal memproses foto")
                    return
                }
                processingExecutor.execute {
                    fun finish(processed: Bitmap, notice: String? = null) {
                        try {
                            val finalBmp = if (squareCrop) cropBitmapToSquare(processed) else processed
                            val baos = ByteArrayOutputStream()
                            finalBmp.compress(Bitmap.CompressFormat.JPEG, 95, baos)
                            val uri = saveJpeg(baos.toByteArray())
                            if (uri != null) {
                                onSaved(uri)
                                if (notice != null) onNotice(notice)
                            } else {
                                onError("Gagal menyimpan foto")
                            }
                        } catch (e: Throwable) {
                            onError("Gagal menyimpan foto")
                        }
                    }
                    when {
                        portraitStrength != null ->
                            PortraitProcessor.process(bmp, portraitStrength) { result ->
                                if (result != null) {
                                    finish(result)
                                } else {
                                    // Honest fallback: keep the photo, say so.
                                    finish(bmp, "Subjek tidak terdeteksi jelas — foto disimpan tanpa blur latar")
                                }
                            }
                        gradeMatrix != null -> finish(applyGrade(bmp, gradeMatrix))
                        else -> finish(bmp)
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Gagal mengambil foto")
            }
        })
    }

    /** Capture one still as a rotated Bitmap (time-lapse frame source). */
    fun captureBitmap(onResult: (Bitmap?) -> Unit) {
        val ic = imageCapture ?: run { onResult(null); return }
        try {
            ic.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bmp = try {
                        imageProxyToBitmap(image)
                    } catch (e: Throwable) {
                        null
                    }
                    try { image.close() } catch (_: Throwable) { }
                    onResult(bmp)
                }

                override fun onError(exception: ImageCaptureException) {
                    onResult(null)
                }
            })
        } catch (e: Throwable) {
            onResult(null)
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: throw IllegalStateException("decode failed")
        val rotation = image.imageInfo.rotationDegrees
        if (rotation != 0) {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    /** Apply a real colour grade (FILTER / STYLES) to the captured photo. */
    private fun applyGrade(src: Bitmap, matrix: FloatArray): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(matrix))
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun cropBitmapToSquare(bmp: Bitmap): Bitmap {
        val side = min(bmp.width, bmp.height)
        return Bitmap.createBitmap(
            bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side
        )
    }

    private fun saveJpeg(bytes: ByteArray): Uri? {
        val resolver = context.contentResolver
        val name = "IMG_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Camera26")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    // ------------------------------------------------------------- video

    fun startRecording(withAudio: Boolean, onFinalize: (Uri?, Boolean) -> Unit) {
        val vc = videoCapture ?: run { onFinalize(null, false); return }
        val name = "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Camera26")
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(context, output)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(mainExecutor) { event ->
            if (event is VideoRecordEvent.Finalize) {
                val ok = !event.hasError()
                recording = null
                onFinalize(if (ok) event.outputResults.outputUri else null, ok)
            }
        }
    }

    fun stopRecording() {
        try {
            recording?.stop()
        } catch (e: Throwable) { /* already stopped */ }
        recording = null
    }
}
