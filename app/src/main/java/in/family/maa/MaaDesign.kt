package `in`.family.maa

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.view.Gravity
import android.widget.*

/** Original native UI primitives. No generated pixels are used as interactive controls. */
object MaaDesign {
    val canvas = Color.rgb(246,244,236)
    val ink = Color.rgb(23,46,39)
    val green = Color.rgb(18,92,72)
    val muted = Color.rgb(78,98,88)
    val border = Color.rgb(220,227,215)
    val pale = Color.rgb(230,238,224)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun shape(context: Context, color: Int, radius: Int = 24, outline: Boolean = false) = GradientDrawable().apply {
        setColor(color); cornerRadius=dp(context,radius).toFloat()
        if(outline) setStroke(dp(context,1),border)
    }
    fun action(context: Context, label: String, primary: Boolean = false, click: () -> Unit) = Button(context).apply {
        text=label; isAllCaps=false; textSize=20f; setTextColor(if(primary) Color.WHITE else ink)
        minHeight=dp(context,64); minimumHeight=dp(context,64)
        setPadding(dp(context,16),dp(context,12),dp(context,16),dp(context,12))
        background=RippleDrawable(ColorStateList.valueOf(0x22000000),shape(context,if(primary) green else Color.WHITE,20,true),null)
        setOnClickListener { click() }
    }
    fun tile(context: Context, label: String, symbol: String, tone: Int, click: () -> Unit) = action(context,label,false,click).apply {
        textSize=21f; minHeight=dp(context,88); minimumHeight=dp(context,88)
        setTypeface(typeface,Typeface.BOLD)
        background=RippleDrawable(ColorStateList.valueOf(0x22000000),shape(context,tone,24,true),null)
        val accent=when(symbol) { "message"->0xff0c5e9d.toInt();"photo"->0xff634ab1.toInt();"apps"->0xff9a4f00.toInt();else->green }
        setPadding(dp(context,10),dp(context,10),dp(context,10),dp(context,10))
        setCompoundDrawablesWithIntrinsicBounds(MaaIconBadge(context,symbol,accent),null,null,null)
        compoundDrawablePadding=dp(context,8); gravity=Gravity.CENTER_VERTICAL or Gravity.START
    }
}

private class MaaIconBadge(private val context:Context,symbol:String,private val accent:Int):Drawable() {
    private val icon=MaaIcon(context,symbol,accent)
    override fun getIntrinsicWidth()=MaaDesign.dp(context,44)
    override fun getIntrinsicHeight()=MaaDesign.dp(context,44)
    override fun draw(canvas:Canvas) {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=accent;alpha=20 }
        canvas.drawCircle(bounds.exactCenterX(),bounds.exactCenterY(),bounds.width()/2f,paint)
        val inset=bounds.width()/5
        icon.setBounds(bounds.left+inset,bounds.top+inset,bounds.right-inset,bounds.bottom-inset);icon.draw(canvas)
    }
    override fun setAlpha(alpha:Int) { icon.alpha=alpha }
    override fun setColorFilter(filter:ColorFilter?) { icon.colorFilter=filter }
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}

class MaaIcon(private val context: Context, private val symbol: String, color: Int) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=1.8f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun getIntrinsicWidth()=MaaDesign.dp(context,32)
    override fun getIntrinsicHeight()=MaaDesign.dp(context,32)
    override fun draw(canvas: Canvas) {
        val saved=canvas.save(); canvas.translate(bounds.left.toFloat(),bounds.top.toFloat()); canvas.scale(bounds.width()/24f,bounds.height()/24f)
        fun line(x1:Float,y1:Float,x2:Float,y2:Float)=canvas.drawLine(x1,y1,x2,y2,paint)
        fun rect(l:Float,t:Float,r:Float,b:Float)=canvas.drawRoundRect(l,t,r,b,3f,3f,paint)
        when(symbol) {
            "phone" -> { val p=Path(); p.moveTo(5f,3f);p.lineTo(9f,3f);p.lineTo(10f,8f);p.lineTo(7f,10f);p.quadTo(9f,15f,14f,17f);p.lineTo(16f,14f);p.lineTo(21f,15f);p.lineTo(21f,19f);p.quadTo(21f,22f,17f,21f);p.quadTo(4f,18f,3f,7f);p.quadTo(2f,3f,5f,3f);canvas.drawPath(p,paint) }
            "message" -> { rect(3f,3f,21f,18f);line(7f,18f,5f,22f);line(5f,22f,12f,18f);line(7f,8f,17f,8f);line(7f,12f,14f,12f) }
            "photo" -> { rect(3f,3f,21f,21f);canvas.drawCircle(8f,8f,1.6f,paint);val p=Path();p.moveTo(4f,18f);p.lineTo(11f,11f);p.lineTo(20f,20f);canvas.drawPath(p,paint) }
            "apps" -> { rect(3f,3f,10f,10f);rect(14f,3f,21f,10f);rect(3f,14f,10f,21f);rect(14f,14f,21f,21f) }
            "alarm" -> { canvas.drawCircle(12f,13f,8f,paint);line(12f,8f,12f,13f);line(12f,13f,16f,15f);line(3f,5f,7f,2f);line(17f,2f,21f,5f);line(6f,20f,4f,23f);line(18f,20f,20f,23f) }
            "mic" -> { rect(8f,2f,16f,15f);canvas.drawArc(5f,7f,19f,19f,0f,180f,false,paint);line(12f,19f,12f,23f);line(8f,23f,16f,23f) }
            "help" -> { val p=Path();p.moveTo(12f,21f);p.cubicTo(2f,14f,0f,8f,5f,4f);p.cubicTo(8f,2f,11f,4f,12f,7f);p.cubicTo(15f,1f,21f,3f,22f,8f);p.cubicTo(23f,13f,17f,18f,12f,21f);canvas.drawPath(p,paint) }
            "bell" -> { val p=Path();p.moveTo(4f,18f);p.lineTo(6f,15f);p.lineTo(6f,9f);p.cubicTo(6f,1f,18f,1f,18f,9f);p.lineTo(18f,15f);p.lineTo(20f,18f);p.close();canvas.drawPath(p,paint);canvas.drawArc(9f,18f,15f,23f,0f,180f,false,paint) }
            "qr" -> { rect(2f,2f,9f,9f);rect(15f,2f,22f,9f);rect(2f,15f,9f,22f);line(15f,15f,22f,15f);line(15f,15f,15f,22f);line(20f,19f,22f,19f);line(20f,22f,22f,22f) }
            else -> { canvas.drawCircle(12f,8f,4f,paint);canvas.drawArc(3f,13f,21f,27f,180f,180f,false,paint) }
        }
        canvas.restoreToCount(saved)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha }
    override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter }
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
