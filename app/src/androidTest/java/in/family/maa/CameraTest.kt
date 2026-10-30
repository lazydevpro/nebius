package `in`.family.maa

import android.Manifest
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Drives the real camera, so the screen must be unlocked. Anything a test captures is deleted afterwards. */
class CameraTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val strings get() = AppLanguage.localizedContext(context)
    private val startedAt = System.currentTimeMillis() / 1000 - 1
    private val photos = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    private val videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    @Before fun allowCamera() {
        listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
            .forEach { instrumentation.uiAutomation.grantRuntimePermission(context.packageName, it) }
    }

    @After fun deleteCaptures() {
        (captures(photos) + captures(videos)).forEach { runCatching { context.contentResolver.delete(it.first, null, null) } }
    }

    /** Rows this test run created, with their size in bytes. Unfinished rows stay hidden from this query. */
    private fun captures(collection: Uri): List<Pair<Uri, Long>> {
        val found = mutableListOf<Pair<Uri, Long>>()
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DISPLAY_NAME),
            "${MediaStore.MediaColumns.DATE_ADDED} >= ?",
            arrayOf(startedAt.toString()),
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                // SQL LIKE ignores case, so names are matched here to leave every other image alone.
                if (cursor.getString(2).orEmpty().startsWith("Maa_"))
                    found.add(ContentUris.withAppendedId(collection, cursor.getLong(0)) to cursor.getLong(1))
            }
        }
        return found
    }

    /** Android lists a finished file before its index shows the size, so this waits for both. */
    private fun awaitCapture(collection: Uri): List<Pair<Uri, Long>> {
        var found = emptyList<Pair<Uri, Long>>()
        repeat(60) {
            found = captures(collection)
            if (found.isNotEmpty() && found.all { it.second > 0 }) return found
            Thread.sleep(250)
        }
        return found
    }

    private fun tapOnceCameraIsLive(label: Int) {
        Thread.sleep(2500)
        onView(withText(strings.getString(label))).perform(click())
    }

    @Test fun screenShowsBothCaptureChoicesAndPhotosWithoutCrashing() {
        ActivityScenario.launch(CameraActivity::class.java).use {
            onView(withText(strings.getString(R.string.camera_photo))).check(matches(isDisplayed()))
            onView(withText(strings.getString(R.string.camera_video))).check(matches(isDisplayed()))
            onView(withText("🖼️  " + strings.getString(R.string.home_gallery))).check(matches(isDisplayed()))
        }
    }

    @Test fun oneTapSavesAPhotoInsideMaa() {
        ActivityScenario.launch(CameraActivity::class.java).use { scenario ->
            tapOnceCameraIsLive(R.string.camera_photo)
            val saved = awaitCapture(photos)
            assertEquals(1, saved.size)
            assertTrue("Photo file is empty", saved.single().second > 0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(saved.single().first).use { BitmapFactory.decodeStream(it, null, bounds) }
            assertTrue("Photo is not a readable image", bounds.outWidth > 0 && bounds.outHeight > 0)
            val tag = context.contentResolver.openInputStream(saved.single().first)!!.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)
            }
            Log.i("CameraTest", "photo ${bounds.outWidth}x${bounds.outHeight} exif=$tag")
            onView(withText(strings.getString(R.string.camera_saved_photo))).check(matches(isDisplayed()))

            // The Photos screen must apply the rotation tag, or portrait photos show sideways.
            val shown = ImageView(context)
            scenario.onActivity { activity ->
                activity.findViewById<ViewGroup>(R.id.communication_content).addView(shown, 0)
                PhotoThumbnails.load(shown, saved.single().first, 260)
            }
            repeat(40) { if (shown.drawable == null) Thread.sleep(250) }
            val image = shown.drawable
            assertNotNull("Photo never loaded", image)
            val storedTall = bounds.outHeight > bounds.outWidth
            val turnedSideways = tag == ExifInterface.ORIENTATION_ROTATE_90 || tag == ExifInterface.ORIENTATION_ROTATE_270
            assertEquals(storedTall != turnedSideways, image.intrinsicHeight > image.intrinsicWidth)
        }
    }

    @Test fun tapRightAfterOpeningStillTakesThePhoto() {
        ActivityScenario.launch(CameraActivity::class.java).use {
            onView(withText(strings.getString(R.string.camera_photo))).perform(click())
            assertEquals(1, awaitCapture(photos).size)
        }
    }

    @Test fun newestPhotoIsTheFirstCardOnThePhotosScreen() {
        val saved = ActivityScenario.launch(CameraActivity::class.java).use {
            tapOnceCameraIsLive(R.string.camera_photo)
            awaitCapture(photos).single().first
        }
        ActivityScenario.launch(GalleryActivity::class.java).use { gallery ->
            lateinit var firstImage: ImageView
            gallery.onActivity { activity ->
                val screen = activity.findViewById<ViewGroup>(R.id.communication_content)
                val cards = (0 until screen.childCount).map { screen.getChildAt(it) }.filterIsInstance<FrameLayout>()
                firstImage = cards.first().getChildAt(0) as ImageView
            }
            assertEquals(saved.toString(), firstImage.tag)
            repeat(40) { if (firstImage.drawable == null) Thread.sleep(250) }
            assertNotNull("The newest photo never appeared", firstImage.drawable)
        }
    }

    @Test fun recordThenStopSavesAVideoInsideMaa() {
        ActivityScenario.launch(CameraActivity::class.java).use {
            tapOnceCameraIsLive(R.string.camera_video)
            Thread.sleep(3000)
            onView(withText(strings.getString(R.string.camera_stop))).perform(click())
            val saved = awaitCapture(videos)
            assertEquals(1, saved.size)
            assertTrue("Video file is empty", saved.single().second > 0)
            onView(withText(strings.getString(R.string.camera_photo))).check(matches(isDisplayed()))
        }
    }
}
