package com.maxrave.simpmusic.ui.screen.musicgen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.maxrave.domain.data.player.GenericMediaItem
import com.maxrave.domain.data.player.GenericMediaMetadata
import com.maxrave.domain.mediaservice.handler.MediaPlayerHandler
import com.maxrave.simpmusic.musicgen.deleteGeneratedAudio
import com.maxrave.simpmusic.musicgen.exportGeneratedAudio
import com.maxrave.simpmusic.musicgen.localGeneratedAudioUri
import com.maxrave.simpmusic.musicgen.saveGeneratedAudio
import com.maxrave.simpmusic.musicgen.shareGeneratedAudio
import com.maxrave.simpmusic.ui.icon.ArrowBackIosNew
import com.maxrave.simpmusic.ui.icon.Delete
import com.maxrave.simpmusic.ui.icon.Download
import com.maxrave.simpmusic.ui.icon.PlayArrow
import com.maxrave.simpmusic.ui.icon.Share
import com.maxrave.simpmusic.ui.icon.SimpIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.simpmusic.musicgenservice.Job
import org.simpmusic.musicgenservice.JobStatus
import org.simpmusic.musicgenservice.MusicGenRepository
import org.simpmusic.musicgenservice.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeneratedScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    state: GeneratedScreenState = rememberGeneratedScreenState(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Generated") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(SimpIcons.ArrowBackIosNew, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let { message ->
                item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            }
            if (state.jobs.isEmpty() && !state.loading) {
                item { Text("No generated songs yet", modifier = Modifier.padding(16.dp)) }
            }
            items(state.jobs, key = Job::id) { job ->
                GeneratedJobCard(
                    job = job,
                    busy = state.busyJobId == job.id,
                    onPlay = { state.play(job) },
                    onShare = { state.share(job) },
                    onExport = { state.export(job) },
                    onDelete = { state.delete(job) },
                )
            }
        }
    }
}

@Composable
private fun GeneratedJobCard(
    job: Job,
    busy: Boolean,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(job.displayTitle(), style = MaterialTheme.typography.titleMedium)
            Text(
                job.statusText(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!job.status.isTerminal) {
                LinearProgressIndicator(
                    progress = { job.progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (job.status.isTerminal) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else {
                        if (job.status == JobStatus.Complete) {
                            IconButton(onClick = onPlay) { Icon(SimpIcons.PlayArrow, "Play") }
                            IconButton(onClick = onShare) { Icon(SimpIcons.Share, "Share") }
                            IconButton(onClick = onExport) { Icon(SimpIcons.Download, "Export FLAC") }
                        }
                        IconButton(onClick = onDelete) { Icon(SimpIcons.Delete, "Delete") }
                    }
                }
            }
        }
    }
}

class GeneratedScreenState(
    private val repository: MusicGenRepository,
    private val player: MediaPlayerHandler,
    private val scope: CoroutineScope,
) {
    var jobs: List<Job> by mutableStateOf(emptyList())
        private set
    var loading: Boolean by mutableStateOf(true)
        private set
    var busyJobId: String? by mutableStateOf(null)
        private set
    var error: String? by mutableStateOf(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            loading = true
            runCatching { repository.refresh() }
                .onSuccess { jobs = it }
                .onFailure { error = it.message ?: "Could not load generated songs" }
            loading = false
        }
    }

    fun play(job: Job) = run(job) {
        val uri = ensureAudio(job)
        player.addMediaItem(
            GenericMediaItem(
                mediaId = "generated:${job.id}",
                uri = uri,
                metadata = GenericMediaMetadata(title = job.displayTitle(), artist = "Generated with YuE2"),
            ),
        )
    }

    fun share(job: Job) = run(job) {
        shareGeneratedAudio(ensureAudio(job), job.displayTitle())
    }

    fun export(job: Job) = run(job) {
        exportGeneratedAudio("${job.safeFileName()}.flac", "audio/flac", repository.exportMaster(job.id))
    }

    fun delete(job: Job) = run(job) {
        repository.delete(job.id)
        deleteGeneratedAudio(job.id)
        jobs = jobs.filterNot { it.id == job.id }
    }

    private fun run(
        job: Job,
        action: suspend () -> Unit,
    ) {
        scope.launch {
            busyJobId = job.id
            error = null
            runCatching { action() }.onFailure { error = it.message ?: "Action failed" }
            busyJobId = null
        }
    }

    private suspend fun ensureAudio(job: Job): String =
        localGeneratedAudioUri(job.id) ?: saveGeneratedAudio(job.id, repository.download(job.id))
}

@Composable
private fun rememberGeneratedScreenState(
    repository: MusicGenRepository = koinInject(),
    player: MediaPlayerHandler = koinInject(),
): GeneratedScreenState {
    val scope = rememberCoroutineScope()
    return remember(repository, player) { GeneratedScreenState(repository, player, scope) }
}

private fun Job.displayTitle(): String = title?.takeIf(String::isNotBlank) ?: "Generated ${id.take(8)}"

private fun Job.safeFileName(): String =
    displayTitle().replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifBlank { "generated-$id" }

private fun Job.statusText(): String =
    when (status) {
        JobStatus.Queued -> queuePosition?.let { "Queued, position $it" } ?: "Queued"
        JobStatus.Running -> stage?.label ?: "Generating"
        JobStatus.Complete -> durationSeconds?.let { "Ready - ${it.toInt()} seconds" } ?: "Ready"
        JobStatus.Failed -> error ?: "Failed"
        JobStatus.Cancelled -> "Cancelled"
    }
