package com.maxrave.simpmusic.ui.screen.musicgen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.maxrave.simpmusic.musicgen.startGenerationMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.simpmusic.musicgenservice.Job
import org.simpmusic.musicgenservice.JobMode
import org.simpmusic.musicgenservice.JobRequest
import org.simpmusic.musicgenservice.JobSource
import org.simpmusic.musicgenservice.LyricsKind
import org.simpmusic.musicgenservice.LyricsSpec
import org.simpmusic.musicgenservice.MusicGenClientFactory
import org.simpmusic.musicgenservice.MusicGenException
import org.simpmusic.musicgenservice.MusicGenRepository
import org.simpmusic.musicgenservice.SourceKind
import org.simpmusic.musicgenservice.TrackMetadata

/**
 * Drives one generation from the sheet.
 *
 * All state that matters lives on the backend, so this holder only mirrors the
 * latest job for display; dismissing the sheet does not cancel the work.
 */
class MusicGenSheetState(
    private val repository: MusicGenRepository,
    private val factory: MusicGenClientFactory,
    private val scope: CoroutineScope,
) {
    var job: Job? by mutableStateOf(null)
        private set

    var error: String? by mutableStateOf(null)
        private set

    var isStarting: Boolean by mutableStateOf(false)
        private set

    private var watcher: CoroutineJob? = null

    init {
        scope.launch {
            runCatching { repository.refresh() }
                .getOrNull()
                ?.firstOrNull { !it.status.isTerminal }
                ?.let(::reattach)
        }
    }

    fun start(request: MusicGenRequest) {
        if (factory.current() == null) {
            error = "Set the backend URL and token in Settings first."
            return
        }
        isStarting = true
        error = null
        scope.launch {
            try {
                val created = repository.start(request.toJobRequest())
                job = created
                startGenerationMonitor(created.id, request.title.ifBlank { "Generated song" })
                watch(created)
            } catch (exception: MusicGenException) {
                error = exception.message ?: "The backend rejected the request."
            } catch (exception: Exception) {
                error = exception.message ?: "Could not reach the backend."
            } finally {
                isStarting = false
            }
        }
    }

    private fun watch(created: Job) {
        watcher?.cancel()
        watcher =
            scope.launch {
                repository.observe(created).collect { latest -> job = latest }
            }
    }

    fun cancel() {
        val id = job?.id ?: return
        scope.launch {
            runCatching { repository.cancel(id) }
                .onSuccess { job = it }
                .onFailure { error = it.message }
        }
    }

    /** Reattach to a job already running, e.g. after the sheet was reopened. */
    fun reattach(latest: Job) {
        job = latest
        watch(latest)
    }
}

@Composable
fun rememberMusicGenSheetState(
    repository: MusicGenRepository = koinInject(),
    factory: MusicGenClientFactory = koinInject(),
): MusicGenSheetState {
    val scope = rememberCoroutineScope()
    return remember(repository, factory) { MusicGenSheetState(repository, factory, scope) }
}

private fun MusicGenRequest.toJobRequest(): JobRequest {
    val source =
        videoId?.let { JobSource(kind = SourceKind.VideoId, videoId = it) }
    val lyrics =
        when (lyricsChoice) {
            LyricsChoice.Generate -> LyricsSpec(kind = LyricsKind.Generate)
            LyricsChoice.Reuse -> LyricsSpec(kind = LyricsKind.Source)
            LyricsChoice.Paste ->
                LyricsSpec(
                    kind = LyricsKind.Provided,
                    text = pastedLyrics,
                    style = pastedStyle,
                )
        }
    return JobRequest(
        mode = if (source == null) JobMode.Scratch else JobMode.Cover,
        source = source,
        prompt = prompt,
        lyrics = lyrics,
        metadata = TrackMetadata(title = title, artist = artist, album = album.orEmpty()),
        seed = seed,
    )
}
