package com.maxrave.simpmusic.musicgen

import java.awt.Desktop
import java.io.File

private fun generatedDirectory(): File =
    File(System.getProperty("user.home"), ".simpmusic/generated").apply { mkdirs() }

private fun generatedFile(jobId: String): File = File(generatedDirectory(), "$jobId.opus")

actual fun startGenerationMonitor(
    jobId: String,
    title: String,
) = Unit

actual fun localGeneratedAudioUri(jobId: String): String? =
    generatedFile(jobId).takeIf(File::isFile)?.toURI()?.toString()

actual fun saveGeneratedAudio(
    jobId: String,
    bytes: ByteArray,
): String {
    val target = generatedFile(jobId)
    target.writeBytes(bytes)
    return target.toURI().toString()
}

actual fun shareGeneratedAudio(
    uri: String,
    title: String,
) {
    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(File(java.net.URI(uri)).parentFile)
}

actual fun exportGeneratedAudio(
    fileName: String,
    mimeType: String,
    bytes: ByteArray,
) {
    val directory = File(System.getProperty("user.home"), "Music/SimpMusic").apply { mkdirs() }
    File(directory, fileName).writeBytes(bytes)
}

actual fun deleteGeneratedAudio(jobId: String) {
    val directory = generatedDirectory().canonicalFile
    val target = generatedFile(jobId).canonicalFile
    check(target.parentFile == directory) { "generated audio path escapes its directory" }
    if (target.isFile) target.delete()
}
