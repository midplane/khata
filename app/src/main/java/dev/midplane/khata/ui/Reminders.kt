// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.net.toUri

object Reminders {
    fun text(clientName: String, amount: Long, forWhat: String?, orgName: String? = null): String = buildString {
        append("Dear $clientName,\n\nThis is a gentle reminder that ${Fmt.money(amount)} is pending")
        if (!forWhat.isNullOrBlank()) append(" towards $forWhat")
        append(". Kindly arrange the payment at the earliest.\n\nThank you.")
        if (!orgName.isNullOrBlank()) append("\n$orgName")
    }

    private fun normalized(phone: String): String {
        val digits = phone.filter(Char::isDigit)
        return if (digits.length == 10) "91$digits" else digits
    }

    fun whatsApp(context: Context, phone: String, text: String) = launch(context,
        Intent(Intent.ACTION_VIEW, "https://wa.me/${normalized(phone)}?text=${Uri.encode(text)}".toUri()))

    fun sms(context: Context, phone: String, text: String) = launch(context,
        Intent(Intent.ACTION_SENDTO, "smsto:$phone".toUri()).putExtra("sms_body", text))

    fun call(context: Context, phone: String) = launch(context, Intent(Intent.ACTION_DIAL, "tel:$phone".toUri()))

    fun share(context: Context, text: String) = launch(context,
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Send reminder"))

    private fun launch(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app available for this", Toast.LENGTH_SHORT).show()
        }
    }
}
