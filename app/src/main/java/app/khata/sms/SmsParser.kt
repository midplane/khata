package app.khata.sms

import java.math.RoundingMode

data class RawSms(val address: String, val body: String, val date: Long)

/** A bank/UPI SMS that looks like money was credited to the user. */
data class CreditSms(
    val key: String,
    val sender: String,
    val body: String,
    val date: Long,
    val amount: Long,
    val payer: String?,
)

object SmsParser {
    private val amountRx = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val creditRx = Regex("""(?i)\b(credited|received|deposited|credit of|added to)\b""")
    private val debitRx = Regex("""(?i)\b(debited|spent|withdrawn|paid to|sent to|transferred to|purchase|deducted)\b""")
    private val noiseRx = Regex(
        """(?i)(\botp\b|one[- ]time password|\brequest(ed)?\b|will be credited|\bis due\b|\bdue (on|by|date)\b|\bbill\b|cashback|\breward|\bloan\b|\bemi\b|\boffer\b|\brefund|pre-?approved|\bvoucher)"""
    )
    private val balanceContextRx = Regex("""(?i)(bal|balance|limit|avl)[^0-9]{0,12}$""")

    private val vpaRx = Regex("""(?i)\b(?:from|by)\s+(?:vpa\s+)?([a-z0-9._\-]{2,}@[a-z]{2,})""")
    private val nameRx = Regex(
        """(?i)\b(?:from|by)\s+(?!(?:a/?c|ac|acct|account|your|vpa|upi|neft|imps|rtgs|bank|you)\b)""" +
            """([a-z][a-z.&' ]{1,40}?)""" +
            """(?=\s*(?:\(|,|;|\.(?:\s|$)|-|$|\s(?:on|via|ref|refno|upi|imps|neft|rtgs|a/c|ac|info|thru|through|at|towards|in|is|has|for|avl|total)\b))"""
    )
    private val transferRx = Regex("""(?i)\b(?:neft|imps|rtgs|upi)[/:\- ]+[a-z0-9]*\d[a-z0-9]*[/:\-]+([a-z][a-z .&]{2,40}?)[/:\-]""")

    fun parse(sms: RawSms): CreditSms? {
        val body = sms.body
        if (!creditRx.containsMatchIn(body)) return null
        if (debitRx.containsMatchIn(body) || noiseRx.containsMatchIn(body)) return null

        val amount = amountRx.findAll(body)
            .firstOrNull { m -> !balanceContextRx.containsMatchIn(body.substring(maxOf(0, m.range.first - 20), m.range.first)) }
            ?.groupValues?.get(1)
            ?.let(::toPaise)
            ?: return null
        if (amount <= 0) return null

        return CreditSms(
            key = keyOf(sms),
            sender = sms.address,
            body = body,
            date = sms.date,
            amount = amount,
            payer = extractPayer(body),
        )
    }

    fun keyOf(sms: RawSms): String = "${sms.date}:${sms.address}:${sms.body.hashCode()}"

    fun extractPayer(body: String): String? {
        transferRx.find(body)?.groupValues?.get(1)?.let(::cleanName)?.let { return it }
        nameRx.find(body)?.groupValues?.get(1)?.let(::cleanName)?.let { return it }
        vpaRx.find(body)?.groupValues?.get(1)?.let { return it.lowercase() }
        return null
    }

    private fun cleanName(raw: String): String? {
        val name = raw.trim().trimEnd('.').replace(Regex("\\s+"), " ")
        if (name.length < 3) return null
        return name.split(' ').joinToString(" ") { w ->
            w.lowercase().replaceFirstChar { it.uppercase() }
        }
    }

    private fun toPaise(s: String): Long? = s.replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)?.setScale(0, RoundingMode.HALF_UP)?.toLong()

}
