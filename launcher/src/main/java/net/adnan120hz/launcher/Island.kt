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
import androidx.compose.foundation.layout.Box
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

    fun startTimer(seconds: Int) {
        timerEndAt = System.currentTimeMillis() + seconds * 1000L
        timerLeftSec = seconds
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TIMER) {
            IslandState.startTimer(60)
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        IslandState.running = true

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

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 14
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        try {
            wm.addView(view, params)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        IslandState.running = false
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
    }
}

/** The pill itself. Width/height animate with a spring so state changes
 *  feel fluid (iOS-style), never stiff. */
@Composable
fun IslandPill() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            tick++
        }
    }

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
    val musicPlaying = controller?.playbackState?.state ==
        android.media.session.PlaybackState.STATE_PLAYING

    val charging = IslandState.chargingPct
    val mode = when {
        timerLeft > 0 -> IslandMode.TIMER
        musicTitle != null -> IslandMode.MUSIC
        charging >= 0 -> IslandMode.CHARGING
        else -> IslandMode.CLOCK
    }

    val targetWidth = when (mode) {
        IslandMode.CLOCK -> 118.dp
        IslandMode.CHARGING -> 176.dp
        IslandMode.TIMER -> 196.dp
        IslandMode.MUSIC -> 292.dp
    }
    val targetHeight = if (mode == IslandMode.CLOCK) 34.dp else 58.dp
    val springSpec = spring<androidx.compose.ui.unit.Dp>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val width by animateDpAsState(targetWidth, springSpec, label = "islandWidth")
    val height by animateDpAsState(targetHeight, springSpec, label = "islandHeight")

    val timeText = remember(tick) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(Color.Black)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        when (mode) {
            IslandMode.CLOCK -> {
                Text(
                    text = timeText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
            IslandMode.CHARGING -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⚡", fontSize = 15.sp, color = Color(0xFF30D158))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mengisi daya · $charging%",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
            IslandMode.TIMER -> {
                val mm = timerLeft / 60
                val ss = timerLeft % 60
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "⏱", fontSize = 15.sp, color = Color(0xFFFF9F0A))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Timer %d:%02d".format(mm, ss),
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
            }
            IslandMode.MUSIC -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (musicPlaying) "♪" else "⏸",
                        fontSize = 15.sp,
                        color = Color(0xFF30D158)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = musicTitle ?: "",
                        fontSize = 13.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
