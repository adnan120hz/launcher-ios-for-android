package net.adnan120hz.camera26

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Real slow-motion post-processing (2.0.0 item 4).
 *
 * SLO-MO records through the normal CameraX recorder with the sensor
 * driven at the device's high-speed frame rate (see Controller's
 * CONTROL_AE_TARGET_FPS_RANGE interop). That file plays back at its
 * recorded rate — fast, not slow. To make it genuinely slow-motion the
 * video track is re-timed: every sample's timestamp is stretched by
 * measuredFps / [PLAYBACK_FPS] and written to a new MP4 (video only —
 * slomo has no audio, like iPhone slo-mo; the source's audio track is
 * dropped and the UI says so).
 *
 * Honesty built in: the fps is MEASURED from the recorded file, never
 * assumed. If the HAL delivered less than [MIN_REAL_FPS], nothing is
 * retimed and the caller keeps the original file and reports the real
 * measured rate instead of claiming slow motion.
 */
object SloMoRetime {

    const val PLAYBACK_FPS = 30
    const val MIN_REAL_FPS = 100f

    class Result(val measuredFps: Float, val retimedUri: Uri?)

    /** Measure the recorded video track's real frame rate from the file. */
    fun measureFps(context: Context, uri: Uri): Float {
        val ex = MediaExtractor()
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                ex.setDataSource(pfd.fileDescriptor)
            } ?: return 0f
            val track = videoTrack(ex) ?: return 0f
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            var count = 0L
            var lastPtsUs = 0L
            val buf = java.nio.ByteBuffer.allocate(64 * 1024)
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                count++
                if (ex.sampleTime > lastPtsUs) lastPtsUs = ex.sampleTime
                if (!ex.advance()) break
            }
            if (count < 2) return 0f
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                fmt.getLong(MediaFormat.KEY_DURATION)
            } else {
                lastPtsUs
            }
            if (durationUs <= 0L) return 0f
            return count * 1_000_000f / durationUs
        } catch (e: Throwable) {
            return 0f
        } finally {
            try {
                ex.release()
            } catch (e: Throwable) { /* ignore */ }
        }
    }

    /**
     * Retime [src] to [PLAYBACK_FPS]; returns Result with the measured fps
     * and (when applied) the new MediaStore uri. On any failure or a
     * too-slow source, retimedUri is null and the caller keeps the source.
     */
    fun retime(context: Context, src: Uri): Result {
        val measured = measureFps(context, src)
        if (measured < MIN_REAL_FPS) return Result(measured, null)
        val factor = measured / PLAYBACK_FPS
        val ex = MediaExtractor()
        var muxer: MediaMuxer? = null
        var tmp: File? = null
        try {
            val cr = context.contentResolver
            cr.openFileDescriptor(src, "r")?.use { pfd ->
                ex.setDataSource(pfd.fileDescriptor)
            } ?: return Result(measured, null)
            val track = videoTrack(ex) ?: return Result(measured, null)
            ex.selectTrack(track)
            val fmt = ex.getTrackFormat(track)
            tmp = File.createTempFile("slomo_", ".mp4", context.cacheDir)
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(fmt)
            muxer.start()
            val maxInput = if (fmt.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                fmt.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
            } else {
                1_000_000
            }
            val buf = java.nio.ByteBuffer.allocate(maxInput.coerceAtLeast(256 * 1024))
            val info = MediaCodec.BufferInfo()
            var written = 0L
            while (true) {
                val size = ex.readSampleData(buf, 0)
                if (size < 0) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (ex.sampleTime * factor).roundToLong()
                info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                    MediaCodec.BUFFER_FLAG_KEY_FRAME
                } else {
                    0
                }
                muxer.writeSampleData(outTrack, buf, info)
                written++
                if (!ex.advance()) break
            }
            if (written < 2) {
                return Result(measured, null)
            }
            muxer.stop()
            muxer = null

            // Publish the retimed file as the recording (replaces source).
            val name = "SLO_" + java.text.SimpleDateFormat(
                "yyyyMMdd_HHmmss", java.util.Locale.US
            ).format(java.util.Date()) + ".mp4"
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(
                    MediaStore.Video.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MOVIES + "/Camera26"
                )
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val newUri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return Result(measured, null)
            cr.openOutputStream(newUri)?.use { out ->
                tmp.inputStream().use { it.copyTo(out) }
            } ?: return Result(measured, null)
            val done = ContentValues().apply {
                put(MediaStore.Video.Media.IS_PENDING, 0)
            }
            cr.update(newUri, done, null, null)
            try {
                cr.delete(src, null, null)
            } catch (e: Throwable) { /* original stays; not fatal */ }
            return Result(measured, newUri)
        } catch (e: Throwable) {
            return Result(measured, null)
        } finally {
            try {
                muxer?.release()
            } catch (e: Throwable) { /* ignore */ }
            try {
                ex.release()
            } catch (e: Throwable) { /* ignore */ }
            try {
                tmp?.delete()
            } catch (e: Throwable) { /* ignore */ }
        }
    }

    private fun videoTrack(ex: MediaExtractor): Int? {
        for (i in 0 until ex.trackCount) {
            val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("video/")) return i
        }
        return null
    }

    /** Slowdown factor text for the honest toast, e.g. "4.0×". */
    fun factorText(measuredFps: Float): String {
        val f = measuredFps / PLAYBACK_FPS
        val rounded = (f * 10).roundToInt() / 10f
        return if (rounded == rounded.roundToInt().toFloat()) {
            "${rounded.roundToInt()}×"
        } else {
            "$rounded×"
        }
    }
}
