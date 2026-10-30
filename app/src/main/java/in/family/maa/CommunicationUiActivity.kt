package `in`.family.maa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

/** Small, dependency-free accessible surface shared by the communication screens. */
abstract class CommunicationUiActivity : Activity() {
    protected lateinit var content: LinearLayout
    protected val cream = Color.rgb(246, 244, 236)
    protected val ink = Color.rgb(23, 46, 39)
    protected val green = Color.rgb(18, 92, 72)
    private var language = ""
    private val photoWorker = Executors.newSingleThreadExecutor()
    protected var ashaBottomButton: Button? = null
    private val proactiveListener: () -> Unit = { runOnUiThread { updateAshaBottomButton() } }
    private var voice: TextToSpeech? = null
    private var voiceReady = false

    override fun attachBaseContext(newBase: Context) {
        language = AppLanguage.code(newBase)
        super.attachBaseContext(AppLanguage.localizedContext(newBase))
    }

    override fun onStart() {
        super.onStart()
        ProactiveAsha.addListener(proactiveListener)
    }

    override fun onStop() {
        ProactiveAsha.removeListener(proactiveListener)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (language != AppLanguage.code(this)) recreate()
        updateAshaBottomButton()
    }

    protected fun updateAshaBottomButton() {
        val btn = ashaBottomButton ?: return
        val hasAlert = ProactiveAsha.hasUnreadUpdates(this)
        val hasDueMed = MedicineManager.hasDueMedicine(this)
        val photoAlert = ProactiveAsha.showsPhotoAlert(this)
        btn.setTextColor(if (photoAlert) MaaDesign.ink else Color.WHITE)
        if (hasAlert) {
            btn.text = when {
                hasDueMed -> if (AppLanguage.isHindi(this)) "💊 दवा का समय" else "💊 Medicine Time"
                photoAlert -> if (AppLanguage.isHindi(this)) "📷 नई फ़ोटो" else "📷 New Photo"
                else -> if (AppLanguage.isHindi(this)) "🔔 नया अपडेट" else "🔔 New Alert"
            }
            btn.background = GradientDrawable().apply {
                setColor(if (photoAlert) MaaDesign.photoYellow else Color.rgb(220, 38, 38))
                cornerRadius = dp(22).toFloat()
            }
            btn.setOnClickListener {
                startActivity(AssistantActivity.readUpdateIntent(this))
            }
        } else {
            btn.text = "🎙️ " + AssistantIdentity.displayName(this)
            btn.background = GradientDrawable().apply {
                setColor(Color.rgb(15, 119, 113))
                cornerRadius = dp(22).toFloat()
            }
            btn.setOnClickListener {
                startActivity(AssistantActivity.voiceIntent(this))
            }
        }
    }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun tr(id: Int, vararg values: Any) = getString(id, *values)
    protected fun surface(title: String, back: () -> Unit = { finish() }) {
        val frame = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cream)
            clipChildren = false
            clipToPadding = false
            setPadding(dp(20), dp(12), dp(20), dp(16))
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(20) + insets.systemWindowInsetLeft, dp(12) + insets.systemWindowInsetTop,
                    dp(20) + insets.systemWindowInsetRight, dp(16) + insets.systemWindowInsetBottom)
                insets
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            clipChildren = false
            clipToPadding = false
        }
        header.addView(Button(this).apply {
            text = "←"; textSize = 34f; setTypeface(typeface, Typeface.BOLD); setTextColor(ink)
            contentDescription = tr(R.string.communication_back)
            background = shape(Color.WHITE, true); minimumHeight = dp(64); minimumWidth = dp(76)
            stateListAnimator = null; elevation = dp(2).toFloat(); clipToOutline = false
            setOnClickListener { back() }
        }, LinearLayout.LayoutParams(dp(76), dp(64)).apply { marginEnd = dp(12) })
        header.addView(TextView(this).apply {
            text = title; textSize = 29f; setTextColor(ink); setTypeface(null, Typeface.BOLD)
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, LinearLayout.LayoutParams(0, -2, 1f))
        frame.addView(header, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(20) })
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = R.id.communication_content
            clipChildren = false
            clipToPadding = false
        }
        frame.addView(ScrollView(this).apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(content)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        val homeBtn = Button(this).apply {
            text = "🏠  " + tr(R.string.communication_home); textSize = 20f; isAllCaps = false
            minimumHeight = dp(64); setTextColor(ink); background = shape(Color.WHITE, true)
            stateListAnimator = null; elevation = dp(2).toFloat(); clipToOutline = false
            setOnClickListener {
                startActivity(Intent(this@CommunicationUiActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                finish()
            }
        }
        bottomBar.addView(homeBtn, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })

        val ashaBtn = Button(this).apply {
            textSize = 20f; isAllCaps = false; setTypeface(typeface, Typeface.BOLD)
            minimumHeight = dp(64); setTextColor(Color.WHITE)
            stateListAnimator = null; elevation = dp(2).toFloat(); clipToOutline = false
        }
        ashaBottomButton = ashaBtn
        updateAshaBottomButton()
        bottomBar.addView(ashaBtn, LinearLayout.LayoutParams(0, -2, 1f))
        frame.addView(bottomBar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12); bottomMargin = dp(8) })
        window.statusBarColor = cream
        window.navigationBarColor = cream
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContentView(frame)
        frame.requestApplyInsets()
    }
    protected fun shape(color: Int, border: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(22).toFloat()
        if (border) setStroke(dp(1), Color.rgb(211, 220, 211))
    }
    protected fun label(text: String, size: Float = 19f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(ink)
        setPadding(dp(4), dp(4), dp(4), dp(12))
    }
    protected fun action(label: String, id: Int = View.NO_ID, secondary: Boolean = false, click: () -> Unit): Button =
        Button(this).apply {
            this.id = id; text = label; textSize = 21f; isAllCaps = false
            minimumHeight = dp(68); setPadding(dp(16), dp(16), dp(16), dp(16))
            stateListAnimator = null; elevation = dp(2).toFloat(); clipToOutline = false
            setTextColor(if (secondary) ink else Color.WHITE); background = shape(if (secondary) Color.WHITE else green, secondary)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            setOnClickListener { click() }
            content.addView(this)
        }
    protected fun avatar(name: String, photoUri: String, size: Int = 84): View {
        val frame = android.widget.FrameLayout(this).apply {
            background = shape(Color.rgb(228, 236, 224)); clipToOutline = true
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        frame.addView(TextView(this).apply {
            text = name.trim().firstOrNull()?.toString().orEmpty(); textSize = (size * 0.43).toFloat()
            setTextColor(green); gravity = Gravity.CENTER; setTypeface(null, Typeface.BOLD)
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        val image = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        frame.addView(image, android.widget.FrameLayout.LayoutParams(-1, -1))
        if (photoUri.isNotBlank()) photoWorker.execute {
            val bitmap = try {
                val uri = Uri.parse(photoUri)
                if (uri.scheme != "content" && uri.scheme != "android.resource") null else {
                    val data = contentResolver.openInputStream(uri)?.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > 8 * 1024 * 1024 || Thread.currentThread().isInterrupted) return@use null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    data?.let {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(it, 0, it.size, options)
                        if (options.outWidth <= 0 || options.outHeight <= 0) null else {
                            options.inSampleSize = 1
                            while (options.outWidth / options.inSampleSize > 320 || options.outHeight / options.inSampleSize > 320)
                                options.inSampleSize *= 2
                            options.inJustDecodeBounds = false
                            BitmapFactory.decodeByteArray(it, 0, it.size, options)
                        }
                    }
                }
            } catch (_: Exception) { null }
            runOnUiThread { if (!isFinishing && !isDestroyed && image.isAttachedToWindow) image.setImageBitmap(bitmap) }
        }
        return frame
    }
    protected fun sayAloud(message: String) {
        if (voiceReady) {
            voice?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "screen_line")
        } else if (voice == null) {
            voice = TextToSpeech(this) { status ->
                val language = voice?.setLanguage(AppLanguage.locale(this))
                voiceReady = status == TextToSpeech.SUCCESS && language != null && language >= TextToSpeech.LANG_AVAILABLE
                if (voiceReady) voice?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "screen_line")
            }
        }
    }
    override fun onDestroy() { voice?.shutdown(); photoWorker.shutdownNow(); super.onDestroy() }
}
