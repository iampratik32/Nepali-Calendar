package app.patro.dotmatrix

import app.patro.bs.Np
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DotMatrixTest {
    @Test fun everyDigitHasAGlyph() {
        for (ch in "0123456789" + Np.digits("0123456789") + "/:-") {
            assertTrue("missing glyph for $ch", Glyphs.has(ch))
            var lit = 0
            for (r in 0 until Glyphs.ROWS) for (c in 0 until Glyphs.COLS) if (Glyphs.lit(ch, r, c)) lit++
            assertTrue("glyph $ch is empty", ch == ' ' || lit > 0)
        }
    }

    @Test fun digitsAreDistinct() {
        val all = "0123456789" + Np.digits("0123456789")
        val shapes = all.map { ch ->
            buildString { for (r in 0 until Glyphs.ROWS) for (c in 0 until Glyphs.COLS) append(if (Glyphs.lit(ch, r, c)) '#' else '.') }
        }
        assertEquals(all.length, shapes.toSet().size)
    }

    @Test fun layoutWidth() {
        val m = DotMatrix("२६")
        assertEquals(2 * Glyphs.COLS + DotMatrix.GAP, m.cols)
        assertEquals(Glyphs.ROWS, m.rows)
        assertEquals(0, DotMatrix("").cols)
    }
}
