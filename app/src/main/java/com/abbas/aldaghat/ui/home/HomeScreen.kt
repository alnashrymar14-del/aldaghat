package com.abbas.aldaghat.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.abbas.aldaghat.R
import com.abbas.aldaghat.domain.BitrateSelector
import com.abbas.aldaghat.domain.CompressionResult
import com.abbas.aldaghat.domain.JobState
import com.abbas.aldaghat.domain.QualityPreset
import com.abbas.aldaghat.domain.VideoInfo
import com.abbas.aldaghat.ui.components.InfoRow
import com.abbas.aldaghat.ui.components.SectionCard
import com.abbas.aldaghat.ui.util.VideoIntents
import com.abbas.aldaghat.util.Formatters
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenHistory: () -> Unit,
    onOpenFolder: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val jobState by viewModel.jobState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.selectVideo(uri)
    }

    val pickDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.selectVideo(uri)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val storageGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            grants[Manifest.permission.WRITE_EXTERNAL_STORAGE] != false
        if (storageGranted) {
            viewModel.startCompression()
        } else {
            viewModel.onStoragePermissionDenied()
        }
    }

    val launchPicker = {
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly),
        )
    }

    val requestStart = {
        val missing = mutableListOf<String>()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            missing += Manifest.permission.POST_NOTIFICATIONS
        }
        if (missing.isEmpty()) viewModel.startCompression() else permissionLauncher.launch(missing.toTypedArray())
    }

    val isBusy = jobState is JobState.Preparing ||
        jobState is JobState.Running ||
        jobState is JobState.Saving

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.video != null && !state.loadingVideo && !isBusy) {
                Surface(
                    color = MaterialTheme.colorScheme.background,
                    shadowElevation = 8.dp,
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                scrollState.animateScrollTo(0)
                                requestStart()
                            }
                        },
                        enabled = state.canStart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.home_start),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp),
        ) {
            HomeHeader(onOpenHistory = onOpenHistory)

            state.errorMessage?.let { message ->
                ErrorBanner(message = message, onDismiss = viewModel::dismissError)
            }

            if (isBusy) {
                JobCard(
                    jobState = jobState,
                    onCancel = { viewModel.cancelJob() },
                )
                Spacer(Modifier.height(12.dp))
            }

            when {
                state.loadingVideo -> LoadingCard()
                state.video == null -> PickerCard(
                    loading = false,
                    picker = { launchPicker() },
                    browse = {
                        pickDocument.launch(arrayOf("video/*"))
                    },
                    offlineNote = stringResource(R.string.home_offline_note),
                )

                else -> {
                    val video = state.video!!
                    VideoCard(
                        video = video,
                        thumbnailPath = state.thumbnailPath,
                        onChangeVideo = { launchPicker() },
                        changeEnabled = !isBusy,
                    )
                    Spacer(Modifier.height(12.dp))

                    QualitySection(
                        state = state,
                        onPresetSelected = viewModel::selectPreset,
                        onCustomBitrate = viewModel::setCustomVideoBitrate,
                        onAudioBitrate = viewModel::setCustomAudioBitrate,
                        onCodecSelected = viewModel::setUseHevc,
                    )
                    Spacer(Modifier.height(12.dp))

                    EstimateCard(state = state)
                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.estimate_disclaimer),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    (jobState as? JobState.Success)?.let { success ->
        CompletionDialog(
            result = success.result,
            onOpen = {
                VideoIntents.open(context, Uri.parse(success.result.outputUri))
                viewModel.acknowledgeJobResult()
            },
            onFolder = {
                viewModel.acknowledgeJobResult()
                if (!VideoIntents.openAbbasFolder(context)) onOpenFolder()
            },
            onShare = {
                VideoIntents.share(
                    context,
                    Uri.parse(success.result.outputUri),
                    success.result.outputName,
                )
            },
            onDismiss = { viewModel.acknowledgeJobResult() },
        )
    }

    if (jobState is JobState.Failed || jobState is JobState.Cancelled) {
        val failed = jobState is JobState.Failed
        val message = when (val current = jobState) {
            is JobState.Failed -> current.message
            is JobState.Cancelled -> stringResource(R.string.job_cancelled_by_user)
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { viewModel.acknowledgeJobResult() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    stringResource(
                        if (failed) R.string.notification_failed_title else R.string.notification_cancelled_title,
                    ),
                )
            },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.acknowledgeJobResult() }) {
                    Text(stringResource(R.string.action_close))
                }
            },
        )
    }
}

