// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.data

import java.time.LocalDate

/**
 * Builds the whole khata as a tab-separated ledger that opens directly in Excel / Sheets.
 * One row per bill and per payment, grouped by client in date order, with a running client balance.
 * Amounts are plain rupee numbers (no ₹ or grouping) and dates are ISO so spreadsheets parse them.
 */
object LedgerExport {
    val headers = listOf(
        "Date", "Org", "Client", "Phone", "Entry", "Work", "Billed", "Received",
        "Client balance", "Due by", "Status", "Note", "Source",
    )

    fun tsv(dues: List<DueRow>, payments: List<PaymentRow>, today: Long = LocalDate.now().toEpochDay()): String {
        val paymentsByDue = payments.groupBy { it.payment.dueId }
        val sb = StringBuilder()
        sb.appendRow(headers)
        // Balances run per client within each org.
        dues.groupBy { it.due.orgId to it.due.clientId }.values
            .sortedWith(compareBy({ it.first().orgName.orEmpty().lowercase() }, { it.first().clientName.lowercase() }))
            .forEach { clientDues ->
                data class Entry(val day: Long, val order: Int, val cells: (Long) -> List<String>, val delta: Long)
                val entries = mutableListOf<Entry>()
                clientDues.forEach { d ->
                    val status = when {
                        d.isPaid -> "Paid"
                        d.isOverdue(today) -> "Overdue"
                        d.paid > 0 -> "Partial"
                        else -> "Open"
                    }
                    entries += Entry(d.due.invoiceDate, 0, { bal ->
                        listOf(
                            iso(d.due.invoiceDate), d.orgName.orEmpty(), d.clientName, d.clientPhone.orEmpty(), "Bill", d.due.title,
                            rupees(d.due.amount), "", rupees(bal), d.due.dueDate?.let(::iso).orEmpty(), status,
                            d.due.note.orEmpty(), "",
                        )
                    }, d.due.amount)
                    paymentsByDue[d.due.id].orEmpty().forEach { p ->
                        entries += Entry(p.payment.receivedOn, 1, { bal ->
                            listOf(
                                iso(p.payment.receivedOn), d.orgName.orEmpty(), d.clientName, d.clientPhone.orEmpty(), "Payment", d.due.title,
                                "", rupees(p.payment.amount), rupees(bal), "", "", p.payment.note.orEmpty(),
                                if (p.payment.smsKey != null) "SMS" else "Manual",
                            )
                        }, -p.payment.amount)
                    }
                }
                var balance = 0L
                entries.sortedWith(compareBy({ it.day }, { it.order })).forEach { e ->
                    balance += e.delta
                    sb.appendRow(e.cells(balance))
                }
            }
        return sb.toString()
    }

    private fun StringBuilder.appendRow(cells: List<String>) {
        cells.joinTo(this, "\t") { it.replace(Regex("[\t\r\n]+"), " ").trim() }
        append("\r\n")
    }

    private fun iso(day: Long) = LocalDate.ofEpochDay(day).toString()

    fun rupees(paise: Long): String = paise.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString()
}
