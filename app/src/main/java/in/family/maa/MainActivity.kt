package `in`.family.maa

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.graphics.Typeface
import android.widget.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    companion object {
        const val ONBOARDING_COMPLETE="onboarding_complete"
        fun toolsIntent(context:android.content.Context)=Intent(context,MainActivity::class.java).putExtra("show_tools",true)
    }
    private val prefs by lazy { getSharedPreferences("family", MODE_PRIVATE) }
    private lateinit var root: LinearLayout
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null
    private var ttsInitialized = false
    private var speechOutputAvailable = false
    private val localizedContext by lazy { AppLanguage.localizedContext(this) }
    private var languageContext: android.content.Context? = null
    private fun tr(id: Int, vararg args: Any) = (languageContext ?: localizedContext).getString(id,*args)
    private var recognizer: SpeechRecognizer? = null
    private var busy = false
    private var qrAmount: EditText? = null
    private var qrPayee = ""
    private var generation = 0
    private var screen = "home"
    private val settingsFields = mutableMapOf<String,EditText>()
    private var quietSwitch: Switch? = null
    private var guardSwitch: Switch? = null
    private var microphoneRequestGeneration = -1
    private val green = MaaDesign.green
    private var homeRoleButton: Button? = null
    private var deviceStatus: DeviceStatusView? = null
    private var homeDock:LinearLayout?=null
    private var onboardingLanguage="en"
    private var awaitingPhoto = false
    private val photoDraft = mutableMapOf<String,String>()
    private var pendingSettingsDraft:Bundle?=null
    private var pendingSettingsLanguage:String?=null
    private var unlockPromptOpen=false
    private val activeDialogs=mutableListOf<AlertDialog>()
    private fun showDialog(builder:AlertDialog.Builder) { builder.show().also { dialog -> activeDialogs.add(dialog);dialog.setOnDismissListener { activeDialogs.remove(dialog) } } }
    private fun dismissDialogs() { activeDialogs.toList().forEach { it.dismiss() };activeDialogs.clear();CaregiverLock.dismiss(this) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=MaaDesign.canvas;window.navigationBarColor=MaaDesign.canvas
        pendingSettingsDraft=state?.getBundle("lockedSettingsDraft")
        pendingSettingsLanguage=state?.getString("lockedSettingsLanguage")
        // Existing installs keep their chosen language and skip first-run setup.
        if(!prefs.contains(ONBOARDING_COMPLETE) && prefs.all.isNotEmpty()) prefs.edit().putBoolean(ONBOARDING_COMPLETE,true).apply()
        if(!prefs.contains("backend") || prefs.getString("backend", "").isNullOrBlank()) {
            prefs.edit().putString("backend", AssistantActivity.DEFAULT_BACKEND).putString("token", AssistantActivity.DEFAULT_TOKEN).apply()
        }
        when(state?.getString("screen")) {
            "onboarding" -> onboarding(state.getBundle("settingsDraft"))
            "settings" -> if(CaregiverLock.unlocked(this)) settings(state.getBundle("settingsDraft")) else { pendingSettingsDraft=state.getBundle("settingsDraft");home(false) }
            "contacts" -> contacts()
            "help" -> help()
            "tools" -> tools()
            "qr" -> showQr(state.getString("qrAmount"))
            else -> if(!prefs.getBoolean(ONBOARDING_COMPLETE,false)) onboarding()
                else if(intent.getBooleanExtra("show_tools",false)) tools() else home(pendingSettingsDraft==null)
        }
        tts = TextToSpeech(this,this)
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("screen",screen)
        if(screen=="settings" || screen=="onboarding") state.putBundle("settingsDraft",settingsDraft())
        pendingSettingsDraft?.let { state.putBundle("lockedSettingsDraft",it) }
        pendingSettingsLanguage?.let { state.putString("lockedSettingsLanguage",it) }
        if(screen=="qr") state.putString("qrAmount",qrAmount?.text?.toString())
        super.onSaveInstanceState(state)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var medicineCardContainer: LinearLayout? = null
    private var permissionBannerContainer: LinearLayout? = null
    private var permissionBannerDismissed = false

    enum class HomeAshaState {
        IDLE, ALERT, LISTENING, THINKING, SPEAKING
    }
    private var homeAshaState = HomeAshaState.IDLE
    private var homeAshaBubble: LinearLayout? = null
    private var homeAshaBubbleText: TextView? = null
    private var homeAshaActionRow: LinearLayout? = null
    private var homeVoiceButton: Button? = null
    private val dismissBubbleRunnable = Runnable {
        if (homeAshaActionRow?.visibility != View.VISIBLE) {
            homeAshaBubble?.visibility = View.GONE
        }
    }
    private var homePendingMedId: String? = null
    private var homePendingAction: (() -> Unit)? = null
    private var onSpeechDoneCallback: (() -> Unit)? = null
    private val audioFocusController by lazy { MaaAudioFocusController(this) { stopHomeVoice() } }
    private val voiceWorker = Executors.newSingleThreadExecutor()
    private var activeBackendJob: Future<*>? = null
    @Volatile private var activeBackendConn: HttpURLConnection? = null

    private val medicineTimerRunnable = object : Runnable {
        override fun run() {
            if (screen == "home" && !isDestroyed && !isFinishing) {
                updateMedicineCard()
                updateHomeVoiceAction()
                mainHandler.postDelayed(this, 30_000L)
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val medToConfirm = intent.getStringExtra("confirm_med_id")
        if (medToConfirm != null) {
            intent.removeExtra("confirm_med_id")
            MedicineManager.markConfirmed(this, medToConfirm)
            val ack = if (AppLanguage.isHindi(this)) "दवा दर्ज कर ली गई है। बहुत बढ़िया!" else "Medicine marked as taken. Great job!"
            speakHomeResponse(ack)
            updateMedicineCard()
            updateHomeVoiceAction()
        }
        if(intent.getBooleanExtra("show_tools",false)) tools() else home()
    }
    private val proactiveUpdateListener: () -> Unit = {
        runOnUiThread {
            if (screen == "home") {
                updateHomeVoiceAction()
                updateMedicineCard()
                updatePermissionBanner()
            }
        }
    }
    override fun onStart() {
        super.onStart()
        ProactiveAsha.addListener(proactiveUpdateListener)
    }
    override fun onResume() {
        super.onResume()
        if(screen=="settings" && !CaregiverLock.unlocked(this) && !awaitingPhoto) {
            pendingSettingsDraft=settingsDraft();home(false)
        }
        resumeSettingsUnlock()
        deviceStatus?.refresh()
        homeRoleButton?.apply { text=tr(if(HomeRoleController.isDefaultHome(this@MainActivity)) R.string.home_default_ready else R.string.home_make_default) }
        mainHandler.removeCallbacks(medicineTimerRunnable)
        if (screen == "home") {
            updatePermissionBanner()
            updateHomeVoiceAction()
            updateMedicineCard()
            mainHandler.postDelayed(medicineTimerRunnable, 30_000L)
        }
    }
    override fun onPause() {
        mainHandler.removeCallbacks(medicineTimerRunnable)
        stopHomeVoice()
        homeAshaBubble?.visibility = View.GONE
        audioFocusController.abandonFocus()
        super.onPause()
    }
    private fun resumeSettingsUnlock() {
        if(pendingSettingsDraft!=null && !unlockPromptOpen) {
            unlockPromptOpen=true
            CaregiverLock.request(this,onCancelled={pendingSettingsDraft=null;pendingSettingsLanguage=null;unlockPromptOpen=false}) {
                val preserved=pendingSettingsDraft
                pendingSettingsLanguage?.let { prefs.edit().putString(AppLanguage.KEY,it).apply();languageContext=AppLanguage.localizedContext(this);configureVoice() }
                pendingSettingsDraft=null;pendingSettingsLanguage=null;unlockPromptOpen=false;settings(preserved)
            }
        }
    }
    private fun reauthorizeSettings(draft:Bundle,language:String?=null) {
        pendingSettingsDraft=draft;pendingSettingsLanguage=language;home(false);resumeSettingsUnlock()
    }
    override fun onInit(code: Int) {
        if(code == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            configureVoice()
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread {
                        if (screen == "home" && utteranceId == "home_asha") {
                            homeAshaState = HomeAshaState.SPEAKING
                            updateHomeVoiceAction()
                        }
                    }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread {
                        if (screen == "home" && utteranceId == "home_asha") {
                            val cb = onSpeechDoneCallback
                            onSpeechDoneCallback = null
                            if (cb != null) {
                                // 350ms delay clears hardware DAC buffers before opening microphone
                                mainHandler.postDelayed({
                                    if (screen == "home" && !isFinishing && !isDestroyed) {
                                        cb.invoke()
                                    }
                                }, 350L)
                            } else {
                                homeAshaState = HomeAshaState.IDLE
                                updateHomeVoiceAction()
                            }
                        }
                    }
                }
                override fun onError(utteranceId: String?) {
                    runOnUiThread {
                        if (screen == "home" && utteranceId == "home_asha") {
                            homeAshaState = HomeAshaState.IDLE
                            updateHomeVoiceAction()
                        }
                    }
                }
            })
            if(screen=="home" && hasWindowFocus()) speak(tr(R.string.greeting))
        }
    }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun configureVoice() {
        if(ttsInitialized) {
            val result=tts?.setLanguage(AppLanguage.locale(this))
            speechOutputAvailable=result!=null && result>=TextToSpeech.LANG_AVAILABLE
        }
    }
    private fun speak(text: String) { status.text=text; if(speechOutputAvailable) tts?.speak(text,TextToSpeech.QUEUE_FLUSH,null,"maa") }
    private fun switchLanguage() {
        val destination=screen
        val draft=if(screen=="settings") settingsDraft() else null
        val amount=qrAmount?.text?.toString()
        val nextLanguage=if(AppLanguage.code(this)=="hi") "en" else "hi"
        if(screen=="settings" && !CaregiverLock.unlocked(this)) { reauthorizeSettings(draft!!,nextLanguage);return }
        cancelPendingVoice()
        prefs.edit().putString(AppLanguage.KEY,nextLanguage).apply()
        languageContext=AppLanguage.localizedContext(this)
        configureVoice()
        when(destination) {
            "settings" -> settings(draft)
            "contacts" -> contacts()
            "help" -> help()
            "tools" -> tools()
            "qr" -> showQr(amount)
            else -> home()
        }
    }
    private fun cancelPendingVoice() {
        generation++; busy=false
        recognizer?.destroy(); recognizer=null
        tts?.stop()
    }
    private fun surface(title: String, destination: String) {
        dismissDialogs()
        cancelPendingVoice(); stopHomeVoice(); screen=destination; qrAmount=null; homeRoleButton=null; deviceStatus=null;homeDock=null; medicineCardContainer=null; permissionBannerContainer=null; homeAshaBubble=null; homeAshaBubbleText=null; homeAshaActionRow=null; homeVoiceButton=null; mainHandler.removeCallbacks(medicineTimerRunnable)
        settingsFields.clear(); quietSwitch=null; guardSwitch=null
        root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            clipChildren=false; clipToPadding=false
            setPadding(dp(16),dp(12),dp(16),dp(if(destination=="home") 10 else 24))
            setBackgroundColor(MaaDesign.canvas)
        }
        val scroll=ScrollView(this).apply {
            isFillViewport=true
            clipChildren=false; clipToPadding=false
            addView(root)
        }
        val frame=FrameLayout(this).apply {
            clipChildren=false; clipToPadding=false
            if(destination=="home") {
                val column=LinearLayout(this@MainActivity).apply {
                    orientation=LinearLayout.VERTICAL
                    clipChildren=false; clipToPadding=false
                    setBackgroundColor(MaaDesign.canvas)
                }
                column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
                homeDock=LinearLayout(this@MainActivity).apply {
                    orientation=LinearLayout.VERTICAL
                    clipChildren=false; clipToPadding=false
                    setPadding(dp(16),dp(4),dp(16),dp(12))
                }
                column.addView(homeDock,LinearLayout.LayoutParams(-1,-2))
                addView(column,FrameLayout.LayoutParams(-1,-1))
            } else addView(scroll,FrameLayout.LayoutParams(-1,-1))
            setOnApplyWindowInsetsListener { view,insets ->
                if(android.os.Build.VERSION.SDK_INT>=30) {
                    val safe=insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout() or android.view.WindowInsets.Type.ime())
                    view.setPadding(safe.left,safe.top,safe.right,safe.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft,insets.systemWindowInsetTop,insets.systemWindowInsetRight,insets.systemWindowInsetBottom)
                }
                insets
            }
        }; setContentView(frame)
        if(android.os.Build.VERSION.SDK_INT>=30) {
            val lightBars=android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(lightBars,lightBars)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility=android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        if(destination!="home") root.addView(LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(8))
            addView(TextView(this@MainActivity).apply { text=title; textSize=29f; setTypeface(typeface,Typeface.BOLD); setTextColor(MaaDesign.ink); gravity=Gravity.START; layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
            if(destination=="settings") addView(Button(this@MainActivity).apply {
                id=R.id.language_switch; text=tr(R.string.switch_language); contentDescription=tr(R.string.switch_language_description)
                isAllCaps=false; textSize=18f; minHeight=dp(48); setTextColor(green)
                background=MaaDesign.shape(this@MainActivity,MaaDesign.pale,24)
                setOnClickListener { switchLanguage() }
            })
        })
        status=TextView(this).apply { text=""; textSize=18f; setTextColor(MaaDesign.muted); gravity=Gravity.START; accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE }
        if(destination!="home") root.addView(status)
    }
    private fun button(text: String, action: () -> Unit) {
        root.addView(MaaDesign.action(this,text,true,action).apply {
            textSize=if(prefs.getBoolean("large_text",false)) 28f else 24f
            minHeight=dp(72)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
        })
    }
    private fun home(clearPendingDraft:Boolean=true) {
        if(clearPendingDraft) { pendingSettingsDraft=null;pendingSettingsLanguage=null }
        unlockPromptOpen=false
        surface(tr(R.string.home_title),"home")
        photoDraft.clear()
        deviceStatus=DeviceStatusView(this).apply {
            onOpenDetails=null
            onOpenSettings={ openSettings() }
            onSwitchLanguage={ switchLanguage() }
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
        }; root.addView(deviceStatus)
        permissionBannerContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        root.addView(permissionBannerContainer)
        updatePermissionBanner()
        val faces=LinearLayout(this).apply {
            orientation=if(resources.configuration.fontScale>=1.5f) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL;gravity=Gravity.CENTER
            background=MaaDesign.shape(this@MainActivity,Color.WHITE,24)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
        }
        for(i in 1..3) {
            val number=prefs.getString("phone$i","")!!
            val name=prefs.getString("name$i", tr(R.string.family_number,i))!!.ifBlank { tr(R.string.family_number,i) }
            val card=LinearLayout(this).apply {
                orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;minimumHeight=dp(96);isFocusable=true;isClickable=true
                layoutParams=if(faces.orientation==LinearLayout.HORIZONTAL) LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(3),0,dp(3),0) } else LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) }
                setPadding(dp(4),dp(8),dp(4),dp(8));background=MaaDesign.shape(this@MainActivity,Color.TRANSPARENT,24)
                contentDescription=if(number.isBlank()) tr(R.string.home_empty_family) else name
                setOnClickListener {
                    if(number.isBlank()) {
                        Toast.makeText(this@MainActivity,tr(R.string.home_settings_gesture),Toast.LENGTH_LONG).show()
                    } else {
                        val canonical=FamilyDirectory.canonicalNumber(number)
                        speak(tr(R.string.assistant_calling_person, name))
                        val targetNumber = canonical ?: number.trim()
                        CommunicationIntents.callDirect(this@MainActivity, targetNumber)
                    }
                }
            }
            val image=ImageView(this).apply {
                layoutParams=LinearLayout.LayoutParams(dp(56),dp(56));scaleType=ImageView.ScaleType.CENTER_CROP;importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO
                background=GradientDrawable().apply { shape=GradientDrawable.OVAL;setColor(MaaDesign.pale) };clipToOutline=true
                setImageDrawable(MaaIcon(this@MainActivity,"person",green));setPadding(dp(16),dp(16),dp(16),dp(16))
            }
            val photo=prefs.getString("photo$i","").orEmpty()
            if(photo.isNotBlank()) PhotoThumbnails.load(image,Uri.parse(photo),dp(100))
            card.addView(image)
            card.addView(TextView(this).apply { text=if(number.isBlank()) "+ " + tr(R.string.family_number,i) else name; textSize=18f; gravity=Gravity.CENTER;setTypeface(typeface,Typeface.BOLD);setTextColor(MaaDesign.ink);setPadding(0,dp(4),0,0);importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO })
            faces.addView(card)
        }
        root.addView(faces)
        medicineCardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        root.addView(medicineCardContainer)
        updateMedicineCard()
        data class Tile(val label:Int,val symbol:String,val color:Int,val id:Int,val action:()->Unit)
        val entries=listOf(
            Tile(R.string.home_phone,"phone",0xffe5f3e9.toInt(),R.id.home_phone_action) { launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_MODE, "phone")) },
            Tile(R.string.home_missed_calls,"missed_call",0xfffde8e8.toInt(),R.id.home_missed_calls_action) { launch(Intent(this,MissedCallsActivity::class.java)) },
            Tile(R.string.home_messages,"message",0xffe3f1fc.toInt(),R.id.home_messages_action) { launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL,"sms").putExtra(CommunicationActivity.EXTRA_MODE, "messages")) },
            Tile(R.string.home_gallery,"gallery",0xfff2e7fe.toInt(),R.id.home_gallery_action) { launch(Intent(this,GalleryActivity::class.java)) },
            Tile(R.string.home_camera,"camera",0xfffce7f3.toInt(),R.id.home_chat_action) { launch(Intent(this,CameraActivity::class.java)) },
            Tile(R.string.home_apps,"apps",0xfffbefda.toInt(),R.id.home_apps_action) { launch(Intent(this,AppsActivity::class.java)) }
        )
        val columns=if(prefs.getBoolean("one_column",false) || resources.configuration.fontScale>=1.5f) 1 else 2
        entries.chunked(columns).forEach { pair ->
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL;isBaselineAligned=false }
            pair.forEach { item -> row.addView(MaaDesign.tile(this,tr(item.label),item.symbol,item.color,item.action).apply {
                id=item.id;textSize=if(prefs.getBoolean("large_text",false)) 26f else 21f
                layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),dp(10)) }
            }) };root.addView(row)
        }
        homeAshaBubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.rgb(236, 253, 245))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.rgb(15, 119, 113))
            }
            elevation = dp(3).toFloat()
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
        }

        val bubbleHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val bubbleIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(6) }
            setImageDrawable(MaaIcon(this@MainActivity, "mic", Color.rgb(15, 119, 113)))
        }
        bubbleHeader.addView(bubbleIcon)
        val bubbleTitle = TextView(this).apply {
            text = AssistantIdentity.displayName(this@MainActivity)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(15, 119, 113))
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        bubbleHeader.addView(bubbleTitle)
        val bubbleClose = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(156, 163, 175))
            setPadding(dp(8), 0, dp(4), 0)
            isClickable = true
            isFocusable = true
            setOnClickListener { dismissHomeAsha() }
        }
        bubbleHeader.addView(bubbleClose)
        homeAshaBubble?.addView(bubbleHeader)

        homeAshaBubbleText = TextView(this).apply {
            text = ""
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(15, 50, 48))
            setPadding(0, dp(4), 0, dp(4))
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        homeAshaBubble?.addView(homeAshaBubbleText)

        homeAshaActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(4) }
        }
        homeAshaBubble?.addView(homeAshaActionRow)
        homeDock?.addView(homeAshaBubble)

        val voiceActionBtn = MaaDesign.action(this,AssistantIdentity.askLabel(this),true) { onHomeVoiceActionClicked() }.apply {
            id=R.id.home_voice_action;textSize=26f;minHeight=dp(76);minimumHeight=dp(76)
            setTypeface(typeface,Typeface.BOLD);maxLines=2;ellipsize=android.text.TextUtils.TruncateAt.END
            setAutoSizeTextTypeUniformWithConfiguration(18,28,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            contentDescription=AssistantIdentity.askLabel(this@MainActivity)
            setPadding(dp(16),dp(12),dp(16),dp(12));compoundDrawablePadding=dp(14)
            val mic=MaaIcon(this@MainActivity,"mic",Color.WHITE).apply { setBounds(0,0,dp(44),dp(44)) }
            setCompoundDrawables(mic,null,null,null)
            background=MaaDesign.shape(this@MainActivity,Color.rgb(15,119,113),24)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(2) }
        }
        homeDock?.addView(voiceActionBtn)
        homeVoiceButton = voiceActionBtn
        updateHomeVoiceAction()
    }

    private fun updatePermissionBanner() {
        val container = permissionBannerContainer ?: return
        container.removeAllViews()
        if (permissionBannerDismissed || PermissionsHelper.hasAllRuntimePermissions(this)) {
            container.visibility = View.GONE
            return
        }
        val isHi = AppLanguage.isHindi(this)
        val banner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.rgb(255, 251, 235))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), Color.rgb(245, 158, 11))
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                PermissionsHelper.showPermissionSetupDialog(this@MainActivity) {
                    updatePermissionBanner()
                    updateMedicineCard()
                    updateHomeVoiceAction()
                }
            }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(8) }
            setImageDrawable(MaaIcon(this@MainActivity, "bell", Color.rgb(217, 119, 6)))
        }
        banner.addView(icon)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val titleText = TextView(this).apply {
            text = if (isHi) "⚠️ आवश्यक अनुमतियाँ सेट करें" else "⚠️ Permissions Setup Needed"
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(146, 64, 14))
        }
        val subText = TextView(this).apply {
            text = if (isHi) "कैमरा, माइक और रिमाइंडर के लिए यहाँ दबाएँ" else "Tap here to allow Camera, Mic & Reminders"
            textSize = 13f
            setTextColor(Color.rgb(180, 83, 9))
        }
        textCol.addView(titleText)
        textCol.addView(subText)
        banner.addView(textCol)

        val actionBtn = Button(this).apply {
            text = if (isHi) "सेट करें" else "Setup"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = MaaDesign.shape(this@MainActivity, Color.rgb(217, 119, 6), 12)
            minHeight = dp(36); minimumHeight = dp(36)
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(-2, dp(36)).apply { marginEnd = dp(6) }
            setOnClickListener {
                PermissionsHelper.showPermissionSetupDialog(this@MainActivity) {
                    updatePermissionBanner()
                    updateMedicineCard()
                    updateHomeVoiceAction()
                }
            }
        }
        banner.addView(actionBtn)

        val dismissBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.rgb(180, 83, 9))
            setPadding(dp(6), dp(2), dp(6), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener {
                permissionBannerDismissed = true
                container.visibility = View.GONE
            }
        }
        banner.addView(dismissBtn)

        container.addView(banner)
        container.visibility = View.VISIBLE
    }

    private fun updateMedicineCard() {
        val container = medicineCardContainer ?: return
        container.removeAllViews()
        container.addView(renderMedicineCard())
    }

    private fun renderMedicineCard(): View {
        val status = MedicineManager.getNextMedicineStatus(this)
        val isHi = AppLanguage.isHindi(this)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val bgColor = if (status.isDue) Color.rgb(254, 242, 242) else Color.WHITE
            background = MaaDesign.shape(this@MainActivity, bgColor, 22, true)
            if (status.isDue) {
                (background as? GradientDrawable)?.setStroke(dp(2), Color.rgb(220, 38, 38))
            }
            setPadding(dp(14), dp(10), dp(14), dp(10))
            elevation = dp(2).toFloat()
            clipChildren = false; clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            isClickable = true
            isFocusable = true
            contentDescription = "${status.title}, ${status.countdownText}"
            setOnClickListener {
                speakHomeResponse("${status.title}。 ${status.countdownText}")
            }
            setOnLongClickListener {
                MedicineManager.showSetupDialog(this@MainActivity) {
                    updateMedicineCard()
                    updateHomeVoiceAction()
                }
                true
            }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconColor = if (status.isDue) Color.rgb(220, 38, 38) else if (status.allTaken) Color.rgb(5, 150, 105) else Color.rgb(15, 119, 113)
        val badgeBgColor = if (status.isDue) Color.rgb(254, 226, 226) else if (status.allTaken) Color.rgb(209, 250, 229) else Color.rgb(224, 242, 254)

        val badge = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(10) }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(badgeBgColor)
            }
            setImageDrawable(MaaIcon(this@MainActivity, "pill", iconColor))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        topRow.addView(badge)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }

        val titleView = TextView(this).apply {
            text = status.title
            textSize = 19f
            setTextColor(if (status.isDue) Color.rgb(185, 28, 28) else MaaDesign.ink)
            setTypeface(typeface, Typeface.BOLD)
        }
        textCol.addView(titleView)

        if (status.countdownText.isNotBlank()) {
            val countView = TextView(this).apply {
                text = status.countdownText
                textSize = 15f
                setTextColor(if (status.isDue) Color.rgb(220, 38, 38) else Color.rgb(55, 65, 81))
                setTypeface(typeface, Typeface.NORMAL)
                setPadding(0, dp(1), 0, 0)
            }
            textCol.addView(countView)
        }
        topRow.addView(textCol)

        if (!status.allTaken && status.medicine != null) {
            val confirmBtn = Button(this).apply {
                text = "✓ " + if (isHi) "दवा ले ली" else "Taken"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                isAllCaps = false
                minHeight = dp(42); minimumHeight = dp(42)
                setTextColor(Color.WHITE)
                background = MaaDesign.shape(this@MainActivity, if (status.isDue) Color.rgb(220, 38, 38) else Color.rgb(15, 119, 113), 14)
                stateListAnimator = null
                elevation = dp(2).toFloat()
                layoutParams = LinearLayout.LayoutParams(-2, dp(42)).apply { marginStart = dp(8) }
                setOnClickListener {
                    confirmMedicineOnHome(status.medicine.id)
                }
            }
            topRow.addView(confirmBtn)
        }

        card.addView(topRow)
        return card
    }

    private fun updateHomeVoiceAction() {
        val voiceBtn = homeVoiceButton ?: findViewById<Button>(R.id.home_voice_action) ?: return
        val isHi = AppLanguage.isHindi(this)
        voiceBtn.setTextColor(Color.WHITE)

        when (homeAshaState) {
            HomeAshaState.LISTENING -> {
                val label = if (isHi) "🎙️ सुन रही हूँ... बोलिए\n(रोकने के लिए दबाएँ)" else "🎙️ Listening... Speak\n(Tap to stop)"
                voiceBtn.text = label
                voiceBtn.contentDescription = label
                voiceBtn.background = MaaDesign.shape(this, Color.rgb(5, 150, 105), 28)
                val mic = MaaIcon(this, "mic", Color.WHITE).apply { setBounds(0, 0, dp(56), dp(56)) }
                voiceBtn.setCompoundDrawables(mic, null, null, null)
            }
            HomeAshaState.THINKING -> {
                val label = if (isHi) "⏳ सोच रही हूँ...\n(कृपया रुकें)" else "⏳ Thinking...\n(Please wait)"
                voiceBtn.text = label
                voiceBtn.contentDescription = label
                voiceBtn.background = MaaDesign.shape(this, Color.rgb(2, 132, 199), 28)
                val mic = MaaIcon(this, "mic", Color.WHITE).apply { setBounds(0, 0, dp(56), dp(56)) }
                voiceBtn.setCompoundDrawables(mic, null, null, null)
            }
            HomeAshaState.SPEAKING -> {
                val label = if (isHi) "🔊 आशा बोल रही है...\n(रोकने के लिए दबाएँ)" else "🔊 Asha is speaking...\n(Tap to stop)"
                voiceBtn.text = label
                voiceBtn.contentDescription = label
                voiceBtn.background = MaaDesign.shape(this, Color.rgb(15, 119, 113), 28)
                val mic = MaaIcon(this, "mic", Color.WHITE).apply { setBounds(0, 0, dp(56), dp(56)) }
                voiceBtn.setCompoundDrawables(mic, null, null, null)
            }
            HomeAshaState.ALERT -> {
                val hasDueMed = MedicineManager.hasDueMedicine(this)
                val photoAlert = ProactiveAsha.showsPhotoAlert(this)
                val label = when {
                    hasDueMed -> if (isHi) "🚨 दवा का समय हो गया है!\nसुनने के लिए दबाएँ" else "🚨 Medicine Time!\nTap to listen"
                    photoAlert -> if (isHi) "नई फ़ोटो आई है!\nदेखने के लिए दबाएँ" else "New photo arrived!\nTap to see"
                    else -> if (isHi) "🚨 नया अपडेट आया है!\nसुनने के लिए दबाएँ" else "🚨 New Alert!\nTap to listen"
                }
                voiceBtn.text = label
                voiceBtn.contentDescription = label
                voiceBtn.background = MaaDesign.shape(this, if (photoAlert) MaaDesign.photoYellow else Color.rgb(220, 38, 38), 28)
                val foreground = if (photoAlert) MaaDesign.ink else Color.WHITE
                voiceBtn.setTextColor(foreground)
                val iconSymbol = when {
                    hasDueMed -> "pill"
                    photoAlert -> "photo"
                    else -> "bell"
                }
                val icon = MaaIcon(this, iconSymbol, foreground).apply { setBounds(0, 0, dp(56), dp(56)) }
                voiceBtn.setCompoundDrawables(icon, null, null, null)
            }
            HomeAshaState.IDLE -> {
                val hasAlert = ProactiveAsha.hasUnreadUpdates(this)
                if (hasAlert) {
                    homeAshaState = HomeAshaState.ALERT
                    updateHomeVoiceAction()
                    return
                }
                val label = "${AssistantIdentity.askLabel(this)}\n${if (isHi) "(बोलने के लिए दबाएँ)" else "(Tap to speak)"}"
                voiceBtn.text = label
                voiceBtn.contentDescription = label
                voiceBtn.background = MaaDesign.shape(this, Color.rgb(15, 119, 113), 28)
                val mic = MaaIcon(this, "mic", Color.WHITE).apply { setBounds(0, 0, dp(56), dp(56)) }
                voiceBtn.setCompoundDrawables(mic, null, null, null)
            }
        }
    }

    private fun onHomeVoiceActionClicked() {
        when (homeAshaState) {
            HomeAshaState.LISTENING, HomeAshaState.THINKING, HomeAshaState.SPEAKING -> {
                stopHomeVoice()
            }
            HomeAshaState.ALERT -> {
                executeHomeReadAlert()
            }
            HomeAshaState.IDLE -> {
                startHomeSpeechListening()
            }
        }
    }

    private fun executeHomeReadAlert() {
        val summary = ProactiveAsha.getUnreadSummary(this)
        if (summary.opensDirectly) {
            homeAshaState = HomeAshaState.IDLE
            launch(summary.actionIntent)
            return
        }
        if (summary.medicineId == null) {
            ProactiveAsha.markAllUpdatesViewed(this)
        }

        showHomeAshaBubble(summary.spokenText)
        homeAshaActionRow?.removeAllViews()

        if (summary.medicineId != null) {
            val medId = summary.medicineId
            homePendingMedId = medId
            val confirmBtn = Button(this).apply {
                text = "✓ " + if (AppLanguage.isHindi(this@MainActivity)) "हाँ, ले ली है" else "Yes, Taken"
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = MaaDesign.shape(this@MainActivity, Color.rgb(5, 150, 105), 14)
                minHeight = dp(44); minimumHeight = dp(44)
                layoutParams = LinearLayout.LayoutParams(-2, dp(44)).apply { marginEnd = dp(8) }
                setOnClickListener { confirmMedicineOnHome(medId) }
            }
            val snoozeBtn = Button(this).apply {
                text = "⏰ " + if (AppLanguage.isHindi(this@MainActivity)) "15 मिनट बाद" else "15m Later"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(55, 65, 81))
                background = MaaDesign.shape(this@MainActivity, Color.rgb(229, 231, 235), 14)
                minHeight = dp(44); minimumHeight = dp(44)
                layoutParams = LinearLayout.LayoutParams(-2, dp(44))
                setOnClickListener { snoozeMedicineOnHome(medId) }
            }
            homeAshaActionRow?.addView(confirmBtn)
            homeAshaActionRow?.addView(snoozeBtn)
            homeAshaActionRow?.visibility = View.VISIBLE
        } else {
            homePendingAction = {
                runCatching { startActivity(summary.actionIntent) }
                Unit
            }
            val actBtn = Button(this).apply {
                text = summary.actionLabel
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = MaaDesign.shape(this@MainActivity, Color.rgb(15, 119, 113), 14)
                minHeight = dp(44); minimumHeight = dp(44)
                layoutParams = LinearLayout.LayoutParams(-2, dp(44))
                setOnClickListener {
                    homePendingAction?.invoke()
                    dismissHomeAsha()
                }
            }
            homeAshaActionRow?.addView(actBtn)
            homeAshaActionRow?.visibility = View.VISIBLE
        }

        speakHomeText(summary.spokenText) {
            runOnUiThread {
                if (screen == "home" && (homePendingMedId != null || homePendingAction != null)) {
                    startHomeSpeechListening()
                } else {
                    homeAshaState = HomeAshaState.IDLE
                    updateHomeVoiceAction()
                }
            }
        }
    }

    private fun startHomeSpeechListening() {
        if (busy) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphoneRequestGeneration = generation
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 10)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            val err = if (AppLanguage.isHindi(this)) "वॉइस पहचान उपलब्ध नहीं है।" else "Speech recognition not available."
            speakHomeResponse(err)
            return
        }
        cancelPendingVoice()
        homeAshaState = HomeAshaState.LISTENING
        updateHomeVoiceAction()
        val isHi = AppLanguage.isHindi(this)
        homeAshaBubble?.visibility = View.GONE
        homeAshaActionRow?.visibility = View.GONE

        val speechGeneration = generation
        fun current() = !isDestroyed && !isFinishing && generation == speechGeneration && screen == "home"

        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this)
            busy = true
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) = Unit
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    if (current()) {
                        homeAshaState = HomeAshaState.THINKING
                        updateHomeVoiceAction()
                        showHomeAshaBubble(if (isHi) "सोच रही हूँ..." else "Thinking...")
                    }
                }
                override fun onError(error: Int) {
                    if (!current()) return
                    cancelPendingVoice()
                    homeAshaState = HomeAshaState.IDLE
                    updateHomeVoiceAction()
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> if (isHi) "आवाज़ सुनाई नहीं दी। फिर से बोलें।" else "Didn't hear anything. Try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (isHi) "कोई आवाज़ नहीं आई। फिर से बोलें।" else "No voice heard. Try again."
                        else -> if (isHi) "सुनने में समस्या आई।" else "Recognition error."
                    }
                    showHomeAshaBubble(msg, autoDismissSeconds = 5L)
                }
                override fun onPartialResults(results: Bundle?) {
                    if (!current()) return
                    val partial = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    if (!partial.isNullOrBlank()) {
                        showHomeAshaBubble("\"$partial...\"")
                    }
                }
                override fun onResults(results: Bundle?) {
                    if (!current()) return
                    cancelPendingVoice()
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                    if (heard.isBlank()) {
                        homeAshaState = HomeAshaState.IDLE
                        updateHomeVoiceAction()
                        showHomeAshaBubble(if (isHi) "आवाज़ सुनाई नहीं दी। फिर से बोलें।" else "Didn't catch that. Please speak again.", autoDismissSeconds = 5L)
                    } else {
                        showHomeAshaBubble("\"$heard\"")
                        handleHomeVoiceInput(heard)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, AppLanguage.locale(this@MainActivity).toLanguageTag())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        } catch (_: RuntimeException) {
            cancelPendingVoice()
            homeAshaState = HomeAshaState.IDLE
            updateHomeVoiceAction()
            showHomeAshaBubble(if (isHi) "माइक शुरू नहीं हो सका।" else "Could not start microphone.")
        }
    }

    private fun handleHomeVoiceInput(heard: String) {
        val lower = heard.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।')

        if (homePendingMedId != null) {
            val medId = homePendingMedId!!
            val affirmative = setOf("हाँ", "हां", "ha", "haan", "yes", "sure", "ok", "okay", "ले ली", "खा ली", "ले ली है", "खा ली है", "taken", "done", "le li", "kha li")
            val negative = setOf("नहीं", "ना", "nahi", "no", "cancel", "बाद में", "mat", "baad me", "not yet")
            if (affirmative.any { lower.contains(it) }) {
                confirmMedicineOnHome(medId)
                return
            } else if (negative.any { lower.contains(it) }) {
                snoozeMedicineOnHome(medId)
                return
            }
            homePendingMedId = null
        }

        if (homePendingAction != null) {
            val affirmative = setOf("हाँ", "हां", "ha", "haan", "yes", "sure", "ok", "okay", "दिखाओ", "दिखाइए", "देखना", "open", "खोलो", "kholo", "dekho", "dekhna", "कॉल करो", "call", "फोन करो")
            val negative = setOf("नहीं", "ना", "nahi", "no", "cancel", "बंद करो", "mat")
            if (affirmative.any { lower.contains(it) }) {
                val act = homePendingAction
                homePendingAction = null
                act?.invoke()
                dismissHomeAsha()
                return
            } else if (negative.any { lower.contains(it) }) {
                homePendingAction = null
                val ack = if (AppLanguage.isHindi(this)) "ठीक है।" else "Okay."
                speakHomeResponse(ack)
                return
            }
            homePendingAction = null
        }

        if (MedicineScanner.isScanRequest(heard)) {
            speakHomeResponse(tr(R.string.scan_open))
            root.postDelayed({ launch(Intent(this, MedicineScanActivity::class.java)) }, 1500)
            return
        }

        if (lower.contains("दवा") || lower.contains("medicine") || lower.contains("tablet") || lower.contains("गोली")) {
            val status = MedicineManager.getNextMedicineStatus(this)
            val isHi = AppLanguage.isHindi(this)
            val reply = if (isHi) {
                if (status.isDue) {
                    "दवा का समय हो गया है! ${status.title}। ${status.countdownText}"
                } else if (status.allTaken) {
                    "आज की सभी दवाएँ ले ली गई हैं। ${status.countdownText}"
                } else {
                    "${status.title}। ${status.countdownText}"
                }
            } else {
                "${status.title}. ${status.countdownText}"
            }
            showHomeAshaBubble(reply)
            homeAshaActionRow?.removeAllViews()
            if (status.isDue && status.medicine != null) {
                homePendingMedId = status.medicine.id
                val confirmBtn = Button(this).apply {
                    text = "✓ " + if (isHi) "हाँ, ले ली है" else "Yes, Taken"
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.WHITE)
                    background = MaaDesign.shape(this@MainActivity, Color.rgb(5, 150, 105), 14)
                    minHeight = dp(44); minimumHeight = dp(44)
                    layoutParams = LinearLayout.LayoutParams(-2, dp(44))
                    setOnClickListener { confirmMedicineOnHome(status.medicine.id) }
                }
                homeAshaActionRow?.addView(confirmBtn)
                homeAshaActionRow?.visibility = View.VISIBLE
            }
            speakHomeResponse(reply)
            return
        }

        val videoCall = CommunicationIntents.videoCall(this, heard)
        if (videoCall != null) {
            speakHomeResponse(videoCall.announcement)
            root.postDelayed({ launch(videoCall.intent) }, 1800)
            return
        }

        val localCall = extractLocalCall(heard)
        if (localCall != null) {
            val match = FamilyDirectory.findMatch(this, localCall)
            if (match != null && match.canonicalNumber != null) {
                val announcement = AppLanguage.localizedContext(this).getString(R.string.assistant_calling_person, match.name)
                speakHomeResponse(announcement)
                root.postDelayed({ CommunicationIntents.callDirect(this@MainActivity, match.canonicalNumber) }, 1000)
                return
            }
        }

        val action = localAction(heard)
        if (action != null) {
            dispatchHomeLocalAction(action)
            return
        }

        callHomeBackend(heard)
    }

    private fun extractLocalCall(text: String): String? {
        val lower = text.lowercase(Locale.ROOT).trim().trim('.', '!', '?', '।').replace(Regex("\\s+"), " ")
        val enMatch = Regex("^(?:call|dial|phone)\\s+(.+)$").find(lower)
        if (enMatch != null) return enMatch.groupValues[1].trim()
        val hiPrefix = Regex("^(?:कॉल|फोन)\\s+(.+)$").find(lower)
        if (hiPrefix != null) return hiPrefix.groupValues[1].trim()
        val hiSuffix = Regex("^(.+?)\\s+को\\s+(?:फोन|कॉल)(?:\\s+(?:करो|लगाओ))?$").find(lower)
        if (hiSuffix != null) return hiSuffix.groupValues[1].trim()
        return null
    }

    private fun localAction(text: String): String? {
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
            "gallery", "गैलरी", "तस्वीरें", "फोटो दिखाओ", "फ़ोटो दिखाओ", "show photos" -> "gallery"
            "camera", "open camera", "take photo", "record video", "कैमरा", "कैमरा खोलो", "फोटो खींचो", "फ़ोटो खींचो", "वीडियो बनाओ" -> "camera"
            "qr", "pay", "payment", "पैसे", "भुगतान" -> "qr"
            else -> null
        }
    }

    private fun dispatchHomeLocalAction(action: String) {
        val isHi = AppLanguage.isHindi(this)
        homeAshaBubble?.visibility = View.GONE
        when (action) {
            "missed_calls" -> {
                speakHomeResponse(if (isHi) "मिस्ड कॉल खोल रही हूँ..." else "Opening missed calls...")
                root.postDelayed({ launch(Intent(this, MissedCallsActivity::class.java)) }, 1000)
            }
            "gallery" -> {
                speakHomeResponse(if (isHi) "फोटो गैलरी खोल रही हूँ..." else "Opening photos...")
                root.postDelayed({ launch(Intent(this, GalleryActivity::class.java)) }, 1000)
            }
            "camera" -> {
                speakHomeResponse(if (isHi) "कैमरा खोल रही हूँ..." else "Opening camera...")
                root.postDelayed({ launch(Intent(this, CameraActivity::class.java)) }, 1000)
            }
            "family", "call" -> {
                speakHomeResponse(if (isHi) "परिवार के नंबर खोल रही हूँ..." else "Opening family calls...")
                root.postDelayed({ launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_MODE, "phone")) }, 1000)
            }
            "messages" -> {
                speakHomeResponse(if (isHi) "मैसेज खोल रही हूँ..." else "Opening messages...")
                root.postDelayed({ launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "sms").putExtra(CommunicationActivity.EXTRA_MODE, "messages")) }, 1000)
            }
            "media" -> {
                speakHomeResponse(if (isHi) "व्हाट्सऐप खोल रही हूँ..." else "Opening WhatsApp...")
                root.postDelayed({ launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL, "whatsapp").putExtra(CommunicationActivity.EXTRA_MODE, "messages")) }, 1000)
            }
            "apps" -> {
                speakHomeResponse(if (isHi) "सभी ऐप्स खोल रही हूँ..." else "Opening apps...")
                root.postDelayed({ launch(Intent(this, AppsActivity::class.java)) }, 1000)
            }
            "alarm" -> {
                speakHomeResponse(if (isHi) "अलार्म खोल रही हूँ..." else "Opening alarms...")
                root.postDelayed({ launch(Intent(this, AlarmActivity::class.java)) }, 1000)
            }
            "help" -> {
                speakHomeResponse(if (isHi) "मदद स्क्रीन खोल रही हूँ..." else "Opening emergency help...")
                root.postDelayed({ launch(Intent(this, SosActivity::class.java)) }, 1000)
            }
            "notifications" -> {
                speakHomeResponse(if (isHi) "सूचनाएँ खोल रही हूँ..." else "Opening notifications...")
                root.postDelayed({ launch(Intent(this, NotificationCenterActivity::class.java)) }, 1000)
            }
            "qr" -> {
                capture()
            }
            "home" -> {
                dismissHomeAsha()
            }
        }
    }

    private fun callHomeBackend(text: String) {
        homeAshaState = HomeAshaState.THINKING
        updateHomeVoiceAction()
        val isHi = AppLanguage.isHindi(this)
        showHomeAshaBubble(if (isHi) "सोच रही हूँ..." else "Thinking...", autoDismissSeconds = 8L)

        activeBackendJob = voiceWorker.submit {
            try {
                val candidates = mutableListOf<String>()
                FamilyDirectory.read(this@MainActivity).forEach { member ->
                    if (member.actionable && member.name.isNotBlank()) candidates.add(member.name)
                }

                val result = NebiusIntentDispatcher.infer(this@MainActivity, text, candidates)

                runOnUiThread {
                    if (screen == "home" && !isDestroyed && !isFinishing) {
                        showHomeAshaBubble(result.spokenResponse, autoDismissSeconds = 4L)
                        speakHomeResponse(result.spokenResponse)

                        // If call action with target
                        if (result.action == "call" && !result.target.isNullOrBlank()) {
                            val targetName = result.target
                            val matched = FamilyDirectory.read(this@MainActivity).firstOrNull { member ->
                                member.name.equals(targetName, ignoreCase = true) ||
                                (targetName.contains("son", true) || targetName.contains("बेटा", true) || targetName.contains("लड़का", true)) && member.name.contains("Son", true) ||
                                (targetName.contains("daughter", true) || targetName.contains("बेटी", true) || targetName.contains("लड़की", true)) && member.name.contains("Daughter", true) ||
                                (targetName.contains("brother", true) || targetName.contains("भाई", true)) && member.name.contains("Brother", true)
                            }
                            val phoneToCall = matched?.canonicalNumber ?: matched?.rawNumber?.trim()
                            if (!phoneToCall.isNullOrBlank()) {
                                root.postDelayed({
                                    homeAshaBubble?.visibility = View.GONE
                                    CommunicationIntents.callDirect(this@MainActivity, phoneToCall)
                                }, 1200)
                                return@runOnUiThread
                            }
                        }

                        if (result.action != "info" && result.action != "chat" && result.action != "unsupported") {
                            root.postDelayed({
                                homeAshaBubble?.visibility = View.GONE
                                dispatchHomeLocalAction(result.action)
                            }, 1500)
                        }
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    if (screen == "home" && !isDestroyed && !isFinishing) {
                        val fallback = if (isHi) "माँ जी, मैंने आपकी बात सुन ली है।" else "Maa ji, I heard you."
                        showHomeAshaBubble(fallback, autoDismissSeconds = 3L)
                        speakHomeResponse(fallback)
                    }
                }
            }
        }
    }

    private fun speakHomeText(text: String, onDone: (() -> Unit)? = null) {
        onSpeechDoneCallback = onDone
        homeAshaState = HomeAshaState.SPEAKING
        updateHomeVoiceAction()
        audioFocusController.requestFocus()
        if (speechOutputAvailable) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "home_asha")
        } else {
            mainHandler.postDelayed({ onDone?.invoke() }, 2000L)
        }
    }

    private fun speakHomeResponse(text: String) {
        showHomeAshaBubble(text, autoDismissSeconds = 4L)
        speakHomeText(text) {
            runOnUiThread {
                if (screen == "home") {
                    homeAshaState = HomeAshaState.IDLE
                    updateHomeVoiceAction()
                    // Auto-hide the text bubble once speech is finished
                    mainHandler.postDelayed({
                        if (homeAshaActionRow?.visibility != View.VISIBLE) {
                            homeAshaBubble?.visibility = View.GONE
                        }
                    }, 2200L)
                }
            }
        }
    }

    private fun showHomeAshaBubble(text: String, autoDismissSeconds: Long = 0L) {
        homeAshaBubble?.visibility = View.VISIBLE
        homeAshaBubbleText?.text = text
        mainHandler.removeCallbacks(dismissBubbleRunnable)
        if (autoDismissSeconds > 0L) {
            mainHandler.postDelayed(dismissBubbleRunnable, autoDismissSeconds * 1000L)
        }
    }

    private fun confirmMedicineOnHome(medId: String) {
        homeAshaBubble?.visibility = View.GONE
        MedicineManager.markConfirmed(this, medId)
        homePendingMedId = null
        homeAshaActionRow?.removeAllViews()
        homeAshaActionRow?.visibility = View.GONE
        val isHi = AppLanguage.isHindi(this)
        val med = MedicineManager.all(this).firstOrNull { it.id == medId }
        val medName = med?.name(this) ?: if (isHi) "दवा" else "Medicine"
        val ack = if (isHi) "बहुत बढ़िया! मैंने दर्ज कर लिया है कि आपने $medName ले ली है।" else "Great! I have marked your $medName as taken."
        updateMedicineCard()
        speakHomeResponse(ack)
    }

    private fun snoozeMedicineOnHome(medId: String) {
        MedicineManager.snoozeMedicine(this, medId, 15)
        homePendingMedId = null
        homeAshaActionRow?.removeAllViews()
        homeAshaActionRow?.visibility = View.GONE
        val isHi = AppLanguage.isHindi(this)
        val ack = if (isHi) "ठीक है, मैं 15 मिनट बाद फिर याद दिलाऊँगी।" else "Okay, I will remind you again in 15 minutes."
        updateMedicineCard()
        speakHomeResponse(ack)
    }

    private fun stopHomeVoice() {
        cancelPendingVoice()
        audioFocusController.abandonFocus()
        activeBackendJob?.cancel(true)
        activeBackendJob = null
        homePendingMedId = null
        homePendingAction = null
        onSpeechDoneCallback = null
        tts?.stop()
        homeAshaState = HomeAshaState.IDLE
        updateHomeVoiceAction()
    }

    private fun dismissHomeAsha() {
        stopHomeVoice()
        homeAshaBubble?.visibility = View.GONE
    }
    private fun tools() {
        surface(tr(R.string.home_tools),"tools")
        val utilities=listOf(R.string.home_alarm to { launch(Intent(this,AlarmActivity::class.java)) },R.string.home_notifications to { launch(Intent(this,NotificationCenterActivity::class.java)) },R.string.home_qr to { capture() })
        utilities.forEach { (label,action) -> root.addView(MaaDesign.action(this,tr(label),false,action).apply { layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) } }) }
        button(tr(R.string.home_help)) { help() }
        button(tr(R.string.home_button)) { home() }
    }
    private fun openSettings() {
        cancelPendingVoice()
        showDialog(AlertDialog.Builder(this).setTitle(tr(R.string.home_confirm_settings))
            .setNegativeButton(tr(R.string.cancel),null).setPositiveButton(tr(R.string.open)) { _,_->
                if(CaregiverLock.configured(this)) CaregiverLock.request(this) { settings() } else settings()
            })
    }
    private fun contacts() {
        surface(tr(R.string.family),"contacts")
        for(i in 1..3) {
            val name=prefs.getString("name$i",if(i==1) tr(R.string.son) else tr(R.string.family_number,i))!!
            val photo=prefs.getString("photo$i","")!!
            if(photo.isNotBlank()) root.addView(ImageView(this).apply {
                contentDescription=name;scaleType=ImageView.ScaleType.CENTER_CROP
                layoutParams=LinearLayout.LayoutParams(-1,dp(150));setImageDrawable(MaaIcon(this@MainActivity,"person",green))
                setOnClickListener { dial(i) };PhotoThumbnails.load(this,Uri.parse(photo),dp(150))
            })
            button(tr(R.string.contact_button,name)) { dial(i) }
        }
        button(tr(R.string.home_button)) { home() }
    }
    private fun dial(i: Int) {
        val person=FamilyDirectory.read(this).firstOrNull { it.slot==i }
        if(person==null || !person.actionable || person.canonicalNumber==null) { speak(tr(R.string.missing_number)); return }
        speak(tr(R.string.assistant_calling_person, person.name))
        CommunicationIntents.callDirect(this@MainActivity, person.canonicalNumber)
    }
    private fun openWhatsApp() {
        val intent=packageManager.getLaunchIntentForPackage("com.whatsapp")
        if(intent==null) speak(tr(R.string.whatsapp_missing)) else { speak(tr(R.string.whatsapp_opening)); launch(intent) }
    }
    private fun launch(intent: Intent) { cancelPendingVoice(); try { startActivity(intent) } catch(_: Exception) { speak(tr(R.string.app_unavailable)) } }
    private fun help() {
        surface(tr(R.string.help_title),"help")
        button(tr(R.string.call_family)) { contacts() }
        button(tr(R.string.home_help)) { launch(Intent(this,SosActivity::class.java)) }
        button(tr(R.string.share_help)) { share(tr(R.string.help_message)) }
        button(tr(R.string.home_button)) { home() }
        speak(tr(R.string.help_prompt))
    }
    private fun share(text: String, photo: Boolean=false) {
        val intent=Intent(Intent.ACTION_SEND).apply {
            type=if(photo) "image/jpeg" else "text/plain"; putExtra(Intent.EXTRA_TEXT,text)
            if(photo) { val uri=Uri.parse("content://in.family.maa.photos/shop.jpg"); putExtra(Intent.EXTRA_STREAM,uri); clipData=ClipData.newRawUri(tr(R.string.qr_title),uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        launch(Intent.createChooser(intent,tr(R.string.share_title)))
        speak(tr(R.string.share_prompt))
    }
    private fun capture() { cancelPendingVoice(); try {
        File(cacheDir,"capture.jpg").delete()
        val uri=Uri.parse("content://in.family.maa.photos/capture.jpg")
        startActivityForResult(Intent("android.media.action.IMAGE_CAPTURE").apply {
            putExtra(android.provider.MediaStore.EXTRA_OUTPUT,uri)
            clipData=ClipData.newRawUri(tr(R.string.qr_title),uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        },40)
    } catch(_:Exception) { speak(tr(R.string.camera_missing)) } }
    @Deprecated("Activity result compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode in 51..53) awaitingPhoto=false
        if(resultCode!=RESULT_OK) return
        if(requestCode in 51..53) {
            awaitingPhoto=false
            val uri=data?.data ?: return
            try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
            val draft=Bundle(pendingSettingsDraft ?: settingsDraft()).apply { putString("photo${requestCode-50}",uri.toString()) }
            if(!CaregiverLock.unlocked(this)) { reauthorizeSettings(draft);return }
            settings(draft);speak(tr(R.string.home_photo_pending));return
        }
        if(requestCode==40) {
            val captured=File(cacheDir,"capture.jpg")
            if(!captured.exists() || captured.length()==0L) { speak(tr(R.string.photo_missing)); return }
            try { captured.copyTo(File(cacheDir,"shop.jpg"),overwrite=true); captured.delete() }
            catch(_:Exception) { speak(tr(R.string.home_photo_error));return }
            showQr()
        }
    }
    private fun showQr(savedAmount: String?=null) {
            surface(tr(R.string.qr_title),"qr")
            if(!File(cacheDir,"shop.jpg").exists()) {
                speak(tr(R.string.photo_missing))
                button(tr(R.string.retake)) { capture() }; button(tr(R.string.home_button)) { home() }; return
            }
            val photoPath=File(cacheDir,"shop.jpg").path
            val options=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(photoPath,options)
            options.inSampleSize=1
            while(options.outWidth/options.inSampleSize>1600 || options.outHeight/options.inSampleSize>1600) options.inSampleSize*=2
            options.inJustDecodeBounds=false
            val bitmap=android.graphics.BitmapFactory.decodeFile(photoPath,options)
            if(bitmap==null) { speak(tr(R.string.photo_unreadable)); button(tr(R.string.retake)) { capture() }; button(tr(R.string.home_button)) { home() }; return }
            root.addView(ImageView(this).apply { setImageBitmap(bitmap); contentDescription=tr(R.string.qr_photo); adjustViewBounds=true; layoutParams=LinearLayout.LayoutParams(-1,dp(230)) })
            qrPayee=""; var decodedAmount=""
            try {
                val pixels=IntArray(bitmap.width*bitmap.height); bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
                val source=com.google.zxing.RGBLuminanceSource(bitmap.width,bitmap.height,pixels)
                val decoded=com.google.zxing.MultiFormatReader().decode(com.google.zxing.BinaryBitmap(com.google.zxing.common.HybridBinarizer(source))).text
                val uri=Uri.parse(decoded)
                if(uri.scheme=="upi" && uri.host=="pay") { qrPayee=(uri.getQueryParameter("pn") ?: "") .take(100); decodedAmount=uri.getQueryParameter("am") ?: ""; status.text=tr(R.string.qr_read) }
                else status.text=tr(R.string.qr_not_upi)
            } catch(_:Exception) { status.text=tr(R.string.qr_unreadable) }
            qrAmount=EditText(this).apply { hint=tr(R.string.amount_hint); textSize=30f; setText(savedAmount ?: decodedAmount); inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }; root.addView(qrAmount)
            button(tr(R.string.speak_amount)) { listen() }
            button(tr(R.string.request_payment)) {
                cancelPendingVoice()
                val value=MoneyAmount.parse(qrAmount?.text.toString())?.setScale(2)
                if(value==null) speak(tr(R.string.invalid_amount))
                else {
                    speak(tr(R.string.request_prompt,value))
                    showDialog(AlertDialog.Builder(this).setTitle(tr(R.string.request_title,value)).setMessage(tr(R.string.request_notice,qrPayee.ifBlank { tr(R.string.merchant_check) })).setPositiveButton(tr(R.string.confirm_share)) { _,_-> share(tr(R.string.request_message,value,qrPayee.ifBlank { tr(R.string.merchant_unknown) }),true) }.setNegativeButton(tr(R.string.cancel),null))
                }
            }
            button(tr(R.string.retake)) { capture() }; button(tr(R.string.home_button)) { home() }
            if(savedAmount==null) speak(tr(R.string.amount_prompt))
    }
    private fun onboarding(draft:Bundle?=null) {
        surface(tr(R.string.home_welcome),"onboarding")
        onboardingLanguage=draft?.getString("setup_language") ?: AppLanguage.code(this)
        root.addView(TextView(this).apply { text=tr(R.string.home_language);textSize=22f;setTextColor(MaaDesign.ink) })
        val languages=RadioGroup(this).apply { orientation=RadioGroup.VERTICAL }
        listOf("en" to "English","hi" to "हिन्दी").forEach { (code,label) ->
            languages.addView(RadioButton(this).apply {
                id=if(code=="en") R.id.setup_english else R.id.setup_hindi
                text=label;textSize=24f;minimumHeight=dp(64);isChecked=onboardingLanguage==code
                setOnCheckedChangeListener { _,checked -> if(checked) onboardingLanguage=code }
            })
        };root.addView(languages)
        root.addView(TextView(this).apply { text=tr(R.string.home_assistant_name);textSize=22f;setTextColor(MaaDesign.ink);setPadding(0,dp(16),0,0) })
        val name=EditText(this).apply {
            id=R.id.assistant_name;setText(draft?.getString(AssistantIdentity.KEY_NAME) ?: AssistantIdentity.displayName(this@MainActivity))
            textSize=24f;setSingleLine(true);filters=arrayOf(android.text.InputFilter.LengthFilter(AssistantIdentity.MAX_CODE_POINTS))
        };settingsFields[AssistantIdentity.KEY_NAME]=name;root.addView(name)
        root.addView(TextView(this).apply { text=tr(R.string.home_settings_gesture);textSize=18f;setTextColor(MaaDesign.muted);setPadding(0,dp(16),0,dp(16)) })
        root.addView(MaaDesign.action(this,tr(R.string.home_start),true) {
            prefs.edit().putString(AppLanguage.KEY,onboardingLanguage)
                .putString(AssistantIdentity.KEY_NAME,AssistantIdentity.normalizeName(name.text.toString()))
                .putBoolean(ONBOARDING_COMPLETE,true).apply()
            languageContext=AppLanguage.localizedContext(this);configureVoice();home()
        }.apply { id=R.id.setup_start })
    }
    private fun settingsDraft() = Bundle().apply {
        settingsFields.forEach { (key,input) -> putString(key,input.text.toString()) }
        putString("setup_language",onboardingLanguage)
        photoDraft.forEach { (key,value) -> putString(key,value) }
        putBoolean("quiet",quietSwitch?.isChecked ?: prefs.getBoolean("quiet",false))
        putBoolean("guard",guardSwitch?.isChecked ?: prefs.getBoolean("guard",false))
    }
    private fun settings(draft: Bundle?=null) {
        if(!CaregiverLock.unlocked(this)) { home();return }
        surface(tr(R.string.settings_title),"settings")
        for(i in 1..3) photoDraft["photo$i"]=draft?.getString("photo$i") ?: prefs.getString("photo$i","").orEmpty()
        homeRoleButton=MaaDesign.action(this,tr(if(HomeRoleController.isDefaultHome(this)) R.string.home_default_ready else R.string.home_make_default)) { HomeRoleController.request(this) }.apply {
            minHeight=dp(72);layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
        };root.addView(homeRoleButton)
        button(tr(R.string.home_tools)) { tools() }
        button(tr(R.string.home_device_details)) { launch(Intent(this,StatusDetailsActivity::class.java)) }
        button(if (AppLanguage.isHindi(this)) "💊 दवाइयों का समय (Medicine Schedule)" else "💊 Medicine Schedule") {
            MedicineManager.showSetupDialog(this) { updateMedicineCard(); updateHomeVoiceAction() }
        }
        button(if (AppLanguage.isHindi(this)) "🛡️ अनुमतियाँ सेट करें (Permissions Setup)" else "🛡️ Permissions Setup") {
            PermissionsHelper.showPermissionSetupDialog(this) { updatePermissionBanner(); updateHomeVoiceAction(); updateMedicineCard() }
        }
        button(tr(R.string.home_set_pin)) { CaregiverLock.configure(this) { settings(settingsDraft()) } }
        if(CaregiverLock.configured(this)) button(tr(R.string.home_pin_remove)) { CaregiverLock.remove(this) { settings(settingsDraft()) } }
        button(tr(R.string.home_look_feel)) {
            val options=arrayOf(tr(R.string.home_layout_grid),tr(R.string.home_layout_list))
            showDialog(AlertDialog.Builder(this).setTitle(tr(R.string.home_layout)).setSingleChoiceItems(options,if(prefs.getBoolean("one_column",false)) 1 else 0) { dialog,which ->
                if(CaregiverLock.unlocked(this)) prefs.edit().putBoolean("one_column",which==1).apply()
                dialog.dismiss()
            }.setNegativeButton(tr(R.string.cancel),null))
        }
        val fields=settingsFields
        fun field(key: String,label: String,number: Boolean=false) {
            root.addView(TextView(this).apply { text=label; textSize=18f })
            val fallback = when(key) {
                "backend" -> prefs.getString("backend", AssistantActivity.DEFAULT_BACKEND) ?: AssistantActivity.DEFAULT_BACKEND
                "token" -> prefs.getString("token", AssistantActivity.DEFAULT_TOKEN) ?: AssistantActivity.DEFAULT_TOKEN
                else -> prefs.getString(key, "")
            }
            val input=EditText(this).apply { setText(draft?.getString(key) ?: fallback); textSize=20f; if(number) inputType=android.text.InputType.TYPE_CLASS_PHONE; if(key=="token") inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
            when(key) { "name1" -> input.id=R.id.family_name_1; "phone1" -> input.id=R.id.family_phone_1; "backend" -> input.id=R.id.backend_url
                AssistantIdentity.KEY_NAME -> { input.id=R.id.assistant_name;input.setSingleLine(true);input.filters=arrayOf(android.text.InputFilter.LengthFilter(AssistantIdentity.MAX_CODE_POINTS));input.setText(draft?.getString(key) ?: AssistantIdentity.displayName(this)) }
            }
            fields[key]=input; root.addView(input)
        }
        field(AssistantIdentity.KEY_NAME,tr(R.string.home_assistant_name))
        for(i in 1..3) {
            field("name$i",tr(R.string.person_name,i)); field("phone$i",tr(R.string.phone_label),true)
            button(tr(R.string.person_photo,i)) {
                try { awaitingPhoto=true;startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) },50+i) }
                catch(_:Exception) { speak(tr(R.string.picker_missing)) }
            }
        }
        field("backend",tr(R.string.backend_label))
        field("token",tr(R.string.token_label))
        field("trusted",tr(R.string.trusted_label))
        val quiet=Switch(this).apply { text=tr(R.string.quiet_label); textSize=18f; isChecked=draft?.getBoolean("quiet") ?: prefs.getBoolean("quiet",false) }; quietSwitch=quiet; root.addView(quiet)
        val guard=Switch(this).apply { text=tr(R.string.guard_label); textSize=18f; isChecked=draft?.getBoolean("guard") ?: prefs.getBoolean("guard",false) }; guardSwitch=guard; root.addView(guard)
        quiet.id=R.id.quiet_enabled; guard.id=R.id.guard_enabled
        root.addView(TextView(this).apply { text=tr(R.string.guard_notice); textSize=16f })
        button(tr(R.string.guard_permission)) { launch(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        root.addView(TextView(this).apply { text=tr(R.string.notification_notice,prefs.getInt("quietCount",0)); textSize=16f })
        button(tr(R.string.notification_permission)) { launch(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
        button(tr(R.string.overlay_enable)) {
            if(!android.provider.Settings.canDrawOverlays(this)) launch(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            else {
                if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),11)
                try {
                    HelpOverlayService.start(this);val request=generation
                    root.postDelayed({ if(!isDestroyed && generation==request) speak(tr(if(HelpOverlayService.isActive(this)) R.string.overlay_enabled else R.string.assistant_overlay_failed)) },800)
                } catch(_:RuntimeException) { speak(tr(R.string.assistant_overlay_failed)) }
            }
        }
        button(tr(R.string.overlay_disable)) { HelpOverlayService.stop(this); speak(tr(R.string.overlay_disabled)) }
        button(tr(R.string.save)) {
            if(!CaregiverLock.unlocked(this)) { reauthorizeSettings(settingsDraft());return@button }
            val url=fields["backend"]!!.text.toString().trim()
            val backendUri=Uri.parse(url)
            if(url.isNotEmpty() && (backendUri.scheme!="https" || backendUri.host.isNullOrBlank() || backendUri.userInfo!=null || backendUri.query!=null || backendUri.fragment!=null)) { speak(tr(R.string.invalid_backend)); return@button }
            prefs.edit().apply { fields.forEach { (key,input)->putString(key,if(key==AssistantIdentity.KEY_NAME) AssistantIdentity.normalizeName(input.text.toString()) else input.text.toString().trim()) };photoDraft.forEach { (key,value)->putString(key,value) }; putBoolean("quiet",quiet.isChecked); putBoolean("guard",guard.isChecked) }.apply(); home(); speak(tr(R.string.settings_saved))
        }
        button(tr(R.string.home_button)) { home() }
    }
    private fun listen() {
        if(screen!="qr") { launch(Intent(this,AssistantActivity::class.java));return }
        if(busy) return
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { microphoneRequestGeneration=generation; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10); return }
        if(!SpeechRecognizer.isRecognitionAvailable(this)) { speak(tr(R.string.recognition_missing)); return }
        cancelPendingVoice()
        val speechGeneration=generation
        fun current() = !isDestroyed && !isFinishing && generation==speechGeneration
        try {
        recognizer=SpeechRecognizer.createSpeechRecognizer(this); busy=true
        recognizer?.setRecognitionListener(object: RecognitionListener {
            override fun onReadyForSpeech(params:Bundle?) { if(current()) status.text=tr(R.string.listening) }
            override fun onResults(results:Bundle?) { if(!current()) return; cancelPendingVoice(); val text=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if(text.isNullOrBlank()) speak(tr(R.string.repeat)) else interpret(text) }
            override fun onError(error:Int) { if(!current()) return; cancelPendingVoice(); speak(tr(R.string.recognition_error)) }
            override fun onBeginningOfSpeech() {}; override fun onRmsChanged(rmsdB:Float) {}; override fun onBufferReceived(buffer:ByteArray?) {}; override fun onEndOfSpeech() { if(current()) status.text=tr(R.string.understanding) }; override fun onPartialResults(results:Bundle?) {}; override fun onEvent(eventType:Int,params:Bundle?) {}
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE,AppLanguage.locale(this@MainActivity).toLanguageTag()); putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM) })
        } catch(_:RuntimeException) { cancelPendingVoice(); speak(tr(R.string.recognition_start_error)) }
    }
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) {
        super.onRequestPermissionsResult(code,permissions,results)
        if(code==10 && microphoneRequestGeneration==generation) {
            microphoneRequestGeneration=-1
            if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) {
                if (screen == "home") startHomeSpeechListening() else listen()
            } else {
                speak(tr(R.string.microphone_permission))
            }
        } else if (code == PermissionsHelper.REQUEST_CODE_RUNTIME) {
            updatePermissionBanner()
            updateMedicineCard()
            updateHomeVoiceAction()
        }
    }
    private fun interpret(text:String) {
        if(qrAmount!=null) {
            val number=MoneyAmount.parse(text)?.toPlainString()
            if(number==null) speak(tr(R.string.amount_unrecognized))
            else { qrAmount?.setText(number); speak(tr(R.string.amount_recognized,number)) }
            return
        }
        speak(tr(R.string.unsupported))
    }
    override fun onStop() {
        ProactiveAsha.removeListener(proactiveUpdateListener)
        cancelPendingVoice();dismissDialogs();unlockPromptOpen=false; if(screen=="settings" && !isChangingConfigurations && !awaitingPhoto) CaregiverLock.relock();super.onStop()
    }
    override fun onDestroy() {
        stopHomeVoice()
        voiceWorker.shutdownNow()
        cancelPendingVoice(); tts?.shutdown(); super.onDestroy()
    }
    @Deprecated("Legacy back compatibility") override fun onBackPressed() { home() }
}
