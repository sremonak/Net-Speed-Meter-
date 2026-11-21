package com.sremonak.netspeedmeter.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/** Draws the current speed as a monochrome bitmap used for the status-bar / tile icon. */
object SpeedIconFactory {
    private const val SIZE = 96
    private var cacheKey: String? = null
    private var cacheBitmap: Bitmap? = null

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }

    @Synchronized
    fun create(value: String, unit: String): Bitmap {
        val key = "$value|$unit"
        cacheBitmap?.let { if (key == cacheKey) return it }
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val maxWidth = SIZE * 0.98f

        valuePaint.textSize = SIZE * 0.64f
        while (valuePaint.measureText(value) > maxWidth && valuePaint.textSize > 12f) valuePaint.textSize -= 2f
        unitPaint.textSize = SIZE * 0.40f
        while (unitPaint.measureText(unit) > maxWidth && unitPaint.textSize > 10f) unitPaint.textSize -= 1f

        canvas.drawText(value, SIZE / 2f, SIZE * 0.58f, valuePaint)
        canvas.drawText(unit, SIZE / 2f, SIZE * 0.97f, unitPaint)
        cacheKey = key
        cacheBitmap = bmp
        return bmp
    }
}
