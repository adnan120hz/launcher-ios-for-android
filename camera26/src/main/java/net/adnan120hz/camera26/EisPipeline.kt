package net.adnan120hz.camera26

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.MeteringRectangle
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.provider.MediaStore
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.abs

/**
 * Software gyro EIS recording pipeline (ACTION mode).
 *
 *   Camera2 -> SurfaceTexture (OES) -> OpenGL ES warp pass
 *     -> the same warped output is drawn to the preview surface AND,
 *        while recording, to a MediaCodec encoder surface -> MediaMuxer
 *        -> MP4 in Movies/Camera26.
 *
 * The warp rotates each frame by the gyro trajectory correction
 * (trajectory-smoothed orientation vs the frame's raw orientation,
 * gain-scaled in GyroTracker) and crops adaptively: the crop follows the
 * measured shake amplitude (6% when the phone is nearly still, up to 18%
 * in heavy shake — see EisTuning) so quality is not thrown away on calm
 * frames, and crop changes are smoothed so they never read as zoom
 * pumping. The sensor stream supersamples (up to 2560x1440) into the
 * <=1080p encoder so the crop rarely has to upscale. Rolling shutter
 * is NOT corrected (stated honestly in the UI/report). Max video size is
 * 1920x1080 for performance; the caller caps and flags that in the UI.
 *
 * Everything runs on one dedicated pipeline thread (camera callbacks, GL,
 * encoder input), with a separate muxer drain thread. Any failure anywhere
 * reports through [Listener.onFailed] so the caller can fall back to the
 * hardware-stabilization / plain CameraX path — recording never hard-crashes.
 */
