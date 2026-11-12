package app.patro.dotmatrix

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint

/** Lays out a string as a grid of dots (5×7 per glyph, one empty column between glyphs). */
class DotMatrix(val text: String) {
    private val chars = text.filter { Glyphs.has(it) }

    val cols: Int = if (chars.isEmpty()) 0 else chars.length * (Glyphs.COLS + GAP) - GAP
    val rows: Int = Glyphs.ROWS

    fun lit(col: Int, row: Int): Boolean {
        val stride = Glyphs.COLS + GAP
        val gi = col / stride
        val c = col % stride
        if (gi >= chars.length || c >= Glyphs.COLS) return false
        return Glyphs.lit(chars[gi], row, c)
    }

    /** Calls [block] for every grid position. */
    inline fun forEach(block: (col: Int, row: Int, lit: Boolean) -> Unit) {
        for (r in 0 until rows) for (c in 0 until cols) block(c, r, lit(c, r))
    }

    /**
     * Renders to a bitmap for RemoteViews (widgets can't use custom fonts but can show bitmaps).
     * [dotPx] is the dot diameter; dots sit on a pitch of [dotPx] × [PITCH].
     */
    fun toBitmap(dotPx: Float, color: Int, dimColor: Int = 0): Bitmap {
        val pitch = dotPx * PITCH
        val w = ((cols - 1) * pitch + dotPx).toInt().coerceAtLeast(1) + 2
        val h = ((rows - 1) * pitch + dotPx).toInt().coerceAtLeast(1) + 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val r = dotPx / 2f
        forEach { c, row, on ->
            if (!on && dimColor == 0) return@forEach
            paint.color = if (on) color else dimColor
            canvas.drawCircle(1 + r + c * pitch, 1 + r + row * pitch, r, paint)
        }
        return bmp
    }

    companion object {
        const val GAP = 1
        /** Centre-to-centre distance as a multiple of the dot diameter. */
        const val PITCH = 1.3f
    }
}
