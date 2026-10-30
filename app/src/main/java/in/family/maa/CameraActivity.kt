package `in`.family.maa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.view.Gravity
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * Accessible camera designed for elderly users, built into Maa so no other camera app opens.
 * A live view and two giant buttons: one tap takes a photo, one tap starts a video.
 * Everything saves to the phone's gallery automatically, with zero confusing menus.
 */
open class CameraActivity : CommunicationUiActivity(), MaaCamera.Listener {
    private companion object {
        const val REQUEST_ACCESS = 501
        val PHOTO_FILL = Color.rgb(230, 244, 241)
        val PHOTO_ACCENT = Color.rgb(15, 119, 113)
        val VIDEO_FILL = Color.rgb(252, 231, 243)
        val VIDEO_ACCENT = Color.rgb(190, 24, 93)
        val STOP_RED = Color.rgb(220, 38, 38)
    }

    protected lateinit var camera: MaaCamera
    protected var cameraWanted = true
    private lateinit var viewfinder: FrameLayout
    private lateinit var preview: TextureView
    private lateinit var flash: View
    private lateinit var status: TextView
    private lateinit var permissionPrompt: LinearLayout
    private lateinit var photoCard: LinearLayout
    private lateinit var videoCard: LinearLayout
    private val clock = Handler(Looper.getMainLooper())
    private val hideStatus = Runnable { status.visibility = View.GONE }
    private var onScreen = false
    private var recording = false
    private var recordingSince = 0L
    private var cameraFailed = false
    private var askedForAccess = false

