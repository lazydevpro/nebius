package `in`.family.maa

import android.app.Service
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper

/** Audible exact alarm. No activity launch and no full-screen intent. */
class MaaAlarmRingService : Service() {
    private var player: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra(MaaAlarmScheduler.ID) ?: return START_NOT_STICKY.also { stopSelf() }
        val alarm = MaaAlarmStore(this).all().firstOrNull { it.id == id } ?: return START_NOT_STICKY.also { stopSelf() }
        ringingId = id
        startForeground(7101, MaaAlarmScheduler.notification(this, alarm))
        player?.release(); player = null
        try {
            val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                setDataSource(this@MaaAlarmRingService, sound); isLooping = true; prepare(); start()
            }
        } catch (_: Exception) {
            player?.release(); player = null
            val fallback = Notification.Builder.recoverBuilder(this, MaaAlarmScheduler.notification(this, alarm))
                .setContentText(AppLanguage.text(this, R.string.daily_alarm_sound_error)).build()
            getSystemService(NotificationManager::class.java).notify(7101, fallback)
        }
        handler.removeCallbacks(timeout); handler.postDelayed(timeout, 10 * 60_000L)
        return START_NOT_STICKY
    }
    override fun onDestroy() { handler.removeCallbacks(timeout); player?.release(); player = null; ringingId = null; super.onDestroy() }
    companion object {
        @Volatile var ringingId: String? = null; private set
        fun stopIfCurrent(context: Context, id: String) { if (ringingId == id) context.stopService(Intent(context, MaaAlarmRingService::class.java)) }
    }
}
