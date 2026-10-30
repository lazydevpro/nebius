package `in`.family.maa

import android.Manifest
import android.app.Dialog
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Accessible, high-visibility photo gallery with large preview cards and one-tap full screen zoom. */
class GalleryActivity : CommunicationUiActivity() {
    companion object {
        private const val REQUEST_PHOTO_PERMISSION = 301
        private const val MAX_DEVICE_PHOTOS = 50
        private const val EXTRA_NEW_SINCE = "in.family.maa.NEW_PHOTOS_SINCE"
        private const val EXTRA_ANNOUNCEMENT = "in.family.maa.NEW_PHOTOS_ANNOUNCEMENT"
        private const val DOWNLOAD_PATIENCE_MS = 45_000L
        // WhatsApp can finish saving a photo moments before it posts the notification.
        private const val ARRIVAL_MARGIN_SECONDS = 60L
        private val WHATSAPP_BUCKETS = arrayOf("WhatsApp Images", "WhatsApp Business Images")

        /** Shows only photos WhatsApp saved since [since], saying [announcement] aloud as it opens. */
        fun newPhotosIntent(context: Context, since: Long, announcement: String): Intent =
            Intent(context, GalleryActivity::class.java)
                .putExtra(EXTRA_NEW_SINCE, since)
                .putExtra(EXTRA_ANNOUNCEMENT, announcement)
    }

    private val newPhotosSince get() = intent.getLongExtra(EXTRA_NEW_SINCE, 0L)
    private val waitClock = Handler(Looper.getMainLooper())
    private var shownArrivals = emptyList<Uri>()
    private var downloadOverdue = false

    private val giveUpWaiting = Runnable {
        downloadOverdue = true
        if (shownArrivals.isEmpty()) loadGallery()
    }

    private val arrivalWatcher = object : ContentObserver(waitClock) {
        override fun onChange(selfChange: Boolean) {
            if (hasPhotoPermission() && queryArrivedPhotos() != shownArrivals) loadGallery()
        }
    }

