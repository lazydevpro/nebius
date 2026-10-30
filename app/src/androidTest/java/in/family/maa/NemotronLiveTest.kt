package `in`.family.maa

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream

/** Calls Nemotron on Token Factory for real, so it needs a network and a key in secrets.properties. */
class NemotronLiveTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun needsKey() = assumeTrue(NebiusIntentDispatcher.NEBIUS_API_KEY.isNotBlank())

    @Test fun understandsAHinglishRequest() {
        val result = NebiusIntentDispatcher.infer(context, "beta ko whatsapp pe call karo", listOf("Beta"))
        assertEquals("call", result.action)
        // Only the model fills in the channel: Maa's offline fallback never does.
        assertEquals("whatsapp", result.channel)
    }

    @Test fun readsAMedicinePack() {
        val reading = MedicineScanner.read(context, medicinePack())
        assertNotNull(reading)
        assertTrue(reading!!.name, reading.name.contains("amlodipine", ignoreCase = true))
        assertEquals("02/2028", reading.expiry)
    }

    private fun medicinePack(): ByteArray {
        val pack = Bitmap.createBitmap(1400, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(pack).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; isFakeBoldText = true }
        listOf("AMLODIPINE" to 130f, "Tablets IP 5 mg" to 80f, "EXP 02/2028" to 60f).forEachIndexed { row, (text, size) ->
            paint.textSize = size
            canvas.drawText(text, 120f, 280f + row * 200f, paint)
        }
        return ByteArrayOutputStream().also { pack.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
