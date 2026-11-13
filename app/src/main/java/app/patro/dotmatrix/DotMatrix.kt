package app.patro.dotmatrix

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

    companion object {
        const val GAP = 1
        /** Centre-to-centre distance as a multiple of the dot diameter. */
        const val PITCH = 1.3f
    }
}
