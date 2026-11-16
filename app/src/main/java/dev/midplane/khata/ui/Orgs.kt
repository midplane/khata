// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import dev.midplane.khata.R
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.midplane.khata.data.Org

private val Rozha = FontFamily(Font(R.font.rozha_one_khata))

/** "Khata खाता": serif Latin wordmark with the Devanagari name beside it, in the icon's colours. */
@Composable
fun Wordmark() {
    Row {
        Text(
            "Khata", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 23.sp,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            "खाता", fontFamily = Rozha, fontSize = 21.sp,
            // The ledger-cover red from the app icon; its gold lettering in dark mode.
            color = if (isSystemInDarkTheme()) Color(0xFFFFD36B) else Color(0xFFB0261C),
            modifier = Modifier.alignByBaseline(),
        )
    }
}

/**
 * Top-bar title. With [brand] it shows the Khata wordmark instead of [title].
 * Underneath: the org switcher when orgs exist, otherwise the optional [tagline].
 */
@Composable
fun OrgTitle(
    vm: KhataViewModel,
    title: String,
    onManageOrgs: () -> Unit,
    brand: Boolean = false,
    tagline: String? = null,
) {
    val orgs by vm.orgs.collectAsStateWithLifecycle()
    val active by vm.activeOrg.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    val hasOrgs = orgs.isNotEmpty()

    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = if (hasOrgs) Modifier.clickable { open = true } else Modifier,
        ) {
            Column {
                if (brand) Wordmark() else Text(title, style = MaterialTheme.typography.titleLarge)
                when {
                    hasOrgs -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            active?.name ?: "All orgs", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Outlined.ArrowDropDown, "Switch org", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                    tagline != null -> Text(
                        tagline, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            OrgMenuItem("All orgs", selected = active == null) { vm.selectOrg(null); open = false }
            orgs.forEach { o -> OrgMenuItem(o.name, selected = active?.id == o.id) { vm.selectOrg(o.id); open = false } }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Manage orgs") },
                leadingIcon = { Icon(Icons.Outlined.Business, null) },
                onClick = { open = false; onManageOrgs() },
            )
        }
    }
}

@Composable
private fun OrgMenuItem(name: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(name, fontWeight = if (selected) FontWeight.SemiBold else null) },
        trailingIcon = { if (selected) Icon(Icons.Outlined.Check, null) },
        onClick = onClick,
    )
}

@Composable
fun OrgNameDialog(title: String, initial: String = "", existing: List<Org>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    val clash = existing.any { it.name.equals(name.trim(), true) && !it.name.equals(initial, true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                name, { name = it }, label = { Text("Org name") }, singleLine = true,
                placeholder = { Text("e.g. Sharma & Associates") },
                isError = clash, supportingText = { if (clash) Text("An org with this name already exists") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && !clash, onClick = { onSave(name.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Org picker for the due form: "No org", each org, or create a new one inline. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrgField(vm: KhataViewModel, orgId: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    val orgs by vm.orgs.collectAsStateWithLifecycle()
    var expanded by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = orgs.find { it.id == orgId }?.name ?: "No org",
            onValueChange = {},
            readOnly = true,
            label = { Text(if (orgs.isEmpty()) "Org (optional)" else "Org") },
            supportingText = { if (orgs.isEmpty()) Text("Billing for more than one firm? Add an org") },
            leadingIcon = { Icon(Icons.Outlined.Business, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            OrgMenuItem("No org", selected = orgId == null) { onChange(null); expanded = false }
            orgs.forEach { o -> OrgMenuItem(o.name, selected = o.id == orgId) { onChange(o.id); expanded = false } }
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("New org…") },
                leadingIcon = { Icon(Icons.Outlined.Add, null) },
                onClick = { expanded = false; creating = true },
            )
        }
    }
    if (creating) {
        OrgNameDialog("New org", existing = orgs, onDismiss = { creating = false }) { name ->
            creating = false
            vm.addOrg(name) { id -> onChange(id) }
        }
    }
}
