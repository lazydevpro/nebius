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
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.After
import org.junit.Test
import java.io.File

/** No calls/messages/payments; language preferences and QR fixtures are restored. */
class NavigationTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences get()=context.getSharedPreferences("family",Context.MODE_PRIVATE)
    private var previousLanguage:String?=null
    private var previousSetup:Boolean?=null
    @Before fun useEnglish() {
        previousLanguage=preferences.getString(AppLanguage.KEY,null)
        previousSetup=if(preferences.contains(MainActivity.ONBOARDING_COMPLETE)) preferences.getBoolean(MainActivity.ONBOARDING_COMPLETE,false) else null
        preferences.edit().remove(AppLanguage.KEY).putBoolean(MainActivity.ONBOARDING_COMPLETE,true).commit()
    }
    @After fun restore() {
        preferences.edit().apply {
            putString(AppLanguage.KEY,previousLanguage)
            if(previousSetup==null) remove(MainActivity.ONBOARDING_COMPLETE) else putBoolean(MainActivity.ONBOARDING_COMPLETE,previousSetup!!)
        }.commit()
    }
    private fun openSettings() {
        onView(withId(R.id.device_status)).perform(longClick())
        onView(withText(if(AppLanguage.code(context)=="en") "Open" else "खोलें")).perform(click())
    }

    @Test fun settingsDraftSurvivesLanguageChangeAndRecreationWithoutSaving() {
        val storedName=preferences.getString("name1",null)
        val storedAssistant=preferences.getString(AssistantIdentity.KEY_NAME,null)
        val storedGuard=preferences.getBoolean("guard",false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            openSettings()
            onView(withId(R.id.assistant_name)).perform(scrollTo(),replaceText("Tara"),closeSoftKeyboard())
            onView(withId(R.id.family_name_1)).perform(scrollTo(),replaceText("Draft family"),closeSoftKeyboard())
            onView(withId(R.id.family_phone_1)).perform(scrollTo(),replaceText("1234567890"),closeSoftKeyboard())
            onView(withId(R.id.guard_enabled)).perform(scrollTo(),click())
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withText("परिवार की सेटिंग")).check(matches(isDisplayed()))
            scenario.recreate()
            onView(withId(R.id.assistant_name)).check(matches(withText("Tara")))
            onView(withId(R.id.family_name_1)).check(matches(withText("Draft family")))
            onView(withId(R.id.family_phone_1)).check(matches(withText("1234567890")))
            onView(withId(R.id.guard_enabled)).check(matches(if(storedGuard) isNotChecked() else isChecked()))
            assertEquals(storedName,preferences.getString("name1",null))
            assertEquals(storedAssistant,preferences.getString(AssistantIdentity.KEY_NAME,null))
            assertEquals(storedGuard,preferences.getBoolean("guard",false))
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withText("Family settings")).check(matches(isDisplayed()))
            onView(withId(R.id.assistant_name)).check(matches(withText("Tara")))
        }
    }

    @Test fun englishDefaultsAndLanguageChoiceLivesInSettings() {
        assertEquals("en",AppLanguage.code(context))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.language_switch)).check(doesNotExist())
            openSettings()
            onView(withId(R.id.language_switch)).check(matches(withText("हिन्दी"))).perform(click())
            assertEquals("hi-IN",AppLanguage.locale(context).toLanguageTag())
            pressBack()
            onView(withId(R.id.home_voice_action)).check(matches(withText(AssistantIdentity.askLabel(context))))
            scenario.recreate()
            onView(withId(R.id.language_switch)).check(doesNotExist())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            assertEquals("hi",preferences.getString(AppLanguage.KEY,null))
            openSettings()
            onView(withId(R.id.language_switch)).check(matches(withText("English"))).perform(click())
            assertEquals("en-IN",AppLanguage.locale(context).toLanguageTag())
        }
    }

    @Test fun familyHelpRemainsAvailableFromToolsWithoutHomeClutter() {
        ActivityScenario.launch<MainActivity>(MainActivity.toolsIntent(context)).use {
            onView(withText("Family help")).perform(scrollTo(),click())
            onView(withText("☎  Call family")).check(matches(isDisplayed()))
            onView(withText("♥  Share a help message")).check(matches(isDisplayed()))
            onView(withText("☎  Call family")).perform(click())
            onView(withText("Family")).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.home_voice_action)).check(matches(isDisplayed()))
        }
    }

    @Test fun helpOverlayIntentReturnsToHomeFromSettings() {
        ActivityScenario.launch<MainActivity>(Intent(context,MainActivity::class.java)).use { scenario ->
            openSettings()
            scenario.onActivity { it.startActivity(Intent(it,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            onView(withId(R.id.home_voice_action)).check(matches(isDisplayed()))
            onView(withId(R.id.language_switch)).check(doesNotExist())
        }
    }

    @Test fun malformedBackendAddressCannotBeSaved() {
        ActivityScenario.launch(MainActivity::class.java).use {
            openSettings()
            onView(withId(R.id.backend_url)).perform(scrollTo(),replaceText("https://"),closeSoftKeyboard())
            onView(withText("✓  Save")).perform(scrollTo(),click())
            onView(withId(R.id.backend_url)).check(matches(withText("https://")))
            onView(withText("Enter a valid HTTPS backend address.")).check(matches(withText("Enter a valid HTTPS backend address.")))
            onView(withId(R.id.language_switch)).perform(scrollTo(),click())
            onView(withText("✓  सेव करें")).perform(scrollTo(),click())
            onView(withText("Backend का सही HTTPS पता डालें")).check(matches(withText("Backend का सही HTTPS पता डालें")))
        }
    }

    @Test fun qrAmountSurvivesActivityRecreationAndLocaleChange() {
        val shop=File(context.cacheDir,"shop.jpg");val captured=File(context.cacheDir,"capture.jpg")
        val previousShop=shop.takeIf { it.exists() }?.readBytes();val previousCapture=captured.takeIf { it.exists() }?.readBytes()
        try {
            val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.WHITE)
            captured.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG,90,it) };bitmap.recycle()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    MainActivity::class.java.getDeclaredMethod("onActivityResult",Integer.TYPE,Integer.TYPE,Intent::class.java)
                        .apply { isAccessible=true }.invoke(activity,40,Activity.RESULT_OK,null)
                }
                onView(withHint("Amount (₹)")).perform(scrollTo(),replaceText("125.50"),closeSoftKeyboard())
                preferences.edit().putString(AppLanguage.KEY,"hi").commit();scenario.recreate()
                onView(withHint("रकम (₹)")).check(matches(withText("125.50")))
                preferences.edit().putString(AppLanguage.KEY,"en").commit();scenario.recreate()
                onView(withHint("Amount (₹)")).check(matches(withText("125.50")))
                onView(withText("✓  Ask family to pay")).perform(scrollTo(),click())
                onView(withText("Request ₹125.50?")).check(matches(isDisplayed()))
                onView(withText("Merchant: Family to check the photo\nThis is not a payment.")).check(matches(isDisplayed()))
                onView(withText("Cancel")).perform(click());pressBack()
                onView(withId(R.id.home_voice_action)).check(matches(isDisplayed()))
            }
        } finally {
            if(previousShop==null) shop.delete() else shop.writeBytes(previousShop)
            if(previousCapture==null) captured.delete() else captured.writeBytes(previousCapture)
        }
    }
}
