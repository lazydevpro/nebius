package `in`.family.maa

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
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
            }.getOrNull()
            view.post { if(view.tag==uri.toString() && view.isAttachedToWindow && bitmap!=null) { view.setPadding(0,0,0,0);view.setImageBitmap(bitmap) } }
        }
    }
}
