package com.example.buswatch.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

object MapUtils {
    fun getScaledBitmap(context: Context, @DrawableRes drawableRes: Int, widthDp: Int, heightDp: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, drawableRes) ?: return null
        val density = context.resources.displayMetrics.density
        val widthPx = (widthDp * density).toInt().coerceAtLeast(1)
        val heightPx = (heightDp * density).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
