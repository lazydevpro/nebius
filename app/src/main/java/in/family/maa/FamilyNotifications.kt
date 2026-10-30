package `in`.family.maa

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.app.Notification

/** Opt-in quieting only. Never claims to block delivery or classify fraud. */
class FamilyNotifications : NotificationListenerService() {
    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val UNREAD_COUNT = Regex("\\s*\\(\\d+[^)]*\\)\\s*$")
        private var connected:java.lang.ref.WeakReference<FamilyNotifications>?=null
        fun refreshArchive() { connected?.get()?.refreshCurrent() }
    }
    private fun refreshCurrent() { try { NotificationArchive.onConnected(this,activeNotifications ?: emptyArray()) } catch(_:SecurityException) { NotificationArchive.onDisconnected() } }
    override fun onListenerConnected() { connected=java.lang.ref.WeakReference(this);refreshCurrent() }
    override fun onListenerDisconnected() { connected=null;NotificationArchive.onDisconnected() }
    override fun onDestroy() { connected=null;NotificationArchive.onDisconnected();super.onDestroy() }
    /** Group notifications carry the group's name separately, with an unread count: "Family (2 messages)". */
    private fun chatName(extras: android.os.Bundle, title: String): String =
        (runCatching { extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() }.getOrNull() ?: title)
            .replace(UNREAD_COUNT, "").trim()
    private fun lastMessage(extras: android.os.Bundle) =
        runCatching { extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.lastOrNull() as? android.os.Bundle }.getOrNull()
    private fun latestMessage(extras: android.os.Bundle, text: String): String =
        lastMessage(extras)?.getCharSequence("text")?.toString() ?: text
    private fun latestMessageTime(extras: android.os.Bundle): Long = lastMessage(extras)?.getLong("time") ?: 0L
    override fun onNotificationRemoved(sbn:StatusBarNotification) { NotificationArchive.removed(sbn.key) }
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.notification == null) return
        NotificationArchive.record(this, sbn)
        val extras = runCatching { sbn.notification.extras }.getOrNull() ?: return
        val title = runCatching { extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty() }.getOrDefault("")
        val text = runCatching {
            (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()?.trim().orEmpty()
        }.getOrDefault("")

        if (sbn.notification.category == Notification.CATEGORY_MISSED_CALL ||
            sbn.packageName.contains("dialer") || sbn.packageName.contains("telecom")) {
            if (sbn.notification.category == Notification.CATEGORY_MISSED_CALL ||
                text.contains("missed", ignoreCase = true) || text.contains("मिस्ड", ignoreCase = true)) {
                ProactiveAsha.onMissedCallNotification(this, title, text)
            }
        }
        if (sbn.packageName !in WHATSAPP_PACKAGES) return
        // The summary only counts chats ("5 messages from 2 chats"), so it says nothing about a sender.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0) {
            ProactiveAsha.onWhatsAppNotification(this, chatName(extras, title), latestMessage(extras, text), latestMessageTime(extras))
        }
        if (sbn.packageName != "com.whatsapp") return
        val prefs = getSharedPreferences("family", MODE_PRIVATE)
        if (!prefs.getBoolean("quiet", false)) return
        val allowed = prefs.getString("trusted", "")!!.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
        if (allowed.isEmpty()) return
        if (sbn.isOngoing || sbn.notification.category == Notification.CATEGORY_CALL || sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) return
        if (title.isEmpty()) return
        if (allowed.any { title.equals(it, true) }) return
        runCatching { cancelNotification(sbn.key) }
        // Retain only a count, never message contents or sender identities.
        prefs.edit().putInt("quietCount", prefs.getInt("quietCount", 0) + 1).apply()
    }
}
