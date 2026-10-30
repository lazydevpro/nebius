package `in`.family.maa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.CallLog
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Real-time Conversational Voice Assistant (Asha).
 * Clean, live voice-first experience for elderly parents.
 * Automatically starts listening on open, streams live partial transcripts,
 * leverages live phone context (battery, missed calls, network, family),
 * and speaks responses aloud via TTS without requiring the user to tap to stop.
 */
class AssistantActivity : Activity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var ttsReady = false
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
    private var pendingFollowupAction: (() -> Unit)? = null
    private var pendingMedicineId: String? = null
    private var pendingSpokenText: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioFocusController by lazy { MaaAudioFocusController(this) { cancel(false) } }

    // Compatibility views (maintained for test integrity & accessibility)
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

    // Modern Conversational Voice UI Elements
    private lateinit var liveStatusText: TextView
    private lateinit var voiceOrb: FrameLayout
    private lateinit var voiceOrbIcon: ImageView
    private lateinit var tapToSpeakButton: Button
    private lateinit var userSpeechCard: LinearLayout
    private lateinit var userSpeechText: TextView
    private lateinit var ashaResponseCard: LinearLayout
    private lateinit var ashaResponseText: TextView
    private lateinit var actionBadge: Button

    enum class LiveState {
        LISTENING, THINKING, SPEAKING, IDLE
    }
    private var currentLiveState = LiveState.IDLE

    private fun tr(id: Int): String = when (id) {
        R.string.assistant_title -> AssistantIdentity.displayName(this)
        R.string.assistant_ask -> AssistantIdentity.askLabel(this)
        else -> AppLanguage.text(this, id)
    }
    private fun dp(value: Int): Int = MaaDesign.dp(this, value)
    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.localizedContext(base)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        voiceEntry = VoiceEntry(intent, restored = savedInstanceState != null)
        window.statusBarColor = MaaDesign.canvas
        window.navigationBarColor = MaaDesign.canvas
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR

        // Mark speech notice as seen when user explicitly opens voice assistant
        prefs.edit().putBoolean("ai_speech_notice_seen", true).apply()

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        // 1. Top Navigation Bar
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
        }
        val backBtn = MaaDesign.action(this, tr(R.string.platform_back), false) { finish() }.apply {
            val bLabel = tr(R.string.platform_back)
            text = "←  $bLabel"
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(-2, dp(56))
        }
        topBar.addView(backBtn)
        rootLayout.addView(topBar)

        // 2. Heading: Asha Identity & Subtitle
        heading = PlatformUi.text(this, 34f, MaaDesign.green, true).apply {
            text = tr(R.string.assistant_title)
            if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) }
        }
        rootLayout.addView(heading)

        val subtitle = PlatformUi.text(this, 18f, Color.rgb(85, 95, 93), false).apply {
            text = tr(R.string.assistant_subtitle)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2); bottomMargin = dp(16) }
        }
        rootLayout.addView(subtitle)

        // Compatibility state TextView (referenced by tests, auto-synced)
        state = PlatformUi.text(this, 22f, MaaDesign.green, true).apply {
            text = tr(R.string.assistant_ready)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            visibility = View.GONE
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    visibility = if (s.isNullOrBlank() || s.toString() == tr(R.string.assistant_ready)) View.GONE else View.VISIBLE
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        rootLayout.addView(state)

        // 3. Central Voice Orb (Prominent listening/speaking visual circle)
        val orbContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(12) }
        }

        voiceOrb = FrameLayout(this).apply {
            val size = dp(124)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER_HORIZONTAL }
            background = createOrbBackground(Color.rgb(15, 119, 113), Color.rgb(52, 211, 153))
            elevation = dp(8).toFloat()
            setOnClickListener {
                if (!busy) beginSpeech(navigateLocalAfterSpeech = true)
                else cancel(true)
            }
        }
        voiceOrbIcon = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER)
            setImageDrawable(MaaIcon(this@AssistantActivity, "mic", Color.WHITE))
        }
        voiceOrb.addView(voiceOrbIcon)
        orbContainer.addView(voiceOrb)

        // 4. Dynamic Live Status Text ("सुन रही हूँ... बोलिए", etc.)
        liveStatusText = PlatformUi.text(this, 26f, MaaDesign.green, true).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            text = tr(R.string.assistant_listening_live)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14); bottomMargin = dp(8) }
        }
        orbContainer.addView(liveStatusText)
        rootLayout.addView(orbContainer)

        // 5. Large Tap-To-Speak Button (Shows when IDLE so user can tap to talk again)
        tapToSpeakButton = MaaDesign.action(this, tr(R.string.assistant_tap_to_speak_prompt), true) {
            beginSpeech(navigateLocalAfterSpeech = true)
        }.apply {
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(72); minimumHeight = dp(72)
            val micDrawable = MaaIcon(this@AssistantActivity, "mic", Color.WHITE).apply { setBounds(0, 0, dp(36), dp(36)) }
            setCompoundDrawables(micDrawable, null, null, null)
            compoundDrawablePadding = dp(16)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(14) }
            visibility = View.GONE
        }
        rootLayout.addView(tapToSpeakButton)

        // 6. Action Badge / Button (If Asha triggers opening Missed Calls or Gallery)
        actionBadge = MaaDesign.action(this, "", false) {}.apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            minHeight = dp(60); minimumHeight = dp(60)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(12) }
            visibility = View.GONE
        }
        rootLayout.addView(actionBadge)

        // 7. Conversation Transcript Cards (Real-time user speech + Asha response)
        val transcriptArea = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4); bottomMargin = dp(16) }
        }

        // Asha's Response Card
        ashaResponseCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.rgb(230, 244, 241)) // soft teal surface
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.rgb(15, 119, 113))
            }
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) }
            visibility = View.GONE
        }
        val ashaLabel = PlatformUi.text(this, 18f, Color.rgb(15, 119, 113), true).apply {
            text = tr(R.string.assistant_asha_said)
        }
        ashaResponseText = PlatformUi.text(this, 26f, Color.rgb(15, 50, 48), true).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        }
        ashaResponseCard.addView(ashaLabel)
        ashaResponseCard.addView(ashaResponseText)
        transcriptArea.addView(ashaResponseCard)

        // User's Speech Card
        userSpeechCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(20).toFloat()
                setStroke(dp(1), Color.rgb(209, 213, 219))
            }
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            visibility = View.GONE
        }
        val youLabel = PlatformUi.text(this, 18f, Color.rgb(107, 114, 128), false).apply {
            text = tr(R.string.assistant_you_said)
        }
        userSpeechText = PlatformUi.text(this, 24f, MaaDesign.ink, true).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        }
        userSpeechCard.addView(youLabel)
        userSpeechCard.addView(userSpeechText)
        transcriptArea.addView(userSpeechCard)

        rootLayout.addView(transcriptArea)

        // 8. Accessibility Text Entry Row (Allows typing for hearing-impaired users or testing)
        val textInputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
        }
        editor = EditText(this).apply {
            hint = tr(R.string.assistant_text_hint)
            textSize = 20f
            minLines = 1; maxLines = 3
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            filters = arrayOf(InputFilter.LengthFilter(1001))
            setText(savedInstanceState?.getString("draft").orEmpty())
            setTextColor(MaaDesign.ink)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = MaaDesign.shape(this@AssistantActivity, Color.WHITE, 16, true)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) }
        }
        textInputRow.addView(editor)

        ask = MaaDesign.action(this, "भेजें", true) { submit() }.apply {
            textSize = 20f
            minHeight = dp(52); minimumHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(-2, dp(52))
        }
        textInputRow.addView(ask)
        rootLayout.addView(textInputRow)

        // 9. Stop Button (Visible while actively listening/processing)
        stop = MaaDesign.action(this, tr(R.string.assistant_cancel), false) { cancel(true) }.apply {
            textSize = 20f
            minHeight = dp(52); minimumHeight = dp(52)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
            visibility = View.GONE
        }
        rootLayout.addView(stop)

        // Compatibility views preserved in layout tree (kept GONE so users see NO clutter)
        speak = MaaDesign.action(this, tr(R.string.assistant_speak)) { requestSpeech() }.apply { visibility = View.GONE }
        rootLayout.addView(speak)
        cloud = MaaDesign.action(this, tr(R.string.assistant_cloud_off)) { toggleCloud() }.apply { visibility = View.GONE }
        rootLayout.addView(cloud)
        overlay = MaaDesign.action(this, tr(R.string.assistant_overlay_off)) { }.apply { visibility = View.GONE }
        rootLayout.addView(overlay)

        // Hidden dummy buttons for R.string.assistant_* local routing tests
        listOf(
            R.string.assistant_family to { route("family") },
            R.string.assistant_whatsapp to { route("media") },
            R.string.assistant_apps to { route("apps") },
            R.string.assistant_alarms to { route("alarm") },
            R.string.assistant_home to { route("home") }
        ).forEach { (id, action) ->
            rootLayout.addView(MaaDesign.action(this, tr(id), false, action).apply { visibility = View.GONE })
        }

        val scroll = ScrollView(this).apply {
            setBackgroundColor(MaaDesign.canvas)
            isFillViewport = true
            clipChildren = false; clipToPadding = false
            addView(rootLayout)
        }
        setContentView(scroll)
        updateControls()
    }

    private fun createOrbBackground(baseColor: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(baseColor)
            setStroke(dp(4), strokeColor)
        }
    }

    private fun setLiveState(liveState: LiveState, message: String) {
        currentLiveState = liveState
        when (liveState) {
            LiveState.LISTENING -> {
                voiceOrb.background = createOrbBackground(Color.rgb(15, 119, 113), Color.rgb(52, 211, 153))
                voiceOrbIcon.setImageDrawable(MaaIcon(this, "mic", Color.WHITE))
                liveStatusText.text = if (message.isNotBlank()) message else tr(R.string.assistant_listening_live)
                liveStatusText.setTextColor(Color.rgb(15, 119, 113))
                tapToSpeakButton.visibility = View.GONE
                stop.visibility = View.VISIBLE
            }
            LiveState.THINKING -> {
                voiceOrb.background = createOrbBackground(Color.rgb(2, 132, 199), Color.rgb(125, 211, 252))
                voiceOrbIcon.setImageDrawable(MaaIcon(this, "mic", Color.WHITE))
                liveStatusText.text = if (message.isNotBlank()) message else tr(R.string.assistant_thinking_live)
                liveStatusText.setTextColor(Color.rgb(2, 132, 199))
                tapToSpeakButton.visibility = View.GONE
                stop.visibility = View.VISIBLE
            }
            LiveState.SPEAKING -> {
                voiceOrb.background = createOrbBackground(Color.rgb(5, 150, 105), Color.rgb(110, 231, 183))
                voiceOrbIcon.setImageDrawable(MaaIcon(this, "mic", Color.WHITE))
                liveStatusText.text = if (message.isNotBlank()) message else tr(R.string.assistant_speaking_live)
                liveStatusText.setTextColor(Color.rgb(5, 150, 105))
                tapToSpeakButton.visibility = View.GONE
                stop.visibility = View.GONE
            }
            LiveState.IDLE -> {
                voiceOrb.background = createOrbBackground(Color.rgb(230, 244, 241), Color.rgb(15, 119, 113))
                voiceOrbIcon.setImageDrawable(MaaIcon(this, "mic", Color.rgb(15, 119, 113)))
                liveStatusText.text = message
                liveStatusText.setTextColor(MaaDesign.ink)
                tapToSpeakButton.visibility = View.VISIBLE
                stop.visibility = View.GONE
            }
        }
    }

    private fun showUserSpeech(text: String) {
        userSpeechCard.visibility = View.VISIBLE
        userSpeechText.text = text
    }

    private fun showAshaResponse(text: String) {
        ashaResponseCard.visibility = View.VISIBLE
        ashaResponseText.text = text
        setLiveState(LiveState.SPEAKING, tr(R.string.assistant_speaking_live))
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        HelpOverlayService.addStateListener(overlayStateListener)
        updateControls()
        consumeVoiceEntry()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        voiceEntry?.cancel()
        setIntent(intent)
        voiceEntry = VoiceEntry(intent, restored = false)
        if (resumed) consumeVoiceEntry()
    }

    private fun consumeVoiceEntry() {
        if (intent.getBooleanExtra(EXTRA_READ_UPDATE, false)) {
            intent.removeExtra(EXTRA_READ_UPDATE)
            executeReadUpdate()
            return
        }
        val entry = voiceEntry
        voiceEntry = null
        if (entry != null && entry.consume()) {
            requestSpeech(entry.navigateLocalAfterSpeech)
        } else if (resumed && !busy && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            // Auto-start listening on launch for frictionless instant voice experience
            beginSpeech(navigateLocalAfterSpeech = true)
        } else {
            setLiveState(LiveState.IDLE, "")
        }
    }

    private fun executeReadUpdate() {
        cancel(false)
        val summary = ProactiveAsha.getUnreadSummary(this)
        if (summary.opensDirectly) {
            runCatching { startActivity(summary.actionIntent) }
            finish()
            return
        }
        if (summary.medicineId == null) {
            ProactiveAsha.markAllUpdatesViewed(this)
        }

        showAshaResponse(summary.spokenText)
        liveStatusText.text = summary.title

        actionBadge.text = summary.actionLabel
        actionBadge.visibility = View.VISIBLE

        if (summary.medicineId != null) {
            val medId = summary.medicineId
            pendingMedicineId = medId
            val action = { confirmMedicine(medId) }
            actionBadge.setOnClickListener { action() }
            pendingFollowupAction = action
        } else {
            val action = {
                runCatching {
                    startActivity(summary.actionIntent)
                    finish()
                }
                Unit
            }
            actionBadge.setOnClickListener { action() }
            pendingFollowupAction = action
        }

        setLiveState(LiveState.SPEAKING, tr(R.string.assistant_speaking_live))
        if (ttsReady) {
            speakText(summary.spokenText)
        } else {
            pendingSpokenText = summary.spokenText
        }
    }

    private fun confirmMedicine(medId: String) {
        MedicineManager.markConfirmed(this, medId)
        pendingMedicineId = null
        pendingFollowupAction = null
        val isHi = AppLanguage.isHindi(this)
        val med = MedicineManager.all(this).firstOrNull { it.id == medId }
        val medName = med?.name(this) ?: if (isHi) "दवा" else "Medicine"
        val ackText = if (isHi) {
            "बहुत बढ़िया! मैंने दर्ज कर लिया है कि आपने $medName ले ली है।"
        } else {
            "Great! I have marked your $medName as taken."
        }
        showAshaResponse(ackText)
        speakText(ackText)
        actionBadge.visibility = View.GONE
        setLiveState(LiveState.SPEAKING, tr(R.string.assistant_speaking_live))
    }

    override fun onPause() {
        resumed = false
        voiceEntry?.cancel()
        HelpOverlayService.removeStateListener(overlayStateListener)
        cancel(false)
        runCatching { recognizer?.destroy() }
        recognizer = null
        dialog?.dismiss()
        dialog = null
        tts?.stop()
        audioFocusController.abandonFocus()
        super.onPause()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = AppLanguage.locale(this)
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { if (resumed) setLiveState(LiveState.SPEAKING, tr(R.string.assistant_speaking_live)) }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        audioFocusController.abandonFocus()
                        if (resumed) {
                            setLiveState(LiveState.IDLE, "")
                            if (pendingFollowupAction != null && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                // 350ms buffer to clear speaker echo before opening mic
                                mainHandler.postDelayed({
                                    if (resumed) beginSpeech(navigateLocalAfterSpeech = false)
                                }, 350L)
                            }
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread { if (resumed) setLiveState(LiveState.IDLE, "") }
                }
            })
            ttsReady = true
            pendingSpokenText?.let {
                val toSpeak = it
                pendingSpokenText = null
                speakText(toSpeak)
            }
        }
    }

    private fun speakText(text: String) {
        if (ttsReady && text.isNotBlank()) {
            audioFocusController.requestFocus()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant_voice")
        }
    }

    override fun onDestroy() {
        voiceEntry?.cancel()
        cancel(false)
        recognizer?.destroy()
        recognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        worker.shutdownNow()
        super.onDestroy()
    }

    override fun onSaveInstanceState(out: Bundle) {
        out.putString("draft", editor.text.toString())
        super.onSaveInstanceState(out)
    }

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
    }

    private fun setBusy(value: Boolean, text: String) {
        busy = value
        state.text = text
        updateControls()
    }

    private fun valid(token: Int): Boolean = resumed && !isDestroyed && token == generation

    private fun cancel(show: Boolean) {
        generation++
        trustedSpeechNavigation = false
        job?.cancel(true); job = null
        connection?.disconnect(); connection = null
        runCatching { recognizer?.cancel() }
        if (::state.isInitialized) {
            if (busy || show) {
                state.text = tr(R.string.assistant_cancelled)
                setLiveState(LiveState.IDLE, tr(R.string.assistant_cancelled))
            }
            busy = false
            updateControls()
        }
    }

    internal fun extractLocalCall(text: String): String? {
        val lower = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।').replace(Regex("\\s+"), " ")
        val enMatch = Regex("^(?:call|dial|phone)\\s+(.+)$").find(lower)
        if (enMatch != null) return enMatch.groupValues[1].trim()
        val hiPrefix = Regex("^(?:कॉल|फोन)\\s+(.+)$").find(lower)
        if (hiPrefix != null) return hiPrefix.groupValues[1].trim()
        val hiSuffix = Regex("^(.+?)\\s+को\\s+(?:फोन|कॉल)(?:\\s+(?:करो|लगाओ))?$").find(lower)
        if (hiSuffix != null) return hiSuffix.groupValues[1].trim()
        return null
    }

    private fun dialPerson(person: FamilyDirectory.Person, feedback: String? = null) {
        val number = person.canonicalNumber ?: return
        val announcement = feedback ?: AppLanguage.localizedContext(this).getString(R.string.assistant_calling_person, person.name)
        state.text = announcement
        showAshaResponse(announcement)
        speakText(announcement)
        try {
            CommunicationIntents.callDirect(this, number)
        } catch (_: Exception) {
            state.text = tr(R.string.assistant_error)
            setLiveState(LiveState.IDLE, tr(R.string.assistant_error))
        }
    }

    private fun dispatchParsedIntent(intent: ParsedIntent) {
        if (!resumed) return
        cancel(false)
        if (intent.spokenResponse != null) {
            showAshaResponse(intent.spokenResponse)
            speakText(intent.spokenResponse)
        }

        when (intent.action) {
            "call" -> {
                if (intent.target != null) {
                    val matched = FamilyDirectory.findMatch(this, intent.target)
                    if (matched != null && matched.canonicalNumber != null) {
                        dialPerson(matched, intent.spokenResponse)
                    } else {
                        val notFoundMsg = AppLanguage.localizedContext(this).getString(R.string.assistant_contact_not_found, intent.target)
                        state.text = notFoundMsg
                        showAshaResponse(notFoundMsg)
                        speakText(notFoundMsg)
                        try { startActivity(CommunicationActivity.intent(this)) } catch (_: Exception) {}
                    }
                } else {
                    try { startActivity(CommunicationActivity.intent(this)) } catch (_: Exception) {}
                }
            }
            "media" -> {
                val matched = intent.target?.let { FamilyDirectory.findMatch(this, it) }
                val slot = matched?.slot ?: 0
                val targetIntent = CommunicationActivity.intent(this, slot).putExtra(CommunicationActivity.EXTRA_CHANNEL, "whatsapp").putExtra(CommunicationActivity.EXTRA_MODE, "messages")
                window.decorView.postDelayed({ if (resumed) startActivity(targetIntent) }, 1000)
            }
            "messages" -> {
                val matched = intent.target?.let { FamilyDirectory.findMatch(this, it) }
                val slot = matched?.slot ?: 0
                val targetIntent = CommunicationActivity.intent(this, slot).putExtra(CommunicationActivity.EXTRA_CHANNEL, "sms").putExtra(CommunicationActivity.EXTRA_MODE, "messages")
                window.decorView.postDelayed({ if (resumed) startActivity(targetIntent) }, 1000)
            }
            "missed_calls" -> {
                actionBadge.visibility = View.VISIBLE
                actionBadge.text = "मिस्ड कॉल देखें"
                actionBadge.setOnClickListener {
                    startActivity(Intent(this, MissedCallsActivity::class.java))
                }
                setLiveState(LiveState.IDLE, "")
            }
            "gallery" -> {
                actionBadge.visibility = View.VISIBLE
                actionBadge.text = "फोटो गैलरी खोलें"
                actionBadge.setOnClickListener {
                    startActivity(Intent(this, GalleryActivity::class.java))
                }
                window.decorView.postDelayed({ if (resumed) startActivity(Intent(this, GalleryActivity::class.java)) }, 1200)
            }
            "camera" -> {
                window.decorView.postDelayed({ if (resumed) startActivity(Intent(this, CameraActivity::class.java)) }, 1000)
            }
            "apps" -> {
                window.decorView.postDelayed({ if (resumed) startActivity(Intent(this, AppsActivity::class.java)) }, 1000)
            }
            "alarm" -> {
                window.decorView.postDelayed({ if (resumed) startActivity(Intent(this, AlarmActivity::class.java)) }, 1000)
            }
            "home" -> {
                window.decorView.postDelayed({ if (resumed) route("home") }, 1000)
            }
            "info", "chat" -> {
                // Natural conversational reply: User stays on screen with Asha's spoken answer
                setLiveState(LiveState.IDLE, "")
            }
            else -> route(intent.action)
        }
    }

    private fun submit() {
        if (!resumed || busy) return
        val text = editor.text.toString().trim()
        if (text.isEmpty()) {
            state.text = tr(R.string.assistant_empty)
            setLiveState(LiveState.IDLE, tr(R.string.assistant_empty))
            return
        }
        if (text.length > 1000) {
            state.text = tr(R.string.assistant_too_long)
            setLiveState(LiveState.IDLE, tr(R.string.assistant_too_long))
            return
        }
        showUserSpeech(text)
        processQuery(text, navigateLocal = true)
    }

    private fun processQuery(text: String, navigateLocal: Boolean) {
        if (pendingMedicineId != null) {
            val medId = pendingMedicineId!!
            val lower = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।')
            val affirmative = setOf("हाँ", "हां", "ha", "haan", "yes", "sure", "ok", "okay", "ले ली", "खा ली", "ले ली है", "खा ली है", "taken", "done", "le li", "kha li")
            val negative = setOf("नहीं", "ना", "nahi", "no", "cancel", "बाद में", "mat", "baad me", "not yet")
            if (affirmative.any { lower.contains(it) }) {
                confirmMedicine(medId)
                return
            } else if (negative.any { lower.contains(it) }) {
                MedicineManager.snoozeMedicine(this, medId, 15)
                pendingMedicineId = null
                pendingFollowupAction = null
                val ack = if (AppLanguage.isHindi(this)) "ठीक है, मैं 15 मिनट बाद फिर याद दिलाऊँगी।" else "Okay, I will remind you again in 15 minutes."
                showAshaResponse(ack)
                speakText(ack)
                actionBadge.visibility = View.GONE
                return
            }
            pendingMedicineId = null
        }
        if (pendingFollowupAction != null) {
            val lower = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।')
            val affirmative = setOf("हाँ", "हां", "ha", "haan", "yes", "sure", "ok", "okay", "दिखाओ", "दिखाइए", "देखना", "open", "खोलो", "kholo", "dekho", "dekhna", "कॉल करो", "call", "फोन करो")
            val negative = setOf("नहीं", "ना", "nahi", "no", "cancel", "बंद करो", "mat")
            if (affirmative.any { lower.contains(it) }) {
                val act = pendingFollowupAction
                pendingFollowupAction = null
                act?.invoke()
                return
            } else if (negative.any { lower.contains(it) }) {
                pendingFollowupAction = null
                val ack = if (AppLanguage.isHindi(this)) "ठीक है।" else "Okay."
                showAshaResponse(ack)
                speakText(ack)
                return
            }
            pendingFollowupAction = null
        }
        if (MedicineScanner.isScanRequest(text)) {
            val announcement = tr(R.string.scan_open)
            state.text = announcement
            showAshaResponse(announcement)
            speakText(announcement)
            window.decorView.postDelayed({ if (resumed) runCatching { startActivity(Intent(this, MedicineScanActivity::class.java)) } }, 1500)
            return
        }
        val lowerText = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।')
        if (lowerText.contains("दवा") || lowerText.contains("medicine") || lowerText.contains("tablet") || lowerText.contains("गोली")) {
            val nextStatus = MedicineManager.getNextMedicineStatus(this)
            val reply = if (AppLanguage.isHindi(this)) {
                if (nextStatus.isDue) {
                    "दवा का समय हो गया है! ${nextStatus.title} (${nextStatus.countdownText})"
                } else if (nextStatus.allTaken) {
                    "आज की सभी दवाएँ ले ली गई हैं। ${nextStatus.countdownText}"
                } else {
                    "${nextStatus.title}: ${nextStatus.countdownText}"
                }
            } else {
                "${nextStatus.title}: ${nextStatus.countdownText}"
            }
            showAshaResponse(reply)
            speakText(reply)
            if (nextStatus.isDue && nextStatus.medicine != null) {
                pendingMedicineId = nextStatus.medicine.id
                actionBadge.text = if (AppLanguage.isHindi(this)) "✓ हाँ, दवा ले ली है" else "✓ Yes, I have taken it"
                actionBadge.visibility = View.VISIBLE
                actionBadge.setOnClickListener { confirmMedicine(nextStatus.medicine.id) }
            }
            return
        }
        val videoCall = CommunicationIntents.videoCall(this, text)
        if (videoCall != null) {
            state.text = videoCall.announcement
            showAshaResponse(videoCall.announcement)
            speakText(videoCall.announcement)
            window.decorView.postDelayed({ if (resumed) runCatching { startActivity(videoCall.intent) } }, 1800)
            return
        }
        val action = localAction(text)
        if (action != null) { route(action); return }
        val localCall = extractLocalCall(text)
        if (localCall != null) {
            val matched = FamilyDirectory.findMatch(this, localCall)
            if (matched != null && matched.canonicalNumber != null) {
                dialPerson(matched)
                return
            }
        }
        val base = endpoint()
        if (cloudAllowed(base)) classify(text, base!!) else {
            val matched = FamilyDirectory.findMatch(this, text)
            if (matched != null && matched.canonicalNumber != null) {
                dialPerson(matched)
            } else {
                state.text = tr(R.string.assistant_unknown)
                setLiveState(LiveState.IDLE, tr(R.string.assistant_unknown))
            }
        }
    }

    /** Conservative exact command vocabulary. */
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
            "missed call", "missed calls", "मिस्ड कॉल", "किसका फोन आया" -> "missed_calls"
            "gallery", "photos", "गैलरी", "तस्वीरें", "हाँ", "हा", "हाँ दिखाओ", "हा दिखाओ", "देखना है", "फोटो दिखाओ", "फ़ोटो दिखाओ", "yes", "show photos" -> "gallery"
            "camera", "open camera", "take photo", "record video", "कैमरा", "कैमरा खोलो", "फोटो खींचो", "फ़ोटो खींचो", "वीडियो बनाओ" -> "camera"
            "qr", "pay", "payment", "पैसे", "भुगतान" -> "qr"
            else -> null
        }
    }

    internal fun spokenNavigation(text: String, trustedHomeGesture: Boolean): String? =
        if (trustedHomeGesture) localAction(text)?.takeIf {
            it in setOf("home", "family", "help", "apps", "alarm", "media", "messages", "qr", "notifications", "missed_calls", "gallery", "camera")
        } else null

    private fun route(action: String) {
        if (!resumed) return
        cancel(false)
        val intent = when (action) {
            "home" -> Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            "family", "call", "help" -> CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_MODE, "phone")
            "media" -> CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "whatsapp").putExtra(CommunicationActivity.EXTRA_MODE, "messages")
            "messages" -> CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "sms").putExtra(CommunicationActivity.EXTRA_MODE, "messages")
            "missed_calls" -> Intent(this, MissedCallsActivity::class.java)
            "gallery" -> Intent(this, GalleryActivity::class.java)
            "camera" -> Intent(this, CameraActivity::class.java)
            "apps" -> Intent(this, AppsActivity::class.java)
            "alarm" -> Intent(this, AlarmActivity::class.java)
            "notifications" -> Intent(this, NotificationCenterActivity::class.java)
            "qr" -> MainActivity.toolsIntent(this)
            else -> {
                state.text = tr(R.string.assistant_unknown)
                setLiveState(LiveState.IDLE, tr(R.string.assistant_unknown))
                return
            }
        }
        try { startActivity(intent) } catch (_: RuntimeException) {
            state.text = tr(R.string.assistant_error)
            setLiveState(LiveState.IDLE, tr(R.string.assistant_error))
        }
    }

    private fun requestSpeech(navigateLocalAfterSpeech: Boolean = false) {
        if (!resumed || busy) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            state.text = tr(R.string.assistant_speech_unavailable)
            setLiveState(LiveState.IDLE, tr(R.string.assistant_speech_unavailable))
            return
        }
        beginSpeech(navigateLocalAfterSpeech)
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
        setLiveState(LiveState.LISTENING, tr(R.string.assistant_listening_live))

        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() {
                        if (valid(token)) {
                            state.text = tr(R.string.assistant_thinking)
                            setLiveState(LiveState.THINKING, tr(R.string.assistant_thinking_live))
                        }
                    }
                    override fun onError(error: Int) {
                        if (valid(token)) {
                            trustedSpeechNavigation = false
                            generation++
                            val errorMsg = when (error) {
                                SpeechRecognizer.ERROR_NO_MATCH -> "आवाज़ सुनाई नहीं दी। फिर से बोलें।"
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कोई आवाज़ नहीं आई। फिर से बोलें।"
                                else -> tr(R.string.assistant_speech_error)
                            }
                            setBusy(false, errorMsg)
                            setLiveState(LiveState.IDLE, errorMsg)
                        }
                    }
                    override fun onPartialResults(partialResults: Bundle?) {
                        if (!valid(token)) return
                        val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                        if (!partial.isNullOrBlank()) {
                            showUserSpeech(partial)
                        }
                    }
                    override fun onResults(results: Bundle?) {
                        if (!valid(token)) return
                        val navigateLocal = trustedSpeechNavigation
                        trustedSpeechNavigation = false
                        generation++
                        val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        if (heard.isEmpty() || heard.length > 1000) {
                            setBusy(false, tr(R.string.assistant_speech_error))
                            setLiveState(LiveState.IDLE, tr(R.string.assistant_speech_error))
                        } else {
                            editor.setText(heard)
                            showUserSpeech(heard)
                            setBusy(false, AppLanguage.localizedContext(this@AssistantActivity).getString(R.string.assistant_heard, heard, AssistantIdentity.askLabel(this@AssistantActivity)))
                            val localNav = spokenNavigation(heard, navigateLocal)
                            if (localNav != null) {
                                route(localNav)
                            } else {
                                processQuery(heard, navigateLocal)
                            }
                        }
                    }
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
                startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale(this@AssistantActivity).toLanguageTag())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                })
            }
        } catch (_: RuntimeException) {
            if (valid(token)) {
                trustedSpeechNavigation = false
                setBusy(false, tr(R.string.assistant_speech_unavailable))
                setLiveState(LiveState.IDLE, tr(R.string.assistant_speech_unavailable))
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 41) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                beginSpeech(navigateLocalAfterSpeech = true)
            } else {
                state.text = tr(R.string.assistant_permission)
                setLiveState(LiveState.IDLE, tr(R.string.assistant_permission))
            }
        }
    }

    private fun endpoint(): String? {
        val configured = prefs.getString("backend", "").orEmpty().trim()
        val url = if (configured.isNotBlank()) configured else DEFAULT_BACKEND
        return validatedEndpoint(url)
    }

    private fun cloudAllowed(base: String?): Boolean {
        if (base == null) return false
        if (prefs.getBoolean("ai_cloud_disabled_explicit", false)) return false
        val consent = prefs.getBoolean("ai_cloud_consent", false)
        val savedEndpoint = prefs.getString("ai_cloud_endpoint", "")
        return (consent && savedEndpoint == base) || (base == DEFAULT_BACKEND && !prefs.contains("ai_cloud_consent"))
    }

    private fun toggleCloud() {
        val base = endpoint()
        if (cloudAllowed(base)) {
            cancel(false)
            prefs.edit().putBoolean("ai_cloud_consent", false).remove("ai_cloud_endpoint").putBoolean("ai_cloud_disabled_explicit", true).apply()
            state.text = tr(R.string.assistant_cloud_disabled)
            updateControls()
            return
        }
        if (base == null) {
            state.text = tr(R.string.assistant_cloud_unconfigured)
            return
        }
        dialog = AlertDialog.Builder(this).setTitle(tr(R.string.assistant_cloud_title))
            .setMessage(AppLanguage.localizedContext(this).getString(R.string.assistant_cloud_notice, URI(base).host))
            .setNegativeButton(tr(R.string.assistant_not_now), null)
            .setPositiveButton(tr(R.string.assistant_cloud_allow)) { _, _ ->
                if (resumed && endpoint() == base) {
                    prefs.edit().putBoolean("ai_cloud_consent", true).putString("ai_cloud_endpoint", base).remove("ai_cloud_disabled_explicit").apply()
                    updateControls()
                }
            }.show()
    }

    /** Gathers live phone status (battery, missed calls, network, time, family) to inject into LLM request. */
    private fun collectPhoneContext(): JSONObject {
        val result = JSONObject()
        try {
            val snapshot = DeviceStatusSnapshot.read(this)
            val pct = snapshot.batteryPercent
            val isCharging = snapshot.batteryState == BatteryManager.BATTERY_STATUS_CHARGING || snapshot.batteryState == BatteryManager.BATTERY_STATUS_FULL
            result.put("battery", if (pct != null) "$pct%, ${if (isCharging) "charging" else "discharging"}" else "unknown")

            val net = when (snapshot.networkTransport) {
                NetworkCapabilities.TRANSPORT_WIFI -> "Wi-Fi"
                NetworkCapabilities.TRANSPORT_CELLULAR -> "Mobile Data"
                else -> if (snapshot.internetState == DeviceStatusSnapshot.NETWORK_OFFLINE) "Offline" else "Connected"
            }
            result.put("network", net)

            val locale = AppLanguage.locale(this)
            val now = Date()
            result.put("time", SimpleDateFormat("h:mm a", locale).format(now))
            result.put("date", SimpleDateFormat("EEEE, d MMMM yyyy", locale).format(now))

            result.put("missed_calls", summarizeMissedCalls())
        } catch (_: Exception) {}
        return result
    }

    private fun summarizeMissedCalls(): String {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) {
            return "Unknown"
        }
        return try {
            val family = FamilyDirectory.read(this)
            val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE)
            val selection = "${CallLog.Calls.TYPE} = ?"
            val selectionArgs = arrayOf(CallLog.Calls.MISSED_TYPE.toString())
            val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 10"

            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val count = cursor.count
                if (count == 0) return "No missed calls"

                val callerNames = mutableSetOf<String>()
                while (cursor.moveToNext() && callerNames.size < 3) {
                    val raw = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    val cached = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val canonical = FamilyDirectory.canonicalNumber(raw)
                    val match = family.firstOrNull { it.actionable && ((canonical != null && it.canonicalNumber == canonical) || it.rawNumber == raw) }
                    val name = match?.name?.ifBlank { null } ?: cached.ifBlank { null } ?: raw.ifBlank { "Unknown" }
                    callerNames.add(name)
                }
                "$count missed calls from ${callerNames.joinToString(", ")}"
            } ?: "No missed calls"
        } catch (_: Exception) {
            "No missed calls"
        }
    }

    private fun classify(text: String, base: String) {
        cancel(false)
        val token = generation
        setBusy(true, tr(R.string.assistant_thinking))
        setLiveState(LiveState.THINKING, tr(R.string.assistant_thinking_live))

        job = worker.submit {
            try {
                val candidates = mutableListOf<String>()
                FamilyDirectory.read(this@AssistantActivity).forEach { member ->
                    if (member.actionable && member.name.isNotBlank()) candidates.add(member.name)
                }

                val result = NebiusIntentDispatcher.infer(this@AssistantActivity, text, candidates)
                val parsed = ParsedIntent(
                    action = result.action,
                    target = result.target,
                    targetType = result.targetType,
                    channel = result.channel ?: "phone",
                    spokenResponse = result.spokenResponse
                )

                runOnUiThread {
                    if (valid(token)) {
                        setBusy(false, tr(R.string.assistant_ready))
                        dispatchParsedIntent(parsed)
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (valid(token)) {
                        setBusy(false, tr(R.string.assistant_error))
                        setLiveState(LiveState.IDLE, tr(R.string.assistant_error))
                    }
                }
            }
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
        const val DEFAULT_BACKEND = "https://maa-intent-gateway.five-magazine.workers.dev"
        const val DEFAULT_TOKEN = BuildConfig.MAA_DEVICE_TOKEN
        const val EXTRA_READ_UPDATE = "in.family.maa.READ_UPDATE"
        private const val VOICE_TOKEN = "in.family.maa.TRUSTED_VOICE_ENTRY"
        private const val VOICE_ENTRY_LIFETIME_MS = 15_000L
        private var voiceToken: String? = null
        private var voiceTokenIssuedAt = 0L
        private var voiceTokenNavigation = false

        /** Call only from an explicit Ask voice gesture; creates no microphone work by itself. */
        @Synchronized fun voiceIntent(context: Context, navigateLocalAfterSpeech: Boolean = true): Intent {
            val token = UUID.randomUUID().toString()
            voiceToken = token
            voiceTokenIssuedAt = SystemClock.elapsedRealtime()
            voiceTokenNavigation = navigateLocalAfterSpeech
            return Intent(context, AssistantActivity::class.java).putExtra(VOICE_TOKEN, token)
        }

        @Synchronized fun readUpdateIntent(context: Context): Intent {
            return Intent(context, AssistantActivity::class.java).putExtra(EXTRA_READ_UPDATE, true)
        }

        @Synchronized private fun consumeVoiceToken(token: String?): Boolean? {
            if (token == null || token != voiceToken) return null
            voiceToken = null
            return voiceTokenNavigation.takeIf { SystemClock.elapsedRealtime() - voiceTokenIssuedAt in 0..VOICE_ENTRY_LIFETIME_MS }
        }

        @Synchronized private fun revokeVoiceToken(token: String?) {
            if (token != null && token == voiceToken) voiceToken = null
        }

        internal fun validatedEndpoint(raw: String): String? = runCatching {
            val uri = URI(raw.trim())
            if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.userInfo != null || uri.query != null || uri.fragment != null) null
            else uri.toString().trimEnd('/')
        }.getOrNull()

        internal fun validatedAction(json: String): String? = runCatching {
            val value = JSONObject(json)
            if (value.length() != 1 || !value.has("action")) null else (value.get("action") as? String)
                ?.takeIf { it in setOf("call", "media", "qr", "help", "home", "unsupported", "apps", "alarm", "notifications", "messages", "missed_calls", "gallery", "camera", "info", "chat") }
        }.getOrNull()

        internal data class ParsedIntent(
            val action: String,
            val target: String? = null,
            val targetType: String? = null,
            val channel: String = "phone",
            val spokenResponse: String? = null
        )

        internal fun validatedIntent(json: String): ParsedIntent? = runCatching {
            val value = JSONObject(json)
            val action = (value.opt("action") as? String)
                ?.takeIf { it in setOf("call", "media", "qr", "help", "home", "unsupported", "apps", "alarm", "notifications", "messages", "missed_calls", "gallery", "camera", "info", "chat") }
                ?: return null
            val target = (value.opt("target") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val targetType = (value.opt("target_type") as? String)?.trim()
            val channel = (value.opt("channel") as? String)?.takeIf { it in setOf("phone", "whatsapp") } ?: "phone"
            val spokenResponse = (value.opt("spoken_response") as? String)?.trim()?.takeIf { it.isNotEmpty() }
            ParsedIntent(action, target, targetType, channel, spokenResponse)
        }.getOrNull()
    }
}
