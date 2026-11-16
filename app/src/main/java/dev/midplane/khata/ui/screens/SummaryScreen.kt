// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.midplane.khata.data.Summary
import dev.midplane.khata.sms.SmsReader
import dev.midplane.khata.ui.Avatar
import dev.midplane.khata.ui.DueFilter
import dev.midplane.khata.ui.EmptyState
import dev.midplane.khata.ui.Fmt
import dev.midplane.khata.ui.KhataViewModel
import dev.midplane.khata.ui.Exporter
import dev.midplane.khata.ui.OrgTitle
import androidx.compose.material.icons.outlined.Business

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    vm: KhataViewModel,
    onAddDue: () -> Unit,
    onOpenDue: (Long) -> Unit,
    onOpenClient: (Long) -> Unit,
    onOpenSms: () -> Unit,
    onShowDues: (DueFilter) -> Unit,
    onManageOrgs: () -> Unit,
) {
    val activeOrg by vm.activeOrg.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val smsGranted = SmsReader.hasPermission(ctx)
    val matched = suggestions.count { it.best != null }

    LaunchedEffect(smsGranted) { if (smsGranted) vm.scanSms() }
    var menu by remember { mutableStateOf(false) }
    val saveFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Exporter.MIME)) { uri ->
        if (uri != null) Exporter.save(ctx, uri, vm.ledgerTsv())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { OrgTitle(vm, "Khata", onManageOrgs, brand = true, tagline = summary?.let(::tagline)) },
                actions = {
                    IconButton(onClick = onOpenSms) {
                        BadgedBox(badge = { if (matched > 0) Badge { Text("$matched") } }) {
                            Icon(Icons.Outlined.Sms, "Payments from SMS")
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        if (summary?.billed != 0L) {
                            DropdownMenuItem(
                                text = { Text("Share ledger (TSV)") },
                                leadingIcon = { Icon(Icons.Outlined.Share, null) },
                                onClick = { menu = false; Exporter.share(ctx, vm.ledgerTsv(), vm.ledgerFileName()) },
                            )
                            DropdownMenuItem(
                                text = { Text("Save ledger to device") },
                                leadingIcon = { Icon(Icons.Outlined.SaveAlt, null) },
                                onClick = { menu = false; saveFile.launch(vm.ledgerFileName()) },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Manage orgs") },
                            leadingIcon = { Icon(Icons.Outlined.Business, null) },
                            onClick = { menu = false; onManageOrgs() },
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (summary?.billed != 0L) ExtendedFloatingActionButton(onClick = onAddDue, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("New due") })
        },
    ) { pad ->
        val s = summary ?: return@Scaffold
        if (s.billed == 0L) {
            EmptyState(
                Icons.Outlined.Receipt,
                activeOrg?.let { "No dues for ${it.name}" } ?: "Your khata is empty",
                if (activeOrg != null) "Record a due for this org, or switch to All orgs from the top."
                else "Record what clients owe you. Mark payments as they come in — fully or in parts — and see who still owes what.",
                Modifier.padding(pad),
            ) { Button(onClick = onAddDue) { Text("Record first due") } }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { HeroCard(s, onClick = { onShowDues(DueFilter.Open) }) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        "Overdue", Fmt.money(s.overdueAmount),
                        if (s.overdueCount == 0) "Nothing overdue" else "${s.overdueCount} ${plural(s.overdueCount, "due")}",
                        container = if (s.overdueCount > 0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
                        content = if (s.overdueCount > 0) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onShowDues(DueFilter.Overdue) },
                    )
                    StatCard(
                        "Received this month", Fmt.money(s.receivedThisMonth),
                        "${s.paymentsThisMonth} ${plural(s.paymentsThisMonth, "payment")}",
                        container = MaterialTheme.colorScheme.surfaceContainer,
                        content = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        onClick = { onShowDues(DueFilter.Paid) },
                    )
                }
            }
            if (matched > 0 || !smsGranted) {
                item { SmsBanner(matched, smsGranted, onOpenSms) }
            }
            if (s.outstanding > 0) item { AgingCard(s) }
            if (s.topClients.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text("Top outstanding", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                        s.topClients.forEach { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenClient(c.clientId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(c.name)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text("${c.openCount} open ${plural(c.openCount, "due")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Fmt.money(c.outstanding), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            if (s.recent.isNotEmpty()) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                        Text("Recent payments", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 4.dp))
                        s.recent.forEach { p ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onOpenDue(p.payment.dueId) }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(p.clientName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                                    Text(
                                        "${Fmt.shortDay(p.payment.receivedOn)} · ${p.dueTitle}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                    )
                                }
                                Text(
                                    "+" + Fmt.money(p.payment.amount), style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

/** A small, friendly line under the wordmark that reflects how things stand. */
private fun tagline(s: Summary): String = when {
    s.billed == 0L -> "Your hisaab, sorted."
    s.outstanding == 0L -> "All square. Chai time ☕"
    s.overdueCount > 0 -> "${s.overdueCount} ${plural(s.overdueCount, "due")} could use a gentle nudge"
    s.receivedThisMonth > 0 -> "${Fmt.money(s.receivedThisMonth)} came in this month"
    else -> "Every rupee, accounted for."
}

private fun plural(n: Int, word: String) = if (n == 1) word else word + "s"

@Composable
private fun HeroCard(s: Summary, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("To collect", style = MaterialTheme.typography.labelLarge)
            Text(Fmt.money(s.outstanding), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Text(
                if (s.openCount == 0) "All dues collected 🎉"
                else "${s.openCount} open ${plural(s.openCount, "due")} from ${s.clientsWithDues} ${plural(s.clientsWithDues, "client")}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { s.collectedFraction },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = MaterialTheme.colorScheme.primaryContainer,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Collected ${Fmt.money(s.received)} of ${Fmt.money(s.billed)} billed (${(s.collectedFraction * 100).toInt()}%)",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, sub: String, container: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick, modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun SmsBanner(matched: Int, granted: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Sms, null)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (granted) "$matched ${plural(matched, "payment")} spotted in SMS" else "Auto-detect payments from SMS",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    if (granted) "Review and close the matching dues" else "Match bank credit alerts to your dues — on-device only",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Icon(Icons.Outlined.ChevronRight, null)
        }
    }
}

@Composable
private fun AgingCard(s: Summary) {
    val max = s.aging.maxOf { it.amount }.coerceAtLeast(1)
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFD9822B),
        MaterialTheme.colorScheme.error,
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Ageing", style = MaterialTheme.typography.titleMedium)
            Text("Outstanding by days since billing", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            s.aging.forEachIndexed { i, b ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp))
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
                        if (b.amount > 0) {
                            Box(
                                Modifier.fillMaxHeight().fillMaxWidth((b.amount.toFloat() / max).coerceAtLeast(0.03f))
                                    .clip(RoundedCornerShape(5.dp)).background(colors[i])
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        Fmt.money(b.amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(96.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 1,
                    )
                }
            }
        }
    }
}
