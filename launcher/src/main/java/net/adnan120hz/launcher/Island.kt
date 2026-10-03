package net.adnan120hz.launcher

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.os.BatteryManager
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Presence of this listener (once the user grants notification
 *  access) lets MediaSessionManager expose now-playing info. Phase 4:
 *  it also mirrors posted notifications into [LockNotificationStore] so
 *  the iOS-style lock screen can show a real notification stack. */
class MusicListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications
                ?.sortedByDescending { it.postTime }
                ?.forEach { LockNotificationCapture.onPosted(this, it) }
        } catch (e: Exception) {
            // best-effort initial snapshot
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        LockNotificationCapture.onPosted(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        LockNotificationCapture.onRemoved(sbn)
    }
}

enum class IslandMode { CLOCK, CHARGING, TIMER, MUSIC }

/** Shared, observable island state (single process, service-owned). */
object IslandState {
    var chargingPct by mutableStateOf(-1) // -1 = not charging
    var timerLeftSec by mutableStateOf(0)
    var timerEndAt: Long = 0L

    @Volatile
    var running: Boolean = false

    /** 0.8.0 status mirrors for the settings page: the plain `running`
     *  flag alone could not explain *why* the island is off. These are
     *  process-local honesty, refreshed by the service on every start. */
    @Volatile
    var windowAttached: Boolean = false

    /** Short machine code: OK / NO_OVERLAY / START_REFUSED / STOPPED. */
    @Volatile
    var lastError: String = "STOPPED"

    /** When > 0, the overlay pill is forced visible (test mode) until
     *  this epoch millis, regardless of the live state priority. */
    @Volatile
    var testUntilMs: Long = 0L

    fun startTimer(seconds: Int) {
        timerEndAt = System.currentTimeMillis() + seconds * 1000L
        timerLeftSec = seconds
    }

