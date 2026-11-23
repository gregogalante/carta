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

/**
 * Full-screen, non-interactive layer that makes the screen read like paper.
 *
 * Windows are composed with plain alpha blending, so the layer has two tools: a light veil that
 * lifts blacks (e-ink ink is never pure black) and a black grain that only darkens. Darkening is
 * proportional to what is underneath, so the grain shows on white areas like paper fibers and
 * leaves dark areas clean instead of covering them with noise.
 */
class PaperOverlayView(context: Context) : View(context) {

    /** 0..1; see [suggestedPaperIntensity] for the default. */
    var intensity = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            liftPaint.alpha = (MAX_LIFT_ALPHA * field).roundToInt()
            grainPaint.alpha = (255 * field).roundToInt() // The tile already holds the full-strength grain.
            invalidate()
        }

    private val liftPaint = Paint().apply { color = PAPER_TINT }

    // One grain cell per dp, so the texture has the same physical size on every screen density.
    private val grainPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        shader = BitmapShader(paperTile(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT).apply {
            val density = resources.displayMetrics.density
            setLocalMatrix(Matrix().apply { setScale(density, density) })
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawPaint(liftPaint)
        canvas.drawPaint(grainPaint)
    }

    private companion object {
        const val TILE = 256
        const val MAX_LIFT_ALPHA = 26 // ~10%: blacks become a deep gray, like e-ink.
        const val MAX_GRAIN_ALPHA = 41 // ~16% darkening at the darkest fiber.
        const val SEED = 7 // Same texture on every start, so it never "jumps".
        val PAPER_TINT = Color.rgb(255, 236, 200)

        /** Fine random fibers over soft clouds, the uneven formation of real paper. */
        fun paperTile(): Bitmap {
            val random = Random(SEED)
            val coarse = valueNoise(random, cell = 32)
            val fine = valueNoise(random, cell = 8)
            val pixels = IntArray(TILE * TILE) { i ->
                // Cubed: most fibers are faint and a few are dark specks.
                val fiber = random.nextFloat().let { it * it * it }
                val cloud = 0.6f * coarse[i] + 0.4f * fine[i]
                val darkness = (0.55f * fiber + 0.45f * cloud).coerceIn(0f, 1f)
                Color.argb((MAX_GRAIN_ALPHA * darkness).roundToInt(), 0, 0, 0)
            }
            return Bitmap.createBitmap(pixels, TILE, TILE, Bitmap.Config.ARGB_8888)
        }

        /** Smooth noise on a [cell]-sized lattice that wraps at the tile edges, so repeats leave no seams. */
        fun valueNoise(random: Random, cell: Int): FloatArray {
            val n = TILE / cell
            val lattice = FloatArray(n * n) { random.nextFloat() }
            fun at(x: Int, y: Int) = lattice[(y % n) * n + x % n]
            fun smooth(t: Float) = t * t * (3 - 2 * t)
            fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
            return FloatArray(TILE * TILE) { i ->
                val x = i % TILE
                val y = i / TILE
                val gx = x / cell
                val gy = y / cell
                val fx = smooth(x % cell / cell.toFloat())
                val fy = smooth(y % cell / cell.toFloat())
                lerp(lerp(at(gx, gy), at(gx + 1, gy), fx), lerp(at(gx, gy + 1), at(gx + 1, gy + 1), fx), fy)
            }
        }
    }
}
