package net.adnan120hz.camera26

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Real time-lapse pipeline: still frames captured at an interval are encoded
 * to an MP4 at 30 fps with a MediaCodec AVC encoder fed YUV byte buffers.
 * Encoder geometry is capped at 1920 on the long edge and aligned to 16 px
 * so the encoder layout stays tight (stride == width on virtually all SoCs).
 */
class TimelapseEncoder(private val outFile: File) {

    private var codec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var trackIndex = -1
    private var muxerStarted = false
    private var drainThread: Thread? = null
    @Volatile private var stopRequested = false
    private var frameCount = 0

    var width: Int = 1280
        private set
    var height: Int = 720
        private set
    private var colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    private var stride = 1280
    private var sliceHeight = 720

    fun prepare(frameWidth: Int, frameHeight: Int, fps: Int = OUTPUT_FPS): Boolean {
        return try {
            val longEdge = max(frameWidth, frameHeight)
            val scale = if (longEdge > 1920) 1920f / longEdge else 1f
            width = align16((frameWidth * scale).toInt())
            height = align16((frameHeight * scale).toInt())
            colorFormat = pickColorFormat()
                ?: run { releaseQuietly(); return false }
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            format.setInteger(MediaFormat.KEY_BIT_RATE, 10_000_000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                format.setInteger(MediaFormat.KEY_STRIDE, width)
                format.setInteger(MediaFormat.KEY_SLICE_HEIGHT, height)
            }
            val c = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inFormat = try { c.getInputFormat() } catch (_: Throwable) { null }
            stride = inFormat?.takeIf { it.containsKey(MediaFormat.KEY_STRIDE) }
                ?.getInteger(MediaFormat.KEY_STRIDE) ?: width
            sliceHeight = inFormat?.takeIf { it.containsKey(MediaFormat.KEY_SLICE_HEIGHT) }
                ?.getInteger(MediaFormat.KEY_SLICE_HEIGHT) ?: height
            codec = c
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            true
        } catch (_: Throwable) {
            releaseQuietly()
            false
        }
    }

    fun start(): Boolean {
        return try {
            val c = codec ?: return false
            c.start()
            stopRequested = false
            drainThread = Thread { drainLoop() }.apply { start() }
            true
        } catch (_: Throwable) {
            false
        }
    }

    /** Scale/crop-fill [source] into the encoder size and queue it as one frame. */
    fun addFrame(source: Bitmap) {
        val c = codec ?: return
        try {
            val idx = c.dequeueInputBuffer(20_000)
            if (idx < 0) return
            val buf = c.getInputBuffer(idx) ?: run {
                c.queueInputBuffer(idx, 0, 0, 0L, 0)
                return
            }
            buf.clear()
            val scaled = cropFill(source, width, height)
            fillYuv(buf, scaled)
            if (scaled !== source) scaled.recycle()
            val ptsUs = frameCount * 1_000_000L / OUTPUT_FPS
            val size = (stride * sliceHeight * 3 / 2).coerceAtMost(buf.capacity())
            c.queueInputBuffer(idx, 0, size, ptsUs, 0)
            frameCount++
        } catch (_: Throwable) {
            // Frame dropped; the loop keeps running.
        }
    }

    /** Signal EOS, flush, finalize the MP4. True when a playable file exists. */
    fun stop(): Boolean {
        stopRequested = true
        return try {
            val c = codec
            if (c != null) {
                val idx = c.dequeueInputBuffer(50_000)
                if (idx >= 0) {
                    c.queueInputBuffer(
                        idx, 0, 0,
                        frameCount * 1_000_000L / OUTPUT_FPS,
                        MediaCodec.BUFFER_FLAG_END_OF_STREAM
                    )
                }
            }
            drainThread?.join(4_000)
            if (muxerStarted) {
                try { muxer?.stop() } catch (_: Throwable) { /* incomplete file */ }
            }
            outFile.exists() && outFile.length() > 0L && frameCount > 0
        } catch (_: Throwable) {
            false
        } finally {
            releaseQuietly()
        }
    }

