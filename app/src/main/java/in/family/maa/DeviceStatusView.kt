package `in`.family.maa

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateFormat
import android.text.SpannableString
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date

/** Live, read-only device information. No runtime permission prompt is triggered. */
class DeviceStatusView(context: Context) : LinearLayout(context) {
    var onOpenDetails: (() -> Unit)? = null
        set(value) { field = value; isClickable = value != null; refresh() }
    private val clock = PlatformUi.text(context, 52f, PlatformUi.GREEN, true)
    private val date = PlatformUi.text(context, 18f, PlatformUi.INK, true)
    private val battery = PlatformUi.text(context, 30f, PlatformUi.INK, true)
    private val network = PlatformUi.text(context, 21f, PlatformUi.GREEN, true)
    private val mainRow = LinearLayout(context)
    private val timeColumn = LinearLayout(context)
    private val statsRow = LinearLayout(context)
    private val divider = View(context)
    private val batteryIcon = DeviceStatusGlyph(context, true)
    private val networkIcon = DeviceStatusGlyph(context, false)
    private var wideLayout: Boolean? = null
    private val monitor = DeviceStatusMonitor(context) { refresh() }

    init {
        id = R.id.device_status
        orientation = VERTICAL
        setPadding(PlatformUi.dp(context, 12), PlatformUi.dp(context, 12), PlatformUi.dp(context, 12), PlatformUi.dp(context, 12))
        background = RippleDrawable(ColorStateList.valueOf(0x22336B50), PlatformUi.rounded(Color.rgb(255, 254, 250), PlatformUi.dp(context, 26).toFloat()), null)
        isClickable = false
        isFocusable = true
        minimumHeight = PlatformUi.dp(context, 64)
        clock.id = R.id.device_status_clock
        battery.id = R.id.device_status_battery
        network.id = R.id.device_status_network
        listOf(clock, date, battery, network, batteryIcon, networkIcon).forEach { child ->
            child.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        timeColumn.orientation = VERTICAL
        timeColumn.addView(clock, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        mainRow.gravity = Gravity.CENTER_VERTICAL
        statsRow.orientation = HORIZONTAL
        statsRow.gravity = Gravity.TOP
        listOf(networkIcon to network, batteryIcon to battery).forEach { (icon, label) ->
            val column = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            column.addView(icon, LayoutParams(PlatformUi.dp(context, 48), PlatformUi.dp(context, 42)))
            label.gravity = Gravity.CENTER
            label.setSingleLine(true)
            if (label === network) label.setAutoSizeTextTypeUniformWithConfiguration(18, 21, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            column.addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = PlatformUi.dp(context, 4) })
            statsRow.addView(column, LayoutParams(0, LayoutParams.WRAP_CONTENT, if (label === battery) 78f else 48f))
        }
        divider.setBackgroundColor(Color.rgb(230, 234, 226))
        mainRow.addView(timeColumn)
        mainRow.addView(divider)
        mainRow.addView(statsRow)
        addView(mainRow, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(date, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = PlatformUi.dp(context, 2) })
        // The connection graphic and card's spoken summary are sufficient on sparse Home.
        network.visibility = View.GONE
        setOnClickListener { onOpenDetails?.invoke() }
        refresh()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val statsWidth = PlatformUi.dp(context, 126)
        val gap = PlatformUi.dp(context, 10)
        val neededClock = kotlin.math.ceil(android.text.Layout.getDesiredWidth(clock.text, clock.paint).toDouble()).toInt()
        val wide = resources.configuration.fontScale <= 1.3f && available >= neededClock + statsWidth + gap
        if (wideLayout != wide) {
            wideLayout = wide
            mainRow.orientation = if (wide) HORIZONTAL else VERTICAL
            timeColumn.layoutParams = if (wide) LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            divider.layoutParams = if (wide) LayoutParams(PlatformUi.dp(context, 1), LayoutParams.MATCH_PARENT).apply {
                marginStart = PlatformUi.dp(context, 4); marginEnd = PlatformUi.dp(context, 5)
            } else LayoutParams(LayoutParams.MATCH_PARENT, PlatformUi.dp(context, 1)).apply {
                topMargin = PlatformUi.dp(context, 14); bottomMargin = PlatformUi.dp(context, 14)
            }
            statsRow.layoutParams = if (wide) LayoutParams(statsWidth, LayoutParams.WRAP_CONTENT)
                else LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); if (windowVisibility == View.VISIBLE) monitor.start() }
    override fun onDetachedFromWindow() { monitor.stop(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow && visibility == View.VISIBLE) monitor.start() else monitor.stop()
    }

    fun refresh() {
        val local = AppLanguage.localizedContext(context)
        val status = DeviceStatusSnapshot.read(context)
        val locale = AppLanguage.locale(context)
        val use24 = DateFormat.is24HourFormat(context)
        val numbers = SimpleDateFormat(if (use24) "HH:mm" else "h:mm", locale).format(Date(status.timestamp))
        val period = if (use24) "" else SimpleDateFormat("a", locale).format(Date(status.timestamp))
        clock.text = if (period.isBlank()) numbers else SpannableString("$numbers $period").apply {
            setSpan(RelativeSizeSpan(.28f), numbers.length + 1, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        date.text = status.date(context)
        battery.text = status.batteryPercent?.let { java.text.NumberFormat.getPercentInstance(locale).format(it / 100.0) }
            ?: "—"
        network.text = status.homeNetworkCaption(local)
        batteryIcon.status = status
        networkIcon.status = status.copy(networkTransport = status.homeTransport())
        contentDescription = (listOf(status.clock(context), date.text, status.batteryLabel(local), status.homeNetworkLabel(local),
            status.soundLabel(local), status.alarmLabel(context)) +
            (if (status.airplane == true) listOf(local.getString(R.string.platform_airplane_on)) else emptyList()) +
            if (onOpenDetails != null) listOf(local.getString(R.string.platform_status_hint)) else emptyList()).joinToString(". ")
    }
}

/** Original transport/battery graphics. No invented cellular-strength bars. */
private class DeviceStatusGlyph(context: Context, private val isBattery: Boolean) : View(context) {
    var status: DeviceStatusSnapshot? = null
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = PlatformUi.GREEN; strokeWidth = 2.8f; style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.scale(width / 48f, height / 42f)
        paint.style = Paint.Style.STROKE
        paint.color = PlatformUi.GREEN
        val value = status
        if (isBattery) {
            canvas.drawRoundRect(3f, 10f, 41f, 33f, 5f, 5f, paint)
            canvas.drawLine(45f, 17f, 45f, 26f, paint)
            value?.batteryPercent?.let { percent ->
                if (percent > 0) {
                    paint.style = Paint.Style.FILL
                    if (percent <= 15) paint.color = Color.rgb(159, 60, 28)
                    canvas.drawRoundRect(7f, 14f, 7f + 30f * percent / 100f, 29f, 2f, 2f, paint)
                }
            } ?: question(canvas, 22f, 27f)
            if (value?.batteryState == BatteryManager.BATTERY_STATUS_CHARGING) {
                paint.style = Paint.Style.FILL
                paint.color = if ((value.batteryPercent ?: 0) >= 60) Color.WHITE else PlatformUi.GREEN
                val bolt = Path().apply { moveTo(25f, 13f); lineTo(18f, 23f); lineTo(23f, 23f); lineTo(20f, 30f); lineTo(29f, 20f); lineTo(24f, 20f); close() }
                canvas.drawPath(bolt, paint)
            }
        } else {
            when {
                value == null || value.internetState == DeviceStatusSnapshot.NETWORK_UNKNOWN -> {
                    canvas.drawCircle(24f, 21f, 15f, paint); question(canvas, 24f, 29f)
                }
                value.internetState == DeviceStatusSnapshot.NETWORK_OFFLINE -> {
                    canvas.drawCircle(24f, 21f, 15f, paint); canvas.drawLine(13f, 32f, 35f, 10f, paint)
                }
                value.networkTransport == NetworkCapabilities.TRANSPORT_WIFI -> {
                    canvas.drawArc(3f, 5f, 45f, 45f, 220f, 100f, false, paint)
                    canvas.drawArc(10f, 13f, 38f, 41f, 220f, 100f, false, paint)
                    canvas.drawArc(17f, 21f, 31f, 35f, 220f, 100f, false, paint)
                    paint.style = Paint.Style.FILL; canvas.drawCircle(24f, 34f, 2.5f, paint)
                }
                value.networkTransport == NetworkCapabilities.TRANSPORT_CELLULAR -> {
                    canvas.drawRoundRect(13f, 3f, 35f, 39f, 4f, 4f, paint)
                    canvas.drawLine(21f, 34f, 27f, 34f, paint)
                    canvas.drawLine(20f, 13f, 20f, 26f, paint); canvas.drawLine(17f, 17f, 20f, 13f, paint); canvas.drawLine(20f, 13f, 23f, 17f, paint)
                    canvas.drawLine(28f, 13f, 28f, 26f, paint); canvas.drawLine(25f, 22f, 28f, 26f, paint); canvas.drawLine(28f, 26f, 31f, 22f, paint)
                }
                else -> {
                    canvas.drawCircle(24f, 21f, 16f, paint); canvas.drawOval(17f, 5f, 31f, 37f, paint)
                    canvas.drawLine(9f, 16f, 39f, 16f, paint); canvas.drawLine(9f, 26f, 39f, 26f, paint)
                }
            }
        }
        canvas.restoreToCount(save)
    }
    private fun question(canvas: Canvas, x: Float, y: Float) {
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.textSize = 22f
        canvas.drawText("?", x, y, paint)
    }
}

internal data class DeviceStatusSnapshot(
    val timestamp: Long,
    val batteryPercent: Int?,
    val batteryState: Int,
    val temperatureTenths: Int?,
    val networkTransport: Int?,
    val internetState: Int,
    val airplane: Boolean?,
    val ringer: Int?,
    val nextAlarm: Long?,
    val alarmAvailable: Boolean,
    val underlyingTransport: Int? = null
) {
    fun clock(context: Context): String = format(context, if (DateFormat.is24HourFormat(context)) "Hm" else "hma", timestamp)
    fun date(context: Context): String = format(context, "EEEEMMMMd", timestamp)
    /** Home names a measured physical transport or a neutral connection, never a VPN badge. */
    fun homeTransport(): Int? = if (networkTransport == NetworkCapabilities.TRANSPORT_VPN) underlyingTransport else networkTransport
    fun homeNetworkCaption(local: Context): String = local.getString(when {
        internetState == NETWORK_UNKNOWN -> R.string.platform_short_unknown
        internetState == NETWORK_OFFLINE -> R.string.platform_short_offline
        homeTransport() == NetworkCapabilities.TRANSPORT_WIFI -> R.string.platform_short_wifi
        homeTransport() == NetworkCapabilities.TRANSPORT_CELLULAR -> R.string.platform_short_mobile
        homeTransport() == NetworkCapabilities.TRANSPORT_ETHERNET -> R.string.platform_short_ethernet
        else -> R.string.platform_short_network
    })
    fun homeNetworkLabel(local: Context): String = copy(networkTransport = homeTransport()).networkLabel(local)
    fun batteryLabel(local: Context): String {
        val value = batteryPercent?.let { local.getString(R.string.platform_battery_value, it) }
            ?: "${local.getString(R.string.platform_battery)}: ${local.getString(R.string.platform_unavailable)}"
        return when (batteryState) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "$value · ${local.getString(R.string.platform_charging)}"
            BatteryManager.BATTERY_STATUS_FULL -> "$value · ${local.getString(R.string.platform_full)}"
            else -> value
        }
    }
    fun networkLabel(local: Context): String {
        if (internetState == NETWORK_UNKNOWN) return local.getString(R.string.platform_network_unavailable)
        if (internetState == NETWORK_OFFLINE) return local.getString(R.string.platform_offline)
        val transport = when (networkTransport) {
            NetworkCapabilities.TRANSPORT_WIFI -> R.string.platform_wifi
            NetworkCapabilities.TRANSPORT_CELLULAR -> R.string.platform_mobile
            NetworkCapabilities.TRANSPORT_ETHERNET -> R.string.platform_ethernet
            NetworkCapabilities.TRANSPORT_VPN -> R.string.platform_vpn
            else -> R.string.platform_connected
        }
        val internet = when (internetState) {
            NETWORK_VALIDATED -> R.string.platform_online
            NETWORK_PORTAL -> R.string.platform_sign_in_network
            else -> R.string.platform_no_verified_internet
        }
        return "${local.getString(transport)} · ${local.getString(internet)}"
    }
    fun soundLabel(local: Context): String = local.getString(when (ringer) {
        AudioManager.RINGER_MODE_NORMAL -> R.string.platform_sound_on
        AudioManager.RINGER_MODE_SILENT -> R.string.platform_silent
        AudioManager.RINGER_MODE_VIBRATE -> R.string.platform_vibrate
        else -> R.string.platform_unavailable
    })
    fun alarmLabel(context: Context): String {
        val local = AppLanguage.localizedContext(context)
        if (!alarmAvailable) return "${local.getString(R.string.platform_alarm)}: ${local.getString(R.string.platform_unavailable)}"
        return nextAlarm?.let {
            local.getString(R.string.platform_alarm_value, format(context, if (DateFormat.is24HourFormat(context)) "EEEHm" else "EEEhma", it))
        } ?: local.getString(R.string.platform_no_alarm)
    }
    companion object {
        const val NETWORK_UNKNOWN = -1
        const val NETWORK_OFFLINE = 0
        const val NETWORK_UNVERIFIED = 1
        const val NETWORK_VALIDATED = 2
        const val NETWORK_PORTAL = 3

        fun percentage(level: Int, scale: Int): Int? =
            if (scale > 0 && level in 0..scale) (level.toLong() * 100L / scale).toInt() else null

        private fun format(context: Context, skeleton: String, value: Long): String {
            val locale = AppLanguage.locale(context)
            return SimpleDateFormat(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(Date(value))
        }

        fun read(context: Context): DeviceStatusSnapshot {
            val battery = runCatching { context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }.getOrNull()
            val percent = battery?.let { percentage(it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1), it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)) }
            val temperature = battery?.takeIf { it.hasExtra(BatteryManager.EXTRA_TEMPERATURE) }
                ?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0)?.takeIf { it in -500..1000 }
            var transport: Int? = null
            var underlying: Int? = null
            var internet = NETWORK_UNKNOWN
            try {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                if (manager != null) {
                    val active = manager.activeNetwork
                    if (active == null) internet = NETWORK_OFFLINE else {
                        val caps = manager.getNetworkCapabilities(active)
                        if (caps != null) {
                            transport = listOf(NetworkCapabilities.TRANSPORT_VPN, NetworkCapabilities.TRANSPORT_WIFI,
                                NetworkCapabilities.TRANSPORT_CELLULAR, NetworkCapabilities.TRANSPORT_ETHERNET)
                                .firstOrNull { caps.hasTransport(it) }
                            // Multiple or absent physical transports do not identify one active route.
                            underlying = listOf(NetworkCapabilities.TRANSPORT_WIFI, NetworkCapabilities.TRANSPORT_CELLULAR,
                                NetworkCapabilities.TRANSPORT_ETHERNET).filter { caps.hasTransport(it) }.singleOrNull()
                            internet = when {
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) -> NETWORK_PORTAL
                                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) -> NETWORK_VALIDATED
                                else -> NETWORK_UNVERIFIED
                            }
                        }
                    }
                }
            } catch (_: SecurityException) { /* Unknown is distinct from offline. */ }
            val airplane = runCatching { Settings.Global.getInt(context.contentResolver, Settings.Global.AIRPLANE_MODE_ON) == 1 }.getOrNull()
            val ringer = runCatching { context.getSystemService(AudioManager::class.java)?.ringerMode }.getOrNull()
            val alarmManager = context.getSystemService(AlarmManager::class.java)
            val alarm = runCatching { alarmManager?.nextAlarmClock }
            return DeviceStatusSnapshot(System.currentTimeMillis(), percent,
                battery?.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN) ?: BatteryManager.BATTERY_STATUS_UNKNOWN,
                temperature, transport, internet, airplane, ringer, alarm.getOrNull()?.triggerTime,
                alarmManager != null && alarm.isSuccess, underlying)
        }
    }
}

