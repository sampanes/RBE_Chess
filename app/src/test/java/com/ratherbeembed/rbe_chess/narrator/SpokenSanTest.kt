package com.ratherbeembed.rbe_chess.narrator

import org.junit.Assert.assertEquals
import org.junit.Test

class SpokenSanTest {

    @Test
    fun `spec section 7 worked examples`() {
        assertEquals("e four", SpokenSan.expand("e4"))
        assertEquals("pawn e four", SpokenSan.expand("e4", pawnWord = true))
        assertEquals("knight f three", SpokenSan.expand("Nf3"))
        assertEquals("bishop takes c six, check", SpokenSan.expand("Bxc6+"))
        assertEquals("e takes d five", SpokenSan.expand("exd5"))
        assertEquals("castles kingside", SpokenSan.expand("O-O"))
        assertEquals("castles queenside, check", SpokenSan.expand("O-O-O+"))
        assertEquals("e eight promotes to queen, checkmate", SpokenSan.expand("e8=Q#"))
        assertEquals("knight from b to d two", SpokenSan.expand("Nbd2"))
        assertEquals("rook from rank one to e two", SpokenSan.expand("R1e2"))
        assertEquals("queen from h five takes f seven, checkmate", SpokenSan.expand("Qh5xf7#"))
    }

    @Test
    fun `pawn word never doubles up on captures`() {
        assertEquals("e takes d six", SpokenSan.expand("exd6", pawnWord = true))
        assertEquals("d takes e eight promotes to knight", SpokenSan.expand("dxe8=N", pawnWord = true))
    }

    @Test
    fun `phonetic override swaps file letters only`() {
        assertEquals("knight from bravo to delta two", SpokenSan.expand("Nbd2", phonetic = true))
        assertEquals("echo takes delta five", SpokenSan.expand("exd5", phonetic = true))
    }

    @Test
    fun `unparseable text passes through`() {
        assertEquals("Zz9", SpokenSan.expand("Zz9"))
    }

    @Test
    fun `eval phrases`() {
        val narrator = Narrator()
        assertEquals("plus 0.3", narrator.evalPhrase(Eval(cp = 30)))
        assertEquals("minus 1.5", narrator.evalPhrase(Eval(cp = -145)))
        assertEquals("level", narrator.evalPhrase(Eval(cp = 5)))
        assertEquals("level", narrator.evalPhrase(Eval(cp = -9)))
        assertEquals("plus 12.0", narrator.evalPhrase(Eval(cp = 1200)))
        assertEquals("mate in three", narrator.evalPhrase(Eval(mate = 3)))
        assertEquals("getting mated in two", narrator.evalPhrase(Eval(mate = -2)))
        assertEquals("mate in 12", narrator.evalPhrase(Eval(mate = 12)))
    }

    @Test
    fun `quality buckets`() {
        assertEquals(MoveQuality.BEST, MoveQuality.classify(QualityFacts(0, isBest = true)))
        assertEquals(MoveQuality.ACCURATE, MoveQuality.classify(QualityFacts(10)))
        assertEquals(MoveQuality.FINE, MoveQuality.classify(QualityFacts(35)))
        assertEquals(MoveQuality.INACCURACY, MoveQuality.classify(QualityFacts(90)))
        assertEquals(MoveQuality.MISTAKE, MoveQuality.classify(QualityFacts(160)))
        assertEquals(MoveQuality.BLUNDER, MoveQuality.classify(QualityFacts(410)))
        assertEquals(MoveQuality.BLUNDER, MoveQuality.classify(QualityFacts(0, missedMate = true)))
    }

    @Test
    fun `win percent follows the lichess curve`() {
        assertEquals(50.0, WinPercent.of(0), 1e-9)
        assertEquals(WinPercent.of(300), 100.0 - WinPercent.of(-300), 1e-9)
        // Dropping 100 cp from +0.2 matters; from +6.0 it barely registers.
        val nearLevel = WinPercent.classifyDrop(WinPercent.of(20), WinPercent.of(-80))
        val alreadyWon = WinPercent.classifyDrop(WinPercent.of(600), WinPercent.of(500))
        assertEquals(MoveQuality.INACCURACY, nearLevel)
        assertEquals(MoveQuality.FINE, alreadyWon)
    }
}
