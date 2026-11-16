// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata

import android.app.Application
import dev.midplane.khata.data.KhataDatabase

class KhataApp : Application() {
    val db: KhataDatabase by lazy { KhataDatabase.build(this) }
}
