package `in`.family.maa

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Centralized Permission Setup & Status Manager.
 * Distinguishes core runtime permissions from optional overlay/notification enhancements.
 */
object PermissionsHelper {
    const val REQUEST_CODE_RUNTIME = 701

    fun missingRuntimePermissions(context: Context): List<String> {
        val list = mutableListOf<String>()
        val required = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        )
        if (Build.VERSION.SDK_INT >= 33) {
            required.add(Manifest.permission.POST_NOTIFICATIONS)
            required.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            required.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        for (perm in required) {
            if (context.checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                list.add(perm)
            }
        }
        return list
    }

    fun hasAllRuntimePermissions(context: Context): Boolean =
        missingRuntimePermissions(context).isEmpty()

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(context.packageName)
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 31) {
            val am = context.getSystemService(AlarmManager::class.java)
            am?.canScheduleExactAlarms() ?: true
        } else true
    }

    /**
     * Only true if critical core permissions for voice, camera, calling & messages are missing.
     * Optional features (overlay, notification listener) do not trigger alarmist warnings.
     */
    fun hasAnyMissingPermissions(context: Context): Boolean =
        !hasAllRuntimePermissions(context)

    fun requestRuntimePermissions(activity: Activity, requestCode: Int = REQUEST_CODE_RUNTIME) {
        val missing = missingRuntimePermissions(activity)
        if (missing.isNotEmpty()) {
            activity.requestPermissions(missing.toTypedArray(), requestCode)
        }
    }

    fun openOverlaySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
        }
    }

    fun openNotificationListenerSettings(context: Context) {
        try {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= 31) {
            try {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
            } catch (_: Exception) {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
            context.startActivity(intent)
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    /**
     * Dynamically refreshing permission dialog.
     * Automatically updates row statuses whenever the user grants a permission or returns from settings.
     */
    fun showPermissionSetupDialog(activity: Activity, onDismiss: () -> Unit = {}) {
        val isHi = AppLanguage.isHindi(activity)
        val dp = { n: Int -> (n * activity.resources.displayMetrics.density).toInt() }
        val handler = Handler(Looper.getMainLooper())

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(20))
            setBackgroundColor(Color.rgb(250, 249, 246))
        }

        fun populateContent() {
            root.removeAllViews()

            val runtimeGranted = hasAllRuntimePermissions(activity)
            val overlayGranted = canDrawOverlays(activity)
            val notifListenerGranted = isNotificationListenerEnabled(activity)
            val exactAlarmGranted = canScheduleExactAlarms(activity)

            // Header Banner
            val banner = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(if (runtimeGranted) Color.rgb(236, 253, 245) else Color.rgb(255, 251, 235))
                    cornerRadius = dp(16).toFloat()
                    setStroke(dp(1), if (runtimeGranted) Color.rgb(167, 243, 208) else Color.rgb(245, 158, 11))
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) }
            }

            val bannerIcon = ImageView(activity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(32), dp(32)).apply { marginEnd = dp(10) }
                setImageDrawable(MaaIcon(activity, if (runtimeGranted) "pill" else "bell", if (runtimeGranted) Color.rgb(5, 150, 105) else Color.rgb(217, 119, 6)))
            }
            banner.addView(bannerIcon)

            val bannerTextCol = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val bannerTitle = TextView(activity).apply {
                text = if (runtimeGranted) (if (isHi) "✓ सभी मुख्य अनुमतियाँ स्वीकृत हैं!" else "✓ All Core Permissions Granted!")
                       else (if (isHi) "⚠️ कुछ अनुमतियाँ अभी बाकी हैं" else "⚠️ Permissions Action Needed")
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(if (runtimeGranted) Color.rgb(6, 95, 70) else Color.rgb(146, 64, 14))
            }
            val bannerSub = TextView(activity).apply {
                text = if (runtimeGranted) (if (isHi) "आवाज़, कैमरा, सीधा कॉल, मैसेज और दवा रिमाइंडर पूरी तरह तैयार हैं।" else "Voice, Camera, Direct Calling, SMS & Alarms are ready.")
                       else (if (isHi) "नीचे दिए गए बटन दबाकर अनुमतियाँ चालू करें।" else "Tap below to allow required features.")
                textSize = 13f
                setTextColor(if (runtimeGranted) Color.rgb(4, 120, 87) else Color.rgb(180, 83, 9))
            }
            bannerTextCol.addView(bannerTitle)
            bannerTextCol.addView(bannerSub)
            banner.addView(bannerTextCol)
            root.addView(banner)

            fun createPermRow(name: String, desc: String, granted: Boolean, isOptional: Boolean, actionLabel: String, onAction: () -> Unit): LinearLayout {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        setColor(Color.WHITE)
                        cornerRadius = dp(16).toFloat()
                        setStroke(dp(1), if (granted) Color.rgb(209, 250, 229) else Color.rgb(229, 231, 235))
                    }
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
                }

                val icon = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(26), dp(26)).apply { marginEnd = dp(10) }
                    setImageDrawable(MaaIcon(activity, if (granted) "pill" else "bell", if (granted) Color.rgb(5, 150, 105) else Color.rgb(156, 163, 175)))
                }
                row.addView(icon)

                val labelCol = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                }
                val label = TextView(activity).apply {
                    text = name + if (isOptional) (if (isHi) " (वैकल्पिक)" else " (Optional)") else ""
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Color.rgb(31, 41, 55))
                }
                val sub = TextView(activity).apply {
                    text = desc
                    textSize = 12f
                    setTextColor(Color.rgb(107, 114, 128))
                }
                labelCol.addView(label)
                labelCol.addView(sub)
                row.addView(labelCol)

                val statusBtn = Button(activity).apply {
                    text = if (granted) (if (isHi) "चालू है ✓" else "Allowed ✓") else actionLabel
                    textSize = 14f
                    setTypeface(typeface, Typeface.BOLD)
                    isAllCaps = false
                    setTextColor(if (granted) Color.rgb(5, 150, 105) else Color.WHITE)
                    background = GradientDrawable().apply {
                        setColor(if (granted) Color.rgb(236, 253, 245) else Color.rgb(15, 119, 113))
                        cornerRadius = dp(14).toFloat()
                    }
                    minHeight = dp(38); minimumHeight = dp(38)
                    setPadding(dp(12), 0, dp(12), 0)
                    isEnabled = !granted
                    if (!granted) setOnClickListener { onAction() }
                }
                row.addView(statusBtn)
                return row
            }

            // 1. Core Runtime Permissions
            root.addView(createPermRow(
                name = if (isHi) "माइक, कैमरा, कॉल और मैसेज" else "Mic, Camera, Calls & SMS",
                desc = if (isHi) "आशा से बात करने और परिवार को कॉल लगाने के लिए" else "For Asha voice, camera & direct phone calls",
                granted = runtimeGranted,
                isOptional = false,
                actionLabel = if (isHi) "चालू करें" else "Allow"
            ) { requestRuntimePermissions(activity) })

            // 2. Exact Alarm
            root.addView(createPermRow(
                name = if (isHi) "सटीक दवा रिमाइंडर" else "Exact Medicine Alarms",
                desc = if (isHi) "फोन बंद/स्लीप में भी समय पर अलार्म बजाने के लिए" else "Wakes phone on time even during deep sleep",
                granted = exactAlarmGranted,
                isOptional = false,
                actionLabel = if (isHi) "चालू करें" else "Allow"
            ) { openExactAlarmSettings(activity) })

            // Section: Optional Features
            val optHeader = TextView(activity).apply {
                text = if (isHi) "अतिरिक्त सुविधाएँ (ज़रूरी नहीं):" else "Optional Enhancements (Not Required):"
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Color.rgb(107, 114, 128))
                setPadding(dp(2), dp(8), 0, dp(4))
            }
            root.addView(optHeader)

            // 3. Draw Over Other Apps
            root.addView(createPermRow(
                name = if (isHi) "व्हाट्सऐप के ऊपर तैरता बटन" else "Floating Screen Helper",
                desc = if (isHi) "व्हाट्सऐप पर फोटो देखते समय आशा को ऊपर दिखाने के लिए" else "Draws floating helper over WhatsApp",
                granted = overlayGranted,
                isOptional = true,
                actionLabel = if (isHi) "चालू करें" else "Allow"
            ) { openOverlaySettings(activity) })

            // 4. Notification Listener
            root.addView(createPermRow(
                name = if (isHi) "व्हाट्सऐप सूचनाएँ पढ़ना" else "Notification Reader",
                desc = if (isHi) "नए फोटो या मैसेज आने पर बोलकर बताने के लिए" else "Reads incoming notifications aloud to Mom",
                granted = notifListenerGranted,
                isOptional = true,
                actionLabel = if (isHi) "चालू करें" else "Allow"
            ) { openNotificationListenerSettings(activity) })

            // Button: "Grant All Core Permissions" (only if runtime missing)
            if (!runtimeGranted) {
                val grantAllBtn = Button(activity).apply {
                    text = if (isHi) "सभी मुख्य अनुमतियाँ चालू करें" else "Grant All Core Permissions"
                    textSize = 18f
                    setTypeface(typeface, Typeface.BOLD)
                    isAllCaps = false
                    setTextColor(Color.WHITE)
                    background = MaaDesign.shape(activity, Color.rgb(15, 119, 113), 18)
                    minHeight = dp(52); minimumHeight = dp(52)
                    layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10); bottomMargin = dp(6) }
                    setOnClickListener { requestRuntimePermissions(activity) }
                }
                root.addView(grantAllBtn)
            }

            // Direct link to System App Settings (crucial for MIUI / Xiaomi)
            val appSettingsBtn = Button(activity).apply {
                text = "⚙ " + if (isHi) "फोन की पूरी ऐप सेटिंग खोलें (System App Info)" else "Open System App Settings"
                textSize = 14f
                setTypeface(typeface, Typeface.NORMAL)
                isAllCaps = false
                setTextColor(Color.rgb(75, 85, 99))
                background = MaaDesign.shape(activity, Color.rgb(243, 244, 246), 14)
                minHeight = dp(42); minimumHeight = dp(42)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6); bottomMargin = dp(4) }
                setOnClickListener { openAppSettings(activity) }
            }
            root.addView(appSettingsBtn)
        }

        populateContent()

        val dialog = AlertDialog.Builder(activity)
            .setView(ScrollView(activity).apply { addView(root) })
            .setPositiveButton(if (isHi) "पूर्ण / Done" else "Done") { d, _ ->
                d.dismiss()
                onDismiss()
            }
            .create()

        // Auto-refresh runnable that runs every 800ms while dialog is visible
        val refreshRunnable = object : Runnable {
            override fun run() {
                if (dialog.isShowing && !activity.isFinishing && !activity.isDestroyed) {
                    populateContent()
                    handler.postDelayed(this, 1200L)
                }
            }
        }

        dialog.setOnShowListener {
            handler.postDelayed(refreshRunnable, 1200L)
        }
        dialog.setOnDismissListener {
            handler.removeCallbacks(refreshRunnable)
            onDismiss()
        }

        dialog.show()
    }
}
