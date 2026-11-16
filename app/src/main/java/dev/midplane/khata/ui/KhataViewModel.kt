// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.midplane.khata.KhataApp
import dev.midplane.khata.data.Client
import dev.midplane.khata.data.ClientTotal
import dev.midplane.khata.data.DismissedSms
import dev.midplane.khata.data.Due
import dev.midplane.khata.data.DueRow
import dev.midplane.khata.data.LedgerExport
import dev.midplane.khata.data.Org
import dev.midplane.khata.data.Payment
import dev.midplane.khata.data.Summary
import dev.midplane.khata.data.isPaid
import dev.midplane.khata.data.outstanding
import dev.midplane.khata.sms.Confidence
import dev.midplane.khata.sms.CreditSms
import dev.midplane.khata.sms.SmsMatcher
import dev.midplane.khata.sms.SmsParser
import dev.midplane.khata.sms.SmsReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit

enum class DueFilter(val label: String) { Open("Open"), Overdue("Overdue"), Paid("Paid"), All("All") }

class KhataViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = (app as KhataApp).db.dao()

    private val prefs = app.getSharedPreferences("khata", Context.MODE_PRIVATE)

    val clients: StateFlow<List<Client>> =
        dao.clients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // ---- Organisations ----

    val orgs: StateFlow<List<Org>> =
        dao.orgs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val selectedOrgId = MutableStateFlow(prefs.getLong(KEY_ORG, -1L).takeIf { it > 0 })

    /** The org the app is currently scoped to; null = all orgs. */
    val activeOrg: StateFlow<Org?> = combine(orgs, selectedOrgId) { os, id -> os.find { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun selectOrg(id: Long?) {
        selectedOrgId.value = id
        prefs.edit { putLong(KEY_ORG, id ?: -1L) }
    }

    fun addOrg(name: String, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        onDone(dao.insertOrg(Org(name = name.trim())))
    }

    fun renameOrg(org: Org, name: String) = viewModelScope.launch { dao.updateOrg(org.copy(name = name.trim())) }

    fun deleteOrg(org: Org) = viewModelScope.launch {
        dao.deleteOrg(org.id)
        if (selectedOrgId.value == org.id) selectOrg(null)
    }

    // ---- Dues & payments ----

    private val allRowsOrNull: StateFlow<List<DueRow>?> =
        dao.dueRows().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** False until the first database read completes — avoids flashing empty states. */
    val loaded: StateFlow<Boolean> =
        allRowsOrNull.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Every due regardless of the selected org — for detail/edit screens and SMS matching. */
    val allDues: StateFlow<List<DueRow>> =
        allRowsOrNull.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val rowsOrNull: StateFlow<List<DueRow>?> = combine(allRowsOrNull, activeOrg) { d, org ->
        if (org == null) d else d?.filter { it.due.orgId == org.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Dues in the selected org (all dues when "All orgs"). */
    val dues: StateFlow<List<DueRow>> =
        rowsOrNull.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val allPayments = dao.paymentRows().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments = combine(allPayments, activeOrg) { p, org ->
        if (org == null) p else p.filter { it.orgId == org.id }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<Summary?> = combine(rowsOrNull, payments) { d, p ->
        d?.let { Summary.build(it, p, LocalDate.now()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clientTotals: StateFlow<List<ClientTotal>> = combine(clients, dues, activeOrg) { cs, ds, org ->
        val byClient = ds.groupBy { it.due.clientId }
        cs.filter { org == null || it.id in byClient }.map { c ->
            val rows = byClient[c.id].orEmpty()
            ClientTotal(
                client = c,
                outstanding = rows.sumOf { it.outstanding },
                openCount = rows.count { !it.isPaid },
                billed = rows.sumOf { it.due.amount },
                received = rows.sumOf { minOf(it.paid, it.due.amount) },
            )
        }.sortedWith(compareByDescending<ClientTotal> { it.outstanding }.thenBy { it.client.name.lowercase() })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _dueFilter = MutableStateFlow(DueFilter.Open)
    val dueFilter = _dueFilter.asStateFlow()
    fun setDueFilter(f: DueFilter) { _dueFilter.value = f }

    /** Org to pre-select for a new due: the active org, else the client's most recent one, else the only org. */
    fun defaultOrgFor(clientName: String?): Long? {
        activeOrg.value?.let { return it.id }
        val os = orgs.value
        if (!clientName.isNullOrBlank()) {
            allDues.value.filter { it.clientName.equals(clientName.trim(), true) && it.due.orgId != null }
                .maxByOrNull { it.due.createdAt }?.let { last -> if (os.any { it.id == last.due.orgId }) return last.due.orgId }
        }
        return os.singleOrNull()?.id
    }

    // ---- Mutations ----

    fun addDue(
        clientName: String, phone: String?, orgId: Long?, title: String, amount: Long,
        invoiceDate: Long, dueDate: Long?, note: String?, onDone: (Long) -> Unit,
    ) = viewModelScope.launch {
        val id = dao.addDue(
            clientName.trim(), phone?.trim()?.ifBlank { null },
            Due(clientId = 0, title = title.trim(), amount = amount, invoiceDate = invoiceDate,
                dueDate = dueDate, note = note?.trim()?.ifBlank { null }, orgId = orgId),
        )
        onDone(id)
    }

    fun updateDue(
        existing: Due, clientName: String, phone: String?, orgId: Long?, title: String, amount: Long,
        invoiceDate: Long, dueDate: Long?, note: String?, onDone: (Long) -> Unit,
    ) = viewModelScope.launch {
        dao.editDue(
            clientName.trim(), phone?.trim()?.ifBlank { null },
            existing.copy(title = title.trim(), amount = amount, invoiceDate = invoiceDate,
                dueDate = dueDate, note = note?.trim()?.ifBlank { null }, orgId = orgId),
        )
        onDone(existing.id)
    }

    fun recordPayment(dueId: Long, amount: Long, day: Long, note: String?, smsKey: String? = null) =
        viewModelScope.launch {
            dao.insertPayment(Payment(dueId = dueId, amount = amount, receivedOn = day,
                note = note?.trim()?.ifBlank { null }, smsKey = smsKey))
        }

    /** Ledger for the selected org (or everything when "All orgs"). */
    fun ledgerTsv(): String = LedgerExport.tsv(dues.value, payments.value)
    fun ledgerFileName(): String = Exporter.fileName(activeOrg.value?.name)

    fun deletePayment(id: Long) = viewModelScope.launch { dao.deletePayment(id) }
    fun deleteDue(id: Long) = viewModelScope.launch { dao.deleteDue(id) }
    fun updateClient(client: Client) = viewModelScope.launch { dao.updateClient(client) }
    fun deleteClient(client: Client) = viewModelScope.launch { dao.deleteClient(client) }

    // ---- SMS suggestions ----

    private val credits = MutableStateFlow<List<CreditSms>>(emptyList())
    private val _scanning = MutableStateFlow(false)
    val scanning = _scanning.asStateFlow()
    private val _scannedOnce = MutableStateFlow(false)
    val scannedOnce = _scannedOnce.asStateFlow()

    val suggestions = combine(credits, allDues, dao.dismissedSmsKeys(), dao.usedSmsKeys()) { cr, ds, dismissed, used ->
        val hidden = dismissed.toHashSet().apply { addAll(used) }
        val open = ds.filter { !it.isPaid }
        cr.filter { it.key !in hidden }
            .map { SmsMatcher.suggest(it, open) }
            .sortedWith(
                compareBy<dev.midplane.khata.sms.Suggestion> { it.confidence?.ordinal ?: Confidence.entries.size }
                    .thenByDescending { it.sms.date }
            )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun scanSms() {
        val ctx = getApplication<Application>()
        if (!SmsReader.hasPermission(ctx) || _scanning.value) return
        viewModelScope.launch {
            _scanning.value = true
            try {
                val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(SMS_LOOKBACK_DAYS)
                credits.value = withContext(Dispatchers.IO) {
                    SmsReader.readInbox(ctx, since).mapNotNull(SmsParser::parse)
                }
            } finally {
                _scanning.value = false
                _scannedOnce.value = true
            }
        }
    }

    fun dismissSms(key: String) = viewModelScope.launch { dao.dismissSms(DismissedSms(key)) }

    companion object {
        const val SMS_LOOKBACK_DAYS = 90L
        private const val KEY_ORG = "selected_org"
    }
}
