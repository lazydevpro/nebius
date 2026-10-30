package `in`.family.maa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.provider.MediaStore
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Calendar

class MedicineScannerTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val sugar = MedicineManager.MedicineItem("med_night", "शुगर की दवा", "Sugar Medicine", 20, 30, "रात के खाने के बाद", "After dinner")
    private val october2026 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 30) }

    private fun reply(saved: String = "", expiry: String = "04/2028", timing: String = "not stated") =
        """{"medicine_name":"Metformin","strength":"500 mg","used_for":"diabetes","used_for_hindi":"मधुमेह","timing_on_label":"$timing","expiry":"$expiry","readable":true,"saved_medicine_id":"$saved"}"""

    @Test fun scanRequestsAreRecognisedInHindiAndEnglish() {
        listOf(
            "scan medicine", "scan this tablet", "what medicine is this", "what is this tablet",
            "ye kaun si dawa hai", "is dawa ko scan karo", "ये कौन सी दवा है", "यह दवा कब लेनी है",
            "इस गोली को स्कैन करो", "दवा पहचानो"
        ).forEach { assertTrue("Should scan: $it", MedicineScanner.isScanRequest(it)) }
    }

    @Test fun scheduleQuestionsAndDoseConfirmationsAreLeftToTheReminders() {
        listOf("मेरी दवा का समय", "medicine time", "ये दवा ले ली", "ye dawa le li", "open camera", "take photo")
            .forEach { assertFalse("Should not scan: $it", MedicineScanner.isScanRequest(it)) }
    }

    @Test fun savedMedicineGivesTheFamilyTimeNotAnAiOne() {
        val reading = MedicineScanner.parse(context, reply(saved = "med_night"), listOf(sugar), october2026)!!
        assertEquals(sugar, reading.saved)
        assertTrue(reading.spoken(context).contains(sugar.formattedTime(context)))
    }

    @Test fun anIdOutsideTheSavedListIsIgnored() {
        assertNull(MedicineScanner.parse(context, reply(saved = "made_up"), listOf(sugar), october2026)!!.saved)
    }

    @Test fun unknownMedicineHasNoTimeAndPointsToDoctorOrFamily() {
        val reading = MedicineScanner.parse(context, reply(), listOf(sugar), october2026)!!
        val spoken = reading.spoken(context)
        assertTrue(spoken.contains(AppLanguage.localizedContext(context).getString(R.string.scan_no_time)))
        assertFalse(Regex("\\d{1,2}:\\d{2}").containsMatchIn(spoken))
    }

    @Test fun expiredPackIsNeverGivenATime() {
        val reading = MedicineScanner.parse(context, reply(saved = "med_night", expiry = "09/2026"), listOf(sugar), october2026)!!
        assertTrue(reading.expired)
        assertFalse(reading.spoken(context).contains(sugar.formattedTime(context)))
    }

    @Test fun expiryCountsThroughThePrintedMonth() {
        assertFalse(MedicineScanner.isExpired("10/2026", october2026))
        assertFalse(MedicineScanner.isExpired("11/26", october2026))
        assertTrue(MedicineScanner.isExpired("09/2026", october2026))
        assertFalse(MedicineScanner.isExpired("not visible", october2026))
    }

    @Test fun unreadablePhotoAndBrokenRepliesGiveNoReading() {
        assertNull(MedicineScanner.parse(context, """{"medicine_name":"","readable":false}""", listOf(sugar)))
        assertNull(MedicineScanner.parse(context, "I cannot help with that", listOf(sugar)))
    }

    /** Uses the real camera and Nebius, so whatever the camera sees is sent to be read, then forgotten. */
    @Test fun tappingScanAnswersWithoutSavingAPhoto() {
        val before = maaPhotoCount()
        ActivityScenario.launch(MedicineScanActivity::class.java).use { screen ->
            Thread.sleep(2500)
            val button = AppLanguage.localizedContext(context).getString(R.string.scan_button)
            onView(allOf(withText(button), isDescendantOfA(withId(R.id.communication_content)))).perform(click())
            Thread.sleep(14000)
            assertEquals(Lifecycle.State.RESUMED, screen.state)
        }
        assertEquals(before, maaPhotoCount())
    }

    private fun maaPhotoCount() = context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Images.Media._ID),
        "${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} = ?", arrayOf("Maa"), null
    )?.use { it.count } ?: 0

    /** Sends one made-up label to Nebius. */
    @Test fun nebiusReadsAMedicineLabel() {
        val label = Bitmap.createBitmap(900, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(label).apply { drawColor(Color.rgb(176, 180, 184)) }
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(24, 52, 110); textSize = 52f; isFakeBoldText = true }
        canvas.drawText("METFORMIN HYDROCHLORIDE", 40f, 120f, ink)
        canvas.drawText("TABLETS IP 500 mg", 40f, 190f, ink)
        ink.textSize = 30f
        canvas.drawText("Dosage: As directed by the Physician", 40f, 280f, ink)
        canvas.drawText("Exp. 04/2028", 40f, 340f, ink)
        val jpeg = ByteArrayOutputStream().also { label.compress(Bitmap.CompressFormat.JPEG, 70, it) }.toByteArray()
        val reading = MedicineScanner.read(context, jpeg)
        assertNotNull(reading)
        assertTrue(reading!!.name, reading.name.contains("metformin", ignoreCase = true))
        assertEquals("04/2028", reading.expiry)
    }
}
