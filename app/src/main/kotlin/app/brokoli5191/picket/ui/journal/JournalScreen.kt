package app.brokoli5191.picket.ui.journal

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.brokoli5191.picket.R
import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.ActionPolicy
import app.brokoli5191.picket.core.ipc.PayloadCodec
import app.brokoli5191.picket.data.ActionsRepository
import app.brokoli5191.picket.data.PlannedUndo
import app.brokoli5191.picket.data.journal.JournalEntry
import app.brokoli5191.picket.ui.actions.touchesRestrictedSetting
import app.brokoli5191.picket.ui.components.NoticeCard
import app.brokoli5191.picket.ui.components.itemMotion
import app.brokoli5191.picket.ui.components.segmentedColors
import app.brokoli5191.picket.ui.settings.InfoScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** A journal entry with what its undo would run, computed once per list update. */
data class JournalRow(
    val entry: JournalEntry,
    val undoCommand: String?,
    val restricted: Boolean,
) {
    val undoable: Boolean
        get() = undoCommand != null && entry.success && entry.undoneById == null && entry.undoOfId == null
}

/** "Undo all from this session", planned and waiting for one confirmation. */
data class PendingBatch(
    val plans: List<PlannedUndo>,
    val unplannable: List<String>,
)

sealed interface JournalMessage {
    data class Undone(
        val restoredRestrictedSetting: Boolean,
    ) : JournalMessage

    data class UndoFailed(
        val reason: String,
    ) : JournalMessage

    data class BatchDone(
        val succeeded: Int,
        val total: Int,
    ) : JournalMessage

    data object NothingToUndo : JournalMessage

    data object Exported : JournalMessage

    data class ExportFailed(
        val reason: String,
    ) : JournalMessage
}

class JournalViewModel(
    private val actions: ActionsRepository,
) : ViewModel() {
    val sessionId: String = actions.sessionId

    val rows: StateFlow<List<JournalRow>> =
        actions.entries
            .map { list -> list.map(::row) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val mutableBatch = MutableStateFlow<PendingBatch?>(null)
    val batch: StateFlow<PendingBatch?> = mutableBatch.asStateFlow()

    private val mutableMessage = MutableStateFlow<JournalMessage?>(null)
    val message: StateFlow<JournalMessage?> = mutableMessage.asStateFlow()

    /** One-tap undo; the row shows the exact command beforehand. */
    fun undo(entryId: Long) {
        viewModelScope.launch {
            val executed = actions.undo(entryId)
            mutableMessage.value =
                if (executed.success) {
                    JournalMessage.Undone(executed.action?.let(::touchesRestrictedSetting) == true)
                } else {
                    JournalMessage.UndoFailed(executed.failure.orEmpty())
                }
        }
    }

    fun planSessionUndo() {
        viewModelScope.launch {
            val (plans, failed) = actions.planSessionUndo()
            if (plans.isEmpty() && failed.isEmpty()) {
                mutableMessage.value = JournalMessage.NothingToUndo
            } else {
                mutableBatch.value = PendingBatch(plans, failed)
            }
        }
    }

    fun confirmBatch() {
        val batch = mutableBatch.value ?: return
        mutableBatch.value = null
        viewModelScope.launch {
            val ok = actions.executeUndos(batch.plans)
            mutableMessage.value = JournalMessage.BatchDone(ok, batch.plans.size)
        }
    }

    fun dismissBatch() {
        mutableBatch.value = null
    }

    fun export(
        resolver: ContentResolver,
        uri: Uri,
    ) {
        viewModelScope.launch {
            mutableMessage.value =
                runCatching {
                    val json = actions.exportJson()
                    withContext(Dispatchers.IO) {
                        val stream = resolver.openOutputStream(uri, "wt") ?: error("Cannot open $uri")
                        stream.use { it.write(json.toByteArray()) }
                    }
                }.fold({ JournalMessage.Exported }, { JournalMessage.ExportFailed(it.message.orEmpty()) })
        }
    }

    fun consumeMessage() {
        mutableMessage.value = null
    }

    private fun row(entry: JournalEntry): JournalRow {
        val inverse =
            entry.inverseJson?.let { json ->
                runCatching { PayloadCodec.json.decodeFromString(Action.serializer(), json) }.getOrNull()
            }
        return JournalRow(
            entry = entry,
            undoCommand = inverse?.let { runCatching { ActionPolicy.command(it).display() }.getOrNull() },
            restricted = inverse?.let(::touchesRestrictedSetting) == true,
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

@Composable
fun JournalScreen(
    rows: List<JournalRow>,
    sessionId: String,
    onUndo: (Long) -> Unit,
    onUndoSession: () -> Unit,
    onExport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbars: SnackbarHostState = remember { SnackbarHostState() },
) {
    val timeFormat = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM) }
    Box(modifier.fillMaxSize()) {
        InfoScaffold(R.string.journal_title, onBack) {
            item(key = "buttons") {
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onUndoSession,
                        enabled = rows.any { it.entry.sessionId == sessionId && it.undoable },
                    ) { Text(stringResource(R.string.journal_undo_session)) }
                    TextButton(onClick = onExport, enabled = rows.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = null)
                        Text(stringResource(R.string.journal_export), Modifier.padding(start = 8.dp))
                    }
                }
            }
            if (rows.isEmpty()) {
                item(key = "empty") {
                    NoticeCard(
                        stringResource(R.string.journal_empty_title),
                        stringResource(R.string.journal_empty_body),
                    )
                }
            }
            itemsIndexed(rows, key = { _, r -> r.entry.id }) { index, row ->
                JournalRowItem(
                    row,
                    timeFormat.format(Date(row.entry.timestampMs)),
                    index,
                    rows.size,
                    onUndo,
                    itemMotion(),
                )
            }
        }
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun JournalRowItem(
    row: JournalRow,
    time: String,
    index: Int,
    count: Int,
    onUndo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entry = row.entry
    val status =
        when {
            !entry.success -> R.string.journal_failed
            entry.undoneById != null -> R.string.journal_undone
            entry.undoOfId != null -> R.string.journal_is_undo
            else -> R.string.journal_done
        }
    val small = MaterialTheme.typography.bodySmall
    SegmentedListItem(
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = segmentedColors(),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 1.dp),
        overlineContent = { Text(time + stringResource(R.string.separator) + stringResource(status)) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.command, fontFamily = FontFamily.Monospace, style = small)
                entry.previousState?.let {
                    Text(
                        stringResource(R.string.confirm_previous, it),
                        style = small,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                entry.note?.let { Text(it, style = small, color = MaterialTheme.colorScheme.error) }
                if (row.undoable) {
                    Text(
                        stringResource(R.string.journal_undo_runs, row.undoCommand.orEmpty()),
                        fontFamily = FontFamily.Monospace,
                        style = small,
                    )
                    if (row.restricted) Text(stringResource(R.string.note_restricted_settings), style = small)
                }
            }
        },
        trailingContent = {
            if (row.undoable) {
                IconButton(onClick = { onUndo(entry.id) }) {
                    Icon(painterResource(R.drawable.ic_undo), contentDescription = stringResource(R.string.action_undo))
                }
            }
        },
    ) { Text(entry.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) }
}

