package `in`.family.maa

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/** Optional read-only system directory. It never edits or exports device contacts. */
class PhoneContactsActivity : CommunicationUiActivity() {
    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var cancellation: CancellationSignal? = null
    private var generation = 0
    private var grantedWhenRendered = false
    private var query = ""
    private var search: EditText? = null
    private var results: LinearLayout? = null
    private var summary: TextView? = null
    private data class Contact(val name: String, val number: String, val canonical: String?)
    companion object {
        private const val CONTACT_PERMISSION = 301
        private const val DISPLAY_LIMIT = 100
        fun intent(context: Context) = Intent(context, PhoneContactsActivity::class.java)
    }
    override fun onCreate(state: Bundle?) { super.onCreate(state); query = state?.getString("query").orEmpty(); render() }
    override fun onResume() {
        super.onResume()
        if (grantedWhenRendered != hasContacts()) render() else if (hasContacts()) scheduleQuery()
    }
    private fun hasContacts() = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    private fun render() {
        generation++; cancellation?.cancel(); handler.removeCallbacksAndMessages(null)
        search = null; results = null; summary = null
        surface(tr(R.string.communication_device_contacts))
        grantedWhenRendered = hasContacts()
        if (!grantedWhenRendered) {
            content.addView(label(tr(R.string.communication_contacts_explanation)))
            action(tr(R.string.communication_allow_contacts), R.id.communication_allow_contacts) {
                requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), CONTACT_PERMISSION)
            }
            action(tr(R.string.communication_keypad), secondary = true) { startActivity(PhoneDialerActivity.intent(this)) }
            content.addView(label(tr(R.string.communication_contacts_denial), 17f))
            return
        }
        content.addView(label(tr(R.string.communication_contacts_local), 17f))
        val input = EditText(this).apply {
            id = R.id.communication_contact_search; hint = tr(R.string.communication_search_contacts)
            contentDescription = hint; textSize = 22f; setTextColor(ink); minimumHeight = dp(64)
            setSingleLine(true); setText(query); setPadding(dp(14), dp(12), dp(14), dp(12))
            background = shape(android.graphics.Color.WHITE, true)
        }
        search = input
        content.addView(input)
        summary = label(tr(R.string.communication_loading), 17f).also { content.addView(it) }
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }.also { content.addView(it) }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { query = s?.toString().orEmpty(); scheduleQuery() }
        })
        scheduleQuery()
    }
    private fun scheduleQuery() {
        handler.removeCallbacksAndMessages(null)
        generation++; cancellation?.cancel()
        val request = generation
        val term = query.trim().take(100)
        handler.postDelayed({ load(term, request) }, 250)
    }
    private fun load(term: String, request: Int) {
        if (!hasContacts()) { render(); return }
        val signal = CancellationSignal().also { cancellation = it }
        summary?.text = tr(R.string.communication_loading)
        worker.execute {
            val rows = ArrayList<Contact>()
            var failed = false
            try {
                val uri = if (term.isBlank()) Phone.CONTENT_URI else android.net.Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, android.net.Uri.encode(term))
                contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME_PRIMARY, Phone.NUMBER), null, null,
                    "${Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC", signal)?.use { cursor ->
                    val nameColumn = cursor.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
                    val numberColumn = cursor.getColumnIndexOrThrow(Phone.NUMBER)
                    val seen = HashSet<String>()
                    while (cursor.moveToNext() && rows.size <= DISPLAY_LIMIT && !signal.isCanceled) {
                        val number = cursor.getString(numberColumn).orEmpty()
                        val canonical = FamilyDirectory.canonicalNumber(number)
                        val name = cursor.getString(nameColumn).orEmpty().ifBlank { number }
                        if (seen.add("$name|${canonical ?: number}")) rows.add(Contact(name, number, canonical))
                    }
                } ?: run { failed = true }
            } catch (_: android.os.OperationCanceledException) { return@execute }
            catch (_: Exception) { failed = true }
            runOnUiThread {
                if (request != generation || isFinishing || isDestroyed) return@runOnUiThread
                if (!hasContacts()) { render(); return@runOnUiThread }
                val list = results ?: return@runOnUiThread
                list.removeAllViews()
                summary?.text = when {
                    failed -> tr(R.string.communication_contacts_failed)
                    rows.isEmpty() -> tr(R.string.communication_contacts_empty)
                    rows.size > DISPLAY_LIMIT -> tr(R.string.communication_contacts_limit, DISPLAY_LIMIT)
                    else -> tr(R.string.communication_contacts_count, rows.size)
                }
                rows.take(DISPLAY_LIMIT).forEach { contact ->
                    list.addView(android.widget.Button(this).apply {
                        text = "${contact.name}\n${contact.canonical ?: contact.number}"
                        textSize = 22f; isAllCaps = false; setTextColor(ink); minimumHeight = dp(92)
                        setPadding(dp(16), dp(16), dp(16), dp(16)); background = shape(android.graphics.Color.WHITE, true)
                        setOnClickListener {
                            if (contact.canonical == null) android.widget.Toast.makeText(this@PhoneContactsActivity, tr(R.string.communication_invalid_number), android.widget.Toast.LENGTH_LONG).show()
                            else startActivity(PhoneDialerActivity.intent(this@PhoneContactsActivity, contact.canonical, contact.name))
                        }
                    }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CONTACT_PERMISSION) render()
    }
    override fun onSaveInstanceState(state: Bundle) { state.putString("query", query); super.onSaveInstanceState(state) }
    override fun onStop() { generation++; cancellation?.cancel(); handler.removeCallbacksAndMessages(null); super.onStop() }
    override fun onDestroy() { worker.shutdownNow(); super.onDestroy() }
}
