// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Contacts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.midplane.khata.ui.AmountField
import dev.midplane.khata.ui.DateField
import dev.midplane.khata.ui.Fmt
import dev.midplane.khata.ui.KhataViewModel
import dev.midplane.khata.ui.OrgField
import dev.midplane.khata.ui.PickPhoneContact

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDueScreen(
    vm: KhataViewModel,
    presetClientId: Long?,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    editDueId: Long? = null,
) {
    val clients by vm.clients.collectAsStateWithLifecycle()
    val dues by vm.allDues.collectAsStateWithLifecycle()
    val orgs by vm.orgs.collectAsStateWithLifecycle()
    val editing = editDueId?.let { id -> dues.find { it.due.id == id } }
    val isEdit = editDueId != null

    var clientName by rememberSaveable { mutableStateOf("") }
    var presetApplied by rememberSaveable { mutableStateOf(false) }
    var phone by rememberSaveable { mutableStateOf("") }
    var title by rememberSaveable { mutableStateOf("") }
    var amountText by rememberSaveable { mutableStateOf("") }
    var invoiceDate by rememberSaveable { mutableLongStateOf(Fmt.today()) }
    var dueDate by rememberSaveable { mutableStateOf<Long?>(null) }
    var dueOffset by rememberSaveable { mutableStateOf<Int?>(null) }
    var note by rememberSaveable { mutableStateOf("") }
    var orgId by rememberSaveable { mutableStateOf<Long?>(null) }
    var orgTouched by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var triedSave by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }

    val pickContact = rememberLauncherForActivityResult(PickPhoneContact()) { picked ->
        if (picked != null) {
            clientName = picked.name
            picked.phone?.let { phone = it }
            expanded = false
        }
    }

    var editLoaded by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(editing) {
        if (!editLoaded && editing != null) {
            val d = editing.due
            clientName = editing.clientName
            title = d.title
            amountText = Fmt.amountInput(d.amount)
            invoiceDate = d.invoiceDate
            dueDate = d.dueDate
            note = d.note.orEmpty()
            orgId = d.orgId
            orgTouched = true
            editLoaded = true
        }
    }

    LaunchedEffect(clients, presetClientId) {
        if (!presetApplied && presetClientId != null) {
            clients.find { it.id == presetClientId }?.let { clientName = it.name; presetApplied = true }
        }
    }

    // New dues default to the active org / the client's last org, until the user picks one.
    LaunchedEffect(orgs, clientName, orgTouched) {
        if (!isEdit && !orgTouched) orgId = vm.defaultOrgFor(clientName)
    }

    val existing = clients.find { it.name.equals(clientName.trim(), ignoreCase = true) }
    val matches = if (clientName.isBlank()) clients.take(6)
    else clients.filter { it.name.contains(clientName.trim(), true) && !it.name.equals(clientName.trim(), true) }.take(6)

    val amount = Fmt.parseAmount(amountText)
    val nameError = triedSave && clientName.isBlank()
    val alreadyPaid = editing?.paid ?: 0L
    val amountError = when {
        amountText.isNotBlank() && amount == null -> "Enter a valid amount"
        triedSave && amount == null -> "Required"
        amount != null && amount < alreadyPaid -> "Already received ${Fmt.money(alreadyPaid)} — can't be less"
        else -> null
    }

    fun save() {
        triedSave = true
        if (clientName.isBlank() || amount == null || amountError != null || saving) return
        if (isEdit && editing == null) return
        saving = true
        if (editing != null) {
            vm.updateDue(
                editing.due, clientName, phone, orgId, title.ifBlank { "Professional fees" },
                amount, invoiceDate, dueDate, note, onDone = onSaved,
            )
        } else {
            vm.addDue(
                clientName, phone, orgId, title.ifBlank { "Professional fees" },
                amount, invoiceDate, dueDate, note, onDone = onSaved,
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit due" else "New due") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { TextButton(onClick = ::save) { Text("Save") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OrgField(vm, orgId, { orgId = it; orgTouched = true }, Modifier.fillMaxWidth())
            ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = clientName,
                    onValueChange = { clientName = it; expanded = true },
                    label = { Text("Client") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = {
                        when {
                            nameError -> Text("Required")
                            clientName.isNotBlank() && existing == null -> Text("New client — will be added")
                            editing != null && existing != null && existing.id != editing.due.clientId ->
                                Text("Due will move to ${existing.name}")
                        }
                    },
                    trailingIcon = {
                        IconButton(onClick = { pickContact.launch(Unit) }) {
                            Icon(Icons.Outlined.Contacts, "Pick from contacts")
                        }
                    },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
                    matches.forEach { c ->
                        DropdownMenuItem(text = { Text(c.name) }, onClick = { clientName = c.name; expanded = false })
                    }
                }
            }
            if (existing == null || (existing.phone.isNullOrBlank() && phone.isNotBlank())) {
                OutlinedTextField(
                    value = phone, onValueChange = { s -> phone = s.filter { it.isDigit() || it == '+' || it == ' ' } },
                    label = { Text("Client mobile (optional)") },
                    supportingText = { Text("Used for reminders and to match SMS payments") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("For (work / invoice)") },
                placeholder = { Text("e.g. ITR filing FY 2025-26") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            AmountField(amountText, { amountText = it }, "Amount due", Modifier.fillMaxWidth(), amountError)
            DateField("Billed on", invoiceDate, {
                if (it != null) {
                    invoiceDate = it
                    dueOffset?.let { off -> dueDate = it + off }
                }
            }, Modifier.fillMaxWidth())
            DateField(
                "Due by (optional)", dueDate, { dueDate = it; dueOffset = null }, Modifier.fillMaxWidth(),
                clearable = true, placeholder = "No due date",
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "On receipt", 7 to "7 days", 15 to "15 days", 30 to "30 days").forEach { (off, label) ->
                    FilterChip(
                        selected = dueOffset == off,
                        onClick = { dueOffset = off; dueDate = invoiceDate + off },
                        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            OutlinedTextField(
                value = note, onValueChange = { note = it },
                label = { Text("Note (optional)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::save, modifier = Modifier.fillMaxWidth().height(48.dp), enabled = !saving) { Text(if (isEdit) "Save changes" else "Save due") }
        }
    }
}