    private fun drainLoop() {
        val c = codec ?: return
        val m = muxer ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            try {
                val outIdx = c.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        if (!muxerStarted) {
                            trackIndex = m.addTrack(c.outputFormat)
                            m.start()
                            muxerStarted = true
                        }
                    }
                    outIdx >= 0 -> {
                        val data = c.getOutputBuffer(outIdx)
                        val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                        if (data != null && muxerStarted && info.size > 0 && !isConfig) {
                            data.position(info.offset)
                            data.limit(info.offset + info.size)
                            m.writeSampleData(trackIndex, data, info)
                        }
                        c.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                    }
                    outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (stopRequested && codec == null) break
                    }
                }
            } catch (_: Throwable) {
                break
            }
        }
    }

    private fun fillYuv(buf: ByteBuffer, bmp: Bitmap) {
        val w = width
        val h = height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        // Luma plane.
        for (y in 0 until h) {
            val rowOff = y * stride
            for (x in 0 until w) {
                val p = pixels[y * w + x]
                val yy = ((66 * ((p shr 16) and 0xFF) + 129 * ((p shr 8) and 0xFF) +
                    25 * (p and 0xFF) + 128) shr 8) + 16
                putByte(buf, rowOff + x, yy)
            }
        }
        val ySize = stride * sliceHeight
        if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            // NV12: interleaved U,V pairs at half resolution.
            for (cy in 0 until h / 2) {
                for (cx in 0 until w / 2) {
                    val p = pixels[(cy * 2) * w + cx * 2]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    val off = ySize + cy * stride + cx * 2
                    putByte(buf, off, u)
                    putByte(buf, off + 1, v)
                }
            }
        } else {
            // I420: separate U and V planes, chroma stride = stride / 2.
            val cStride = stride / 2
            val cSlice = sliceHeight / 2
            val uOff = ySize
            val vOff = ySize + cStride * cSlice
            for (cy in 0 until h / 2) {
                for (cx in 0 until w / 2) {
                    val p = pixels[(cy * 2) * w + cx * 2]
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                    val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128
                    putByte(buf, uOff + cy * cStride + cx, u)
                    putByte(buf, vOff + cy * cStride + cx, v)
                }
            }
        }
    }

    private fun putByte(buf: ByteBuffer, index: Int, value: Int) {
        if (index >= 0 && index < buf.capacity()) {
            buf.put(index, value.coerceIn(0, 255).toByte())
        }
    }

    private fun cropFill(src: Bitmap, tw: Int, th: Int): Bitmap {
        if (src.width == tw && src.height == th) return src
        val srcRatio = src.width.toFloat() / src.height
        val dstRatio = tw.toFloat() / th
        val cropW: Int
        val cropH: Int
        if (srcRatio > dstRatio) {
            cropH = src.height
            cropW = (src.height * dstRatio).toInt().coerceAtLeast(1)
        } else {
            cropW = src.width
            cropH = (src.width / dstRatio).toInt().coerceAtLeast(1)
        }
        val x = (src.width - cropW) / 2
        val y = (src.height - cropH) / 2
        val cropped = Bitmap.createBitmap(src, x, y, cropW, cropH)
        val scaled = Bitmap.createScaledBitmap(cropped, tw, th, true)
        if (cropped !== src && cropped !== scaled) cropped.recycle()
        return scaled
    }

    private fun pickColorFormat(): Int? {
        return try {
            val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            var planar: Int? = null
            for (ci in infos) {
                if (!ci.isEncoder) continue
                val type = ci.supportedTypes.firstOrNull {
                    it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true)
                } ?: continue
                val formats = ci.getCapabilitiesForType(type).colorFormats
                if (formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar)) {
                    return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
                }
                if (formats.contains(MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar)) {
                    planar = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar
                }
            }
            planar
        } catch (_: Throwable) {
            null
        }
    }

    private fun releaseQuietly() {
        try { codec?.stop() } catch (_: Throwable) { /* not started */ }
        try { codec?.release() } catch (_: Throwable) { /* already released */ }
        try { muxer?.release() } catch (_: Throwable) { /* already released */ }
        codec = null
        muxer = null
    }

    private fun align16(v: Int): Int = max(16, (v / 16) * 16)

    private companion object {
        const val OUTPUT_FPS = 30
    }
}
