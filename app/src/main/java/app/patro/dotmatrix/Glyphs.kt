package app.patro.dotmatrix

/**
 * Hand-made 5×7 dot-matrix glyphs. `#` = lit dot, `.` = unlit.
 * Devanagari digits were traced from Noto Sans Devanagari by eye and simplified to the grid.
 * Nothing's own Ndot font is deliberately not used (its licence is brand-only).
 */
object Glyphs {
    const val COLS = 5
    const val ROWS = 7

    private val SRC: Map<Char, String> = mapOf(
        '0' to ".###. #...# #..## #.#.# ##..# #...# .###.",
        '1' to "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
        '2' to ".###. #...# ....# ...#. ..#.. .#... #####",
        '3' to "####. ....# ....# .###. ....# ....# ####.",
        '4' to "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
        '5' to "##### #.... ####. ....# ....# #...# .###.",
        '6' to ".###. #.... #.... ####. #...# #...# .###.",
        '7' to "##### ....# ...#. ..#.. .#... .#... .#...",
        '8' to ".###. #...# #...# .###. #...# #...# .###.",
        '9' to ".###. #...# #...# .#### ....# ....# .###.",

        '०' to "..... .###. #...# #...# #...# .###. .....",
        '१' to ".###. #...# ....# .###. .#... ..#.. ...#.",
        '२' to ".###. #...# ....# ##.#. ..#.. ..#.. ...#.",
        '३' to ".###. ....# ..##. ....# ##.#. ..#.. ...#.",
        '४' to "#...# #...# .#.#. ..#.. .#.#. #...# .###.",
        '५' to ".#..# #...# #...# #..## .##.# ....# ....#",
        '६' to ".###. #.... .##.. #.... .###. ...#. ....#",
        '७' to "#.... #.##. #.#.# #.#.# #..## #...# .###.",
        '८' to "....# ...#. ..#.. .#... #.... #...# .###.",
        '९' to ".###. #...# #...# .###. ..#.. ...#. ....#",

        '/' to "....# ....# ...#. ..#.. .#... #.... #....",
        ':' to "..... ..#.. ..... ..... ..... ..#.. .....",
        '-' to "..... ..... ..... .###. ..... ..... .....",
        ' ' to "..... ..... ..... ..... ..... ..... .....",
    )

    /** Glyph bitmaps: `rows[r]` has bit (COLS-1-c) set when column c is lit. */
    private val BITS: Map<Char, IntArray> = SRC.mapValues { (ch, s) ->
        val rows = s.split(' ')
        require(rows.size == ROWS) { "Glyph '$ch' needs $ROWS rows" }
        IntArray(ROWS) { r ->
            val row = rows[r]
            require(row.length == COLS) { "Glyph '$ch' row $r needs $COLS columns" }
            var v = 0
            for (c in 0 until COLS) if (row[c] == '#') v = v or (1 shl (COLS - 1 - c))
            v
        }
    }

    fun has(ch: Char) = ch in BITS

    fun lit(ch: Char, row: Int, col: Int): Boolean {
        val g = BITS[ch] ?: return false
        return (g[row] shr (COLS - 1 - col)) and 1 == 1
    }
}
