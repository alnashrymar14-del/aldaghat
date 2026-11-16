package com.abbas.aldaghat.ui.folder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import com.abbas.aldaghat.data.MediaStorePublisher
import com.abbas.aldaghat.ui.components.EmptyState
import com.abbas.aldaghat.ui.components.SimpleTopBar
import com.abbas.aldaghat.ui.util.VideoIntents
import com.abbas.aldaghat.util.Formatters

/** In-app listing of `Movies/ABBAS`, used when the system cannot open the folder externally. */
@Composable
fun FolderScreen(
    viewModel: FolderViewModel,
    onBack: () -> Unit,
) {
    val videos by viewModel.videos.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingDelete by remember { mutableStateOf<MediaStorePublisher.SavedVideo?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SimpleTopBar(title = stringResource(R.string.folder_title), onBack = onBack) {
                TextButton(onClick = {
                    VideoIntents.openAbbasFolder(context)
                }) {
                    Text(stringResource(R.string.folder_open_external))
                }
            }
        },
    ) { padding ->
        if (videos.isEmpty()) {
            Column(modifier = Modifier.padding(padding)) {
                EmptyState(
                    title = stringResource(R.string.folder_title),
                    body = stringResource(R.string.folder_empty),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items = videos, key = { it.uri.toString() }) { video ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = video.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = Formatters.formatBytes(video.sizeBytes) + " • " +
                                        Formatters.formatDate(video.dateAddedMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = {
                                VideoIntents.open(context, video.uri)
                            }) {
                                Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.action_open_video))
                            }
                            IconButton(onClick = {
                                VideoIntents.share(context, video.uri, video.displayName)
                            }) {
                                Icon(Icons.Default.Share, contentDescription = stringResource(R.string.action_share))
                            }
                            IconButton(onClick = { pendingDelete = video }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    pendingDelete?.let { video ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(video.displayName) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(video)
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
