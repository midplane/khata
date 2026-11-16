package app.khata.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.khata.data.DueRow
import app.khata.data.outstanding
import app.khata.sms.Confidence
import app.khata.sms.SmsReader
import app.khata.sms.Suggestion
import app.khata.ui.EmptyState
import app.khata.ui.Fmt
import app.khata.ui.KhataViewModel
import app.khata.ui.Pill
import app.khata.ui.RecordPaymentDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(vm: KhataViewModel, onBack: () -> Unit, onOpenDue: (Long) -> Unit) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(SmsReader.hasPermission(ctx)) }
    var askedOnce by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it; askedOnce = true
    }
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val scanning by vm.scanning.collectAsStateWithLifecycle()
    val scannedOnce by vm.scannedOnce.collectAsStateWithLifecycle()

    // (suggestion, chosen due) awaiting confirmation; null due means "pick one first".
    var picking by remember { mutableStateOf<Suggestion?>(null) }
    var recording by remember { mutableStateOf<Pair<Suggestion, DueRow>?>(null) }

    LaunchedEffect(granted) { if (granted) vm.scanSms() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payments from SMS") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (granted) IconButton(onClick = { vm.scanSms() }, enabled = !scanning) { Icon(Icons.Outlined.Refresh, "Rescan") }
                },
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                !granted -> EmptyState(
                    Icons.Outlined.Sms, "Spot payments automatically",
                    "Khata can read bank and UPI credit alerts on this phone and suggest which dues they settle. " +
                        "Messages are processed on-device and never leave your phone.",
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(onClick = { launcher.launch(Manifest.permission.READ_SMS) }) { Text("Allow SMS access") }
                        if (askedOnce) {
                            TextButton(onClick = {
                                ctx.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                )
                            }) { Text("Open app settings") }
                        }
                    }
                }
                suggestions.isEmpty() && (scanning || !scannedOnce) -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                suggestions.isEmpty() -> EmptyState(
                    Icons.Outlined.Sms, "No new payments found",
                    "Looked at credit SMS from the last ${KhataViewModel.SMS_LOOKBACK_DAYS} days. " +
                        "Payments already recorded or dismissed aren't shown again.",
                )
                else -> {
                    val matched = suggestions.count { it.best != null }
                    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Text(
                                "${suggestions.size} credits found · $matched likely ${if (matched == 1) "match" else "matches"}",
                                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        items(suggestions, key = { it.sms.key }) { s ->
                            SuggestionCard(
                                s,
                                onApply = { recording = s to s.best!! },
                                onPick = { picking = s },
                                onDismiss = { vm.dismissSms(s.sms.key) },
                                onOpenDue = onOpenDue,
                            )
                        }
                    }
                }
            }
        }
    }

    picking?.let { s ->
        PickDueDialog(
            s, onDismiss = { picking = null },
            onPick = { due -> picking = null; recording = s to due },
        )
    }
    recording?.let { (s, due) ->
        val sms = s.sms
        RecordPaymentDialog(
            title = "${due.clientName} · ${due.due.title}",
            outstanding = due.outstanding,
            initialAmount = minOf(sms.amount, due.outstanding),
            initialDay = Fmt.millisToDay(sms.date),
            initialNote = "Via SMS" + (sms.payer?.let { " from $it" } ?: ""),
            hint = if (sms.amount > due.outstanding) "SMS shows ${Fmt.money(sms.amount)}, more than what's outstanding." else null,
            onDismiss = { recording = null },
            onConfirm = { amt, day, note ->
                recording = null
                vm.recordPayment(due.due.id, amt, day, note, smsKey = sms.key)
            },
        )
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, onApply: () -> Unit, onPick: () -> Unit, onDismiss: () -> Unit, onOpenDue: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sms = s.sms
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "+" + Fmt.money(sms.amount), style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        listOfNotNull(sms.payer?.let { "from $it" }, Fmt.shortDay(Fmt.millisToDay(sms.date)), sms.sender).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                sms.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { expanded = !expanded },
            )
            val best = s.best
            if (best != null) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().clickable { onOpenDue(best.due.id) },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Looks like", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            ConfidencePill(s.confidence!!)
                        }
                        Text(best.clientName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${best.due.title} · ${Fmt.money(best.outstanding)} outstanding",
                            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            when {
                                sms.amount >= best.outstanding -> "Closes this due"
                                else -> "Part payment — ${Fmt.money(best.outstanding - sms.amount)} will remain"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (best != null) {
                    Button(onClick = onApply) { Text("Record") }
                    OutlinedButton(onClick = onPick, enabled = s.ranked.isNotEmpty()) { Text("Other due") }
                } else {
                    Button(onClick = onPick, enabled = s.ranked.isNotEmpty()) { Text("Assign to due") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@Composable
private fun ConfidencePill(c: Confidence) {
    val cs = MaterialTheme.colorScheme
    when (c) {
        Confidence.High -> Pill("Strong match", cs.primary, cs.onPrimary)
        Confidence.Medium -> Pill("Name match", cs.tertiaryContainer, cs.onTertiaryContainer)
        Confidence.Low -> Pill("Amount match", cs.surfaceContainerHighest, cs.onSurfaceVariant)
    }
}

@Composable
private fun PickDueDialog(s: Suggestion, onDismiss: () -> Unit, onPick: (DueRow) -> Unit) {
    var selected by remember { mutableStateOf(s.best ?: s.ranked.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Which due is ${Fmt.money(s.sms.amount)} for?") },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(s.ranked, key = { it.due.id }) { d ->
                    Row(
                        Modifier.fillMaxWidth().clickable { selected = d }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected?.due?.id == d.due.id, onClick = { selected = d })
                        Column(Modifier.weight(1f)) {
                            Text(d.clientName, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                            Text(
                                "${d.due.title} · ${Fmt.money(d.outstanding)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = selected != null, onClick = { onPick(selected!!) }) { Text("Next") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
