package `in`.family.maa

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Camera2 capture behind the simple camera screen. All camera work runs on one background thread
 * and Listener calls arrive on the main thread. A camera failure is reported, never thrown.
 */
class MaaCamera(private val context: Context, private val listener: Listener) {
    interface Listener {
        fun onPreviewSize(size: Size)
        fun onShutter()
        fun onPhotoFinished(saved: Boolean)
        fun onRecordingStarted()
        fun onVideoFinished(saved: Boolean)
        fun onCameraFailed()
        fun onScanCaptured(jpeg: ByteArray) {}
    }

    private companion object {
        const val PHOTO_SHAPE = 4f / 3f
        const val VIDEO_SHAPE = 16f / 9f
        const val MAX_STREAM_SIDE = 1920
        const val VIDEO_BIT_RATE = 6_000_000
        const val FOCUS_TIMEOUT_MS = 1200L
        const val JPEG_QUALITY: Byte = 92
    }

    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("maa-camera").apply { start() }
    private val handler = Handler(thread.looper)
    private val sound = MediaActionSound()
    private var wanted = false
    private var busy = false
    private var waitingForFocus = false
    private var photoWaiting = false
    private var scanning = false
    private var hasAutofocus = false
    private var frontFacing = false
    private var sensorDegrees = 90
    private var previewSize = Size(0, 0)
    private var videoPreviewSize = Size(0, 0)
    private var videoSize = Size(0, 0)
    private var texture: SurfaceTexture? = null
    private var previewSurface: Surface? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var videoUri: Uri? = null
    private var videoFile: ParcelFileDescriptor? = null
    @Volatile private var deviceDegrees = 0

