package com.maxrave.simpmusic.service.musicgen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job as CoroutineJob
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.simpmusic.musicgenservice.Job
import org.simpmusic.musicgenservice.JobStage
import org.simpmusic.musicgenservice.JobStatus
import org.simpmusic.musicgenservice.MusicGenRepository
import org.simpmusic.musicgenservice.label

class MusicGenerationService : Service() {
    private val repository: MusicGenRepository by inject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var monitor: CoroutineJob? = null

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Music generation", NotificationManager.IMPORTANCE_LOW),
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val jobId = intent?.getStringExtra(EXTRA_JOB_ID) ?: preferences.getString(EXTRA_JOB_ID, null)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: preferences.getString(EXTRA_TITLE, null) ?: "Generated song"
        if (jobId == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        preferences.edit().putString(EXTRA_JOB_ID, jobId).putString(EXTRA_TITLE, title).apply()
        startForeground(NOTIFICATION_ID, notification(title, "Connecting to backend", null))
        monitor?.cancel()
        monitor = scope.launch { monitor(jobId, title) }
        return START_STICKY
    }

    private suspend fun monitor(
        jobId: String,
        title: String,
    ) {
        var initial: Job? = null
        while (scope.isActive && initial == null) {
            initial = runCatching { repository.get(jobId) }.getOrNull()
            if (initial == null) delay(5_000)
        }
        val first = initial ?: return
        repository.observe(first).collect { job ->
            getSystemService(NotificationManager::class.java).notify(
                NOTIFICATION_ID,
                notification(title, job.label(), job),
            )
            if (job.status.isTerminal) {
                getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit().clear().apply()
                stopForeground(STOP_FOREGROUND_DETACH)
                stopSelf()
            }
        }
    }

    private fun notification(
        title: String,
        text: String,
        job: Job?,
    ): Notification {
        val openApp =
            PendingIntent.getActivity(
                this,
                0,
                packageManager.getLaunchIntentForPackage(packageName) ?: Intent(),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val builder =
            NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(openApp)
                .setOnlyAlertOnce(true)
                .setOngoing(job?.status?.isTerminal != true)
        when (job?.status) {
            JobStatus.Running -> builder.setProgress(1000, job.overallProgress(), false)
            JobStatus.Complete -> builder.setProgress(1000, 1000, false)
            JobStatus.Queued, null -> builder.setProgress(0, 0, true)
            else -> Unit
        }
        return builder.build()
    }

    override fun onDestroy() {
        monitor?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_JOB_ID = "music_generation_job_id"
        const val EXTRA_TITLE = "music_generation_title"
        private const val PREFERENCES = "music_generation"
        private const val CHANNEL_ID = "music_generation"
        private const val NOTIFICATION_ID = 9021
    }
}

private fun Job.label(): String =
    when (status) {
        JobStatus.Queued -> queuePosition?.let { "Queued, position $it" } ?: "Queued"
        JobStatus.Running -> stage?.label ?: "Generating"
        JobStatus.Complete -> "Ready to play"
        JobStatus.Cancelled -> "Generation cancelled"
        JobStatus.Failed -> error ?: "Generation failed"
    }

private fun Job.overallProgress(): Int {
    val stageIndex =
        when (stage) {
            JobStage.Fetch -> 0
            JobStage.Transcribe -> 1
            JobStage.Lyrics -> 2
            JobStage.Plan -> 3
            JobStage.Render -> 4
            JobStage.Transcode -> 5
            null -> 0
        }
    return (((stageIndex + progress.coerceIn(0f, 1f)) / 6f) * 1000).toInt()
}
