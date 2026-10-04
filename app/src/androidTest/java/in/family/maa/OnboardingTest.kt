package `in`.family.maa

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class OnboardingTest {
    @Test fun firstRunDefaultsToEnglishAndNameDraftSurvivesRotation() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("family",Context.MODE_PRIVATE)
        val oldLanguage=prefs.getString(AppLanguage.KEY,null)
        val oldName=prefs.getString(AssistantIdentity.KEY_NAME,null)
        val oldComplete=if(prefs.contains(MainActivity.ONBOARDING_COMPLETE)) prefs.getBoolean(MainActivity.ONBOARDING_COMPLETE,false) else null
        try {
            prefs.edit().remove(AppLanguage.KEY).remove(AssistantIdentity.KEY_NAME).putBoolean(MainActivity.ONBOARDING_COMPLETE,false).commit()
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                onView(withId(R.id.setup_english)).check(matches(isChecked()))
                onView(withId(R.id.assistant_name)).check(matches(withText("Asha")))
                onView(withId(R.id.assistant_name)).perform(replaceText("Tara"),closeSoftKeyboard())
                onView(withId(R.id.setup_hindi)).perform(click())
                scenario.recreate()
                onView(withId(R.id.setup_hindi)).check(matches(isChecked()))
                onView(withId(R.id.assistant_name)).check(matches(withText("Tara")))
                assertNull(prefs.getString(AssistantIdentity.KEY_NAME,null))
                onView(withId(R.id.setup_start)).perform(scrollTo(),click())
                onView(withId(R.id.home_voice_action)).check(matches(withText("Tara से पूछें")))
                onView(withId(R.id.language_switch)).check(doesNotExist())
                assertEquals("hi",AppLanguage.code(context))
                assertTrue(prefs.getBoolean(MainActivity.ONBOARDING_COMPLETE,false))
            }
            ActivityScenario.launch(MainActivity::class.java).use {
                onView(withId(R.id.home_voice_action)).check(matches(withText("Tara से पूछें")))
                onView(withId(R.id.setup_start)).check(doesNotExist())
            }
        } finally {
            prefs.edit().apply {
                putString(AppLanguage.KEY,oldLanguage);putString(AssistantIdentity.KEY_NAME,oldName)
                if(oldComplete==null) remove(MainActivity.ONBOARDING_COMPLETE) else putBoolean(MainActivity.ONBOARDING_COMPLETE,oldComplete)
            }.commit()
        }
    }
}