    private val orientation = object : OrientationEventListener(context) {
        override fun onOrientationChanged(degrees: Int) {
            if (degrees != ORIENTATION_UNKNOWN) deviceDegrees = degrees
        }
    }
    private val focusTimeout = Runnable { guarded { if (waitingForFocus) capture() } }
    private val focusWatcher = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(current: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            val focus = result.get(CaptureResult.CONTROL_AF_STATE)
            if (waitingForFocus && (focus == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    focus == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED)) guarded { capture() }
        }
    }
    private val deviceCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            if (!wanted) { camera.close(); return }
            device = camera
            guarded { startPreview() }
        }
        override fun onDisconnected(camera: CameraDevice) { camera.close(); if (wanted) fail() }
        override fun onError(camera: CameraDevice, error: Int) { camera.close(); if (wanted) fail() }
    }

    fun start(surface: SurfaceTexture) {
        orientation.enable()
        onCameraThread {
            if (!wanted) { wanted = true; texture = surface; open() }
        }
    }

    fun stop() {
        orientation.disable()
        onCameraThread { wanted = false; release() }
    }

    fun destroy() {
        stop()
        handler.post { sound.release() }
        thread.quitSafely()
    }

    fun takePhoto() = onCameraThread {
        if (busy || recorder != null) return@onCameraThread
        val current = session
        if (current == null) {
            // Still starting up: keep the tap so a quick press is not silently lost.
            photoWaiting = true
            return@onCameraThread
        }
        busy = true
        if (hasAutofocus) {
            waitingForFocus = true
            handler.postDelayed(focusTimeout, FOCUS_TIMEOUT_MS)
            val lockFocus = previewRequest().apply { set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START) }
            current.capture(lockFocus.build(), null, handler)
        } else capture()
    }

    /** Like takePhoto, but the picture goes to onScanCaptured and is never saved to the gallery. */
    fun takeScan() {
        onCameraThread { scanning = true }
        takePhoto()
    }

    fun startVideo(withAudio: Boolean) = onCameraThread {
        val camera = device ?: return@onCameraThread
        if (session == null || busy || recorder != null) return@onCameraThread
        val uri = runCatching { newEntry(video = true) }.getOrNull()
        if (uri == null) { onMain { listener.onVideoFinished(false) }; return@onCameraThread }
        busy = true
        videoUri = uri
        try {
            val file = context.contentResolver.openFileDescriptor(uri, "rw")!!.also { videoFile = it }
            val media = newRecorder(file, withAudio).also { recorder = it }
            session?.close()
            session = null
            val surface = showOn(videoPreviewSize)
            camera.createCaptureSession(listOf(surface, media.surface), sessionCallback { configured ->
                try {
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(surface)
                        addTarget(media.surface)
                        if (hasAutofocus) set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    }
                    configured.setRepeatingRequest(request.build(), null, handler)
                    sound.play(MediaActionSound.START_VIDEO_RECORDING)
                    media.start()
                    busy = false
                    onMain { listener.onRecordingStarted() }
                } catch (_: Exception) { abandonVideo() }
            }, handler)
        } catch (_: Exception) { abandonVideo() }
    }

    fun stopVideo() = onCameraThread {
        if (recorder == null || busy) return@onCameraThread
        finishVideo()
        startPreview()
    }

    @SuppressLint("MissingPermission")
    private fun open() {
        val manager = context.getSystemService(CameraManager::class.java)
        val id = manager.cameraIdList.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.first()
        val info = manager.getCameraCharacteristics(id)
        val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
        frontFacing = info.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        sensorDegrees = info.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
        hasAutofocus = (info.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f) > 0f
        val photoSize = closest(sizes.getOutputSizes(ImageFormat.JPEG), PHOTO_SHAPE)
        previewSize = closest(sizes.getOutputSizes(SurfaceTexture::class.java), PHOTO_SHAPE, MAX_STREAM_SIDE)
        videoPreviewSize = closest(sizes.getOutputSizes(SurfaceTexture::class.java), VIDEO_SHAPE, MAX_STREAM_SIDE)
        videoSize = closest(sizes.getOutputSizes(MediaRecorder::class.java), VIDEO_SHAPE, MAX_STREAM_SIDE)
        reader = ImageReader.newInstance(photoSize.width, photoSize.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ guarded { savePhoto(it) } }, handler)
        }
        manager.openCamera(id, deviceCallback, handler)
    }

    private fun closest(sizes: Array<Size>, shape: Float, maxSide: Int = Int.MAX_VALUE): Size =
        sizes.filter { it.width <= maxSide && it.height <= maxSide }.ifEmpty { sizes.toList() }
            .sortedWith(compareBy<Size> { abs(it.width.toFloat() / it.height - shape) > 0.02f }
                .thenByDescending { it.width.toLong() * it.height })
            .first()

    private fun startPreview() {
        val camera = device ?: return
        val jpeg = reader ?: return
        session?.close()
        session = null
        val surface = showOn(previewSize)
        camera.createCaptureSession(listOf(surface, jpeg.surface), sessionCallback { configured ->
            configured.setRepeatingRequest(previewRequest().build(), focusWatcher, handler)
            if (photoWaiting) {
                photoWaiting = false
                takePhoto()
            }
        }, handler)
    }

    private fun showOn(size: Size): Surface {
        val target = texture!!
        target.setDefaultBufferSize(size.width, size.height)
        onMain { listener.onPreviewSize(size) }
        return previewSurface ?: Surface(target).also { previewSurface = it }
    }

    private fun sessionCallback(ready: (CameraCaptureSession) -> Unit) = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(configured: CameraCaptureSession) {
            if (device == null) configured.close() else guarded { session = configured; ready(configured) }
        }
        override fun onConfigureFailed(configured: CameraCaptureSession) { if (wanted) fail() }
    }

    private fun previewRequest() = device!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
        addTarget(previewSurface!!)
        if (hasAutofocus) set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
    }

    private fun capture() {
        waitingForFocus = false
        handler.removeCallbacks(focusTimeout)
        val request = device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
            addTarget(reader!!.surface)
            if (hasAutofocus) set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.JPEG_ORIENTATION, captureDegrees())
            set(CaptureRequest.JPEG_QUALITY, JPEG_QUALITY)
        }
        session!!.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
            override fun onCaptureFailed(current: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                guarded { finishPhoto(false) }
            }
        }, handler)
        sound.play(MediaActionSound.SHUTTER_CLICK)
        onMain { listener.onShutter() }
    }

    private fun captureDegrees(): Int {
        val rounded = (deviceDegrees + 45) / 90 * 90
        return (sensorDegrees + (if (frontFacing) -rounded else rounded) + 360) % 360
    }

    private fun savePhoto(source: ImageReader) {
        val bytes = source.acquireLatestImage()?.use { image ->
            val buffer = image.planes[0].buffer
            ByteArray(buffer.remaining()).also { buffer.get(it) }
        }
        val forScan = scanning
        scanning = false
        if (forScan && bytes != null) onMain { listener.onScanCaptured(bytes) }
        finishPhoto(bytes != null && (forScan || writePhoto(bytes)))
    }

    private fun writePhoto(bytes: ByteArray): Boolean = runCatching {
        val uri = newEntry(video = false)
        try {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
            publish(uri)
        } catch (e: Exception) {
            discard(uri)
            throw e
        }
    }.isSuccess

    private fun finishPhoto(saved: Boolean) {
        busy = false
        onMain { listener.onPhotoFinished(saved) }
        if (hasAutofocus) session?.let { current ->
            val unlockFocus = previewRequest().apply { set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL) }
            current.capture(unlockFocus.build(), null, handler)
        }
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun newRecorder(file: ParcelFileDescriptor, withAudio: Boolean) =
        (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()).apply {
            if (withAudio) setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(file.fileDescriptor)
            setVideoEncodingBitRate(VIDEO_BIT_RATE)
            setVideoFrameRate(30)
            setVideoSize(videoSize.width, videoSize.height)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128_000)
                setAudioSamplingRate(44_100)
            }
            setOrientationHint(captureDegrees())
            prepare()
        }

    private fun abandonVideo() {
        finishVideo()
        startPreview()
    }

    private fun finishVideo() {
        val uri = videoUri ?: return
        val media = recorder
        videoUri = null
        recorder = null
        busy = false
        runCatching { session?.stopRepeating(); session?.abortCaptures() }
        val saved = media != null && runCatching { media.stop() }.isSuccess
        runCatching { media?.release() }
        runCatching { videoFile?.close() }
        videoFile = null
        runCatching { if (saved) publish(uri) else discard(uri) }
        if (saved) sound.play(MediaActionSound.STOP_VIDEO_RECORDING)
        onMain { listener.onVideoFinished(saved) }
    }

    @Suppress("DEPRECATION")
    private fun newEntry(video: Boolean): Uri {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val name = "Maa_$stamp" + if (video) ".mp4" else ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Maa")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                val folder = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "Maa").apply { mkdirs() }
                put(MediaStore.MediaColumns.DATA, File(folder, name).path)
            }
        }
        val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        return context.contentResolver.insert(collection, values)!!
    }

    private fun publish(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29)
            context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    }

    private fun discard(uri: Uri) {
        context.contentResolver.delete(uri, null, null)
    }

    private fun release() {
        finishVideo()
        handler.removeCallbacks(focusTimeout)
        waitingForFocus = false
        photoWaiting = false
        scanning = false
        busy = false
        session?.close()
        session = null
        device?.close()
        device = null
        reader?.close()
        reader = null
        previewSurface?.release()
        previewSurface = null
    }

    private fun fail() {
        wanted = false
        release()
        onMain { listener.onCameraFailed() }
    }

    private fun guarded(block: () -> Unit) {
        try { block() } catch (_: Exception) { fail() }
    }

    private fun onCameraThread(block: () -> Unit) {
        handler.post { guarded(block) }
    }

    private fun onMain(block: () -> Unit) {
        main.post(block)
    }
}
