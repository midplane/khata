// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.activity.result.contract.ActivityResultContract

data class PickedContact(val name: String, val phone: String?)

/**
 * Opens the system phone-number picker. The picker grants temporary read access to the chosen
 * row, so no READ_CONTACTS permission is needed.
 */
class PickPhoneContact : ActivityResultContract<Unit, PickedContact?>() {
    private lateinit var appContext: Context

    override fun createIntent(context: Context, input: Unit): Intent {
        appContext = context.applicationContext
        return Intent(Intent.ACTION_PICK, Phone.CONTENT_URI)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): PickedContact? {
        val uri = intent?.data ?: return null
        if (resultCode != Activity.RESULT_OK) return null
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0)?.trim().orEmpty()
                val phone = c.getString(1)?.let(::cleanPhone)
                if (name.isEmpty() && phone == null) null else PickedContact(name.ifEmpty { phone!! }, phone)
            }
        }.getOrNull()
    }

    private fun cleanPhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty()) return null
        // Store Indian mobiles as plain 10 digits; keep other formats as-is.
        val d = digits.removePrefix("+")
        return when {
            d.length == 12 && d.startsWith("91") -> d.drop(2)
            d.length == 11 && d.startsWith("0") -> d.drop(1)
            else -> digits
        }
    }
}
