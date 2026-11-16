package app.khata.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Contacts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.khata.data.Client
import app.khata.data.isPaid
import app.khata.data.outstanding
import app.khata.ui.Avatar
import app.khata.ui.ConfirmDialog
import app.khata.ui.DueListItem
import app.khata.ui.EmptyState
import app.khata.ui.Fmt
import app.khata.ui.KhataViewModel
import app.khata.ui.PickPhoneContact
import app.khata.ui.ReminderButton
import app.khata.ui.Reminders

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(vm: KhataViewModel, onOpenClient: (Long) -> Unit, onAddDue: () -> Unit) {
    val totals by vm.clientTotals.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val list = remember(totals, query) {
        if (query.isBlank()) totals else totals.filter { it.client.name.contains(query, true) || it.client.phone.orEmpty().contains(query) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Clients") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAddDue) { Icon(Icons.Outlined.Add, "New due") } },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            if (!loaded) return@Column
            if (totals.isEmpty()) {
                EmptyState(Icons.Outlined.People, "No clients yet", "Clients are added automatically when you record a due for them.")
                return@Column
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search clients") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(list, key = { it.client.id }) { t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenClient(t.client.id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(t.client.name)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.client.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                if (t.openCount == 0) "All settled" else "${t.openCount} open ${if (t.openCount == 1) "due" else "dues"}",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            Fmt.money(t.outstanding), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                            color = if (t.outstanding == 0L) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(vm: KhataViewModel, clientId: Long, onBack: () -> Unit, onOpenDue: (Long) -> Unit, onAddDue: () -> Unit) {
    val clients by vm.clients.collectAsStateWithLifecycle()
    val dues by vm.dues.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val client = clients.find { it.id == clientId }
    val mine = remember(dues, clientId) {
        dues.filter { it.due.clientId == clientId }
            .sortedWith(compareBy({ it.isPaid }, { it.due.dueDate ?: Long.MAX_VALUE }))
    }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    var seen by remember { mutableStateOf(false) }
    if (client != null) seen = true
    LaunchedEffect(loaded, client, seen) { if (loaded && client == null && seen) onBack() }
    if (client == null) return

    val open = mine.filter { !it.isPaid }
    val outstanding = open.sumOf { it.outstanding }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(client.name, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = { editing = true }) { Icon(Icons.Outlined.Edit, "Edit client") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete client") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddDue, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New due") })
        },
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Outstanding", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Fmt.money(outstanding), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Billed ${Fmt.money(mine.sumOf { it.due.amount })} · Received ${Fmt.money(mine.sumOf { minOf(it.paid, it.due.amount) })}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (outstanding > 0) {
                                ReminderButton(client.phone, text = {
                                    Reminders.text(client.name, outstanding, open.joinToString(", ") { it.due.title }.takeIf { open.size <= 3 })
                                })
                            }
                            if (!client.phone.isNullOrBlank()) {
                                OutlinedButton(onClick = { Reminders.call(ctx, client.phone) }) {
                                    Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(8.dp)); Text(client.phone)
                                }
                            } else {
                                TextButton(onClick = { editing = true }) { Text("Add mobile number") }
                            }
                        }
                    }
                }
            }
            if (mine.isEmpty()) {
                item {
                    Text(
                        "No dues for this client yet.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(mine, key = { it.due.id }) { row ->
                DueListItem(row, onClick = { onOpenDue(row.due.id) }, showClient = false)
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    if (editing) EditClientDialog(client, onDismiss = { editing = false }) { vm.updateClient(it); editing = false }
    if (confirmDelete) {
        ConfirmDialog(
            "Delete ${client.name}?", "All ${mine.size} dues and their payments for this client will be deleted. This can't be undone.",
            "Delete", onDismiss = { confirmDelete = false }, onConfirm = { vm.deleteClient(client) },
        )
    }
}

@Composable
private fun EditClientDialog(client: Client, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by rememberSaveable { mutableStateOf(client.name) }
    var phone by rememberSaveable { mutableStateOf(client.phone.orEmpty()) }
    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { picked ->
        picked?.phone?.let { phone = it }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit client") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    phone, { s -> phone = s.filter { it.isDigit() || it == '+' || it == ' ' } }, label = { Text("Mobile") },
                    trailingIcon = {
                        IconButton(onClick = { pickContact.launch(Unit) }) { Icon(Icons.Outlined.Contacts, "Pick from contacts") }
                    },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                onSave(client.copy(name = name.trim(), phone = phone.trim().ifBlank { null }))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