    private fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasPhotoPermission(): Boolean =
        checkSelfPermission(photoPermission()) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        surface(tr(if (newPhotosSince > 0L) R.string.gallery_new_title else R.string.gallery_title))
        loadGallery()
        if (newPhotosSince > 0L) waitClock.postDelayed(giveUpWaiting, DOWNLOAD_PATIENCE_MS)
        if (savedInstanceState == null) intent.getStringExtra(EXTRA_ANNOUNCEMENT)?.let(::sayAloud)
    }

    override fun onStart() {
        super.onStart()
        if (newPhotosSince > 0L) contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, arrivalWatcher)
    }

    override fun onResume() {
        super.onResume()
        ProactiveAsha.markPhotosViewed(this)
        if (hasPhotoPermission()) {
            loadGallery()
        }
    }

    override fun onStop() {
        if (newPhotosSince > 0L) contentResolver.unregisterContentObserver(arrivalWatcher)
        super.onStop()
    }

    override fun onDestroy() {
        waitClock.removeCallbacks(giveUpWaiting)
        super.onDestroy()
    }

    private fun loadGallery() {
        content.removeAllViews()

        if (newPhotosSince > 0L) {
            renderArrivals()
            return
        }

        // Top Action: Open System Gallery
        renderOpenSystemGalleryButton()

        val familyPhotos = mutableListOf<Uri>()

        // Configured family photos never change, so they go after anything newly taken
        val prefs = getSharedPreferences("family", MODE_PRIVATE)
        for (i in 1..3) {
            val photoUriStr = prefs.getString("photo$i", "").orEmpty()
            if (photoUriStr.isNotBlank()) {
                runCatching { Uri.parse(photoUriStr) }.getOrNull()?.let { familyPhotos.add(it) }
            }
        }

        if (!hasPhotoPermission()) {
            renderPermissionRequired()
            if (familyPhotos.isNotEmpty()) {
                renderPhotoCards(familyPhotos)
            }
            return
        }

        // Query device pictures, newest first
        val photos = queryDevicePhotos() + familyPhotos

        if (photos.isEmpty()) {
            renderEmptyState()
            return
        }

        renderPhotoCards(photos.distinct())
    }

    private fun renderArrivals() {
        if (!hasPhotoPermission()) {
            renderPermissionRequired()
            return
        }
        shownArrivals = queryArrivedPhotos()
        if (shownArrivals.isEmpty()) renderDownloadState() else renderPhotoCards(shownArrivals)
        action(tr(R.string.gallery_show_all), secondary = true) {
            startActivity(Intent(this, GalleryActivity::class.java))
        }
    }

    private fun queryArrivedPhotos(): List<Uri> {
        val buckets = WHATSAPP_BUCKETS.joinToString(",") { "?" }
        val since = newPhotosSince / 1000 - ARRIVAL_MARGIN_SECONDS
        return queryDevicePhotos(
            "${MediaStore.Images.Media.DATE_ADDED} >= ? AND ${MediaStore.Images.Media.BUCKET_DISPLAY_NAME} IN ($buckets)",
            arrayOf(since.toString()) + WHATSAPP_BUCKETS
        )
    }

    private fun queryDevicePhotos(selection: String? = null, selectionArgs: Array<String>? = null): List<Uri> {
        val list = mutableListOf<Uri>()
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                while (list.size < MAX_DEVICE_PHOTOS && cursor.moveToNext()) {
                    if (idCol >= 0) {
                        val id = cursor.getLong(idCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        list.add(contentUri)
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query error handled safely
        }
        return list
    }

    private fun renderOpenSystemGalleryButton() {
        val btn = Button(this).apply {
            text = "🖼  " + tr(R.string.gallery_open_app)
            textSize = 21f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            minHeight = dp(64)
            minimumHeight = dp(64)
            setTextColor(green)
            background = shape(Color.WHITE, true)
            stateListAnimator = null
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
            setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    type = "image/*"
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                try {
                    startActivity(intent)
                } catch (_: Exception) {
                    Toast.makeText(this@GalleryActivity, tr(R.string.app_unavailable), Toast.LENGTH_SHORT).show()
                }
            }
        }
        content.addView(btn)
    }

    private fun renderPermissionRequired() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(Color.WHITE, true)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }

        val prompt = TextView(this).apply {
            text = tr(R.string.gallery_permission_needed)
            textSize = 20f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        }
        card.addView(prompt)

        val allowButton = Button(this).apply {
            text = tr(R.string.gallery_allow)
            textSize = 21f
            isAllCaps = false
            minHeight = dp(64)
            minimumHeight = dp(64)
            setTextColor(Color.WHITE)
            background = shape(green)
            stateListAnimator = null
            elevation = dp(2).toFloat()
            setOnClickListener {
                requestPermissions(arrayOf(photoPermission()), REQUEST_PHOTO_PERMISSION)
            }
        }
        card.addView(allowButton)
        content.addView(card)
    }

    private fun renderDownloadState() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, true)
            setPadding(dp(24), dp(36), dp(24), dp(36))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }
        card.addView(ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
            setImageDrawable(MaaIcon(this@GalleryActivity, "photo", green))
        })
        card.addView(TextView(this).apply {
            text = tr(if (downloadOverdue) R.string.gallery_not_downloaded else R.string.gallery_downloading)
            textSize = 22f
            setTextColor(ink)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        content.addView(card)
        if (downloadOverdue) action(tr(R.string.gallery_open_whatsapp)) { openWhatsApp() }
    }

    private fun openWhatsApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
            ?: packageManager.getLaunchIntentForPackage("com.whatsapp.w4b")
        try {
            startActivity(launch ?: return)
        } catch (_: Exception) {
            Toast.makeText(this, tr(R.string.app_unavailable), Toast.LENGTH_SHORT).show()
        }
    }

    private fun renderEmptyState() {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = shape(Color.WHITE, true)
            setPadding(dp(24), dp(36), dp(24), dp(36))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) }
        }

        val icon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(16) }
            setImageDrawable(MaaIcon(this@GalleryActivity, "photo", green))
        }
        card.addView(icon)

        val text = TextView(this).apply {
            text = tr(R.string.gallery_empty)
            textSize = 22f
            setTextColor(MaaDesign.muted)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        card.addView(text)
        content.addView(card)
    }

    private fun renderPhotoCards(photos: List<Uri>) {
        photos.forEach { uri ->
            val card = FrameLayout(this).apply {
                background = shape(Color.WHITE, true)
                clipToOutline = true
                elevation = dp(3).toFloat()
                layoutParams = LinearLayout.LayoutParams(-1, dp(260)).apply { bottomMargin = dp(18) }
                isClickable = true
                isFocusable = true
                setOnClickListener { showEnlargedPhoto(uri) }
            }

            val image = ImageView(this).apply {
                layoutParams = FrameLayout.LayoutParams(-1, -1)
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply { setColor(MaaDesign.pale) }
            }
            card.addView(image)
            PhotoThumbnails.load(image, uri, dp(260))
            content.addView(card)
        }
    }

    private fun showEnlargedPhoto(uri: Uri) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.argb(230, 0, 0, 0)))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(-1, -1)
            setPadding(dp(16), dp(24), dp(16), dp(24))
        }

        val imageView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        PhotoThumbnails.load(imageView, uri, dp(600))
        root.addView(imageView)

        val closeButton = Button(this).apply {
            text = "✕  " + tr(R.string.gallery_close)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            isAllCaps = false
            minHeight = dp(64)
            minimumHeight = dp(64)
            setTextColor(Color.WHITE)
            background = shape(green)
            stateListAnimator = null
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) }
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(closeButton)

        dialog.setContentView(root)
        dialog.show()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PHOTO_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadGallery()
            } else {
                Toast.makeText(this, tr(R.string.gallery_permission_needed), Toast.LENGTH_LONG).show()
            }
        }
    }
}
