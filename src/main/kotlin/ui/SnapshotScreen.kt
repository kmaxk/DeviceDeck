package ui

import adb.AdbDevice
import adb.AdbService
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import device.Device
import ios.IosDevice
import ios.IosService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import snapshot.SectionResult
import snapshot.Snapshot
import snapshot.SnapshotStore
import snapshot.diff
import snapshot.snapshot
import java.text.SimpleDateFormat
import java.util.Date

private sealed interface DiffRow {
    data class Header(val section: String, val summary: String) : DiffRow
    data class Note(val text: String) : DiffRow
    data class Entry(val mark: String, val key: String, val value: String) : DiffRow
}

private fun buildRows(result: Map<String, SectionResult>, query: String): List<DiffRow> {
    val q = query.trim()
    fun matches(vararg parts: String) = q.isEmpty() || parts.any { it.contains(q, ignoreCase = true) }
    val rows = mutableListOf<DiffRow>()
    for ((name, section) in result.toSortedMap()) {
        when (section) {
            is SectionResult.NotComparable -> {
                rows += DiffRow.Header(name, "not comparable")
                rows += DiffRow.Note("Only one of the two snapshots could read this section.")
            }
            is SectionResult.Compared -> {
                val d = section.diff
                rows += DiffRow.Header(name, "+${d.added.size}  −${d.removed.size}  ~${d.changed.size}")
                if (d.isEmpty) {
                    rows += DiffRow.Note("No changes")
                } else {
                    d.added.toSortedMap().forEach { (k, v) -> if (matches(k, v)) rows += DiffRow.Entry("+", k, v) }
                    d.removed.toSortedMap().forEach { (k, v) -> if (matches(k, v)) rows += DiffRow.Entry("−", k, v) }
                    d.changed.toSortedMap().forEach { (k, v) ->
                        if (matches(k, v.first, v.second)) rows += DiffRow.Entry("~", k, "${v.first} → ${v.second}")
                    }
                }
            }
        }
    }
    return rows
}

private fun rowsAsText(rows: List<DiffRow>): String = rows.joinToString("\n") {
    when (it) {
        is DiffRow.Header -> "\n## ${it.section} (${it.summary})"
        is DiffRow.Note -> "  ${it.text}"
        is DiffRow.Entry -> "  ${it.mark} ${it.key}: ${it.value}"
    }
}.trim()

private suspend fun capture(device: Device, label: String): Snapshot? = when (device) {
    is AdbDevice -> AdbService.snapshot(device, label)
    is IosDevice -> IosService.snapshot(device, label)
    else -> null
}

private val TimeFormat get() = SimpleDateFormat("dd.MM. HH:mm:ss")

@Composable
fun SnapshotScreen(device: Device) = key(device.id) {
    val scope = rememberCoroutineScope()
    val store = remember { SnapshotStore() }
    val clipboard = LocalClipboardManager.current

    var snapshots by remember { mutableStateOf<List<Snapshot>>(emptyList()) }
    var label by remember { mutableStateOf("") }
    var isTaking by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var before by remember { mutableStateOf<Snapshot?>(null) }
    var after by remember { mutableStateOf<Snapshot?>(null) }
    var afterNow by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var isComparing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<Map<String, SectionResult>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { snapshots = withContext(Dispatchers.IO) { store.list(device.id) } }

    LaunchedEffect(before, after, afterNow, refresh) {
        result = null
        val b = before ?: return@LaunchedEffect
        val a = if (afterNow) {
            isComparing = true
            val now = runCatching { capture(device, "now") }.getOrNull()
            isComparing = false
            if (now == null || now.sections.isEmpty()) {
                error = "Could not read the current device state. Is the device still connected?"
                return@LaunchedEffect
            }
            now
        } else after
        if (a != null) result = diff(b, a)
    }

    fun takeSnapshot() {
        scope.launch {
            isTaking = true
            error = null
            val taken = runCatching { capture(device, label.trim()) }.getOrNull()
            if (taken == null || taken.sections.isEmpty()) {
                error = "Could not read the device state. Is the device still connected?"
            } else {
                withContext(Dispatchers.IO) { store.save(taken) }
                label = ""
                snapshots = withContext(Dispatchers.IO) { store.list(device.id) }
            }
            isTaking = false
        }
    }

    fun delete(s: Snapshot) {
        scope.launch {
            withContext(Dispatchers.IO) { store.delete(s) }
            if (before?.id == s.id) before = null
            if (after?.id == s.id) after = null
            snapshots = withContext(Dispatchers.IO) { store.list(device.id) }
        }
    }

    Row(Modifier.fillMaxSize().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // --- Left: take + pick ---
        Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Snapshots", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = label, onValueChange = { label = it }, singleLine = true,
                label = { Text("Label (optional)") }, modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::takeSnapshot, enabled = !isTaking, modifier = Modifier.fillMaxWidth()) {
                if (isTaking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.CameraAlt, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isTaking) "Reading device…" else "Take snapshot")
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FilterChip(
                selected = afterNow,
                onClick = { afterNow = !afterNow; if (afterNow) after = null },
                label = { Text("After: current state (Now)") },
                leadingIcon = { Icon(Icons.Filled.Bolt, null, Modifier.size(16.dp)) },
            )
            HorizontalDivider()
            if (snapshots.isEmpty()) {
                Text("No snapshots yet.", style = MaterialTheme.typography.bodySmall)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(snapshots, key = { it.id }) { s ->
                    Card(border = appCardBorder(), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.label.ifBlank { s.id }, fontWeight = FontWeight.SemiBold,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        TimeFormat.format(Date(s.takenAt)) + "  ·  " +
                                            s.sections.entries.joinToString(", ") { "${it.key} ${it.value.size}" },
                                        style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { delete(s) }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Filled.Delete, "Delete snapshot", Modifier.size(16.dp))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = before?.id == s.id,
                                    onClick = { before = if (before?.id == s.id) null else s },
                                    label = { Text("Before") },
                                )
                                FilterChip(
                                    selected = !afterNow && after?.id == s.id,
                                    onClick = { afterNow = false; after = if (after?.id == s.id) null else s },
                                    label = { Text("After") },
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- Right: diff ---
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val shown = result
            val rows = remember(shown, query) { shown?.let { buildRows(it, query) } ?: emptyList() }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Filter keys and values") }, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { refresh++ }, enabled = before != null && (afterNow || after != null)) {
                    Icon(Icons.Filled.Refresh, "Compare again")
                }
                OutlinedButton(
                    onClick = { shown?.let { clipboard.setText(AnnotatedString(rowsAsText(buildRows(it, "")))) } },
                    enabled = shown != null,
                ) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy as text")
                }
            }
            when {
                isComparing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                shown == null -> EmptyState(
                    Icons.Filled.Compare, "Pick two states to compare",
                    "Choose “Before”, then either “After” on another snapshot or “Now” for the live state.",
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(rows) { row ->
                        when (row) {
                            is DiffRow.Header -> Row(Modifier.padding(top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(row.section, fontWeight = FontWeight.Bold)
                                Text(row.summary, style = MaterialTheme.typography.bodySmall)
                            }
                            is DiffRow.Note -> Text(row.text, style = MaterialTheme.typography.bodySmall)
                            is DiffRow.Entry -> {
                                val color = when (row.mark) {
                                    "+" -> LogLevelColors.Info
                                    "−" -> LogLevelColors.Error
                                    else -> LogLevelColors.Warn
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(row.mark, color = color, fontFamily = FontFamily.Monospace)
                                    Text(
                                        row.key + ": " + row.value, color = color, fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
