package `in`.family.maa

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

/** Opt-in quieting only. Never claims to block delivery or classify fraud. */
class FamilyNotifications : NotificationListenerService() {
    companion object {
        private var connected:java.lang.ref.WeakReference<FamilyNotifications>?=null
        fun refreshArchive() { connected?.get()?.refreshCurrent() }
    }
    private fun refreshCurrent() { try { NotificationArchive.onConnected(this,activeNotifications ?: emptyArray()) } catch(_:SecurityException) { NotificationArchive.onDisconnected() } }
    override fun onListenerConnected() { connected=java.lang.ref.WeakReference(this);refreshCurrent() }
    override fun onListenerDisconnected() { connected=null;NotificationArchive.onDisconnected() }
    override fun onDestroy() { connected=null;NotificationArchive.onDisconnected();super.onDestroy() }
    override fun onNotificationRemoved(sbn:StatusBarNotification) { NotificationArchive.removed(sbn.key) }
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        NotificationArchive.record(this,sbn)
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