@Composable
private fun HomeHeader(onOpenHistory: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_logo),
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onOpenHistory) {
            Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.history_title))
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard() {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.job_preparing),
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun PickerCard(
    loading: Boolean,
    picker: () -> Unit,
    browse: () -> Unit,
    offlineNote: String,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(R.string.home_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                text = stringResource(R.string.home_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = picker,
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp)
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.home_pick_video),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            TextButton(onClick = browse, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.home_browse_files))
            }
            Text(
                text = offlineNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun VideoCard(
    video: VideoInfo,
    thumbnailPath: String?,
    onChangeVideo: () -> Unit,
    changeEnabled: Boolean,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            VideoPreview(
                uri = Uri.parse(video.uri),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(androidx.compose.ui.graphics.Color.Black),
            )
            Text(
                text = video.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp),
            )
            Column(modifier = Modifier.padding(top = 6.dp)) {
                InfoRow(stringResource(R.string.home_label_resolution), Formatters.formatResolution(video.displayWidth, video.displayHeight))
                InfoRow(stringResource(R.string.home_label_duration), Formatters.formatDuration(video.durationMs))
                InfoRow(stringResource(R.string.label_original_size), Formatters.formatBytes(video.sizeBytes))
                InfoRow(
                    stringResource(R.string.home_label_frame_rate),
                    Formatters.formatFrameRate(video.effectiveFrameRate),
                )
                InfoRow(
                    stringResource(R.string.home_label_audio_track),
                    stringResource(
                        if (video.hasAudio) R.string.home_audio_present else R.string.home_audio_absent,
                    ),
                )
            }
            OutlinedButton(
                onClick = onChangeVideo,
                enabled = changeEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(stringResource(R.string.home_change_video))
            }
        }
    }
}

@Composable
private fun VideoPreview(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }
    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
        },
    )
}

