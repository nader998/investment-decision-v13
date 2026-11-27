package com.example.investmentdecision

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local store for paired daily closing-price and NAV observations.
 * A row is eligible for premium statistics and trading-day returns only
 * when both values are valid. NAV-only dates are retained for reference
 * but are not counted as exchange trading days.
 */
class GoldFundHistoryStore(context: Context) :
    SQLiteOpenHelper(context, "gold_fund_history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_history (
                ins_code TEXT NOT NULL,
                trade_date TEXT NOT NULL,
                close_toman REAL,
                nav_toman REAL,
                fetched_at INTEGER NOT NULL,
                PRIMARY KEY (ins_code, trade_date)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_history_date ON daily_history(ins_code, trade_date DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No destructive migration is needed for version 1.
    }

    fun upsert(insCode: String, date: String, closeToman: Double?, navToman: Double?) {
        if (insCode.isBlank() || !date.matches(Regex("\\d{8}"))) return
        val db = writableDatabase
        var existingClose: Double? = null
        var existingNav: Double? = null
        db.rawQuery(
            "SELECT close_toman, nav_toman FROM daily_history WHERE ins_code = ? AND trade_date = ?",
            arrayOf(insCode, date)
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                if (!cursor.isNull(0)) existingClose = cursor.getDouble(0)
                if (!cursor.isNull(1)) existingNav = cursor.getDouble(1)
            }
        }
        // A missing value from one source must never erase a valid value already
        // collected from another source for the same fund/date.
        val finalClose = closeToman?.takeIf { it.isFinite() && it > 0.0 } ?: existingClose
        val finalNav = navToman?.takeIf { it.isFinite() && it > 0.0 } ?: existingNav
        val values = ContentValues().apply {
            put("ins_code", insCode)
            put("trade_date", date)
            if (finalClose != null) put("close_toman", finalClose) else putNull("close_toman")
            if (finalNav != null) put("nav_toman", finalNav) else putNull("nav_toman")
            put("fetched_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict(
            "daily_history", null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    data class PremiumPoint(val date: String, val premiumPercent: Double)

    fun latestPremiums(insCode: String, limit: Int = 60): List<PremiumPoint> {
        val result = mutableListOf<PremiumPoint>()
        readableDatabase.rawQuery(
            """
            SELECT trade_date, close_toman, nav_toman
            FROM daily_history
            WHERE ins_code = ? AND close_toman > 0 AND nav_toman > 0
            ORDER BY trade_date DESC LIMIT ?
            """.trimIndent(),
            arrayOf(insCode, limit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val close = cursor.getDouble(1)
                val nav = cursor.getDouble(2)
                if (nav > 0.0) {
                    result.add(PremiumPoint(cursor.getString(0), (close / nav - 1.0) * 100.0))
                }
            }
        }
        return result
    }

    fun pairedObservationCount(insCode: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM daily_history WHERE ins_code = ? AND close_toman > 0 AND nav_toman > 0",
            arrayOf(insCode)
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun navReturnPercent(insCode: String, tradingDays: Int): Double? {
        if (tradingDays <= 0) return null
        val values = mutableListOf<Double>()
        readableDatabase.rawQuery(
            """
            SELECT nav_toman FROM daily_history
            WHERE ins_code = ? AND nav_toman > 0
            ORDER BY trade_date DESC LIMIT ?
            """.trimIndent(),
            arrayOf(insCode, (tradingDays + 1).toString())
        ).use { cursor ->
            while (cursor.moveToNext()) values.add(cursor.getDouble(0))
        }
        // NAV performance is calculated from NAV observations only. Requiring
        // a same-day closing price incorrectly made valid NAV-only dates vanish
        // and returned null/incorrect 5, 15 and 30 trading-day performance.
        if (values.size <= tradingDays) return null
        val latest = values.first()
        val prior = values[tradingDays]
        if (prior <= 0.0) return null
        return (latest / prior - 1.0) * 100.0
    }

    fun percentile60(insCode: String): Double? {
        val points = latestPremiums(insCode, 60)
        if (points.size < 60) return null
        val current = points.first().premiumPercent
        val belowOrEqual = points.count { it.premiumPercent <= current }
        return (belowOrEqual.toDouble() / points.size.toDouble()) * 100.0
    }
}
