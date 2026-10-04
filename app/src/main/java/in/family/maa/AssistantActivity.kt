package `in`.family.maa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputFilter
import android.text.InputType
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Explicit foreground requests only. Routes navigate; they never send, dial or execute payments. */
class AssistantActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("family", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private var job: Future<*>? = null
    @Volatile private var connection: HttpURLConnection? = null
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var generation = 0
    @Volatile private var resumed = false
    private var busy = false
    private var dialog: AlertDialog? = null
    private var voiceEntry: VoiceEntry? = null
    private var trustedSpeechNavigation = false
    private lateinit var editor: EditText
    private lateinit var state: TextView
    private lateinit var heading: TextView
    private lateinit var ask: Button
    private lateinit var speak: Button
    private lateinit var stop: Button
    private lateinit var cloud: Button
    private lateinit var overlay: Button
    internal var overlayCommand: () -> Unit = {}
        private set
    private val overlayStateListener: () -> Unit = { if (resumed && !isDestroyed) updateControls() }
    private fun tr(id: Int): String = when (id) {
        R.string.assistant_title -> AssistantIdentity.displayName(this)
        R.string.assistant_ask -> AssistantIdentity.askLabel(this)
        else -> AppLanguage.text(this, id)
    }
    private fun dp(value: Int): Int = MaaDesign.dp(this, value)
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.localizedContext(base)) }

    override fun onCreate(saved: Bundle?) {
        super.onCreate(saved)
        voiceEntry = VoiceEntry(intent, restored = saved != null)
        window.statusBarColor = MaaDesign.canvas; window.navigationBarColor = MaaDesign.canvas
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(32)) }
        fun add(view: View, gap: Int = 12) { content.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(gap) }) }
        fun action(id: Int, primary: Boolean = false, click: () -> Unit): Button = MaaDesign.action(this, tr(id), primary, click)
        add(action(R.string.platform_back) { finish() }, 0)
        heading = PlatformUi.text(this, 34f, MaaDesign.green, true).apply { text = tr(R.string.assistant_title); if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }
        add(heading)
        state = PlatformUi.text(this, 22f, MaaDesign.green, true).apply {
            text = tr(R.string.assistant_ready); accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    visibility = if (s.isNullOrBlank() || s.toString() == tr(R.string.assistant_ready)) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        add(state, 20)
        editor = EditText(this).apply {
            hint = tr(R.string.assistant_text_hint); textSize = 23f; minLines = 2; maxLines = 6
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(1001)); setText(saved?.getString("draft").orEmpty())
            setTextColor(MaaDesign.ink); setPadding(dp(16), dp(16), dp(16), dp(16))
            background = MaaDesign.shape(this@AssistantActivity, Color.WHITE, 20, true)
        }
        add(editor)
        ask = action(R.string.assistant_ask, true) { submit() }; add(ask)
        speak = action(R.string.assistant_speak) { requestSpeech() }; add(speak)
        stop = action(R.string.assistant_cancel) { cancel(true) }.apply { visibility = View.GONE }; add(stop, 6)
        add(action(R.string.assistant_family) { route("family") })
        add(action(R.string.assistant_whatsapp) { route("media") })
        add(action(R.string.assistant_apps) { route("apps") })
        add(action(R.string.assistant_alarms) { route("alarm") })
        add(action(R.string.assistant_home) { route("home") })
        cloud = action(R.string.assistant_cloud_off) { toggleCloud() }; add(cloud, 22)
        overlay = action(R.string.assistant_overlay_off) { }; add(overlay)
        setContentView(ScrollView(this).apply { setBackgroundColor(MaaDesign.canvas); isFillViewport = true; addView(content) })
        updateControls()
    }

    override fun onResume() {
        super.onResume(); resumed = true; HelpOverlayService.addStateListener(overlayStateListener); updateControls()
        consumeVoiceEntry()
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); voiceEntry?.cancel(); setIntent(intent)
        voiceEntry = VoiceEntry(intent, restored = false)
        if (resumed) consumeVoiceEntry()
    }
    private fun consumeVoiceEntry() {
        val entry = voiceEntry; voiceEntry = null
        entry?.let { if (it.consume()) requestSpeech(it.navigateLocalAfterSpeech) }
    }
    override fun onPause() { resumed = false; voiceEntry?.cancel(); HelpOverlayService.removeStateListener(overlayStateListener); cancel(false); dialog?.dismiss(); dialog = null; super.onPause() }
    override fun onDestroy() { voiceEntry?.cancel(); cancel(false); recognizer?.destroy(); recognizer = null; worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("draft", editor.text.toString()); super.onSaveInstanceState(out) }

    private fun updateControls() {
        if (!::cloud.isInitialized) return
        heading.text = AssistantIdentity.displayName(this)
        ask.text = AssistantIdentity.askLabel(this)
        cloud.text = tr(if (cloudAllowed(endpoint())) R.string.assistant_cloud_on else R.string.assistant_cloud_off)
        val requested = HelpOverlayService.isRequested(this)
        val renderedGeneration = HelpOverlayService.generation(this)
        overlay.text = tr(when {
            HelpOverlayService.isActive(this) -> R.string.assistant_overlay_on
            requested -> R.string.assistant_overlay_stop
            else -> R.string.assistant_overlay_off
        })
        val command: () -> Unit = {
            if (HelpOverlayService.generation(this) != renderedGeneration) updateControls()
            else if (requested) { HelpOverlayService.stop(this); updateControls() }
            else startOverlay()
        }
        overlayCommand = command
        overlay.setOnClickListener { command() }
        ask.isEnabled = !busy; speak.isEnabled = !busy; editor.isEnabled = !busy
        stop.visibility = if (busy) View.VISIBLE else View.GONE
    }
    private fun setBusy(value: Boolean, text: String) { busy = value; state.text = text; updateControls() }
    private fun valid(token: Int): Boolean = resumed && !isDestroyed && token == generation
    private fun cancel(show: Boolean) {
        generation++
        trustedSpeechNavigation = false
        job?.cancel(true); job = null
        connection?.disconnect(); connection = null
        runCatching { recognizer?.cancel() }
        if (::state.isInitialized) {
            if (busy || show) state.text = tr(R.string.assistant_cancelled)
            busy = false; updateControls()
        }
    }

    private fun submit() {
        if (!resumed || busy) return
        val text = editor.text.toString().trim()
        if (text.isEmpty()) { state.text = tr(R.string.assistant_empty); return }
        if (text.length > 1000) { state.text = tr(R.string.assistant_too_long); return }
        val action = localAction(text)
        if (action != null) { route(action); return }
        val base = endpoint()
        if (cloudAllowed(base)) classify(text, base!!) else state.text = tr(R.string.assistant_unknown)
    }

    /** Conservative exact command vocabulary. Unrecognized or compound text is never guessed locally. */
    internal fun localAction(text: String): String? {
        val command = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।').replace(Regex("\\s+"), " ")
        return when (command) {
            "home", "go home", "open home", "घर", "घर चलो", "घर खोलो" -> "home"
            "family", "open family", "call family", "phone", "call", "परिवार", "परिवार खोलो", "परिवार को फोन", "फोन", "कॉल" -> "family"
            "help", "family help", "help me", "मदद", "मदद करो", "परिवार से मदद" -> "help"
            "apps", "open apps", "all apps", "ऐप", "सभी ऐप", "ऐप खोलो" -> "apps"
            "alarm", "alarms", "open alarm", "open alarms", "अलार्म", "अलार्म खोलो" -> "alarm"
            "notifications", "open notifications", "alerts", "सूचनाएँ", "सूचनाएं", "सूचनाएँ खोलो" -> "notifications"
            "whatsapp", "open whatsapp", "family whatsapp", "photos", "videos", "व्हाट्सऐप", "व्हाट्सएप", "व्हाट्सऐप खोलो", "फोटो", "वीडियो" -> "media"
            "messages", "message", "sms", "संदेश", "मैसेज" -> "messages"
            "qr", "pay", "payment", "पैसे", "भुगतान" -> "qr"
            else -> null
        }
    }
    internal fun spokenNavigation(text: String, trustedHomeGesture: Boolean): String? =
        if (trustedHomeGesture) localAction(text)?.takeIf {
            it in setOf("home", "family", "help", "apps", "alarm", "media", "messages", "qr", "notifications")
        } else null

    private fun route(action: String) {
        if (!resumed) return
        cancel(false)
        val intent = when (action) {
            "home" -> Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            "family", "call", "help" -> CommunicationActivity.intent(this)
            "media" -> CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "whatsapp")
            "messages" -> CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "sms")
            "apps" -> Intent(this, AppsActivity::class.java)
            "alarm" -> Intent(this, AlarmActivity::class.java)
            "notifications" -> Intent(this, NotificationCenterActivity::class.java)
            "qr" -> MainActivity.toolsIntent(this)
            else -> { state.text = tr(R.string.assistant_unknown); return }
        }
        try { startActivity(intent) } catch (_: RuntimeException) { state.text = tr(R.string.assistant_error) }
    }

    private fun requestSpeech(navigateLocalAfterSpeech: Boolean = false) {
        if (!resumed || busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { state.text = tr(R.string.assistant_speech_unavailable); return }
        if (!prefs.getBoolean("ai_speech_notice_seen", false)) {
            dialog = AlertDialog.Builder(this).setTitle(tr(R.string.assistant_speech_title)).setMessage(tr(R.string.assistant_speech_notice))
                .setNegativeButton(tr(R.string.assistant_not_now), null)
                .setPositiveButton(tr(R.string.assistant_speech_allow)) { _, _ ->
                    if (resumed) { prefs.edit().putBoolean("ai_speech_notice_seen", true).apply(); beginSpeech(navigateLocalAfterSpeech) }
                }.show()
        } else beginSpeech(navigateLocalAfterSpeech)
    }
    private fun beginSpeech(navigateLocalAfterSpeech: Boolean = false) {
        if (!resumed) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            state.text = tr(R.string.assistant_permission)
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 41)
            return
        }
        cancel(false)
        trustedSpeechNavigation = navigateLocalAfterSpeech
        val token = generation
        setBusy(true, tr(R.string.assistant_listening))
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() { if (valid(token)) state.text = tr(R.string.assistant_thinking) }
                    override fun onError(error: Int) { if (valid(token)) { trustedSpeechNavigation = false; generation++; setBusy(false, tr(R.string.assistant_speech_error)) } }
                    override fun onResults(results: Bundle?) {
                        if (!valid(token)) return
                        val navigateLocal = trustedSpeechNavigation; trustedSpeechNavigation = false
                        generation++
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        if (heard.isEmpty() || heard.length > 1000) setBusy(false, tr(R.string.assistant_speech_error)) else {
                            editor.setText(heard)
                            setBusy(false, AppLanguage.localizedContext(this@AssistantActivity).getString(R.string.assistant_heard, heard, AssistantIdentity.askLabel(this@AssistantActivity)))
                            spokenNavigation(heard, navigateLocal)?.let { route(it) }
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale(this@AssistantActivity).toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false); putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            }
        } catch (_: RuntimeException) { if (valid(token)) { trustedSpeechNavigation = false; setBusy(false, tr(R.string.assistant_speech_unavailable)) } }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) state.text = tr(R.string.assistant_permission)
    }

    private fun endpoint(): String? = validatedEndpoint(prefs.getString("backend", "").orEmpty())
    private fun cloudAllowed(base: String?): Boolean = base != null && prefs.getBoolean("ai_cloud_consent", false) && prefs.getString("ai_cloud_endpoint", "") == base
    private fun toggleCloud() {
        if (prefs.getBoolean("ai_cloud_consent", false)) {
            cancel(false); prefs.edit().putBoolean("ai_cloud_consent", false).remove("ai_cloud_endpoint").apply()
            state.text = tr(R.string.assistant_cloud_disabled); updateControls(); return
        }
        val base = endpoint()
        if (base == null) { state.text = tr(R.string.assistant_cloud_unconfigured); return }
        dialog = AlertDialog.Builder(this).setTitle(tr(R.string.assistant_cloud_title))
            .setMessage(AppLanguage.localizedContext(this).getString(R.string.assistant_cloud_notice, URI(base).host))
            .setNegativeButton(tr(R.string.assistant_not_now), null)
            .setPositiveButton(tr(R.string.assistant_cloud_allow)) { _, _ ->
                if (resumed && endpoint() == base) {
                    prefs.edit().putBoolean("ai_cloud_consent", true).putString("ai_cloud_endpoint", base).apply(); updateControls()
                }
            }.show()
    }
    private fun classify(text: String, base: String) {
        cancel(false)
        val token = generation
        setBusy(true, tr(R.string.assistant_thinking))
        job = worker.submit {
            var call: HttpURLConnection? = null
            try {
                val started = SystemClock.elapsedRealtime()
                call = URL("$base/intent").openConnection() as HttpURLConnection
                connection = call
                call.instanceFollowRedirects = false; call.requestMethod = "POST"
                call.connectTimeout = 8000; call.readTimeout = 8000; call.doOutput = true
                call.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                call.setRequestProperty("Authorization", "Bearer " + prefs.getString("token", ""))
                val body = JSONObject().put("text", text).toString().toByteArray(Charsets.UTF_8)
                call.setFixedLengthStreamingMode(body.size)
                if (Thread.currentThread().isInterrupted || token != generation || !resumed || !cloudAllowed(base)) error("Cancelled")
                call.outputStream.use { it.write(body) }
                if (call.responseCode != 200 || call.contentLengthLong > 4096) error("Invalid response")
                val bytes = ByteArrayOutputStream()
                call.inputStream.use { input ->
                    val buffer = ByteArray(1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted || token != generation || !resumed || SystemClock.elapsedRealtime() - started > 20_000) error("Cancelled")
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > 4096) error("Response too large")
                        bytes.write(buffer, 0, count)
                    }
                }
                val action = validatedAction(bytes.toString(Charsets.UTF_8.name())) ?: error("Invalid action")
                runOnUiThread {
                    if (valid(token) && cloudAllowed(base) && endpoint() == base) { setBusy(false, tr(R.string.assistant_ready)); route(action) }
                }
            } catch (_: Exception) {
                runOnUiThread { if (valid(token)) setBusy(false, tr(R.string.assistant_error)) }
            } finally { call?.disconnect(); if (connection === call) connection = null }
        }
    }
    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            state.text = tr(R.string.assistant_overlay_permission)
            try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            catch (_: RuntimeException) { state.text = tr(R.string.assistant_overlay_failed) }
            return
        }
        try { HelpOverlayService.start(this) }
        catch (_: RuntimeException) { state.text = tr(R.string.assistant_overlay_failed) }
    }

    /** Foreground entry consumes an explicit in-process authorization once, never from saved state. */
    internal class VoiceEntry(intent: Intent, restored: Boolean) {
        private var pending = intent.getStringExtra(VOICE_TOKEN).also { intent.removeExtra(VOICE_TOKEN) }
        var navigateLocalAfterSpeech = false
            private set
        init { if (restored) cancel() }
        fun consume(): Boolean {
            val token = pending; pending = null
            val navigation = consumeVoiceToken(token) ?: return false
            navigateLocalAfterSpeech = navigation
            return true
        }
        fun cancel() { revokeVoiceToken(pending); pending = null; navigateLocalAfterSpeech = false }
    }

    companion object {
        private const val VOICE_TOKEN = "in.family.maa.TRUSTED_VOICE_ENTRY"
        private const val VOICE_ENTRY_LIFETIME_MS = 15_000L
        private var voiceToken: String? = null
        private var voiceTokenIssuedAt = 0L
        private var voiceTokenNavigation = false
        /** Call only from an explicit Ask voice gesture; creates no microphone work by itself. */
        @Synchronized fun voiceIntent(context: Context, navigateLocalAfterSpeech: Boolean = true): Intent {
            val token = UUID.randomUUID().toString()
            voiceToken = token; voiceTokenIssuedAt = SystemClock.elapsedRealtime(); voiceTokenNavigation = navigateLocalAfterSpeech
            return Intent(context, AssistantActivity::class.java).putExtra(VOICE_TOKEN, token)
        }
        @Synchronized private fun consumeVoiceToken(token: String?): Boolean? {
            if (token == null || token != voiceToken) return null
            voiceToken = null
            return voiceTokenNavigation.takeIf { SystemClock.elapsedRealtime() - voiceTokenIssuedAt in 0..VOICE_ENTRY_LIFETIME_MS }
        }
        @Synchronized private fun revokeVoiceToken(token: String?) { if (token != null && token == voiceToken) voiceToken = null }
        internal fun validatedEndpoint(raw: String): String? = runCatching {
            val uri = URI(raw.trim())
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) null
            else uri.toString().trimEnd('/')
        }.getOrNull()
        internal fun validatedAction(json: String): String? = runCatching {
            val value = JSONObject(json)
            if (value.length() != 1 || !value.has("action")) null else (value.get("action") as? String)
                ?.takeIf { it in setOf("call", "media", "qr", "help", "home", "unsupported") }
        }.getOrNull()
    }
}
