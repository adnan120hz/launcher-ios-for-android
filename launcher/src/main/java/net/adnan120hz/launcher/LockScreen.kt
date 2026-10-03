package net.adnan120hz.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * iOS-style lock screen — Phase 4.
 *
 * PLATFORM HONESTY (also stated in Settings + README): a third-party app
 * CANNOT replace Android's system lock screen / keyguard. What this
 * builds is an iOS-styled DISPLAY LAYER that appears right AFTER the
 * system lock (PIN/pattern/fingerprint) has been passed — exactly the
 * approach every "iOS lock screen" app on Android uses. The biometric
 * prompt here only dismisses this visual layer; it never replaces or
 * weakens the device's real security lock.
 */

/** User-tunable lock-screen appearance. Persisted by [LauncherStore]. */
data class LockPrefs(
    /** Clock size multiplier (0.7 .. 1.4). */
    val clockScale: Float = 1.0f,
    /** Clock text color (ARGB). */
    val clockColorArgb: Int = 0xFFFFFFFF.toInt(),
    /** Clock font weight (100..900). */
    val clockWeight: Int = 700,
    /** "Extended" clock: wider, slightly larger glyphs (iOS 26 flavour). */
    val clockExtended: Boolean = false,
    /** Blur radius applied to the wallpaper copy behind the clock, dp. */
    val wallpaperBlurDp: Float = 18f,
    /** Liquid Glass strength on lock elements (0..1). */
    val glassIntensity: Float = 0.6f,
    /** Ask for fingerprint/face (BiometricPrompt) on swipe-up when the
     *  device has one enrolled; swipe-only when it has not. */
    val useBiometric: Boolean = true
)

/** Observable cross-component state for the lock feature (single process). */
object LockScreenRuntime {
    /** LockScreenService is alive. */
    var serviceRunning by mutableStateOf(false)
    /** The overlay window is currently on screen. */
    var overlayShowing by mutableStateOf(false)
    /** SCREEN_OFF was seen and not yet consumed by an unlock surface. */
    var screenOffPending by mutableStateOf(false)
    /** Ask the in-app (Home) lock layer to show — used when the overlay
     *  window cannot be drawn (permission missing). */
    var showInAppRequest by mutableStateOf(false)
    /** Bumped when a biometric attempt fails/cancels, so the lock UI can
     *  offer the explicit swipe-only fallback button. */
    var biometricErrorTick by mutableIntStateOf(0)
    /** A biometric prompt launched from the overlay is on screen. */
    var biometricAwaiting by mutableStateOf(false)
}

data class LockNotification(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAt: Long
)

/** Notifications captured by [LockNotificationCapture] (the existing
 *  MusicListenerService) for the lock-screen stack. In-memory only. */
object LockNotificationStore {
    private const val MAX_ITEMS = 6

    var items by mutableStateOf<List<LockNotification>>(emptyList())
        private set

    fun upsert(n: LockNotification) {
        items = (listOf(n) + items.filterNot { it.key == n.key })
            .sortedByDescending { it.postedAt }
            .take(MAX_ITEMS)
    }

    fun remove(key: String) {
        items = items.filterNot { it.key != key }
    }
}

/** Receives the notifications the system delivers to our already-declared
 *  MusicListenerService and mirrors them into [LockNotificationStore].
 *  Called from the listener's overrides; guarded — never crashes it. */
object LockNotificationCapture {

    fun onPosted(context: Context, sbn: StatusBarNotification?) {
        val n = sbn ?: return
        if (n.packageName == context.packageName) return
        try {
            val extras = n.notification.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)
                ?.toString().orEmpty()
            val text = extras.getCharSequence(Notification.EXTRA_TEXT)
                ?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                    ?.toString().orEmpty()
            val label = try {
                context.packageManager.getApplicationLabel(
                    context.packageManager.getApplicationInfo(n.packageName, 0)
                ).toString()
            } catch (e: Exception) {
                n.packageName
            }
            if (title.isBlank() && text.isBlank()) return
            LockNotificationStore.upsert(
                LockNotification(
                    key = n.key,
                    packageName = n.packageName,
                    appLabel = label,
                    title = title,
                    text = text,
                    postedAt = n.postTime
                )
            )
        } catch (e: Exception) {
            // Notification capture is best-effort for the lock stack.
        }
    }

    fun onRemoved(sbn: StatusBarNotification?) {
        sbn?.let { LockNotificationStore.remove(it.key) }
    }
}

/** True when this device can actually show a BiometricPrompt for
 *  fingerprint/face (BIOMETRIC_WEAK enrolled & available). */
