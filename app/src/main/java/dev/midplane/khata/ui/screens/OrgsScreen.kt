// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.midplane.khata.data.Org
import dev.midplane.khata.data.isPaid
import dev.midplane.khata.data.outstanding
import dev.midplane.khata.ui.Avatar
import dev.midplane.khata.ui.ConfirmDialog
import dev.midplane.khata.ui.EmptyState
import dev.midplane.khata.ui.Fmt
import dev.midplane.khata.ui.KhataViewModel
import dev.midplane.khata.ui.OrgNameDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgsScreen(vm: KhataViewModel, onBack: () -> Unit) {
    val orgs by vm.orgs.collectAsStateWithLifecycle()
    val dues by vm.allDues.collectAsStateWithLifecycle()
    val active by vm.activeOrg.collectAsStateWithLifecycle()
    var adding by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<Org?>(null) }
    var deleting by remember { mutableStateOf<Org?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Organisations") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            if (orgs.isNotEmpty()) {
                ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add org") })
            }
        },
    ) { pad ->
        if (orgs.isEmpty()) {
            EmptyState(
                Icons.Outlined.Business, "Work for more than one firm?",
                "Add each organisation you bill clients for. Tag every due with its org, then switch between orgs " +
                    "at the top of Summary, Dues and Clients — totals, reminders and exports follow along.",
                Modifier.padding(pad),
            ) { Button(onClick = { adding = true }) { Text("Add org") } }
            return@Scaffold
        }
        val untagged = dues.count { it.due.orgId == null }
        LazyColumn(Modifier.fillMaxSize().padding(pad), contentPadding = PaddingValues(bottom = 96.dp)) {
            items(orgs, key = { it.id }) { o ->
                val mine = dues.filter { it.due.orgId == o.id }
                val open = mine.filter { !it.isPaid }
                ListItem(
                    modifier = Modifier.clickable { vm.selectOrg(o.id); onBack() },
                    leadingContent = { Avatar(o.name) },
                    headlineContent = { Text(o.name) },
                    supportingContent = {
                        Text(
                            if (mine.isEmpty()) "No dues yet"
                            else "${open.size} open · ${Fmt.money(open.sumOf { it.outstanding })} to collect"
                        )
                    },
                    trailingContent = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (active?.id == o.id) Icon(Icons.Outlined.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
                            IconButton(onClick = { renaming = o }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton(onClick = { deleting = o }) { Icon(Icons.Outlined.Delete, "Delete") }
                        }
                    },
                )
                HorizontalDivider(Modifier.padding(start = 72.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
            item {
                Text(
                    "Tap an org to switch to it." + if (untagged > 0) " $untagged ${if (untagged == 1) "due has" else "dues have"} no org — they show under “All orgs”; edit a due to tag it." else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (adding) OrgNameDialog("New org", existing = orgs, onDismiss = { adding = false }) { vm.addOrg(it); adding = false }
    renaming?.let { o ->
        OrgNameDialog("Rename org", initial = o.name, existing = orgs, onDismiss = { renaming = null }) { vm.renameOrg(o, it); renaming = null }
    }
    deleting?.let { o ->
        val n = dues.count { it.due.orgId == o.id }
        ConfirmDialog(
            "Delete ${o.name}?",
            if (n == 0) "This org has no dues." else "Its $n ${if (n == 1) "due" else "dues"} and payments are kept, but will no longer be tagged to an org.",
            "Delete", onDismiss = { deleting = null }, onConfirm = { vm.deleteOrg(o) },
        )
    }
}
