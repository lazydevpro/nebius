package `in`.family.maa

import android.content.Context
import android.content.SharedPreferences
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** No test confirms an external call/message handoff. Fixture keys are restored after each test. */
class CommunicationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("family", Context.MODE_PRIVATE)
    private val drafts get() = context.getSharedPreferences("communication_drafts", Context.MODE_PRIVATE)
    private val familyKeys = listOf("name1", "name2", "name3", "phone1", "phone2", "phone3", "photo1", "photo2", "photo3", "language")
    private val draftKeys = listOf("sms:+919876543210", "whatsapp:+919876543210", "sms:+919876543211", "whatsapp:+919876543211")
    private var familyBefore = emptyMap<String, String?>()
    private var draftsBefore = emptyMap<String, String?>()
    @Before fun setUp() {
        familyBefore = familyKeys.associateWith { prefs.getString(it, null) }
        draftsBefore = draftKeys.associateWith { drafts.getString(it, null) }
        prefs.edit().putString("name1", "Test recipient").putString("phone1", "+919876543210")
            .putString("phone2", "").putString("phone3", "").putString("photo1", "")
            .putString("photo2", "").putString("photo3", "").putString("language", "en").commit()
        val editor = drafts.edit(); draftKeys.forEach { editor.remove(it) }; editor.commit()
    }
    @After fun restore() {
        restoreKeys(prefs, familyBefore); restoreKeys(drafts, draftsBefore)
    }
    private fun restoreKeys(store: SharedPreferences, values: Map<String, String?>) {
        val edit = store.edit()
        values.forEach { (key, value) -> if (value == null) edit.remove(key) else edit.putString(key, value) }
        edit.commit()
    }
    @Test fun canonicalPhoneNumbersRejectDialSequencesAndAmbiguousInputs() {
        assertEquals("+919876543210", FamilyDirectory.canonicalNumber("98765 43210"))
        assertEquals("+919876543210", FamilyDirectory.canonicalNumber("+९१ ९८७६५४३२१०"))
        assertEquals("+442079460958", FamilyDirectory.canonicalNumber("+44 (20) 7946-0958"))
        listOf("", "123", "+91", "*123#", "9876543210,123", "9876543210;123", "tel:+919876543210",
            "9876543210 ext 5", "+91\n9876543210", "++919876543210", "9876543210?x=1").forEach {
            assertNull("Must reject $it", FamilyDirectory.canonicalNumber(it))
        }
    }
    @Test fun duplicateFamilyNumbersAreBlockedAfterCanonicalization() {
        prefs.edit().putString("phone2", "98765 43210").commit()
        val family = FamilyDirectory.read(context)
        assertEquals(FamilyDirectory.Issue.DUPLICATE_NUMBER, family[0].issue)
        assertEquals(FamilyDirectory.Issue.DUPLICATE_NUMBER, family[1].issue)
        assertFalse(family[0].actionable)
    }
    @Test fun pendingFamilyIdentityIsInvalidatedWhenNumberOrNameChanges() {
        val recipient = FamilyDirectory.read(context).first()
        assertTrue(FamilyDirectory.stillMatches(context, recipient))
        prefs.edit().putString("name1", "Changed name").commit()
        assertFalse(FamilyDirectory.stillMatches(context, recipient))
        prefs.edit().putString("name1", "Test recipient").putString("phone1", "+919876543211").commit()
        assertFalse(FamilyDirectory.stillMatches(context, recipient))
    }
    @Test fun draftsAreScopedToRecipientAndChannel() {
        CommunicationDrafts.save(context, "+919876543210", "sms", "SMS draft")
        CommunicationDrafts.save(context, "+919876543210", "whatsapp", "WhatsApp draft")
        assertEquals("SMS draft", CommunicationDrafts.read(context, "+919876543210", "sms"))
        assertEquals("WhatsApp draft", CommunicationDrafts.read(context, "+919876543210", "whatsapp"))
        assertEquals("", CommunicationDrafts.read(context, "+919876543211", "sms"))
        CommunicationDrafts.clear(context, "+919876543210", "sms")
        assertEquals("WhatsApp draft", CommunicationDrafts.read(context, "+919876543210", "whatsapp"))
    }
    @Test fun communicationIntentsRemainUnsentAndAddressOneRecipient() {
        val dial = CommunicationIntents.dial("+919876543210")
        assertEquals(Intent.ACTION_DIAL, dial.action)
        assertEquals("tel", dial.data?.scheme)
        assertEquals("+919876543210", dial.data?.schemeSpecificPart)
        val sms = CommunicationIntents.sms(context, "+919876543210", "A & B?")
        assertEquals(Intent.ACTION_SENDTO, sms.action)
        assertEquals("smsto", sms.data?.scheme)
        assertEquals("+919876543210", sms.data?.schemeSpecificPart)
        assertEquals("A & B?", sms.getStringExtra("sms_body"))
        val whatsapp = CommunicationIntents.whatsApp("+919876543210", "A & B?")
        assertEquals("com.whatsapp", whatsapp.`package`)
        assertEquals("/919876543210", whatsapp.data?.path)
        assertEquals("A & B?", whatsapp.data?.getQueryParameter("text"))
    }
    @Test fun messageDraftSurvivesRecreationAndReviewCanBeCancelled() {
        ActivityScenario.launch<CommunicationActivity>(CommunicationActivity.intent(context, 1)
            .putExtra(CommunicationActivity.EXTRA_CHANNEL, "sms")).use { scenario ->
            onView(withId(R.id.communication_draft)).perform(replaceText("Unsent test draft"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.communication_draft)).check(matches(withText("Unsent test draft")))
            onView(withId(R.id.communication_review)).perform(scrollTo(), click())
            onView(withText("Check this message")).check(matches(isDisplayed()))
            onView(withText("Cancel")).perform(click())
            onView(withId(R.id.communication_draft)).check(matches(withText("Unsent test draft")))
            assertEquals("Unsent test draft", CommunicationDrafts.read(context, "+919876543210", "sms"))
        }
    }
    @Test fun recipientEditWhileConfirmationIsOpenPreventsHandoff() {
        ActivityScenario.launch<CommunicationActivity>(CommunicationActivity.intent(context, 1)).use {
            onView(withId(R.id.communication_call)).perform(scrollTo(), click())
            prefs.edit().putString("phone1", "+919876543211").commit()
            // This button must now invalidate the action; it must not launch the external phone app.
            onView(withText("Open app")).perform(click())
            onView(withId(R.id.communication_recipient_number)).check(matches(withText("+919876543211")))
            onView(withText("This contact has changed. Please check the person and number again.")).check(matches(isDisplayed()))
        }
    }
    @Test fun keypadRestoresUnsentNumberAndRequiresReview() {
        ActivityScenario.launch(PhoneDialerActivity::class.java).use { scenario ->
            onView(withId(R.id.communication_dial_number)).perform(replaceText("9876543210"), closeSoftKeyboard())
            scenario.recreate()
            onView(withId(R.id.communication_dial_number)).check(matches(withText("9876543210")))
            onView(withId(R.id.communication_dial_review)).perform(scrollTo(), click())
            onView(withText("Check this call")).check(matches(isDisplayed()))
            onView(withText("Cancel")).perform(click())
            onView(withId(R.id.communication_dial_number)).check(matches(withText("9876543210")))
        }
    }
}
