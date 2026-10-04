package `in`.family.maa

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.*

/** Small native-view surface shared by the offline daily tools. */
abstract class DailyToolsActivity : Activity() {
    protected val ink = Color.rgb(23, 46, 39)
    protected val green = Color.rgb(18, 92, 72)
    protected lateinit var body: LinearLayout
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(AppLanguage.localizedContext(newBase)) }
    protected fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    protected fun surface(title: Int) {
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(24))
            setBackgroundColor(Color.rgb(246, 244, 236))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        val frame = FrameLayout(this).apply {
            addView(scroll)
            setOnApplyWindowInsetsListener { v, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                    v.setPadding(safe.left, safe.top, safe.right, safe.bottom)
                } else { @Suppress("DEPRECATION") v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom) }
                insets
            }
        }
        setContentView(frame)
        text(getString(title), 32f)
        button(getString(R.string.daily_home)) { startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)); finish() }
    }
    protected fun text(value: String, size: Float = 20f, parent: LinearLayout = body): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(ink); setPadding(0, dp(8), 0, dp(12)); parent.addView(this)
    }
    protected fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(16))
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(24).toFloat() }
        body.addView(this, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
    }
    protected fun button(label: String, parent: LinearLayout = body, action: () -> Unit): Button = Button(this).apply {
        text = label; textSize = 20f; isAllCaps = false; minHeight = dp(64)
        setTextColor(Color.WHITE); background = GradientDrawable().apply { setColor(green); cornerRadius = dp(18).toFloat() }
        setPadding(dp(12), dp(12), dp(12), dp(12)); setOnClickListener { action() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
    }
    protected fun toast(id: Int) { Toast.makeText(this, id, Toast.LENGTH_LONG).show() }
    protected fun launch(intent: Intent): Boolean = try { startActivity(intent); true } catch (_: Exception) { toast(R.string.daily_unavailable); false }
}
