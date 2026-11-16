package app.khata.sms

import app.khata.data.Due
import app.khata.data.DueRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    private fun parse(body: String) = SmsParser.parse(RawSms("AX-HDFCBK", body, 1_763_000_000_000))

    @Test fun upiCredit() {
        val c = parse("Rs.25,000.00 credited to a/c XX1234 on 12-11-25 by VPA rahul.sharma@okhdfcbank (UPI Ref No 531234567890). -HDFC Bank")
        assertNotNull(c)
        assertEquals(2_500_000L, c!!.amount)
        assertEquals("rahul.sharma@okhdfcbank", c.payer)
    }

    @Test fun neftCreditWithName() {
        val c = parse("Dear Customer, INR 1,50,000.00 credited to your A/c No XX5678 on 10/11/2025 through NEFT with UTR SBIN125314 by MEHTA TRADERS PVT LTD, Avl Bal INR 3,20,000.00")
        assertNotNull(c)
        assertEquals(15_000_000L, c!!.amount)
        assertEquals("Mehta Traders Pvt Ltd", c.payer)
    }

    @Test fun receivedFrom() {
        val c = parse("You have received Rs 5000 from PRIYA NAIR on 14-Nov. UPI Ref 1234")
        assertEquals(500_000L, c!!.amount)
        assertEquals("Priya Nair", c.payer)
    }

    @Test fun ignoresDebitsOtpsAndRequests() {
        assertNull(parse("Rs.500 debited from A/c XX12 and credited to bob@upi. UPI Ref 123"))
        assertNull(parse("123456 is your OTP. Amount Rs 500 will be credited"))
        assertNull(parse("ACME has requested Rs 2000 from you on UPI. Pay now"))
        assertNull(parse("Your electricity bill of Rs 1200 is due on 20 Nov"))
    }

    @Test fun skipsBalanceAmount() {
        val c = parse("Avl Bal Rs 90,000. A/c XX12 credited with Rs 12,500 by IMPS")
        assertEquals(1_250_000L, c!!.amount)
    }

    @Test fun matcherPrefersNameAndAmount() {
        val sms = parse("INR 1,50,000.00 credited to your A/c XX5678 by MEHTA TRADERS PVT LTD via NEFT")!!
        fun row(id: Long, name: String, amount: Long) =
            DueRow(Due(id = id, clientId = id, title = "Fees", amount = amount, invoiceDate = 0, dueDate = null), name, null, 0)
        val s = SmsMatcher.suggest(sms, listOf(row(1, "Kapoor & Sons", 15_000_000), row(2, "Mehta Traders", 15_000_000), row(3, "Mehta Traders", 9_000_000)))
        assertEquals(2L, s.best!!.due.id)
        assertEquals(Confidence.High, s.confidence)
    }
}
