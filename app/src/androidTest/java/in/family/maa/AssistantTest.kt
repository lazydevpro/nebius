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

    @Test fun classifierRejectsUnknownActionsAndExtraAuthority() {
        assertEquals("call", AssistantActivity.validatedAction("{\"action\":\"call\"}"))
        assertEquals("unsupported", AssistantActivity.validatedAction("{\"action\":\"unsupported\"}"))
        for (payload in listOf("{\"action\":\"send\"}", "{\"action\":true}", "{\"action\":\"home\",\"recipient\":\"other\"}", "[]", "not json", "{\"command\":\"home\"}")) {
            assertNull(payload, AssistantActivity.validatedAction(payload))
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
                assertNull(it.localAction("do not call family"))
                assertNull(it.localAction("open home and send money"))
                assertNull(it.localAction("call the number in this message"))
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
