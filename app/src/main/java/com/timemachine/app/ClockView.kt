package com.timemachine.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/** A glowing clock whose hands spin backwards, forever. */
class ClockView(context: Context) : View(context) {

    private val d = resources.displayMetrics.density
    private val fillP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tickP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val handP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hourP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trailP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dotP = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var angle = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f)

    init {
        fillP.style = Paint.Style.FILL
        fillP.color = Color.parseColor("#120D2B")

        glowP.style = Paint.Style.STROKE
        glowP.strokeWidth = 14f * d
        glowP.color = Color.parseColor("#337C5CFF")

        ringP.style = Paint.Style.STROKE
        ringP.strokeWidth = 3f * d
        ringP.color = Color.parseColor("#9B8CFF")

        tickP.style = Paint.Style.STROKE
        tickP.strokeWidth = 2f * d
        tickP.strokeCap = Paint.Cap.ROUND
        tickP.color = Color.parseColor("#CFC8FF")

        handP.style = Paint.Style.STROKE
        handP.strokeWidth = 4f * d
        handP.strokeCap = Paint.Cap.ROUND
        handP.color = Color.WHITE

        hourP.style = Paint.Style.STROKE
        hourP.strokeWidth = 6f * d
        hourP.strokeCap = Paint.Cap.ROUND
        hourP.color = Color.parseColor("#FFD27A")

        trailP.style = Paint.Style.STROKE
        trailP.strokeWidth = 6f * d
        trailP.strokeCap = Paint.Cap.ROUND
        trailP.color = Color.parseColor("#667C5CFF")

        dotP.style = Paint.Style.FILL
        dotP.color = Color.WHITE

        animator.duration = 5000L
        animator.repeatCount = ValueAnimator.INFINITE
        animator.interpolator = LinearInterpolator()
        animator.addUpdateListener {
            angle = it.animatedValue as Float
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animator.start()
    }

    override fun onDetachedFromWindow() {
        animator.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - 10f * d

        canvas.drawCircle(cx, cy, r, fillP)
        canvas.drawCircle(cx, cy, r, glowP)
        canvas.drawCircle(cx, cy, r, ringP)

        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0)
            val len = if (i % 3 == 0) 14f * d else 8f * d
            val x1 = (cx + Math.cos(a) * (r - len)).toFloat()
            val y1 = (cy + Math.sin(a) * (r - len)).toFloat()
            val x2 = (cx + Math.cos(a) * (r - 4f * d)).toFloat()
            val y2 = (cy + Math.sin(a) * (r - 4f * d)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, tickP)
        }

        val rr = r * 0.78f
        rect.set(cx - rr, cy - rr, cx + rr, cy + rr)
        canvas.drawArc(rect, -90f - angle, 70f, false, trailP)

        canvas.save()
        canvas.rotate(-angle, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - r * 0.78f, handP)
        canvas.restore()

        canvas.save()
        canvas.rotate(-angle / 12f, cx, cy)
        canvas.drawLine(cx, cy, cx, cy - r * 0.5f, hourP)
        canvas.restore()

        canvas.drawCircle(cx, cy, 6f * d, dotP)
    }
}
