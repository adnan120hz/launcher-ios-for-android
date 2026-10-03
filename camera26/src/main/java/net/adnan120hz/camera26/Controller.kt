package net.adnan120hz.camera26

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.camera2.CaptureRequest
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Range
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
import androidx.camera.extensions.ExtensionMode
import androidx.camera.extensions.ExtensionsManager
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
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
        videoQuality: Quality
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

        if (bindOnce(p, lifecycleOwner, surfaceProvider, selector, extensionMode, aspect, videoQuality)) {
            return true
        }
        // Fallback: default camera, no extension, no physical-lens filter.
        if (extensionMode != ExtensionMode.NONE || sessionCameraId != null) {
            val plain = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            return bindOnce(p, lifecycleOwner, surfaceProvider, plain, ExtensionMode.NONE, aspect, videoQuality)
        }
        return false
    }

    private fun bindOnce(
        p: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        selector: CameraSelector,
        extensionMode: Int,
        aspect: PhotoAspect,
        videoQuality: Quality
    ): Boolean {
        return try {
            p.unbindAll()
            val preview = Preview.Builder().build()
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

            camera = if (extensionMode != ExtensionMode.NONE) {
                // CameraX Extensions support Preview + ImageCapture only.
                videoCapture = null
                p.bindToLifecycle(lifecycleOwner, selector, preview, ic)
            } else {
                val recorder = Recorder.Builder()
                    .setQualitySelector(QualitySelector.from(videoQuality))
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
        val cam = camera ?: return
        try {
            val control = Camera2CameraControl.from(cam.cameraControl)
            val options = CaptureRequestOptions.Builder()
                .setCaptureRequestOption(
                    CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                    Range(fps, fps)
                )
                .build()
            control.setCaptureRequestOptions(options)
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
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val ic = imageCapture ?: run { onError("Kamera belum siap"); return }
        ic.takePicture(mainExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                try {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                    val rotation = image.imageInfo.rotationDegrees
                    image.close()
                    val out = if (squareCrop) cropToSquare(bytes, rotation) ?: bytes else bytes
                    val uri = saveJpeg(out)
                    if (uri != null) onSaved(uri) else onError("Gagal menyimpan foto")
                } catch (e: Throwable) {
                    try { image.close() } catch (_: Throwable) { }
                    onError("Gagal memproses foto")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("Gagal mengambil foto")
            }
        })
    }

    private fun cropToSquare(jpeg: ByteArray, rotationDegrees: Int): ByteArray? {
        return try {
            var bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return null
            if (rotationDegrees != 0) {
                val m = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            }
            val side = min(bmp.width, bmp.height)
            val cropped = Bitmap.createBitmap(
                bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side
            )
            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 95, baos)
            baos.toByteArray()
        } catch (e: Throwable) {
            null
        }
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
