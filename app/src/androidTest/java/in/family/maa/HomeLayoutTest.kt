package `in`.family.maa

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Checks the real layout with device data; screenshots preserve existing family configuration. */
class HomeLayoutTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val prefs get() = context.getSharedPreferences("family", Context.MODE_PRIVATE)

    @Test fun bilingualHomeHasNoVpnOrInstructionParagraphsAndCoreControlsFit() {
        val language=prefs.getString(AppLanguage.KEY,null)
        try {
            for(locale in listOf("en","hi")) {
                prefs.edit().putString(AppLanguage.KEY,locale).commit()
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    instrumentation.waitForIdleSync()
                    scenario.onActivity { activity ->
                        val decor=activity.window.decorView
                        fun texts(view:View):List<String> = if(view is ViewGroup) (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
                            else if(view is TextView && view.visibility==View.VISIBLE) listOf(view.text.toString()) else emptyList()
                        val labels=texts(decor)
                        assertFalse(labels.any { it.contains("VPN",ignoreCase=true) })
                        assertFalse(labels.contains(AppLanguage.text(activity,R.string.home_use_buttons)))
                        assertFalse(labels.contains(AppLanguage.text(activity,R.string.home_setup_incomplete)))
                        val screen=Rect();decor.getWindowVisibleDisplayFrame(screen)
                        val config=activity.resources.configuration
                        if(config.fontScale<=1.1f && config.screenHeightDp>=700 && !prefs.getBoolean("one_column",false)) {
                            listOf(R.id.home_voice_action,R.id.home_phone_action,R.id.home_messages_action,
                                R.id.home_chat_action,R.id.home_apps_action,R.id.home_help_action,
                                R.id.home_settings_action,R.id.home_more_action).forEach { id ->
                                val view=activity.findViewById<View>(id);val rect=Rect()
                                assertTrue("Core action $id must be visible",view.getGlobalVisibleRect(rect))
                                assertEquals("Core action $id must not be clipped",view.height,rect.height())
                                assertTrue("Core action $id must fit above navigation",rect.bottom<=screen.bottom)
                                assertTrue("Core action $id must remain a large target",view.height>=48*activity.resources.displayMetrics.density)
                            }
                        }
                    }
                    val screenshot=instrumentation.uiAutomation.takeScreenshot()
                    File(context.getExternalFilesDir(null),"home-$locale.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG,100,it) }
                    screenshot.recycle()
                }
            }
        } finally { prefs.edit().putString(AppLanguage.KEY,language).commit() }
    }

    @Test fun homeIntentClearsOpenSettingsGate() {
        val language=prefs.getString(AppLanguage.KEY,null)
        try {
            prefs.edit().putString(AppLanguage.KEY,"en").commit()
            ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
                onView(withText("Settings")).perform(click())
                scenario.onActivity { activity ->
                    activity.startActivity(Intent(activity,MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                }
                onView(withText("Maa")).check(matches(isDisplayed()))
                onView(withText("Open")).check(doesNotExist())
            }
        } finally { prefs.edit().putString(AppLanguage.KEY,language).commit() }
    }
}
