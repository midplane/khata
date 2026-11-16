// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.data

import java.time.LocalDate

data class AgingBucket(val label: String, val amount: Long, val count: Int)
data class TopClient(val clientId: Long, val name: String, val outstanding: Long, val openCount: Int)
data class ClientTotal(
    val client: Client,
    val outstanding: Long,
    val openCount: Int,
    val billed: Long,
    val received: Long,
)

data class Summary(
    val billed: Long,
    val received: Long,
    val outstanding: Long,
    val openCount: Int,
    val clientsWithDues: Int,
    val overdueAmount: Long,
    val overdueCount: Int,
    val receivedThisMonth: Long,
    val paymentsThisMonth: Int,
    val aging: List<AgingBucket>,
    val topClients: List<TopClient>,
    val recent: List<PaymentRow>,
) {
    val collectedFraction: Float get() = if (billed == 0L) 0f else received.toFloat() / billed

    companion object {
        fun build(dues: List<DueRow>, payments: List<PaymentRow>, today: LocalDate): Summary {
            val todayDay = today.toEpochDay()
            val open = dues.filter { !it.isPaid }
            val overdue = open.filter { it.isOverdue(todayDay) }
            val monthStart = today.withDayOfMonth(1).toEpochDay()
            val thisMonth = payments.filter { it.payment.receivedOn in monthStart..todayDay }

            // Aging by days since invoice date.
            val ranges = listOf("0–30 days" to 0L..30L, "31–60 days" to 31L..60L, "61–90 days" to 61L..90L, "90+ days" to 91L..Long.MAX_VALUE)
            val aging = ranges.map { (label, range) ->
                val inBucket = open.filter { (todayDay - it.due.invoiceDate).coerceAtLeast(0) in range }
                AgingBucket(label, inBucket.sumOf { it.outstanding }, inBucket.size)
            }

            val top = open.groupBy { it.due.clientId }
                .map { (id, rows) -> TopClient(id, rows.first().clientName, rows.sumOf { it.outstanding }, rows.size) }
                .sortedByDescending { it.outstanding }
                .take(5)

            return Summary(
                billed = dues.sumOf { it.due.amount },
                received = dues.sumOf { minOf(it.paid, it.due.amount) },
                outstanding = open.sumOf { it.outstanding },
                openCount = open.size,
                clientsWithDues = open.map { it.due.clientId }.distinct().size,
                overdueAmount = overdue.sumOf { it.outstanding },
                overdueCount = overdue.size,
                receivedThisMonth = thisMonth.sumOf { it.payment.amount },
                paymentsThisMonth = thisMonth.size,
                aging = aging,
                topClients = top,
                recent = payments.take(5),
            )
        }
    }
}