    // Battery exemption state for the island settings page (0.8.0):
    // whether the system is ignoring battery optimizations for us.
    fun isBatteryOptimizationIgnored(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE)
                as android.os.PowerManager
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Dynamic Island overlay: a black pill pinned to the top-center of the
 * screen in a TYPE_APPLICATION_OVERLAY window. Requires the overlay
 * permission (Settings.ACTION_MANAGE_OVERLAY_PERMISSION). Content states:
 * clock (idle), charging, a simple countdown timer, now playing.
 */
class IslandService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: OverlayLifecycleOwner? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TIMER -> IslandState.startTimer(60)
            ACTION_REFRESH -> applyGeometry()
            ACTION_SHOW_NOW -> {
                // Explicit user test from the settings page: keep the
                // pill forced visible for a few seconds so they can see
                // it even with no live activity, then it returns to the
                // normal state priority. Still real data (clock).
                IslandState.testUntilMs = System.currentTimeMillis() + 12_000L
            }
        }
        return START_STICKY
    }

    /** Re-apply the user-tuned geometry (0.7.0) to the live window so
     *  Settings sliders move the island while it is on screen. */
    private fun applyGeometry() {
        val view = composeView ?: return
        val wm = windowManager ?: return
        val params = layoutParams ?: return
        val store = LauncherStore(this)
        val density = resources.displayMetrics.density
        params.x = (store.islandOffsetXDp * density).toInt()
        params.y = (store.islandOffsetYDp * density).toInt()
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            // view detached; next onCreate applies the stored values
        }
    }

    override fun onCreate() {
        super.onCreate()
        IslandState.running = true

        // 0.8.0: this is a FOREGROUND service now. 0.7.0 ran it as a
        // plain background service started with startService(), so the
        // process died silently whenever the user left the launcher and
        // the island "never worked" on the real phone. Promote first,
        // attach the window after.
        startIslandForeground()

        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                val plugged = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
                val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                IslandState.chargingPct =
                    if (plugged && level >= 0) level * 100 / scale else -1
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        if (!Settings.canDrawOverlays(this)) {
            // Permission revoked since start; nothing we can draw.
            IslandState.lastError = "NO_OVERLAY"
            IslandState.windowAttached = false
            stopSelf()
            return
        }

        val lifecycleOwner = OverlayLifecycleOwner()
        lifecycleOwner.performRestore()
        owner = lifecycleOwner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent { IslandPill() }
        }
        composeView = view

        val store = LauncherStore(this)
        val density = resources.displayMetrics.density
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // The pill accepts taps (music play/pause, open the app);
            // it must NOT steal focus from the app underneath, hence
            // NOT_FOCUSABLE only (0.7.0: no more NOT_TOUCHABLE).
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = (store.islandOffsetXDp * density).toInt()
            y = (store.islandOffsetYDp * density).toInt()
        }
        layoutParams = params
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        try {
            wm.addView(view, params)
            IslandState.windowAttached = true
            IslandState.lastError = "OK"
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            IslandState.windowAttached = false
            IslandState.lastError = "START_REFUSED"
            stopSelf()
        }
    }

    /** Foreground promotion — same proven pattern as LockScreenService:
     *  low-importance channel, ongoing notification that opens the app,
     *  specialUse type on API 34+. If the platform refuses, the error
     *  code lands in IslandState so Settings can explain it honestly. */
    private fun startIslandForeground() {
        val channelId = "dynamic_island"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE)
            as android.app.NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                android.app.NotificationChannel(
                    channelId,
                    "Dynamic Island",
                    android.app.NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val contentIntent = android.app.PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Dynamic Island aktif")
            .setContentText("Pill gaya iOS menampilkan jam, daya, timer & lagu (data nyata).")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // Platform refused the FGS start; keep the process alive as
            // long as it lasts and report the refusal on the page.
            IslandState.lastError = "START_REFUSED"
        }
    }

    override fun onDestroy() {
        IslandState.running = false
        IslandState.windowAttached = false
        if (IslandState.lastError == "OK") IslandState.lastError = "STOPPED"
        IslandState.testUntilMs = 0L
        batteryReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        batteryReceiver = null
        composeView?.let { view ->
            try {
                windowManager?.removeView(view)
            } catch (e: Exception) {
                // view already gone
            }
        }
        composeView = null
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        owner?.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        owner = null
        super.onDestroy()
    }

    /** Minimal owners so a ComposeView can live inside a Service window. */
    private class OverlayLifecycleOwner :
        LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        fun performRestore() {
            savedStateController.performRestore(null)
        }

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            lifecycleRegistry.handleLifecycleEvent(event)
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val viewModelStore: ViewModelStore get() = store
        override val savedStateRegistry: SavedStateRegistry
            get() = savedStateController.savedStateRegistry
    }

    companion object {
        const val ACTION_TIMER = "net.adnan120hz.launcher.action.ISLAND_TIMER"
        const val ACTION_REFRESH = "net.adnan120hz.launcher.action.ISLAND_REFRESH"
        const val ACTION_SHOW_NOW = "net.adnan120hz.launcher.action.ISLAND_SHOW_NOW"
        private const val NOTIF_ID = 2001

        /** Whether the island is supposed to run right now: user toggle
         *  on (0.8.0 pref) AND overlay granted. The follow-gate is
         *  checked by the callers that know the unlock state; the
         *  service itself only ever starts when a caller passes this
         *  gate, and the settings page reads the same state so page
         *  and service never disagree. */
        fun shouldBeRunning(context: Context): Boolean {
            val store = LauncherStore(context)
            return store.islandEnabled && Settings.canDrawOverlays(context)
        }

        /** Start (or nudge) the island foreground service. Uses
         *  startForegroundService — the ONLY start API that survives
         *  background restrictions on modern Android. Wrapped: a refused
         *  start is recorded, never thrown at the UI. */
        fun start(context: Context, action: String? = null): Boolean {
            return try {
                val intent = Intent(context, IslandService::class.java)
                if (action != null) intent.action = action
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                IslandState.lastError = "START_REFUSED"
                false
            }
        }

        /** Restart-if-needed: called from Home when it comes to the
         *  foreground, so a killed island revives itself without the
         *  user hunting for a toggle. Only acts when [shouldBeRunning]
         *  AND the follow-gate unlock flag is set. */
        fun ensureRunning(context: Context, unlocked: Boolean) {
            if (!unlocked) return
            if (!shouldBeRunning(context)) return
            if (!IslandState.running) start(context)
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, IslandService::class.java))
            } catch (e: Exception) {
                // already stopped
            }
        }
    }
}

/** The pill itself. Width/height animate with a spring so state changes
 *  feel fluid (iOS-style), never stiff. */
