package `in`.family.maa

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Finishes a video call Mom asked for out loud. WhatsApp has no link that starts a video call, so
 * this presses Call, Video call and WhatsApp's confirmation in the chat Asha opened. It stays idle unless armed
 * by that request, and only for a few seconds, so it can never act while someone is using WhatsApp.
 */
object VideoCallAssist {
    private const val ARMED_MS = 15_000L
    private const val MAX_NODES = 600
    private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
    private val CALL_LABELS = setOf("call", "कॉल")
    private val VIDEO_LABELS = setOf("video call", "वीडियो कॉल")

    private enum class Step { CALL_BUTTON, VIDEO_OPTION, CONFIRM }

    @Volatile private var armedUntil = 0L
    private var step = Step.CALL_BUTTON

    /** True when Maa's accessibility service is switched on in Android settings. */
    fun ready(context: Context): Boolean =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().contains("${context.packageName}/")

    fun arm() {
        step = Step.CALL_BUTTON
        armedUntil = SystemClock.elapsedRealtime() + ARMED_MS
    }

    fun isArmed() = SystemClock.elapsedRealtime() <= armedUntil

    fun onWindowChanged(root: AccessibilityNodeInfo?, screenHeight: Int) {
        if (root == null || root.packageName?.toString() !in WHATSAPP) return
        when (step) {
            // Call must come first: old "Video call" bubbles in the chat are not the menu option.
            Step.CALL_BUTTON -> if (pressFirst(root) { isCallButton(it, screenHeight) }) step = Step.VIDEO_OPTION
            Step.VIDEO_OPTION -> if (pressFirst(root) { isVideoOption(it) }) step = Step.CONFIRM
            Step.CONFIRM -> if (asksToStartVideoCall(root) && pressFirst(root) { isConfirmButton(it) }) armedUntil = 0L
        }
    }

    private fun pressFirst(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): Boolean =
        find(root, match)?.let { press(it) } == true

    /** WhatsApp's "Start video call?" box. Checking the wording keeps this off its voice call prompt. */
    private fun asksToStartVideoCall(root: AccessibilityNodeInfo) = find(root) {
        it.viewIdResourceName.orEmpty().endsWith(":id/message") &&
            (label(it).contains("video") || label(it).contains("वीडियो"))
    } != null

    private fun isConfirmButton(node: AccessibilityNodeInfo) =
        node.viewIdResourceName.orEmpty().endsWith(":id/button1") && label(node) in CALL_LABELS && node.isClickable

    private fun label(node: AccessibilityNodeInfo) =
        (node.contentDescription ?: node.text)?.toString()?.trim()?.lowercase().orEmpty()

    /** Only the button in the top bar: a message that says "call" sits lower and is not this control. */
    private fun isCallButton(node: AccessibilityNodeInfo, screenHeight: Int): Boolean {
        if (label(node) !in CALL_LABELS || !node.isClickable || !node.isVisibleToUser) return false
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        return bounds.top < screenHeight / 5
    }

    private fun isVideoOption(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName.orEmpty()
        return label(node) in VIDEO_LABELS && node.isVisibleToUser &&
            !id.contains("bubble") && !id.contains("call_log")
    }

    private fun press(node: AccessibilityNodeInfo): Boolean {
        var target: AccessibilityNodeInfo? = node
        while (target != null && !target.isClickable) target = target.parent
        return target?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun find(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        var visited = 0
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (++visited > MAX_NODES) return null
            if (match(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { child -> visit(child)?.let { return it } }
            return null
        }
        return visit(root)
    }
}
