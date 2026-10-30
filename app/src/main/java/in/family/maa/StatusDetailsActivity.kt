package `in`.family.maa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.os.StatFs
import android.os.SystemClock
import android.provider.Settings
import android.text.format.Formatter
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.NumberFormat

/** Read-only status plus explicit links to settings controlled by Android. */
class StatusDetailsActivity : Activity() {
    private val values = mutableMapOf<Int, TextView>()
    private lateinit var monitor: DeviceStatusMonitor
    private lateinit var status: DeviceStatusView
    private fun local(): Context = AppLanguage.localizedContext(this)
    private fun label(id: Int): String = local().getString(id)
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.localizedContext(base)) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor = PlatformUi.CREAM
        window.navigationBarColor = PlatformUi.CREAM
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            id = R.id.platform_status_details; orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            setPadding(PlatformUi.dp(this@StatusDetailsActivity, 20), PlatformUi.dp(this@StatusDetailsActivity, 16), PlatformUi.dp(this@StatusDetailsActivity, 20), PlatformUi.dp(this@StatusDetailsActivity, 28))
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(PlatformUi.CREAM); isFillViewport = true
            clipChildren = false; clipToPadding = false
            addView(root)
        }
        root.addView(PlatformUi.button(this, label(R.string.platform_back)) { finish() }.apply { id = R.id.platform_back }, PlatformUi.params(this, 0))
        root.addView(PlatformUi.text(this, 30f, bold = true).apply { text = label(R.string.platform_status); if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }, PlatformUi.params(this))
        root.addView(PlatformUi.text(this, 18f).apply { text = label(R.string.platform_status_description) }, PlatformUi.params(this, 8))
        status = DeviceStatusView(this).apply {
            // The summary is informative here; avoid a self-link in the details screen.
            isClickable = false
            isFocusable = true
            onOpenDetails = null
        }
        root.addView(status, PlatformUi.params(this, 16))
        for (id in listOf(R.string.platform_battery, R.string.platform_network, R.string.platform_airplane,
            R.string.platform_sound, R.string.platform_alarm, R.string.platform_temperature, R.string.platform_storage,
            R.string.platform_device, R.string.platform_android, R.string.platform_uptime)) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = PlatformUi.rounded(android.graphics.Color.WHITE, PlatformUi.dp(context, 18).toFloat())
                setPadding(PlatformUi.dp(context, 18), PlatformUi.dp(context, 14), PlatformUi.dp(context, 18), PlatformUi.dp(context, 14))
            }
            card.addView(PlatformUi.text(this, 16f).apply { text = label(id) })
            values[id] = PlatformUi.text(this, 23f, bold = true).also { card.addView(it) }
            root.addView(card, PlatformUi.params(this, 10))
        }
        root.addView(PlatformUi.text(this, 17f).apply { text = label(R.string.platform_network_description) }, PlatformUi.params(this))
        for ((id, action) in listOf(R.string.platform_wifi_settings to Settings.ACTION_WIFI_SETTINGS,
            R.string.platform_network_settings to Settings.ACTION_WIRELESS_SETTINGS,
            R.string.platform_sound_settings to Settings.ACTION_SOUND_SETTINGS,
            R.string.platform_battery_settings to Settings.ACTION_BATTERY_SAVER_SETTINGS)) {
            root.addView(PlatformUi.button(this, label(id)) { openSettings(action) }, PlatformUi.params(this))
        }
        root.addView(PlatformUi.button(this, label(R.string.platform_home_settings)) { HomeRoleController.request(this) }, PlatformUi.params(this))
        setContentView(scroll)
        monitor = DeviceStatusMonitor(this) { refresh() }
        refresh()
    }
    override fun onStart() { super.onStart(); monitor.start() }
    override fun onStop() { monitor.stop(); super.onStop() }
    private fun openSettings(action: String) {
        try { startActivity(Intent(action)) }
        catch (_: RuntimeException) { Toast.makeText(this, label(R.string.platform_settings_unavailable), Toast.LENGTH_LONG).show() }
    }
    private fun refresh() {
        val local = local()
        val state = DeviceStatusSnapshot.read(this)
        val unknown = label(R.string.platform_unavailable)
        values[R.string.platform_battery]?.text = state.batteryLabel(local)
        values[R.string.platform_network]?.text = state.networkLabel(local)
        values[R.string.platform_airplane]?.text = when (state.airplane) {
            true -> label(R.string.platform_airplane_on); false -> label(R.string.platform_airplane_off); null -> unknown
        }
        values[R.string.platform_sound]?.text = state.soundLabel(local)
        values[R.string.platform_alarm]?.text = state.alarmLabel(this)
        val number = NumberFormat.getNumberInstance(AppLanguage.locale(this)).apply { maximumFractionDigits = 1 }
        values[R.string.platform_temperature]?.text = state.temperatureTenths?.let {
            local.getString(R.string.platform_temperature_value, number.format(it / 10.0))
        } ?: unknown
        values[R.string.platform_storage]?.text = runCatching {
            val disk = StatFs(filesDir.absolutePath)
            local.getString(R.string.platform_storage_value, Formatter.formatFileSize(local, disk.availableBytes), Formatter.formatFileSize(local, disk.totalBytes))
        }.getOrDefault(unknown)
        values[R.string.platform_device]?.text = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        values[R.string.platform_android]?.text = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        val minutes = SystemClock.elapsedRealtime() / 60_000
        values[R.string.platform_uptime]?.text = local.getString(R.string.platform_uptime_value, minutes / 60, minutes % 60)
        status.refresh()
    }
}
