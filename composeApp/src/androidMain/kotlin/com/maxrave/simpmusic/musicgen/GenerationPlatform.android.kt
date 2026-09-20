package com.maxrave.simpmusic.musicgen

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.maxrave.simpmusic.service.musicgen.MusicGenerationService
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

private fun context(): Context = getKoin().get()

private fun generatedDirectory(): File = File(context().filesDir, "generated").apply { mkdirs() }

private fun generatedFile(jobId: String): File = File(generatedDirectory(), "$jobId.opus")

actual fun startGenerationMonitor(
    jobId: String,
    title: String,
) {
    val context = context()
    ContextCompat.startForegroundService(
        context,
        Intent(context, MusicGenerationService::class.java)
            .putExtra(MusicGenerationService.EXTRA_JOB_ID, jobId)
            .putExtra(MusicGenerationService.EXTRA_TITLE, title),
    )
}

actual fun localGeneratedAudioUri(jobId: String): String? =
    generatedFile(jobId).takeIf(File::isFile)?.toUri()?.toString()

actual fun saveGeneratedAudio(
    jobId: String,
    bytes: ByteArray,
): String {
    val target = generatedFile(jobId)
    target.writeBytes(bytes)
    return target.toUri().toString()
}

actual fun shareGeneratedAudio(
    uri: String,
    title: String,
) {
    val context = context()
    val file = File(requireNotNull(uri.toUri().path))
    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val intent =
        Intent(Intent.ACTION_SEND)
            .setType("audio/ogg")
            .putExtra(Intent.EXTRA_STREAM, contentUri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

actual fun exportGeneratedAudio(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
) {
    val context = context()
    val values =
        ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/SimpMusic")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
        }
    val uri = requireNotNull(context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values))
    context.contentResolver.openOutputStream(uri).use { output ->
        requireNotNull(output).write(bytes)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        values.clear()
        values.put(MediaStore.Audio.Media.IS_PENDING, 0)
        context.contentResolver.update(uri, values, null, null)
    }
}

actual fun deleteGeneratedAudio(jobId: String) {
    val directory = generatedDirectory().canonicalFile
    val target = generatedFile(jobId).canonicalFile
    check(target.parentFile == directory) { "generated audio path escapes its directory" }
    if (target.isFile) target.delete()
}
