package com.sremonak.netspeedmeter.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate

/** Lightweight SQLite store for the app's own per-day traffic accounting. */
class UsageDatabase(context: Context) : SQLiteOpenHelper(context, "usage.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE daily_usage (
                day TEXT PRIMARY KEY NOT NULL,
                mobile_rx INTEGER NOT NULL DEFAULT 0,
                mobile_tx INTEGER NOT NULL DEFAULT 0,
                wifi_rx INTEGER NOT NULL DEFAULT 0,
                wifi_tx INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun add(u: DailyUsage) {
        if (u.isEmpty) return
        val db = writableDatabase
        val day = u.date.toString()
        db.beginTransaction()
        try {
            db.execSQL("INSERT OR IGNORE INTO daily_usage(day) VALUES(?)", arrayOf(day))
            db.execSQL(
                "UPDATE daily_usage SET mobile_rx = mobile_rx + ?, mobile_tx = mobile_tx + ?, " +
                    "wifi_rx = wifi_rx + ?, wifi_tx = wifi_tx + ? WHERE day = ?",
                arrayOf<Any>(u.mobileRx, u.mobileTx, u.wifiRx, u.wifiTx, day)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun get(day: LocalDate): DailyUsage = range(day, day).firstOrNull() ?: DailyUsage(day)

    fun range(from: LocalDate, to: LocalDate): List<DailyUsage> {
        val out = ArrayList<DailyUsage>()
        readableDatabase.rawQuery(
            "SELECT day, mobile_rx, mobile_tx, wifi_rx, wifi_tx FROM daily_usage WHERE day BETWEEN ? AND ? ORDER BY day",
            arrayOf(from.toString(), to.toString())
        ).use { c ->
            while (c.moveToNext()) {
                out += DailyUsage(
                    date = LocalDate.parse(c.getString(0)),
                    mobileRx = c.getLong(1),
                    mobileTx = c.getLong(2),
                    wifiRx = c.getLong(3),
                    wifiTx = c.getLong(4),
                )
            }
        }
        return out
    }

    fun clear() {
        writableDatabase.delete("daily_usage", null, null)
    }
}