    private val recordingClock = object : Runnable {
        override fun run() {
            val seconds = (SystemClock.elapsedRealtime() - recordingSince) / 1000
            showStatus(tr(R.string.camera_recording, String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)), sticky = true)
            clock.postDelayed(this, 1000)
        }
    }

    private val previewListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
            if (onScreen && hasPermission(Manifest.permission.CAMERA)) camera.start(texture)
        }
        override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
        override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
            camera.stop()
            return true
        }
        override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        camera = MaaCamera(this, this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        surface(screenTitle())
        buildCameraUi()
    }

    protected open fun screenTitle() = tr(R.string.camera_title)

    override fun onResume() {
        super.onResume()
        onScreen = true
        val allowed = hasPermission(Manifest.permission.CAMERA)
        if (allowed && cameraWanted) preview.surfaceTexture?.let { camera.start(it) }
        val missing = wantedPermissions().filterNot(::hasPermission)
        if (!askedForAccess && missing.isNotEmpty()) {
            askedForAccess = true
            requestPermissions(missing.toTypedArray(), REQUEST_ACCESS)
        } else permissionPrompt.visibility = if (allowed) View.GONE else View.VISIBLE
    }

    override fun onPause() {
        onScreen = false
        camera.stop()
        super.onPause()
    }

    override fun onDestroy() {
        clock.removeCallbacksAndMessages(null)
        camera.destroy()
        super.onDestroy()
    }

    protected open fun wantedPermissions(): List<String> {
        val permissions = mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT < 29) permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        return permissions
    }

    private fun hasPermission(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    protected fun buildCameraUi() {
        content.removeAllViews()

        // 1. Live view, shaped exactly like the photo that will be saved
        viewfinder = FrameLayout(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(28).toFloat()
            }
            clipToOutline = true
            minimumHeight = dp(220)
        }
        preview = TextureView(this).apply { surfaceTextureListener = previewListener }
        viewfinder.addView(preview, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        flash = View(this).apply {
            setBackgroundColor(Color.WHITE)
            alpha = 0f
        }
        viewfinder.addView(flash, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(10), dp(20), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.argb(190, 0, 0, 0))
                cornerRadius = dp(22).toFloat()
            }
            visibility = View.GONE
        }
        viewfinder.addView(status, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply {
            bottomMargin = dp(16)
            marginStart = dp(12)
            marginEnd = dp(12)
        })
        permissionPrompt = buildPermissionPrompt()
        viewfinder.addView(permissionPrompt, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
        content.addView(viewfinder, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(14) })
        buildControls()
    }

    protected open fun buildControls() {
        // 2. Take Photo and Record Video, side by side
        photoCard = createOptionCard(tr(R.string.camera_photo), "camera", PHOTO_FILL, PHOTO_ACCENT) {
            camera.takePhoto()
        }
        videoCard = createOptionCard(tr(R.string.camera_video), "video", VIDEO_FILL, VIDEO_ACCENT) {
            if (recording) camera.stopVideo() else camera.startVideo(hasPermission(Manifest.permission.RECORD_AUDIO))
        }
        val choices = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        choices.addView(photoCard, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        choices.addView(videoCard, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(choices, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })

        // 3. View Gallery and Scan Medicine, side by side
        val photos = action("🖼️  " + tr(R.string.home_gallery), secondary = true) {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
        val scan = action("💊 " + tr(R.string.scan_title), secondary = true) {
            startActivity(Intent(this, MedicineScanActivity::class.java))
        }
        val more = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
        }
        content.removeView(photos)
        content.removeView(scan)
        more.addView(photos, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(12) })
        more.addView(scan, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(more)
    }

    private fun buildPermissionPrompt() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(20), dp(20), dp(20), dp(20))
        visibility = View.GONE
        addView(TextView(this@CameraActivity).apply {
            text = tr(R.string.camera_permission_needed)
            textSize = 20f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(14))
        })
        addView(Button(this@CameraActivity).apply {
            text = tr(R.string.camera_allow)
            textSize = 21f
            isAllCaps = false
            minimumHeight = dp(64)
            setTextColor(Color.WHITE)
            background = shape(green)
            stateListAnimator = null
            setOnClickListener { askForCameraAgain() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun askForCameraAgain() {
        if (shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_ACCESS)
        } else {
            // Android stops showing the prompt after repeated denials, so Settings is the only way left.
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))) }
        }
    }

    protected fun showStatus(message: String, sticky: Boolean = false) {
        clock.removeCallbacks(hideStatus)
        status.text = message
        status.visibility = View.VISIBLE
        if (!sticky) clock.postDelayed(hideStatus, 2500)
    }

    private fun showVideoCardAsStop(stop: Boolean) {
        val background = videoCard.background as GradientDrawable
        background.setColor(if (stop) STOP_RED else VIDEO_FILL)
        background.setStroke(dp(3), if (stop) STOP_RED else VIDEO_ACCENT)
        val foreground = if (stop) Color.WHITE else VIDEO_ACCENT
        (videoCard.getChildAt(0) as ImageView).setImageDrawable(MaaIcon(this, "video", foreground))
        (videoCard.getChildAt(1) as TextView).apply {
            text = tr(if (stop) R.string.camera_stop else R.string.camera_video)
            setTextColor(foreground)
        }
    }

    override fun onPreviewSize(size: Size) {
        if (cameraFailed) {
            cameraFailed = false
            status.visibility = View.GONE
        }
        val shape = minOf(size.width, size.height).toFloat() / maxOf(size.width, size.height)
        val width = minOf(viewfinder.width.toFloat(), viewfinder.height * shape)
        preview.layoutParams = FrameLayout.LayoutParams(width.toInt(), (width / shape).toInt(), Gravity.CENTER)
    }

    override fun onShutter() {
        flash.animate().cancel()
        flash.alpha = 0.85f
        flash.animate().alpha(0f).setDuration(250).start()
    }

    override fun onPhotoFinished(saved: Boolean) {
        showStatus(tr(if (saved) R.string.camera_saved_photo else R.string.camera_not_saved))
    }

    override fun onRecordingStarted() {
        recording = true
        recordingSince = SystemClock.elapsedRealtime()
        photoCard.visibility = View.GONE
        showVideoCardAsStop(true)
        recordingClock.run()
    }

    override fun onVideoFinished(saved: Boolean) {
        recording = false
        clock.removeCallbacks(recordingClock)
        photoCard.visibility = View.VISIBLE
        showVideoCardAsStop(false)
        showStatus(tr(if (saved) R.string.camera_saved_video else R.string.camera_not_saved))
    }

    override fun onCameraFailed() {
        cameraFailed = true
        showStatus(tr(R.string.camera_unavailable), sticky = true)
    }

    protected fun createOptionCard(
        title: String,
        symbol: String,
        bgColor: Int,
        accentColor: Int,
        onClick: () -> Unit
    ): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumHeight = dp(128)
            isClickable = true
            isFocusable = true
            setPadding(dp(10), dp(14), dp(10), dp(14))
            background = GradientDrawable().apply {
                setColor(bgColor)
                cornerRadius = dp(28).toFloat()
                setStroke(dp(3), accentColor)
            }
            elevation = dp(4).toFloat()

            val icon = ImageView(this@CameraActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56))
                setImageDrawable(MaaIcon(this@CameraActivity, symbol, accentColor))
            }
            addView(icon)

            val text = TextView(this@CameraActivity).apply {
                this.text = title
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(accentColor)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
            }
            addView(text)

            setOnClickListener { onClick() }
        }
    }
}
