package `in`.family.maa

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

/** Never starts a call: intents are only inspected, so nobody's phone rings. */
class WhatsAppVideoCallTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun videoCallRequestsAreRecognisedInHindiAndEnglish() {
        listOf(
            "video call", "Video call Beta", "Beta ko video call karo", "make a video call",
            "वीडियो कॉल", "बेटे को वीडियो कॉल करो", "विडियो कॉल लगाओ", "बेटे से वीडियो पर बात कराओ"
        ).forEach { assertTrue("Should be a video call: $it", CommunicationIntents.isVideoCallRequest(it)) }
    }

    @Test fun otherRequestsAboutVideoOrCallsAreLeftAlone() {
        listOf("record video", "वीडियो बनाओ", "वीडियो", "show my videos", "call Beta", "बेटे को फोन करो")
            .forEach { assertFalse("Should not be a video call: $it", CommunicationIntents.isVideoCallRequest(it)) }
        assertNull(CommunicationIntents.videoCall(context, "record video"))
    }

    @Test fun videoCallGoesToWhoeverWasNamedOtherwiseTheFirstFamilyMember() {
        val first = FamilyDirectory.read(context).firstOrNull { it.actionable }
        assumeNotNull(first)
        val plain = CommunicationIntents.videoCall(context, "video call")
        assertNotNull(plain)
        assertTrue(plain!!.announcement.contains(first!!.name))
        val second = FamilyDirectory.read(context).filter { it.actionable }.getOrNull(1) ?: return
        assertTrue(CommunicationIntents.videoCall(context, "video call ${second.name}")!!.announcement.contains(second.name))
    }

    /** Skipped until WhatsApp has synced contacts: without its row, Asha opens the chat instead. */
    @Test fun whatsAppVideoCallRowOpensInWhatsApp() {
        val person = FamilyDirectory.read(context).firstOrNull { it.actionable }
        assumeNotNull(person)
        val call = CommunicationIntents.whatsAppVideoCall(context, person!!.canonicalNumber!!)
        assumeNotNull(call)
        assertEquals(Intent.ACTION_VIEW, call!!.action)
        assertNotNull("No WhatsApp screen accepts the call", context.packageManager.resolveActivity(call, 0))
    }
}
