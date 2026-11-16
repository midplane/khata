package app.khata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.khata.data.PaymentRow
import app.khata.data.isOverdue
import app.khata.data.isPaid
import app.khata.data.outstanding
import app.khata.ui.ConfirmDialog
import app.khata.ui.DueStatusPill
import app.khata.ui.Fmt
import app.khata.ui.KhataViewModel
import app.khata.ui.Pill
import app.khata.ui.RecordPaymentDialog
import app.khata.ui.ReminderButton
import app.khata.ui.Reminders
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDetailScreen(vm: KhataViewModel, dueId: Long, onBack: () -> Unit, onOpenClient: (Long) -> Unit) {
    val dues by vm.dues.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val allPayments by vm.payments.collectAsStateWithLifecycle()
    val row = dues.find { it.due.id == dueId }
    val payments = remember(allPayments, dueId) { allPayments.filter { it.payment.dueId == dueId } }

    var menu by remember { mutableStateOf(false) }
    var showRecord by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletePayment by remember { mutableStateOf<PaymentRow?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // A just-created due may not be in the list yet, so only leave once we've seen it disappear (deleted).
    var seen by remember { mutableStateOf(false) }
    if (row != null) seen = true
    LaunchedEffect(loaded, row, seen) { if (loaded && row == null && seen) onBack() }
    if (row == null) return
    val today = Fmt.today()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(row.clientName, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("View client") }, leadingIcon = { Icon(Icons.Outlined.Person, null) },
                            onClick = { menu = false; onOpenClient(row.due.clientId) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete due") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { menu = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.due.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            DueStatusPill(row, today)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(if (row.isPaid) "Fully received" else "Outstanding", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            Fmt.money(if (row.isPaid) row.due.amount else row.outstanding),
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold,
                            color = if (row.isPaid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { (row.paid.toFloat() / row.due.amount).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            drawStopIndicator = {},
                        )
                        Spacer(Modifier.height(12.dp))
                        InfoRow("Billed", Fmt.money(row.due.amount))
                        InfoRow("Received", Fmt.money(row.paid))
                        InfoRow("Billed on", Fmt.day(row.due.invoiceDate))
                        row.due.dueDate?.let {
                            InfoRow(
                                "Due by", Fmt.day(it) + if (!row.isPaid) "  ·  " + Fmt.dueRelative(it, today) else "",
                                highlight = row.isOverdue(today),
                            )
                        }
                        row.due.note?.let { InfoRow("Note", it) }
                    }
                }
            }
            if (!row.isPaid) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val amt = row.outstanding
                                vm.recordPayment(row.due.id, amt, today, null)
                                scope.launch { snackbar.showSnackbar("Marked as paid · ${Fmt.money(amt)} received") }
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Received in full")
                        }
                        OutlinedButton(onClick = { showRecord = true }, modifier = Modifier.weight(1f)) { Text("Part payment") }
                    }
                }
                item {
                    ReminderButton(
                        row.clientPhone,
                        text = { Reminders.text(row.clientName, row.outstanding, row.due.title) },
                    )
                }
            }
            item {
                Text(
                    if (payments.isEmpty()) "No payments yet" else "Payments (${payments.size})",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(payments, key = { it.payment.id }) { p ->
                ListItem(
                    headlineContent = { Text(Fmt.money(p.payment.amount), fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Text(listOfNotNull(Fmt.day(p.payment.receivedOn), p.payment.note).joinToString(" · "), maxLines = 2)
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (p.payment.smsKey != null) {
                                Pill("SMS", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                            IconButton(onClick = { deletePayment = p }) { Icon(Icons.Outlined.Delete, "Delete payment") }
                        }
                    },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (showRecord) {
        RecordPaymentDialog(
            title = "Record payment",
            outstanding = row.outstanding,
            initialAmount = row.outstanding,
            onDismiss = { showRecord = false },
            onConfirm = { amt, day, note ->
                showRecord = false
                vm.recordPayment(row.due.id, amt, day, note)
            },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete this due?", "This removes ${row.due.title} for ${row.clientName} along with its recorded payments.", "Delete",
            onDismiss = { confirmDelete = false }, onConfirm = { vm.deleteDue(row.due.id) },
        )
    }
    deletePayment?.let { p ->
        ConfirmDialog(
            "Delete payment?", "${Fmt.money(p.payment.amount)} received on ${Fmt.day(p.payment.receivedOn)} will be removed and added back to outstanding.",
            "Delete", onDismiss = { deletePayment = null }, onConfirm = { vm.deletePayment(p.payment.id) },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(96.dp))
        Text(
            value, style = MaterialTheme.typography.bodyMedium,
            color = if (highlight) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
    }
}