fun biometricAvailable(context: Context): Boolean = try {
    BiometricManager.from(context).canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_WEAK
    ) == BiometricManager.BIOMETRIC_SUCCESS
} catch (e: Exception) {
    false
}

/**
 * Hosts the biometric prompt for the lock layer. Transparent, UI-less:
 * the system prompt draws itself. Reports the result back either as an
 * activity result (in-app lock) or via the service (overlay lock).
 */
class LockUnlockActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!biometricAvailable(this)) {
            // Nothing enrolled — caller falls back to swipe-only unlock.
            deliver(ok = false)
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    deliver(ok = true)
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    deliver(ok = false)
                }

                override fun onAuthenticationFailed() {
                    // Single bad read — the prompt stays up and retries.
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka kunci")
            .setSubtitle("Sidik jari atau wajah buat membuka layar kunci")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            .setNegativeButtonText("Batal")
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            deliver(ok = false)
        }
    }

    private var delivered = false

    private fun deliver(ok: Boolean) {
        if (delivered) return
        delivered = true
        if (intent.getBooleanExtra(EXTRA_FROM_OVERLAY, false)) {
            try {
                startService(
                    Intent(this, LockScreenService::class.java)
                        .setAction(LockScreenService.ACTION_BIOMETRIC_RESULT)
                        .putExtra(LockScreenService.EXTRA_BIOMETRIC_OK, ok)
                )
            } catch (e: Exception) {
                // Service gone — nothing to report to.
            }
        }
        setResult(if (ok) Activity.RESULT_OK else Activity.RESULT_CANCELED)
        finish()
    }

    companion object {
        const val EXTRA_FROM_OVERLAY =
            "net.adnan120hz.launcher.extra.BIOMETRIC_FROM_OVERLAY"
    }
}

/**
 * Foreground service that (a) watches screen-off / unlock broadcasts and
 * (b) draws the full-screen lock overlay in a TYPE_APPLICATION_OVERLAY
 * window right after the system lock is passed.
 */
