package `in`.family.maa

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.widget.EditText

object SosRecipient {
    /** International numbers only; no silently guessed country code or extension. */
    fun canonical(raw: String): String? {
        val compact = raw.trim().replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        return compact.takeIf { Regex("\\+[1-9][0-9]{7,14}").matches(it) }
    }
}

/** Family help preparation, deliberately not an unattended emergency dialer. */
class SosActivity : DailyToolsActivity() {
    private val prefs by lazy { getSharedPreferences("maa_sos_v1", MODE_PRIVATE) }
    private var countdown: CountDownTimer? = null
    private var counting = false
    override fun onCreate(state: Bundle?) { super.onCreate(state); render() }
    override fun onStop() { cancelCountdown(); super.onStop() }
    override fun onRestart() { super.onRestart(); render() }
    override fun onDestroy() { cancelCountdown(); super.onDestroy() }
    private fun cancelCountdown() { countdown?.cancel(); countdown = null; counting = false }
    private fun render() {
        cancelCountdown(); surface(R.string.daily_sos_title)
        text(getString(R.string.daily_sos_explanation), 20f)
        val number = prefs.getString("number", "").orEmpty()
        val name = prefs.getString("name", "").orEmpty()
        if (SosRecipient.canonical(number) != null && name.isNotBlank()) {
            val c = card(); text(name, 30f, c); text(number, 22f, c)
            button(getString(R.string.daily_sos_start), c) { startCountdown(name, number, prefs.getString("message", "").orEmpty().ifBlank { getString(R.string.daily_sos_default_message) }) }.id = R.id.daily_sos_start
        } else text(getString(R.string.daily_sos_not_configured))
        button(getString(R.string.daily_sos_setup)) { CaregiverLock.request(this) { setup() } }
        text(getString(R.string.daily_sos_no_location), 16f)
    }
    private fun setup() {
        surface(R.string.daily_sos_setup)
        text(getString(R.string.daily_sos_number_hint), 18f)
        val name = EditText(this).apply { hint = getString(R.string.daily_sos_name); setText(prefs.getString("name", "")); textSize = 24f; minHeight = dp(64); id = R.id.daily_sos_name }
        val number = EditText(this).apply { hint = getString(R.string.daily_sos_number); inputType = InputType.TYPE_CLASS_PHONE; setText(prefs.getString("number", "")); textSize = 24f; minHeight = dp(64); id = R.id.daily_sos_number }
        val message = EditText(this).apply { hint = getString(R.string.daily_sos_message); setText(prefs.getString("message", getString(R.string.daily_sos_default_message))); textSize = 22f; minLines = 3; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        body.addView(name); body.addView(number); body.addView(message)
        button(getString(R.string.daily_save)) {
            if (!CaregiverLock.unlocked(this)) { CaregiverLock.request(this) { setup() }; return@button }
            val canonical = SosRecipient.canonical(number.text.toString())
            if (canonical == null) { number.error = getString(R.string.daily_sos_number_invalid); return@button }
            if (name.text.isBlank()) { name.error = getString(R.string.daily_sos_name); return@button }
            if (prefs.edit().putString("name", name.text.toString().trim().take(80)).putString("number", canonical)
                    .putString("message", message.text.toString().trim().take(1000)).commit()) render() else toast(R.string.daily_save_failed)
        }
        button(getString(R.string.daily_cancel)) { render() }
    }
    private fun startCountdown(name: String, number: String, message: String) {
        cancelCountdown(); surface(R.string.daily_sos_title); counting = true
        text(getString(R.string.daily_sos_preparing, name), 26f)
        val remaining = text("", 44f)
        text(getString(R.string.daily_sos_countdown_explanation), 18f)
        button(getString(R.string.daily_cancel)) { render() }.id = R.id.daily_sos_cancel
        countdown = object : CountDownTimer(5000L, 250L) {
            override fun onTick(millis: Long) { remaining.text = ((millis + 999L) / 1000L).toString() }
            override fun onFinish() { counting = false; countdown = null; review(name, number, message) }
        }.start()
    }
    private fun review(name: String, number: String, message: String) {
        surface(R.string.daily_sos_review); text(name, 30f); text(number, 24f); text(message)
        text(getString(R.string.daily_sos_review_explanation), 18f)
        button(getString(R.string.daily_sos_dial)) { launch(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))) }
        button(getString(R.string.daily_sos_sms)) { launch(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", number, null)).putExtra("sms_body", message)) }
        button(getString(R.string.daily_cancel)) { render() }
    }
}
