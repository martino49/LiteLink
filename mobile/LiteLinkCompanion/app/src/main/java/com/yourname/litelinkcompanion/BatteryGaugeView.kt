package com.yourname.litelinkcompanion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

/** Simple circular gauge that fills from 0% to [percent]. */
class BatteryGaugeView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var percent = 0f
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.pill_grey)
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 26f
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.teal_500)
    }

    private val rect = RectF()

    fun setPercent(value: Float, animate: Boolean = false) {
        percent = value.coerceIn(0f, 100f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val size = minOf(w, h)
        rect.set(
            (w - size) / 2 + 14f,
            (h - size) / 2 + 14f,
            (w + size) / 2 - 14f,
            (h + size) / 2 - 14f
        )
        canvas.drawArc(rect, 135f, 270f, false, trackPaint)
        val sweep = 270f * (percent / 100f)
        canvas.drawArc(rect, 135f, sweep, false, arcPaint)
    }
}