class LockScreenService : Service() {

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null
    private var owner: LockOverlayLifecycleOwner? = null
    private var screenReceiver: BroadcastReceiver? = null
    private var armed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        LockScreenRuntime.serviceRunning = true
        startLockForeground()

        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        armed = true
                        LockScreenRuntime.screenOffPending = true
                    }
                    Intent.ACTION_USER_PRESENT -> {
                        if (armed) {
                            armed = false
                            LockScreenRuntime.screenOffPending = false
                            showOverlay()
                        }
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        // No secure keyguard (None/Swipe lock): nothing
                        // else will fire, so show right away.
                        if (armed && !isKeyguardLocked()) {
                            armed = false
                            LockScreenRuntime.screenOffPending = false
                            showOverlay()
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                LockScreenRuntime.screenOffPending = false
                showOverlay()
            }
            ACTION_HIDE -> hideOverlay()
            ACTION_BIOMETRIC_RESULT -> {
                LockScreenRuntime.biometricAwaiting = false
                if (intent.getBooleanExtra(EXTRA_BIOMETRIC_OK, false)) {
                    hideOverlay()
                } else {
                    LockScreenRuntime.biometricErrorTick++
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        LockScreenRuntime.serviceRunning = false
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                // already unregistered
            }
        }
        screenReceiver = null
        hideOverlay()
        super.onDestroy()
    }

    private fun isKeyguardLocked(): Boolean = try {
        (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
            .isKeyguardLocked
    } catch (e: Exception) {
        false
    }

    private fun startLockForeground() {
        val channelId = "lock_screen"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(
                    channelId,
                    "Layar Kunci",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("Layar kunci iOS aktif")
            .setContentText(
                "Lapisan tampilan gaya iOS di atas kunci sistem Android."
            )
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (e: Exception) {
            // If the platform refuses the FGS start we still keep the
            // receiver alive while the process lives.
        }
    }

    private fun showOverlay() {
        if (composeView != null) return
        LockScreenRuntime.biometricErrorTick = 0
        if (!Settings.canDrawOverlays(this)) {
            // Cannot draw a window — ask the in-app layer in Home to
            // show the lock instead (appears when the user reaches Home).
            LockScreenRuntime.showInAppRequest = true
            return
        }

        val lifecycleOwner = LockOverlayLifecycleOwner()
        lifecycleOwner.performRestore()
        owner = lifecycleOwner

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                val store = LauncherStore(this@LockScreenService)
                LockScreenContent(
                    cfg = store.lockPrefs(),
                    glassStyleIs26 =
                        store.effectiveCcStyle() == CcStyle.IOS26,
                    maxBlurDp = blurCapDp(store.perfTier),
                    notifications = LockNotificationStore.items,
                    notifAccessGranted =
                        notificationListenerGranted(this@LockScreenService),
                    showFallback =
                        LockScreenRuntime.biometricErrorTick > 0,
                    onRequestUnlock = { requestUnlock() },
                    onDismissFallback = { hideOverlay() },
                    onCameraClick = { openCamera() }
                )
            }
        }
        composeView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams
                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        windowManager = wm
        try {
            wm.addView(view, params)
            LockScreenRuntime.overlayShowing = true
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleOwner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            composeView = null
            LockScreenRuntime.overlayShowing = false
            LockScreenRuntime.showInAppRequest = true
        }
    }

    private fun hideOverlay() {
        LockScreenRuntime.overlayShowing = false
        LockScreenRuntime.biometricAwaiting = false
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
        // A one-off "Kunci sekarang" while the feature is switched off
        // should not leave the service (and its notification) lingering.
        if (!LauncherStore(this).lockEnabled) {
            stopSelf()
        }
    }

    /** Swipe-up on the overlay: biometric when usable, else straight
     *  dismissal (the documented swipe-only fallback). */
    private fun requestUnlock() {
        val cfg = LauncherStore(this).lockPrefs()
        if (cfg.useBiometric && biometricAvailable(this)) {
            LockScreenRuntime.biometricAwaiting = true
            try {
                startActivity(
                    Intent(this, LockUnlockActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(LockUnlockActivity.EXTRA_FROM_OVERLAY, true)
                )
            } catch (e: Exception) {
                // Some OEM builds throttle activity starts from an
                // overlay window — never trap the user behind the layer.
                LockScreenRuntime.biometricAwaiting = false
                hideOverlay()
            }
        } else {
            hideOverlay()
        }
    }

    private fun openCamera() {
        hideOverlay()
        try {
            val pkg = "net.adnan120hz.camera26"
            val launch = if (isPackageInstalled(this, pkg)) {
                packageManager.getLaunchIntentForPackage(pkg)
            } else {
                null
            }
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launch)
            } else {
                startActivity(
                    Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            // No camera app available — the layer is already dismissed.
        }
    }

    /** Minimal owners so a ComposeView can live inside a Service window
     *  (same pattern as the Dynamic Island overlay). */
    private class LockOverlayLifecycleOwner :
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
        const val ACTION_SHOW = "net.adnan120hz.launcher.action.LOCK_SHOW"
        const val ACTION_HIDE = "net.adnan120hz.launcher.action.LOCK_HIDE"
        const val ACTION_BIOMETRIC_RESULT =
            "net.adnan120hz.launcher.action.LOCK_BIOMETRIC_RESULT"
        const val EXTRA_BIOMETRIC_OK = "biometric_ok"
        private const val NOTIF_ID = 4204
    }
}

/**
 * The lock screen itself — shared by the overlay window and the in-app
 * fallback layer in Home. Pure UI + callbacks; all platform decisions
 * (biometric, window, intents) live with the callers.
 */
