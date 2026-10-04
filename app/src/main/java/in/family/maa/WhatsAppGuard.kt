package `in`.family.maa

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/** Experimental UI masking. Never clicks, sends messages, or stores screen text. */
class WhatsAppGuard : AccessibilityService() {
    private val masks=mutableListOf<TextView>()
    private var signature=""
    private val distractions=setOf("updates","communities","channels","status","अपडेट","अपडेट्स","कम्युनिटी","कम्युनिटीज़","चैनल","स्टेटस")
    private fun clear() {
        val windows=getSystemService(WindowManager::class.java)
        masks.forEach { try { windows.removeView(it) } catch(_:Exception) {} }
        masks.clear(); signature=""
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val prefs=getSharedPreferences("family",MODE_PRIVATE)
        if(!prefs.getBoolean("guard",false)) { clear(); return }
        // Do not inspect other applications. Window switches remove all masks.
        if(event?.packageName?.toString()!="com.whatsapp") { if(event?.eventType==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) clear(); return }
        val node=rootInActiveWindow ?: run { clear(); return }
        if(node.packageName?.toString()!="com.whatsapp") { clear(); return }
        val blocked=mutableListOf<Rect>()
        val metrics=resources.displayMetrics
        var visited=0
        fun visit(current: AccessibilityNodeInfo) {
            if(++visited>600 || blocked.size>=12) return
            val label=(current.text ?: current.contentDescription)?.toString()?.trim()?.lowercase() ?: ""
            val isLink=label.contains("https://") || label.contains("http://") || label.startsWith("www.")
            if(current.isVisibleToUser && (label in distractions || isLink)) {
                val bounds=Rect(); current.getBoundsInScreen(bounds)
                if(bounds.width()>20 && bounds.height()>16 && bounds.width()*bounds.height() < metrics.widthPixels*metrics.heightPixels/4) blocked.add(bounds)
            }
            for(i in 0 until current.childCount) current.getChild(i)?.let { visit(it) }
        }
        visit(node)
        val next=AppLanguage.code(this)+blocked.joinToString(";") { it.toShortString() }
        if(next==signature) return
        clear(); signature=next
        val windows=getSystemService(WindowManager::class.java)
        blocked.distinctBy { it.toShortString() }.forEach { bounds ->
            val mask=TextView(this).apply {
                text="♥"; textSize=20f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(23,107,87)); contentDescription=AppLanguage.text(this@WhatsAppGuard,R.string.guard_description)
                setOnClickListener { clear(); startActivity(Intent(this@WhatsAppGuard,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            val params=WindowManager.LayoutParams(bounds.width(),bounds.height(),WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.TOP or Gravity.LEFT; x=bounds.left; y=bounds.top }
            try { windows.addView(mask,params); masks.add(mask) } catch(_:Exception) { clear(); return }
        }
    }
    override fun onInterrupt() { clear() }
    override fun onUnbind(intent: Intent?): Boolean { clear(); return super.onUnbind(intent) }
    override fun onDestroy() { clear(); super.onDestroy() }
}
