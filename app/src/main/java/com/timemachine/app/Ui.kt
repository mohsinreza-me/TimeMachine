package com.timemachine.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.widget.Button

object Ui {
    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun background(): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                Color.parseColor("#0B0F1E"),
                Color.parseColor("#1B1140"),
                Color.parseColor("#2A1458")
            )
        )
    }

    fun pill(ctx: Context, hex: String): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(ctx, 28).toFloat()
        g.setColor(Color.parseColor(hex))
        return g
    }

    fun card(ctx: Context): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(ctx, 20).toFloat()
        g.setColor(Color.parseColor("#1AFFFFFF"))
        g.setStroke(dp(ctx, 1), Color.parseColor("#33FFFFFF"))
        return g
    }

    fun button(ctx: Context, label: String, hex: String): Button {
        val b = Button(ctx)
        b.text = label
        b.setAllCaps(false)
        b.textSize = 16f
        b.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        b.setTextColor(Color.WHITE)
        b.background = pill(ctx, hex)
        return b
    }
}
