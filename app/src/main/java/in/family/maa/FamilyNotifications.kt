package `in`.family.maa

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

/** Opt-in quieting only. Never claims to block delivery or classify fraud. */
class FamilyNotifications : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if(sbn.packageName!="com.whatsapp") return
        val prefs=getSharedPreferences("family",MODE_PRIVATE)
        if(!prefs.getBoolean("quiet",false)) return
        val allowed=prefs.getString("trusted","")!!.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if(allowed.isEmpty()) return
        if(sbn.notification.category==Notification.CATEGORY_CALL || sbn.notification.flags and Notification.FLAG_ONGOING_EVENT !=0) return
        val title=sbn.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: return
        if(allowed.any { title.equals(it,true) }) return
        cancelNotification(sbn.key)
        // Retain only a count, never message contents or sender identities.
        prefs.edit().putInt("quietCount",prefs.getInt("quietCount",0)+1).apply()
    }
}
