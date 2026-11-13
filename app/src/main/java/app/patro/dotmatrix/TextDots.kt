package app.patro.dotmatrix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/**
 * Dot-matrix text for any script. Each line is drawn small with the system font (which covers
 * Devanagari and Latin), and every lit pixel becomes one dot. At ~14 px this keeps Nepali month
 * and weekday names readable, which a hand-made 5×7 font can't do.
 */
object TextDots {
    class Line(val text: String, val color: Int, val size: Float = 14f, val bold: Boolean = false)

    /** Alpha (0–255) above which a raster pixel becomes a dot. */
    private const val THRESHOLD = 110

    private class Grid(val w: Int, val h: Int, val on: BooleanArray)

    private fun raster(line: Line): Grid {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = line.size
            typeface = if (line.bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        val text = line.text.ifEmpty { " " }
        val b = Rect()
        paint.getTextBounds(text, 0, text.length, b)
        val w = (b.width() + 2).coerceAtLeast(1)
        val h = (b.height() + 2).coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawText(text, 1f - b.left, 1f - b.top, paint)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        bmp.recycle()
        return Grid(w, h, BooleanArray(w * h) { Color.alpha(px[it]) > THRESHOLD })
    }

    /**
     * Renders [lines] top to bottom, left aligned. [dotPx] is the dot diameter, [pitchPx] the
     * distance between dot centres, [gapDots] the empty rows between lines. A soft shadow keeps
     * white dots readable on light wallpapers.
     */
    fun render(lines: List<Line>, dotPx: Float, pitchPx: Float, gapDots: Int = 3, shadow: Boolean = true): Bitmap {
        val grids = lines.filter { it.text.isNotBlank() }.map { it to raster(it) }
        val cols = grids.maxOfOrNull { it.second.w } ?: 1
        val rows = grids.sumOf { it.second.h } + gapDots * (grids.size - 1).coerceAtLeast(0)
        val pad = if (shadow) (dotPx * 1.5f).toInt() else 1
        val bmp = Bitmap.createBitmap(
            (cols * pitchPx + pad * 2).toInt().coerceAtLeast(1),
            (rows * pitchPx + pad * 2).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (shadow) paint.setShadowLayer(dotPx * 0.6f, 0f, dotPx * 0.25f, 0x80000000.toInt())
        val r = dotPx / 2f
        var y0 = 0
        for ((line, g) in grids) {
            paint.color = line.color
            for (y in 0 until g.h) for (x in 0 until g.w) {
                if (g.on[y * g.w + x]) {
                    canvas.drawCircle(pad + x * pitchPx + r, pad + (y0 + y) * pitchPx + r, r, paint)
                }
            }
            y0 += g.h + gapDots
        }
        return bmp
    }
}
