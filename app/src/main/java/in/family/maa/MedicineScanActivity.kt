package `in`.family.maa

import android.Manifest
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * Point the camera at a medicine and tap once. Asha says what it is, and when to take it if the
 * family has saved it. The photo goes to Nebius to be read and is never saved on the phone.
 */
class MedicineScanActivity : CameraActivity() {
    private companion object {
        val TEAL = Color.rgb(15, 119, 113)
        val PALE_TEAL = Color.rgb(230, 244, 241)
        val RED = Color.rgb(185, 28, 28)
        val PALE_RED = Color.rgb(254, 226, 226)
        val PALE_YELLOW = Color.rgb(254, 249, 195)
    }

    private val worker = Executors.newSingleThreadExecutor()
    private var reading = false

    override fun screenTitle() = tr(R.string.scan_title)
    override fun wantedPermissions() = listOf(Manifest.permission.CAMERA)

    override fun buildControls() {
        content.addView(TextView(this).apply {
            text = tr(R.string.scan_hint)
            textSize = 19f
            setTextColor(ink)
            gravity = Gravity.CENTER
            setPadding(dp(4), 0, dp(4), dp(12))
        })
        val card = createOptionCard(tr(R.string.scan_button), "pill", PALE_TEAL, TEAL) { scan() }
        content.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
    }

    private fun scan() {
        if (reading) return
        reading = true
        showStatus(tr(R.string.scan_reading), sticky = true)
        camera.takeScan()
    }

    override fun onPhotoFinished(saved: Boolean) {
        if (!saved) tryAgain(tr(R.string.scan_unreadable))
    }

    override fun onScanCaptured(jpeg: ByteArray) {
        worker.execute {
            val outcome = runCatching { MedicineScanner.read(this, jpeg) }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                val result = outcome.getOrNull()
                when {
                    outcome.isFailure -> tryAgain(tr(R.string.scan_no_internet))
                    result == null -> tryAgain(tr(R.string.scan_unreadable))
                    else -> showResult(result)
                }
            }
        }
    }

    private fun tryAgain(message: String) {
        reading = false
        showStatus(message)
        sayAloud(message)
    }

    private fun showResult(result: MedicineScanner.Reading) {
        reading = false
        cameraWanted = false
        camera.stop()
        content.removeAllViews()

        content.addView(label(result.title, 30f).apply { setTypeface(typeface, Typeface.BOLD) })
        if (result.usedFor.isNotBlank()) content.addView(label(tr(R.string.scan_used_for, result.usedFor), 21f))

        val advice = if (result.expired) tr(R.string.scan_expired, result.expiry) else result.whenToTake(this)
        val color = when {
            result.expired -> PALE_RED
            result.saved != null -> PALE_TEAL
            else -> PALE_YELLOW
        }
        content.addView(TextView(this).apply {
            text = advice
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (result.expired) RED else ink)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(22).toFloat()
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        content.addView(label(tr(R.string.scan_check), 17f))

        action(tr(R.string.scan_hear_again), secondary = true) { sayAloud(result.spoken(this)) }
        if (result.saved == null && !result.expired) {
            action(tr(R.string.scan_add), secondary = true) {
                MedicineManager.addFromScan(this, result.title) { showResult(rematched(result)) }
            }
        }
        action(tr(R.string.scan_again)) { startOver() }

        sayAloud(result.spoken(this))
    }

    /** The family just saved this medicine, so its new time can be shown straight away. */
    private fun rematched(result: MedicineScanner.Reading) =
        result.copy(saved = MedicineManager.all(this).firstOrNull { it.nameEn == result.title })

    private fun startOver() {
        cameraWanted = true
        buildCameraUi()
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }
}
