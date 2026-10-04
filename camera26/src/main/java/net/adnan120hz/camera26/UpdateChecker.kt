package net.adnan120hz.camera26

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

/**
 * Update detection against this repo's GitHub releases: finds the newest
 * `camera-v*` release tag and compares it with the installed version.
 * Checks are cached for 24h in the app's preferences and run fully
 * off-thread; any failure (offline, rate-limit, odd payload) is silent —
 * the camera must never nag or slow down because GitHub is unreachable.
 */
object UpdateChecker {

    private const val RELEASES_URL =
        "https://api.github.com/repos/adnan120hz/launcher-ios-for-android/releases?per_page=20"
    private const val CACHE_MS = 24L * 60L * 60L * 1000L

    data class Result(val tag: String, val version: String, val url: String)

    /**
     * Returns the newest camera release when it is strictly newer than
     * [currentVersion] (e.g. "1.0.0"), else null. Never throws.
     */
    fun check(context: Context, currentVersion: String): Result? {
        return try {
            val prefs = context.getSharedPreferences("camera26_prefs", Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val last = prefs.getLong("update_last_check", 0L)
            if (now - last < CACHE_MS) {
                val tag = prefs.getString("update_latest_tag", null)
                val url = prefs.getString("update_latest_url", null)
                if (tag != null && url != null) {
                    val v = tag.removePrefix("camera-v")
                    return if (isNewer(v, currentVersion)) Result(tag, v, url) else null
                }
                return null
            }
            val result = fetchLatest()
            prefs.edit()
                .putLong("update_last_check", now)
                .putString("update_latest_tag", result?.tag)
                .putString("update_latest_url", result?.url)
                .apply()
            if (result != null && isNewer(result.version, currentVersion)) {
                result
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun fetchLatest(): Result? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(RELEASES_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Camera26-UpdateCheck")
            }
            if (conn.responseCode != 200) return null
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val releases = JSONArray(body)
            var best: Result? = null
            for (i in 0 until releases.length()) {
                val rel = releases.optJSONObject(i) ?: continue
                if (rel.optBoolean("draft", false)) continue
                val tag = rel.optString("tag_name", "")
                if (!tag.startsWith("camera-v")) continue
                val version = tag.removePrefix("camera-v")
                val url = rel.optString("html_url", "")
                if (url.isEmpty()) continue
                if (best == null || isNewer(version, best.version)) {
                    best = Result(tag, version, url)
                }
            }
            best
        } catch (e: Throwable) {
            null
        } finally {
            try {
                conn?.disconnect()
            } catch (e: Throwable) { /* ignore */ }
        }
    }

    /** Numeric semver-ish comparison ("0.5.1" > "0.5.0"); junk-safe. */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.trim().split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        val n = maxOf(a.size, b.size)
        for (i in 0 until n) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
