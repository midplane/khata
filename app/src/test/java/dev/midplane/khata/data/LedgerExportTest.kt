// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.data

import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerExportTest {
    @Test fun ledgerRowsWithRunningBalance() {
        val due = Due(id = 1, clientId = 7, title = "Audit\tFY26", amount = 5_000_000, invoiceDate = 20_000, dueDate = 20_030, note = "line1\nline2")
        val dues = listOf(DueRow(due.copy(orgId = 3), "Priya Nair", "9876543210", 2_000_050, orgName = "Sharma & Co"))
        val pays = listOf(PaymentRow(Payment(id = 1, dueId = 1, amount = 2_000_050, receivedOn = 20_010, smsKey = "k"), "Audit", "Priya Nair", 7))
        val lines = LedgerExport.tsv(dues, pays, today = 20_040).trimEnd().split("\r\n")
        assertEquals(3, lines.size)
        assertEquals(LedgerExport.headers.size, lines[1].split("\t").size)
        val bill = lines[1].split("\t")
        assertEquals("Sharma & Co", bill[1]); assertEquals("Audit FY26", bill[5]); assertEquals("50000", bill[6]); assertEquals("50000", bill[8])
        assertEquals("Overdue", bill[10]); assertEquals("line1 line2", bill[11])
        val pay = lines[2].split("\t")
        assertEquals("Payment", pay[4]); assertEquals("20000.5", pay[7]); assertEquals("29999.5", pay[8]); assertEquals("SMS", pay[12])
    }
}
