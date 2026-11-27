package com.example.investmentdecision

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldFundTradeLevelsTest {
    @Test
    fun rejectsInsufficientHistory() {
        assertNull(GoldFundTradeLevels.calculate(1000.0, List(19) { 1000.0 + it }))
    }

    @Test
    fun rejectsInvalidCurrentPrice() {
        val closes = (0 until 30).map { 1000.0 + it * 2.0 }
        assertNull(GoldFundTradeLevels.calculate(0.0, closes))
    }

    @Test
    fun returnsOrderedLevelsForVolatileSeries() {
        val closes = (0 until 60).map { i ->
            1000.0 + i * 2.0 + if (i % 2 == 0) 8.0 else -8.0
        }.reversed()
        val levels = GoldFundTradeLevels.calculate(closes.first(), closes)
        assertNotNull(levels)
        levels!!
        assertTrue(levels.stopLoss < levels.entry2)
        assertTrue(levels.entry2 < levels.entry1)
        assertTrue(levels.entry1 <= closes.first())
        assertTrue(levels.target1 > levels.entry1)
        assertTrue(levels.target2 > levels.target1)
    }

    @Test
    fun returnsNullWhenSecondEntryCannotBeSeparated() {
        val closes = List(30) { 1000.0 }
        // Flat prices produce a volatility fallback; the resulting levels must
        // still be strictly ordered and positive rather than NaN or infinity.
        val levels = GoldFundTradeLevels.calculate(1000.0, closes)
        assertNotNull(levels)
        levels!!
        assertTrue(levels.stopLoss > 0.0)
        assertTrue(levels.stopLoss < levels.entry2)
        assertTrue(levels.entry2 < levels.entry1)
        assertTrue(levels.target1 > levels.entry1)
        assertTrue(levels.target2 > levels.target1)
    }
}
