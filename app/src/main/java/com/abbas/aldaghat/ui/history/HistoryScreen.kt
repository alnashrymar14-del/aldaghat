package com.abbas.aldaghat.ui.history

import android.net.Uri
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.abbas.aldaghat.R
import com.abbas.aldaghat.data.model.HistoryEntry
import com.abbas.aldaghat.domain.HistoryStatus
import com.abbas.aldaghat.ui.components.EmptyState
import com.abbas.aldaghat.ui.components.SimpleTopBar
import com.abbas.aldaghat.ui.components.StatusBadge
import com.abbas.aldaghat.ui.components.ThumbnailImage
import com.abbas.aldaghat.ui.util.VideoIntents
import com.abbas.aldaghat.util.Formatters

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<HistoryEntry?>(null) }
    var deleteFileToo by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(title = stringResource(R.string.history_title), onBack = onBack) {
                TextButton(onClick = onOpenFolder) {
                    Text(stringResource(R.string.history_open_folder))
                }
            }
        },
    ) { padding ->
        if (entries.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.history_title),
                    body = stringResource(R.string.history_empty),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(items = entries, key = { it.id }) { entry ->
                    HistoryCard(
                        entry = entry,
                        onOpen = {
                            entry.outputUri?.let { uri ->
                                VideoIntents.open(context, Uri.parse(uri))
                            }
                        },
                        onShare = {
                            entry.outputUri?.let { uri ->
                                VideoIntents.share(
                                    context,
                                    Uri.parse(uri),
                                    entry.outputName ?: entry.sourceName,
                                )
                            }
                        },
                        onDelete = {
                            deleteFileToo = false
                            pendingDelete = entry
                        },
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.history_delete_body))
                    if (entry.outputUri != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        ) {
                            Checkbox(
                                checked = deleteFileToo,
                                onCheckedChange = { deleteFileToo = it },
                            )
                            Text(
                                text = stringResource(R.string.history_delete_file_too),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry, deleteFileToo)
                    pendingDelete = null
                }) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun HistoryCard(
    entry: HistoryEntry,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(10.dp)) {
            ThumbnailImage(
                path = entry.thumbPath,
                modifier = Modifier
                    .width(96.dp)
                    .height(64.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            ) {
                Text(
                    text = entry.outputName ?: entry.sourceName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${Formatters.formatBytes(entry.originalBytes)} ← ${Formatters.formatBytes(entry.outputBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${entry.quality} • ${entry.codec} • ${Formatters.formatPercent(entry.savingPercent, 1)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = Formatters.formatDate(entry.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusBadge(status = entry.status)
                    Spacer(Modifier.weight(1f))
                    if (entry.status == HistoryStatus.COMPLETED && entry.outputUri != null) {
                        IconButton(onClick = onOpen) {
                            Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_open_video))
                        }
                        IconButton(onClick = onShare) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
                entry.error?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