/** Journal with one-tap undo, "undo all from this session" behind one confirmation, and JSON export. */
@Composable
fun JournalDestination(
    vm: JournalViewModel,
    onBack: () -> Unit,
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val batch by vm.batch.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val resolver = LocalContext.current.contentResolver
    val resources = LocalResources.current
    val exporter =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            uri?.let { vm.export(resolver, it) }
        }

    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.consumeMessage()
        val text =
            when (m) {
                is JournalMessage.Undone -> {
                    resources.getString(
                        if (m.restoredRestrictedSetting) {
                            R.string.action_result_undone_restricted
                        } else {
                            R.string.action_result_undone
                        },
                    )
                }

                is JournalMessage.UndoFailed -> {
                    resources.getString(R.string.action_result_undo_failed, m.reason)
                }

                is JournalMessage.BatchDone -> {
                    resources.getString(R.string.journal_batch_result, m.succeeded, m.total)
                }

                JournalMessage.NothingToUndo -> {
                    resources.getString(R.string.journal_nothing_to_undo)
                }

                JournalMessage.Exported -> {
                    resources.getString(R.string.journal_exported)
                }

                is JournalMessage.ExportFailed -> {
                    resources.getString(R.string.journal_export_failed, m.reason)
                }
            }
        snackbars.showSnackbar(text, withDismissAction = true)
    }

    JournalScreen(
        rows = rows,
        sessionId = vm.sessionId,
        onUndo = vm::undo,
        onUndoSession = vm::planSessionUndo,
        onExport = { exporter.launch("picket-journal-${LocalDate.now()}.json") },
        onBack = onBack,
        snackbars = snackbars,
    )
    batch?.let { BatchUndoDialog(it, onConfirm = vm::confirmBatch, onDismiss = vm::dismissBatch) }
}

@Composable
private fun BatchUndoDialog(
    batch: PendingBatch,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val small = MaterialTheme.typography.bodySmall
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.journal_batch_title, batch.plans.size, batch.plans.size)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.journal_batch_body), style = MaterialTheme.typography.bodyMedium)
                batch.plans.forEach { planned ->
                    Text(planned.plan.command.display(), fontFamily = FontFamily.Monospace, style = small)
                }
                if (batch.plans.any { touchesRestrictedSetting(it.plan.action) }) {
                    Text(stringResource(R.string.note_restricted_settings), style = small)
                }
                if (batch.unplannable.isNotEmpty()) {
                    Text(
                        stringResource(R.string.journal_batch_unplannable),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    batch.unplannable.forEach { Text(it, style = small, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = batch.plans.isNotEmpty()) {
                Text(stringResource(R.string.action_run))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
