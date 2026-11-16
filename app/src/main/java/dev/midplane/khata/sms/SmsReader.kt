// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

object SmsReader {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Reads inbox messages newer than [sinceMillis]. Everything stays on-device. */
    fun readInbox(context: Context, sinceMillis: Long, limit: Int = 1000): List<RawSms> {
        if (!hasPermission(context)) return emptyList()
        val result = mutableListOf<RawSms>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { c ->
            val iAddr = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val iBody = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val iDate = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (c.moveToNext() && result.size < limit) {
                result += RawSms(
                    address = c.getString(iAddr).orEmpty(),
                    body = c.getString(iBody).orEmpty(),
                    date = c.getLong(iDate),
                )
            }
        }
        return result
    }
}
