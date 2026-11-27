package com.example.investmentdecision

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Conservative, rule-based price levels derived from paired daily closing prices.
 * This is a volatility/support-resistance estimate, not a validated forecast.
 */
internal object GoldFundTradeLevels {
    data class Result(
        val entry1: Double,
        val entry2: Double,
        val stopLoss: Double,
        val target1: Double,
        val target2: Double,
        val support20: Double,
        val resistance20: Double,
        val averageDailyMove: Double
    )

    fun calculate(current: Double, closesNewestFirst: List<Double>): Result? {
        if (!current.isFinite() || current <= 0.0 || closesNewestFirst.size < 20) return null
        val closes = closesNewestFirst.take(60).filter { it.isFinite() && it > 0.0 }
        if (closes.size < 20) return null

        val window20 = closes.take(20)
        val support = window20.minOrNull() ?: return null
        val resistance = window20.maxOrNull() ?: return null
        val moves = closes.take(15).zipWithNext().mapNotNull { (newer, older) ->
            if (older > 0.0) abs(newer - older) else null
        }
        if (moves.size < 10) return null
        val avgMove = moves.average().takeIf { it.isFinite() && it > 0.0 }
            ?: current * 0.01

        // Staggered pullback entries; do not chase above the current market price.
        val e1 = min(current, max(support, current - 0.5 * avgMove))
        val e2 = min(e1, max(support * 0.995, current - 1.0 * avgMove))
        if (e1 <= 0.0 || e2 <= 0.0 || e2 >= e1) return null

        // Stop is placed below both the observed 20-session support and second entry.
        val stop = min(support - 0.5 * avgMove, e2 - 1.5 * avgMove)
        if (stop <= 0.0 || stop >= e2) return null
        val risk = e1 - stop
        val t1 = e1 + 1.5 * risk
        val t2 = e1 + 2.5 * risk
        if (!listOf(t1, t2).all { it.isFinite() && it > e1 }) return null

        return Result(e1, e2, stop, t1, t2, support, resistance, avgMove)
    }
}
