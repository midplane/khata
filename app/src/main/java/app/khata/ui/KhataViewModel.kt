package app.khata.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.khata.KhataApp
import app.khata.data.Client
import app.khata.data.ClientTotal
import app.khata.data.DismissedSms
import app.khata.data.Due
import app.khata.data.DueRow
import app.khata.data.LedgerExport
import app.khata.data.Payment
import app.khata.data.Summary
import app.khata.data.isPaid
import app.khata.data.outstanding
import app.khata.sms.Confidence
import app.khata.sms.CreditSms
import app.khata.sms.SmsMatcher
import app.khata.sms.SmsParser
import app.khata.sms.SmsReader
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

    val clients: StateFlow<List<Client>> =
        dao.clients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val dueRowsOrNull: StateFlow<List<DueRow>?> =
        dao.dueRows().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** False until the first database read completes — avoids flashing empty states. */
    val loaded: StateFlow<Boolean> =
        dueRowsOrNull.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val dues: StateFlow<List<DueRow>> =
        dueRowsOrNull.map { it.orEmpty() }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val payments = dao.paymentRows().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val summary: StateFlow<Summary?> = combine(dueRowsOrNull, payments) { d, p ->
        d?.let { Summary.build(it, p, LocalDate.now()) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val clientTotals: StateFlow<List<ClientTotal>> = combine(clients, dues) { cs, ds ->
        val byClient = ds.groupBy { it.due.clientId }
        cs.map { c ->
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

    // ---- Mutations ----

    fun addDue(
        clientName: String, phone: String?, title: String, amount: Long,
        invoiceDate: Long, dueDate: Long?, note: String?, onDone: (Long) -> Unit,
    ) = viewModelScope.launch {
        val id = dao.addDue(
            clientName.trim(), phone?.trim()?.ifBlank { null },
            Due(clientId = 0, title = title.trim(), amount = amount, invoiceDate = invoiceDate,
                dueDate = dueDate, note = note?.trim()?.ifBlank { null }),
        )
        onDone(id)
    }

    fun recordPayment(dueId: Long, amount: Long, day: Long, note: String?, smsKey: String? = null) =
        viewModelScope.launch {
            dao.insertPayment(Payment(dueId = dueId, amount = amount, receivedOn = day,
                note = note?.trim()?.ifBlank { null }, smsKey = smsKey))
        }

    fun ledgerTsv(): String = LedgerExport.tsv(dues.value, payments.value)

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

    val suggestions = combine(credits, dues, dao.dismissedSmsKeys(), dao.usedSmsKeys()) { cr, ds, dismissed, used ->
        val hidden = dismissed.toHashSet().apply { addAll(used) }
        val open = ds.filter { !it.isPaid }
        cr.filter { it.key !in hidden }
            .map { SmsMatcher.suggest(it, open) }
            .sortedWith(
                compareBy<app.khata.sms.Suggestion> { it.confidence?.ordinal ?: Confidence.entries.size }
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
    }
}
