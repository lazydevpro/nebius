package `in`.family.maa

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.ImageView
import java.io.InputStream
import java.util.concurrent.Executors

/** Bounded sampled decoding off the Home UI thread. */
object PhotoThumbnails {
    private val worker=Executors.newFixedThreadPool(2)
    fun load(view:ImageView,uri:Uri,target:Int) {
        view.tag=uri.toString()
        val resolver=view.context.applicationContext.contentResolver
        worker.execute {
            val bitmap=runCatching {
                val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,bounds) }
                if(bounds.outWidth<=0 || bounds.outHeight<=0) return@runCatching null
                var sample=1
                while(bounds.outWidth/sample>target*2 || bounds.outHeight/sample>target*2) sample*=2
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply { inSampleSize=sample }) }
                    ?.let { upright(it,rotationOf(resolver,uri)) }
            }.getOrNull()
            view.post { if(view.tag==uri.toString() && view.isAttachedToWindow && bitmap!=null) { view.setPadding(0,0,0,0);view.setImageBitmap(bitmap) } }
        }
    }
    /** Cameras often save sideways pixels plus a rotation tag, and BitmapFactory ignores that tag. */
    private fun rotationOf(resolver:ContentResolver,uri:Uri)=runCatching {
        resolver.openInputStream(uri)?.use { exifDegrees(it) } ?: 0
    }.getOrDefault(0)
    fun exifDegrees(input:InputStream)=when(ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90->90
        ExifInterface.ORIENTATION_ROTATE_180->180
        ExifInterface.ORIENTATION_ROTATE_270->270
        else->0
    }
    fun upright(bitmap:Bitmap,degrees:Int)=
        if(degrees==0) bitmap else Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(degrees.toFloat()) },true)
}
