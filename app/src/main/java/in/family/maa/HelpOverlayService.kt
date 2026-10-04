package `in`.family.maa

import android.app.Service
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button

class HelpOverlayService : Service() {
    private var button: Button? = null
    private lateinit var windows: WindowManager
    private val prefs by lazy { getSharedPreferences("family",MODE_PRIVATE) }
    private val languageListener=SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
        if(key==AppLanguage.KEY && button!=null) updateLanguage()
    }
    private fun tr(id: Int) = AppLanguage.text(this,id)
    override fun onCreate() { super.onCreate(); prefs.registerOnSharedPreferenceChangeListener(languageListener) }
    override fun onBind(intent: Intent?): IBinder? = null
    private fun updateLanguage() {
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("help",tr(R.string.overlay_channel),NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),PendingIntent.FLAG_IMMUTABLE)
        startForeground(1,Notification.Builder(this,"help").setSmallIcon(R.drawable.ic_maa).setContentTitle(tr(R.string.overlay_title)).setContentText(tr(R.string.overlay_notification)).setContentIntent(open).build())
        button?.apply { text=tr(R.string.overlay_button); contentDescription=tr(R.string.overlay_description) }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(!Settings.canDrawOverlays(this)) { stopSelf(); return START_NOT_STICKY }
        updateLanguage()
        if(button==null) {
            windows=getSystemService(WindowManager::class.java)
            button=Button(this).apply {
                text=tr(R.string.overlay_button); textSize=22f; isAllCaps=false; setTextColor(Color.WHITE); setBackgroundColor(Color.rgb(23,107,87)); contentDescription=tr(R.string.overlay_description)
                setOnClickListener { startActivity(Intent(this@HelpOverlayService,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
            }
            val density=resources.displayMetrics.density
            val params=WindowManager.LayoutParams((140*density).toInt(),(72*density).toInt(),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT).apply { gravity=Gravity.BOTTOM or Gravity.END; x=(12*density).toInt(); y=(88*density).toInt() }
            try { windows.addView(button,params) } catch(_:Exception) { button=null; stopSelf() }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { prefs.unregisterOnSharedPreferenceChangeListener(languageListener); button?.let { try { windows.removeView(it) } catch(_:Exception) {} }; button=null; super.onDestroy() }
}
