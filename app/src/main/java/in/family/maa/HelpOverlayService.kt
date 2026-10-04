package `in`.family.maa

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.math.abs

/** User-enabled navigation helper. It never starts a microphone or observes another app. */
class HelpOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFERENCES, MODE_PRIVATE) }
    private val languagePrefs by lazy { getSharedPreferences("family", MODE_PRIVATE) }
    private lateinit var windows: WindowManager
    private var panel: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var expanded = false
    private var stopping = false
    private var requestGeneration = -1L
    private var panelGeneration = -1L
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == AppLanguage.KEY || key == AssistantIdentity.KEY_NAME) handler.post { if (!stopping) { updateNotification(); render() } }
    }
    private val permissionCheck = object : Runnable {
        override fun run() {
            if (stopping) return
            if (!currentRequest() || !Settings.canDrawOverlays(this@HelpOverlayService)) { finishRequest(); return }
            handler.postDelayed(this, 1500)
        }
    }
    private fun tr(id: Int): String = when (id) {
        R.string.assistant_title -> AssistantIdentity.displayName(this)
        R.string.assistant_ask -> AssistantIdentity.askLabel(this)
        R.string.assistant_overlay_channel, R.string.assistant_overlay_bubble, R.string.assistant_overlay_expand ->
            AppLanguage.localizedContext(this).getString(id, AssistantIdentity.displayName(this))
        else -> AppLanguage.text(this, id)
    }
    private fun dp(value: Int): Int = MaaDesign.dp(this, value)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        windows = getSystemService(WindowManager::class.java)
        languagePrefs.registerOnSharedPreferenceChangeListener(languageListener)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stop(this); stopping = true; removePanel(); stopSelf(); return START_NOT_STICKY
        }
        if (!acceptsStart(this, intent)) {
            if (!currentRequest()) stopSelf(startId)
            return if (currentRequest()) START_STICKY else START_NOT_STICKY
        }
        requestGeneration = generation(this); stopping = false
        if (!Settings.canDrawOverlays(this)) { finishRequest(); return START_NOT_STICKY }
        try { updateNotification(); if (panel == null || panelGeneration != requestGeneration) render() }
        catch (_: RuntimeException) { finishRequest(); return START_NOT_STICKY }
        handler.removeCallbacks(permissionCheck); handler.post(permissionCheck)
        // Android may restart a requested helper; continuous presence is not guaranteed.
        return START_STICKY
    }
    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr(R.string.assistant_overlay_channel), NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 80, Intent(this, AssistantActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val close = PendingIntent.getService(this, 81, Intent(this, HelpOverlayService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(NOTIFICATION, Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_maa)
            .setContentTitle(tr(R.string.assistant_title)).setContentText(tr(R.string.assistant_overlay_notification))
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .addAction(Notification.Action.Builder(null, tr(R.string.assistant_overlay_stop), close).build()).build())
    }
    private fun screen(): DisplayMetrics = DisplayMetrics().also {
        @Suppress("DEPRECATION") windows.defaultDisplay.getRealMetrics(it)
    }
    private fun currentRequest(): Boolean = isRequested(this) && requestGeneration == generation(this)
    private fun finishRequest() {
        if (currentRequest()) stop(this)
        stopping = true; removePanel(); stopSelf()
    }
    private fun render() {
        if (stopping || !currentRequest() || !Settings.canDrawOverlays(this)) { finishRequest(); return }
        removePanel()
        val renderingGeneration = requestGeneration
        val metrics = screen()
        val width = if (expanded) minOf(dp(290), metrics.widthPixels - dp(24)) else minOf(dp(166), metrics.widthPixels - dp(24))
        val maxHeight = minOf(dp(520), metrics.heightPixels - dp(100)).coerceAtLeast(dp(84))
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(10), dp(10), dp(10))
            background = MaaDesign.shape(this@HelpOverlayService, Color.rgb(255, 254, 250), 25, true)
            elevation = dp(6).toFloat()
        }
        fun button(id: Int, primary: Boolean = false, click: () -> Unit): View = MaaDesign.action(this, tr(id), primary, click).also {
            content.addView(it, LinearLayout.LayoutParams(-1, -2).apply { if (content.childCount > 1) topMargin = dp(6) })
        }
        val handle = if (!expanded) {
            button(R.string.assistant_overlay_bubble, true) { expanded = true; render() }.apply { contentDescription = tr(R.string.assistant_overlay_expand) }
        } else {
            button(R.string.assistant_overlay_move) { expanded = false; render() }.apply { contentDescription = tr(R.string.assistant_overlay_move_hint) }
        }
        makeMovable(handle)
        if (expanded) {
            button(R.string.assistant_ask, true) { open(AssistantActivity.voiceIntent(this, navigateLocalAfterSpeech = false)) }
            button(R.string.assistant_family) { open(CommunicationActivity.intent(this)) }
            button(R.string.assistant_home) { open(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            button(R.string.assistant_overlay_collapse) { expanded = false; render() }
            button(R.string.assistant_overlay_close) { stop(this) }
        }
        val root = object : ScrollView(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST))
            }
        }.apply { isFillViewport = false; addView(content); clipToPadding = false }
        val layout = WindowManager.LayoutParams(width, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (prefs.getFloat("x", .9f).coerceIn(0f, 1f) * (metrics.widthPixels - width).coerceAtLeast(0)).toInt()
            y = (prefs.getFloat("y", .72f).coerceIn(0f, 1f) * metrics.heightPixels).toInt()
        }
        panel = root; params = layout; panelGeneration = renderingGeneration
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (!reportAttachment(this@HelpOverlayService, renderingGeneration, true)) {
                    handler.post { if (panel === v && !currentRequest()) { removePanel(); stopSelf() } }
                }
            }
            override fun onViewDetachedFromWindow(v: View) { reportAttachment(this@HelpOverlayService, renderingGeneration, false) }
        })
        try {
            windows.addView(root, layout)
            root.post { if (panel === root && !stopping) clampAndUpdate(false) }
        } catch (_: RuntimeException) { finishRequest() }
    }
    private fun open(intent: Intent) {
        try { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); expanded = false; render() }
        catch (_: RuntimeException) { Toast.makeText(this, tr(R.string.assistant_error), Toast.LENGTH_LONG).show() }
    }
    private fun makeMovable(handle: View) {
        var startX = 0; var startY = 0; var downX = 0f; var downY = 0f; var moved = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        handle.setOnTouchListener { view, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params?.x ?: 0; startY = params?.y ?: 0
                    downX = event.rawX; downY = event.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (moved || abs(dx) > slop || abs(dy) > slop) {
                        moved = true; view.parent?.requestDisallowInterceptTouchEvent(true)
                        params?.let { it.x = startX + dx.toInt(); it.y = startY + dy.toInt() }; clampAndUpdate(false)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> { if (moved) clampAndUpdate(true) else view.performClick(); true }
                MotionEvent.ACTION_CANCEL -> { if (moved) clampAndUpdate(true); true }
                else -> false
            }
        }
        handle.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                listOf(MOVE_LEFT to R.string.assistant_move_left, MOVE_RIGHT to R.string.assistant_move_right,
                    MOVE_UP to R.string.assistant_move_up, MOVE_DOWN to R.string.assistant_move_down).forEach { (action, label) ->
                    info.addAction(AccessibilityNodeInfo.AccessibilityAction(action, tr(label)))
                }
            }
            override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                if (action in setOf(MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN)) {
                    params?.let {
                        when (action) { MOVE_LEFT -> it.x -= dp(48); MOVE_RIGHT -> it.x += dp(48); MOVE_UP -> it.y -= dp(48); MOVE_DOWN -> it.y += dp(48) }
                    }
                    clampAndUpdate(true); return true
                }
                return super.performAccessibilityAction(host, action, args)
            }
        }
    }
    private fun clampAndUpdate(persist: Boolean) {
        val view = panel ?: return
        val layout = params ?: return
        val bounds = screen()
        val margin = dp(12)
        val maxX = (bounds.widthPixels - view.width - margin).coerceAtLeast(margin)
        // Leave system-bar margins; positioning does not guarantee keyboard avoidance.
        val maxY = (bounds.heightPixels - view.height - dp(64)).coerceAtLeast(dp(32))
        layout.x = layout.x.coerceIn(margin, maxX); layout.y = layout.y.coerceIn(dp(32), maxY)
        try { windows.updateViewLayout(view, layout) }
        catch (_: RuntimeException) { finishRequest(); return }
        if (persist) prefs.edit().putFloat("x", layout.x.toFloat() / (bounds.widthPixels - layout.width).coerceAtLeast(1))
            .putFloat("y", layout.y.toFloat() / bounds.heightPixels.coerceAtLeast(1)).apply()
    }
    private fun removePanel() {
        val old = panel; val oldGeneration = panelGeneration
        panel = null; params = null; panelGeneration = -1L
        reportAttachment(this, oldGeneration, false)
        if (old != null) runCatching { windows.removeViewImmediate(old) }
    }
    override fun onConfigurationChanged(newConfig: Configuration) { super.onConfigurationChanged(newConfig); render() }
    override fun onDestroy() {
        stopping = true; handler.removeCallbacksAndMessages(null)
        languagePrefs.unregisterOnSharedPreferenceChangeListener(languageListener)
        removePanel(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
    }
    companion object {
        const val ACTION_STOP = "in.family.maa.STOP_HELPER"
        private const val ACTION_START = "in.family.maa.START_HELPER"
        private const val REQUEST_GENERATION = "request_generation"
        private const val PREFERENCES = "maa_assistant_overlay"
        private const val CHANNEL = "maa_helper"
        private const val NOTIFICATION = 1
        private const val MOVE_LEFT = 0x01010001
        private const val MOVE_RIGHT = 0x01010002
        private const val MOVE_UP = 0x01010003
        private const val MOVE_DOWN = 0x01010004
        @Volatile private var attachedGeneration = -1L
        private val listeners = CopyOnWriteArraySet<() -> Unit>()
        private val stateLock = Any()
        private val stateHandler = Handler(Looper.getMainLooper())
        fun addStateListener(listener: () -> Unit) { listeners.add(listener) }
        fun removeStateListener(listener: () -> Unit) { listeners.remove(listener) }
        private fun publish() {
            if (Looper.myLooper() == Looper.getMainLooper()) listeners.forEach { it() }
            else stateHandler.post { listeners.forEach { it() } }
        }
        internal fun generation(context: Context): Long = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getLong(REQUEST_GENERATION, 0L)
        fun isActive(context: Context): Boolean = isRequested(context) && attachedGeneration == generation(context) && Settings.canDrawOverlays(context)
        fun isRequested(context: Context): Boolean = context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).getBoolean("enabled", false)
        internal fun acceptsStart(context: Context, intent: Intent?): Boolean = isRequested(context) &&
            (intent == null || (intent.action == ACTION_START && intent.getLongExtra(REQUEST_GENERATION, -1L) == generation(context)))
        internal fun reportAttachment(context: Context, requestToken: Long, attached: Boolean): Boolean {
            val accepted = synchronized(stateLock) {
                if (attached) {
                    if (isRequested(context) && requestToken == generation(context)) { attachedGeneration = requestToken; true } else false
                } else {
                    if (attachedGeneration == requestToken) attachedGeneration = -1L
                    false
                }
            }
            publish()
            return accepted
        }
        internal fun prepareStart(context: Context): Intent {
            val next = synchronized(stateLock) {
                val next = generation(context) + 1L
                context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean("enabled", true).putLong(REQUEST_GENERATION, next).apply()
                attachedGeneration = -1L
                next
            }
            publish()
            return Intent(context, HelpOverlayService::class.java).setAction(ACTION_START).putExtra(REQUEST_GENERATION, next)
        }
        fun start(context: Context) {
            val request = prepareStart(context)
            try { context.startForegroundService(request) }
            catch (failure: RuntimeException) {
                if (acceptsStart(context, request)) stop(context)
                throw failure
            }
        }
        fun stop(context: Context) {
            synchronized(stateLock) {
                context.getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().putBoolean("enabled", false)
                    .putLong(REQUEST_GENERATION, generation(context) + 1L).apply()
                attachedGeneration = -1L
            }
            publish() // Synchronous on the UI thread; stopService/onDestroy may run later.
            context.stopService(Intent(context, HelpOverlayService::class.java))
        }
    }
}
