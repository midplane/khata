// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.sms

import dev.midplane.khata.data.DueRow
import dev.midplane.khata.data.outstanding
import java.time.Instant
import java.time.ZoneId

enum class Confidence { High, Medium, Low }

data class Suggestion(
    val sms: CreditSms,
    /** Best-matching open due, if any scored above zero. */
    val best: DueRow?,
    val confidence: Confidence?,
    /** All open dues ordered by how well they match — used for the "pick another due" list. */
    val ranked: List<DueRow>,
)

object SmsMatcher {
    private val stopWords = setOf(
        "pvt", "ltd", "private", "limited", "llp", "and", "the", "mr", "mrs", "ms", "dr", "shri", "smt",
        "company", "inc", "corp", "sons", "bros", "enterprises", "traders", "industries", "services",
        "solutions", "india", "group", "huf", "associates",
    )

    private fun nameTokens(name: String): List<String> =
        name.lowercase().split(Regex("[^a-z0-9]+")).filter { it.length >= 3 && it !in stopWords }

    private data class Score(val name: Double, val amount: Double) {
        val total get() = if (name == 0.0 && amount < 1.0) 0.0 else name * 2 + amount * 1.5
    }

    private fun score(sms: CreditSms, due: DueRow): Score {
        val outstanding = due.outstanding
        if (outstanding <= 0) return Score(0.0, 0.0)
        val smsDay = Instant.ofEpochMilli(sms.date).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        // A payment is very unlikely to arrive long before the work was invoiced.
        if (smsDay < due.due.invoiceDate - 7) return Score(0.0, 0.0)

        val text = (sms.body + " " + sms.payer.orEmpty()).lowercase()
        val tokens = nameTokens(due.clientName)
        var name = if (tokens.isEmpty()) 0.0 else
            tokens.count { Regex("\\b" + Regex.escape(it)).containsMatchIn(text) }.toDouble() / tokens.size
        val phone = due.clientPhone?.filter(Char::isDigit)?.takeLast(10)
        if (phone != null && phone.length == 10 && text.contains(phone)) name = 1.0

        val amount = when {
            sms.amount == outstanding -> 1.0
            sms.amount == due.due.amount -> 0.8
            sms.amount < outstanding -> 0.3
            else -> 0.1
        }
        return Score(name, amount)
    }

    fun suggest(sms: CreditSms, openDues: List<DueRow>): Suggestion {
        val scored = openDues.map { it to score(sms, it) }
            .sortedWith(compareByDescending<Pair<DueRow, Score>> { it.second.total }.thenBy { it.first.due.invoiceDate })
        val top = scored.firstOrNull()?.takeIf { it.second.total > 0 }
        val confidence = top?.second?.let {
            when {
                it.name >= 0.5 && it.amount >= 0.8 -> Confidence.High
                it.name >= 0.5 -> Confidence.Medium
                else -> Confidence.Low
            }
        }
        return Suggestion(sms, top?.first, confidence, scored.map { it.first })
    }
}
