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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.File

class MainActivity : Activity(), TextToSpeech.OnInitListener {
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
    private val green = Color.rgb(23,107,87)
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        when(state?.getString("screen")) {
            "settings" -> settings(state.getBundle("settingsDraft"))
            "contacts" -> contacts()
            "help" -> help()
            "qr" -> showQr(state.getString("qrAmount"))
            else -> home()
        }
        tts = TextToSpeech(this,this)
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("screen",screen)
        if(screen=="settings") state.putBundle("settingsDraft",settingsDraft())
        if(screen=="qr") state.putString("qrAmount",qrAmount?.text?.toString())
        super.onSaveInstanceState(state)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); home() }
    override fun onInit(code: Int) {
        if(code == TextToSpeech.SUCCESS) {
            ttsInitialized = true
            configureVoice()
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
        cancelPendingVoice()
        prefs.edit().putString(AppLanguage.KEY,if(AppLanguage.code(this)=="hi") "en" else "hi").apply()
        languageContext=AppLanguage.localizedContext(this)
        configureVoice()
        when(destination) {
            "settings" -> settings(draft)
            "contacts" -> contacts()
            "help" -> help()
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
        cancelPendingVoice(); screen=destination; qrAmount=null
        settingsFields.clear(); quietSwitch=null; guardSwitch=null
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(24),dp(20),dp(20)); setBackgroundColor(Color.rgb(250,247,239)) }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(root) }
        val frame=FrameLayout(this).apply {
            addView(scroll,FrameLayout.LayoutParams(-1,-1))
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
        root.addView(LinearLayout(this).apply {
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(12))
            addView(TextView(this@MainActivity).apply { text=title; textSize=32f; setTextColor(green); gravity=Gravity.CENTER; layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
            addView(Button(this@MainActivity).apply {
                id=R.id.language_switch; text=tr(R.string.switch_language); contentDescription=tr(R.string.switch_language_description)
                isAllCaps=false; textSize=18f; minHeight=dp(48); setTextColor(green)
                setOnClickListener { switchLanguage() }
            })
        })
        status=TextView(this).apply { text=tr(R.string.default_status); textSize=20f; gravity=Gravity.CENTER; setPadding(0,0,0,dp(12)) }; root.addView(status)
    }
    private fun button(text: String, action: () -> Unit) {
        root.addView(Button(this).apply {
            this.text=text; textSize=24f; isAllCaps=false; setTextColor(Color.WHITE)
            background=GradientDrawable().apply { setColor(green); cornerRadius=dp(22).toFloat() }
            minHeight=dp(88); setPadding(dp(16),dp(12),dp(16),dp(12))
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) }
            setOnClickListener { action() }
        })
    }
    private fun home() {
        surface(tr(R.string.home_title),"home")
        val faces=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        for(i in 1..3) {
            val number=prefs.getString("phone$i","")!!
            if(number.isBlank()) continue
            val name=prefs.getString("name$i", tr(R.string.family))!!.ifBlank { tr(R.string.family) }
            val card=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),dp(12)) }; contentDescription=tr(R.string.call_person,name) }
            val image=ImageView(this).apply { layoutParams=LinearLayout.LayoutParams(dp(86),dp(86)); scaleType=ImageView.ScaleType.CENTER_CROP; contentDescription=name }
            try { val photo=prefs.getString("photo$i","")!!; if(photo.isNotBlank()) image.setImageURI(Uri.parse(photo)) else image.setImageResource(android.R.drawable.sym_call_incoming) } catch(_:Exception) { image.setImageResource(android.R.drawable.sym_call_incoming) }
            image.setOnClickListener { dial(i) }; card.addView(image)
            card.addView(TextView(this).apply { text=name; textSize=20f; gravity=Gravity.CENTER; setTextColor(green); setOnClickListener { dial(i) } }); faces.addView(card)
        }
        if(faces.childCount>0) root.addView(faces)
        button(tr(R.string.voice_help)) { listen() }
        fun tiles(left:String,leftAction:()->Unit,right:String,rightAction:()->Unit) {
            val row=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
            listOf(left to leftAction,right to rightAction).forEach { (label,action) ->
                row.addView(Button(this).apply {
                    text=label; textSize=21f; isAllCaps=false; setTextColor(Color.WHITE)
                    background=GradientDrawable().apply { setColor(green); cornerRadius=dp(22).toFloat() }
                    minHeight=dp(114); setPadding(dp(8),dp(12),dp(8),dp(12))
                    layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),dp(12)) }
                    setOnClickListener { action() }
                })
            }; root.addView(row)
        }
        tiles(tr(R.string.call_tile),{ contacts() },tr(R.string.media_tile),{ openWhatsApp() })
        tiles(tr(R.string.qr_tile),{ capture() },tr(R.string.help_tile),{ help() })
        root.addView(Button(this).apply {
            text=tr(R.string.settings_button); textSize=18f; isAllCaps=false; layoutParams=LinearLayout.LayoutParams(-1,dp(56))
            setOnClickListener { cancelPendingVoice(); AlertDialog.Builder(this@MainActivity).setTitle(tr(R.string.settings_title)).setMessage(tr(R.string.settings_notice)).setPositiveButton(tr(R.string.open)) { _,_-> settings() }.setNegativeButton(tr(R.string.back),null).show() }
        })
    }
    private fun contacts() {
        surface(tr(R.string.family),"contacts")
        for(i in 1..3) {
            val name=prefs.getString("name$i",if(i==1) tr(R.string.son) else tr(R.string.family_number,i))!!
            val photo=prefs.getString("photo$i","")!!
            if(photo.isNotBlank()) try { root.addView(ImageView(this).apply { setImageURI(Uri.parse(photo)); contentDescription=name; scaleType=ImageView.ScaleType.CENTER_CROP; layoutParams=LinearLayout.LayoutParams(-1,dp(150)); setOnClickListener { dial(i) } }) } catch(_: Exception) {}
            button(tr(R.string.contact_button,name)) { dial(i) }
        }
        button(tr(R.string.home_button)) { home() }
    }
    private fun dial(i: Int) {
        val number=prefs.getString("phone$i","")!!
        if(number.isBlank()) { speak(tr(R.string.missing_number)); return }
        launch(Intent(Intent.ACTION_DIAL,Uri.fromParts("tel",number,null)))
    }
    private fun openWhatsApp() {
        val intent=packageManager.getLaunchIntentForPackage("com.whatsapp")
        if(intent==null) speak(tr(R.string.whatsapp_missing)) else { speak(tr(R.string.whatsapp_opening)); launch(intent) }
    }
    private fun launch(intent: Intent) { cancelPendingVoice(); try { startActivity(intent) } catch(_: Exception) { speak(tr(R.string.app_unavailable)) } }
    private fun help() {
        surface(tr(R.string.help_title),"help")
        button(tr(R.string.call_family)) { contacts() }
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
        if(resultCode!=RESULT_OK) return
        if(requestCode in 51..53) { val uri=data?.data ?: return; try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}; prefs.edit().putString("photo${requestCode-50}",uri.toString()).apply(); settings(settingsDraft()); return }
        if(requestCode==40) {
            val captured=File(cacheDir,"capture.jpg")
            if(!captured.exists() || captured.length()==0L) { speak(tr(R.string.photo_missing)); return }
            captured.copyTo(File(cacheDir,"shop.jpg"),overwrite=true); captured.delete()
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
                val value=qrAmount?.text.toString().toBigDecimalOrNull()
                if(value==null || value.signum()<=0 || value>java.math.BigDecimal("100000")) speak(tr(R.string.invalid_amount))
                else {
                    speak(tr(R.string.request_prompt,value))
                    AlertDialog.Builder(this).setTitle(tr(R.string.request_title,value)).setMessage(tr(R.string.request_notice,qrPayee.ifBlank { tr(R.string.merchant_check) })).setPositiveButton(tr(R.string.confirm_share)) { _,_-> share(tr(R.string.request_message,value,qrPayee.ifBlank { tr(R.string.merchant_unknown) }),true) }.setNegativeButton(tr(R.string.cancel),null).show()
                }
            }
            button(tr(R.string.retake)) { capture() }; button(tr(R.string.home_button)) { home() }
            if(savedAmount==null) speak(tr(R.string.amount_prompt))
    }
    private fun settingsDraft() = Bundle().apply {
        settingsFields.forEach { (key,input) -> putString(key,input.text.toString()) }
        putBoolean("quiet",quietSwitch?.isChecked ?: prefs.getBoolean("quiet",false))
        putBoolean("guard",guardSwitch?.isChecked ?: prefs.getBoolean("guard",false))
    }
    private fun settings(draft: Bundle?=null) {
        surface(tr(R.string.settings_title),"settings")
        val fields=settingsFields
        fun field(key: String,label: String,number: Boolean=false) {
            root.addView(TextView(this).apply { text=label; textSize=18f })
            val input=EditText(this).apply { setText(draft?.getString(key) ?: prefs.getString(key,"")); textSize=20f; if(number) inputType=android.text.InputType.TYPE_CLASS_PHONE; if(key=="token") inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
            when(key) { "name1" -> input.id=R.id.family_name_1; "phone1" -> input.id=R.id.family_phone_1; "backend" -> input.id=R.id.backend_url }
            fields[key]=input; root.addView(input)
        }
        for(i in 1..3) {
            field("name$i",tr(R.string.person_name,i)); field("phone$i",tr(R.string.phone_label),true)
            button(tr(R.string.person_photo,i)) {
                try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { type="image/*"; addCategory(Intent.CATEGORY_OPENABLE); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) },50+i) }
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
            else { if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),11); startForegroundService(Intent(this,HelpOverlayService::class.java)); speak(tr(R.string.overlay_enabled)) }
        }
        button(tr(R.string.overlay_disable)) { stopService(Intent(this,HelpOverlayService::class.java)); speak(tr(R.string.overlay_disabled)) }
        button(tr(R.string.save)) {
            val url=fields["backend"]!!.text.toString().trim()
            val backendUri=Uri.parse(url)
            if(url.isNotEmpty() && (backendUri.scheme!="https" || backendUri.host.isNullOrBlank() || backendUri.userInfo!=null || backendUri.query!=null || backendUri.fragment!=null)) { speak(tr(R.string.invalid_backend)); return@button }
            prefs.edit().apply { fields.forEach { (key,input)->putString(key,input.text.toString().trim()) }; putBoolean("quiet",quiet.isChecked); putBoolean("guard",guard.isChecked) }.apply(); home(); speak(tr(R.string.settings_saved))
        }
        button(tr(R.string.home_button)) { home() }
    }
    private fun listen() {
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
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) { super.onRequestPermissionsResult(code,permissions,results); if(code==10 && microphoneRequestGeneration==generation) { microphoneRequestGeneration=-1; if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) listen() else speak(tr(R.string.microphone_permission)) } }
    private fun interpret(text:String) {
        if(qrAmount!=null) {
            val normalized=text.map { if(it in '०'..'९') '0'+(it-'०') else it }.joinToString("")
            val number=Regex("[0-9]+(?:\\.[0-9]{1,2})?").find(normalized)?.value
            if(number==null) speak(tr(R.string.amount_unrecognized))
            else { qrAmount?.setText(number); speak(tr(R.string.amount_recognized,number)) }
            return
        }
        val backend=prefs.getString("backend","")!!
        if(backend.isBlank()) {
            status.text=tr(R.string.local_command,text)
            val action=when { listOf("फोन","कॉल","call","phone").any { text.contains(it,true) } -> "call"; listOf("फोटो","वीडियो","व्हाट्स","whatsapp","photo","video").any { text.contains(it,true) } -> "media"; listOf("पैसे","भुगतान","qr","दुकान","pay","shop").any { text.contains(it,true) } -> "qr"; listOf("घर","home").any { text.contains(it,true) } -> "home"; else -> "help" }
            act(action,text); return
        }
        busy=true; status.text=tr(R.string.asking_ai); val requestGeneration=generation
        Thread {
            var connection: HttpURLConnection?=null
            try {
                connection=URL(backend.trimEnd('/')+"/intent").openConnection() as HttpURLConnection
                connection.requestMethod="POST"; connection.connectTimeout=15000; connection.readTimeout=20000; connection.doOutput=true
                connection.setRequestProperty("Content-Type","application/json"); connection.setRequestProperty("Authorization","Bearer "+prefs.getString("token",""))
                connection.outputStream.use { it.write(JSONObject().put("text",text).toString().toByteArray(Charsets.UTF_8)) }
                if(connection.responseCode!=200) error("Backend unavailable")
                val result=JSONObject(connection.inputStream.bufferedReader().use { it.readText() }); val action=result.getString("action")
                runOnUiThread { if(!isDestroyed && generation==requestGeneration) { busy=false; act(action,text) } }
            } catch(_:Exception) { runOnUiThread { if(!isDestroyed && generation==requestGeneration) { busy=false; speak(tr(R.string.backend_error)) } } } finally { connection?.disconnect() }
        }.start()
    }
    private fun act(action:String,text:String) { when(action) {
        "home" -> home(); "media" -> openWhatsApp(); "qr" -> capture(); "help" -> help()
        "call" -> { val i=(1..3).firstOrNull { val name=prefs.getString("name$it",if(it==1) tr(R.string.son) else "")!!; name.isNotBlank() && text.contains(name,true) }; if(i==null) contacts() else AlertDialog.Builder(this).setTitle(tr(R.string.confirm_call)).setMessage(prefs.getString("name$i",tr(R.string.family))).setPositiveButton(tr(R.string.yes)) { _,_->dial(i) }.setNegativeButton(tr(R.string.cancel),null).show() }
        else -> speak(tr(R.string.unsupported))
    } }
    override fun onStop() { cancelPendingVoice(); super.onStop() }
    override fun onDestroy() { cancelPendingVoice(); tts?.shutdown(); super.onDestroy() }
    @Deprecated("Legacy back compatibility") override fun onBackPressed() { home() }
}
