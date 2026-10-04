package `in`.family.maa

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.SystemClock
import android.text.InputType
import android.util.Base64
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Local configuration protection; intentionally cannot trap the owner in Maa. */
object CaregiverLock {
    private val dialogs=mutableMapOf<Activity,MutableList<AlertDialog>>()
    private fun track(activity:Activity,dialog:AlertDialog) {
        dialogs.getOrPut(activity) { mutableListOf() }.add(dialog)
        dialog.setOnDismissListener { dialogs[activity]?.let { list -> list.remove(dialog);if(list.isEmpty()) dialogs.remove(activity) } }
    }
    fun dismiss(activity:Activity) { dialogs.remove(activity)?.toList()?.forEach { it.dismiss() } }
    private var unlockedUntil=0L
    private fun prefs(c:Context)=c.getSharedPreferences("caregiver_lock",Context.MODE_PRIVATE)
    fun configured(c:Context)=prefs(c).contains("hash")
    fun unlocked(c:Context)=!configured(c) || SystemClock.elapsedRealtime()<unlockedUntil
    fun relock() { unlockedUntil=0 }
    private fun tr(c:Context,id:Int)=AppLanguage.text(c,id)
    private fun derive(pin:String,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(pin.toCharArray(),salt,120_000,256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
    fun request(activity:Activity, onCancelled:(()->Unit)?=null, onUnlocked:()->Unit) {
        if(configured(activity) && unlocked(activity)) { onUnlocked(); return }
        if(!configured(activity)) {
            AlertDialog.Builder(activity).setTitle(tr(activity,R.string.settings_title)).setMessage(tr(activity,R.string.settings_notice))
                .setPositiveButton(tr(activity,R.string.open)) { _,_->onUnlocked() }.setNegativeButton(tr(activity,R.string.back)) { _,_->onCancelled?.invoke() }.setOnCancelListener { onCancelled?.invoke() }.show().also { track(activity,it) }
            return
        }
        val p=prefs(activity)
        if(p.getLong("retryAfter",0)>System.currentTimeMillis()) { Toast.makeText(activity,tr(activity,R.string.home_pin_wait),Toast.LENGTH_LONG).show(); return }
        val input=EditText(activity).apply { hint=tr(activity,R.string.home_pin_hint); inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; textSize=24f }
        val dialog=AlertDialog.Builder(activity).setTitle(tr(activity,R.string.home_unlock_title)).setView(input)
            .setPositiveButton(tr(activity,R.string.open),null).setNegativeButton(tr(activity,R.string.cancel)) { _,_->onCancelled?.invoke() }
            .setOnCancelListener { onCancelled?.invoke() }
            .setNeutralButton("?",null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                AlertDialog.Builder(activity).setMessage(tr(activity,R.string.home_pin_recovery)).setPositiveButton(android.R.string.ok,null).show().also { track(activity,it) }
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin=input.text.toString(); input.text.clear(); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
                Thread {
                    val valid=runCatching { MessageDigest.isEqual(derive(pin,Base64.decode(p.getString("salt",""),Base64.NO_WRAP)),Base64.decode(p.getString("hash",""),Base64.NO_WRAP)) }.getOrDefault(false)
                    activity.runOnUiThread {
                        if(activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                        if(valid) { p.edit().remove("attempts").remove("retryAfter").apply(); unlockedUntil=SystemClock.elapsedRealtime()+120_000; dialog.dismiss(); onUnlocked() }
                        else {
                            val attempts=p.getInt("attempts",0)+1
                            p.edit().putInt("attempts",attempts).apply()
                            if(attempts>=5) { p.edit().putInt("attempts",0).putLong("retryAfter",System.currentTimeMillis()+60_000).apply(); dialog.dismiss(); Toast.makeText(activity,tr(activity,R.string.home_pin_wait),Toast.LENGTH_LONG).show() }
                            else { input.error=tr(activity,R.string.home_pin_wrong); dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true }
                        }
                    }
                }.start()
            }
        }; track(activity,dialog);dialog.show()
    }
    fun configure(activity:Activity,changed:()->Unit) {
        if(!unlocked(activity)) { request(activity) { configure(activity,changed) }; return }
        val container=LinearLayout(activity).apply { orientation=LinearLayout.VERTICAL; setPadding(32,8,32,8) }
        fun field(label:Int)=EditText(activity).apply { hint=tr(activity,label); textSize=22f; inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD; container.addView(this) }
        val first=field(R.string.home_pin_hint);val second=field(R.string.home_pin_confirm)
        val dialog=AlertDialog.Builder(activity).setTitle(tr(activity,R.string.home_set_pin)).setMessage(tr(activity,R.string.home_pin_recovery)).setView(container)
            .setPositiveButton(tr(activity,R.string.save),null).setNegativeButton(tr(activity,R.string.cancel),null).create()
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            if(!unlocked(activity)) { dialog.dismiss();request(activity) { configure(activity,changed) };return@setOnClickListener }
            val pin=first.text.toString()
            if(!pin.matches(Regex("[0-9]{6,12}")) || pin!=second.text.toString()) { second.error=tr(activity,R.string.home_pin_invalid);return@setOnClickListener }
            first.text.clear();second.text.clear();dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=false
            Thread {
                val salt=ByteArray(24).also { SecureRandom().nextBytes(it) }
                val digest=runCatching { derive(pin,salt) }
                activity.runOnUiThread {
                    if(activity.isDestroyed || !dialog.isShowing) return@runOnUiThread
                    if(!unlocked(activity)) { dialog.dismiss();request(activity) { configure(activity,changed) };return@runOnUiThread }
                    digest.onSuccess { hash -> prefs(activity).edit().putString("salt",Base64.encodeToString(salt,Base64.NO_WRAP)).putString("hash",Base64.encodeToString(hash,Base64.NO_WRAP)).remove("retryAfter").remove("attempts").commit();unlockedUntil=SystemClock.elapsedRealtime()+120_000;dialog.dismiss();Toast.makeText(activity,tr(activity,R.string.home_pin_saved),Toast.LENGTH_LONG).show();changed() }
                        .onFailure { dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=true;second.error=tr(activity,R.string.app_unavailable) }
                }
            }.start()
        } };track(activity,dialog);dialog.show()
    }
    fun remove(activity:Activity,changed:()->Unit) {
        if(!unlocked(activity)) { request(activity) { remove(activity,changed) };return }
        AlertDialog.Builder(activity).setMessage(tr(activity,R.string.home_pin_remove)).setPositiveButton(tr(activity,R.string.yes)) { _,_->
            if(!unlocked(activity)) request(activity) { remove(activity,changed) }
            else { prefs(activity).edit().clear().apply();relock();changed() }
        }.setNegativeButton(tr(activity,R.string.cancel),null).show().also { track(activity,it) }
    }
}