@Composable
private fun QualitySection(
    state: HomeUiState,
    onPresetSelected: (QualityPreset) -> Unit,
    onCustomBitrate: (Int) -> Unit,
    onAudioBitrate: (Int) -> Unit,
    onCodecSelected: (Boolean) -> Unit,
) {
    SectionCard(
        title = stringResource(R.string.home_quality_title),
        subtitle = stringResource(R.string.home_quality_subtitle),
    ) {
        val standardPresets = remember {
            QualityPreset.entries.filter { !it.isCustom }
        }
        standardPresets.chunked(2).forEach { rowPresets ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowPresets.forEach { preset ->
                    PresetOption(
                        preset = preset,
                        selected = state.selectedPreset == preset,
                        onClick = { onPresetSelected(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowPresets.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        PresetOption(
            preset = QualityPreset.CUSTOM,
            selected = state.selectedPreset.isCustom,
            onClick = { onPresetSelected(QualityPreset.CUSTOM) },
            modifier = Modifier.fillMaxWidth(),
        )

        if (state.selectedPreset.isCustom) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                Text(
                    text = stringResource(R.string.home_custom_bitrate_title) + ": " +
                        stringResource(R.string.home_custom_bitrate_value, state.customVideoBitrateKbps),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = state.customVideoBitrateKbps.toFloat(),
                    onValueChange = { onCustomBitrate(it.toInt()) },
                    valueRange = 200f..8000f,
                )
                Text(
                    text = stringResource(R.string.home_audio_bitrate_title),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(64, 96, 128, 192, 256).forEach { kbps ->
                        FilterChip(
                            selected = state.customAudioBitrateKbps == kbps,
                            onClick = { onAudioBitrate(kbps) },
                            label = { Text("$kbps") },
                        )
                    }
                }
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = stringResource(R.string.home_codec_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CodecOption(
                label = stringResource(R.string.home_codec_h264),
                selected = !state.useHevc,
                enabled = true,
                onClick = { onCodecSelected(false) },
                modifier = Modifier.weight(1f),
            )
            CodecOption(
                label = stringResource(R.string.home_codec_hevc),
                selected = state.useHevc,
                enabled = state.hevcSupported,
                onClick = { onCodecSelected(true) },
                modifier = Modifier.weight(1f),
            )
        }
        if (!state.hevcSupported) {
            Text(
                text = stringResource(R.string.home_codec_hevc_unavailable),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun PresetOption(
    preset: QualityPreset,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val selectedColor = MaterialTheme.colorScheme.primary
    val borderColor = if (selected) selectedColor else MaterialTheme.colorScheme.outline
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .border(BorderStroke(if (selected) 2.dp else 1.dp, borderColor), shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = null)
            Text(
                text = preset.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Text(
            text = preset.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (preset.isCustom) {
            Text(
                text = "${BitrateSelector.ABSOLUTE_MIN_VIDEO_KBPS}–${BitrateSelector.CUSTOM_MAX_VIDEO_KBPS} كيلوبت/ث",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = "${preset.minVideoBitrateKbps}–${preset.maxVideoBitrateKbps} كيلوبت/ث",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodecOption(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant
                    selected -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .border(
                BorderStroke(
                    1.dp,
                    if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
                ),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun EstimateCard(state: HomeUiState) {
    val video = state.video ?: return
    val plan = state.plan ?: return
    SectionCard(title = stringResource(R.string.home_estimate_title)) {
        InfoRow(stringResource(R.string.label_original_size), Formatters.formatBytes(video.sizeBytes))
        InfoRow(
            stringResource(R.string.label_estimated_size),
            Formatters.formatMegabytes(state.estimatedMegabytes),
        )
        InfoRow(
            stringResource(R.string.label_expected_saving),
            Formatters.formatMegabytes(state.savedBytes / 1024.0 / 1024.0),
        )
        InfoRow(
            stringResource(R.string.label_reduction_ratio),
            Formatters.formatPercent(state.savingPercent, 1),
        )
        LinearProgressIndicator(
            progress = { (state.savingPercent / 100.0).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.height(10.dp))
        InfoRow(
            stringResource(R.string.label_output_resolution),
            Formatters.formatResolution(plan.outputWidth, plan.outputHeight),
        )
        InfoRow(
            stringResource(R.string.label_output_bitrate),
            Formatters.formatBitrate(plan.videoBitrateKbps),
        )
        if (plan.hasAudio) {
            InfoRow(
                stringResource(R.string.home_audio_bitrate_title),
                Formatters.formatBitrate(plan.audioBitrateKbps),
            )
        }
    }
}

@Composable
private fun JobCard(jobState: JobState, onCancel: () -> Unit) {
    val inputName = when (jobState) {
        is JobState.Preparing -> jobState.inputName
        is JobState.Running -> jobState.inputName
        is JobState.Saving -> jobState.inputName
        else -> ""
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = inputName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            when (jobState) {
                is JobState.Running -> {
                    Text(
                        text = stringResource(R.string.job_running, jobState.progressPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    LinearProgressIndicator(
                        progress = { jobState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    InfoRow(
                        stringResource(R.string.job_elapsed),
                        Formatters.formatElapsed(jobState.elapsedMs),
                    )
                    InfoRow(
                        stringResource(R.string.job_remaining),
                        Formatters.formatEta(jobState.etaMs),
                    )
                }

                is JobState.Saving -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = stringResource(R.string.job_saving),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                    Text(
                        text = stringResource(R.string.job_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Text(
                text = stringResource(R.string.job_background_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    }
}

@Composable
private fun CompletionDialog(
    result: CompressionResult,
    onOpen: () -> Unit,
    onFolder: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(R.string.success_title)) },
        text = {
            Column {
                Text(
                    text = result.outputName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                InfoRow(stringResource(R.string.label_original_size), Formatters.formatBytes(result.sourceBytes))
                InfoRow(stringResource(R.string.label_final_size), Formatters.formatBytes(result.outputBytes))
                InfoRow(
                    stringResource(R.string.label_expected_saving),
                    Formatters.formatBytes(result.savedBytes),
                )
                InfoRow(
                    stringResource(R.string.label_reduction_ratio),
                    Formatters.formatPercent(result.savingPercent, 1),
                )
                Text(
                    text = stringResource(R.string.success_saved_in),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            Button(onClick = onOpen) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.action_open_video))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onFolder) {
                    Text(stringResource(R.string.action_open_folder))
                }
                TextButton(onClick = onShare) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_share))
                }
            }
        },
    )
}
