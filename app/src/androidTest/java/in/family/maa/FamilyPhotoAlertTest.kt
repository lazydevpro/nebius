package `in`.family.maa

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/** Uses a made-up WhatsApp photo, removed afterwards. Real alert state is put back after each test. */
class FamilyPhotoAlertTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val strings get() = AppLanguage.localizedContext(context)
    private val alerts get() = context.getSharedPreferences("proactive_asha", Context.MODE_PRIVATE)
    private var alertsBefore = emptyMap<String, Any?>()
    private var fakePhoto: Uri? = null

    @Before fun startClear() {
        alertsBefore = alerts.all.toMap()
        alerts.edit().clear().commit()
    }

    @After fun restore() {
        fakePhoto?.let { context.contentResolver.delete(it, null, null) }
        val editor = alerts.edit().clear()
        alertsBefore.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
            }
        }
        editor.commit()
    }

    private fun saveFakeWhatsAppPhoto(): Uri {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "MaaTest_${System.currentTimeMillis()}.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WhatsApp Images")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        fakePhoto = uri
        val picture = Bitmap.createBitmap(64, 48, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        context.contentResolver.openOutputStream(uri)!!.use { picture.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }

    @Test fun familyGroupPhotoRaisesAlertThatOpensThePhotosDirectly() {
        ProactiveAsha.onWhatsAppNotification(context, "Family", "Rahul: 📷 Photo")
        assertTrue(ProactiveAsha.hasPendingPhoto(context))
        val alert = ProactiveAsha.getPendingWhatsAppAlert(context)!!
        assertEquals("Family", alert.sender)
    }

    @Test fun familyPhotoTurnsHomeYellowAndTapOpensTheNewPhotos() {
        ProactiveAsha.onWhatsAppNotification(context, "Family", "📷 Photo")
        assumeTrue("A due medicine or missed call keeps Asha red", ProactiveAsha.showsPhotoAlert(context))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val photosOpened = instrumentation.addMonitor(GalleryActivity::class.java.name, null, false)
        ActivityScenario.launch(MainActivity::class.java).use { home ->
            lateinit var button: Button
            home.onActivity { activity -> button = activity.findViewById(R.id.home_voice_action) }
            Thread.sleep(2500)
            instrumentation.runOnMainSync {
                assertEquals(MaaDesign.photoYellow, (button.background as GradientDrawable).color?.defaultColor)
                assertEquals(MaaDesign.ink, button.currentTextColor)
            }
            onView(withId(R.id.home_voice_action)).perform(click())
            val photos = instrumentation.waitForMonitorWithTimeout(photosOpened, 8000)
            assertNotNull("Tapping yellow Asha must open the new photos", photos)
            repeat(20) { if (ProactiveAsha.hasPendingPhoto(context)) Thread.sleep(250) }
            assertFalse("Seeing the photos must clear the alert", ProactiveAsha.hasPendingPhoto(context))
            photos.finish()
        }
        instrumentation.removeMonitor(photosOpened)
    }

    @Test fun photosFromOutsideTheFamilyStayQuiet() {
        ProactiveAsha.onWhatsAppNotification(context, "Cousin Shop Offers", "📷 Photo")
        assertFalse(ProactiveAsha.hasPendingWhatsApp(context))
    }

    @Test fun chatThatMerelyMentionsAPhotoIsNotAPhotoAlert() {
        ProactiveAsha.onWhatsAppNotification(context, "Family", "Send me that photo please")
        assertFalse(ProactiveAsha.hasPendingPhoto(context))
    }

    @Test fun laterChatDoesNotReplaceAWaitingPhoto() {
        ProactiveAsha.onWhatsAppNotification(context, "Family", "📷 Photo", 1_000L)
        val photoTime = ProactiveAsha.getPendingWhatsAppAlert(context)!!.timestamp
        Thread.sleep(5)
        ProactiveAsha.onWhatsAppNotification(context, "Family", "Nice picture!", 2_000L)
        assertTrue(ProactiveAsha.hasPendingPhoto(context))
        assertEquals(photoTime, ProactiveAsha.getPendingWhatsAppAlert(context)!!.timestamp)
    }

    @Test fun repostOfAPhotoAlreadySeenDoesNotAlertAgain() {
        ProactiveAsha.onWhatsAppNotification(context, "Family", "📷 Photo", 1_000L)
        ProactiveAsha.markPhotosViewed(context)
        ProactiveAsha.onWhatsAppNotification(context, "Family", "📷 Photo", 1_000L)
        assertFalse(ProactiveAsha.hasPendingPhoto(context))
    }

    @Test fun newPhotosScreenShowsOnlyTheArrivedPhoto() {
        val since = System.currentTimeMillis()
        val arrived = saveFakeWhatsAppPhoto()
        ActivityScenario.launch<GalleryActivity>(GalleryActivity.newPhotosIntent(context, since, "")).use { gallery ->
            lateinit var first: ImageView
            gallery.onActivity { activity ->
                val screen = activity.findViewById<ViewGroup>(R.id.communication_content)
                val cards = (0 until screen.childCount).map { screen.getChildAt(it) }.filterIsInstance<FrameLayout>()
                assertEquals(1, cards.size)
                first = cards.single().getChildAt(0) as ImageView
            }
            assertEquals(arrived.toString(), first.tag)
            repeat(40) { if (first.drawable == null) Thread.sleep(250) }
            assertNotNull("The arrived photo never appeared", first.drawable)
        }
    }

    @Test fun newPhotosScreenWaitsThenShowsAPhotoThatArrivesLate() {
        // The screen looks back 60 seconds, so this counts only photos saved after it opens.
        val since = System.currentTimeMillis() + 61_000L
        ActivityScenario.launch<GalleryActivity>(GalleryActivity.newPhotosIntent(context, since, "")).use { gallery ->
            onView(withText(strings.getString(R.string.gallery_downloading))).check(matches(isDisplayed()))
            Thread.sleep(2500)
            val arrived = saveFakeWhatsAppPhoto()
            var shown: String? = null
            repeat(40) {
                if (shown == null) {
                    Thread.sleep(250)
                    gallery.onActivity { activity ->
                        val screen = activity.findViewById<ViewGroup>(R.id.communication_content)
                        val card = (0 until screen.childCount).map { screen.getChildAt(it) }.filterIsInstance<FrameLayout>().firstOrNull()
                        shown = (card?.getChildAt(0) as? ImageView)?.tag as? String
                    }
                }
            }
            assertEquals(arrived.toString(), shown)
        }
    }
}
