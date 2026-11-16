// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

object Fmt {
    private val india = Locale.forLanguageTag("en-IN")
    private val whole = NumberFormat.getCurrencyInstance(india).apply { maximumFractionDigits = 0 }
    private val exact = NumberFormat.getCurrencyInstance(india).apply {
        minimumFractionDigits = 2; maximumFractionDigits = 2
    }
    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", india)
    private val shortDateFmt = DateTimeFormatter.ofPattern("d MMM", india)

    fun money(paise: Long): String =
        if (paise % 100 == 0L) whole.format(paise / 100) else exact.format(paise / 100.0)

    fun today(): Long = LocalDate.now().toEpochDay()

    fun day(epochDay: Long): String = LocalDate.ofEpochDay(epochDay).format(dateFmt)

    fun shortDay(epochDay: Long): String {
        val d = LocalDate.ofEpochDay(epochDay)
        return if (d.year == LocalDate.now().year) d.format(shortDateFmt) else d.format(dateFmt)
    }

    fun millisToDay(millis: Long): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /** "Due today", "Due in 4 days", "12 days overdue". */
    fun dueRelative(dueDay: Long, today: Long = today()): String {
        val diff = dueDay - today
        return when {
            diff == 0L -> "Due today"
            diff == 1L -> "Due tomorrow"
            diff > 1 -> "Due in $diff days"
            diff == -1L -> "1 day overdue"
            else -> "${abs(diff)} days overdue"
        }
    }

    fun parseAmount(text: String): Long? = text.replace(",", "").trim().toBigDecimalOrNull()
        ?.takeIf { it.signum() > 0 && it.scale() <= 2 }
        ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

    fun amountInput(paise: Long): String =
        paise.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString()
}
