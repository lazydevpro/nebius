package `in`.family.maa

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager

object DirectSmsDispatcher {
    const val ACTION_SMS_SENT = "in.family.maa.SMS_SENT_STATUS"
    const val EXTRA_RECIPIENT = "extra_recipient"

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun sendSmsWithCallback(
        context: Context,
        recipientNumber: String,
        messageText: String,
        onComplete: (success: Boolean, errorMessage: String?) -> Unit
    ) {
        val cleanNumber = CommunicationIntents.sanitizeNumber(recipientNumber)
        if (cleanNumber.isBlank()) {
            val isHi = AppLanguage.isHindi(context)
            onComplete(false, if (isHi) "अमान्य फोन नंबर" else "Invalid recipient number")
            return
        }

        if (context.checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val isHi = AppLanguage.isHindi(context)
            onComplete(false, if (isHi) "एसएमएस भेजने की अनुमति नहीं है" else "Permission SEND_SMS not granted")
            return
        }

        if (CommunicationIntents.isAirplaneModeOn(context)) {
            val isHi = AppLanguage.isHindi(context)
            onComplete(false, if (isHi) "हवाई जहाज़ मोड चालू है। कृपया इसे बंद करें।" else "Airplane mode is active. Please turn it off.")
            return
        }

        val smsManager = try {
            if (Build.VERSION.SDK_INT >= 31) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        } catch (_: Exception) { null }

        if (smsManager == null) {
            val isHi = AppLanguage.isHindi(context)
            onComplete(false, if (isHi) "एसएमएस सेवा उपलब्ध नहीं है" else "SMS service unavailable")
            return
        }

        val sentIntent = PendingIntent.getBroadcast(
            context,
            cleanNumber.hashCode(),
            Intent(ACTION_SMS_SENT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_RECIPIENT, cleanNumber)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        var receiverUnregistered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (!receiverUnregistered) {
                    receiverUnregistered = true
                    try { context.unregisterReceiver(this) } catch (_: Exception) {}
                }
                val isHi = AppLanguage.isHindi(context)
                when (resultCode) {
                    Activity.RESULT_OK -> onComplete(true, null)
                    SmsManager.RESULT_ERROR_NO_SERVICE -> onComplete(false, if (isHi) "मोबाइल नेटवर्क उपलब्ध नहीं है।" else "No mobile service.")
                    SmsManager.RESULT_ERROR_RADIO_OFF -> onComplete(false, if (isHi) "हवाई जहाज़ मोड बंद करें।" else "Radio is OFF.")
                    else -> onComplete(false, if (isHi) "संदेश भेजा नहीं जा सका।" else "Failed to send SMS.")
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                context.registerReceiver(receiver, IntentFilter(ACTION_SMS_SENT), Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, IntentFilter(ACTION_SMS_SENT))
            }
        } catch (_: Exception) {
            // Fallback if receiver registration fails
        }

        try {
            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                val sentIntents = ArrayList<PendingIntent>().apply { repeat(parts.size) { add(sentIntent) } }
                smsManager.sendMultipartTextMessage(cleanNumber, null, parts, sentIntents, null)
            } else {
                smsManager.sendTextMessage(cleanNumber, null, messageText, sentIntent, null)
            }
        } catch (e: Exception) {
            if (!receiverUnregistered) {
                receiverUnregistered = true
                try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
            }
            onComplete(false, e.localizedMessage)
        }
    }
}
