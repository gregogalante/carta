package com.gregoriogalante.carta

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import kotlin.math.roundToInt
import kotlin.random.Random

/** Full-screen, non-interactive layer: warm paper tint plus static grain. */
class PaperOverlayView(context: Context) : View(context) {

    /** 0..1; see PAPER_INTENSITY_SUGGESTED for the default. */
    var intensity = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            tintPaint.alpha = (MAX_TINT_ALPHA * field).roundToInt()
            grainPaint.alpha = (MAX_GRAIN_ALPHA * field).roundToInt()
            invalidate()
        }

    private val tintPaint = Paint().apply { color = PAPER_TINT }

    // One grain cell per dp, so the texture looks the same on every screen density.
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(grainTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            val density = resources.displayMetrics.density
            setLocalMatrix(Matrix().apply { setScale(density, density) })
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(tintPaint)
        canvas.drawPaint(grainPaint)
    }

    private fun grainTile(): Bitmap {
        val pixels = IntArray(GRAIN_SIZE * GRAIN_SIZE) {
            val v = Random.nextInt(256)
            Color.rgb(v, v, v)
        }
        return Bitmap.createBitmap(pixels, GRAIN_SIZE, GRAIN_SIZE, Bitmap.Config.ARGB_8888)
    }

    private companion object {
        const val GRAIN_SIZE = 128
        const val MAX_TINT_ALPHA = 60
        const val MAX_GRAIN_ALPHA = 36
        val PAPER_TINT = Color.rgb(255, 236, 200)
    }
}
