package `in`.family.maa

import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import java.text.Collator
import java.util.concurrent.Executors

/** Launchable apps only; hiding affects Maa's list, never another app's permissions. */
class AppsActivity : Activity() {
    private data class AppEntry(val component: ComponentName, val label: String, val icon: Drawable) {
        val key: String get() = component.flattenToString()
    }
    private val preferences by lazy { getSharedPreferences("maa_apps", MODE_PRIVATE) }
    private val worker = Executors.newSingleThreadExecutor()
    private var generation = 0
    private var entries = emptyList<AppEntry>()
    private var shown = emptyList<AppEntry>()
    private var search = ""
    private var showHidden = false
    private var organizing = false
    private var resumed = false
    private var authorizationEpoch = 0
    private val dialogs = mutableListOf<AlertDialog>()
    private var receiverRegistered = false
    private lateinit var empty: TextView
    private lateinit var hiddenButton: android.widget.Button
    private lateinit var organizeButton: android.widget.Button
    private lateinit var list: ListView
    private val adapter = AppsAdapter()
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }
    private fun local(): Context = AppLanguage.localizedContext(this)
    private fun label(id: Int): String = local().getString(id)
    private fun favorites(): Set<String> = preferences.getStringSet("favorites", emptySet())?.toSet().orEmpty()
    private fun hidden(): Set<String> = preferences.getStringSet("hidden", emptySet())?.toSet().orEmpty()

    override fun attachBaseContext(base: Context) { super.attachBaseContext(AppLanguage.localizedContext(base)) }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        search = state?.getString("search").orEmpty()
        showHidden = (state?.getBoolean("hidden") ?: false) && CaregiverLock.unlocked(this)
        organizing = (state?.getBoolean("organizing") ?: false) && CaregiverLock.unlocked(this)
        window.statusBarColor = PlatformUi.CREAM
        window.navigationBarColor = PlatformUi.CREAM
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(PlatformUi.CREAM)
            clipChildren = false; clipToPadding = false
            setPadding(0, PlatformUi.dp(this@AppsActivity, 16), 0, PlatformUi.dp(this@AppsActivity, 16))
        }
        root.addView(PlatformUi.button(this, label(R.string.platform_back)) { finish() }.apply { id = R.id.platform_back }, PlatformUi.params(this, 0))
        root.addView(PlatformUi.text(this, 30f, bold = true).apply { text = label(R.string.platform_apps); if (Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true }, PlatformUi.params(this))
        val searchField = EditText(this).apply {
            id = R.id.platform_app_search; hint = label(R.string.platform_app_search)
            contentDescription = hint; textSize = 21f; minHeight = PlatformUi.dp(this@AppsActivity, 64)
            setSingleLine(true); setText(search)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { search = s?.toString().orEmpty(); filter() }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        root.addView(searchField, PlatformUi.params(this, 4))
        organizeButton = PlatformUi.button(this, "") {
            if (organizing) { organizing = false; updateButtons(); adapter.notifyDataSetChanged() }
            else requestChanges { organizing = true; updateButtons(); adapter.notifyDataSetChanged() }
        }.apply { id = R.id.platform_organize }
        hiddenButton = PlatformUi.button(this, "") {
            if (showHidden) { showHidden = false; updateButtons(); filter() }
            else requestChanges { showHidden = true; updateButtons(); filter() }
        }.apply { id = R.id.platform_hidden_toggle }
        val controls = LinearLayout(this).apply {
            orientation = if (resources.configuration.fontScale > 1.3f || resources.configuration.screenWidthDp < 360) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        for (control in listOf(organizeButton, hiddenButton)) {
            controls.addView(control, if (controls.orientation == LinearLayout.HORIZONTAL)
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = PlatformUi.dp(this@AppsActivity, 4) }
            else PlatformUi.params(this, 4))
        }
        root.addView(controls, PlatformUi.params(this, 8))
        empty = PlatformUi.text(this, 20f).apply { text = label(R.string.platform_loading_apps); setPadding(0, PlatformUi.dp(this@AppsActivity, 20), 0, 0) }
        root.addView(empty, PlatformUi.params(this, 0))
        list = ListView(this).apply {
            id = R.id.platform_app_list; divider = null; dividerHeight = PlatformUi.dp(this@AppsActivity, 8)
            setBackgroundColor(PlatformUi.CREAM)
            setPadding(PlatformUi.dp(this@AppsActivity, 20), 0, PlatformUi.dp(this@AppsActivity, 20), PlatformUi.dp(this@AppsActivity, 20))
            clipToPadding = false
            addHeaderView(root, null, false)
            adapter = this@AppsActivity.adapter
            setOnItemClickListener { _, _, position, _ ->
                shown.getOrNull(position - headerViewsCount)?.let { if (organizing) organize(it) else launch(it) }
            }
            setOnItemLongClickListener { _, _, position, _ ->
                shown.getOrNull(position - headerViewsCount)?.let { organize(it); true } ?: false
            }
        }
        setContentView(list)
        updateButtons()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED); addDataScheme("package")
        }
        try {
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(packages, filter, Context.RECEIVER_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(packages, filter)
            receiverRegistered = true
        } catch (_: RuntimeException) { /* onResume still refreshes if an OEM rejects package broadcasts. */ }
    }
    override fun onResume() { super.onResume(); resumed = true; reload() }
    override fun onPause() { resumed = false; super.onPause() }
    override fun onStop() {
        authorizationEpoch++
        dialogs.toList().forEach { it.dismiss() }; dialogs.clear()
        CaregiverLock.dismiss(this)
        if (!isChangingConfigurations) {
            CaregiverLock.relock(); organizing = false; showHidden = false
            updateButtons(); filter()
        }
        if (receiverRegistered) { unregisterReceiver(packages); receiverRegistered = false }
        super.onStop()
    }
    override fun onDestroy() { generation++; worker.shutdownNow(); super.onDestroy() }
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("search", search); out.putBoolean("hidden", showHidden); out.putBoolean("organizing", organizing)
        super.onSaveInstanceState(out)
    }

    private fun updateButtons() {
        organizeButton.text = label(if (organizing) R.string.platform_done else R.string.platform_organize)
        hiddenButton.text = label(if (showHidden) R.string.platform_hide_hidden else R.string.platform_show_hidden)
    }

    private fun reload() {
        if (isDestroyed || worker.isShutdown) return
        val token = ++generation
        worker.execute {
            val result = runCatching {
                val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                packageManager.queryIntentActivities(query, 0).asSequence()
                    .filter { it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && it.activityInfo.exported && it.activityInfo.packageName != packageName }
                    .mapNotNull { info -> runCatching { AppEntry(ComponentName(info.activityInfo.packageName, info.activityInfo.name), info.loadLabel(packageManager).toString(), info.loadIcon(packageManager)) }.getOrNull() }
                    .distinctBy { it.key }.toList()
            }
            runOnUiThread {
                if (isDestroyed || token != generation) return@runOnUiThread
                if (result.isSuccess) { entries = result.getOrThrow(); filter() }
                else { empty.text = label(R.string.platform_apps_load_failed); empty.visibility = View.VISIBLE }
            }
        }
    }

    private fun filter() {
        if (!::empty.isInitialized) return
        val hidden = hidden()
        val favorites = favorites()
        val collator = Collator.getInstance(AppLanguage.locale(this))
        shown = entries.filter {
            (showHidden || it.key !in hidden) && (search.isBlank() || it.label.contains(search.trim(), ignoreCase = true))
        }.sortedWith(Comparator { first, second ->
            val rank = (if (first.key in favorites) 0 else 1).compareTo(if (second.key in favorites) 0 else 1)
            if (rank != 0) rank else collator.compare(first.label, second.label)
        })
        empty.text = label(R.string.platform_no_apps)
        empty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
        adapter.notifyDataSetChanged()
    }

    private fun launch(entry: AppEntry) {
        try {
            startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(entry.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED))
        } catch (_: RuntimeException) {
            Toast.makeText(this, label(R.string.platform_app_unavailable), Toast.LENGTH_LONG).show(); reload()
        }
    }

    private fun updateSet(key: String, entry: AppEntry, include: Boolean, expectedEpoch: Int = authorizationEpoch) {
        if (!resumed || isFinishing || isDestroyed || expectedEpoch != authorizationEpoch) return
        if (!CaregiverLock.unlocked(this)) {
            requestChanges { updateSet(key, entry, include, expectedEpoch) }
            return
        }
        val next = preferences.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (include) next.add(entry.key) else next.remove(entry.key)
        preferences.edit().putStringSet(key, next).apply()
        filter()
    }

    private fun organize(entry: AppEntry) {
        if (!resumed) return
        if (!organizing || !CaregiverLock.unlocked(this)) {
            requestChanges { showOrganize(entry) }
        } else showOrganize(entry)
    }

    private fun showOrganize(entry: AppEntry) {
        if (!resumed || isFinishing || isDestroyed) return
        val epoch = authorizationEpoch
        val isFavorite = entry.key in favorites()
        val isHidden = entry.key in hidden()
        val options = arrayOf(label(if (isFavorite) R.string.platform_favorite_remove else R.string.platform_favorite_add),
            label(if (isHidden) R.string.platform_restore_app else R.string.platform_hide_app))
        val choices = object : android.widget.ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                PlatformUi.text(this@AppsActivity, 20f).apply {
                    text = options[position]; minHeight = PlatformUi.dp(context, 64); gravity = Gravity.CENTER_VERTICAL
                    setPadding(PlatformUi.dp(context, 24), PlatformUi.dp(context, 12), PlatformUi.dp(context, 24), PlatformUi.dp(context, 12))
                }
        }
        val dialog = AlertDialog.Builder(this).setTitle(local().getString(R.string.platform_organize_app, entry.label))
            .setAdapter(choices) { _, choice ->
                if (!resumed || epoch != authorizationEpoch) return@setAdapter
                if (choice == 0) updateSet("favorites", entry, !isFavorite, epoch)
                else if (isHidden) updateSet("hidden", entry, false, epoch)
                else showLargeDialog(AlertDialog.Builder(this).setTitle(entry.label).setMessage(label(R.string.platform_hide_explanation))
                    .setNegativeButton(label(R.string.platform_cancel), null)
                    .setPositiveButton(label(R.string.platform_hide_confirm)) { _, _ -> updateSet("hidden", entry, true, epoch) }.create())
            }.setNegativeButton(label(R.string.platform_cancel), null).create()
        showLargeDialog(dialog)
    }

    private fun showLargeDialog(dialog: AlertDialog) {
        if (!resumed || isFinishing || isDestroyed) return
        dialogs.add(dialog)
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.setOnShowListener {
            for (button in listOf(AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL)) {
                dialog.getButton(button)?.apply { minHeight = PlatformUi.dp(context, 64); textSize = 18f; isAllCaps = false }
            }
        }
        dialog.show()
    }

    private fun requestChanges(change: () -> Unit) {
        if (!resumed || isFinishing || isDestroyed) return
        val epoch = authorizationEpoch
        CaregiverLock.request(this) {
            if (resumed && !isFinishing && !isDestroyed && epoch == authorizationEpoch) change()
        }
    }

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount(): Int = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int): Long = shown[position].key.hashCode().toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val entry = shown[position]
            val row = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                minimumHeight = PlatformUi.dp(context, 82)
                setPadding(PlatformUi.dp(context, 16), PlatformUi.dp(context, 12), PlatformUi.dp(context, 16), PlatformUi.dp(context, 12))
                background = PlatformUi.rounded(android.graphics.Color.WHITE, PlatformUi.dp(context, 20).toFloat())
            }
            row.addView(ImageView(this@AppsActivity).apply {
                setImageDrawable(entry.icon); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(PlatformUi.dp(this@AppsActivity, 44), PlatformUi.dp(this@AppsActivity, 44)).apply { marginEnd = PlatformUi.dp(this@AppsActivity, 16) })
            val favorite = entry.key in favorites()
            val hidden = entry.key in hidden()
            val description = listOfNotNull(entry.label, if (favorite) label(R.string.platform_favorite) else null, if (hidden) label(R.string.platform_hidden) else null).joinToString(" · ")
            row.addView(PlatformUi.text(this@AppsActivity, 22f, bold = true).apply {
                text = (if (favorite) "★  " else "") + entry.label + (if (hidden) "\n${label(R.string.platform_hidden)}" else "")
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.contentDescription = if (organizing) local().getString(R.string.platform_organize_app, description) else description
            return row
        }
    }
}
