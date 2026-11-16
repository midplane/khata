// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.midplane.khata.data.DueRow
import dev.midplane.khata.data.isOverdue
import dev.midplane.khata.data.isPaid
import dev.midplane.khata.data.outstanding
import dev.midplane.khata.ui.DueFilter
import dev.midplane.khata.ui.DueListItem
import dev.midplane.khata.ui.EmptyState
import dev.midplane.khata.ui.Fmt
import dev.midplane.khata.ui.KhataViewModel
import dev.midplane.khata.ui.OrgTitle

fun List<DueRow>.filterBy(filter: DueFilter, today: Long): List<DueRow> = when (filter) {
    DueFilter.Open -> filter { !it.isPaid }.sortedBy { it.due.dueDate ?: Long.MAX_VALUE }
    DueFilter.Overdue -> filter { it.isOverdue(today) }.sortedBy { it.due.dueDate }
    DueFilter.Paid -> filter { it.isPaid }
    DueFilter.All -> this
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuesScreen(vm: KhataViewModel, onOpenDue: (Long) -> Unit, onAddDue: () -> Unit, onManageOrgs: () -> Unit) {
    val activeOrg by vm.activeOrg.collectAsStateWithLifecycle()
    val orgs by vm.orgs.collectAsStateWithLifecycle()
    val dues by vm.dues.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val filter by vm.dueFilter.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val today = Fmt.today()

    val searched = remember(dues, query) {
        if (query.isBlank()) dues
        else dues.filter { it.clientName.contains(query, true) || it.due.title.contains(query, true) }
    }
    val list = remember(searched, filter) { searched.filterBy(filter, today) }

    Scaffold(
        topBar = { TopAppBar(title = { OrgTitle(vm, "Dues", onManageOrgs) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDue) { Icon(Icons.Outlined.Add, "New due") }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                placeholder = { Text("Search client or work") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                singleLine = true, shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DueFilter.entries) { f ->
                    val n = searched.filterBy(f, today).size
                    FilterChip(selected = f == filter, onClick = { vm.setDueFilter(f) }, label = { Text("${f.label} · $n") })
                }
            }
            if (!loaded) return@Column
            if (list.isEmpty()) {
                EmptyState(
                    Icons.Outlined.Receipt,
                    when {
                        dues.isEmpty() -> "No dues yet"
                        query.isNotBlank() -> "No matches"
                        filter == DueFilter.Overdue -> "Nothing overdue"
                        filter == DueFilter.Open -> "All collected"
                        else -> "Nothing here"
                    },
                    if (dues.isEmpty()) "Tap + to record a payment a client owes you." else "Try another filter or search.",
                )
                return@Column
            }
            if (filter != DueFilter.Paid) {
                Text(
                    "${list.size} dues · ${Fmt.money(list.sumOf { it.outstanding })} outstanding",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                items(list, key = { it.due.id }) { row ->
                    DueListItem(row, onClick = { onOpenDue(row.due.id) }, today = today, showOrg = activeOrg == null && orgs.isNotEmpty())
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}