/** One observer per visible owner. Call stop to release every registered callback. */
internal class DeviceStatusMonitor(private val context: Context, private val changed: () -> Unit) {
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var receiverRegistered = false
    private var networkRegistered = false
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val prefs = context.getSharedPreferences("family", Context.MODE_PRIVATE)
    private val refreshTask = Runnable { if (active) changed() }
    private fun update() { handler.removeCallbacks(refreshTask); handler.post(refreshTask) }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { update() }
    }
    private val languageListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == AppLanguage.KEY) update() }
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { update() }
        override fun onLost(network: Network) { update() }
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) { update() }
    }
    fun start() {
        if (active) return
        active = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED); addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED); addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
        }
        try {
            // All actions above are protected system broadcasts; no app-defined input is consumed.
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else @Suppress("DEPRECATION") context.registerReceiver(receiver, filter)
            receiverRegistered = true
        } catch (_: RuntimeException) { /* Initial and explicit refresh still work on restrictive OEMs. */ }
        try {
            manager?.registerDefaultNetworkCallback(network, handler)
            networkRegistered = manager != null
        } catch (_: RuntimeException) { /* Missing permission is represented as unknown. */ }
        prefs.registerOnSharedPreferenceChangeListener(languageListener)
        changed()
    }
    fun stop() {
        if (!active) return
        active = false
        if (receiverRegistered) runCatching { context.unregisterReceiver(receiver) }
        if (networkRegistered) runCatching { manager?.unregisterNetworkCallback(network) }
        prefs.unregisterOnSharedPreferenceChangeListener(languageListener)
        receiverRegistered = false
        networkRegistered = false
        handler.removeCallbacks(refreshTask)
    }
}

internal object PlatformUi {
    val GREEN: Int = Color.rgb(24, 68, 55)
    val CREAM: Int = Color.rgb(249, 247, 239)
    val INK: Int = Color.rgb(29, 53, 45)
    val MINT: Int = Color.rgb(217, 234, 222)
    fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun rounded(color: Int, radius: Float): GradientDrawable = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    fun text(context: Context, size: Float, color: Int = INK, bold: Boolean = false): TextView = TextView(context).apply {
        textSize = size; setTextColor(color); setLineSpacing(0f, 1.08f)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }
    fun button(context: Context, label: String, action: () -> Unit): Button = Button(context).apply {
        text = label; textSize = 20f; isAllCaps = false; setTextColor(GREEN)
        gravity = Gravity.CENTER; minHeight = dp(context, 64)
        setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
        background = RippleDrawable(ColorStateList.valueOf(0x22336B50), rounded(Color.WHITE, dp(context, 18).toFloat()), null)
        setOnClickListener { action() }
    }
    fun params(context: Context, top: Int = 12): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(context, top) }
}
