// SPDX-FileCopyrightText: 2026 The Khata authors
// SPDX-License-Identifier: GPL-3.0-or-later

package dev.midplane.khata.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.midplane.khata.data.DueRow
import dev.midplane.khata.data.isOverdue
import dev.midplane.khata.data.isPaid
import dev.midplane.khata.data.outstanding

private const val DAY_MS = 86_400_000L

@Composable
fun Pill(text: String, container: Color, content: Color) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50)) {
        Text(
            text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun DueStatusPill(row: DueRow, today: Long = Fmt.today()) {
    val c = MaterialTheme.colorScheme
    when {
        row.isPaid -> Pill("Paid", c.primaryContainer, c.onPrimaryContainer)
        row.isOverdue(today) -> Pill("Overdue", c.errorContainer, c.onErrorContainer)
        row.paid > 0 -> Pill("Partial", c.tertiaryContainer, c.onTertiaryContainer)
        else -> Pill("Open", c.surfaceContainerHighest, c.onSurfaceVariant)
    }
}

@Composable
fun Avatar(name: String, modifier: Modifier = Modifier) {
    val initials = name.split(' ').filter { it.firstOrNull()?.isLetterOrDigit() == true }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
    Box(
        modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
fun DueListItem(
    row: DueRow,
    onClick: () -> Unit,
    showClient: Boolean = true,
    today: Long = Fmt.today(),
    showOrg: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (showClient) row.clientName else row.due.title.ifBlank { "Professional fees" },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (showOrg && row.orgName != null) {
                    Text(
                        row.orgName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val sub = buildList {
                    if (showClient) add(row.due.title.ifBlank { "Professional fees" })
                    add(
                        when {
                            row.isPaid -> "Billed ${Fmt.shortDay(row.due.invoiceDate)}"
                            row.due.dueDate != null -> Fmt.dueRelative(row.due.dueDate, today)
                            else -> "Billed ${Fmt.shortDay(row.due.invoiceDate)}"
                        }
                    )
                }.joinToString(" · ")
                Text(
                    sub, style = MaterialTheme.typography.bodyMedium,
                    color = if (row.isOverdue(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    Fmt.money(if (row.isPaid) row.due.amount else row.outstanding),
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                DueStatusPill(row, today)
            }
        }
        if (!row.isPaid && row.paid > 0) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { row.paid.toFloat() / row.due.amount },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                drawStopIndicator = {},
            )
            Text(
                "${Fmt.money(row.paid)} of ${Fmt.money(row.due.amount)} received",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp)); action()
        }
    }
}

/** Read-only text field that opens a date picker. Values are epoch days. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(
    label: String,
    epochDay: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    placeholder: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect { if (it is PressInteraction.Release) open = true }
    }
    OutlinedTextField(
        value = epochDay?.let(Fmt::day) ?: "",
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        trailingIcon = {
            if (clearable && epochDay != null) IconButton(onClick = { onChange(null) }) { Icon(Icons.Outlined.Close, "Clear") }
            else Icon(Icons.Outlined.CalendarMonth, null)
        },
        interactionSource = source,
        singleLine = true,
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (epochDay ?: Fmt.today()) * DAY_MS)
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { state.selectedDateMillis?.let { onChange(it / DAY_MS) }; open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

@Composable
fun AmountField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, error: String? = null) {
    OutlinedTextField(
        value = value,
        onValueChange = { s -> onChange(s.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label) },
        prefix = { Text("₹ ") },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/** Dialog for recording a full or partial payment against a due. */
@Composable
fun RecordPaymentDialog(
    title: String,
    outstanding: Long,
    initialAmount: Long = outstanding,
    initialDay: Long = Fmt.today(),
    initialNote: String = "",
    hint: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (amount: Long, day: Long, note: String) -> Unit,
) {
    var amountText by rememberSaveable { mutableStateOf(Fmt.amountInput(initialAmount)) }
    var day by rememberSaveable { mutableLongStateOf(initialDay) }
    var note by rememberSaveable { mutableStateOf(initialNote) }
    val amount = Fmt.parseAmount(amountText)
    val error = when {
        amountText.isBlank() -> null
        amount == null -> "Enter a valid amount"
        amount > outstanding -> "More than outstanding (${Fmt.money(outstanding)})"
        else -> null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Outstanding: ${Fmt.money(outstanding)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                AmountField(amountText, { amountText = it }, "Amount received", Modifier.fillMaxWidth(), error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(onClick = { amountText = Fmt.amountInput(outstanding) }, label = { Text("Full") })
                    if (outstanding >= 200) AssistChip(onClick = { amountText = Fmt.amountInput(outstanding / 2) }, label = { Text("Half") })
                }
                DateField("Received on", day, { if (it != null) day = it }, Modifier.fillMaxWidth())
                OutlinedTextField(
                    note, { note = it }, label = { Text("Note (optional)") },
                    placeholder = { Text("e.g. UPI, cheque no.") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = amount != null && error == null,
                onClick = { onConfirm(amount!!, day, note) },
            ) { Text(if (amount == outstanding) "Mark paid" else "Record") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, message: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** "Remind" button: WhatsApp / SMS when a phone number is known, otherwise the share sheet. */
@Composable
fun ReminderButton(phone: String?, text: () -> String, modifier: Modifier = Modifier) {
    val ctx: Context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(onClick = { if (phone.isNullOrBlank()) Reminders.share(ctx, text()) else menu = true }) {
            Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Remind")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("WhatsApp") }, onClick = { menu = false; Reminders.whatsApp(ctx, phone!!, text()) })
            DropdownMenuItem(text = { Text("SMS") }, onClick = { menu = false; Reminders.sms(ctx, phone!!, text()) })
            DropdownMenuItem(text = { Text("Other apps…") }, onClick = { menu = false; Reminders.share(ctx, text()) })
        }
    }
}
