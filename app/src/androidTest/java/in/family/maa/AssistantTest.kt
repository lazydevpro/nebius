package `in`.family.maa

import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.TextView
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.Assume.assumeFalse

/** No microphone, recognition provider, network or external communication is exercised. */
class AssistantTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("family", Context.MODE_PRIVATE)
    private var language: String? = null
    @Before fun english() { language = prefs.getString(AppLanguage.KEY, null); prefs.edit().putString(AppLanguage.KEY, "en").commit() }
    @After fun restore() { prefs.edit().putString(AppLanguage.KEY, language).commit() }

    @Test fun identityIsBoundedSingleLineAndLocalizedWithoutChangingTheAppBrand() {
        assertEquals("Asha", AssistantIdentity.normalizeName(" \n\u0000\t"))
        assertEquals("Asha Devi", AssistantIdentity.normalizeName("  Asha\n  Devi  "))
        assertEquals("आशा", AssistantIdentity.normalizeName("आशा"))
        assertEquals("ASha", AssistantIdentity.normalizeName("A\u202eSha"))
        val unicode = AssistantIdentity.normalizeName("🙂".repeat(40))
        assertEquals(24, unicode.codePointCount(0, unicode.length))
        val previous = prefs.getString(AssistantIdentity.KEY_NAME, null)
        try {
            prefs.edit().putString(AssistantIdentity.KEY_NAME, "Anya").putString(AppLanguage.KEY, "en").commit()
            assertEquals("Anya", AssistantIdentity.displayName(context))
            assertEquals("Ask Anya", AssistantIdentity.askLabel(context))
            prefs.edit().putString(AssistantIdentity.KEY_NAME, "आशा").putString(AppLanguage.KEY, "hi").commit()
            assertEquals("आशा से पूछें", AssistantIdentity.askLabel(context))
        } finally {
            prefs.edit().putString(AssistantIdentity.KEY_NAME, previous).putString(AppLanguage.KEY, "en").commit()
        }
    }

    @Test fun explicitVoiceAuthorizationIsOneUseAndNeverRestoredOrForged() {
        val authorized = AssistantActivity.voiceIntent(context)
        val replay = Intent(authorized)
        val first = AssistantActivity.VoiceEntry(authorized, restored = false)
        assertTrue(first.consume())
        assertTrue(first.navigateLocalAfterSpeech)
        assertFalse(first.consume())
        assertFalse(AssistantActivity.VoiceEntry(replay, restored = false).consume())
        val forged = Intent(context, AssistantActivity::class.java).putExtra("auto_listen", true)
            .putExtra("in.family.maa.TRUSTED_VOICE_ENTRY", "forged-token")
        assertFalse(AssistantActivity.VoiceEntry(forged, restored = false).consume())
        val restored = AssistantActivity.voiceIntent(context)
        val restoredReplay = Intent(restored)
        assertFalse(AssistantActivity.VoiceEntry(restored, restored = true).consume())
        assertFalse(AssistantActivity.VoiceEntry(restoredReplay, restored = false).consume())
        val cancelled = AssistantActivity.VoiceEntry(AssistantActivity.voiceIntent(context), restored = false)
        cancelled.cancel()
        assertFalse(cancelled.consume())
        val draftOnly = AssistantActivity.VoiceEntry(AssistantActivity.voiceIntent(context, navigateLocalAfterSpeech = false), restored = false)
        assertTrue(draftOnly.consume())
        assertFalse(draftOnly.navigateLocalAfterSpeech)
    }

    @Test fun classifierRejectsUnknownActionsAndExtraAuthority() {
        assertEquals("call", AssistantActivity.validatedAction("{\"action\":\"call\"}"))
        assertEquals("unsupported", AssistantActivity.validatedAction("{\"action\":\"unsupported\"}"))
        for (payload in listOf("{\"action\":\"send\"}", "{\"action\":true}", "{\"action\":\"home\",\"recipient\":\"other\"}", "[]", "not json", "{\"command\":\"home\"}")) {
            assertNull(payload, AssistantActivity.validatedAction(payload))
        }
    }

    @Test fun parsedIntentExtractsTargetAndSpokenResponse() {
        val json = """{"action":"call","target":"Rahul","target_type":"name","channel":"phone","spoken_response":"Calling Rahul"}"""
        val parsed = AssistantActivity.validatedIntent(json)
        assertNotNull(parsed)
        assertEquals("call", parsed!!.action)
        assertEquals("Rahul", parsed.target)
        assertEquals("name", parsed.targetType)
        assertEquals("phone", parsed.channel)
        assertEquals("Calling Rahul", parsed.spokenResponse)

        val minimal = AssistantActivity.validatedIntent("""{"action":"home"}""")
        assertNotNull(minimal)
        assertEquals("home", minimal!!.action)
        assertNull(minimal.target)

        assertNull(AssistantActivity.validatedIntent("""{"action":"invalid_action"}"""))
        assertNull(AssistantActivity.validatedIntent("not json"))
    }

    @Test fun extractLocalCallParsesEnglishAndHindiCallPhrases() {
        ActivityScenario.launch(AssistantActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("rahul", activity.extractLocalCall("call rahul"))
                assertEquals("rahul", activity.extractLocalCall("Call Rahul!"))
                assertEquals("mom", activity.extractLocalCall("dial mom"))
                assertEquals("my son", activity.extractLocalCall("phone my son"))
                assertEquals("राहुल", activity.extractLocalCall("राहुल को कॉल करो"))
                assertEquals("अमित", activity.extractLocalCall("अमित को फोन लगाओ"))
                assertEquals("रोहन", activity.extractLocalCall("कॉल रोहन"))
                assertEquals("रोहन", activity.extractLocalCall("फोन रोहन"))
                assertNull(activity.extractLocalCall("home"))
                assertNull(activity.extractLocalCall("open alarms"))
                assertNull(activity.extractLocalCall("all apps"))
            }
        }
    }

    @Test fun familyDirectoryFindMatchResolvesNamesAliasesAndNumbers() {
        val prev1Name = prefs.getString("name1", null)
        val prev1Phone = prefs.getString("phone1", null)
        val prev2Name = prefs.getString("name2", null)
        val prev2Phone = prefs.getString("phone2", null)
        try {
            prefs.edit()
                .putString("name1", "Rahul")
                .putString("phone1", "+919876543210")
                .putString("name2", "Priya")
                .putString("phone2", "+919876543211")
                .commit()

            // Exact and case-insensitive
            val m1 = FamilyDirectory.findMatch(context, "rahul")
            assertNotNull(m1)
            assertEquals("Rahul", m1!!.name)
            assertEquals("+919876543210", m1.canonicalNumber)

            val m2 = FamilyDirectory.findMatch(context, "PRIYA")
            assertNotNull(m2)
            assertEquals("Priya", m2!!.name)

            // Slot numbers
            val slot1 = FamilyDirectory.findMatch(context, "first contact")
            assertNotNull(slot1)
            assertEquals("Rahul", slot1!!.name)

            val slot2 = FamilyDirectory.findMatch(context, "दूसरा")
            assertNotNull(slot2)
            assertEquals("Priya", slot2!!.name)

            // Direct phone number
            val direct = FamilyDirectory.findMatch(context, "9876543219")
            assertNotNull(direct)
            assertEquals("+919876543219", direct!!.canonicalNumber)

            // Unknown returns null
            assertNull(FamilyDirectory.findMatch(context, "Unknown Person"))
        } finally {
            prefs.edit().apply {
                if (prev1Name != null) putString("name1", prev1Name) else remove("name1")
                if (prev1Phone != null) putString("phone1", prev1Phone) else remove("phone1")
                if (prev2Name != null) putString("name2", prev2Name) else remove("name2")
                if (prev2Phone != null) putString("phone2", prev2Phone) else remove("phone2")
            }.commit()
        }
    }

    @Test fun onlyUncredentialedHttpsEndpointsAreAccepted() {
        assertEquals("https://example.test/base", AssistantActivity.validatedEndpoint(" https://example.test/base/ "))
        for (url in listOf("http://example.test", "https://name:password@example.test", "https://example.test?token=secret", "https://example.test/#fragment", "javascript:alert(1)", "https:///missing", "")) {
            assertNull(url, AssistantActivity.validatedEndpoint(url))
        }
    }

    @Test fun localRoutingRejectsCompoundAndNegatedRequests() {
        ActivityScenario.launch(AssistantActivity::class.java).use { scenario ->
            scenario.onActivity {
                assertEquals("family", it.localAction("call family"))
                assertEquals("apps", it.localAction("सभी ऐप"))
                assertEquals("media", it.localAction("व्हाट्सऐप"))
                assertEquals("home", it.localAction("  HOME!  "))
                assertEquals("notifications", it.localAction("open notifications"))
                assertEquals("notifications", it.localAction("सूचनाएँ"))
                assertNull(it.localAction("do not call family"))
                assertNull(it.localAction("open home and send money"))
                assertNull(it.localAction("call the number in this message"))
                assertEquals("family", it.spokenNavigation("call family", true))
                assertEquals("qr", it.spokenNavigation("payment", true))
                assertNull(it.spokenNavigation("call family", false))
                assertNull(it.spokenNavigation("send a message to someone", true))
                assertNull(it.spokenNavigation("call the number in this message", true))
            }
        }
    }

    @Test fun entryExtrasCannotStartListeningOrSubmitAndDraftSurvivesBackground() {
        val entry = Intent(context, AssistantActivity::class.java).putExtra("auto_listen", true)
            .putExtra("text", "call family").putExtra("action", "call")
        ActivityScenario.launch<AssistantActivity>(entry).use { scenario ->
            scenario.onActivity { activity ->
                val views = descendants(activity.window.decorView)
                assertTrue(views.filterIsInstance<TextView>().any { it.text.toString() == "Ready when you are" && it.visibility == View.GONE })
                val edit = views.filterIsInstance<EditText>().single()
                assertEquals("", edit.text.toString())
                edit.setText("Draft kept locally")
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.onActivity {
                assertEquals("Draft kept locally", descendants(it.window.decorView).filterIsInstance<EditText>().single().text.toString())
            }
        }
    }
    @Test fun stoppingRequestedOverlayUpdatesButtonAndStaleCallbacksCannotRestartIt() {
        // Exercise the real state/command path without starting a window service or changing permission.
        // Preserve an explicitly enabled helper instead of disrupting a user's existing session.
        assumeFalse("An existing requested helper must be preserved", HelpOverlayService.isRequested(context))
        val overlayPrefs = context.getSharedPreferences("maa_assistant_overlay", Context.MODE_PRIVATE)
        val hadEnabled = overlayPrefs.contains("enabled")
        val previousEnabled = overlayPrefs.getBoolean("enabled", false)
        val hadGeneration = overlayPrefs.contains("request_generation")
        val previousGeneration = overlayPrefs.getLong("request_generation", 0L)
        try {
            ActivityScenario.launch(AssistantActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val pending = HelpOverlayService.prepareStart(activity)
                    val token = HelpOverlayService.generation(activity)
                    assertTrue(HelpOverlayService.acceptsStart(activity, pending))
                    assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>()
                        .any { it.text.toString() == "Stop floating helper" })
                    val renderedStop = activity.overlayCommand
                    renderedStop()
                    val stoppedGeneration = HelpOverlayService.generation(activity)
                    assertFalse(HelpOverlayService.isRequested(activity))
                    assertFalse(HelpOverlayService.isActive(activity))
                    assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>()
                        .any { it.text.toString() == "Turn on floating helper" })
                    // A late service start/attach and a second queued invocation of the old Stop are inert.
                    assertFalse(HelpOverlayService.acceptsStart(activity, pending))
                    assertFalse(HelpOverlayService.reportAttachment(activity, token, true))
                    renderedStop()
                    assertFalse(HelpOverlayService.isRequested(activity))
                    assertEquals(stoppedGeneration, HelpOverlayService.generation(activity))

                    // Notification/overlay Close use this same published stop path while the activity stays visible.
                    HelpOverlayService.prepareStart(activity)
                    HelpOverlayService.stop(activity)
                    assertTrue(descendants(activity.window.decorView).filterIsInstance<TextView>()
                        .any { it.text.toString() == "Turn on floating helper" })
                }
            }
        } finally {
            HelpOverlayService.stop(context)
            overlayPrefs.edit().apply {
                if (hadEnabled) putBoolean("enabled", previousEnabled) else remove("enabled")
                if (hadGeneration) putLong("request_generation", previousGeneration) else remove("request_generation")
            }.commit()
        }
    }
    private fun descendants(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
}
