package net.adnan120hz.launcher

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Phase 5 update checker: quietly asks GitHub for this repo's latest
 * release and compares its version against the installed build.
 *
 * Privacy/behavior contract (user requirement): at most one check per
 * day, triggered when the launcher opens; a polite banner + red dot in
 * Settings when something newer exists; a manual "Cek sekarang" button;
 * an ON/OFF toggle (default ON). No ads, no tracking, no analytics —
 * the only data fetched is the public release tag and its page URL.
 */
object UpdateChecker {

    data class ReleaseInfo(val versionName: String, val htmlUrl: String)

    private const val LATEST_API =
        "https://api.github.com/repos/adnan120hz/launcher-ios-for-android/releases/latest"

    const val RELEASES_PAGE =
        "https://github.com/adnan120hz/launcher-ios-for-android/releases"

    /** One check per day at most (manual checks bypass this). */
    const val CHECK_INTERVAL_MS: Long = 24L * 60L * 60L * 1000L

    fun currentVersionName(context: Context): String = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0).versionName ?: "0"
    } catch (e: Exception) {
        "0"
    }

    /** Blocking network call — always run on Dispatchers.IO. Returns
     *  null on any failure (offline, no releases yet, bad payload) so
     *  the caller can simply stay silent. */
    fun fetchLatest(): ReleaseInfo? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(LATEST_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "iOS-Launcher-for-Android")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                null
            } else {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val tag = json.optString("tag_name")
                    .trim()
                    .removePrefix("v")
                    .removePrefix("V")
                val url = json.optString("html_url")
                    .takeIf { it.isNotBlank() } ?: RELEASES_PAGE
                if (tag.isEmpty()) null else ReleaseInfo(tag, url)
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    /** Dotted-numeric compare ("0.10.0" > "0.9.9"); suffixes after
     *  '-' or '+' are ignored numerically (treated as equal parts). */
    fun isNewer(remote: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            v.split('.', '-', '+').map { it.toIntOrNull() ?: 0 }
        val r = parts(remote)
        val c = parts(current)
        for (i in 0 until maxOf(r.size, c.size)) {
            val rv = r.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }
}
