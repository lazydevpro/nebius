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
import android.view.View
import android.graphics.Typeface
import android.widget.*
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
    private val green = MaaDesign.green
    private var homeRoleButton: Button? = null
    private var deviceStatus: DeviceStatusView? = null
    private var awaitingPhoto = false
    private val photoDraft = mutableMapOf<String,String>()
    private var pendingSettingsDraft:Bundle?=null
    private var unlockPromptOpen=false
    private val activeDialogs=mutableListOf<AlertDialog>()
    private fun showDialog(builder:AlertDialog.Builder) { builder.show().also { dialog -> activeDialogs.add(dialog);dialog.setOnDismissListener { activeDialogs.remove(dialog) } } }
    private fun dismissDialogs() { activeDialogs.toList().forEach { it.dismiss() };activeDialogs.clear();CaregiverLock.dismiss(this) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=MaaDesign.canvas;window.navigationBarColor=MaaDesign.canvas
        pendingSettingsDraft=state?.getBundle("lockedSettingsDraft")
        when(state?.getString("screen")) {
            "settings" -> if(CaregiverLock.unlocked(this)) settings(state.getBundle("settingsDraft")) else { pendingSettingsDraft=state.getBundle("settingsDraft");home(false) }
            "contacts" -> contacts()
            "help" -> help()
            "tools" -> tools()
            "qr" -> showQr(state.getString("qrAmount"))
            else -> home(pendingSettingsDraft==null)
        }
        tts = TextToSpeech(this,this)
    }
    override fun onSaveInstanceState(state: Bundle) {
        state.putString("screen",screen)
        if(screen=="settings") state.putBundle("settingsDraft",settingsDraft())
        pendingSettingsDraft?.let { state.putBundle("lockedSettingsDraft",it) }
        if(screen=="qr") state.putString("qrAmount",qrAmount?.text?.toString())
        super.onSaveInstanceState(state)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); home() }
    override fun onResume() {
        super.onResume()
        if(screen=="settings" && !CaregiverLock.unlocked(this) && !awaitingPhoto) {
            pendingSettingsDraft=settingsDraft();home(false)
        }
        if(pendingSettingsDraft!=null && !unlockPromptOpen) {
            unlockPromptOpen=true
            CaregiverLock.request(this,onCancelled={pendingSettingsDraft=null;unlockPromptOpen=false}) {
                val preserved=pendingSettingsDraft;pendingSettingsDraft=null;unlockPromptOpen=false;settings(preserved)
            }
        }
        deviceStatus?.refresh()
        homeRoleButton?.apply { text=tr(if(HomeRoleController.isDefaultHome(this@MainActivity)) R.string.home_default_ready else R.string.home_make_default) }
    }
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
        cancelPendingVoice(); screen=destination; qrAmount=null; homeRoleButton=null; deviceStatus=null
        settingsFields.clear(); quietSwitch=null; guardSwitch=null
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(12),dp(16),dp(24)); setBackgroundColor(MaaDesign.canvas) }
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
            orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL; setPadding(0,0,0,dp(8))
            addView(TextView(this@MainActivity).apply { text=title; textSize=29f; setTypeface(typeface,Typeface.BOLD); setTextColor(MaaDesign.ink); gravity=Gravity.START; layoutParams=LinearLayout.LayoutParams(0,-2,1f) })
            addView(Button(this@MainActivity).apply {
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
        if(clearPendingDraft) pendingSettingsDraft=null
        unlockPromptOpen=false
        surface(tr(R.string.home_title),"home")
        photoDraft.clear()
        deviceStatus=DeviceStatusView(this).apply {
            onOpenDetails={ launch(Intent(this@MainActivity,StatusDetailsActivity::class.java)) }
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) }
        }; root.addView(deviceStatus)
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
                contentDescription=if(number.isBlank()) tr(R.string.home_add_family) else name
                setOnClickListener { if(number.isBlank()) openSettings() else launch(CommunicationActivity.intent(this@MainActivity,i)) }
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
        root.addView(MaaDesign.action(this,tr(R.string.home_voice),true) { launch(Intent(this,AssistantActivity::class.java)) }.apply {
            id=R.id.home_voice_action;textSize=24f;minHeight=dp(68);setCompoundDrawablesWithIntrinsicBounds(MaaIcon(this@MainActivity,"mic",Color.WHITE),null,null,null)
            background=MaaDesign.shape(this@MainActivity,Color.rgb(15,119,113),24)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) }
        })
        data class Tile(val label:Int,val symbol:String,val color:Int,val id:Int,val action:()->Unit)
        val entries=listOf(
            Tile(R.string.home_phone,"phone",0xffe5f3e9.toInt(),R.id.home_phone_action) { launch(CommunicationActivity.intent(this)) },
            Tile(R.string.home_messages,"message",0xffe3f1fc.toInt(),R.id.home_messages_action) { launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL,"sms")) },
            Tile(R.string.home_photos,"photo",0xffede8f7.toInt(),R.id.home_chat_action) { launch(CommunicationActivity.intent(this).putExtra(CommunicationActivity.EXTRA_CHANNEL,"whatsapp")) },
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
        root.addView(MaaDesign.action(this,tr(R.string.home_help)) { help() }.apply {
            id=R.id.home_help_action;background=MaaDesign.shape(this@MainActivity,0xffb94c36.toInt(),22);setTextColor(Color.WHITE);textSize=24f
            setCompoundDrawablesWithIntrinsicBounds(MaaIcon(this@MainActivity,"help",Color.WHITE),null,null,null)
            layoutParams=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(4);bottomMargin=dp(8) }
        })
        val footer=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL }
        listOf(R.string.home_settings to { openSettings() },R.string.home_more to { tools() }).forEach { (label,action) ->
            footer.addView(MaaDesign.action(this,tr(label),false,action).apply {
                minHeight=dp(48);minimumHeight=dp(48);textSize=18f
                id=if(label==R.string.home_settings) R.id.home_settings_action else R.id.home_more_action
                layoutParams=LinearLayout.LayoutParams(0,-2,1f).apply { setMargins(dp(4),0,dp(4),0) }
            })
        }
        root.addView(footer)
    }
    private fun tools() {
        surface(tr(R.string.home_more),"tools")
        val utilities=listOf(R.string.home_alarm to { launch(Intent(this,AlarmActivity::class.java)) },R.string.home_notifications to { launch(Intent(this,NotificationCenterActivity::class.java)) },R.string.home_qr to { capture() })
        utilities.forEach { (label,action) -> root.addView(MaaDesign.action(this,tr(label),false,action).apply { layoutParams=LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(8) } }) }
        button(tr(R.string.home_button)) { home() }
    }
    private fun openSettings() { cancelPendingVoice(); CaregiverLock.request(this) { settings() } }
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
        if(person==null || !person.actionable) { speak(tr(R.string.missing_number)); return }
        launch(CommunicationActivity.intent(this,i))
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
            if(!CaregiverLock.unlocked(this)) { home();return }
            try { contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch(_:Exception) {}
            val draft=settingsDraft().apply { putString("photo${requestCode-50}",uri.toString()) }
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
    private fun settingsDraft() = Bundle().apply {
        settingsFields.forEach { (key,input) -> putString(key,input.text.toString()) }
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
            val input=EditText(this).apply { setText(draft?.getString(key) ?: prefs.getString(key,"")); textSize=20f; if(number) inputType=android.text.InputType.TYPE_CLASS_PHONE; if(key=="token") inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
            when(key) { "name1" -> input.id=R.id.family_name_1; "phone1" -> input.id=R.id.family_phone_1; "backend" -> input.id=R.id.backend_url }
            fields[key]=input; root.addView(input)
        }
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
            if(!CaregiverLock.unlocked(this)) { home();return@button }
            val url=fields["backend"]!!.text.toString().trim()
            val backendUri=Uri.parse(url)
            if(url.isNotEmpty() && (backendUri.scheme!="https" || backendUri.host.isNullOrBlank() || backendUri.userInfo!=null || backendUri.query!=null || backendUri.fragment!=null)) { speak(tr(R.string.invalid_backend)); return@button }
            prefs.edit().apply { fields.forEach { (key,input)->putString(key,input.text.toString().trim()) };photoDraft.forEach { (key,value)->putString(key,value) }; putBoolean("quiet",quiet.isChecked); putBoolean("guard",guard.isChecked) }.apply(); home(); speak(tr(R.string.settings_saved))
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
    override fun onRequestPermissionsResult(code:Int,permissions:Array<out String>,results:IntArray) { super.onRequestPermissionsResult(code,permissions,results); if(code==10 && microphoneRequestGeneration==generation) { microphoneRequestGeneration=-1; if(results.firstOrNull()==PackageManager.PERMISSION_GRANTED) listen() else speak(tr(R.string.microphone_permission)) } }
    private fun interpret(text:String) {
        if(qrAmount!=null) {
            val number=MoneyAmount.parse(text)?.toPlainString()
            if(number==null) speak(tr(R.string.amount_unrecognized))
            else { qrAmount?.setText(number); speak(tr(R.string.amount_recognized,number)) }
            return
        }
        speak(tr(R.string.unsupported))
    }
    override fun onStop() { cancelPendingVoice();dismissDialogs();unlockPromptOpen=false; if(screen=="settings" && !isChangingConfigurations && !awaitingPhoto) CaregiverLock.relock();super.onStop() }
    override fun onDestroy() { cancelPendingVoice(); tts?.shutdown(); super.onDestroy() }
    @Deprecated("Legacy back compatibility") override fun onBackPressed() { home() }
}