@Composable
fun LockScreenContent(
    cfg: LockPrefs,
    glassStyleIs26: Boolean,
    maxBlurDp: Float,
    notifications: List<LockNotification>,
    notifAccessGranted: Boolean,
    showFallback: Boolean,
    onRequestUnlock: () -> Unit,
    onDismissFallback: () -> Unit,
    onCameraClick: () -> Unit
) {
    val context = LocalContext.current

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = Date()
        }
    }
    val dateText = remember(now) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    }
    val timeText = remember(now) {
        SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
    }

    // Torch (flashlight) — real toggle, kept in sync with the system
    // torch state exactly like the Control Center tile does.
    var torchOn by remember { mutableStateOf(false) }
    val cm = remember {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    DisposableEffect(cm) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == torchCameraId(context)) torchOn = enabled
            }
        }
        cm.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { cm.unregisterTorchCallback(callback) }
    }

    val wallpaper by produceState<Bitmap?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadLockWallpaper(context) }
    }

    // Swipe-up-to-unlock: drag the whole scene slightly, spring back on
    // release; past the threshold = unlock request.
    val density = LocalDensity.current
    val thresholdPx = with(density) { 64.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var dragTotal by remember { mutableStateOf(0f) }
    val sceneOffset by animateFloatAsState(
        targetValue = if (dragging) dragTotal.coerceIn(-110f, 0f) else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "lockSceneOffset"
    )

    val clockColor = Color(cfg.clockColorArgb)
    val effectiveScale =
        cfg.clockScale * if (cfg.clockExtended) 1.12f else 1f

    Box(modifier = Modifier.fillMaxSize()) {
        // --- Wallpaper copy (blurred per the user's slider) -----------
        val blurRadius = cfg.wallpaperBlurDp.coerceIn(0f, maxBlurDp)
        Box(Modifier.matchParentSize()) {
            if (wallpaper != null) {
                Image(
                    bitmap = wallpaper!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .let {
                            if (blurRadius > 0f && Build.VERSION.SDK_INT >= 31) {
                                it.blur(blurRadius.dp)
                            } else {
                                it
                            }
                        }
                )
            } else {
                WallpaperBackground(Modifier.matchParentSize())
            }
            if (blurRadius > 0f && Build.VERSION.SDK_INT < 31) {
                // Pre-31 has no RenderEffect blur — a dark veil stands in.
                Box(
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                )
            }
            // Legibility scrim, iOS-style.
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.30f)
                            )
                        )
                    )
            )
        }

        // --- Scene: clock, notifications, shortcuts -------------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = sceneOffset }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragging = true
                            dragTotal = 0f
                        },
                        onDragEnd = {
                            dragging = false
                            if (dragTotal < -thresholdPx) {
                                onRequestUnlock()
                            }
                            dragTotal = 0f
                        },
                        onDragCancel = {
                            dragging = false
                            dragTotal = 0f
                        },
                        onVerticalDrag = { _, amount ->
                            dragTotal += amount
                        }
                    )
                }
                .padding(start = 22.dp, end = 22.dp, top = 64.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dateText,
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = clockColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = timeText,
                fontSize = (96 * effectiveScale).sp,
                fontWeight = FontWeight(cfg.clockWeight),
                color = clockColor,
                letterSpacing = if (cfg.clockExtended) 2.sp else 0.sp,
                textAlign = TextAlign.Center,
                modifier = if (cfg.clockExtended) {
                    Modifier.graphicsLayer { scaleX = 1.18f }
                } else {
                    Modifier
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Notification stack (real notifications once notification
            // access is granted; an honest hint card until then).
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                when {
                    !notifAccessGranted -> {
                        LockGlassCard(glassStyleIs26, cfg.glassIntensity) {
                            Text(
                                text = "Notifikasi",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Izin akses notifikasi belum diberikan — " +
                                    "aktifkan di Pengaturan launcher › Layar Kunci.",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                    notifications.isEmpty() -> {
                        LockGlassCard(glassStyleIs26, cfg.glassIntensity) {
                            Text(
                                text = "Tidak ada notifikasi",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    else -> {
                        notifications.take(4).forEach { n ->
                            LockNotificationCard(
                                n = n,
                                glassStyleIs26 = glassStyleIs26,
                                intensity = cfg.glassIntensity
                            )
                        }
                        if (notifications.size > 4) {
                            LockGlassCard(glassStyleIs26, cfg.glassIntensity) {
                                Text(
                                    text = "+${notifications.size - 4} notifikasi lain",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (showFallback) {
                TextButton(onClick = onDismissFallback) {
                    Text(
                        text = "Biometrik dibatalkan — ketuk buat geser saja",
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Bottom shortcuts: torch (real) + camera (camera26/system).
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LockShortcutButton(
                    glyph = "🔦",
                    active = torchOn,
                    glassStyleIs26 = glassStyleIs26,
                    intensity = cfg.glassIntensity,
                    onClick = { setTorch(context, !torchOn) }
                )
                LockShortcutButton(
                    glyph = "📷",
                    active = false,
                    glassStyleIs26 = glassStyleIs26,
                    intensity = cfg.glassIntensity,
                    onClick = onCameraClick
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Geser ke atas buat membuka",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .width(134.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun LockGlassCard(
    glassStyleIs26: Boolean,
    intensity: Float,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                if (glassStyleIs26) {
                    Color.White.copy(alpha = 0.10f + 0.30f * intensity)
                } else {
                    Color(0xFF1C1C1E).copy(alpha = 0.88f)
                }
            )
            .border(
                1.dp,
                if (glassStyleIs26) {
                    Color.White.copy(alpha = 0.18f + 0.30f * intensity)
                } else {
                    Color.White.copy(alpha = 0.10f)
                },
                shape
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        content()
    }
}

@Composable
private fun LockNotificationCard(
    n: LockNotification,
    glassStyleIs26: Boolean,
    intensity: Float
) {
    LockGlassCard(glassStyleIs26, intensity) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App initial monogram (we mirror text, not other apps' icons).
            val hue = (n.packageName.hashCode() and 0xFFFFFF) % 360
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        Color.hsv(hue.toFloat(), 0.45f, 0.75f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = n.appLabel.take(1).uppercase(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = n.appLabel.uppercase(),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = relativeTime(n.postedAt),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                if (n.title.isNotBlank()) {
                    Text(
                        text = n.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (n.text.isNotBlank()) {
                    Text(
                        text = n.text,
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun LockShortcutButton(
    glyph: String,
    active: Boolean,
    glassStyleIs26: Boolean,
    intensity: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(50))
            .background(
                when {
                    active -> Color(0xFFFFD60A).copy(alpha = 0.9f)
                    glassStyleIs26 ->
                        Color.Black.copy(alpha = 0.28f + 0.25f * intensity)
                    else -> Color.Black.copy(alpha = 0.45f)
                }
            )
            .border(
                1.dp,
                if (glassStyleIs26) {
                    Color.White.copy(alpha = 0.25f + 0.25f * intensity)
                } else {
                    Color.White.copy(alpha = 0.12f)
                },
                RoundedCornerShape(50)
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = glyph, fontSize = 22.sp)
    }
}

private fun relativeTime(postedAt: Long): String {
    val diffMin = ((System.currentTimeMillis() - postedAt) / 60000L)
        .coerceAtLeast(0)
    return when {
        diffMin < 1 -> "baru saja"
        diffMin < 60 -> "${diffMin}m lalu"
        diffMin < 1440 -> "${diffMin / 60}j lalu"
        else -> "${diffMin / 1440}h lalu"
    }
}

/** The device wallpaper as a bitmap (the lock screen's own copy, which
 *  the blur slider then blurs). Null when unreadable — caller falls back
 *  to the launcher gradient. */
private fun loadLockWallpaper(context: Context): Bitmap? = try {
    val drawable = android.app.WallpaperManager.getInstance(context).drawable
        ?: return null
    val w = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 1080
    val h = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 1920
    // Cap the decode size — a full-res photo wallpaper is wasteful here.
    val scale = (1080f / w).coerceAtMost(1f)
    val bw = (w * scale).toInt().coerceAtLeast(1)
    val bh = (h * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, bw, bh)
    drawable.draw(canvas)
    bitmap
} catch (e: Exception) {
    null
}

/** Small live preview used by the lock section in Settings — same
 *  ingredients, miniature. Everything moves as the sliders move. */
@Composable
fun LockPreview(
    cfg: LockPrefs,
    glassStyleIs26: Boolean
) {
    val shape = RoundedCornerShape(26.dp)
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            now = Date()
        }
    }
    val dateText = remember(now) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(now)
    }
    val timeText = remember(now) {
        SimpleDateFormat("h:mm", Locale.getDefault()).format(now)
    }
    val clockColor = Color(cfg.clockColorArgb)
    val effectiveScale =
        cfg.clockScale * if (cfg.clockExtended) 1.12f else 1f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(shape)
            .border(1.dp, Color.Black.copy(alpha = 0.08f), shape)
    ) {
        Box(
            Modifier
                .matchParentSize()
                .let {
                    if (cfg.wallpaperBlurDp > 0f &&
                        Build.VERSION.SDK_INT >= 31
                    ) {
                        it.blur(cfg.wallpaperBlurDp.dp)
                    } else {
                        it
                    }
                }
        ) {
            WallpaperBackground(Modifier.matchParentSize())
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = dateText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = clockColor,
                textAlign = TextAlign.Center
            )
            Text(
                text = timeText,
                fontSize = (54 * effectiveScale).sp,
                fontWeight = FontWeight(cfg.clockWeight),
                color = clockColor,
                letterSpacing = if (cfg.clockExtended) 2.sp else 0.sp,
                textAlign = TextAlign.Center,
                modifier = if (cfg.clockExtended) {
                    Modifier.graphicsLayer { scaleX = 1.18f }
                } else {
                    Modifier
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
            LockGlassCard(glassStyleIs26, cfg.glassIntensity) {
                Text(
                    text = "WhatsApp",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "Contoh notifikasi di layar kunci kamu…",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LockShortcutButton(
                    glyph = "🔦",
                    active = false,
                    glassStyleIs26 = glassStyleIs26,
                    intensity = cfg.glassIntensity,
                    onClick = {}
                )
                LockShortcutButton(
                    glyph = "📷",
                    active = false,
                    glassStyleIs26 = glassStyleIs26,
                    intensity = cfg.glassIntensity,
                    onClick = {}
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
            )
        }
    }
}
