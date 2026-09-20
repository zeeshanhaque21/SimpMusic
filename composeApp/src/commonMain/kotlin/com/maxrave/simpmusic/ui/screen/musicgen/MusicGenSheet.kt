package com.maxrave.simpmusic.ui.screen.musicgen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.maxrave.simpmusic.ui.component.rememberSurfaceDarkColors
import org.simpmusic.musicgenservice.Job
import org.simpmusic.musicgenservice.JobStage
import org.simpmusic.musicgenservice.JobStatus
import org.simpmusic.musicgenservice.label

/** Lyrics source chosen in the sheet, mapped to the backend's lyrics spec. */
enum class LyricsChoice { Generate, Paste, Reuse }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicGenSheet(
    videoId: String,
    title: String,
    artist: String,
    album: String?,
    onDismiss: () -> Unit,
    state: MusicGenSheetState = rememberMusicGenSheetState(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = rememberSurfaceDarkColors()

    var prompt by remember { mutableStateOf("") }
    var pastedLyrics by remember { mutableStateOf("") }
    var pastedStyle by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("831001") }
    var choice by remember { mutableStateOf(LyricsChoice.Generate) }
    var includeSource by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.container,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Reimagine",
                style = MaterialTheme.typography.titleLarge,
                color = colors.content,
            )

            if (includeSource) {
                Text(
                    text = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" - "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.subtitle,
                )
            }

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("What should it become?") },
                placeholder = { Text("a slow jazz ballad with brushed drums") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = choice == LyricsChoice.Generate,
                    onClick = { choice = LyricsChoice.Generate },
                    label = { Text("Write lyrics") },
                )
                FilterChip(
                    selected = choice == LyricsChoice.Reuse,
                    onClick = { choice = LyricsChoice.Reuse },
                    label = { Text("Keep lyrics") },
                )
                FilterChip(
                    selected = choice == LyricsChoice.Paste,
                    onClick = { choice = LyricsChoice.Paste },
                    label = { Text("Paste") },
                )
            }

            if (choice == LyricsChoice.Paste) {
                OutlinedTextField(
                    value = pastedStyle,
                    onValueChange = { pastedStyle = it },
                    label = { Text("Style prompt") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = pastedLyrics,
                    onValueChange = { pastedLyrics = it },
                    label = { Text("Lyrics") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                )
            }

            OutlinedTextField(
                value = seed,
                onValueChange = { seed = it.filter { c -> c.isDigit() } },
                label = { Text("Seed") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
            )

            FilterChip(
                selected = includeSource,
                onClick = { includeSource = !includeSource },
                label = { Text(if (includeSource) "Using this track" else "Writing a new song") },
            )

            state.job?.let { job ->
                JobProgress(job = job, onCancel = { state.cancel() })
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Close") }
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = state.job?.status?.isTerminal != false && !state.isStarting,
                    onClick = {
                        state.start(
                            MusicGenRequest(
                                videoId = videoId.takeIf { includeSource },
                                title = title,
                                artist = artist,
                                album = album,
                                prompt = prompt,
                                lyricsChoice = choice,
                                pastedLyrics = pastedLyrics,
                                pastedStyle = pastedStyle,
                                seed = seed.toLongOrNull() ?: 831001L,
                            ),
                        )
                    },
                ) {
                    if (state.isStarting) {
                        CircularProgressIndicator(modifier = Modifier.height(18.dp))
                    } else {
                        Text("Generate")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun JobProgress(job: Job, onCancel: () -> Unit) {
    val colors = rememberSurfaceDarkColors()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val stage = job.stage
        val label =
            when {
                job.status == JobStatus.Queued ->
                    job.queuePosition?.let { "Queued, position $it" } ?: "Queued"
                job.status == JobStatus.Running && stage != null -> stage.label
                job.status == JobStatus.Complete -> "Done"
                job.status == JobStatus.Cancelled -> "Cancelled"
                else -> "Failed"
            }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.content,
                modifier = Modifier.weight(1f),
            )
            if (job.status == JobStatus.Queued || job.status == JobStatus.Running) {
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        if (job.status == JobStatus.Running || job.status == JobStatus.Complete) {
            LinearProgressIndicator(
                progress = { job.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        job.durationSeconds?.let { seconds ->
            Text(
                text = "%.0f seconds of audio".format(seconds),
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtitle,
            )
        }
        if (job.truncated == true) {
            Text(
                text = "The model hit its length limit, so the ending is cut short.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.subtitle,
            )
        }
    }
}

/** Everything the sheet needs to build a request. */
data class MusicGenRequest(
    val videoId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val prompt: String,
    val lyricsChoice: LyricsChoice,
    val pastedLyrics: String,
    val pastedStyle: String,
    val seed: Long,
)