class EisPipeline(
    private val context: Context,
    private val perfTier: PerfTier = PerfTier.FLAGSHIP
) {

    interface Listener {
        fun onStarted()

        /** First warped frame was actually presented to the preview. */
        fun onFirstFrame() {}
        fun onFailed(reason: String)
        fun onRecordingSaved(uri: Uri)
        fun onRecordingFailed()
    }

    var listener: Listener? = null

    @Volatile var running: Boolean = false
        private set
    @Volatile var recording: Boolean = false
        private set

    private val gyro = GyroTracker(context)
    val gyroAvailable: Boolean get() = gyro.available

    /** Which motion source feeds the warp (real gyroscope or virtual). */
    val motionSource: MotionSource get() = gyro.source

    /** Long-side cap for the sensor stream, scaled by performance tier. */
    private val streamLongCap: Int = when (perfTier) {
        PerfTier.ENTRY -> 1280
        PerfTier.MID -> 1920
        PerfTier.FLAGSHIP -> 2560
    }

    /** GL unsharp: off on entry-tier (saves a pass + encoder artefacts). */
    private val sharpAmount: Float =
        if (perfTier == PerfTier.ENTRY) 0f else EisTuning.UNSHARP_AMOUNT

    // -- pipeline thread -----------------------------------------------------
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    // -- camera ---------------------------------------------------------------
    @Volatile private var cameraDevice: CameraDevice? = null
    @Volatile private var session: CameraCaptureSession? = null
    @Volatile private var cameraSurface: Surface? = null
    private var frameSize = Size(1920, 1080)
    private var sensorOrientation = 0
    private var displayRotationDeg = 0
    private var focalNdcX = 1.9f
    private var focalNdcY = 1.9f
    private var activeArrayW = 0
    private var activeArrayH = 0
    private var frontFacing = false
    private var hasFlashUnit = false
    private var focusRects: MeteringRectangle? = null

    @Volatile private var zoomRatio = 1f
    @Volatile private var torchOn = false

    // -- GL -------------------------------------------------------------------
    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglConfig: EGLConfig? = null
    private var previewEglSurface: EGLSurface? = null
    private var encoderEglSurface: EGLSurface? = null
    private var oesTextureId = 0
    private var program = 0
    private var surfaceTexture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    @Volatile private var previewW = 0
    @Volatile private var previewH = 0
    private val renderQueued = AtomicBoolean(false)
    private val texMatrix = FloatArray(16)

    // -- stabilization state (render thread only) ------------------------------
    private var prevCorrectionQuat: FloatArray? = null
    private var prevCorrectionTsNs = 0L
    private var shakeAmpEmaDeg = EisTuning.SHAKE_LOW_DEG
    private var cropCurrent = EisTuning.CROP_INITIAL

    // -- encoder --------------------------------------------------------------
    private var videoCodec: MediaCodec? = null
    private var encoderInputSurface: Surface? = null
    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val muxerLock = Any()
    private var drainThread: Thread? = null
    private var recordTempFile: File? = null

    // -- audio ----------------------------------------------------------------
    private var audioRecord: AudioRecord? = null
    private var audioCodec: MediaCodec? = null
    private var audioThread: Thread? = null
    @Volatile private var audioRunning = false
    private var audioSamplesWritten = 0L

    // -- recording bookkeeping --------------------------------------------------
    // First encoder-frame timestamp of the current recording; video PTS is
    // normalised against it so video starts at ~0 like the audio track
    // (approximate A/V sync — both clocks start at record time).
    private var recordStartTsNs = -1L
    // Set when startRecording() itself failed, so the cleanup inside it does
    // not also fire onRecordingFailed (the caller reports the fallback).
    private var suppressRecordResult = false

    // -- start safety (fix8) ----------------------------------------------------
    /** Frames actually presented to the preview surface since start. */
    private val framesPresented = AtomicLong(0)
    private val firstFrameNotified = AtomicBoolean(false)
    /** Failure is torn down + reported at most once per start. */
    private val startFailed = AtomicBoolean(false)
    @Volatile private var stopRequested = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val quadBuffer: ByteBuffer by lazy {
        val verts = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).apply {
            asFloatBuffer().put(verts)
            position(0)
        }
    }

    // =============================================================== lifecycle

    fun start(preview: Surface, previewWidth: Int, previewHeight: Int, front: Boolean) {
        if (running) return
        previewSurface = preview
        previewW = previewWidth
        previewH = previewHeight
        frontFacing = front
        displayRotationDeg = currentDisplayRotationDeg()
        framesPresented.set(0L)
        firstFrameNotified.set(false)
        startFailed.set(false)
        stopRequested = false
        val t = HandlerThread("camera26-eis")
        t.start()
        thread = t
        handler = Handler(t.looper)
        // fix8: the blocking camera bring-up (latch waits for openCamera /
        // createCaptureSession) runs on this starter thread, never on the
        // pipeline thread that receives the Camera2 callbacks. Both the
        // first release and fix7 waited on the callback thread itself, so
        // a slow HAL turned start-up into stacked 5-second timeouts while
        // CameraX was already unbound — a black viewfinder with nothing
        // producing frames.
        val starter = object : Runnable {
            override fun run() {
                try {
                    startCameraOnThread(front)
                    if (stopRequested) {
                        handler?.post { cleanupOnThread() }
                        return
                    }
                    running = true
                    mainHandler.post { listener?.onStarted() }
                    scheduleFrameWatchdog()
                } catch (e: Throwable) {
                    failStart(e)
                }
            }
        }
        Thread(starter, "camera26-eis-start").start()
    }

    /** Start failure: tear down once, report once, never while stopping. */
    private fun failStart(e: Throwable) {
        if (!startFailed.compareAndSet(false, true)) return
        running = false
        handler?.post { cleanupOnThread() }
        if (!stopRequested) {
            mainHandler.post {
                listener?.onFailed(e.message ?: "EIS tidak dapat dimulai")
            }
        }
    }

    /**
     * Frame watchdog: [FRAME_WATCHDOG_MS] after a "successful" start the
     * pipeline must have presented at least one real frame. If it claimed
     * the camera but produced nothing, tear it down so the caller restores
     * the CameraX preview — a black viewfinder must never persist.
     */
    private fun scheduleFrameWatchdog() {
        val h = handler ?: return
        h.postDelayed({
            if (running && !stopRequested && framesPresented.get() == 0L &&
                startFailed.compareAndSet(false, true)
            ) {
                running = false
                try {
                    cleanupOnThread()
                } catch (e: Throwable) { /* best effort */ }
                mainHandler.post {
                    listener?.onFailed(
                        "Stabilisasi software tidak berjalan di perangkat ini — rekam biasa"
                    )
                }
            }
        }, FRAME_WATCHDOG_MS)
    }

    fun stop() {
        stopRequested = true
        val h = handler
        if (h == null) {
            gyro.stop()
            return
        }
        val latch = CountDownLatch(1)
        h.post {
            try {
                if (recording) {
                    try {
                        stopRecordingOnThread()
                    } catch (e: Throwable) { /* best effort */ }
                    recording = false
                }
                cleanupOnThread()
            } catch (e: Throwable) { /* best effort */ }
            running = false
            latch.countDown()
        }
        try {
            latch.await(3, TimeUnit.SECONDS)
        } catch (e: Throwable) { /* ignore */ }
        try {
            thread?.quitSafely()
        } catch (e: Throwable) { /* ignore */ }
        thread = null
        handler = null
    }

    /** Live preview surface size changed (rotation); viewport adapts per frame. */
    fun updatePreviewSize(width: Int, height: Int) {
        previewW = width
        previewH = height
    }

    fun setZoom(ratio: Float) {
        zoomRatio = ratio.coerceIn(1f, 8f)
    }

    fun setTorch(on: Boolean) {
        torchOn = on
        handler?.post { applyRepeatingRequest() }
    }

    /** Tap-to-focus with real AF/AE metering regions (normalised view coords). */
    fun tapToFocus(normX: Float, normY: Float) {
        handler?.post {
            if (activeArrayW <= 0 || activeArrayH <= 0) return@post
            val cx = (normX.coerceIn(0f, 1f) * activeArrayW).toInt()
            val cy = (normY.coerceIn(0f, 1f) * activeArrayH).toInt()
            val half = minOf(activeArrayW, activeArrayH) / 8
            val left = maxOf(0, cx - half)
            val top = maxOf(0, cy - half)
            focusRects = MeteringRectangle(
                left, top,
                minOf(activeArrayW, cx + half) - left,
                minOf(activeArrayH, cy + half) - top,
                500
            )
            applyRepeatingRequest()
        }
    }

    private fun startCameraOnThread(front: Boolean) {
        if (!gyro.available) throw IllegalStateException("Sensor gerak tidak tersedia")
        val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val targetFacing = if (front) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val cameraId = cm.cameraIdList.firstOrNull { id ->
            cm.getCameraCharacteristics(id)
                .get(CameraCharacteristics.LENS_FACING) == targetFacing
        } ?: throw IllegalStateException("Kamera tidak ditemukan")

        val chars = cm.getCameraCharacteristics(cameraId)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        hasFlashUnit = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val active = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (active != null) {
            activeArrayW = active.width()
            activeArrayH = active.height()
        }
        // Normalised focal length (NDC units) for the warp homography.
        val focals = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val phys = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focals != null && focals.isNotEmpty() && phys != null &&
            phys.height > 0f && phys.width > 0f
        ) {
            val f = focals.min()
            focalNdcY = 2f * f / phys.height
            focalNdcX = 2f * f / phys.width
        }
        val scm = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = scm?.getOutputSizes(SurfaceTexture::class.java)?.toList().orEmpty()
        frameSize = pickFrameSize(sizes)

        gyro.start()
        prevCorrectionQuat = null
        prevCorrectionTsNs = 0L
        shakeAmpEmaDeg = EisTuning.SHAKE_LOW_DEG
        cropCurrent = EisTuning.CROP_INITIAL

        // GL init first (baseline order of the first release), but executed
        // on the pipeline thread so the EGL context stays confined there;
        // this starter thread only waits for it.
        val pipelineHandler = handler
            ?: throw IllegalStateException("Pipeline thread tidak ada")
        val glReady = CountDownLatch(1)
        var glError: Throwable? = null
        pipelineHandler.post {
            try {
                initGl()
            } catch (e: Throwable) {
                glError = e
            }
            glReady.countDown()
        }
        if (!glReady.await(5, TimeUnit.SECONDS)) {
            throw IllegalStateException("GL tidak siap")
        }
        glError?.let { throw it }
        if (stopRequested) throw IllegalStateException("Start dibatalkan")

        val st = surfaceTexture ?: throw IllegalStateException("SurfaceTexture gagal")
        // ONE Surface over the SurfaceTexture for the whole session — the
        // first release did exactly this. The fix7 staged loop re-wrapped
        // the texture in a new Surface per attempt and could bind the
        // session to a superseded/released surface (frames draining into a
        // dead queue while onStarted had already fired).
        cameraSurface = Surface(st)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw IllegalStateException("Izin kamera belum diberikan")
        }
        val openLatch = CountDownLatch(1)
        var openError: Throwable? = null
        cm.openCamera(cameraId, object : CameraDevice.StateCallback() {
            override fun onOpened(device: CameraDevice) {
                cameraDevice = device
                openLatch.countDown()
            }

            override fun onDisconnected(device: CameraDevice) {
                try { device.close() } catch (e: Throwable) { /* ignore */ }
                openLatch.countDown()
            }

            override fun onError(device: CameraDevice, error: Int) {
                try { device.close() } catch (e: Throwable) { /* ignore */ }
                openError = IllegalStateException("Camera error $error")
                openLatch.countDown()
            }
        }, handler)
        if (!openLatch.await(5, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timeout membuka kamera")
        }
        openError?.let { throw it }
        val device = cameraDevice ?: throw IllegalStateException("Kamera gagal dibuka")

        if (stopRequested) throw IllegalStateException("Start dibatalkan")

        // ONE capture session on the one surface (first-release baseline).
        val sessionLatch = CountDownLatch(1)
        var sessionError: Throwable? = null
        val surface = cameraSurface ?: throw IllegalStateException("Surface kamera tidak ada")
        @Suppress("DEPRECATION")
        device.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    session = s
                    sessionLatch.countDown()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    sessionError = IllegalStateException("Sesi kamera gagal")
                    sessionLatch.countDown()
                }
            },
            handler
        )
        if (!sessionLatch.await(5, TimeUnit.SECONDS)) {
            throw IllegalStateException("Timeout sesi kamera")
        }
        sessionError?.let { throw it }
        if (stopRequested) throw IllegalStateException("Start dibatalkan")
        applyRepeatingRequest()
    }

    private fun pickFrameSize(sizes: List<Size>): Size {
        if (sizes.isEmpty()) return Size(1280, 720)
        // Supersample for quality: the GL warp downscales the sensor
        // stream onto the <=1080p encoder, so the largest stream hands
        // the stabilizer real pixels to crop into instead of upscaling a
        // cropped 1080p frame back up afterwards. The cap follows the
        // performance tier so entry-level GPUs are not drowned in pixels.
        val wide = sizes.filter {
            abs(it.width.toFloat() / it.height - 16f / 9f) < 0.06f
        }
        val pool = if (wide.isNotEmpty()) wide else sizes
        val capped = pool.filter {
            it.width <= streamLongCap && it.height <= streamLongCap
        }
        return if (capped.isNotEmpty()) {
            capped.maxByOrNull { it.width.toLong() * it.height }!!
        } else {
            pool.minByOrNull { it.width.toLong() * it.height }!!
        }
    }

    private fun applyRepeatingRequest() {
        val device = cameraDevice ?: return
        val s = session ?: return
        val surface = cameraSurface ?: return
        try {
            val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder.addTarget(surface)
            builder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            try {
                builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            } catch (e: Throwable) { /* device clamps */ }
            if (hasFlashUnit) {
                builder.set(
                    CaptureRequest.FLASH_MODE,
                    if (torchOn) CaptureRequest.FLASH_MODE_TORCH else CaptureRequest.FLASH_MODE_OFF
                )
            }
            focusRects?.let { rect ->
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(rect))
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(rect))
            }
            s.setRepeatingRequest(builder.build(), null, handler)
        } catch (e: Throwable) {
            // Losing one request must not kill the pipeline.
        }
    }

    // =============================================================== recording

    /**
     * Starts recording the warped output (max 1920x1080, enforced by the
     * caller). Returns false when the encoder cannot be created — the caller
     * then falls back so the recording is never lost.
     */
    fun startRecording(withAudio: Boolean, outWidth: Int, outHeight: Int): Boolean {
        if (!running || recording) return false
        val h = handler ?: return false
        val result = AtomicBoolean(false)
        val latch = CountDownLatch(1)
        h.post {
            try {
                startRecordingOnThread(withAudio, outWidth, outHeight)
                recording = true
                result.set(true)
            } catch (e: Throwable) {
                // The caller handles this failure (fallback recording); the
                // cleanup must not ALSO report a failed recording.
                suppressRecordResult = true
                try {
                    stopRecordingOnThread()
                } catch (t: Throwable) { /* ignore */ }
                suppressRecordResult = false
                recording = false
            }
            latch.countDown()
        }
        return try {
            latch.await(5, TimeUnit.SECONDS)
            result.get()
        } catch (e: Throwable) {
            false
        }
    }

    fun stopRecording() {
        val h = handler ?: return
        h.post {
            try {
                stopRecordingOnThread()
            } catch (e: Throwable) {
                mainHandler.post { listener?.onRecordingFailed() }
            }
            recording = false
        }
    }

    private fun startRecordingOnThread(withAudio: Boolean, outWidth: Int, outHeight: Int) {
        recordStartTsNs = -1L
        suppressRecordResult = false
        // The warped output is upright in display space, so the encoder
        // frame must match that orientation: portrait-held phones get a
        // portrait MP4 (long side still capped at 1920, short at 1080).
        val quarterTurn = ((sensorOrientation - displayRotationDeg) % 180 + 180) % 180 != 0
        val longSide = minOf(maxOf(outWidth, outHeight), 1920)
        val shortSide = minOf(minOf(outWidth, outHeight), 1080)

        // Staged encoder init: entry-level encoders sometimes reject the
        // requested size/bitrate combination — step down through smaller,
        // cheaper configurations before conceding to the fallback path.
        val dimAttempts = ArrayList<Pair<Int, Int>>()
        fun dims(ls: Int, ss: Int) {
            val w = if (quarterTurn) ss else ls
            val h = if (quarterTurn) ls else ss
            val aligned = Pair(
                (w / 16 * 16).coerceAtLeast(176),
                (h / 16 * 16).coerceAtLeast(176)
            )
            if (aligned !in dimAttempts) dimAttempts += aligned
        }
        dims(longSide, shortSide)
        dims(minOf(longSide, 1280), minOf(shortSide, 720))
        dims(minOf(longSide, 854), minOf(shortSide, 480))
        var codec: MediaCodec? = null
        var lastEncError: Throwable? = null
        for ((w, h) in dimAttempts) {
            var c: MediaCodec? = null
            try {
                val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h)
                format.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                val pixels = w.toLong() * h.toLong()
                format.setInteger(
                    MediaFormat.KEY_BIT_RATE,
                    (pixels * EisTuning.BITRATE_PER_PIXEL)
                        .coerceIn(EisTuning.BITRATE_MIN.toLong(), EisTuning.BITRATE_MAX.toLong())
                        .toInt()
                )
                format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
                c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                encoderInputSurface = c.createInputSurface()
                c.start()
                codec = c
                break
            } catch (e: Throwable) {
                lastEncError = e
                // fix8: a half-configured codec from a failed attempt must
                // be released, not leaked (native encoder instances are
                // scarce on entry-level HALs).
                try {
                    c?.stop()
                } catch (t: Throwable) { /* ignore */ }
                try {
                    c?.release()
                } catch (t: Throwable) { /* ignore */ }
                try {
                    encoderInputSurface?.release()
                } catch (t: Throwable) { /* ignore */ }
                encoderInputSurface = null
            }
        }
        videoCodec = codec
            ?: throw IllegalStateException(
                "Encoder video tidak dapat dibuat: ${lastEncError?.message}"
            )

        // Encoder EGL window surface sharing the pipeline GL context.
        val display = eglDisplay ?: throw IllegalStateException("EGL belum siap")
        val encSurface = encoderInputSurface ?: throw IllegalStateException("Surface encoder tidak ada")
        encoderEglSurface = EGL14.eglCreateWindowSurface(
            display, eglConfig, encSurface,
            intArrayOf(EGL14.EGL_NONE), 0
        )
        if (encoderEglSurface == null || encoderEglSurface == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("Surface encoder GL gagal")
        }

        recordTempFile = File(context.cacheDir, "eis_${System.currentTimeMillis()}.mp4")
        muxer = MediaMuxer(
            recordTempFile!!.absolutePath,
            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
        )
        videoTrack = -1
        audioTrack = -1
        muxerStarted = false

        if (withAudio) {
            try {
                startAudioOnThread()
            } catch (e: Throwable) {
                // Audio must never cost the video: continue video-only.
                stopAudioCapture()
            }
        }

        drainThread = Thread { drainLoop() }.apply {
            name = "camera26-eis-drain"
            start()
        }
    }

    private fun startAudioOnThread() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuf <= 0) return
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.CAMCORDER,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf * 2, 8192)
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            try { recorder.release() } catch (e: Throwable) { /* ignore */ }
            return
        }
        val aFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1)
        aFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
        aFormat.setInteger(MediaFormat.KEY_BIT_RATE, 96000)
        aFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 8192)
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(aFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        audioRecord = recorder
        audioCodec = codec
        audioSamplesWritten = 0L
        audioRunning = true
        recorder.startRecording()
        audioThread = Thread {
            val buffer = ByteArray(4096)
            while (audioRunning) {
                try {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read <= 0) continue
                    val idx = codec.dequeueInputBuffer(10000)
                    if (idx >= 0) {
                        val input = codec.getInputBuffer(idx)
                        input?.clear()
                        input?.put(buffer, 0, read)
                        val samples = read / 2
                        val ptsUs = audioSamplesWritten * 1_000_000L / sampleRate
                        audioSamplesWritten += samples
                        codec.queueInputBuffer(idx, 0, read, ptsUs, 0)
                    }
                } catch (e: Throwable) {
                    break
                }
            }
            // Signal end-of-stream to the audio encoder.
            try {
                val idx = codec.dequeueInputBuffer(1000)
                if (idx >= 0) {
                    codec.queueInputBuffer(
                        idx, 0, 0, audioSamplesWritten * 1_000_000L / sampleRate,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            } catch (e: Throwable) { /* drain copes without audio EOS */ }
        }.apply {
            name = "camera26-eis-audio"
            start()
        }
    }

    /** Stops microphone capture only; the codec is released after the drain. */
    private fun stopAudioCapture() {
        audioRunning = false
        try {
            audioThread?.join(800)
        } catch (e: Throwable) { /* ignore */ }
        audioThread = null
        try {
            audioRecord?.stop()
        } catch (e: Throwable) { /* ignore */ }
        try {
            audioRecord?.release()
        } catch (e: Throwable) { /* ignore */ }
        audioRecord = null
    }

    private fun drainLoop() {
        val info = MediaCodec.BufferInfo()
        var videoEos = false
        var audioEos = audioCodec == null
        val deadline = System.currentTimeMillis() + 1500
        while (!videoEos || !audioEos) {
            var progressed = false
            val vc = videoCodec
            if (vc != null && !videoEos) {
                try {
                    val idx = vc.dequeueOutputBuffer(info, 5000)
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        synchronized(muxerLock) {
                            videoTrack = muxer?.addTrack(vc.outputFormat) ?: -1
                            maybeStartMuxerLocked(deadline)
                        }
                        progressed = true
                    } else if (idx >= 0) {
                        handleSample(vc, idx, info, isVideo = true)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) videoEos = true
                        progressed = true
                    }
                } catch (e: Throwable) {
                    videoEos = true
                }
            } else if (vc == null) {
                videoEos = true
            }
            val ac = audioCodec
            if (ac != null && !audioEos) {
                try {
                    val idx = ac.dequeueOutputBuffer(info, 0)
                    if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        synchronized(muxerLock) {
                            audioTrack = muxer?.addTrack(ac.outputFormat) ?: -1
                            maybeStartMuxerLocked(deadline)
                        }
                        progressed = true
                    } else if (idx >= 0) {
                        handleSample(ac, idx, info, isVideo = false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) audioEos = true
                        progressed = true
                    }
                } catch (e: Throwable) {
                    audioEos = true
                }
            }
            synchronized(muxerLock) { maybeStartMuxerLocked(deadline) }
            if (!progressed) {
                try {
                    Thread.sleep(2)
                } catch (e: Throwable) {
                    break
                }
            }
        }
    }

    private fun maybeStartMuxerLocked(deadlineMs: Long) {
        if (muxerStarted) return
        val m = muxer ?: return
        val audioPending = audioCodec != null && audioTrack < 0 &&
            System.currentTimeMillis() < deadlineMs
        if (videoTrack >= 0 && !audioPending) {
            try {
                m.start()
                muxerStarted = true
            } catch (e: Throwable) { /* drain loop ends; caller reports failure */ }
        }
    }

    private fun handleSample(
        codec: MediaCodec,
        idx: Int,
        info: MediaCodec.BufferInfo,
        isVideo: Boolean
    ) {
        try {
            val buffer = codec.getOutputBuffer(idx)
            if (buffer != null && info.size > 0) {
                synchronized(muxerLock) {
                    if (muxerStarted) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        muxer?.writeSampleData(
                            if (isVideo) videoTrack else audioTrack, buffer, info
                        )
                    }
                }
            }
        } catch (e: Throwable) { /* drop a bad sample, keep recording */ }
        try {
            codec.releaseOutputBuffer(idx, false)
        } catch (e: Throwable) { /* ignore */ }
    }

    private fun stopRecordingOnThread() {
        // 1. Stop the mic; the audio thread queues encoder EOS itself.
        stopAudioCapture()
        // 2. Signal video EOS; the drain thread finishes both tracks.
        try {
            videoCodec?.signalEndOfInputStream()
        } catch (e: Throwable) { /* ignore */ }
        try {
            drainThread?.join(3000)
        } catch (e: Throwable) { /* ignore */ }
        drainThread = null
        synchronized(muxerLock) {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (e: Throwable) { /* partial file */ }
            try {
                muxer?.release()
            } catch (e: Throwable) { /* ignore */ }
            muxer = null
            muxerStarted = false
        }
        try {
            videoCodec?.stop()
        } catch (e: Throwable) { /* ignore */ }
        try {
            videoCodec?.release()
        } catch (e: Throwable) { /* ignore */ }
        videoCodec = null
        try {
            audioCodec?.stop()
        } catch (e: Throwable) { /* ignore */ }
        try {
            audioCodec?.release()
        } catch (e: Throwable) { /* ignore */ }
        audioCodec = null
        encoderEglSurface?.let { s ->
            try {
                EGL14.eglDestroySurface(eglDisplay, s)
            } catch (e: Throwable) { /* ignore */ }
        }
        encoderEglSurface = null
        try {
            encoderInputSurface?.release()
        } catch (e: Throwable) { /* ignore */ }
        encoderInputSurface = null

        val file = recordTempFile
        recordTempFile = null
        // A failed startRecording() reports through its return value; the
        // cleanup inside it must not ALSO fire these callbacks.
        val notify = !suppressRecordResult
        if (file != null && file.exists() && file.length() > 0) {
            val uri = saveVideoToMediaStore(file)
            try {
                file.delete()
            } catch (e: Throwable) { /* cache cleanup */ }
            if (!notify) return
            if (uri != null) {
                mainHandler.post { listener?.onRecordingSaved(uri) }
            } else {
                mainHandler.post { listener?.onRecordingFailed() }
            }
        } else if (notify) {
            mainHandler.post { listener?.onRecordingFailed() }
        }
    }

    private fun saveVideoToMediaStore(file: File): Uri? {
        return try {
            val resolver = context.contentResolver
            val name = "VID_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Camera26"
                )
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

    // =============================================================== GL core

    private fun initGl() {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == null || display == EGL14.EGL_NO_DISPLAY) {
            throw IllegalStateException("EGL display tidak tersedia")
        }
        eglDisplay = display
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw IllegalStateException("EGL gagal inisialisasi")
        }
        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0) ||
            numConfigs[0] <= 0
        ) {
            throw IllegalStateException("Konfigurasi EGL tidak ditemukan")
        }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            display, eglConfig, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
        )
        if (eglContext == null || eglContext == EGL14.EGL_NO_CONTEXT) {
            throw IllegalStateException("Konteks EGL gagal")
        }
        val pv = previewSurface ?: throw IllegalStateException("Surface pratinjau tidak ada")
        previewEglSurface = EGL14.eglCreateWindowSurface(
            display, eglConfig, pv, intArrayOf(EGL14.EGL_NONE), 0
        )
        if (previewEglSurface == null || previewEglSurface == EGL14.EGL_NO_SURFACE) {
            throw IllegalStateException("Surface GL pratinjau gagal")
        }
        if (!EGL14.eglMakeCurrent(display, previewEglSurface, previewEglSurface, eglContext)) {
            throw IllegalStateException("EGL makeCurrent gagal")
        }

        val texIds = IntArray(1)
        GLES20.glGenTextures(1, texIds, 0)
        oesTextureId = texIds[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        program = buildProgram()
        surfaceTexture = SurfaceTexture(oesTextureId).apply {
            setDefaultBufferSize(frameSize.width, frameSize.height)
            setOnFrameAvailableListener({ _ ->
                if (renderQueued.compareAndSet(false, true)) {
                    handler?.post {
                        renderQueued.set(false)
                        try {
                            renderFrame()
                        } catch (e: Throwable) { /* drop frame */ }
                    }
                }
            }, handler)
        }
    }

    private fun buildProgram(): Int {
        val vertexSrc = """
            attribute vec2 aPos;
            varying vec2 vNdc;
            void main() {
                vNdc = aPos;
                gl_Position = vec4(aPos, 0.0, 1.0);
            }
        """.trimIndent()
        val fragmentSrc = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vNdc;
            uniform samplerExternalOES uTex;
            uniform mat4 uTexMat;
            uniform mat3 uWarp;
            uniform vec2 uCrop;
            uniform float uMirror;
            uniform vec2 uTexel;
            uniform float uSharp;
            void main() {
                vec3 p = uWarp * vec3(vNdc, 1.0);
                vec2 inUv = (p.xy / p.z) * 0.5 + 0.5;
                inUv = (inUv - 0.5) * uCrop + 0.5;
                if (uMirror > 0.5) inUv.x = 1.0 - inUv.x;
                vec2 tc = (uTexMat * vec4(inUv, 0.0, 1.0)).xy;
                vec3 c = texture2D(uTex, tc).rgb;
                if (uSharp > 0.001) {
                    // Mild 4-tap unsharp in the same pass: restores some
                    // edge bite the warp + downscale softens.
                    vec3 blur = texture2D(uTex, tc + vec2(uTexel.x, 0.0)).rgb
                        + texture2D(uTex, tc - vec2(uTexel.x, 0.0)).rgb
                        + texture2D(uTex, tc + vec2(0.0, uTexel.y)).rgb
                        + texture2D(uTex, tc - vec2(0.0, uTexel.y)).rgb;
                    c += uSharp * (c - blur * 0.25);
                }
                gl_FragColor = vec4(c, 1.0);
            }
        """.trimIndent()
        fun compile(type: Int, src: String): Int {
            val shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
            val status = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(shader)
                GLES20.glDeleteShader(shader)
                throw IllegalStateException("Shader gagal: $log")
            }
            return shader
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val status = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) {
            throw IllegalStateException("Program GL gagal link")
        }
        return prog
    }

    /**
     * Adaptive crop driver (render thread): the measured shake amplitude
     * (EMA over per-frame raw-vs-trajectory deltas) maps to a crop target;
     * the live crop eases toward it — fast when the shake grows (edge
     * protection), slow when it calms (no zoom pumping).
     */
    private fun updateAdaptiveCrop(deltaDeg: Float) {
        shakeAmpEmaDeg += (deltaDeg - shakeAmpEmaDeg) * EisTuning.SHAKE_EMA_ALPHA
        val target = EisTuning.cropForShake(shakeAmpEmaDeg)
        val alpha = if (target > cropCurrent) {
            EisTuning.CROP_GROW_ALPHA
        } else {
            EisTuning.CROP_SHRINK_ALPHA
        }
        cropCurrent += (target - cropCurrent) * alpha
    }

    /**
     * Warp homography (output NDC -> input NDC) for a frame: rotates the
     * sampled image by the gyro trajectory correction (trajectory-smoothed
     * vs raw orientation, gain-scaled and rate-clamped), plus the
     * uprighting rotation and the adaptive stabilization crop + user zoom.
     */
    private fun computeWarp(frameTsNs: Long): FloatArray {
        var rDelta = floatArrayOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f
        )
        val correction = try {
            gyro.correctionAt(frameTsNs)
        } catch (e: Throwable) {
            null
        }
        if (correction != null) {
            updateAdaptiveCrop(correction.deltaDeg)
            // Rate-clamp the correction itself: one bad gyro sample must
            // not jerk the frame — that jerk is what reads as jelly.
            var q = correction.quat
            val prev = prevCorrectionQuat
            if (prev != null && frameTsNs > prevCorrectionTsNs) {
                val dt = (frameTsNs - prevCorrectionTsNs) / 1e9f
                val maxStep = EisTuning.CORRECTION_RATE_DEG_S * dt
                val ang = GyroTracker.angleBetweenDeg(prev, q)
                if (ang > maxStep && ang > 1e-6f) {
                    q = GyroTracker.slerp(prev, q, maxStep / ang)
                }
            }
            prevCorrectionQuat = q
            prevCorrectionTsNs = frameTsNs
            var r = GyroTracker.toMatrix3(q)
            // Re-express the device-frame rotation in the display frame.
            val psi = Math.toRadians(displayRotationDeg.toDouble()).toFloat()
            r = multiply3(multiply3(rotZ3(psi), r), rotZ3(-psi))
            rDelta = r
        }
        // Uprighting: sample the sensor frame rotated into display space.
        val base = Math.toRadians((sensorOrientation - displayRotationDeg).toDouble()).toFloat()
        val b = rotZ3(-base)
        // K matrices: out uses focal scaled by stabilization+user zoom.
        val stabZoom = zoomRatio / (1f - cropCurrent)
        val kOutInv = floatArrayOf(
            1f / (focalNdcX * stabZoom), 0f, 0f,
            0f, 1f / (focalNdcY * stabZoom), 0f,
            0f, 0f, 1f
        )
        val kIn = floatArrayOf(
            focalNdcX, 0f, 0f,
            0f, focalNdcY, 0f,
            0f, 0f, 1f
        )
        return multiply3(multiply3(multiply3(kIn, b), rDelta), kOutInv)
    }

    private fun renderFrame() {
        val st = surfaceTexture ?: return
        val display = eglDisplay ?: return
        val ctx = eglContext ?: return
        st.updateTexImage()
        st.getTransformMatrix(texMatrix)
        // Row-major math on CPU; GLSL wants column-major (ES2 has no transpose).
        val warp = transpose3(computeWarp(st.timestamp))

        val aPos = GLES20.glGetAttribLocation(program, "aPos")
        val uTex = GLES20.glGetUniformLocation(program, "uTex")
        val uTexMat = GLES20.glGetUniformLocation(program, "uTexMat")
        val uWarp = GLES20.glGetUniformLocation(program, "uWarp")
        val uCrop = GLES20.glGetUniformLocation(program, "uCrop")
        val uMirror = GLES20.glGetUniformLocation(program, "uMirror")
        val uTexel = GLES20.glGetUniformLocation(program, "uTexel")
        val uSharp = GLES20.glGetUniformLocation(program, "uSharp")

        fun drawTo(
            surface: EGLSurface?,
            width: Int,
            height: Int,
            fillCrop: Boolean,
            mirror: Boolean,
            presentationTimeNs: Long = -1L
        ): Boolean {
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return false
            if (!EGL14.eglMakeCurrent(display, surface, surface, ctx)) return false
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId)
            GLES20.glUniform1i(uTex, 0)
            GLES20.glUniformMatrix4fv(uTexMat, 1, false, texMatrix, 0)
            GLES20.glUniformMatrix3fv(uWarp, 1, false, warp, 0)
            // Fill-crop (preview): scale input UVs so the frame covers the
            // target with centre crop; encoder gets the native frame.
            val frameAspect = frameSize.width.toFloat() / frameSize.height
            val upright = ((sensorOrientation - displayRotationDeg) % 180) != 0
            val effFrameAspect = if (upright) 1f / frameAspect else frameAspect
            val targetAspect = width.toFloat() / height.coerceAtLeast(1)
            val cropX: Float
            val cropY: Float
            if (fillCrop) {
                cropX = minOf(1f, effFrameAspect / targetAspect)
                cropY = minOf(1f, targetAspect / effFrameAspect)
            } else {
                cropX = 1f
                cropY = 1f
            }
            GLES20.glUniform2f(uCrop, cropX, cropY)
            GLES20.glUniform1f(uMirror, if (mirror) 1f else 0f)
            GLES20.glUniform2f(
                uTexel,
                1f / frameSize.width.coerceAtLeast(1),
                1f / frameSize.height.coerceAtLeast(1)
            )
            GLES20.glUniform1f(uSharp, sharpAmount)
            val buf = quadBuffer.asFloatBuffer()
            buf.position(0)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(aPos)
            if (presentationTimeNs >= 0L) {
                try {
                    EGLExt.eglPresentationTimeANDROID(display, surface, presentationTimeNs)
                } catch (e: Throwable) { /* older driver */ }
            }
            return EGL14.eglSwapBuffers(display, surface)
        }

        if (drawTo(previewEglSurface, previewW, previewH, fillCrop = true, mirror = frontFacing)) {
            // A frame was really presented: feed the watchdog and let the
            // UI swap the CameraX preview out for this GL surface.
            framesPresented.incrementAndGet()
            if (firstFrameNotified.compareAndSet(false, true)) {
                mainHandler.post { listener?.onFirstFrame() }
            }
        }
        if (recording && videoCodec != null) {
            // Normalise the video clock: first recorded frame is t=0, like
            // the audio track (approximate sync, both start at record time).
            if (recordStartTsNs < 0L) recordStartTsNs = st.timestamp
            val ptsNs = (st.timestamp - recordStartTsNs).coerceAtLeast(0L)
            val fmt = try {
                videoCodec?.outputFormat
            } catch (e: Throwable) {
                null
            }
            val w = fmt?.getInteger(MediaFormat.KEY_WIDTH) ?: frameSize.width
            val hgt = fmt?.getInteger(MediaFormat.KEY_HEIGHT) ?: frameSize.height
            drawTo(
                encoderEglSurface, w, hgt,
                fillCrop = false, mirror = false,
                presentationTimeNs = ptsNs
            )
        }
    }

    private fun cleanupOnThread() {
        try {
            session?.close()
        } catch (e: Throwable) { /* ignore */ }
        session = null
        try {
            cameraDevice?.close()
        } catch (e: Throwable) { /* ignore */ }
        cameraDevice = null
        try {
            cameraSurface?.release()
        } catch (e: Throwable) { /* ignore */ }
        cameraSurface = null
        try {
            surfaceTexture?.release()
        } catch (e: Throwable) { /* ignore */ }
        surfaceTexture = null
        val display = eglDisplay
        if (display != null) {
            try {
                EGL14.eglMakeCurrent(
                    display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
                )
            } catch (e: Throwable) { /* ignore */ }
            previewEglSurface?.let {
                try {
                    EGL14.eglDestroySurface(display, it)
                } catch (e: Throwable) { /* ignore */ }
            }
            encoderEglSurface?.let {
                try {
                    EGL14.eglDestroySurface(display, it)
                } catch (e: Throwable) { /* ignore */ }
            }
            if (oesTextureId != 0) {
                try {
                    GLES20.glDeleteTextures(1, intArrayOf(oesTextureId), 0)
                } catch (e: Throwable) { /* ignore */ }
            }
            if (program != 0) {
                try {
                    GLES20.glDeleteProgram(program)
                } catch (e: Throwable) { /* ignore */ }
            }
            eglContext?.let {
                try {
                    EGL14.eglDestroyContext(display, it)
                } catch (e: Throwable) { /* ignore */ }
            }
            try {
                EGL14.eglTerminate(display)
            } catch (e: Throwable) { /* ignore */ }
        }
        previewEglSurface = null
        encoderEglSurface = null
        eglContext = null
        eglDisplay = null
        program = 0
        oesTextureId = 0
        gyro.stop()
    }

    private fun currentDisplayRotationDeg(): Int {
        return try {
            @Suppress("DEPRECATION")
            val rotation = (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
                ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
            when (rotation) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        } catch (e: Throwable) {
            0
        }
    }

    companion object {
        /** Max wait for the first presented frame before teardown. */
        private const val FRAME_WATCHDOG_MS = 1500L

        private fun transpose3(m: FloatArray): FloatArray = floatArrayOf(
            m[0], m[3], m[6],
            m[1], m[4], m[7],
            m[2], m[5], m[8]
        )

        private fun multiply3(a: FloatArray, b: FloatArray): FloatArray {
            val out = FloatArray(9)
            for (row in 0..2) {
                for (col in 0..2) {
                    out[row * 3 + col] =
                        a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col]
                }
            }
            return out
        }

        private fun rotZ3(angleRad: Float): FloatArray {
            val c = kotlin.math.cos(angleRad)
            val s = kotlin.math.sin(angleRad)
            return floatArrayOf(
                c, -s, 0f,
                s, c, 0f,
                0f, 0f, 1f
            )
        }
    }
}
