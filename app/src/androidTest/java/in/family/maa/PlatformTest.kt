package `in`.family.maa

import android.content.Context
import android.content.res.Configuration
import android.media.AudioManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** Read-only platform tests: no system setting changes, external launches, calls or sends. */
class PlatformTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("family", Context.MODE_PRIVATE)
    private var language: String? = null
    @Before fun english() { language = prefs.getString(AppLanguage.KEY, null); prefs.edit().putString(AppLanguage.KEY, "en").commit() }
    @After fun restore() { prefs.edit().putString(AppLanguage.KEY, language).commit() }

    @Test fun impossibleBatteryValuesAreNotInvented() {
        assertNull(DeviceStatusSnapshot.percentage(-1, 100))
        assertNull(DeviceStatusSnapshot.percentage(120, 100))
        assertNull(DeviceStatusSnapshot.percentage(1, 0))
        assertEquals(0, DeviceStatusSnapshot.percentage(0, 100))
        assertEquals(50, DeviceStatusSnapshot.percentage(1, 2))
        assertEquals(100, DeviceStatusSnapshot.percentage(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test fun networkUnknownOfflineAndUnverifiedRemainDistinct() {
        val sample = DeviceStatusSnapshot(0, null, BatteryManager.BATTERY_STATUS_UNKNOWN, null,
            NetworkCapabilities.TRANSPORT_WIFI, DeviceStatusSnapshot.NETWORK_UNKNOWN, false,
            AudioManager.RINGER_MODE_NORMAL, null, true)
        val local = AppLanguage.localizedContext(context)
        assertEquals("Network status unavailable", sample.networkLabel(local))
        assertEquals("Offline", sample.copy(internetState = DeviceStatusSnapshot.NETWORK_OFFLINE).networkLabel(local))
        assertEquals("Wi-Fi · Internet unverified", sample.copy(internetState = DeviceStatusSnapshot.NETWORK_UNVERIFIED).networkLabel(local))
        assertEquals("Wi-Fi · Network sign-in needed", sample.copy(internetState = DeviceStatusSnapshot.NETWORK_PORTAL).networkLabel(local))
        assertEquals("Wi-Fi · Internet available", sample.copy(internetState = DeviceStatusSnapshot.NETWORK_VALIDATED).networkLabel(local))
    }

    @Test fun narrowLargeTextStatusWrapsAndRefreshesLanguage() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val large = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 2f })
            val view = DeviceStatusView(large).apply { onOpenDetails = {} }
            val width = (320 * large.resources.displayMetrics.density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val battery = view.findViewById<TextView>(R.id.device_status_battery)
            assertTrue(view.measuredHeight > 0)
            assertEquals(0, battery.layout.getEllipsisCount(battery.lineCount - 1))
            assertTrue(view.contentDescription.contains("Battery"))
            prefs.edit().putString(AppLanguage.KEY, "hi").commit()
            view.refresh()
            assertTrue(view.contentDescription.contains("बैटरी"))
        }
    }

    @Test fun homeUsesPhysicalTransportOrNeutralConnectionWithoutVpnBadge() {
        val vpn = DeviceStatusSnapshot(0, 100, BatteryManager.BATTERY_STATUS_FULL, null,
            NetworkCapabilities.TRANSPORT_VPN, DeviceStatusSnapshot.NETWORK_VALIDATED, false,
            AudioManager.RINGER_MODE_NORMAL, null, true)
        val local = AppLanguage.localizedContext(context)
        assertEquals("Network", vpn.homeNetworkCaption(local))
        assertFalse(vpn.homeNetworkLabel(local).contains("VPN"))
        assertEquals("Wi-Fi", vpn.copy(underlyingTransport = NetworkCapabilities.TRANSPORT_WIFI).homeNetworkCaption(local))
        assertEquals("Mobile", vpn.copy(underlyingTransport = NetworkCapabilities.TRANSPORT_CELLULAR).homeNetworkCaption(local))
        assertEquals("Offline", vpn.copy(internetState = DeviceStatusSnapshot.NETWORK_OFFLINE).homeNetworkCaption(local))
        // Details retain the real connection information; presentation does not alter networking.
        assertTrue(vpn.networkLabel(local).contains("VPN"))
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val view = DeviceStatusView(context)
            assertFalse(view.findViewById<TextView>(R.id.device_status_network).text.contains("VPN"))
            assertEquals("Home has only essentials and the full-width date", 2, view.childCount)
            assertEquals(View.GONE, view.findViewById<TextView>(R.id.device_status_network).visibility)
        }
    }

    @Test fun normalPocoWidthKeepsStatusCompactWithoutShrinkingClock() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val normal = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1f })
            val view = DeviceStatusView(normal)
            val density = normal.resources.displayMetrics.density
            // 393dp phone minus the Home screen's 16dp margin on each side.
            val width = (361 * density).toInt()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            view.layout(0, 0, width, view.measuredHeight)
            assertTrue("Normal Home status should stay under 155dp", view.measuredHeight <= 155 * density)
            assertEquals(52f, view.findViewById<TextView>(R.id.device_status_clock).textSize / normal.resources.displayMetrics.scaledDensity, .1f)
        }
    }

    @Test fun appSearchSurvivesRecreationWithoutChangingPreferences() {
        val appPrefs = context.getSharedPreferences("maa_apps", Context.MODE_PRIVATE)
        val before = appPrefs.all.toMap()
        ActivityScenario.launch(AppsActivity::class.java).use { scenario ->
            scenario.onActivity { it.findViewById<EditText>(R.id.platform_app_search).setText("unlikely-test-app-938417") }
            scenario.recreate()
            scenario.onActivity { assertEquals("unlikely-test-app-938417", it.findViewById<EditText>(R.id.platform_app_search).text.toString()) }
        }
        assertEquals(before, appPrefs.all)
    }
}
