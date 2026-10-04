package `in`.family.maa

import android.content.Context
import android.content.Intent
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File

/** Run on an emulator. Tests do not send messages or save valid family settings. */
class NavigationTest {
    private val preferences get() = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("family",Context.MODE_PRIVATE)
    private var previousLanguage: String? = null
    @Before fun useDefaultHindi() {
        previousLanguage=preferences.getString(AppLanguage.KEY,null)
        preferences.edit().remove(AppLanguage.KEY).commit()
    }
    @After fun restoreLanguage() {
        preferences.edit().putString(AppLanguage.KEY,previousLanguage).commit()
    }
    private fun openSettings() {
        onView(withText("सेटिंग")).perform(scrollTo(),click())
        onView(withText("खोलें")).perform(click())
    }

    @Test fun settingsDraftSurvivesActivityRecreationWithoutSaving() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val preferences=context.getSharedPreferences("family",Context.MODE_PRIVATE)
        val storedName=preferences.getString("name1",null)
        val storedGuard=preferences.getBoolean("guard",false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()
            onView(withId(R.id.family_name_1)).perform(scrollTo(),replaceText("Draft family"),closeSoftKeyboard())
            onView(withId(R.id.family_phone_1)).perform(scrollTo(),replaceText("1234567890"),closeSoftKeyboard())
            onView(withId(R.id.guard_enabled)).perform(scrollTo(),click())
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withText("Family settings")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.family_name_1)).check(matches(withText("Draft family")))
            onView(withId(R.id.family_phone_1)).check(matches(withText("1234567890")))
            onView(withId(R.id.guard_enabled)).check(matches(if(storedGuard) isNotChecked() else isChecked()))
            assertEquals(storedName,preferences.getString("name1",null))
            assertEquals(storedGuard,preferences.getBoolean("guard",false))
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withText("परिवार की सेटिंग")).check(matches(isDisplayed()))
            onView(withId(R.id.family_name_1)).check(matches(withText("Draft family")))
        }
    }

    @Test fun languageSwitchPersistsAcrossRecreationAndRelaunch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.language_switch)).check(matches(withText("English"))).perform(click())
            onView(withText("Maa")).check(matches(isDisplayed()))
            onView(withText("Ask Maa")).check(matches(isDisplayed()))
            assertEquals("en",preferences.getString(AppLanguage.KEY,null))
            assertEquals("en-IN",AppLanguage.locale(InstrumentationRegistry.getInstrumentation().targetContext).toLanguageTag())
            onView(withId(R.id.home_help_action)).perform(scrollTo(),click())
            onView(withText("♥  Share a help message")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withText("☎  Call family")).perform(scrollTo(),click())
            onView(withText("Family")).check(matches(isDisplayed()))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withText("Maa")).check(matches(isDisplayed()))
            onView(withId(R.id.language_switch)).check(matches(withText("हिन्दी"))).perform(click())
            onView(withText("माँ")).check(matches(isDisplayed()))
            assertEquals("hi",preferences.getString(AppLanguage.KEY,null))
            assertEquals("hi-IN",AppLanguage.locale(InstrumentationRegistry.getInstrumentation().targetContext).toLanguageTag())
        }
    }

    @Test fun familyHelpSupportsLargeButtonsAndBackNavigation() {
        ActivityScenario.launch(MainActivity::class.java).use {
            onView(withId(R.id.home_help_action)).perform(scrollTo(),click())
            onView(withText("☎  परिवार को फोन करें")).check(matches(isDisplayed()))
            onView(withText("♥  मदद का संदेश साझा करें")).check(matches(isDisplayed()))
            onView(withText("☎  परिवार को फोन करें")).perform(click())
            onView(withText("परिवार")).check(matches(isDisplayed()))
            pressBack()
            onView(withText("माँ")).check(matches(isDisplayed()))
        }
    }

    @Test fun helpOverlayIntentReturnsToHomeFromSettings() {
        // Match the overlay's explicit intent so ActivityScenario can track the
        // same activity after onNewIntent updates getIntent().
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            openSettings()
            scenario.onActivity { activity ->
                activity.startActivity(Intent(activity,MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            }
            onView(withText("माँ")).check(matches(isDisplayed()))
        }
    }

    @Test fun malformedBackendAddressCannotBeSaved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettings()
            onView(withId(R.id.backend_url)).perform(scrollTo(),replaceText("https://"),closeSoftKeyboard())
            onView(withText("✓  सेव करें")).perform(scrollTo(),click())
            onView(withId(R.id.backend_url)).check(matches(withText("https://")))
            onView(withText("Backend का सही HTTPS पता डालें")).check(matches(withText("Backend का सही HTTPS पता डालें")))
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withId(R.id.backend_url)).check(matches(withText("https://")))
            onView(withText("✓  Save")).perform(scrollTo(),click())
            onView(withText("Enter a valid HTTPS backend address.")).check(matches(withText("Enter a valid HTTPS backend address.")))
        }
    }

    @Test fun qrAmountSurvivesActivityRecreation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val shop=File(context.cacheDir,"shop.jpg")
        val captured=File(context.cacheDir,"capture.jpg")
        val previousShop=shop.takeIf { it.exists() }?.readBytes()
        val previousCapture=captured.takeIf { it.exists() }?.readBytes()
        try {
            val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE)
            captured.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) }
            bitmap.recycle()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    // Deliver a simulated camera result; this does not test camera hardware.
                    MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java)
                        .apply { isAccessible=true }.invoke(activity,40,Activity.RESULT_OK,null)
                }
                onView(withHint("रकम (₹)")).perform(scrollTo(),replaceText("125.50"),closeSoftKeyboard())
                onView(withId(R.id.language_switch)).perform(scrollTo(),click())
                scenario.recreate()
                onView(withHint("Amount (₹)")).check(matches(withText("125.50")))
                onView(withText("✓  Ask family to pay")).perform(scrollTo(),click())
                onView(withText("Request ₹125.50?")).check(matches(isDisplayed()))
                onView(withText("Merchant: Family to check the photo\nThis is not a payment.")).check(matches(isDisplayed()))
                onView(withText("Cancel")).perform(click())
                onView(withId(R.id.language_switch)).perform(scrollTo(),click())
                onView(withHint("रकम (₹)")).check(matches(withText("125.50")))
                pressBack()
                onView(withText("माँ")).check(matches(isDisplayed()))
            }
        } finally {
            if(previousShop==null) shop.delete() else shop.writeBytes(previousShop)
            if(previousCapture==null) captured.delete() else captured.writeBytes(previousCapture)
        }
    }
}