@Composable
fun IslandPill() {
    val context = LocalContext.current
    val store = remember { LauncherStore(context) }
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

    // User-tuned geometry (0.7.0), re-read every tick so Settings
    // sliders reshape the island LIVE while it is on screen.
    val geoScale = store.islandScale
    val geoWidthFactor = store.islandWidthFactor
    // GLOBAL GLASS RULE: island is glass ONLY when the iOS 26 style is
    // effectively picked AND the global Liquid Glass switch is on;
    // otherwise it renders solid iOS 18. Never glass-off "iOS 26".
    val glassIsland = store.islandGlassStyleIs26()

    // Countdown is computed locally from the shared end-time each tick.
    val timerLeft = if (IslandState.timerEndAt > 0L) {
        ((IslandState.timerEndAt - System.currentTimeMillis()) / 1000L)
            .toInt().coerceAtLeast(0)
    } else {
        0
    }

    val controller = remember(tick) { activeMediaController(context) }
    val musicTitle = controller?.metadata
        ?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: controller?.metadata
            ?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
    val musicArtist = controller?.metadata
        ?.getString(MediaMetadata.METADATA_KEY_ARTIST)
    val musicPlaying = controller?.playbackState?.state ==
        android.media.session.PlaybackState.STATE_PLAYING

    val charging = IslandState.chargingPct
    // Test window (settings "Tampilkan island sekarang"): the pill stays
    // visibly pinned while it runs; the state below is still real data.
    val testActive = IslandState.testUntilMs > System.currentTimeMillis()
    val mode = when {
        timerLeft > 0 -> IslandMode.TIMER
        // MUSIC shows ONLY with a real title — no data, no state.
        !musicTitle.isNullOrBlank() -> IslandMode.MUSIC
        charging >= 0 -> IslandMode.CHARGING
        testActive -> IslandMode.CLOCK
        else -> IslandMode.CLOCK
    }

    val timeText = remember(tick) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    IslandPillContent(
        mode = mode,
        scale = geoScale,
        widthFactor = geoWidthFactor,
        glass = glassIsland,
        timeText = timeText,
        chargingPct = charging,
        timerLeftSec = timerLeft,
        musicTitle = musicTitle,
        musicArtist = musicArtist,
        musicPlaying = musicPlaying,
        onTap = {
            // Real actions only: music pill toggles play/pause; any
            // other state opens the launcher itself.
            if (mode == IslandMode.MUSIC && controller != null) {
                if (musicPlaying) controller.transportControls.pause()
                else controller.transportControls.play()
            } else {
                try {
                    context.startActivity(
                        Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (e: Exception) {
                    // launcher entry refused; nothing else to do
                }
            }
        }
    )
}

/**
 * The pill body, parameterised so the Dynamic Island settings page can
 * preview every state (compact + expanded) with the EXACT renderer the
 * overlay uses. Real overlay data flows in through [IslandPill]; the
 * preview passes its own values, so what the user tunes here is what
 * appears on screen — no mock renderer anywhere.
 */
@Composable
fun IslandPillContent(
    mode: IslandMode,
    scale: Float,
    widthFactor: Float,
    glass: Boolean,
    timeText: String,
    chargingPct: Int,
    timerLeftSec: Int,
    musicTitle: String?,
    musicArtist: String?,
    musicPlaying: Boolean,
    onTap: () -> Unit = {}
) {
    val baseWidth = when (mode) {
        IslandMode.CLOCK -> 118.dp
        IslandMode.CHARGING -> 176.dp
        IslandMode.TIMER -> 196.dp
        IslandMode.MUSIC -> 292.dp
    }
    val baseHeight = if (mode == IslandMode.CLOCK) 34.dp else 58.dp
    val targetWidth = baseWidth * (widthFactor * scale)
    val targetHeight = baseHeight * scale
    val springSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val width by animateDpAsState(targetWidth, springSpec, label = "islandWidth")
    val height by animateDpAsState(targetHeight, springSpec, label = "islandHeight")

    val fontK = scale.coerceIn(0.8f, 1.3f)

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(
                if (glass) RoundedCornerShape(50)
                else RoundedCornerShape(34.dp)
            )
            .then(
                if (glass) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF0A0A12).copy(alpha = 0.94f),
                                Color(0xFF000000).copy(alpha = 0.97f)
                            )
                        )
                    ).border(
                        1.dp,
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        RoundedCornerShape(50)
                    )
                } else {
                    Modifier.background(Color.Black)
                }
            )
            .clickable { onTap() }
            .padding(horizontal = (16 * fontK).dp),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            IslandMode.CLOCK -> {
                Text(
                    text = timeText,
                    fontSize = (14 * fontK).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            IslandMode.CHARGING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "↯",
                        fontSize = (15 * fontK).sp,
                        color = Color(0xFF30D158)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mengisi daya · $chargingPct%",
                        fontSize = (13 * fontK).sp,
                        color = Color.White
                    )
                }
            }
            IslandMode.TIMER -> {
                val mm = timerLeftSec / 60
                val ss = timerLeftSec % 60
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Timer %d:%02d".format(mm, ss),
                        fontSize = (13 * fontK).sp,
                        color = Color.White
                    )
                }
            }
            IslandMode.MUSIC -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (musicPlaying) "♪" else "‖",
                        fontSize = (15 * fontK).sp,
                        color = Color(0xFF30D158)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = musicTitle ?: "",
                            fontSize = (13 * fontK).sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!musicArtist.isNullOrBlank()) {
                            Text(
                                text = musicArtist,
                                fontSize = (11 * fontK).sp,
                                color = Color.White.copy(alpha = 0.75f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
